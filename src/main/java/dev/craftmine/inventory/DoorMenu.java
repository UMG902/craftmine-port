package dev.craftmine.inventory;

import dev.craftmine.mixin.StructureTemplateAccessor;
import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.server.CraftmineServerHooks;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.NonInteractiveResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import dev.craftmine.item.component.RoomerinoComponentino;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import dev.craftmine.worldgen.HubLevelSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The shimmering door menu: preview layout of the room attached behind the
 * clicked door (arrow buttons cycle options, plop stamps it into the world).
 * The server extracts candidate rooms from the door's structure template
 * ({@link #extractLayout}), classifying every column as room/wall/door;
 * only the Layout list syncs to the client - placement data stays server-side.
 */
public class DoorMenu
extends AbstractContainerMenu {
    public static final int BUTTON_NEXT = 0;
    public static final int BUTTON_PREV = 1;
    public static final int BUTTON_PLOP = 2;
    private static final int MAGIC_SLOT = 0;
    private static final int INV_SLOT_START = 1;
    private static final int INV_SLOT_END = 28;
    private static final int USE_ROW_SLOT_START = 28;
    private static final int USE_ROW_SLOT_END = 37;
    public static final Layout ERROR = DoorMenu.fromText(FieldType.AWWW_SAD, "X X   X X", " X     X ", "X X   X X", "         ", "    X    ", "   X     ", "  XXXX   ", "         ", "         ", "  XXXX   ", " X    X  ", "X      X ");
    private final ContainerLevelAccess access;
    private final List<Layout> displays;
    @Nullable
    private final List<PlacementInfo> actualPlacements;
    private final DataSlot index = DataSlot.standalone();
    private final SimpleContainer storage = new SimpleContainer(1);

    private static Layout fromText(FieldType fieldType, String ... stringArray) {
        int n2 = stringArray[0].length();
        ArrayList<FieldType> arrayList = new ArrayList<FieldType>(stringArray.length * n2);
        for (String string : stringArray) {
            if (string.length() != n2) {
                throw new IllegalStateException("door art rows must all be the same length");
            }
            string.chars().mapToObj(n -> n == 32 ? FieldType.EMPTY : FieldType.AWWW_SAD).forEach(arrayList::add);
        }
        return new Layout(arrayList, n2);
    }

    public DoorMenu(int n, Inventory inventory, List<Layout> list) {
        this(n, inventory, ContainerLevelAccess.NULL, list, null);
    }

    public DoorMenu(int n, Inventory inventory, ContainerLevelAccess containerLevelAccess, List<Layout> list, @Nullable List<PlacementInfo> list2) {
        super(null, n);
        this.access = containerLevelAccess;
        this.displays = list;
        this.actualPlacements = list2;
        this.addSlot(new NonInteractiveResultSlot(this.storage, 0, 8, 8){

            @Override
            public boolean isHighlightable() {
                return true;
            }
        });
        this.addStandardInventorySlots(inventory, 8, 165);
        this.addDataSlot(this.index);
    }

    public void setKey(ItemStack itemStack) {
        this.storage.setItem(0, itemStack);
    }

    public ItemStack getKey() {
        return this.storage.getItem(0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int n) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = (Slot)this.slots.get(n);
        if (slot != null && slot.hasItem()) {
            ItemStack itemStack2 = slot.getItem();
            itemStack = itemStack2.copy();
            if (n >= 1 && n < 28 ? !this.moveItemStackTo(itemStack2, 28, 37, false) : n >= 28 && n < 37 && !this.moveItemStackTo(itemStack2, 1, 28, false)) {
                return ItemStack.EMPTY;
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

    @Override
    public boolean stillValid(Player player) {
        return DoorMenu.stillValid(this.access, player, CraftmineContent.SHIMMERING_DOOR);
    }

    @Nullable
    public Layout getOption() {
        if (this.displays.isEmpty()) {
            return null;
        }
        return this.displays.get(this.index.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int n) {
        int n2 = this.displays.size();
        if (n2 == 0) {
            return false;
        }
        switch (n) {
            case 0: {
                this.index.set(Math.floorMod(this.index.get() + 1, n2));
                break;
            }
            case 1: {
                this.index.set(Math.floorMod(this.index.get() - 1, n2));
                break;
            }
            case 2: {
                this.access.execute((level, blockPos) -> {
                    if (this.actualPlacements == null) {
                        player.closeContainer();
                        return;
                    }
                    PlacementInfo placementInfo = this.actualPlacements.get(this.index.get());
                    if (level instanceof ServerLevel) {
                        ServerLevel serverLevel = (ServerLevel)level;
                        this.storage.setItem(0, ItemStack.EMPTY);
                        player.closeContainer();
                        RandomSource randomSource = RandomSource.create(placementInfo.seed());
                        placementInfo.settings().setRandom(randomSource);
                        placementInfo.structure().placeInWorld(serverLevel, placementInfo.origin(), placementInfo.origin(), placementInfo.settings(), level.random, 3);
                        for (BlockPos doorPos : placementInfo.doorsToOpen()) {
                            BlockState blockState = serverLevel.getBlockState(doorPos);
                            serverLevel.setBlock(doorPos, blockState.trySetValue(DoorBlock.OPEN, true), 3);
                        }
                    }
                });
            }
        }
        return super.clickMenuButton(player, n);
    }

    private static ColumnPos column(BlockPos blockPos) {
        return new ColumnPos(blockPos.getX(), blockPos.getZ());
    }

    private static List<ColumnPos> columnNeighbours(ColumnPos columnPos) {
        int n = columnPos.x();
        int n2 = columnPos.z();
        return List.of(new ColumnPos(n - 1, n2 - 1), new ColumnPos(n, n2 - 1), new ColumnPos(n + 1, n2 - 1), new ColumnPos(n - 1, n2), new ColumnPos(n + 1, n2), new ColumnPos(n - 1, n2 + 1), new ColumnPos(n, n2 + 1), new ColumnPos(n + 1, n2 + 1));
    }

    /**
     * Fork-accurate port of the removed unfiltered StructureTemplate.filterBlocks
     * (fork ezw.a(ix,ezs,boolean) - iterates Palette.blocks(), i.e. ALL blocks).
     * 26.3's 4-arg filterBlocks routes through Palette.blocks(Block) where
     * blocks(null) filters everything out (BlockState.is(null) is always false),
     * which silently broke shimmering door room extraction (instant_room keys
     * never opened any room). Mirrors the fork 1:1: palette selection, absolute
     * position transform, bounding-box clip, rotation.
     */
    private static ObjectArrayList<StructureTemplate.StructureBlockInfo> filterAllBlocks(
        StructureTemplate structureTemplate, BlockPos position, StructurePlaceSettings structurePlaceSettings, boolean absolute
    ) {
        ObjectArrayList<StructureTemplate.StructureBlockInfo> objectArrayList = new ObjectArrayList<>();
        List<StructureTemplate.Palette> palettes = ((StructureTemplateAccessor)structureTemplate).craftmine$palettes();
        if (palettes.isEmpty()) {
            return objectArrayList;
        }
        BoundingBox boundingBox = structurePlaceSettings.getBoundingBox();
        for (StructureTemplate.StructureBlockInfo structureBlockInfo : structurePlaceSettings.getRandomPalette(palettes, position).blocks()) {
            BlockPos blockPos = absolute ? StructureTemplate.calculateRelativePosition(structurePlaceSettings, structureBlockInfo.pos()).offset(position) : structureBlockInfo.pos();
            if (boundingBox != null && !boundingBox.isInside(blockPos)) continue;
            objectArrayList.add(new StructureTemplate.StructureBlockInfo(blockPos, structureBlockInfo.state().rotate(structurePlaceSettings.getRotation()), structureBlockInfo.nbt()));
        }
        return objectArrayList;
    }

    public static List<Pair<Layout, PlacementInfo>> extractLayout(ServerLevel serverLevel, BlockPos blockPos, Direction direction, DoubleBlockHalf doubleBlockHalf, RoomerinoComponentino roomerinoComponentino) {
        Optional<StructureTemplate> optional = CraftmineServerHooks.theGame().structureManager().get(roomerinoComponentino.structureId());
        if (optional.isPresent()) {
            StructureTemplate structureTemplate = optional.get();
            Direction direction2 = direction.getOpposite();
            BlockPos blockPos3 = blockPos.relative(direction2);
            ArrayList<Pair<Layout, PlacementInfo>> arrayList = new ArrayList<Pair<Layout, PlacementInfo>>();
            StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings();
            long l = serverLevel.random.nextLong();
            RandomSource randomSource = RandomSource.create(l);
            structurePlaceSettings.setRandom(randomSource);
            structurePlaceSettings.addProcessor(HubLevelSource.SKYIFIER);
            for (Rotation rotation : Rotation.values()) {
                structurePlaceSettings.setRotation(rotation);
                randomSource.setSeed(l);
                BlockPos blockPos4 = BlockPos.ZERO;
                ObjectArrayList<StructureTemplate.StructureBlockInfo> objectArrayList = DoorMenu.filterAllBlocks(structureTemplate, blockPos4, structurePlaceSettings, true);
                Map<BlockPos, BlockState> map = DoorMenu.findInterestingDoors(objectArrayList);
                List<BlockPos> list = map.entrySet().stream().filter(entry -> DoorMenu.isMatchingDoor((BlockState)entry.getValue(), doubleBlockHalf, direction2)).map(entry -> blockPos3.subtract(entry.getKey())).toList();
                if (list.isEmpty()) continue;
                Object2ObjectMap<ColumnPos, FieldType> object2ObjectMap = DoorMenu.computeBaseRoomLayout(objectArrayList);
                list.forEach(blockPos2 -> {
                    Layout layout;
                    HashSet<BlockPos> hashSet = new HashSet<BlockPos>();
                    Map<ColumnPos, FieldType> map3 = DoorMenu.scanSurroundings(serverLevel, blockPos, blockPos2, objectArrayList, object2ObjectMap, map, hashSet);
                    if (map3 != null && (layout = DoorMenu.createLayout(map3)) != null) {
                        PlacementInfo placementInfo = new PlacementInfo(structureTemplate, blockPos2, structurePlaceSettings.copy(), l, hashSet);
                        arrayList.add(Pair.of(layout, placementInfo));
                    }
                });
            }
            return arrayList;
        }
        return List.of();
    }

    private static List<BlockPos> wallNeighbours(BlockPos blockPos) {
        return List.of(blockPos.relative(Direction.NORTH), blockPos.relative(Direction.SOUTH), blockPos.relative(Direction.EAST), blockPos.relative(Direction.WEST));
    }

    @Nullable
    private static Map<ColumnPos, FieldType> scanSurroundings(ServerLevel serverLevel, BlockPos blockPos, BlockPos blockPos2, List<StructureTemplate.StructureBlockInfo> list, Map<ColumnPos, FieldType> map, Map<BlockPos, BlockState> map2, Set<BlockPos> set) {
        Object2ObjectOpenHashMap<ColumnPos, FieldType> object2ObjectOpenHashMap = new Object2ObjectOpenHashMap<ColumnPos, FieldType>();
        object2ObjectOpenHashMap.defaultReturnValue(FieldType.EMPTY);
        for (StructureTemplate.StructureBlockInfo structureBlockInfo : list) {
            BlockPos blockPos3 = structureBlockInfo.pos().offset(blockPos2);
            if (serverLevel.getBlockState(blockPos3).isAir()) continue;
            return null;
        }
        DoorMenu.shiftAndCopyRoom(blockPos2, map, object2ObjectOpenHashMap);
        DoorMenu.markAdjacentWalls(serverLevel, blockPos2, list, object2ObjectOpenHashMap);
        DoorMenu.markMatchingDoors(serverLevel, blockPos2, map2, object2ObjectOpenHashMap, set);
        object2ObjectOpenHashMap.put(DoorMenu.column(blockPos), FieldType.ACTIVE_DOOR);
        return object2ObjectOpenHashMap;
    }

    private static void markMatchingDoors(ServerLevel serverLevel, BlockPos blockPos, Map<BlockPos, BlockState> map, Object2ObjectMap<ColumnPos, FieldType> object2ObjectMap, Set<BlockPos> set) {
        map.forEach((blockPos2, blockState) -> {
            BlockPos blockPos3 = blockPos2.offset(blockPos);
            Direction direction = blockState.getValue(DoorBlock.FACING);
            DoubleBlockHalf doubleBlockHalf = blockState.getValue(DoorBlock.HALF);
            Direction direction2 = direction.getOpposite();
            BlockPos blockPos4 = blockPos3.relative(direction2);
            BlockState blockState2 = serverLevel.getBlockState(blockPos4);
            if (DoorMenu.isMatchingDoor(blockState2, doubleBlockHalf, direction2)) {
                object2ObjectMap.put(DoorMenu.column(blockPos4), FieldType.EXISTING_DOOR);
                set.add(blockPos3);
                set.add(blockPos4);
            }
        });
    }

    private static void shiftAndCopyRoom(BlockPos blockPos, Map<ColumnPos, FieldType> map, Object2ObjectMap<ColumnPos, FieldType> object2ObjectMap) {
        map.forEach((columnPos, fieldType) -> {
            ColumnPos columnPos2 = new ColumnPos(columnPos.x() + blockPos.getX(), columnPos.z() + blockPos.getZ());
            object2ObjectMap.put(columnPos2, fieldType);
        });
    }

    private static void markAdjacentWalls(ServerLevel serverLevel, BlockPos offset, List<StructureTemplate.StructureBlockInfo> roomBlocks, Map<ColumnPos, FieldType> layout) {
        Set<BlockPos> roomPositions = roomBlocks.stream().map(structureBlockInfo -> structureBlockInfo.pos().offset(offset)).collect(Collectors.toSet());
        Map<BlockPos, BlockState> wallStates = roomPositions.stream().flatMap(blockPos -> DoorMenu.wallNeighbours(blockPos).stream()).filter(blockPos -> !roomPositions.contains(blockPos)).distinct().collect(Collectors.toMap(blockPos -> blockPos, serverLevel::getBlockState));
        Map<ColumnPos, List<Map.Entry<BlockPos, BlockState>>> byColumn = wallStates.entrySet().stream().collect(Collectors.groupingBy(entry -> DoorMenu.column(entry.getKey())));
        byColumn.forEach((columnPos, entries) -> {
            if (entries.stream().anyMatch(entry -> !entry.getValue().isAir())) {
                layout.put(columnPos, FieldType.EXISTING_WALL);
            }
        });
    }

    private static Map<BlockPos, BlockState> findInterestingDoors(List<StructureTemplate.StructureBlockInfo> list) {
        HashMap<BlockPos, BlockState> hashMap = new HashMap<BlockPos, BlockState>();
        for (StructureTemplate.StructureBlockInfo structureBlockInfo : list) {
            if (!DoorMenu.isInterestingDoor(structureBlockInfo.state())) continue;
            hashMap.put(structureBlockInfo.pos(), structureBlockInfo.state());
        }
        return hashMap;
    }

    private static boolean isInterestingDoor(BlockState blockState) {
        return blockState.is(CraftmineContent.SHIMMERING_DOOR) && !blockState.getValue(DoorBlock.OPEN);
    }

    private static boolean isMatchingDoor(BlockState blockState, DoubleBlockHalf doubleBlockHalf, Direction direction) {
        return DoorMenu.isInterestingDoor(blockState) && blockState.getValue(DoorBlock.FACING) == direction && blockState.getValue(DoorBlock.HALF) == doubleBlockHalf;
    }

    private static Object2ObjectMap<ColumnPos, FieldType> computeBaseRoomLayout(List<StructureTemplate.StructureBlockInfo> list) {
        Map<ColumnPos, List<StructureTemplate.StructureBlockInfo>> map = list.stream().collect(Collectors.groupingBy(structureBlockInfo -> DoorMenu.column(structureBlockInfo.pos())));
        Object2ObjectOpenHashMap<ColumnPos, FieldType> object2ObjectOpenHashMap = new Object2ObjectOpenHashMap<ColumnPos, FieldType>();
        object2ObjectOpenHashMap.defaultReturnValue(FieldType.EMPTY);
        map.forEach((columnPos, blockInfoList) -> object2ObjectOpenHashMap.put(columnPos, DoorMenu.categorizeRoomColumn(blockInfoList)));
        Set<ColumnPos> set = object2ObjectOpenHashMap.entrySet().stream().filter(entry -> DoorMenu.isRoom(entry.getValue())).map(Map.Entry::getKey).collect(Collectors.toSet());
        DoorMenu.fixupWalls(object2ObjectOpenHashMap, set);
        return object2ObjectOpenHashMap;
    }

    @Nullable
    private static Layout createLayout(Map<ColumnPos, FieldType> map) {
        Layout layout;
        Rect rect = Rect.compute(map.keySet());
        if (rect != null) {
            int n = rect.width();
            int n2 = rect.height();
            ArrayList<FieldType> arrayList = new ArrayList<FieldType>(n * n2);
            for (int i = 0; i < n * n2; ++i) {
                arrayList.add(FieldType.EMPTY);
            }
            map.forEach((columnPos, fieldType) -> arrayList.set(rect.index(columnPos), fieldType));
            layout = new Layout(arrayList, n);
        } else {
            layout = null;
        }
        return layout;
    }

    private static void fixupWalls(Object2ObjectMap<ColumnPos, FieldType> layout, Set<ColumnPos> roomColumns) {
        for (ColumnPos columnPos : roomColumns) {
            if (layout.get(columnPos) == FieldType.NEW_ROOM && DoorMenu.columnNeighbours(columnPos).stream().anyMatch(neighbour -> DoorMenu.shouldHaveWall(layout.get(neighbour)))) {
                layout.put(columnPos, FieldType.NEW_WALL);
            }
        }
    }

    private static boolean isRoom(FieldType fieldType) {
        return fieldType == FieldType.NEW_DOOR || fieldType == FieldType.NEW_ROOM || fieldType == FieldType.NEW_WALL;
    }

    private static boolean shouldHaveWall(FieldType fieldType) {
        return !DoorMenu.isRoom(fieldType);
    }

    private static FieldType categorizeRoomColumn(List<StructureTemplate.StructureBlockInfo> list) {
        boolean bl = false;
        for (StructureTemplate.StructureBlockInfo structureBlockInfo : list) {
            if (structureBlockInfo.state().is(CraftmineContent.SHIMMERING_DOOR)) {
                return FieldType.NEW_DOOR;
            }
            if (structureBlockInfo.state().isAir()) continue;
            bl = true;
        }
        return bl ? FieldType.NEW_ROOM : FieldType.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, blockPos) -> this.clearContainer(player, this.storage));
    }

    public record Layout(List<FieldType> fields, int width) {
        public static final StreamCodec<ByteBuf, Layout> STREAM_CODEC = StreamCodec.composite(FieldType.STREAM_CODEC.apply(ByteBufCodecs.list()), Layout::fields, ByteBufCodecs.VAR_INT, Layout::width, Layout::new);

        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            int n = this.fields.size();
            for (int i = 0; i < n; ++i) {
                stringBuilder.append(this.fields.get((int)i).debugChar);
                if (i % this.width != this.width - 1) continue;
                stringBuilder.append("\n");
            }
            return stringBuilder.toString();
        }
    }

    public static enum FieldType {
        EMPTY(0, null, ' ', null),
        NEW_ROOM(1, DyeColor.GREEN, '\u2588', Component.translatable("gui.door.field.new_room")),
        NEW_WALL(2, DyeColor.LIME, '+', Component.translatable("gui.door.field.new_wall")),
        NEW_DOOR(3, DyeColor.PURPLE, '#', Component.translatable("gui.door.field.new_door")),
        EXISTING_WALL(4, DyeColor.GRAY, 'o', Component.translatable("gui.door.field.existing_wall")),
        EXISTING_DOOR(5, DyeColor.RED, 'x', Component.translatable("gui.door.field.existing_door")),
        ACTIVE_DOOR(6, DyeColor.YELLOW, '@', Component.translatable("gui.door.field.active_door")),
        AWWW_SAD(7, DyeColor.CYAN, '!', Component.translatable("gui.door.field.error"));

        private final int id;
        @Nullable
        public final DyeColor color;
        public final char debugChar;
        @Nullable
        public final Component tooltip;
        public static final IntFunction<FieldType> BY_ID;
        public static final StreamCodec<ByteBuf, FieldType> STREAM_CODEC;

        private FieldType(int n2, @Nullable DyeColor dyeColor, char c, Component component) {
            this.id = n2;
            this.color = dyeColor;
            this.debugChar = c;
            this.tooltip = component;
        }

        public int getId() {
            return this.id;
        }

        static {
            BY_ID = ByIdMap.continuous(FieldType::getId, FieldType.values(), ByIdMap.OutOfBoundsStrategy.ZERO);
            STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, FieldType::getId);
        }
    }

    record Rect(int zMin, int zMax, int xMin, int xMax) {
        public static Rect single(ColumnPos columnPos) {
            return new Rect(columnPos.z(), columnPos.z(), columnPos.x(), columnPos.x());
        }

        public int width() {
            return this.xMax - this.xMin + 1;
        }

        public int height() {
            return this.zMax - this.zMin + 1;
        }

        public int index(ColumnPos columnPos) {
            int n = columnPos.x() - this.xMin;
            int n2 = columnPos.z() - this.zMin;
            return n2 * this.width() + n;
        }

        public Rect append(Rect rect) {
            return new Rect(Math.min(this.zMin, rect.zMin), Math.max(this.zMax, rect.zMax), Math.min(this.xMin, rect.xMin), Math.max(this.xMax, rect.xMax));
        }

        @Nullable
        public static Rect compute(Collection<ColumnPos> collection) {
            List<Rect> list = collection.stream().map(Rect::single).toList();
            if (list.isEmpty()) {
                return null;
            }
            Rect rect = list.getFirst();
            for (int i = 1; i < list.size(); ++i) {
                rect = rect.append(list.get(i));
            }
            return rect;
        }
    }

    /**
     * One room-placement option behind a door: which template to stamp, where,
     * with which placement settings/seed, and which door blocks to force open
     * afterwards (the room's other side must connect to the corridor).
     */
    public record PlacementInfo(StructureTemplate structure, BlockPos origin, StructurePlaceSettings settings,
        long seed, Set<BlockPos> doorsToOpen) {
    }
}

