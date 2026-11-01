package com.example.woolmapart;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class PaintingTableBlock extends BaseEntityBlock {
	public PaintingTableBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PaintingTableBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
		Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof PaintingTableBlockEntity table) player.openMenu(table);
		}
		return InteractionResult.SUCCESS;
	}
}
