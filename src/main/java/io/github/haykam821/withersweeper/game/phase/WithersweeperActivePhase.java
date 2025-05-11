package io.github.haykam821.withersweeper.game.phase;

import java.util.HashSet;
import java.util.Set;

import io.github.haykam821.withersweeper.game.WithersweeperConfig;
import io.github.haykam821.withersweeper.game.board.Board;
import io.github.haykam821.withersweeper.game.field.Field;
import io.github.haykam821.withersweeper.game.field.FieldVisibility;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
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
import xyz.nucleoid.plasmid.api.util.ItemStackBuilder;
import xyz.nucleoid.plasmid.api.util.PlayerRef;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.block.BlockUseEvent;
import xyz.nucleoid.stimuli.event.item.ItemThrowEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class WithersweeperActivePhase {
	private final ServerWorld world;
	public final GameSpace gameSpace;
	private final WithersweeperConfig config;
	private final Board board;
	private final GameStatisticBundle statistics;
	private final Set<PlayerRef> participants = new HashSet<>();
	private int timeElapsed = 0;
	public int mistakes = 0;

	private int ticksUntilClose = -1;

	public WithersweeperActivePhase(GameSpace gameSpace, ServerWorld world, WithersweeperConfig config, Board board) {
		this.world = world;
		this.gameSpace = gameSpace;
		this.config = config;
		this.board = board;
		this.statistics = config.getStatisticBundle(gameSpace);
	}

	public static void open(GameSpace gameSpace, ServerWorld world, WithersweeperConfig config, Board board) {
		gameSpace.setActivity(activity -> {
			WithersweeperActivePhase phase = new WithersweeperActivePhase(gameSpace, world, config, board);

			// Rules
			WithersweeperActivePhase.setRules(activity);

			// Listeners
			activity.listen(ItemThrowEvent.EVENT, phase::onThrowItem);
			activity.listen(GameActivityEvents.ENABLE, phase::enable);
			activity.listen(GameActivityEvents.TICK, phase::tick);
			activity.listen(GamePlayerEvents.ACCEPT, phase::onAcceptPlayers);
			activity.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
			activity.listen(PlayerDeathEvent.EVENT, phase::onPlayerDeath);
			activity.listen(BlockUseEvent.EVENT, phase::useBlock);
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

		for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
			if (player.getY() < 0) {
				this.spawn(player);
			}
		}
	}

	private Text getMistakeText(PlayerEntity causer) {
		Text displayName = causer.getDisplayName();

		if (this.config.getMaxMistakes() <= 1) {
			return Text.translatable("text.withersweeper.reveal_mine", displayName).formatted(Formatting.RED);
		} else {
			return Text.translatable("text.withersweeper.reveal_mine.max_mistakes", displayName, this.config.getMaxMistakes()).formatted(Formatting.RED);
		}
	}

	private void checkMistakes(PlayerEntity causer) {
		if (this.mistakes < this.config.getMaxMistakes()) return;

		Text text = this.getMistakeText(causer);
		for (PlayerEntity player : this.gameSpace.getPlayers()) {
			player.sendMessage(text, false);
		}

		if (this.statistics != null) {
			for (PlayerRef participant : this.participants) {
				this.statistics.forPlayer(participant).increment(StatisticKeys.GAMES_LOST, 1);
			}
		}

		this.endGame();
	}

	private boolean isModifyingFlags(PlayerEntity player) {
		return player.getInventory().getSelectedSlot() == 8;
	}

	private ItemStackBuilder getFlagStackBuilder() {
		return ItemStackBuilder.of(this.config.getFlagStack())
			.addLore(Text.translatable("text.withersweeper.flag_description.line1").formatted(Formatting.GRAY))
			.addLore(Text.translatable("text.withersweeper.flag_description.line2").formatted(Formatting.GRAY))
			.set(DataComponentTypes.MAX_STACK_SIZE, Item.MAX_MAX_COUNT)
			.setCount(this.board.getRemainingFlags());
	}

	private void setFlagSlot(ServerPlayerEntity player, ItemStack stack) {
		player.getInventory().setStack(8, stack);

		// Update inventory
		player.currentScreenHandler.sendContentUpdates();
		player.playerScreenHandler.onContentChanged(player.getInventory());
	}

	private void updateFlagCount() {
		ItemStackBuilder flagStackBuilder = this.getFlagStackBuilder();

		for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
			this.setFlagSlot(player, flagStackBuilder.build());
		}
	}

	private EventResult modifyField(ServerPlayerEntity uncoverer, BlockPos pos, Field field) {
		if (this.isModifyingFlags(uncoverer) && field.getVisibility() != FieldVisibility.UNCOVERED) {
			if (field.getVisibility() == FieldVisibility.FLAGGED) {
				field.setVisibility(FieldVisibility.COVERED);
				this.world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1, 1);
			} else {
				field.setVisibility(FieldVisibility.FLAGGED);
				this.world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1, 1);
			}

			return EventResult.ALLOW;
		} else if (field.getVisibility() == FieldVisibility.COVERED) {
			field.uncover(pos, uncoverer, this);
			this.world.playSound(null, pos, SoundEvents.BLOCK_SAND_BREAK, SoundCategory.BLOCKS, 0.5f, 1);

			return EventResult.ALLOW;
		}
			
		return EventResult.PASS;
	}

	private void addParticipant(ServerPlayerEntity player) {
		PlayerRef participant = PlayerRef.of(player);
		if (this.participants.add(participant) && this.statistics != null) {
			this.statistics.forPlayer(participant).increment(StatisticKeys.GAMES_PLAYED, 1);
		}
	}

	private ActionResult useBlock(ServerPlayerEntity uncoverer, Hand hand, BlockHitResult hitResult) {
		if (this.isGameEnding()) return ActionResult.PASS;
		if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

		BlockPos pos = hitResult.getBlockPos();
		if (pos.getY() != 0) return ActionResult.PASS;
		if (!this.board.isValidPos(pos.getX(), pos.getZ())) return ActionResult.PASS;

		this.board.placeMines(pos.getX(), pos.getZ(), this.world.getRandom());

		Field field = this.board.getField(pos.getX(), pos.getZ());
		EventResult result = this.modifyField(uncoverer, pos, field);

		if (result == EventResult.ALLOW) {
			this.addParticipant(uncoverer);

			this.checkMistakes(uncoverer);
			this.board.build(this.world);
			this.updateFlagCount();

			if (this.board.isCompleted()) {
				Text text = Text.translatable("text.withersweeper.complete", this.timeElapsed / 20).formatted(Formatting.GOLD);
				for (PlayerEntity player : this.gameSpace.getPlayers()) {
					player.sendMessage(text, false);
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

		return result.asActionResult();
	}

	private JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.world, WithersweeperActivePhase.getSpawnPos(this.config)).thenRunForEach(player -> {
			player.changeGameMode(GameMode.ADVENTURE);
			this.setFlagSlot(player, this.getFlagStackBuilder().build());
		});
	}

	private EventResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		// Respawn player
		this.spawn(player);
		return EventResult.DENY;
	}

	private boolean attemptToSendInfoMessage(PlayerEntity player, BlockPos pos) {
		if (pos.getY() != 0) return false;
		if (!this.board.isValidPos(pos.getX(), pos.getZ())) return false;

		Field field = this.board.getField(pos.getX(), pos.getZ());

		Text message = field.getCoveredInfoMessage().copy().formatted(Formatting.DARK_PURPLE);
		player.sendMessage(message, true);

		return true;
	}

	private EventResult onThrowItem(PlayerEntity player, int slot, ItemStack stack) {
		HitResult hit = player.raycast(8, 0, false);
		if (hit.getType() == HitResult.Type.BLOCK) {
			this.attemptToSendInfoMessage(player, ((BlockHitResult) hit).getBlockPos());
		}

		return EventResult.DENY;
	}

	public StatisticMap getStatisticsForPlayer(ServerPlayerEntity player) {
		if (this.statistics == null) {
			return null;
		}
		return this.statistics.forPlayer(player);
	}

	public Board getBoard() {
		return this.board;
	}

	private void spawn(ServerPlayerEntity player) {
		WithersweeperActivePhase.spawn(player, this.world, this.config);
	}

	private void endGame() {
		this.ticksUntilClose = this.config.getTicksUntilClose().get(this.world.getRandom());
	}

	private boolean isGameEnding() {
		return this.ticksUntilClose >= 0;
	}

	protected static void spawn(ServerPlayerEntity player, ServerWorld world, WithersweeperConfig config) {
		Vec3d spawnPos = WithersweeperActivePhase.getSpawnPos(config);
		player.teleport(world, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), Set.of(), 0, 0, true);
	}

	protected static Vec3d getSpawnPos(WithersweeperConfig config) {
		return new Vec3d(config.getBoardConfig().x / 2d, 1, config.getBoardConfig().x / 2d);
	}
}