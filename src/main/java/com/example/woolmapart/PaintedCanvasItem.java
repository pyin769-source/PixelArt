package com.example.woolmapart;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class PaintedCanvasItem extends MapItem {
	public static final String PIXELS = "PixelColors";

	public PaintedCanvasItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	public static ItemStack create(int[] pixels) {
		ItemStack stack = new ItemStack(WoolMapArt.PAINTED_CANVAS);
		CompoundTag tag = new CompoundTag();
		tag.putIntArray(PIXELS, pixels);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		return stack;
	}

	public static ItemStack create(ServerLevel level, int centerX, int centerZ, int[] pixels) {
		ItemStack stack = create(pixels);
		MapId mapId = level.getFreeMapId();
		MapItemSavedData mapData = MapItemSavedData.createFresh(
			centerX, centerZ, (byte) 0, false, false, level.dimension());
		for (int x = 0; x < 128; x++) {
			for (int z = 0; z < 128; z++) {
				mapData.setColor(x, z, BetterMapItem.mapColorFor(pixels[z * 128 + x])
					.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.NORMAL));
			}
		}
		level.setMapData(mapId, mapData);
		stack.set(DataComponents.MAP_ID, mapId);
		return stack;
	}

	public static int[] read(ItemStack stack) {
		if (!stack.is(WoolMapArt.PAINTED_CANVAS)) return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		int[] pixels = data.copyTag().getIntArray(PIXELS).orElse(null);
		return pixels != null && pixels.length == 128 * 128 ? pixels : null;
	}
}
