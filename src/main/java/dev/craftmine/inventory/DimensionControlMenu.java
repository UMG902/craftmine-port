/*
 * Port of craftmine's Dimension Control menu (reference class sEOV + slot
 * inner classes): a single slot that accepts the sky box item and applies the
 * item's minecraft:sky component as the level's active sky. The item IS the
 * picker - there is NO click cycling in the original: placing a sky box from
 * the boiler room chest applies its sky, taking it out (leaving the slot
 * empty) switches the level to the "nothing" sky - a fully BLACK sky (task
 * 27; the fork's save storage keeps this as a present-but-empty sky, NOT as
 * "no override": a level never touched by the control shows its DEFAULT sky
 * instead, which is the code rain on the hub).
 *
 * Server-side prefill at open: a sky box carrying the level's current sky
 * (stored override, or the dimension default via SkyDefaults) - matching the
 * reference's ExotelcraftSaveStorage lookup; with the nothing sky stored the
 * slot starts EMPTY (the original has no sky_box item for "nothing"). The
 * client copy arrives through the vanilla menu slot sync.
 *
 * slotsChanged: the sky from the slot item (NothingSky when empty) is
 * persisted (SkyData) and broadcast (ClientboundSetSkyPacket) to the level's
 * players. DimensionType gate: an end sky keeps the END skybox, every other
 * sky (including nothing) renders through the OVERWORLD skybox pass (the
 * custom sky renderer cancels the vanilla content itself and clears the
 * target black for nothing).
 *
 * Task 44: the gate swaps the picked sky's whole VISUAL ENVIRONMENT, not
 * just the skybox enum. 26.3 renders the vanilla sky from the dimension
 * type's EnvironmentAttributes (sky/fog/cloud colors) + its timelines
 * (sun/moon/star angles, day-night multipliers) + its default clock - the
 * hub dimension type carries NONE of those (its code-rain sky never reads
 * them), so a plain skybox swap made the vanilla overworld sky render the
 * attribute DEFAULTS: black sky, sun frozen at 0 degrees. The overworld sky
 * now carries the canonical overworld visual set (same as the generated
 * mine dimension type), the end sky the end's; custom skies strip back to
 * the attribute-less black environment. The swapped type is broadcast via
 * ClientboundChangeDimensionTypePacket (the task-28 holder codec restored
 * direct-holder encoding) so the client can rebuild its environment system.
 */
package dev.craftmine.inventory;

import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.registry.CraftmineDataComponents;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import dev.craftmine.network.packet.ClientboundChangeDimensionTypePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;
import net.minecraft.world.timeline.Timeline;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import dev.craftmine.level.DimensionSpecialEffects;
import net.minecraft.world.level.dimension.DimensionType;

