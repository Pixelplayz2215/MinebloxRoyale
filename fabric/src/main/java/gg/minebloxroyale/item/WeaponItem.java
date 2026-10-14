package gg.minebloxroyale.item;

import gg.minebloxroyale.sheets.WeaponDef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** A gun from the weapons sheet. Fortnite controls: left mouse fires, right mouse aims down sights. */
public class WeaponItem extends Item {
	public final WeaponDef def;

	public WeaponItem(WeaponDef def, Item.Properties properties) {
		super(properties);
		this.def = def;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		// Right mouse = aim down sights (Fortnite). Firing is left mouse (Net action "fire").
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (owner instanceof LivingEntity le) Guns.tickReload(stack, def, level.getGameTime(), le);
	}
}
