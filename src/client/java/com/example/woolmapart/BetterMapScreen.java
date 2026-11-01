package com.example.woolmapart;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

public class BetterMapScreen extends Screen {
	private static final int SIZE = 128;
	private static final int SCALE = 4;
	private final int[] snapshot;

	public BetterMapScreen(int[] snapshot) {
		super(Component.literal("Better Map"));
		this.snapshot = snapshot;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF202020);
		Minecraft minecraft = Minecraft.getInstance();
		int left = (graphics.guiWidth() - SIZE * SCALE) / 2;
		int top = Math.max(24, (graphics.guiHeight() - SIZE * SCALE) / 2);
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				int color = 0xFF000000 | snapshot[z * SIZE + x];
				graphics.fill(left + x * SCALE, top + z * SCALE,
					left + (x + 1) * SCALE, top + (z + 1) * SCALE, color);
			}
		}
		graphics.centeredText(minecraft.font, title, graphics.guiWidth() / 2, 8, 0xFFFFFFFF);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}
}
