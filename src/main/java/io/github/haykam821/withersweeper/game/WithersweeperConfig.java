package io.github.haykam821.withersweeper.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.haykam821.withersweeper.game.board.BoardConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import xyz.nucleoid.plasmid.game.config.PlayerConfig;

public class WithersweeperConfig {
	public static final Codec<WithersweeperConfig> CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
			BoardConfig.CODEC.fieldOf("board").forGetter(WithersweeperConfig::getBoardConfig),
			PlayerConfig.CODEC.fieldOf("players").forGetter(WithersweeperConfig::getPlayerConfig),
			ItemStack.CODEC.optionalFieldOf("flag_stack", new ItemStack(Items.RED_BANNER)).forGetter(WithersweeperConfig::getFlagStack),
			Codec.INT.optionalFieldOf("max_mistakes", 1).forGetter(WithersweeperConfig::getMaxMistakes),
			Codec.BOOL.optionalFieldOf("uncover_neighbors", false).forGetter(WithersweeperConfig::uncoverNeighbors)
		).apply(instance, WithersweeperConfig::new);
	});

	private final BoardConfig boardConfig;
	private final PlayerConfig playerConfig;
	private final ItemStack flagStack;
	private final int maxMistakes;
	private final boolean uncoverNeighbors;

	public WithersweeperConfig(BoardConfig boardConfig, PlayerConfig playerConfig, ItemStack flagStack, int maxMistakes, boolean uncoverNeighbors) {
		this.boardConfig = boardConfig;
		this.playerConfig = playerConfig;
		this.flagStack = flagStack;
		this.maxMistakes = maxMistakes;
		this.uncoverNeighbors = uncoverNeighbors;
	}

	public BoardConfig getBoardConfig() {
		return this.boardConfig;
	}

	public PlayerConfig getPlayerConfig() {
		return this.playerConfig;
	}

	public ItemStack getFlagStack() {
		return this.flagStack;
	}

	public int getMaxMistakes() {
		return this.maxMistakes;
	}

	public boolean uncoverNeighbors() {
		return this.uncoverNeighbors;
	}
}