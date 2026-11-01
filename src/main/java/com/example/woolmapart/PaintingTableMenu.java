package com.example.woolmapart;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class PaintingTableMenu extends AbstractContainerMenu {
	public static final int CREATE_BUTTON = 1;
	private static final int PANEL_X = 8;
	private static final int PANEL_Y = 28;
	private static final int LAYOUT_Y_OFFSET = 24;
	private final BlockPos tablePos;
	private final Container tableInventory;
	private String selectedFilename = "";

	public PaintingTableMenu(int syncId, Inventory inventory) {
		this(syncId, inventory, new SimpleContainer(PaintingTableBlockEntity.SLOT_COUNT), null);
	}

	public PaintingTableMenu(int syncId, Inventory inventory, BlockPos tablePos) {
		this(syncId, inventory,
			inventory.player.level().getBlockEntity(tablePos) instanceof PaintingTableBlockEntity entity
				? entity : new SimpleContainer(PaintingTableBlockEntity.SLOT_COUNT),
			tablePos);
	}

	public PaintingTableMenu(int syncId, Inventory inventory, PaintingTableBlockEntity blockEntity) {
		this(syncId, inventory, blockEntity, blockEntity.getBlockPos());
	}

	private PaintingTableMenu(int syncId, Inventory inventory, Container tableInventory, BlockPos tablePos) {
		super(WoolMapArt.PAINTING_TABLE_MENU, syncId);
		checkContainerSize(tableInventory, PaintingTableBlockEntity.SLOT_COUNT);
		this.tableInventory = tableInventory;
		this.tablePos = tablePos;
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(tableInventory, column + row * 9,
					PANEL_X + 8 + column * 18, PANEL_Y + 92 + LAYOUT_Y_OFFSET + row * 18));
			}
		}
		addSlot(new CanvasSlot(tableInventory, tablePos, false, 110, 192 + LAYOUT_Y_OFFSET));
		addSlot(new CanvasSlot(tableInventory, tablePos, true, 172, 192 + LAYOUT_Y_OFFSET));
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(inventory, column + row * 9 + 9,
					PANEL_X + 8 + column * 18, 242 + LAYOUT_Y_OFFSET + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(inventory, column, PANEL_X + 8 + column * 18, 300 + LAYOUT_Y_OFFSET));
		}
	}

	public BlockPos tablePos() {
		return tablePos;
	}

	public String selectedFilename() {
		return selectedFilename;
	}

	public Container tableInventory() {
		return tableInventory;
	}

	public void setSelectedFilename(String filename) {
		selectedFilename = filename;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == CREATE_BUTTON && !player.level().isClientSide() && tablePos != null) {
			WoolMapArt.startPainting(player, tablePos);
			return true;
		}
		return false;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(slotIndex);
		if (slot == null || !slot.hasItem()) return result;
		ItemStack stack = slot.getItem();
		result = stack.copy();
		if (slotIndex < PaintingTableBlockEntity.SLOT_COUNT) {
			if (!moveItemStackTo(stack, PaintingTableBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
		} else if (slotIndex == PaintingTableBlockEntity.SLOT_COUNT) {
			if (!moveItemStackTo(stack, PaintingTableBlockEntity.SLOT_COUNT + 2, slots.size(), true)) return ItemStack.EMPTY;
		} else if (slotIndex == PaintingTableBlockEntity.SLOT_COUNT + 1) {
			if (!moveItemStackTo(stack, PaintingTableBlockEntity.SLOT_COUNT + 2, slots.size(), true)) return ItemStack.EMPTY;
		} else {
			if (stack.is(WoolMapArt.BLANK_CANVAS)
				&& !slots.get(PaintingTableBlockEntity.SLOT_COUNT).hasItem()
				&& moveItemStackTo(stack, PaintingTableBlockEntity.SLOT_COUNT,
					PaintingTableBlockEntity.SLOT_COUNT + 1, false)) {
				// moved into the dedicated canvas input
			} else if (!moveItemStackTo(stack, 0, PaintingTableBlockEntity.SLOT_COUNT, false)) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setByPlayer(stack);
		slot.onTake(player, stack);
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return tablePos == null
			|| AbstractContainerMenu.stillValid(
				net.minecraft.world.inventory.ContainerLevelAccess.create(player.level(), tablePos),
				player, WoolMapArt.PAINTING_TABLE);
	}

	private static final class CanvasSlot extends Slot {
		private final BlockPos tablePos;
		private final boolean output;
		private final Container fallback;

		private CanvasSlot(Container fallback, BlockPos tablePos, boolean output, int x, int y) {
			super(new SimpleContainer(1), 0, x, y);
			this.fallback = fallback;
			this.tablePos = tablePos;
			this.output = output;
		}

		private PaintingTableBlockEntity entity() {
			if (tablePos == null || !(fallback instanceof PaintingTableBlockEntity entity)) return null;
			return entity;
		}

		@Override
		public ItemStack getItem() {
			PaintingTableBlockEntity entity = entity();
			return entity == null ? super.getItem() : (output ? entity.getPaintedCanvas() : entity.getBlankCanvas());
		}

		@Override
		public void set(ItemStack stack) {
			PaintingTableBlockEntity entity = entity();
			if (entity == null) super.set(stack);
			else if (output) entity.setPaintedCanvas(stack);
			else entity.setBlankCanvas(stack);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return !output && stack.is(WoolMapArt.BLANK_CANVAS);
		}

		@Override
		public boolean mayPickup(Player player) {
			return output || super.mayPickup(player);
		}

		@Override
		public ItemStack remove(int amount) {
			PaintingTableBlockEntity entity = entity();
			if (entity == null) return super.remove(amount);
			ItemStack current = output ? entity.getPaintedCanvas() : entity.getBlankCanvas();
			ItemStack result = current.split(amount);
			if (output) entity.setPaintedCanvas(current);
			else entity.setBlankCanvas(current);
			return result;
		}
	}
}
