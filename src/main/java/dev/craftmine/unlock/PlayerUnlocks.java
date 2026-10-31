package dev.craftmine.unlock;

import com.mojang.datafixers.util.Function4;
import it.unimi.dsi.fastutil.Pair;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.function.TriFunction;
import dev.craftmine.CraftmineConfig;
import dev.craftmine.duck.CraftminePlayerDuck;
import dev.craftmine.entity.pet.PetWolf;
import dev.craftmine.mines.WorldEffects;
import dev.craftmine.registry.CraftmineAttributes;
import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.registry.CraftmineItemTags;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.entity.PotDecorations;



/**
 * Static definition table of every player unlock in the game: the builder chains below
 * populate the {@code player_unlock} registry at bootstrap time (see
 * {@link #bootstrap}), keyed by registry name = unlock key.
 *
 * <p>Field initializers run in declaration order and cross-reference each other via
 * parent/gate holders, so the order below is load-bearing - do not reorder without
 * checking the tree shape. The static tail builds the repetitive chains (learning
 * levels, jump levels) with loops and registers the unoriginal QoL tab.</p>
 */
public abstract class PlayerUnlocks {
    private static final String EXCLUSIVE_PET_KEY = "exclusive_pet";
    private static final List<Item> BUNDLE_OF_FORTUNE_CONTENTS = List.of(Items.DIRT, Items.DIAMOND, Items.WOODEN_SWORD, Items.MACE, Items.TNT, Items.POISONOUS_POTATO, Items.NETHERITE_PICKAXE, Items.REDSTONE, Items.GOLDEN_APPLE, Items.CAKE, Items.BONE, Items.MUSIC_DISC_CAT);
    public static Holder<PlayerUnlock> EXPLORATION = PlayerUnlock.root("exploration").withIcon(Items.LEATHER_BOOTS).givesAttributeModifier(Attributes.MOVEMENT_SPEED, 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).givesAttributeModifier(Attributes.SNEAKING_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).register();
    public static Holder<PlayerUnlock> ENDER_PEARL_STARTER = PlayerUnlock.child("ender_pearl_starter", EXPLORATION).withIcon(Items.ENDER_PEARL).givesItemStackInMine(Items.ENDER_PEARL.getDefaultInstance().copyWithCount(5)).withVisibility(PlayerUnlock.UnlockVisibility.MYSTERY).becomesVisibleWhen(UnlockCondition.playerKilledEntity(EntityTypes.ENDERMAN)).withPrice(5).register();
    public static Holder<PlayerUnlock> MORE_PEARLS = PlayerUnlock.child("more_pearls", ENDER_PEARL_STARTER).withIcon(Items.ENDER_PEARL).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(ENDER_PEARL_STARTER)).givesItemStackInMine(Items.ENDER_PEARL.getDefaultInstance().copyWithCount(10)).withPrice(10).register();
    public static Holder<PlayerUnlock> MORE_MORE_PEARLS = PlayerUnlock.child("more_more_pearls", MORE_PEARLS).withIcon(Items.ENDER_PEARL).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(MORE_PEARLS)).givesItemStackInMine(Items.ENDER_PEARL.getDefaultInstance().copyWithCount(25)).withPrice(25).register();
    public static Holder<PlayerUnlock> MOVEMENT_SPEED_1 = PlayerUnlock.child("speed_1", EXPLORATION).withIcon(Items.FURNACE_MINECART).givesAttributeModifier(Attributes.MOVEMENT_SPEED, 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).withPrice(5).register();
    public static Holder<PlayerUnlock> AMPHIBIAN = PlayerUnlock.child("amphibian", MOVEMENT_SPEED_1).withIcon(Items.SALMON).givesAttributeModifier(Attributes.WATER_MOVEMENT_EFFICIENCY, 0.5, AttributeModifier.Operation.ADD_VALUE).withPrice(5).register();
    public static Holder<PlayerUnlock> MOVEMENT_SPEED_2 = PlayerUnlock.child("speed_2", MOVEMENT_SPEED_1).withIcon(Items.MINECART).givesAttributeModifier(Attributes.MOVEMENT_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).withPrice(10).register();
    public static Holder<PlayerUnlock> MOVEMENT_SPEED_3 = PlayerUnlock.child("speed_3", MOVEMENT_SPEED_2).withIcon(Items.CHEST_MINECART).givesAttributeModifier(Attributes.MOVEMENT_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).withPrice(20).register();
    public static Holder<PlayerUnlock> SHAZBOOT = PlayerUnlock.child("shazboots", MOVEMENT_SPEED_3).withIcon(CraftmineContent.SHAZBOOTS).givesEnchantedItemInMine(CraftmineContent.SHAZBOOTS).withPrice(40).register();
    public static Holder<PlayerUnlock> PATHFINDER = PlayerUnlock.child("pathfinder", EXPLORATION).withIcon(CraftmineContent.EXIT_EYE).withPrice(10).register();
    public static Holder<PlayerUnlock> LODESTONE_EXITS = PlayerUnlock.child("lodestone_exits", PATHFINDER).withIcon(Items.LODESTONE).withPrice(15).register();
    public static Holder<PlayerUnlock> STARTER_COMPASS = PlayerUnlock.child("starter_compass", LODESTONE_EXITS).withIcon(Items.COMPASS).withPrice(20).givesItemInMine(Items.COMPASS).register();
    public static Holder<PlayerUnlock> MOVEMENT_SNEAK_1 = PlayerUnlock.child("sneak_1", MOVEMENT_SPEED_1).withIcon(Items.APPLE).givesAttributeModifier(Attributes.SNEAKING_SPEED, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).withPrice(2).register();
    public static Holder<PlayerUnlock> MOVEMENT_SNEAK_2 = PlayerUnlock.child("sneak_2", MOVEMENT_SNEAK_1).withIcon(Items.GOLDEN_APPLE).givesAttributeModifier(Attributes.SNEAKING_SPEED, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).withPrice(4).register();
    public static Holder<PlayerUnlock> MOVEMENT_BOOST = PlayerUnlock.child("movement_boost", MOVEMENT_SPEED_1).withIcon(Items.COOKIE).givesEffectInMine(MobEffects.SPEED, 120, 1).withPrice(5).register();
    public static Holder<PlayerUnlock> CRAFTING = PlayerUnlock.root("crafting").withIcon(Items.CRAFTING_TABLE).register();
    public static Holder<PlayerUnlock> SMELTER_1 = PlayerUnlock.child("smelter_1", CRAFTING).withIcon(Items.FURNACE).givesItemStackInMine(Items.FURNACE.getDefaultInstance()).givesItemStackInMine(Items.COAL.getDefaultInstance().copyWithCount(32)).withPrice(4).register();
    public static Holder<PlayerUnlock> SMELTER_2 = PlayerUnlock.child("smelter_2", SMELTER_1).withIcon(Items.BLAST_FURNACE).givesItemStackInMine(Items.BLAST_FURNACE.getDefaultInstance()).givesItemStackInMine(Items.CHARCOAL.getDefaultInstance().copyWithCount(64)).disablesOtherUnlock(SMELTER_1).withPrice(8).register();
    public static Holder<PlayerUnlock> SMELT_VALUE_1 = PlayerUnlock.child("smelt_value_1", SMELTER_2).withIcon(Items.GOLD_INGOT).withPrice(15).modifyExperienceGainForItem(CraftmineItemTags.INGOT, 2.0f).register();
    public static Holder<PlayerUnlock> SMELT_VALUE_2 = PlayerUnlock.child("smelt_value_2", SMELT_VALUE_1).withIcon(Items.NETHERITE_INGOT).withPrice(15).modifyExperienceGainForItem(CraftmineItemTags.INGOT, 4.0f).register();
    public static Holder<PlayerUnlock> INVENTORY_SLOTS_1 = PlayerUnlock.child("inventory_slots_1", CRAFTING).withIcon(Items.CHEST).withPrice(2).register();
    public static Holder<PlayerUnlock> INVENTORY_SLOTS_2 = PlayerUnlock.child("inventory_slots_2", INVENTORY_SLOTS_1).withIcon(Items.CHEST).withPrice(15).register();
    public static Holder<PlayerUnlock> INVENTORY_SLOTS_3 = PlayerUnlock.child("inventory_slots_3", INVENTORY_SLOTS_2).withIcon(Items.CHEST).withPrice(30).register();
    public static Holder<PlayerUnlock> BUNDLE_OF_FORTUNE = PlayerUnlock.child("bundle_of_fortune", INVENTORY_SLOTS_3).withIcon(Items.BUNDLE).withPrice(50).givesItemStackInMine(serverPlayer -> {
        ItemStack itemStack = Items.BUNDLE.getDefaultInstance().copy();
        Item item = BUNDLE_OF_FORTUNE_CONTENTS.get(serverPlayer.getRandom().nextInt(BUNDLE_OF_FORTUNE_CONTENTS.size()));
        itemStack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStackTemplate(item))));
        return itemStack;
    }).register();
    public static Holder<PlayerUnlock> INVENTORY_CRAFTING = PlayerUnlock.child("inventory_crafting", CRAFTING).withIcon(Items.STICK).withPrice(2).register();
    public static Holder<PlayerUnlock> INVENTORY_CRAFTING_3X3 = PlayerUnlock.child("inventory_crafting_3x3", INVENTORY_CRAFTING).withIcon(Items.HOPPER).withPrice(33).withVisibility(PlayerUnlock.UnlockVisibility.MYSTERY).becomesVisibleWhen(UnlockCondition.craftedItem((ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> true)).register();
    public static Holder<PlayerUnlock> CRAFTING_EFFICIENCY = PlayerUnlock.child("crafting_efficiency", INVENTORY_CRAFTING_3X3).withIcon(Items.COMPOSTER).withPrice(50).withVisibility(PlayerUnlock.UnlockVisibility.MYSTERY).becomesVisibleWhen(UnlockCondition.craftedItem((ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(Items.CRAFTING_TABLE))).register();
    public static Holder<PlayerUnlock> STARTER_CRAFTING = PlayerUnlock.child("starter_crafting", INVENTORY_CRAFTING).withIcon(Items.CRAFTING_TABLE).givesItemInMine(Items.CRAFTING_TABLE).withPrice(10).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(INVENTORY_CRAFTING)).register();
    public static Holder<PlayerUnlock> ENCHANTING = PlayerUnlock.child("enchanting", INVENTORY_SLOTS_1).withIcon(Items.ENCHANTING_TABLE).givesItemInMine(Items.ENCHANTING_TABLE).withPrice(5).register();
    public static Holder<PlayerUnlock> MAGIC_KIT = PlayerUnlock.child("magic_starter_kit", ENCHANTING).withIcon(Items.LAPIS_LAZULI).givesItemStackInMine(Items.LAPIS_LAZULI.getDefaultInstance().copyWithCount(3), Items.BOOKSHELF.getDefaultInstance().copyWithCount(5)).withPrice(15).register();
    public static Holder<PlayerUnlock> MASTER_KIT = PlayerUnlock.child("magic_master_kit", MAGIC_KIT).withIcon(Items.ENCHANTED_BOOK).givesItemStackInMine(Items.LAPIS_LAZULI.getDefaultInstance().copyWithCount(6), Items.BOOKSHELF.getDefaultInstance().copyWithCount(10)).disablesOtherUnlock(MAGIC_KIT).withPrice(25).register();
    public static Holder<PlayerUnlock> RUNEMASTER = PlayerUnlock.child("runemaster", MASTER_KIT).withIcon(Items.BOOKSHELF).givesItemStackInMine(Items.LAPIS_LAZULI.getDefaultInstance().copyWithCount(9), Items.BOOKSHELF.getDefaultInstance().copyWithCount(15)).disablesOtherUnlock(MASTER_KIT, MAGIC_KIT).withPrice(35).register();
    public static Holder<PlayerUnlock> FIRE_WAND = PlayerUnlock.child("fire_wand", RUNEMASTER).withIcon(CraftmineContent.FIRE_WAND).givesItemInMine(CraftmineContent.FIRE_WAND).withVisibility(PlayerUnlock.UnlockVisibility.MYSTERY).becomesVisibleWhen(UnlockCondition.playerKilledEntity((ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> entity instanceof Blaze)).withPrice(50).register();
    public static Holder<PlayerUnlock> WIND_WAND = PlayerUnlock.child("wind_wand", RUNEMASTER).withIcon(CraftmineContent.WIND_WAND).givesItemInMine(CraftmineContent.WIND_WAND).withVisibility(PlayerUnlock.UnlockVisibility.MYSTERY).becomesVisibleWhen(UnlockCondition.playerKilledEntity((ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> entity instanceof Breeze)).withPrice(50).register();
    public static Holder<PlayerUnlock> TELEPORTATION_WAND = PlayerUnlock.child("teleportation_wand", RUNEMASTER).withIcon(CraftmineContent.TELEPORTATION_WAND).givesItemInMine(CraftmineContent.TELEPORTATION_WAND).withVisibility(PlayerUnlock.UnlockVisibility.MYSTERY).becomesVisibleWhen(UnlockCondition.playerKilledEntity((ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> entity instanceof Enderman)).withPrice(50).register();
    public static Holder<PlayerUnlock> EFFICIENT_ENCHANTING = PlayerUnlock.child("efficient_enchanting", MASTER_KIT).withIcon(Items.KNOWLEDGE_BOOK).withPrice(35).register();
    public static Holder<PlayerUnlock> ALCHEMY = PlayerUnlock.child("alchemy", INVENTORY_SLOTS_1).withIcon(Items.BREWING_STAND).givesItemInMine(Items.BREWING_STAND).withPrice(15).register();
    public static Holder<PlayerUnlock> MISE_EN_PLACE = PlayerUnlock.child("mise_en_place", ALCHEMY).withIcon(Items.GLASS_BOTTLE).givesItemStackInMine(Items.GLASS_BOTTLE.getDefaultInstance().copyWithCount(16), Items.NETHER_WART.getDefaultInstance().copyWithCount(4), Items.GLISTERING_MELON_SLICE.getDefaultInstance().copyWithCount(4), Items.SUGAR.getDefaultInstance().copyWithCount(4), Items.BLAZE_POWDER.getDefaultInstance().copyWithCount(2)).withPrice(30).register();
    public static Holder<PlayerUnlock> ALLEZ_CUISINE = PlayerUnlock.child("allez_cuisine", MISE_EN_PLACE).withIcon(Items.SPIDER_EYE).givesItemStackInMine(Items.REDSTONE.getDefaultInstance().copyWithCount(4), Items.GLOWSTONE_DUST.getDefaultInstance().copyWithCount(4), Items.GUNPOWDER.getDefaultInstance().copyWithCount(4), Items.SPIDER_EYE.getDefaultInstance().copyWithCount(4)).withPrice(35).register();
    public static Holder<PlayerUnlock> MASS_PRODUCTION = PlayerUnlock.child("mass_production", MISE_EN_PLACE).withIcon(Items.EXPERIENCE_BOTTLE).withPrice(35).register();
    public static Holder<PlayerUnlock> GATHERER = PlayerUnlock.root("gatherer").withIcon(Items.SWEET_BERRIES).register();
    public static Holder<PlayerUnlock> PICKUP_AREA_SIZE = PlayerUnlock.child("pickup_area_size", GATHERER).withIcon(Items.FISHING_ROD).withPrice(25).givesAttributeModifier(CraftmineAttributes.PICKUP_AREA_SIZE, 9.0, AttributeModifier.Operation.ADD_VALUE).register();
    public static Holder<PlayerUnlock> DIRT_ENJOYER_1 = PlayerUnlock.child("dirt_enjoyer_1", GATHERER).withIcon(Items.DIRT).modifyExperienceGainForItem(ItemTags.DIRT, 2.0f).withPrice(2).register();
    public static Holder<PlayerUnlock> APPRENTICE_SHOVELER = PlayerUnlock.child("apprentice_shoveler", DIRT_ENJOYER_1).withIcon(Items.WOODEN_SHOVEL).withPrice(5).givesItemInMine(Items.WOODEN_SHOVEL).register();
    public static Holder<PlayerUnlock> JOURNEYMAN_SHOVELER = PlayerUnlock.child("journeyman_shoveler", APPRENTICE_SHOVELER).withIcon(Items.STONE_SHOVEL).withPrice(15).givesItemInMine(Items.STONE_SHOVEL).disablesOtherUnlock(APPRENTICE_SHOVELER).register();
    public static Holder<PlayerUnlock> EXPERT_SHOVELER = PlayerUnlock.child("expert_shoveler", JOURNEYMAN_SHOVELER).withIcon(Items.IRON_SHOVEL).withPrice(25).givesItemInMine(Items.IRON_SHOVEL).disablesOtherUnlock(APPRENTICE_SHOVELER, JOURNEYMAN_SHOVELER).register();
    public static Holder<PlayerUnlock> MASTER_SHOVELER = PlayerUnlock.child("master_shoveler", EXPERT_SHOVELER).withIcon(Items.DIAMOND_SHOVEL).withPrice(35).givesItemInMine(Items.DIAMOND_SHOVEL).disablesOtherUnlock(APPRENTICE_SHOVELER, JOURNEYMAN_SHOVELER, EXPERT_SHOVELER).register();
    public static Holder<PlayerUnlock> DIRT_ENJOYER_2 = PlayerUnlock.child("dirt_enjoyer_2", DIRT_ENJOYER_1).withIcon(Items.DIRT).modifyExperienceGainForItem(ItemTags.DIRT, 4.0f).disablesOtherUnlock(DIRT_ENJOYER_1).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(DIRT_ENJOYER_1)).withPrice(5).register();
    public static Holder<PlayerUnlock> DIRT_ENJOYER_3 = PlayerUnlock.child("dirt_enjoyer_3", DIRT_ENJOYER_2).withIcon(Items.DIRT).modifyExperienceGainForItem(ItemTags.DIRT, 6.0f).disablesOtherUnlock(DIRT_ENJOYER_2).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(DIRT_ENJOYER_2)).withPrice(10).register();
    public static Holder<PlayerUnlock> DIRT_DESTROYER_1 = PlayerUnlock.child("dirt_destroyer_1", DIRT_ENJOYER_3).withIcon(Items.DIRT).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(DIRT_ENJOYER_3)).withPrice(10).register();
    public static Holder<PlayerUnlock> DIRT_DESTROYER_2 = PlayerUnlock.child("dirt_destroyer_2", DIRT_DESTROYER_1).withIcon(Items.DIRT).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(DIRT_DESTROYER_1)).withPrice(20).register();
    public static Holder<PlayerUnlock> DIRT_CONNOISSEUR = PlayerUnlock.child("dirt_connoisseur", DIRT_DESTROYER_2).withIcon(Items.DIRT).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(DIRT_DESTROYER_2)).withPrice(10).register();
    public static Holder<PlayerUnlock> HUNTER = PlayerUnlock.child("hunter", GATHERER).withIcon(Items.PORKCHOP).register();
    public static Holder<PlayerUnlock> CAMPFIRE = PlayerUnlock.child("campfire", HUNTER).withIcon(Items.CAMPFIRE).givesItemInMine(Items.CAMPFIRE).withPrice(5).register();
    public static Holder<PlayerUnlock> YOU_ARE_THE_CAMPFIRE = PlayerUnlock.child("you_are_the_campfire", CAMPFIRE).withIcon(Items.BLAZE_POWDER).withPrice(50).disablesOtherUnlock(CAMPFIRE).register();
    public static Holder<PlayerUnlock> MINING = PlayerUnlock.child("mining", GATHERER).withIcon(Items.WOODEN_PICKAXE).givesAttributeModifier(Attributes.MINING_EFFICIENCY, 0.5, AttributeModifier.Operation.ADD_VALUE).givesAttributeModifier(Attributes.SUBMERGED_MINING_SPEED, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).withPrice(2).register();
    public static Holder<PlayerUnlock> SUB_MINING_EFFICIENCY = PlayerUnlock.child("sub_mining_efficiency", MINING).withCustomIcon("water_mining").withPrice(4).withVisibility(PlayerUnlock.UnlockVisibility.MYSTERY).becomesVisibleWhen(UnlockCondition.mineCompletedWith(true, WorldEffects.WATER_WORLD)).givesAttributeModifier(Attributes.SUBMERGED_MINING_SPEED, 10.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).register();
    public static Holder<PlayerUnlock> FISHING = PlayerUnlock.child("fishing", GATHERER).withIcon(Items.SALMON).withPrice(2).givesAttributeModifier(Attributes.LUCK, 1.0, AttributeModifier.Operation.ADD_VALUE).register();
    public static Holder<PlayerUnlock> FISHING_ROD = PlayerUnlock.child("fishing_rod", FISHING).withIcon(Items.FISHING_ROD).withPrice(5).givesItemInMine(Items.FISHING_ROD).register();
    public static Holder<PlayerUnlock> TRIDENT = PlayerUnlock.child("trident", FISHING_ROD).withIcon(Items.TRIDENT).withPrice(25).givesItemInMine(Items.TRIDENT).register();
    public static Holder<PlayerUnlock> POSEIDON = PlayerUnlock.child("poseidon", TRIDENT).withIcon(Items.TRIDENT).withPrice(45).disablesOtherUnlock(TRIDENT).givesEnchantedItemInMine(Items.TRIDENT, Pair.of(Enchantments.LOYALTY, 3), Pair.of(Enchantments.UNBREAKING, 3)).givesItemInMine(Items.TURTLE_HELMET).register();
    public static Holder<PlayerUnlock> MINING_EFFICIENCY = PlayerUnlock.child("mining_efficiency", MINING).withIcon(Items.IRON_ORE).withPrice(5).givesAttributeModifier(Attributes.MINING_EFFICIENCY, 1.0, AttributeModifier.Operation.ADD_VALUE).register();
    public static Holder<PlayerUnlock> MINING_EFFICIENCY_2 = PlayerUnlock.child("mining_efficiency_2", MINING_EFFICIENCY).withIcon(Items.GOLD_NUGGET).withPrice(10).givesAttributeModifier(Attributes.MINING_EFFICIENCY, 1.0, AttributeModifier.Operation.ADD_VALUE).register();
    public static Holder<PlayerUnlock> MINING_EFFICIENCY_3 = PlayerUnlock.child("mining_efficiency_3", MINING_EFFICIENCY_2).withIcon(Items.GOLD_INGOT).withPrice(10).givesAttributeModifier(Attributes.MINING_EFFICIENCY, 1.0, AttributeModifier.Operation.ADD_VALUE).register();
    public static Holder<PlayerUnlock> STARTER_PICK = PlayerUnlock.child("starter_pick", MINING_EFFICIENCY_3).withIcon(Items.IRON_PICKAXE).givesItemInMine(Items.IRON_PICKAXE).withPrice(10).register();
    public static Holder<PlayerUnlock> SILKY_PICK = PlayerUnlock.child("silky_pick", STARTER_PICK).withIcon(Items.GOLDEN_PICKAXE).disablesOtherUnlock(STARTER_PICK).givesEnchantedItemInMine(Items.GOLDEN_PICKAXE, Pair.of(Enchantments.SILK_TOUCH, 1), Pair.of(Enchantments.UNBREAKING, 1)).withPrice(15).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(STARTER_PICK)).register();
    public static Holder<PlayerUnlock> ORE_SEEKER_1 = PlayerUnlock.child("ore_seeker_1", SILKY_PICK).withIcon(Items.GOLD_ORE).withPrice(20).modifyExperienceGainForItem(CraftmineItemTags.ORE, 2.0f).register();
    public static Holder<PlayerUnlock> ORE_SEEKER_2 = PlayerUnlock.child("ore_seeker_2", ORE_SEEKER_1).withIcon(Items.DIAMOND_ORE).withPrice(20).modifyExperienceGainForItem(CraftmineItemTags.ORE, 4.0f).register();
    public static Holder<PlayerUnlock> LUCKY_PICK = PlayerUnlock.child("lucky_pick", STARTER_PICK).withIcon(Items.DIAMOND_PICKAXE).disablesOtherUnlock(STARTER_PICK).givesEnchantedItemInMine(Items.DIAMOND_PICKAXE, Pair.of(Enchantments.FORTUNE, 3), Pair.of(Enchantments.UNBREAKING, 1), Pair.of(Enchantments.EFFICIENCY, 4)).withPrice(20).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(STARTER_PICK)).register();
    public static Holder<PlayerUnlock> RARE_EARTH_SPECIALIST = PlayerUnlock.child("rare_earth_specialist", LUCKY_PICK).withIcon(Items.DIAMOND).withPrice(20).modifyExperienceGainForItem(Items.DIAMOND, 4.0f).modifyExperienceGainForItem(Items.REDSTONE, 4.0f).modifyExperienceGainForItem(Items.LAPIS_LAZULI, 4.0f).register();
    public static Holder<PlayerUnlock> COMBATANT = PlayerUnlock.root("combatant").withIcon(Items.NETHERITE_SWORD).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesAttributeModifier(Attributes.ATTACK_DAMAGE, 2.0, AttributeModifier.Operation.ADD_VALUE).register();
    public static Holder<PlayerUnlock> SORCERER_SUPREME = PlayerUnlock.child("sorcerer_supreme", COMBATANT).withIcon(Items.SOUL_CAMPFIRE).withPrice(10).register();
    public static Holder<PlayerUnlock> WING_GUARDIAN = PlayerUnlock.child("wing_guardian_lift_off_to_the_sky", EXPLORATION).withIcon(Items.FEATHER).withPrice(10).withVisibility(PlayerUnlock.UnlockVisibility.MYSTERY).becomesVisibleWhen(UnlockCondition.playerTookDamage((ServerLevel serverLevel, ServerPlayer serverPlayer, DamageSource damageSource, Float f) -> ((CraftminePlayerDuck)serverPlayer).isUnlocked(SORCERER_SUPREME) && damageSource.is(DamageTypeTags.IS_FALL) && f > 10.0f)).register();
    public static Holder<PlayerUnlock> DRAGON_FIRE = PlayerUnlock.child("dragon_fire", FIRE_WAND).withIcon(Items.DRAGON_BREATH).withPrice(10).withVisibility(PlayerUnlock.UnlockVisibility.MYSTERY).becomesVisibleWhen(UnlockCondition.playerTookDamage((ServerLevel serverLevel, ServerPlayer serverPlayer, DamageSource damageSource, Float f) -> {
        // Task 35 (BugMine "Obtainable Dragon Fire", always-on): the dragon
        // fireball never hits the player directly - damage arrives from its
        // AREA_EFFECT_CLOUD, whose attacker is the dragon (or the fireball).
        // Accept the cloud path in addition to the direct fireball hit.
        if (((CraftminePlayerDuck)serverPlayer).isUnlocked(SORCERER_SUPREME) && damageSource.getEntity() != null && damageSource.getEntity().getType() == EntityTypes.DRAGON_FIREBALL) {
            return true;
        }
        return ((CraftminePlayerDuck)serverPlayer).isUnlocked(SORCERER_SUPREME)
            && damageSource.getDirectEntity() != null
            && damageSource.getDirectEntity().getType() == EntityTypes.AREA_EFFECT_CLOUD
            && damageSource.getEntity() != null
            && (damageSource.getEntity().getType() == EntityTypes.ENDER_DRAGON || damageSource.getEntity().getType() == EntityTypes.DRAGON_FIREBALL);
        })).register();
    public static Holder<PlayerUnlock> ARCHER = PlayerUnlock.child("archer", COMBATANT).withIcon(Items.BOW).withPrice(10).givesItemInMine(Items.BOW).register();
    public static Holder<PlayerUnlock> QUIVER = PlayerUnlock.child("quiver", ARCHER).withIcon(Items.ARROW).withPrice(10).givesItemStackInMine(Items.ARROW.getDefaultInstance().copyWithCount(16)).register();
    public static Holder<PlayerUnlock> FLETCHER = PlayerUnlock.child("fletcher", QUIVER).withIcon(Items.FLETCHING_TABLE).withPrice(20).disablesOtherUnlock(QUIVER).givesItemStackInMine(Items.ARROW.getDefaultInstance().copyWithCount(64)).register();
    public static Holder<PlayerUnlock> ARROWVERSE = PlayerUnlock.child("arrowverse", FLETCHER).withIcon(Items.TIPPED_ARROW).withPrice(30).register();
    public static Holder<PlayerUnlock> TRACKER = PlayerUnlock.child("tracker", QUIVER).withIcon(Items.SPECTRAL_ARROW).withPrice(20).givesItemStackInMine(Items.SPECTRAL_ARROW.getDefaultInstance().copyWithCount(16)).register();
    public static Holder<PlayerUnlock> ARMAMENTS = PlayerUnlock.child("armaments", COMBATANT).withIcon(Items.IRON_HELMET).withPrice(10).register();
    public static Holder<PlayerUnlock> DECKED_OUT = PlayerUnlock.child("decked_out", ARMAMENTS).withIcon(Items.LEATHER_CHESTPLATE).withPrice(20).givesItemInMine(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS).register();
    public static Holder<PlayerUnlock> FULL_METAL = PlayerUnlock.child("full_metal", DECKED_OUT).withIcon(Items.IRON_CHESTPLATE).withPrice(40).disablesOtherUnlock(DECKED_OUT).givesItemInMine(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS).register();
    public static Holder<PlayerUnlock> STARTER_SHIELD = PlayerUnlock.child("starter_shield", COMBATANT).withIcon(Items.SHIELD).withPrice(15).givesItemInMine(Items.SHIELD).register();
    public static Holder<PlayerUnlock> SHIELD_BASH = PlayerUnlock.child("shield_bash", STARTER_SHIELD).withIcon(Items.SKELETON_SKULL).withPrice(15).register();
    public static Holder<PlayerUnlock> FIRE_SHIELD = PlayerUnlock.child("fire_shield", SHIELD_BASH).withIcon(Items.POTION).withPrice(15).withVisibility(PlayerUnlock.UnlockVisibility.MYSTERY).becomesVisibleWhen(UnlockCondition.playerTookDamage((ServerLevel serverLevel, ServerPlayer serverPlayer, DamageSource damageSource, Float f) -> ((CraftminePlayerDuck)serverPlayer).isUnlocked(SORCERER_SUPREME) && damageSource.is(DamageTypeTags.IS_FIRE))).register();
    public static Holder<PlayerUnlock> CRUSHER = PlayerUnlock.child("crusher", SHIELD_BASH).withIcon(Items.MACE).withPrice(50).givesItemInMine(Items.MACE).register();
    public static Holder<PlayerUnlock> STARTER_APPLES = PlayerUnlock.child("starter_apples", ARMAMENTS).withIcon(Items.APPLE).withPrice(15).givesItemStackInMine(Items.APPLE.getDefaultInstance().copyWithCount(5)).register();
    public static Holder<PlayerUnlock> MORE_STARTER_APPLES = PlayerUnlock.child("more_starter_apples", STARTER_APPLES).withIcon(Items.APPLE).withPrice(15).givesItemStackInMine(Items.APPLE.getDefaultInstance().copyWithCount(10)).disablesOtherUnlock(STARTER_APPLES).register();
    public static Holder<PlayerUnlock> GOLDEN_STARTER_APPLES = PlayerUnlock.child("golden_starter_apples", MORE_STARTER_APPLES).withIcon(Items.GOLDEN_APPLE).withPrice(15).givesItemStackInMine(Items.GOLDEN_APPLE.getDefaultInstance().copyWithCount(5)).disablesOtherUnlock(MORE_STARTER_APPLES).register();
    public static Holder<PlayerUnlock> STARTER_SWORD = PlayerUnlock.child("starter_sword", COMBATANT).withIcon(Items.WOODEN_SWORD).withPrice(5).givesItemInMine(Items.WOODEN_SWORD).register();
    public static Holder<PlayerUnlock> STARTER_SWORD_IRON = PlayerUnlock.child("starter_sword_iron", STARTER_SWORD).withIcon(Items.IRON_SWORD).withPrice(25).givesEnchantedItemInMine(Items.IRON_SWORD, Pair.of(Enchantments.SHARPNESS, 3), Pair.of(Enchantments.SWEEPING_EDGE, 1)).disablesOtherUnlock(STARTER_SWORD).register();
    public static Holder<PlayerUnlock> BETTER_STARTER_SWORD = PlayerUnlock.child("better_starter_sword", STARTER_SWORD_IRON).withIcon(Items.DIAMOND_SWORD).withPrice(30).givesEnchantedItemInMine(Items.DIAMOND_SWORD, Pair.of(Enchantments.SHARPNESS, 3), Pair.of(Enchantments.SWEEPING_EDGE, 2), Pair.of(Enchantments.LOOTING, 2)).disablesOtherUnlock(STARTER_SWORD, STARTER_SWORD_IRON).register();
    public static Holder<PlayerUnlock> BEST_STARTER_SWORD = PlayerUnlock.child("best_starter_sword", BETTER_STARTER_SWORD).withIcon(Items.NETHERITE_SWORD).withPrice(50).givesEnchantedItemInMine(Items.NETHERITE_SWORD, Pair.of(Enchantments.SHARPNESS, 5), Pair.of(Enchantments.SWEEPING_EDGE, 2), Pair.of(Enchantments.LOOTING, 2)).disablesOtherUnlock(STARTER_SWORD, STARTER_SWORD_IRON, BETTER_STARTER_SWORD).register();
    public static Holder<PlayerUnlock> CHOPPER = PlayerUnlock.child("chopper", COMBATANT).withIcon(Items.WOODEN_AXE).withPrice(5).givesItemInMine(Items.WOODEN_AXE).register();
    public static Holder<PlayerUnlock> VIKING = PlayerUnlock.child("viking", CHOPPER).withIcon(Items.IRON_AXE).withPrice(25).givesItemInMine(Items.IRON_AXE, Items.OAK_BOAT).disablesOtherUnlock(CHOPPER).register();
    public static Holder<PlayerUnlock> BERSERKER = PlayerUnlock.child("berserker", VIKING).withIcon(Items.DIAMOND_AXE).withPrice(35).givesItemInMine(Items.DIAMOND_AXE, Items.DIAMOND_AXE, Items.OAK_BOAT).disablesOtherUnlock(CHOPPER, VIKING).register();
    public static Holder<PlayerUnlock> VARANGIAN = PlayerUnlock.child("varangian", BERSERKER).withIcon(Items.NETHERITE_AXE).withPrice(50).givesEnchantedItemInMine(Items.NETHERITE_AXE, Pair.of(Enchantments.SHARPNESS, 5), Pair.of(Enchantments.LOOTING, 3)).givesItemInMine(Items.OAK_BOAT).disablesOtherUnlock(CHOPPER, VIKING, BERSERKER).register();
    public static Holder<PlayerUnlock> PETS = PlayerUnlock.root("pets").withCustomIcon("pets_icon").withPrice(0).givesItemInMine(Items.EGG).register();
    public static Holder<PlayerUnlock> PET_CHICKEN_IN_WORLD = PlayerUnlock.child("pet_chicken", PETS).withIcon(Items.CHICKEN_SPAWN_EGG).withPrice(10).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesPetInWorld(CraftmineContent.PET_CHICKEN).withExclusiveApplication("exclusive_pet").register();
    public static Holder<PlayerUnlock> CAN_PET = PlayerUnlock.child("pet_the_animal", PET_CHICKEN_IN_WORLD).withIcon(() -> {
        ItemStack itemStack = Items.DECORATED_POT.getDefaultInstance();
        itemStack.set(DataComponents.POT_DECORATIONS, new PotDecorations(Optional.of(new ItemStackTemplate(Items.HEART_POTTERY_SHERD)), Optional.of(new ItemStackTemplate(Items.HEART_POTTERY_SHERD)), Optional.of(new ItemStackTemplate(Items.HEART_POTTERY_SHERD)), Optional.of(new ItemStackTemplate(Items.HEART_POTTERY_SHERD))));
        return itemStack;
    }).withPrice(10).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).register();
    public static Holder<PlayerUnlock> PET_ARMADILLO_IN_WORLD = PlayerUnlock.child("pet_armadillo", PET_CHICKEN_IN_WORLD).withIcon(Items.ARMADILLO_SPAWN_EGG).withPrice(30).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesPetInWorld(CraftmineContent.PET_ARMADILLO).withExclusiveApplication("exclusive_pet").register();
    public static Holder<PlayerUnlock> PET_FROG_IN_WORLD = PlayerUnlock.child("pet_frog", PET_CHICKEN_IN_WORLD).withIcon(Items.FROG_SPAWN_EGG).withPrice(15).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesPetInWorld(CraftmineContent.PET_FROG).withExclusiveApplication("exclusive_pet").register();
    public static Holder<PlayerUnlock> PET_COW_IN_WORLD = PlayerUnlock.child("pet_cow", PET_CHICKEN_IN_WORLD).withIcon(Items.COW_SPAWN_EGG).withPrice(15).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesPetInWorld(CraftmineContent.PET_COW).withExclusiveApplication("exclusive_pet").register();
    public static Holder<PlayerUnlock> PET_MOOSHROOM_IN_WORLD = PlayerUnlock.child("pet_mooshroom", PET_COW_IN_WORLD).withIcon(Items.MOOSHROOM_SPAWN_EGG).withPrice(20).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(PET_COW_IN_WORLD)).givesPetInWorld(CraftmineContent.PET_MUSHROOM_COW).withExclusiveApplication("exclusive_pet").register();
    public static Holder<PlayerUnlock> PET_AXOLOTL_IN_WORD = PlayerUnlock.child("pet_axolotl", PET_FROG_IN_WORLD).withIcon(Items.AXOLOTL_SPAWN_EGG).withPrice(15).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesPetInWorld(CraftmineContent.PET_AXOLOTL).withExclusiveApplication("exclusive_pet").register();
    public static Holder<PlayerUnlock> PET_TURTLE_IN_WORLD = PlayerUnlock.child("pet_turtle", PET_AXOLOTL_IN_WORD).withIcon(Items.TURTLE_SPAWN_EGG).withPrice(20).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesPetInWorld(CraftmineContent.PET_TURTLE).withExclusiveApplication("exclusive_pet").register();
    public static Holder<PlayerUnlock> PET_CAT_IN_WORLD = PlayerUnlock.child("pet_cat", PET_CHICKEN_IN_WORLD).withIcon(Items.CAT_SPAWN_EGG).withPrice(15).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesPetInWorld(CraftmineContent.PET_CAT).withExclusiveApplication("exclusive_pet").register();
    public static Holder<PlayerUnlock> PET_BEE_IN_WORLD = PlayerUnlock.child("pet_bee", PET_CHICKEN_IN_WORLD).withIcon(Items.BEE_SPAWN_EGG).withPrice(15).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesPetInWorld(CraftmineContent.PET_BEE).withExclusiveApplication("exclusive_pet").register();
    public static Holder<PlayerUnlock> PET_FOX_IN_WORLD = PlayerUnlock.child("pet_fox", PET_CAT_IN_WORLD).withIcon(Items.FOX_SPAWN_EGG).withPrice(15).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesPetInWorld(CraftmineContent.PET_FOX).withExclusiveApplication("exclusive_pet").register();
    public static Holder<PlayerUnlock> PET_POLAR_BEAR_IN_WORLD = PlayerUnlock.child("pet_polar_bear", PET_FOX_IN_WORLD).withIcon(Items.POLAR_BEAR_SPAWN_EGG).withPrice(30).givesPetInWorld(CraftmineContent.PET_POLAR_BEAR).withExclusiveApplication("exclusive_pet").register();
    public static Holder<PlayerUnlock> PET_SLIME_IN_WORLD = PlayerUnlock.child("pet_slime", PET_CHICKEN_IN_WORLD).withIcon(Items.SLIME_SPAWN_EGG).withPrice(30).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(PET_CHICKEN_IN_WORLD)).givesPetInWorld(CraftmineContent.PET_SLIME).withExclusiveApplication("exclusive_pet").register();
    public static Holder<PlayerUnlock> PET_CREEPER_IN_WORLD = PlayerUnlock.child("pet_creeper", PET_SLIME_IN_WORLD).withIcon(Items.CREEPER_SPAWN_EGG).withPrice(100).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(PET_SLIME_IN_WORLD)).givesPetInWorld(CraftmineContent.PET_CREEPER).withExclusiveApplication("exclusive_pet").register();
    public static Holder<PlayerUnlock> PET_WOLF = PlayerUnlock.child("pet_wolf", PET_CAT_IN_WORLD).withIcon(Items.WOLF_SPAWN_EGG).withPrice(10).givesPetInWorld(CraftmineContent.PET_WOLF).register();
    public static Holder<PlayerUnlock> BIG_WOLF = PlayerUnlock.child("pet_wolf_big", PET_WOLF).withIcon(Items.POTATO).withPrice(25).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(PET_WOLF)).upgradePet(CraftmineContent.PET_WOLF, PetWolf.class, tamableAnimal -> tamableAnimal.getAttribute(Attributes.SCALE).setBaseValue(2.0)).register();
    public static Holder<PlayerUnlock> ARMORED_WOLF = PlayerUnlock.child("pet_wolf_armored", PET_WOLF).withIcon(Items.WOLF_ARMOR).withPrice(15).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(PET_WOLF)).upgradePet(CraftmineContent.PET_WOLF, PetWolf.class, tamableAnimal -> tamableAnimal.setItemSlot(EquipmentSlot.BODY, Items.WOLF_ARMOR.getDefaultInstance())).register();
    public static Holder<PlayerUnlock> SWORD_WOLF = PlayerUnlock.child("pet_wolf_sword", PET_WOLF).withIcon(Items.NETHERITE_SWORD).withPrice(35).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(PET_WOLF)).upgradePet(CraftmineContent.PET_WOLF, PetWolf.class, tamableAnimal -> tamableAnimal.setItemInHand(InteractionHand.MAIN_HAND, Items.NETHERITE_SWORD.getDefaultInstance())).register();
    public static Holder<PlayerUnlock> SCHOOL_OF_HARD_KNOCKS = PlayerUnlock.root("school_of_hard_knocks").withIcon(Items.FLETCHING_TABLE).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesAttributeModifier(CraftmineAttributes.EXPERIENCE_GAIN_MODIFIER, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).register();
    public static Holder<PlayerUnlock> SCHOOL_OF_ACTUAL_HARD_KNOCKS = PlayerUnlock.child("school_of_actual_hard_knocks", SCHOOL_OF_HARD_KNOCKS).withIcon(Items.GOLD_NUGGET).withPrice(5).givesAttributeModifier(Attributes.MINING_EFFICIENCY, -3.0, AttributeModifier.Operation.ADD_VALUE).register();
    public static Holder<PlayerUnlock> REWRITE_IN_RUST = PlayerUnlock.child("rewrite_in_rust", SCHOOL_OF_ACTUAL_HARD_KNOCKS).withIcon(Items.IRON_CHAIN).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesAttributeModifier(Attributes.ARMOR_TOUGHNESS, -6.0, AttributeModifier.Operation.ADD_VALUE).withPrice(15).register();
    public static Holder<PlayerUnlock> EXTRA_FIREPOWER = PlayerUnlock.child("extra_firepower", REWRITE_IN_RUST).withIcon(Items.FLINT_AND_STEEL).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesAttributeModifier(Attributes.BURNING_TIME, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).withPrice(25).register();
    public static Holder<PlayerUnlock> PROPER_WALKING_PACE = PlayerUnlock.child("proper_walking_pace", EXTRA_FIREPOWER).withIcon(Items.IRON_BOOTS).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesAttributeModifier(Attributes.MOVEMENT_SPEED, -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).givesAttributeModifier(Attributes.SNEAKING_SPEED, -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).withPrice(35).register();
    public static Holder<PlayerUnlock> THORNS_PLUS_PLUS = PlayerUnlock.child("thorns_plus_plus", PROPER_WALKING_PACE).withIcon(Items.DIAMOND_CHESTPLATE).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).withPrice(45).register();
    public static Holder<PlayerUnlock> LEARNING_20;
    public static Holder<PlayerUnlock> JUMPING_10;

    // ---- Unoriginal features (everything below only exists while config unoriginal_features = true) ----
    /** QoL tab root: free, does nothing. */
    public static Holder<PlayerUnlock> QOL;
    /** Shows the reroll button in the minecrafter (rerolls the random ingredients). */
    public static Holder<PlayerUnlock> QOL_REROLL;
    /** Lets the reroll button also reroll special (boss) mines. */
    public static Holder<PlayerUnlock> QOL_REROLL_SPECIAL;
    /** Adds extra unlocks to the normal tabs (currently: full diamond armor). */
    public static Holder<PlayerUnlock> QOL_EXTRA_UNLOCKS;
    /** Base relock: ctrl+click an unlock in the hub to relock it (no xp back). */
    public static Holder<PlayerUnlock> QOL_RELOCK;
    /** Relocking gives back half the xp cost. */
    public static Holder<PlayerUnlock> QOL_RELOCK_HALF;
    /** Relocking gives back the full xp cost. */
    public static Holder<PlayerUnlock> QOL_RELOCK_FULL;
    /** Extra unlock (needs QOL_EXTRA_UNLOCKS): diamond armor after the iron armor unlock. */
    public static Holder<PlayerUnlock> FULL_DIAMOND;

    public static Holder<PlayerUnlock> bootstrap(Registry<PlayerUnlock> registry) {
        return CRAFTING;
    }

    static {
        Holder<PlayerUnlock> holder = PlayerUnlock.child("learning_1", EXPLORATION).withIcon(Items.BOOK).givesAttributeModifier(CraftmineAttributes.EXPERIENCE_GAIN_MODIFIER, 0.1f, AttributeModifier.Operation.ADD_VALUE).withPrice(2).register();
        for (int i = 2; i <= 20; ++i) {
            holder = PlayerUnlock.child("learning_" + i, holder).withIcon(Items.BOOK).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(holder)).givesAttributeModifier(CraftmineAttributes.EXPERIENCE_GAIN_MODIFIER, 0.1f, AttributeModifier.Operation.ADD_VALUE).withPrice(2 * i).register();
        }
        LEARNING_20 = holder;
        holder = PlayerUnlock.child("jump_king", EXPLORATION).withIcon(Items.SLIME_BLOCK).givesAttributeModifier(CraftmineAttributes.MAX_JUMPS, 1.0, AttributeModifier.Operation.ADD_VALUE).withPrice(20).register();
        Holder<PlayerUnlock> holder2 = PlayerUnlock.child("mega_jump", holder).withIcon(Items.IRON_BOOTS).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.playerKilledEntity(EntityTypes.RABBIT)).givesAttributeModifier(Attributes.JUMP_STRENGTH, 2.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).withPrice(5).register();
        PlayerUnlock.child("safe_falling", holder2).withIcon(Items.FEATHER).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesAttributeModifier(Attributes.SAFE_FALL_DISTANCE, 6.0, AttributeModifier.Operation.ADD_VALUE).withPrice(15).register();
        for (int i = 2; i <= 10; ++i) {
            holder = PlayerUnlock.child("jump_king_" + i, holder).withIcon(Items.SLIME_BLOCK).withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(holder)).givesAttributeModifier(CraftmineAttributes.MAX_JUMPS, 1.0, AttributeModifier.Operation.ADD_VALUE).withPrice(20 + i * 2).register();
        }
        JUMPING_10 = holder;
        Holder<PlayerUnlock> holder3 = PlayerUnlock.child("elytra", JUMPING_10).withIcon(Items.ELYTRA).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesItemInMine(Items.ELYTRA).withPrice(50).register();
        PlayerUnlock.child("rockets", holder3).withIcon(Items.FIREWORK_ROCKET).withVisibility(PlayerUnlock.UnlockVisibility.VISIBLE).givesItemStackInMine(Items.FIREWORK_ROCKET.getDefaultInstance().copyWithCount(10)).withPrice(50).register();

        // QoL tab. Prices are in levels. Every node is .unoriginal() so the whole tab (and the extra
        // unlocks it adds elsewhere) is absent while the unoriginal_features config is off; the
        // reroll/relock/extra-unlocks branches carry a per-feature scope on top, so their sub-toggle
        // (unoriginal_reroll_tree / unoriginal_relock_tree / unoriginal_extra_unlocks) can switch
        // them off individually while the master switch stays on.
        QOL = PlayerUnlock.root("qol").withIcon(CraftmineContent.MINE_CRAFTER_ITEM).withPrice(0).unoriginal().register();
        QOL_REROLL = PlayerUnlock.child("qol_reroll", QOL).withIcon(CraftmineContent.MINE_CRAFTER_ITEM).withPrice(10).unoriginal(CraftmineConfig.FEATURE_REROLL_TREE).register();
        QOL_REROLL_SPECIAL = PlayerUnlock.child("qol_reroll_special", QOL_REROLL).withIcon(Items.NETHER_STAR).withPrice(25).unoriginal(CraftmineConfig.FEATURE_REROLL_TREE).register();
        QOL_EXTRA_UNLOCKS = PlayerUnlock.child("qol_extra_unlocks", QOL).withIcon(Items.ENDER_CHEST).withPrice(15).unoriginal(CraftmineConfig.FEATURE_EXTRA_UNLOCKS).register();
        QOL_RELOCK = PlayerUnlock.child("qol_relock", QOL).withIcon(Items.GRINDSTONE).withPrice(10).unoriginal(CraftmineConfig.FEATURE_RELOCK_TREE).register();
        QOL_RELOCK_HALF = PlayerUnlock.child("qol_relock_half", QOL_RELOCK).withIcon(Items.IRON_INGOT).withPrice(20).unoriginal(CraftmineConfig.FEATURE_RELOCK_TREE).register();
        QOL_RELOCK_FULL = PlayerUnlock.child("qol_relock_full", QOL_RELOCK_HALF).withIcon(Items.EMERALD).withPrice(40).unoriginal(CraftmineConfig.FEATURE_RELOCK_TREE).register();

        // Extra unlock added to a normal tab (Combat, after Full Metal). Hidden until QOL_EXTRA_UNLOCKS is bought.
        FULL_DIAMOND = PlayerUnlock.child("full_diamond", FULL_METAL).withIcon(Items.DIAMOND_CHESTPLATE).withPrice(60)
            .givesItemInMine(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS)
            .disablesOtherUnlock(DECKED_OUT, FULL_METAL)
            .withVisibility(PlayerUnlock.UnlockVisibility.INVISIBLE).becomesVisibleWhen(UnlockCondition.unlocked(QOL_EXTRA_UNLOCKS))
            .requires(QOL_EXTRA_UNLOCKS).unoriginal(CraftmineConfig.FEATURE_EXTRA_UNLOCKS).register();
    }
}

