package io.github.haykam821.withersweeper.game.field;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.Component;

public class NumberField extends Field {
	private static final BlockState[] VALUES_TO_STATES = new BlockState[] {
		Blocks.WOOL.white().defaultBlockState(),
		Blocks.WOOL.blue().defaultBlockState(),
		Blocks.WOOL.green().defaultBlockState(),
		Blocks.WOOL.red().defaultBlockState(),
		Blocks.WOOL.lightBlue().defaultBlockState(),
		Blocks.WOOL.brown().defaultBlockState(),
		Blocks.WOOL.cyan().defaultBlockState(),
		Blocks.WOOL.black().defaultBlockState(),
		Blocks.WOOL.lightGray().defaultBlockState()
	};

	private int value = 0;

	public NumberField(FieldVisibility visibility, int value) {
		super(visibility);

		if (value < 0 || value >= VALUES_TO_STATES.length) {
			throw new IllegalStateException("Value must be between 0 and 8 (inclusive)");
		}

		this.value = value;
	}

	public NumberField(int value) {
		this(FieldVisibility.COVERED, value);
	}

	@Override
	public boolean canUncoverRecursively() {
		return this.value == 0;
	}

	@Override
	public BlockState getBlockState() {
		return VALUES_TO_STATES[this.value];
	}

	@Override
	public Component getInfoMessage() {
		return Component.translatable("text.withersweeper.info.number" + (this.value == 1 ? "" : ".plural"), this.value);
	}

	public NumberField increaseValue() {
		return new NumberField(this.getVisibility(), Math.min(this.value + 1, 8));
	}

	@Override
	public String toString() {
		return "NumberField{value=" + this.value + "}";
	}
}