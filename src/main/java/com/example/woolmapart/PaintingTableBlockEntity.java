package com.example.woolmapart;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PaintingTableBlockEntity extends BlockEntity implements Container, ExtendedMenuProvider<BlockPos> {
	public static final int SLOT_COUNT = 27;
	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
	private ItemStack blankCanvas = ItemStack.EMPTY;
	private ItemStack paintedCanvas = ItemStack.EMPTY;

	public PaintingTableBlockEntity(BlockPos pos, BlockState state) {
		super(WoolMapArt.PAINTING_TABLE_BLOCK_ENTITY, pos, state);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		ContainerHelper.loadAllItems(input, items);
		blankCanvas = input.read("BlankCanvas", ItemStack.CODEC).orElse(ItemStack.EMPTY);
		paintedCanvas = input.read("PaintedCanvas", ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
		if (!blankCanvas.isEmpty()) output.store("BlankCanvas", ItemStack.CODEC, blankCanvas);
		if (!paintedCanvas.isEmpty()) output.store("PaintedCanvas", ItemStack.CODEC, paintedCanvas);
	}

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) return false;
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack result = ContainerHelper.removeItem(items, slot, amount);
		if (!result.isEmpty()) setChanged();
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		stack.limitSize(getMaxStackSize());
		setChanged();
	}

	@Override
	public void setChanged() {
		super.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		items.clear();
		blankCanvas = ItemStack.EMPTY;
		paintedCanvas = ItemStack.EMPTY;
		setChanged();
	}

	public ItemStack getBlankCanvas() {
		return blankCanvas;
	}

	public void setBlankCanvas(ItemStack stack) {
		blankCanvas = stack;
		setChanged();
	}

	public ItemStack getPaintedCanvas() {
		return paintedCanvas;
	}

	public void setPaintedCanvas(ItemStack stack) {
		paintedCanvas = stack;
		setChanged();
	}

	public boolean hasBlankCanvas() {
		return !blankCanvas.isEmpty() && blankCanvas.is(WoolMapArt.BLANK_CANVAS);
	}

	@Override
	public Component getDisplayName() {
		return Component.literal("Painting Table");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new PaintingTableMenu(syncId, inventory, this);
	}

	@Override
	public BlockPos getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
		return getBlockPos();
	}

	public int countPixelBlocks() {
		int count = 0;
		for (ItemStack stack : items) {
			if (stack.is(WoolMapArt.PIXEL_BLOCK.asItem())) count += stack.getCount();
		}
		return count;
	}

	public boolean consumePixelBlock() {
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			if (stack.is(WoolMapArt.PIXEL_BLOCK.asItem())) {
				stack.shrink(1);
				if (stack.isEmpty()) items.set(i, ItemStack.EMPTY);
				setChanged();
				return true;
			}
		}
		return false;
	}
}
