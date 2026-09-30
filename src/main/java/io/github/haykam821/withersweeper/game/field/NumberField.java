package io.github.haykam821.withersweeper.game.field;

import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.*;

import java.lang.Math;

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
	private boolean hasDisplay = false;
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

	public void createDisplay(Level level, BlockPos pos) {
		if (value > 0 && this.isCompleted() && !this.hasDisplay) {
			this.hasDisplay = true;
			Display.TextDisplay display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
			display.setText(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE));
			display.setBackgroundColor(0);
			display.setPos(Vec3.atBottomCenterOf(pos.above()).add(0, 0.01, 0));
			// Center the display and make it rotate, if transformation scale is modified translation must be modified too
			display.setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
			display.setTransformation(new Transformation(new Vector3f(-0.03125f, 0f, 0.3375f),
				new Quaternionf(-0.707f, 0f, 0f, 0.707f), new Vector3f(2.5f), new Quaternionf()));
			level.addFreshEntity(display);
		}
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