package com.example.woolmapart;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PaintingTableScreen extends AbstractContainerScreen<PaintingTableMenu> {
	private static final int IMAGE_LIST_VISIBLE_ROWS = 5;
	private static final int IMAGE_LIST_ROW_HEIGHT = 22;
	private static final int LAYOUT_Y_OFFSET = 24;
	private static final int SEARCH_ROW_Y = 38;
	private static final int IMAGE_LIST_Y = 68;
	private static int required = (128 * 128 + 99) / 100;
	private static int remaining = required;
	private static String status = "Enter an image filename from the mapart folder.";
	private EditBox filename;
	private List<String> images = List.of();
	private final List<Button> imageButtons = new ArrayList<>();
	private boolean showImageList;
	private int imageScroll;
	private String selectedImage = "";

	public PaintingTableScreen(PaintingTableMenu menu, net.minecraft.world.entity.player.Inventory inventory,
		Component title) {
		super(menu, inventory, title, 360, 444);
	}

	public static void receiveStatus(int requiredCount, int remainingCount, String message) {
		required = requiredCount;
		remaining = remainingCount;
		status = message;
	}

	@Override
	protected void init() {
		super.init();
		titleLabelX = -1000;
		titleLabelY = -1000;
		inventoryLabelX = -1000;
		inventoryLabelY = -1000;
		int left = leftPos + 8;
		filename = addRenderableWidget(new EditBox(font, left, topPos + SEARCH_ROW_Y + LAYOUT_Y_OFFSET, 190, 20,
			Component.literal("Image filename")));
		filename.setMaxLength(256);
		filename.setResponder(value -> {
			if (!value.equals(selectedImage)) selectedImage = "";
		});
		addRenderableWidget(Button.builder(Component.literal("Check"), button -> {
			showImageList = false;
			filename.setFocused(false);
			ClientPlayNetworking.send(new PaintingTablePayloads.Select(filename.getValue()));
		}).bounds(left + 198, topPos + SEARCH_ROW_Y + LAYOUT_Y_OFFSET, 102, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Create Canvas"), button ->
			Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId,
				PaintingTableMenu.CREATE_BUTTON))
			.bounds(left, topPos + 392 + LAYOUT_Y_OFFSET, 300, 20).build());

		images = imageNames();
		for (int row = 0; row < IMAGE_LIST_VISIBLE_ROWS; row++) {
			int buttonRow = row;
			imageButtons.add(addRenderableWidget(Button.builder(Component.empty(), button -> {
				int index = imageScroll + buttonRow;
				if (index >= 0 && index < images.size()) {
					String image = images.get(index);
					selectedImage = image;
					filename.setValue(image);
					filename.setFocused(false);
					showImageList = false;
					ClientPlayNetworking.send(new PaintingTablePayloads.Select(image));
				}
			}).bounds(left, topPos + IMAGE_LIST_Y + LAYOUT_Y_OFFSET + row * IMAGE_LIST_ROW_HEIGHT, 300, 20).build()));
		}
		updateImageButtons();
	}

	private static List<String> imageNames() {
		File directory = new File("mapart");
		File[] files = directory.listFiles(file -> file.isFile()
			&& (file.getName().toLowerCase().endsWith(".png")
			|| file.getName().toLowerCase().endsWith(".jpg")
			|| file.getName().toLowerCase().endsWith(".jpeg")));
		List<String> names = new ArrayList<>();
		if (files != null) {
			for (File file : files) names.add(file.getName());
			names.sort(String.CASE_INSENSITIVE_ORDER);
		}
		return names;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xFF202020);
		int left = leftPos;
		int top = topPos;
		graphics.fill(left - 8, top - 8, left + imageWidth + 8, top + imageHeight, 0xFF303030);

		graphics.text(font, "Image filename:", left + 8, top + 20 + LAYOUT_Y_OFFSET, 0xFFFFFFFF);
		if (showImageList) {
			int listTop = top + IMAGE_LIST_Y - 2 + LAYOUT_Y_OFFSET;
			int listBottom = listTop + IMAGE_LIST_VISIBLE_ROWS * IMAGE_LIST_ROW_HEIGHT + 2;
			graphics.fill(left + 7, listTop, left + 309, listBottom, 0xFF1E1E1E);
		}
		graphics.text(font, "Table inventory", left + 8, top + 105 + LAYOUT_Y_OFFSET, 0xFFFFFFFF);
		drawSlotGrid(graphics, left + 16, top + 120 + LAYOUT_Y_OFFSET, 3, 9);

		graphics.text(font, "Blank", left + 100, top + 180 + LAYOUT_Y_OFFSET, 0xFFFFFFFF);
		graphics.text(font, "Painted", left + 162, top + 180 + LAYOUT_Y_OFFSET, 0xFFFFFFFF);
		drawSlotGrid(graphics, left + 110, top + 192 + LAYOUT_Y_OFFSET, 1, 1);
		graphics.text(font, "->", left + 148, top + 197 + LAYOUT_Y_OFFSET, 0xFFFFFFFF);
		drawSlotGrid(graphics, left + 172, top + 192 + LAYOUT_Y_OFFSET, 1, 1);

		graphics.text(font, "Player inventory", left + 8, top + 224 + LAYOUT_Y_OFFSET, 0xFFFFFFFF);
		drawSlotGrid(graphics, left + 16, top + 242 + LAYOUT_Y_OFFSET, 3, 9);
		drawSlotGrid(graphics, left + 16, top + 300 + LAYOUT_Y_OFFSET, 1, 9);

		graphics.text(font, "Required pixel_block: " + required, left + 8, top + 330 + LAYOUT_Y_OFFSET, 0xFFFFFFFF);
		graphics.text(font, "Remaining: " + remaining, left + 8, top + 350 + LAYOUT_Y_OFFSET, 0xFFFFFFFF);
		graphics.textWithWordWrap(font, Component.literal(status), left + 8, top + 368 + LAYOUT_Y_OFFSET, 300, 0xFFE0E0E0);

		if (filename != null && filename.isFocused() && !images.isEmpty()) showImageList = true;
		updateImageButtons();
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void updateImageButtons() {
		for (int row = 0; row < imageButtons.size(); row++) {
			Button button = imageButtons.get(row);
			int index = imageScroll + row;
			boolean visible = showImageList && index < images.size();
			button.visible = visible;
			button.active = visible;
			if (visible) {
				button.setMessage(Component.literal(images.get(index)));
				button.setX(leftPos + 8);
				button.setY(topPos + IMAGE_LIST_Y + LAYOUT_Y_OFFSET + row * IMAGE_LIST_ROW_HEIGHT);
			}
		}
	}

	private void drawSlotGrid(GuiGraphicsExtractor graphics, int left, int top, int rows, int columns) {
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				int x = left + column * 18;
				int y = top + row * 18;
				graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF111111);
				graphics.fill(x, y, x + 16, y + 16, 0xFF8A8A8A);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int left = leftPos + 8;
		int top = topPos;
		boolean inSearch = event.x() >= left && event.x() <= left + 190
			&& event.y() >= top + SEARCH_ROW_Y + LAYOUT_Y_OFFSET
			&& event.y() <= top + SEARCH_ROW_Y + 20 + LAYOUT_Y_OFFSET;
		boolean inList = event.x() >= left && event.x() <= left + 300
			&& event.y() >= top + IMAGE_LIST_Y + LAYOUT_Y_OFFSET
			&& event.y() < top + IMAGE_LIST_Y + LAYOUT_Y_OFFSET + IMAGE_LIST_VISIBLE_ROWS * IMAGE_LIST_ROW_HEIGHT;
		if (showImageList && event.button() == 0 && !inSearch && !inList) showImageList = false;
		if (event.button() == 0 && inSearch) {
			showImageList = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (showImageList && images.size() > IMAGE_LIST_VISIBLE_ROWS) {
			int maxScroll = Math.max(0, images.size() - IMAGE_LIST_VISIBLE_ROWS);
			imageScroll = Math.max(0, Math.min(maxScroll,
				imageScroll + (verticalAmount < 0 ? 1 : -1)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}
}
