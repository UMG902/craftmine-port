/*
 * Registration of all craftmine-added content: items, blocks, entities, menus,
 * block entity types and particles (ported from the 25w14craftmine registry diffs).
 */
package dev.craftmine.registry;

import dev.craftmine.block.entity.MobTrophyBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import dev.craftmine.worldgen.PlaceTemplateFeature;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.Rarity;
import dev.craftmine.item.component.ItemExchangeValue;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import dev.craftmine.block.MineTravellingBlock;
import dev.craftmine.block.MineRevisitorBlock;
import dev.craftmine.block.MineCrafterBlock;
import dev.craftmine.block.DimensionControlBlock;
import dev.craftmine.block.MobTrophyBlock;
import dev.craftmine.block.ShimmeringDoorBlock;
import dev.craftmine.block.SkyWindowBlock;
import net.minecraft.world.level.block.TransparentBlock;
import dev.craftmine.block.TrophyBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import dev.craftmine.block.entity.MineCrafterBlockEntity;
import dev.craftmine.block.entity.MineTravellingBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import dev.craftmine.inventory.DoorMenu;
import dev.craftmine.inventory.DimensionControlMenu;
import net.minecraft.world.inventory.MenuType;
import dev.craftmine.inventory.MineCraftingMenu;
import dev.craftmine.item.WandItems;
import dev.craftmine.item.ExitEyeItem;
import dev.craftmine.item.MineItem;
import dev.craftmine.item.ShimmeringKeyItem;
import dev.craftmine.entity.AngryGhast;
import dev.craftmine.entity.pet.PetArmadillo;
import dev.craftmine.entity.pet.PetAxolotl;
import dev.craftmine.entity.pet.PetBee;
import dev.craftmine.entity.pet.PetCat;
import dev.craftmine.entity.pet.PetChicken;
import dev.craftmine.entity.pet.PetCow;
import dev.craftmine.entity.pet.PetCreeper;
import dev.craftmine.entity.pet.PetFox;
import dev.craftmine.entity.pet.PetFrog;
import dev.craftmine.entity.pet.PetMushroomCow;
import dev.craftmine.entity.pet.PetPolarBear;
import dev.craftmine.entity.pet.PetSlime;
import dev.craftmine.entity.pet.PetTurtle;
import dev.craftmine.entity.pet.PetWolf;
import net.minecraft.world.entity.EntityTypes;
import dev.craftmine.registry.CraftmineDataComponents;

