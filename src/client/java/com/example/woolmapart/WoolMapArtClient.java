package com.example.woolmapart;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.util.ARGB;

import java.util.List;

public class WoolMapArtClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BetterMapTextures.registerTick();
		PaintingTableClientNetworking.register();
		MenuScreens.register(WoolMapArt.PAINTING_TABLE_MENU, PaintingTableScreen::new);
		BlockColorRegistry.register(List.of(new BlockTintSource() {
			@Override
			public int color(net.minecraft.world.level.block.state.BlockState state) {
				return ARGB.opaque(WoolMapArt.PIXEL_COLORS[state.getValue(PixelBlock.COLOR)]);
			}
		}), WoolMapArt.PIXEL_BLOCK);
	}
}
