package com.example.woolmapart;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import com.mojang.blaze3d.platform.NativeImage;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public final class BetterMapTextures {
	private static final Map<Integer, Identifier> TEXTURES = new HashMap<>();
	private static final int SIZE = 128;

	private BetterMapTextures() {
	}

	public static void registerTick() {
		ClientTickEvents.END_CLIENT_TICK.register(BetterMapTextures::scan);
	}

	public static void scan(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) return;
		for (ItemStack stack : minecraft.player.getInventory().getNonEquipmentItems()) {
			ensure(minecraft, stack);
		}
		ensure(minecraft, minecraft.player.getItemBySlot(EquipmentSlot.OFFHAND));
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof ItemFrame frame) ensure(minecraft, frame.getItem());
		}
	}

	private static void ensure(Minecraft minecraft, ItemStack stack) {
		if (stack.is(WoolMapArt.PAINTED_CANVAS)) {
			int[] pixels = PaintedCanvasItem.read(stack);
			if (pixels == null) return;
			int key = Arrays.hashCode(pixels);
			if (!TEXTURES.containsKey(key)) {
				registerTexture(minecraft, pixels, key, "painted_canvas");
			}
			MapId mapId = stack.get(DataComponents.MAP_ID);
			Identifier texture = TEXTURES.get(key);
			if (mapId != null && texture != null) TEXTURES.put(mapId.id(), texture);
			return;
		}
		if (!stack.is(WoolMapArt.BETTER_MAP) && !stack.is(WoolMapArt.FILLED_BETTER_MAP)) return;
		MapId mapId = stack.get(DataComponents.MAP_ID);
		int[] pixels = BetterMapItem.readSnapshot(stack);
		if (mapId == null || pixels == null || TEXTURES.containsKey(mapId.id())) return;
		registerTexture(minecraft, pixels, mapId.id(), "better_map");
	}

	private static void registerTexture(Minecraft minecraft, int[] pixels, int key, String name) {
		NativeImage image = new NativeImage(SIZE, SIZE, true);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				int rgb = pixels[y * SIZE + x];
				int r = (rgb >> 16) & 255;
				int g = (rgb >> 8) & 255;
				int b = rgb & 255;
				int abgr = 0xFF000000 | (b << 16) | (g << 8) | r;
				image.setPixelABGR(x, y, abgr);
			}
		}

		Identifier id = Identifier.fromNamespaceAndPath("woolmapart", name + "/" + key);
		DynamicTexture texture = new DynamicTexture(
			() -> "woolmapart_" + name + "_" + key, image);
		texture.upload();
		minecraft.getTextureManager().register(id, texture);
		TEXTURES.put(key, id);
	}

	public static Identifier textureFor(MapId mapId) {
		return mapId == null ? null : TEXTURES.get(mapId.id());
	}

	public static Identifier textureFor(ItemStack stack) {
		int[] pixels = PaintedCanvasItem.read(stack);
		return pixels == null ? null : TEXTURES.get(Arrays.hashCode(pixels));
	}
}
