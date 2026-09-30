package io.github.haykam821.withersweeper.game.phase;

import java.util.HashSet;
import java.util.Set;

import io.github.haykam821.withersweeper.game.WithersweeperConfig;
import io.github.haykam821.withersweeper.game.board.Board;
import io.github.haykam821.withersweeper.game.field.Field;
import io.github.haykam821.withersweeper.game.field.FieldVisibility;
import net.minecraft.core.Direction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.plasmid.api.game.GameActivity;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.game.stats.GameStatisticBundle;
import xyz.nucleoid.plasmid.api.game.stats.StatisticKeys;
import xyz.nucleoid.plasmid.api.game.stats.StatisticMap;
import xyz.nucleoid.plasmid.api.util.PlayerRef;
import xyz.nucleoid.plasmid.api.util.PlayerUtil;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.block.BlockPunchEvent;
import xyz.nucleoid.stimuli.event.block.BlockUseEvent;
import xyz.nucleoid.stimuli.event.item.ItemThrowEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class WithersweeperActivePhase {
	private final ServerLevel level;
	public final GameSpace gameSpace;
	private final WithersweeperConfig config;
	private final Board board;
	private final GameStatisticBundle statistics;
	private final Set<PlayerRef> participants = new HashSet<>();
	private int timeElapsed = 0;
	public int mistakes = 0;

	private int ticksUntilClose = -1;

	public WithersweeperActivePhase(GameSpace gameSpace, ServerLevel level, WithersweeperConfig config, Board board) {
		this.level = level;
		this.gameSpace = gameSpace;
		this.config = config;
		this.board = board;
		this.statistics = config.getStatisticBundle(gameSpace);
	}

	public static void open(GameSpace gameSpace, ServerLevel level, WithersweeperConfig config, Board board) {
		gameSpace.setActivity(activity -> {
			WithersweeperActivePhase phase = new WithersweeperActivePhase(gameSpace, level, config, board);

			// Rules
			WithersweeperActivePhase.setRules(activity);

			// Listeners
			activity.listen(ItemThrowEvent.EVENT, phase::onThrowItem);
			activity.listen(GameActivityEvents.ENABLE, phase::enable);
			activity.listen(GameActivityEvents.TICK, phase::tick);
			activity.listen(GamePlayerEvents.ACCEPT, phase::onAcceptPlayers);
			activity.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
			activity.listen(PlayerDeathEvent.EVENT, phase::onPlayerDeath);
			activity.listen(BlockPunchEvent.EVENT, phase::onPunchBlock);
			activity.listen(BlockUseEvent.EVENT, phase::onUseBlock);
		});
	}

	protected static void setRules(GameActivity activity) {
		activity.deny(GameRuleType.BLOCK_DROPS);
		activity.deny(GameRuleType.CRAFTING);
		activity.deny(GameRuleType.FALL_DAMAGE);
		activity.deny(GameRuleType.HUNGER);
		activity.deny(GameRuleType.MODIFY_ARMOR);
		activity.deny(GameRuleType.MODIFY_INVENTORY);
		activity.deny(GameRuleType.PORTALS);
		activity.deny(GameRuleType.PVP);
		activity.deny(GameRuleType.BREAK_BLOCKS);
	}

	private void enable() {
		this.updateFlagCount();
	}

	private void tick() {
		// Decrease ticks until game end to zero
		if (this.isGameEnding()) {
			if (this.ticksUntilClose == 0) {
				this.gameSpace.close(GameCloseReason.FINISHED);
			}

			this.ticksUntilClose -= 1;
			return;
		}

		this.timeElapsed += 1;

		for (ServerPlayer player : this.gameSpace.getPlayers()) {
			if (player.getY() < 0) {
				this.spawn(player);
			}
		}
	}

	private Component getMistakeText(Player causer) {
		Component displayName = causer.getDisplayName();

		if (this.config.getMaxMistakes() <= 1) {
			return Component.translatable("text.withersweeper.reveal_mine", displayName).withStyle(ChatFormatting.RED);
		} else {
			return Component.translatable("text.withersweeper.reveal_mine.max_mistakes", displayName, this.config.getMaxMistakes()).withStyle(ChatFormatting.RED);
		}
	}

	private void checkMistakes(Player causer) {
		if (this.mistakes < this.config.getMaxMistakes()) return;

		Component text = this.getMistakeText(causer);
		this.board.uncoverMines();
		for (ServerPlayer player : this.gameSpace.getPlayers()) {
			player.sendSystemMessage(text, false);
			PlayerUtil.playSoundToPlayer(player, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1, 1);
		}

		if (this.statistics != null) {
			for (PlayerRef participant : this.participants) {
				this.statistics.forPlayer(participant).increment(StatisticKeys.GAMES_LOST, 1);
			}
		}

		this.endGame();
	}

	private void updateFlagCount() {
		int remainingFlags = Math.max(this.board.getRemainingFlags(), 0);
		float experienceProgress = 1F - (float) remainingFlags / this.config.getBoardConfig().mines;

		for (ServerPlayer player : this.gameSpace.getPlayers()) {
			player.setExperienceLevels(remainingFlags);
			player.experienceProgress = experienceProgress;
		}
	}

	private EventResult modifyFlag(ServerPlayer uncoverer, BlockPos pos, Field field) {
		if (field.getVisibility() != FieldVisibility.UNCOVERED) {
			if (field.getVisibility() == FieldVisibility.FLAGGED) {
				field.setVisibility(FieldVisibility.COVERED);
				this.level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1, 1);
			} else {
				field.setVisibility(FieldVisibility.FLAGGED);
				this.level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1, 1);
			}

			return EventResult.ALLOW;
		}

		return EventResult.PASS;
	}

	private EventResult uncoverField(ServerPlayer uncoverer, BlockPos pos, Field field) {
		if (field.getVisibility() == FieldVisibility.COVERED) {
			field.uncover(pos, uncoverer, this);
			this.level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.5f, 1);

			return EventResult.ALLOW;
		}

		return EventResult.PASS;
	}

	private void addParticipant(ServerPlayer player) {
		PlayerRef participant = PlayerRef.of(player);
		if (this.participants.add(participant) && this.statistics != null) {
			this.statistics.forPlayer(participant).increment(StatisticKeys.GAMES_PLAYED, 1);
		}
	}

	private EventResult onPunchBlock(ServerPlayer player, Direction direction, BlockPos pos) {
		this.modifyField(player, true, pos);
		return EventResult.DENY;
	}

	private InteractionResult onUseBlock(ServerPlayer player, InteractionHand hand, BlockHitResult hitResult) {
		if (hand == InteractionHand.MAIN_HAND) {
			return this.modifyField(player, false, hitResult.getBlockPos()).asActionResult();
		}

		return InteractionResult.FAIL;
	}

	private EventResult modifyField(ServerPlayer uncoverer, boolean uncover, BlockPos pos) {
		if (this.isGameEnding()) return EventResult.PASS;

		if (pos.getY() != 0) return EventResult.PASS;
		if (!this.board.isValidPos(pos.getX(), pos.getZ())) return EventResult.PASS;

		this.board.placeMines(pos.getX(), pos.getZ(), this.level.getRandom());

		Field field = this.board.getField(pos.getX(), pos.getZ());
		EventResult result = uncover ? this.uncoverField(uncoverer, pos, field) : this.modifyFlag(uncoverer, pos, field);

		if (result == EventResult.ALLOW) {
			this.addParticipant(uncoverer);

			this.checkMistakes(uncoverer);
			this.board.build(this.level);
			this.updateFlagCount();

			if (this.board.isCompleted()) {
				Component text = Component.translatable("text.withersweeper.complete", this.timeElapsed / 20).withStyle(ChatFormatting.GOLD);
				for (ServerPlayer player : this.gameSpace.getPlayers()) {
					player.sendSystemMessage(text, false);
					PlayerUtil.playSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, SoundSource.UI, 1, 1);
				}

				if (this.statistics != null) {
					for (PlayerRef participant : this.participants) {
						this.statistics.forPlayer(participant).increment(StatisticKeys.GAMES_WON, 1);
						this.statistics.forPlayer(participant).increment(StatisticKeys.QUICKEST_TIME, this.timeElapsed);
					}
				}

				this.endGame();
			}
		}

		return result;
	}

	private JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.level, WithersweeperActivePhase.getSpawnPos(this.config)).thenRunForEach(player -> {
			player.setGameMode(GameType.SURVIVAL);
		});
	}

	private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
		// Respawn player
		this.spawn(player);
		return EventResult.DENY;
	}

	private boolean attemptToSendInfoMessage(ServerPlayer player, BlockPos pos) {
		if (pos.getY() != 0) return false;
		if (!this.board.isValidPos(pos.getX(), pos.getZ())) return false;

		Field field = this.board.getField(pos.getX(), pos.getZ());

		Component message = field.getCoveredInfoMessage().copy().withStyle(ChatFormatting.DARK_PURPLE);
		player.sendSystemMessage(message, true);

		return true;
	}

	private EventResult onThrowItem(ServerPlayer player, int slot, ItemStack stack) {
		HitResult hit = player.pick(8, 0, false);
		if (hit.getType() == HitResult.Type.BLOCK) {
			this.attemptToSendInfoMessage(player, ((BlockHitResult) hit).getBlockPos());
		}

		return EventResult.DENY;
	}

	public StatisticMap getStatisticsForPlayer(ServerPlayer player) {
		if (this.statistics == null) {
			return null;
		}
		return this.statistics.forPlayer(player);
	}

	public Board getBoard() {
		return this.board;
	}

	private void spawn(ServerPlayer player) {
		WithersweeperActivePhase.spawn(player, this.level, this.config);
	}

	private void endGame() {
		this.ticksUntilClose = this.config.getTicksUntilClose().sample(this.level.getRandom());
	}

	private boolean isGameEnding() {
		return this.ticksUntilClose >= 0;
	}

	protected static void spawn(ServerPlayer player, ServerLevel level, WithersweeperConfig config) {
		Vec3 spawnPos = WithersweeperActivePhase.getSpawnPos(config);
		player.teleportTo(level, spawnPos.x(), spawnPos.y(), spawnPos.z(), Set.of(), 0, 0, true);
	}

	protected static Vec3 getSpawnPos(WithersweeperConfig config) {
		return new Vec3(config.getBoardConfig().x / 2d, 1, config.getBoardConfig().x / 2d);
	}
}