package io.github.haykam821.withersweeper.game.phase;

import io.github.haykam821.withersweeper.game.WithersweeperConfig;
import io.github.haykam821.withersweeper.game.board.Board;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.plasmid.api.game.GameOpenContext;
import xyz.nucleoid.plasmid.api.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.api.game.GameResult;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.level.generator.TemplateChunkGenerator;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class WithersweeperWaitingPhase {
	private final ServerLevel level;
	public final GameSpace gameSpace;
	private final WithersweeperConfig config;
	private final Board board;

	public WithersweeperWaitingPhase(GameSpace gameSpace, ServerLevel level, WithersweeperConfig config, Board board) {
		this.level = level;
		this.gameSpace = gameSpace;
		this.config = config;
		this.board = board;
	}

	public static GameOpenProcedure open(GameOpenContext<WithersweeperConfig> context) {
		Board board = new Board(context.config().getBoardConfig());
		MapTemplate template = board.buildFromTemplate();

		RuntimeLevelConfig levelConfig = new RuntimeLevelConfig()
			.setGenerator(new TemplateChunkGenerator(context.server(), template));

		return context.openWithLevel(levelConfig, (activity, level) -> {
			WithersweeperWaitingPhase phase = new WithersweeperWaitingPhase(activity.getGameSpace(), level, context.config(), board);
			GameWaitingLobby.addTo(activity, context.config().getPlayerConfig());

			// Rules
			WithersweeperActivePhase.setRules(activity);

			// Listeners
			activity.listen(GameActivityEvents.TICK, phase::tick);
			activity.listen(GamePlayerEvents.ACCEPT, phase::onAcceptPlayers);
			activity.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
			activity.listen(GamePlayerEvents.ADD, phase::addPlayer);
			activity.listen(PlayerDeathEvent.EVENT, phase::onPlayerDeath);
			activity.listen(GameActivityEvents.REQUEST_START, phase::requestStart);
		});
	}

	private void tick() {
		for (ServerPlayer player : this.gameSpace.getPlayers()) {
			if (player.getY() < 0) {
				this.spawn(player);
			}
		}
	}

	private JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.level, WithersweeperActivePhase.getSpawnPos(this.config)).thenRunForEach(player -> {
			player.setGameMode(GameType.ADVENTURE);
		});
	}

	private GameResult requestStart() {
		WithersweeperActivePhase.open(this.gameSpace, this.level, this.config, this.board);
		return GameResult.ok();
	}

	private void addPlayer(ServerPlayer player) {
		this.spawn(player);
	}

	private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
		// Respawn player
		this.spawn(player);
		return EventResult.DENY;
	}

	private void spawn(ServerPlayer player) {
		WithersweeperActivePhase.spawn(player, this.level, this.config);
	}
}