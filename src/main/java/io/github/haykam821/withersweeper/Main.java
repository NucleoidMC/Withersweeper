package io.github.haykam821.withersweeper;

import io.github.haykam821.withersweeper.game.WithersweeperConfig;
import io.github.haykam821.withersweeper.game.phase.WithersweeperWaitingPhase;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import xyz.nucleoid.plasmid.api.game.GameType;
import xyz.nucleoid.plasmid.api.game.GameTypes;
import xyz.nucleoid.plasmid.api.game.stats.StatisticKey;

public class Main implements ModInitializer {
	private static final String MOD_ID = "withersweeper";

	private static final Identifier WITHERSWEEPER_ID = Main.identifier("withersweeper");
	public static final GameType<WithersweeperConfig> WITHERSWEEPER_TYPE = GameTypes.register(WITHERSWEEPER_ID, WithersweeperConfig.CODEC, WithersweeperWaitingPhase::open);

	private static final Identifier MINES_REVEALED_ID = Main.identifier("mines_revealed");
	public static final StatisticKey<Integer> MINES_REVEALED = StatisticKey.intKey(MINES_REVEALED_ID);

	private static final Identifier FIELDS_UNCOVERED_ID = Main.identifier("fields_uncovered");
	public static final StatisticKey<Integer> FIELDS_UNCOVERED = StatisticKey.intKey(FIELDS_UNCOVERED_ID);

	@Override
	public void onInitialize() {
		return;
	}

	public static Identifier identifier(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}