public class DimensionControlMenu
extends AbstractContainerMenu
implements dev.craftmine.duck.CraftmineMenuDuck {
    private static final ResourceKey<DimensionType> GENERATED_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, net.minecraft.resources.Identifier.withDefaultNamespace("generated"));
    private static final ResourceKey<DimensionType> THE_END_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, net.minecraft.resources.Identifier.withDefaultNamespace("the_end"));
    /** The attribute entries a sky pick is allowed to swap (the vanilla sky/fog/cloud/light set; no gameplay/audio). */
    private static final List<EnvironmentAttribute<?>> VISUAL_KEYS = List.of(
        EnvironmentAttributes.SKY_COLOR,
        EnvironmentAttributes.FOG_COLOR,
        EnvironmentAttributes.CLOUD_COLOR,
        EnvironmentAttributes.CLOUD_HEIGHT,
        EnvironmentAttributes.AMBIENT_LIGHT_COLOR,
        EnvironmentAttributes.SKY_LIGHT_COLOR,
        EnvironmentAttributes.SKY_LIGHT_FACTOR
    );
    public static final int SKY_SLOT = 0;
    private static final int INV_SLOT_START = 1;
    private static final int INV_SLOT_END = 28;
    private static final int USE_ROW_SLOT_START = 28;
    private static final int USE_ROW_SLOT_END = 37;

    private final Container funkySlots = new SimpleContainer(1){

        @Override
        public void setChanged() {
            super.setChanged();
            DimensionControlMenu.this.slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;
    /** Suppresses updateSky while the constructor prefills the slot. */
    private boolean craftmine$constructing = true;

    public DimensionControlMenu(int n, Inventory inventory, List<Integer> list) {
        this(n, inventory, ContainerLevelAccess.NULL);
    }

    public DimensionControlMenu(int n, Inventory inventory, ContainerLevelAccess containerLevelAccess) {
        super(CraftmineContent.DIMENSION_CONTROL_MENU, n);
        this.access = containerLevelAccess;
        this.addSlot(new Slot(this.funkySlots, 0, 129, 34){

            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.has(CraftmineDataComponents.SKY);
            }
        });
        this.addStandardInventorySlots(inventory, 8, 84);
        containerLevelAccess.execute((level, blockPos) -> {
            // Server-side prefill only (reference sEOV constructor): a sky box
            // with the level's current sky (override, or the dimension default
            // via SkyDefaults) - or an empty slot when the stored sky is the
            // "nothing" sky (the original ships no sky_box item for it). The
            // client copy arrives via the vanilla menu slot sync. NOTE: both
            // ServerLevel and ClientLevel carry the level duck, so the cast is
            // safe on either side.
            if (!level.isClientSide) {
                DimensionSpecialEffects.Sky sky = ((CraftmineLevelDuck)level).craftmine$getActiveSky();
                if (sky != null && sky != DimensionSpecialEffects.NothingSky.INSTANCE) {
                    ItemStack itemStack = new ItemStack(CraftmineContent.SKY_BOX);
                    itemStack.set(CraftmineDataComponents.SKY, sky);
                    this.funkySlots.setItem(0, itemStack);
                }
            }
            this.craftmine$constructing = false;
        });
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == this.funkySlots && !this.craftmine$constructing) {
            this.updateSky();
        }
    }

    private void updateSky() {
        ItemStack itemStack = this.funkySlots.getItem(0);
        // Task 27: an emptied slot (or a slot item without a sky component,
        // which mayPlace makes unreachable) selects the "nothing" sky - a
        // BLACK sky - NOT the dimension default (that is only the prefill for
        // levels never touched by the control).
        DimensionSpecialEffects.Sky sky = itemStack.has(CraftmineDataComponents.SKY) ? itemStack.get(CraftmineDataComponents.SKY) : DimensionSpecialEffects.NothingSky.INSTANCE;
        this.access.execute((level, blockPos) -> {
            CraftmineLevelDuck craftmineLevelDuck = (CraftmineLevelDuck)level;
            if (level.isClientSide) {
                // Client mirror of the sky gate (the sky value itself
                // arrives via ClientboundSetSkyPacket; the immediate
                // setActiveSky below just predicts it). The nothing sky goes
                // through the OVERWORLD gate like every other custom sky: the
                // renderer cancels the vanilla content and clears black.
                // Task 44: same full visual-environment swap as the server,
                // so the prediction matches the incoming packet.
                craftmineLevelDuck.craftmine$setDimensionType(sky == null ? null : Holder.direct(DimensionControlMenu.withSkyGate(level, sky)));
            } else if (sky == null) {
                // Defensive (unreachable via the menu now that an emptied
                // slot selects the nothing sky): restore the level's pre-sky
                // dimension type so the vanilla sky path renders again.
                craftmineLevelDuck.craftmine$restorePreSkyDimensionType();
            } else {
                Holder<DimensionType> swapped = Holder.direct(DimensionControlMenu.withSkyGate(level, sky));
                craftmineLevelDuck.craftmine$applySkyDimensionType(swapped);
                // The swapped type MUST reach the clients: the vanilla sky pass
                // reads the visual environment (attributes/timelines) from the
                // dimension type, and the client's EnvironmentAttributeSystem
                // is built from the type its ClientLevel was created with.
                // The task-28 codec restore made runtime Direct holders
                // encodable (the old "cannot be encoded" caveat is gone).
                if (level instanceof ServerLevel serverLevel) {
                    ClientboundChangeDimensionTypePacket packet = new ClientboundChangeDimensionTypePacket(swapped);
                    for (ServerPlayer serverPlayer : serverLevel.players()) {
                        serverPlayer.connection.send(packet);
                    }
                }
            }
            // Server: persist (SkyData) + broadcast to this level's players.
            // Client: instant prediction, confirmed by the packet.
            craftmineLevelDuck.craftmine$setActiveSky(sky);
        });
    }

    /**
     * The current type with the picked sky's canonical visual environment:
     * the skybox gate (end sky -> END, everything else -> OVERWORLD) plus the
     * attributes/timelines/clock the vanilla 26.3 sky renderer consumes.
     * Structural fields (height, monsters, infiniburn, ...) and the time
     * behavior (hasFixedTime) always stay with the level. Also used by the
     * join resync (PlayerListMixin) for levels with a stored override.
     */
    public static DimensionType withSkyGate(Level level, DimensionSpecialEffects.Sky sky) {
        DimensionType current = level.dimensionType();
        DimensionType.Skybox skybox = sky.type() == DimensionSpecialEffects.SkyType.END ? DimensionType.Skybox.END : DimensionType.Skybox.OVERWORLD;
        if (sky instanceof DimensionSpecialEffects.OverworldSky) {
            DimensionType source = registeredType(level, DimensionControlMenu.GENERATED_TYPE);
            // Overworld sky = the canonical overworld environment (the same
            // one the generated mine levels default to): blue sky/fog/cloud
            // colors, the in_overworld timelines (sun/moon/star angles + the
            // day-night color multipliers) and the overworld clock.
            return withEnvironment(current, skybox,
                source == null ? current.attributes() : copyVisualAttributes(source.attributes()),
                source == null ? current.timelines() : source.timelines(),
                source == null ? current.defaultClock() : source.defaultClock());
        }
        if (sky instanceof DimensionSpecialEffects.EndSky) {
            DimensionType source = registeredType(level, DimensionControlMenu.THE_END_TYPE);
            // End sky = the end's static dark-purple ambience; the end skybox
            // disc is a texture and the end has no visual day tracks, so the
            // level's own timelines/clock are kept.
            return withEnvironment(current, skybox,
                source == null ? current.attributes() : copyVisualAttributes(source.attributes()),
                current.timelines(),
                current.defaultClock());
        }
        // Custom skies (code rain, cube skies, skynorama, nothing): the custom
        // renderer cancels the vanilla sky pass entirely, so the level falls
        // back to the attribute-less environment (default black fog/sky) -
        // which is also what switching BACK to a custom sky must restore.
        return withEnvironment(current, skybox, EnvironmentAttributeMap.EMPTY, HolderSet.empty(), java.util.Optional.empty());
    }

    private static DimensionType withEnvironment(DimensionType current, DimensionType.Skybox skybox,
            EnvironmentAttributeMap attributes, HolderSet<Timeline> timelines, java.util.Optional<Holder<WorldClock>> defaultClock) {
        return new DimensionType(current.hasFixedTime(), current.hasSkyLight(), current.hasCeiling(), current.hasEnderDragonFight(), current.coordinateScale(), current.minY(), current.height(), current.logicalHeight(), current.infiniburn(), current.ambientLight(), current.monsterSettings(),
            skybox,
            current.cardinalLightType(), attributes, timelines, defaultClock);
    }

    /** The registered source dimension type for a sky's visual environment, or null. */
    private static DimensionType registeredType(Level level, ResourceKey<DimensionType> key) {
        try {
            return level.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).getValue(key);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Copies ONLY the visual attribute entries from a source environment -
     * the gameplay/audio attributes (bed rules, ambient sounds, music, ...)
     * belong to the level's own type and must not leak through a sky pick.
     */
    private static EnvironmentAttributeMap copyVisualAttributes(EnvironmentAttributeMap source) {
        EnvironmentAttributeMap.Builder builder = EnvironmentAttributeMap.builder();
        for (EnvironmentAttribute<?> attribute : DimensionControlMenu.VISUAL_KEYS) {
            EnvironmentAttributeMap.Entry<?, ?> entry = source.get(attribute);
            if (entry != null) {
                DimensionControlMenu.copyEntry(builder, attribute, entry);
            }
        }
        return builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <Value, Argument> void copyEntry(EnvironmentAttributeMap.Builder builder, EnvironmentAttribute<Value> attribute, EnvironmentAttributeMap.Entry<?, ?> entry) {
        builder.modify(attribute, (AttributeModifier<Value, Argument>)entry.modifier(), (Argument)entry.argument());
    }

    @Override
    public boolean stillValid(Player player) {
        return DimensionControlMenu.stillValid(this.access, player, CraftmineContent.DIMENSION_CONTROL);
    }

    /**
     * The fork keeps this menu data-less: sky changes sync via
     * ClientboundSetSkyPacket / ClientboundChangeDimensionTypePacket,
     * and the fork's AbstractContainerMenu.updateData default is a no-op.
     * Implemented only so the client's menu-open routing
     * (CraftmineMenuDuck cast) cannot ClassCastException.
     */
    @Override
    public void updateData(List<Integer> list) {
    }

    @Override
    public List<Integer> craftmine$getAdditionalData() {
        return List.of();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int n) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = (Slot)this.slots.get(n);
        if (slot != null && slot.hasItem()) {
            ItemStack itemStack2 = slot.getItem();
            itemStack = itemStack2.copy();
            if (n == 0) {
                if (!this.moveItemStackTo(itemStack2, 1, 37, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(itemStack2, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
                if (n >= 1 && n < 28 ? !this.moveItemStackTo(itemStack2, 28, 37, false) : n >= 28 && n < 37 && !this.moveItemStackTo(itemStack2, 1, 28, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (itemStack2.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (itemStack2.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, itemStack2);
        }
        return itemStack;
    }
}
