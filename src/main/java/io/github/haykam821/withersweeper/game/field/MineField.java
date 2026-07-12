package io.github.haykam821.withersweeper.game.field;

import io.github.haykam821.withersweeper.Main;
import io.github.haykam821.withersweeper.game.phase.WithersweeperActivePhase;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import xyz.nucleoid.plasmid.api.game.stats.StatisticMap;

public class MineField extends Field {
	private static final BlockState STATE = Blocks.TNT.defaultBlockState();
	private static final Component INFO_MESSAGE = Component.translatable("text.withersweeper.info.mine");

	@Override
	public boolean isCompleted() {
		return true;
	}

	@Override
	public void uncover(BlockPos pos, ServerPlayer uncoverer, WithersweeperActivePhase phase) {
		super.uncover(pos, uncoverer, phase);

		StatisticMap statistics = phase.getStatisticsForPlayer(uncoverer);
		if (statistics != null) {
			statistics.increment(Main.MINES_REVEALED, 1);
		}

		phase.mistakes += 1;
	}

	@Override
	public BlockState getBlockState() {
		return STATE;
	}

	@Override
	public Component getInfoMessage() {
		return INFO_MESSAGE;
	}
}