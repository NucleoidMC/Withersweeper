package io.github.haykam821.withersweeper.game;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.haykam821.withersweeper.game.board.BoardConfig;
import net.minecraft.SharedConstants;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.config.WaitingLobbyConfig;
import xyz.nucleoid.plasmid.api.game.stats.GameStatisticBundle;

public class WithersweeperConfig {
	public static final MapCodec<WithersweeperConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> {
		return instance.group(
			BoardConfig.CODEC.fieldOf("board").forGetter(WithersweeperConfig::getBoardConfig),
			WaitingLobbyConfig.CODEC.fieldOf("players").forGetter(WithersweeperConfig::getPlayerConfig),
			IntProviders.NON_NEGATIVE_CODEC.optionalFieldOf("ticks_until_close", ConstantInt.of(SharedConstants.TICKS_PER_SECOND * 5)).forGetter(WithersweeperConfig::getTicksUntilClose),
			GameStatisticBundle.NAMESPACE_CODEC.optionalFieldOf("statistic_bundle_namespace").forGetter(WithersweeperConfig::getStatisticBundleNamespace),
			ItemStackTemplate.CODEC.optionalFieldOf("flag_stack", new ItemStackTemplate(Items.BANNER.red())).forGetter(WithersweeperConfig::getFlagStack),
			Codec.INT.optionalFieldOf("max_mistakes", 1).forGetter(WithersweeperConfig::getMaxMistakes)
		).apply(instance, WithersweeperConfig::new);
	});

	private final BoardConfig boardConfig;
	private final WaitingLobbyConfig playerConfig;
	private final IntProvider ticksUntilClose;
	private final Optional<String> statisticBundleNamespace;
	private final ItemStackTemplate flagStack;
	private final int maxMistakes;

	public WithersweeperConfig(BoardConfig boardConfig, WaitingLobbyConfig playerConfig, IntProvider ticksUntilClose, Optional<String> statisticBundleNamespace, ItemStackTemplate flagStack, int maxMistakes) {
		this.boardConfig = boardConfig;
		this.playerConfig = playerConfig;
		this.ticksUntilClose = ticksUntilClose;
		this.statisticBundleNamespace = statisticBundleNamespace;
		this.flagStack = flagStack;
		this.maxMistakes = maxMistakes;
	}

	public BoardConfig getBoardConfig() {
		return this.boardConfig;
	}

	public WaitingLobbyConfig getPlayerConfig() {
		return this.playerConfig;
	}

	public IntProvider getTicksUntilClose() {
		return this.ticksUntilClose;
	}

	private Optional<String> getStatisticBundleNamespace() {
		return this.statisticBundleNamespace;
	}

	public GameStatisticBundle getStatisticBundle(GameSpace gameSpace) {
		if (this.statisticBundleNamespace.isEmpty()) {
			return null;
		}
		return gameSpace.getStatistics().bundle(this.statisticBundleNamespace.get());
	}

	public ItemStackTemplate getFlagStack() {
		return this.flagStack;
	}

	public int getMaxMistakes() {
		return this.maxMistakes;
	}
}