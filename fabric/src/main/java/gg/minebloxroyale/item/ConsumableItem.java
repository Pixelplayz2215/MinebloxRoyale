package gg.minebloxroyale.item;

import gg.minebloxroyale.match.Combat;
import gg.minebloxroyale.sheets.ConsumableDef;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Heals and shields (consumables sheet). Hold right mouse for useTicks; Minecraft's drinking or eating animation and
 * sound play while using (the item carries a vanilla CONSUMABLE component just for those effects, see ModItems).
 * Finishing removes exactly one from the stack. The Chug Jug instead fills in steps while held and keeps its unused
 * points (shown as the item's bar) until it is empty.
 */
public class ConsumableItem extends Item {
	public final ConsumableDef def;

	public ConsumableItem(ConsumableDef def, Item.Properties properties) {
		super(properties);
		this.def = def;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		String why = Combat.whyNot(player, def);
		if (why != null) {
			if (player instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(why), true);
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	private boolean stepped() {
		return def.stepTicks() > 0;
	}

	/** Chug Jug: points still in this jug (starts at the sheet's amount, 200). */
	public float pointsLeft(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? def.amount() : data.copyTag().getFloatOr("pointsLeft", def.amount());
	}

	private static void setPointsLeft(ItemStack stack, float v) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		tag.putFloat("pointsLeft", v);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** Stepped items (Chug Jug): one step every stepTicks while right mouse is held; stops when full or empty. */
	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
		if (!stepped() || level.isClientSide()) return;
		int elapsed = def.useTicks() - ticksRemaining;
		if (elapsed <= 0 || elapsed % def.stepTicks() != 0) return;
		float left = pointsLeft(stack);
		if (left > 0 && Combat.canUse(entity, def)) left -= Combat.applyStep(entity, def, Math.min(def.stepAmount(), left));
		if (left <= 0.001F) {
			entity.stopUsingItem();
			stack.shrink(1);
		} else {
			setPointsLeft(stack, left);
			if (!Combat.canUse(entity, def)) entity.stopUsingItem();
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (level.isClientSide() || stepped()) return stack;
		if (Combat.canUse(entity, def)) {
			Combat.applyConsumable(entity, def);
			stack.shrink(1);
		}
		return stack;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return def.useTicks();
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return def.anim().equals("eat") ? ItemUseAnimation.EAT : ItemUseAnimation.DRINK;
	}

	// The Chug Jug shows how much is left as the item's bar.

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return stepped() && pointsLeft(stack) < def.amount();
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Mth.clamp(Math.round(13F * pointsLeft(stack) / def.amount()), 0, 13);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return def.color();
	}
}
