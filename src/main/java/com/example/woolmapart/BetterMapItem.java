package com.example.woolmapart;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.MapColor.Brightness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public class BetterMapItem extends MapItem {
	private static final String PIXELS = "PixelColors";
	private static final int[] MAP_BASE = {
		0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
		0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21,
		0xD2B1A1, 0x9D5123, 0x94566C, 0x766F8C, 0xB88221, 0x677535, 0xA65151, 0x3A2B24,
		0x876B62, 0x565C5C, 0x7B4A59, 0x4A3B5B, 0x4F3524, 0x4E552C, 0x8D3A2D, 0x251710
	};
	private static final MapColor[] MAP_COLORS = {
		MapColor.SNOW, MapColor.COLOR_ORANGE, MapColor.COLOR_MAGENTA, MapColor.COLOR_LIGHT_BLUE,
		MapColor.COLOR_YELLOW, MapColor.COLOR_LIGHT_GREEN, MapColor.COLOR_PINK, MapColor.COLOR_GRAY,
		MapColor.COLOR_LIGHT_GRAY, MapColor.COLOR_CYAN, MapColor.COLOR_PURPLE, MapColor.COLOR_BLUE,
		MapColor.COLOR_BROWN, MapColor.COLOR_GREEN, MapColor.COLOR_RED, MapColor.COLOR_BLACK,
		MapColor.TERRACOTTA_WHITE, MapColor.TERRACOTTA_ORANGE, MapColor.TERRACOTTA_MAGENTA,
		MapColor.TERRACOTTA_LIGHT_BLUE, MapColor.TERRACOTTA_YELLOW, MapColor.TERRACOTTA_LIGHT_GREEN,
		MapColor.TERRACOTTA_PINK, MapColor.TERRACOTTA_GRAY, MapColor.TERRACOTTA_LIGHT_GRAY,
		MapColor.TERRACOTTA_CYAN, MapColor.TERRACOTTA_PURPLE, MapColor.TERRACOTTA_BLUE,
		MapColor.TERRACOTTA_BROWN, MapColor.TERRACOTTA_GREEN, MapColor.TERRACOTTA_RED,
		MapColor.TERRACOTTA_BLACK
	};
	private final boolean captures;

	public BetterMapItem(Properties properties, boolean captures) {
		super(properties.stacksTo(1));
		this.captures = captures;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide() && captures && !hasSnapshot(stack)) {
			BlockPos playerPos = player.blockPosition();
			int centerX = Math.floorDiv(playerPos.getX() + 64, 128) * 128;
			int centerZ = Math.floorDiv(playerPos.getZ() + 64, 128) * 128;
			int baseY = playerPos.getY() - 1;
			int[] snapshot = new int[128 * 128];
			for (int x = 0; x < 128; x++) {
				for (int z = 0; z < 128; z++) {
					BlockPos pos = new BlockPos(centerX - 64 + x, baseY, centerZ - 64 + z);
					var state = level.getBlockState(pos);
					if (state.is(WoolMapArt.PIXEL_BLOCK)) {
						snapshot[z * 128 + x] = WoolMapArt.PIXEL_COLORS[
							state.getValue(PixelBlock.COLOR)];
					} else {
						snapshot[z * 128 + x] = 0xB0B0B0;
					}
				}
			}
			createFilledMap((net.minecraft.server.level.ServerLevel) level, player, hand,
				centerX, centerZ, snapshot);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		Level level = context.getLevel();
		if (!captures || level.isClientSide() || hasSnapshot(stack)
			|| !level.getBlockState(context.getClickedPos()).is(WoolMapArt.PIXEL_BLOCK)) {
			return InteractionResult.PASS;
		}
		CanvasCapture capture = findCanvas((net.minecraft.server.level.ServerLevel) level,
			context.getClickedPos());
		if (capture == null) {
			context.getPlayer().sendSystemMessage(
				net.minecraft.network.chat.Component.literal(
					"Better Map needs a complete connected 128x128 pixel canvas."));
			return InteractionResult.SUCCESS;
		}
		createFilledMap((net.minecraft.server.level.ServerLevel) level, context.getPlayer(),
			context.getHand(), capture.centerX(), capture.centerZ(), capture.pixels());
		return InteractionResult.SUCCESS;
	}

	private static void createFilledMap(
		net.minecraft.server.level.ServerLevel level, Player player,
		InteractionHand hand, int centerX, int centerZ, int[] pixels) {
		MapId mapId = level.getFreeMapId();
		MapItemSavedData mapData = MapItemSavedData.createFresh(
			centerX, centerZ, (byte) 0, false, false, level.dimension());
		for (int x = 0; x < 128; x++) {
			for (int z = 0; z < 128; z++) {
				mapData.setColor(x, z, mapColor(pixels[z * 128 + x]));
			}
		}
		level.setMapData(mapId, mapData);
		ItemStack filled = new ItemStack(WoolMapArt.FILLED_BETTER_MAP);
		CompoundTag tag = new CompoundTag();
		tag.putIntArray(PIXELS, pixels);
		CustomData.set(DataComponents.CUSTOM_DATA, filled, tag);
		filled.set(DataComponents.MAP_ID, mapId);
		player.setItemInHand(hand, filled);
	}

	private static CanvasCapture findCanvas(
		net.minecraft.server.level.ServerLevel level, BlockPos start) {
		ArrayDeque<BlockPos> pending = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		pending.add(start);
		int minX = start.getX(), maxX = start.getX();
		int minZ = start.getZ(), maxZ = start.getZ();
		int y = start.getY();
		while (!pending.isEmpty()) {
			BlockPos pos = pending.removeFirst();
			if (!visited.add(pos) || pos.getY() != y
				|| !level.getBlockState(pos).is(WoolMapArt.PIXEL_BLOCK)) continue;
			minX = Math.min(minX, pos.getX());
			maxX = Math.max(maxX, pos.getX());
			minZ = Math.min(minZ, pos.getZ());
			maxZ = Math.max(maxZ, pos.getZ());
			pending.add(pos.west());
			pending.add(pos.east());
			pending.add(pos.north());
			pending.add(pos.south());
			if (visited.size() > 16384) return null;
		}
		if (visited.size() != 128 * 128 || maxX - minX != 127 || maxZ - minZ != 127) {
			return null;
		}
		int[] pixels = new int[128 * 128];
		for (BlockPos pos : visited) {
			int x = pos.getX() - minX;
			int z = pos.getZ() - minZ;
			pixels[z * 128 + x] = WoolMapArt.PIXEL_COLORS[
				level.getBlockState(pos).getValue(PixelBlock.COLOR)];
		}
		return new CanvasCapture(minX + 64, minZ + 64, pixels);
	}

	private record CanvasCapture(int centerX, int centerZ, int[] pixels) {
	}

	private static byte mapColor(int rgb) {
		return mapColorFor(rgb).getPackedId(Brightness.NORMAL);
	}

	public static MapColor mapColorFor(int rgb) {
		int best = 0;
		long bestDistance = Long.MAX_VALUE;
		for (int i = 0; i < MAP_BASE.length; i++) {
			long dr = ((rgb >> 16) & 255) - ((MAP_BASE[i] >> 16) & 255);
			long dg = ((rgb >> 8) & 255) - ((MAP_BASE[i] >> 8) & 255);
			long db = (rgb & 255) - (MAP_BASE[i] & 255);
			long distance = dr * dr + dg * dg + db * db;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return MAP_COLORS[best];
	}

	public static boolean hasSnapshot(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(PIXELS);
	}

	public static int[] readSnapshot(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		int[] pixels = data.copyTag().getIntArray(PIXELS).orElse(null);
		return pixels != null && pixels.length == 128 * 128 ? pixels : null;
	}
}
