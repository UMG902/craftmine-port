package dev.craftmine.unlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.Pair;

import dev.craftmine.duck.CraftminePlayerDuck;
import dev.craftmine.item.component.ItemExchangeValue;
import dev.craftmine.mines.CustomIcons;
import dev.craftmine.registry.CraftmineDataComponents;
import dev.craftmine.registry.CraftmineRegistries;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * One node of the player unlock tree ("buy speed II for 10 levels"): what it shows in
 * the hub UI, what it costs, how it becomes visible, and the hooks that run when it is
 * activated, re-entered on mine entry, or deactivated (unoriginal relock).
 *
 * <p>Instances are registered into the {@link CraftmineRegistries#PLAYER_UNLOCK} registry
 * by the {@link Builder} (one builder chain per node in {@link PlayerUnlocks}); the
 * {@link #CODEC} resolves holders by registry name for networking and saves.</p>
 */
public record PlayerUnlock(String key, Optional<Holder<PlayerUnlock>> parent, List<Holder<PlayerUnlock>> disables,
    DisplayInfo display, Consumer<ServerPlayer> activation, Consumer<ServerPlayer> onMineEnter,
    List<UnlockCondition> madeVisibleBy, UnlockVisibility defaultVisibility,
    Map<TagKey<Item>, Float> experienceFactorForItemTag, Map<Item, Float> experienceFactorForItem,
    int unlockPrice, String exclusiveKey, boolean unoriginal, String unoriginalFeature,
    Optional<Holder<PlayerUnlock>> requires, Consumer<ServerPlayer> deactivation) {

    public static final Codec<Holder<PlayerUnlock>> CODEC = CraftmineRegistries.PLAYER_UNLOCK.holderByNameCodec();

    /** False for unoriginal unlocks while the unoriginal_features config is off, or (for nodes scoped
     *  to a sub-feature via {@link Builder#unoriginal(String)}) while that sub-toggle is off too. */
    public boolean isEnabled() {
        return !this.unoriginal || dev.craftmine.CraftmineConfig.unoriginalFeatureAllowed(this.unoriginalFeature);
    }

    /** Builder for a tree root, with the default unlock-background texture for its key. */
    public static Builder root(String key) {
        return new Builder(key, Optional.empty(), Optional.of(new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("unlock_backgrounds/" + key))));
    }

    /** Builder for a node displayed below {@code parent} in the tree. */
    public static Builder child(String key, Holder<PlayerUnlock> parent) {
        return new Builder(key, Optional.of(parent), Optional.empty());
    }

    /** Walks up the parent chain to the tree root of the given node. */
    public static Holder<PlayerUnlock> getRoot(Holder<PlayerUnlock> holder) {
        Holder<PlayerUnlock> current = holder;
        Optional<Holder<PlayerUnlock>> parent = current.value().parent();
        while (parent.isPresent()) {
            current = parent.get();
            parent = current.value().parent();
        }
        return current;
    }

    /** How a node shows up in the hub tree before being bought. Mystery nodes appear as an unnamed silhouette. */
    public static enum UnlockVisibility implements StringRepresentable {
        VISIBLE,
        INVISIBLE,
        MYSTERY;

        public static final StreamCodec<ByteBuf, UnlockVisibility> STREAM_CODEC =
            ByteBufCodecs.idMapper(ordinal -> UnlockVisibility.values()[ordinal], Enum::ordinal);

        @Override
        public String getSerializedName() {
            return this.name();
        }
    }

    /** Fluent factory for one unlock: defaults (visible, price 1, standard title/description/hint) overridable per node. */
    public static class Builder {
        private final String key;
        private final Optional<Holder<PlayerUnlock>> parent;
        private final DisplayInfoBuilder display;
        private final List<Consumer<ServerPlayer>> activation = new ArrayList<>();
        private final List<Consumer<ServerPlayer>> onMineEnter = new ArrayList<>();
        private final List<UnlockCondition> madeVisibleBy = new ArrayList<>();
        private final List<Holder<PlayerUnlock>> disables = new ArrayList<>();
        private final Map<TagKey<Item>, Float> experienceFactorForItemTag = new HashMap<>();
        private final Map<Item, Float> experienceFactorForItem = new HashMap<>();
        private UnlockVisibility defaultVisibility = UnlockVisibility.VISIBLE;
        int unlockPrice = 1;
        private String exclusiveKey = "";
        private boolean unoriginal;
        private String unoriginalFeature;
        private Optional<Holder<PlayerUnlock>> requires = Optional.empty();
        private final List<Consumer<ServerPlayer>> deactivation = new ArrayList<>();

        public Builder(String key, Optional<Holder<PlayerUnlock>> parent, Optional<ClientAsset> background) {
            this.key = key;
            this.parent = parent;
            this.display = new DisplayInfoBuilder()
                .withTitle(Component.translatable("unlocks.unlock." + key + ".name"))
                .withDescription(Component.translatable("unlocks.unlock." + key + ".description"))
                .withHint(Component.translatable("unlocks.unlock." + key + ".hint"))
                .withType(AdvancementType.TASK)
                .withAnnounceChat(true)
                .withHidden(false)
                .withShowToast(false);
            background.ifPresent(this.display::withBackground);
        }

        public Builder withIcon(Supplier<ItemStack> icon) {
            this.display.withIcon(icon.get().copy());
            return this;
        }

        public Builder withIcon(Item item) {
            this.display.withIcon(item.getDefaultInstance());
            return this;
        }

        /** Uses a mod-registered custom icon model (see {@link dev.craftmine.mines.CustomIcons}). */
        public Builder withCustomIcon(String icon) {
            ItemStack itemStack = new ItemStack(Items.STONE);
            itemStack.set(DataComponents.ITEM_MODEL, CustomIcons.register(icon));
            this.display.withIcon(itemStack);
            return this;
        }

        public Builder withTitle(Component title) {
            this.display.withTitle(title);
            return this;
        }

        public Builder becomesVisibleWhen(UnlockCondition... conditions) {
            this.madeVisibleBy.addAll(List.of(conditions));
            return this;
        }

        public Builder withVisibility(UnlockVisibility visibility) {
            this.defaultVisibility = visibility;
            return this;
        }

        public Builder withPrice(int price) {
            this.unlockPrice = price;
            return this;
        }

        /** Nodes sharing an exclusive key form a radio group: activating one deactivates the others. */
        public Builder withExclusiveApplication(String exclusiveKey) {
            this.exclusiveKey = exclusiveKey;
            return this;
        }

        public Builder whenActivated(Consumer<ServerPlayer> action) {
            this.activation.add(action);
            return this;
        }

        /** Runs when the unlock is relocked (unoriginal "relock" feature); undoes whenActivated effects. */
        public Builder whenDeactivated(Consumer<ServerPlayer> action) {
            this.deactivation.add(action);
            return this;
        }

        /** Marks this unlock as an unoriginal feature: it only exists while the unoriginal_features config is on. */
        public Builder unoriginal() {
            this.unoriginal = true;
            return this;
        }

        /** Same, scoped to one named sub-feature ({@link dev.craftmine.CraftmineConfig} FEATURE_ constants):
         *  the node additionally vanishes while that per-feature sub-toggle is off. */
        public Builder unoriginal(String feature) {
            this.unoriginal = true;
            this.unoriginalFeature = feature;
            return this;
        }

        /** The unlock only counts as visible/active while the given unlock is unlocked (extra unlocks gate). */
        public Builder requires(Holder<PlayerUnlock> gate) {
            this.requires = Optional.of(gate);
            return this;
        }

        /** Grants a permanent attribute modifier while active (and removes it again on relock). */
        public Builder givesAttributeModifier(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
            Identifier modifierId = Identifier.withDefaultNamespace("unlock_" + this.key);
            this.whenActivated(player -> {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    instance.removeModifier(modifierId);
                    instance.addPermanentModifier(new AttributeModifier(modifierId, amount, operation));
                }
            });
            this.whenDeactivated(player -> {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    instance.removeModifier(modifierId);
                }
            });
            return this;
        }

        public Builder onMineEnter(Consumer<ServerPlayer> action) {
            this.onMineEnter.add(action);
            return this;
        }

        public Builder givesItemInMine(Item... items) {
            List<Item> granted = Arrays.asList(items);
            this.onMineEnter(player -> granted.forEach(item -> {
                ItemStack itemStack = item.getDefaultInstance();
                itemStack.set(CraftmineDataComponents.EXCHANGE_VALUE, new ItemExchangeValue(0.0f));
                ((CraftminePlayerDuck)player).addOrDropItem(itemStack);
            }));
            return this;
        }

        /** Scales mine XP gained from items of the tag; repeated calls multiply onto the existing factor. */
        public Builder modifyExperienceGainForItem(TagKey<Item> tag, float factor) {
            this.experienceFactorForItemTag.merge(tag, factor, (current, next) -> current * next);
            return this;
        }

        /** Scales mine XP gained from the item; repeated calls multiply onto the existing factor. */
        public Builder modifyExperienceGainForItem(Item item, float factor) {
            this.experienceFactorForItem.merge(item, factor, (current, next) -> current * next);
            return this;
        }

        public Builder givesItemStackInMine(ItemStack... stacks) {
            List<ItemStack> templates = List.of(stacks);
            this.onMineEnter(player -> templates.forEach(template -> {
                ItemStack itemStack = template.copy();
                itemStack.set(CraftmineDataComponents.EXCHANGE_VALUE, new ItemExchangeValue(0.0f));
                ((CraftminePlayerDuck)player).addOrDropItem(itemStack);
            }));
            return this;
        }

        public Builder givesItemStackInMine(Function<ServerPlayer, ItemStack> factory) {
            this.onMineEnter(player -> {
                ItemStack itemStack = factory.apply(player).copy();
                itemStack.set(CraftmineDataComponents.EXCHANGE_VALUE, new ItemExchangeValue(0.0f));
                ((CraftminePlayerDuck)player).addOrDropItem(itemStack);
            });
            return this;
        }

        @SafeVarargs
        public final Builder givesEnchantedItemInMine(Item item, Pair<ResourceKey<Enchantment>, Integer>... enchantments) {
            List<Pair<ResourceKey<Enchantment>, Integer>> applied = Arrays.asList(enchantments);
            this.onMineEnter(player -> {
                ItemStack itemStack = item.getDefaultInstance();
                itemStack.set(CraftmineDataComponents.EXCHANGE_VALUE, new ItemExchangeValue(0.0f));
                for (Pair<ResourceKey<Enchantment>, Integer> entry : applied) {
                    player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .get(entry.first())
                        .ifPresent(enchantment -> itemStack.enchant(enchantment, entry.second()));
                }
                ((CraftminePlayerDuck)player).addOrDropItem(itemStack);
            });
            return this;
        }

        public Builder givesEffectInMine(Holder<MobEffect> effect, int durationTicks) {
            return this.givesEffectInMine(effect, durationTicks, 0);
        }

        /** Grants an ambient effect on every mine entry; duration -1 means infinite, ticks are converted to seconds. */
        public Builder givesEffectInMine(Holder<MobEffect> effect, int durationTicks, int amplifier) {
            MobEffectInstance instance = new MobEffectInstance(effect, durationTicks == -1 ? -1 : durationTicks * 20, amplifier);
            this.onMineEnter(player -> player.addEffect(instance, null));
            return this;
        }

        /** Registers the pet species for world spawning (activation grants, relock revokes, entry spawns). */
        public Builder givesPetInWorld(EntityType<?> petType) {
            this.whenActivated(player -> PetUpgrades.addPet(petType));
            this.whenDeactivated(player -> PetUpgrades.removePet(petType));
            this.onMineEnter(player -> PetUpgrades.getPet(petType, player));
            return this;
        }

        /** Applies an extra upgrade to already-spawned pets of the species (activation adds, relock removes). */
        public Builder upgradePet(EntityType<?> petType, Class<? extends TamableAnimal> petClass, Consumer<TamableAnimal> upgrade) {
            this.whenActivated(player -> PetUpgrades.addUpgrade(petType, upgrade));
            this.whenDeactivated(player -> PetUpgrades.removeUpgrade(petType, upgrade));
            this.onMineEnter(player -> PetUpgrades.upgradePet(petType, petClass, player));
            return this;
        }

        @SafeVarargs
        public final Builder disablesOtherUnlock(Holder<PlayerUnlock>... others) {
            this.disables.addAll(List.of(others));
            return this;
        }

        /** Folds the accumulated hook lists into single composite consumers (no-op when empty). */
        private static Consumer<ServerPlayer> combine(List<Consumer<ServerPlayer>> actions) {
            if (actions.isEmpty()) {
                return player -> {};
            }
            List<Consumer<ServerPlayer>> snapshot = List.copyOf(actions);
            return player -> snapshot.forEach(action -> action.accept(player));
        }

        private PlayerUnlock build() {
            return new PlayerUnlock(this.key, this.parent, this.disables, this.display.build(),
                combine(this.activation), combine(this.onMineEnter), this.madeVisibleBy, this.defaultVisibility,
                this.experienceFactorForItemTag, this.experienceFactorForItem, this.unlockPrice, this.exclusiveKey,
                this.unoriginal, this.unoriginalFeature, this.requires, combine(this.deactivation));
        }

        public Holder<PlayerUnlock> register() {
            return Registry.registerForHolder(CraftmineRegistries.PLAYER_UNLOCK, Identifier.withDefaultNamespace(this.key), this.build());
        }
    }
}