public final class CraftmineContent {
    /**
     * Task 60: full ids of every entity type the port registers. Declared FIRST
     * in the class so its initializer runs before any registerEntity field init
     * (static initializers run in source order). Util's datafixer schema check
     * (CHECK_DATA_FIXER_SCHEMA) logs a benign ERROR for ids that are new in this
     * port (no legacy NBT exists to fix); UtilFetchChoiceTypeMixin filters
     * exactly these from the log.
     */
    public static final java.util.Set<String> DATAPACK_SCHEMA_IDS = new java.util.HashSet<String>();

    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.dimension.DimensionType> GENERATED_DIMENSION_TYPE = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION_TYPE, Identifier.withDefaultNamespace("generated"));
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> HUB_BIOME = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, Identifier.withDefaultNamespace("hub"));
    /**
     * Task 42 (mine exits): jigsaw structure type with required_exit_type biome
     * matching; the three minecraft:*_mine_exit structure JSONs reference this
     * type. Registered in bootstrap().
     */
    public static final net.minecraft.world.level.levelgen.structure.StructureType<dev.craftmine.worldgen.MineExitStructure> MINE_EXIT = () -> dev.craftmine.worldgen.MineExitStructure.CODEC;

    private CraftmineContent() {
    }

    /**
     * Blocks registered by this class. Vanilla populates
     * Block.BLOCK_STATE_REGISTRY (numeric per-state ids used by block_update
     * packets, chunk palettes and save data) and builds the per-state render
     * caches (BlockState.initCache - occlusionShapesByFace etc.) in a sweep at
     * the END of Blocks' static initializer, covering only the blocks known at
     * that point. Craftmine blocks register later, so the sweep must be
     * mirrored for them here - otherwise any packet carrying a craftmine
     * BlockState fails to encode ("Can't find id for 'Block{...}' in map
     * IdMapper", kicks the player) and tesselating one crashes the client
     * (NullPointerException occlusionShapesByFace).
     */
    private static final List<Block> CRAFTMINE_BLOCKS = new ArrayList<>();

    // Ported: BlockStateProperties.TROPHY_TYPE (25w14craftmine BlockStateProperties.java:176)
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<dev.craftmine.block.TrophyType> TROPHY_TYPE_PROPERTY = net.minecraft.world.level.block.state.properties.EnumProperty.create("type", dev.craftmine.block.TrophyType.class);

    // ---- blocks ----
    public static final Block MINE_TRAVELLING_BLOCK = registerBlock("mine_travelling_block", MineTravellingBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).lightLevel(blockState -> 15).strength(-1.0f, 3600000.0f).noLootTable().pushReaction(PushReaction.IMMOVEABLE));
    public static final Block MINE_REVISITOR = registerBlock("mine_revisitor", MineRevisitorBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).lightLevel(blockState -> 15).strength(-1.0f, 3600000.0f).pushReaction(PushReaction.IMMOVEABLE));
    public static final Block MINE_CRAFTER = registerBlock("mine_crafter", MineCrafterBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS).sound(SoundType.WOOD).strength(-1.0f));
    public static final Block DIMENSION_CONTROL = registerBlock("dimension_control", DimensionControlBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BANJO).strength(2.5f).sound(SoundType.WOOD));
    public static final Block SKY = registerBlock("sky", SkyWindowBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).lightLevel(blockState -> 15).strength(-1.0f, 3600000.0f).noLootTable().pushReaction(PushReaction.IMMOVEABLE));
    public static final Block SHIMMERING_DOOR = registerBlock("shimmering_door",
        (BlockBehaviour.Properties properties) -> new ShimmeringDoorBlock(BlockSetType.IRON, properties),
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).lightLevel(blockState -> 15).strength(-1.0f, 3600000.0f).noLootTable().pushReaction(PushReaction.IMMOVEABLE));
    public static final Block TROPHY = registerBlock("trophy", TrophyBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).noCollision());
    public static final Block MOB_TROPHY = registerBlock("mob_trophy", MobTrophyBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).noCollision());

    // ---- items ----
    public static final Item SKY_BOX = registerItem("sky_box", new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));
    public static final Item MOB_TROPHY_ITEM = registerBlockItem(MOB_TROPHY, new Item.Properties().rarity(Rarity.EPIC).stacksTo(16));
    public static final Item SHAZBOOTS = registerItem("shazboots", new Item.Properties().humanoidArmor(net.minecraft.world.item.equipment.ArmorMaterials.CHAINMAIL, net.minecraft.world.item.equipment.ArmorType.BOOTS));
    public static final Item EXIT_EYE = registerItem("exit_eye", ExitEyeItem::new, new Item.Properties().component(CraftmineDataComponents.EXCHANGE_VALUE, ItemExchangeValue.of(1.0f)));
    public static final Item FIRE_WAND = registerItem("fire_wand", WandItems.Fire::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
        .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.minecraft.fire_wand.lore")))));
    public static final Item WIND_WAND = registerItem("wind_wand", WandItems.Wind::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
        .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.minecraft.wind_wand.lore")))));
    public static final Item TELEPORTATION_WAND = registerItem("teleportation_wand", WandItems.Teleportation::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
        .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.minecraft.teleportation_wand.lore")))));
    public static final Item MINE_CRAFTER_ITEM = registerBlockItem(MINE_CRAFTER, new Item.Properties());
    public static final Item MINE_REVISITOR_ITEM = registerBlockItem(MINE_REVISITOR, new Item.Properties());
    public static final Item DIMENSION_CONTROL_ITEM = registerBlockItem(DIMENSION_CONTROL, new Item.Properties());
    public static final Item SHIMMERING_DOOR_ITEM = registerBlockItem(SHIMMERING_DOOR, new Item.Properties());
    public static final Item SHIMMERING_KEY = registerItem("shimmering_key", ShimmeringKeyItem::new, new Item.Properties());
    public static final Item MINE_INGREDIENT = registerItem("mine_ingredient", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final Item MINE = registerItem("mine", MineItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final Item GRAVE_ADVANCEMENT = registerItem("grave_advancement", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final Item TROPHY_ITEM = registerBlockItem(TROPHY, new Item.Properties());
    public static final Item SKY_ITEM = registerBlockItem(SKY, new Item.Properties());

    // ---- entities ----
    public static final EntityType<PetArmadillo> PET_ARMADILLO = registerEntity("pet_armadillo", EntityType.Builder.of(PetArmadillo::new, MobCategory.CREATURE).sized(0.7f, 0.65f).eyeHeight(0.26f).clientTrackingRange(10));
    public static final EntityType<PetAxolotl> PET_AXOLOTL = registerEntity("pet_axolotl", EntityType.Builder.of(PetAxolotl::new, MobCategory.AXOLOTLS).sized(0.75f, 0.42f).eyeHeight(0.2751f).clientTrackingRange(10));
    public static final EntityType<PetBee> PET_BEE = registerEntity("pet_bee", EntityType.Builder.of(PetBee::new, MobCategory.CREATURE).sized(0.7f, 0.6f).eyeHeight(0.3f).clientTrackingRange(8));
    public static final EntityType<PetCat> PET_CAT = registerEntity("pet_cat", EntityType.Builder.of(PetCat::new, MobCategory.CREATURE).sized(0.6f, 0.7f).eyeHeight(0.35f).passengerAttachments(0.5125f).clientTrackingRange(8));
    public static final EntityType<PetChicken> PET_CHICKEN = registerEntity("pet_chicken", EntityType.Builder.of(PetChicken::new, MobCategory.CREATURE).sized(0.6f, 0.7f).clientTrackingRange(8));
    public static final EntityType<PetCow> PET_COW = registerEntity("pet_cow", EntityType.Builder.of(PetCow::new, MobCategory.CREATURE).sized(0.9f, 1.4f).eyeHeight(1.3f).passengerAttachments(1.36875f).clientTrackingRange(10));
    public static final EntityType<PetCreeper> PET_CREEPER = registerEntity("pet_creeper", EntityType.Builder.of(PetCreeper::new, MobCategory.CREATURE).sized(0.6f, 1.7f).clientTrackingRange(8));
    public static final EntityType<PetFox> PET_FOX = registerEntity("pet_fox", EntityType.Builder.of(PetFox::new, MobCategory.CREATURE).sized(0.6f, 0.7f).eyeHeight(0.4f).passengerAttachments(new net.minecraft.world.phys.Vec3(0.0, 0.6375, -0.25)).clientTrackingRange(8));
    public static final EntityType<PetFrog> PET_FROG = registerEntity("pet_frog", EntityType.Builder.of(PetFrog::new, MobCategory.CREATURE).sized(0.5f, 0.5f).passengerAttachments(new net.minecraft.world.phys.Vec3(0.0, 0.375, -0.25)).clientTrackingRange(10));
    public static final EntityType<PetMushroomCow> PET_MUSHROOM_COW = registerEntity("pet_mushroom_cow", EntityType.Builder.of(PetMushroomCow::new, MobCategory.CREATURE).sized(0.9f, 1.4f).clientTrackingRange(10));
    public static final EntityType<PetPolarBear> PET_POLAR_BEAR = registerEntity("pet_polar_bear", EntityType.Builder.of(PetPolarBear::new, MobCategory.CREATURE).sized(1.4f, 1.4f).clientTrackingRange(10));
    public static final EntityType<PetSlime> PET_SLIME = registerEntity("pet_slime", EntityType.Builder.of(PetSlime::new, MobCategory.MONSTER).sized(0.52f, 0.52f).eyeHeight(0.325f).clientTrackingRange(10));
    public static final EntityType<PetTurtle> PET_TURTLE = registerEntity("pet_turtle", EntityType.Builder.of(PetTurtle::new, MobCategory.CREATURE).sized(1.2f, 0.4f).passengerAttachments(new net.minecraft.world.phys.Vec3(0.0, 0.55625, -0.25)).clientTrackingRange(10));
    public static final EntityType<PetWolf> PET_WOLF = registerEntity("pet_wolf", EntityType.Builder.of(PetWolf::new, MobCategory.CREATURE).sized(0.6f, 0.85f).eyeHeight(0.68f).passengerAttachments(new net.minecraft.world.phys.Vec3(0.0, 0.81875, -0.0625)).clientTrackingRange(10));
    public static final EntityType<AngryGhast> ANGRY_GHAST = registerEntity("angry_ghast", EntityType.Builder.of(AngryGhast::new, MobCategory.MONSTER).fireImmune().sized(9.0f, 9.0f).eyeHeight(2.6f).clientTrackingRange(10));

    // ---- menus ----
    public static final MenuType<MineCraftingMenu> MAP_MAKING = registerMenu("map_making", (n, inventory) -> new MineCraftingMenu(n, inventory, java.util.List.of()));
    public static final MenuType<DoorMenu> DOOR = registerMenu("door", (n, inventory) -> new DoorMenu(n, inventory, java.util.List.of()));
    public static final MenuType<DimensionControlMenu> DIMENSION_CONTROL_MENU = registerMenu("dimension_control", (n, inventory) -> new DimensionControlMenu(n, inventory, java.util.List.of()));

    // ---- block entities ----
    public static final BlockEntityType<MineTravellingBlockEntity> MINE_TRAVELLING_BLOCK_ENTITY = registerBlockEntity("mine_travelling_block", MineTravellingBlockEntity::new, MINE_TRAVELLING_BLOCK);
    public static final BlockEntityType<MineCrafterBlockEntity> MINE_CRAFTER_BLOCK_ENTITY = registerBlockEntity("mine_crafter", MineCrafterBlockEntity::new, MINE_CRAFTER);
    public static final BlockEntityType<MobTrophyBlockEntity> MOB_TROPHY_BLOCK_ENTITY = registerBlockEntity("mob_trophy", MobTrophyBlockEntity::new, MOB_TROPHY);

    private static Block registerBlock(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        Block block = Registry.register(BuiltInRegistries.BLOCK, Identifier.withDefaultNamespace(name), factory.apply(properties.setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, Identifier.withDefaultNamespace(name)))));
        CRAFTMINE_BLOCKS.add(block);
        return block;
    }

    private static Item registerItem(String name, Item.Properties properties) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.withDefaultNamespace(name), new Item(properties.setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.withDefaultNamespace(name)))));
    }

    private static Item registerItem(String name, java.util.function.Function<Item.Properties, Item> factory, Item.Properties properties) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.withDefaultNamespace(name), factory.apply(properties.setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.withDefaultNamespace(name)))));
    }

    private static Item registerBlockItem(Block block, Item.Properties properties) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return Registry.register(BuiltInRegistries.ITEM, id, new net.minecraft.world.item.BlockItem(block,
            properties.setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id)).useBlockDescriptionPrefix()));
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> registerEntity(String name, EntityType.Builder<T> builder) {
        DATAPACK_SCHEMA_IDS.add("minecraft:" + name);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, Identifier.withDefaultNamespace(name), builder.build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, Identifier.withDefaultNamespace(name))));
    }

    private static <T extends net.minecraft.world.inventory.AbstractContainerMenu> MenuType<T> registerMenu(String name, MenuType.MenuSupplier<T> supplier) {
        return Registry.register(BuiltInRegistries.MENU, Identifier.withDefaultNamespace(name), new MenuType<>(supplier, net.minecraft.world.flag.FeatureFlags.VANILLA_SET));
    }

    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityType<T> registerBlockEntity(String name, net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier<T> supplier, Block... validBlocks) {
        BlockEntityType<T> type = new BlockEntityType<>(supplier, java.util.Set.of(validBlocks));
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.withDefaultNamespace(name), type);
        return type;
    }

    public static void bootstrap() {
        // static initialization performs registration
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.withDefaultNamespace("place_template"), PlaceTemplateFeature.CODEC);
        // the hub level source (empty flat level the mines branch off) - the
        // normal world preset references generator type "minecraft:hub"
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.withDefaultNamespace("hub"), dev.craftmine.worldgen.HubLevelSource.CODEC);
        // Task 42 (mine exits): jigsaw structure with required_exit_type biome
        // matching - the three minecraft:*_mine_exit structures in this jar's
        // datapack reference this type
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Identifier.withDefaultNamespace("mine_exit"), MINE_EXIT);
        initBlockStateIds();
    }

    /**
     * Mirrors the vanilla sweep from the end of Blocks' static initializer
     * (Blocks.lambda$static$429 in 26.3): add every state of every block to
     * Block.BLOCK_STATE_REGISTRY and run BlockState.initCache(). Only needed
     * for blocks registered after that sweep - i.e. the craftmine ones.
     */
    private static void initBlockStateIds() {
        for (Block block : CRAFTMINE_BLOCKS) {
            for (BlockState blockState : block.getStateDefinition().getPossibleStates()) {
                Block.BLOCK_STATE_REGISTRY.add(blockState);
                blockState.initCache();
            }
        }
    }
}
