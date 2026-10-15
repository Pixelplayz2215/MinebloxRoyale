package gg.minebloxroyale.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.client.ClientState;
import gg.minebloxroyale.client.Tracers;
import gg.minebloxroyale.client.DropControl;
import gg.minebloxroyale.client.LockerScreen;
import gg.minebloxroyale.client.avatar.Cosmetics;
import gg.minebloxroyale.client.avatar.RobloxAvatarRenderer;
import gg.minebloxroyale.item.ConsumableItem;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.match.BattleBusEntity;
import gg.minebloxroyale.match.CarEntity;
import gg.minebloxroyale.match.Knocked;
import gg.minebloxroyale.net.Net;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Players and bots are drawn as Roblox R6 avatars built from the player's Roblox files (falls back to Minecraft's
 * model).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void minebloxroyale$tagAvatar(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (entity instanceof Player || entity instanceof BotEntity) {
			state.setData(RobloxAvatarRenderer.ENTITY_ID, entity.getId());
			state.setData(RobloxAvatarRenderer.COLORS, entity instanceof BotEntity bot ? bot.colors() : "classic");
			state.setData(RobloxAvatarRenderer.AIMING, entity.getMainHandItem().getItem() instanceof WeaponItem);
			// Aiming down the sights: a player holding right mouse with a gun; a bot while it's shooting (it fired lately).
			float[] shot = Tracers.lastShot(entity.getId());
			state.setData(RobloxAvatarRenderer.ADS, entity instanceof Player ? entity.isUsingItem() && entity.getUseItem().getItem() instanceof WeaponItem
				: shot != null && shot[0] < 1.5F);
			state.setData(RobloxAvatarRenderer.GLIDING, gliding(entity));
			state.setData(RobloxAvatarRenderer.SKYDIVING, skydiving(entity)); // (after gliding: that keeps who has landed)
			// In a car (v2.0.0): sitting, hands on the wheel, nothing in them (as in Fortnite).
			boolean inCar = entity.getVehicle() instanceof CarEntity;
			state.setData(RobloxAvatarRenderer.SITTING, inCar);
			// Getting in (2.0.0-test3): for its first second in a car, climbing in from outside its door (the car_enter
			// move, from the side its seat is on; the car swings that door open and shut, CarEntityRenderer).
			if (inCar && entity.getVehicle() instanceof CarEntity car) minebloxroyale$entering(entity, car, state);
			else minebloxroyale$onFoot(entity);
			if (inCar && state instanceof ArmedEntityRenderState armed) {
				armed.rightHandItemState.clear();
				armed.leftHandItemState.clear();
			}
			state.setData(RobloxAvatarRenderer.IN_BUS, entity.getVehicle() instanceof BattleBusEntity);
			state.setData(RobloxAvatarRenderer.KNOCKED, Knocked.CLIENT.contains(entity.getId()));
			state.setData(RobloxAvatarRenderer.IS_PLAYER, entity instanceof Player);
			state.setData(RobloxAvatarRenderer.MOTION, minebloxroyale$motion(entity));
			state.setData(RobloxAvatarRenderer.POSE, new RobloxAvatarRenderer.Pose[1]); // (worked out when first drawn this frame)
			// Teammates' name tags in their teammate colour (team modes; your party in the hub), on your screen only.
			if (state.nameTag != null) {
				Net.Mate mate = ClientState.mateByEntity(entity.getId());
				Integer color = mate != null ? Integer.valueOf(ClientState.teamColor(mate.slot())) : null;
				if (color == null && entity instanceof Player && !ClientState.inMatch() && entity != Minecraft.getInstance().player) {
					int i = ClientState.partyIds.indexOf(entity.getUUID().toString());
					if (i >= 0) color = ClientState.teamColor(i);
				}
				if (color != null) {
					int rgb = color & 0xFFFFFF;
					state.nameTag = Component.literal(state.nameTag.getString()).withStyle(s -> s.withColor(rgb));
				}
			}
			// Players: their Locker outfit; bots: the random outfit they were given (or none: the default look).
			String look = "";
			if (entity instanceof Player) look = entity.getUUID().toString();
			else if (entity instanceof BotEntity bot && !bot.outfit().isEmpty()) {
				Cosmetics.botOutfit(entity.getUUID(), bot.outfit());
				look = entity.getUUID().toString();
			}
			state.setData(RobloxAvatarRenderer.PLAYER_UUID, look);
			// Locker open: hide yourself in the world; only the Locker's big preview of you is drawn.
			var mc = Minecraft.getInstance();
			boolean hidden = entity == mc.player && mc.gui.screen() instanceof LockerScreen && !LockerScreen.previewing;
			state.setData(RobloxAvatarRenderer.HIDDEN, hidden);
			if (hidden) state.isInvisible = true; // no shadow either (shadows skip invisible entities)
		}
	}

	/** Each avatar's car (entity id, or -1), when it was last drawn, and since when it has been getting in (or -1). */
	private static final java.util.Map<Integer, long[]> CAR_SEEN = new java.util.HashMap<>();
	private static final float ENTER_SECONDS = 1.0F;

	/**
	 * Getting into a car: an avatar drawn on foot a moment ago that's now in a car climbs in over a second, its move played
	 * and its body carried from standing outside its door (on the ground) to its seat. One already in a car when it comes
	 * into view is simply sitting.
	 */
	private static void minebloxroyale$entering(LivingEntity entity, CarEntity car, LivingEntityRenderState state) {
		long now = System.nanoTime();
		long[] seen = CAR_SEEN.get(entity.getId());
		if (seen == null) {
			CAR_SEEN.put(entity.getId(), new long[] { car.getId(), now, -1 });
			return;
		}
		if (seen[0] != car.getId()) seen[2] = now - seen[1] < 500_000_000L ? now : -1;
		seen[0] = car.getId();
		seen[1] = now;
		float p = seen[2] < 0 ? 1 : (now - seen[2]) / 1e9F / ENTER_SECONDS;
		if (p >= 1) return;
		boolean right = car.getPassengers().indexOf(entity) % 2 == 1;
		state.setData(RobloxAvatarRenderer.CAR_ENTER, p);
		state.setData(RobloxAvatarRenderer.CAR_ENTER_RIGHT, right);
		// (out of its side of the car: the car's left for the driver's side, its right for the other; down on the ground)
		double yaw = Math.toRadians(car.getYRot()), side = right ? -1.2 : 1.2;
		float f = Math.max(0, Math.min(1, (p - 0.2F) / 0.7F)), away = 1 - f * f * (3 - 2 * f);
		state.x += Math.cos(yaw) * side * away;
		state.z += Math.sin(yaw) * side * away;
		state.y += (car.getY() - entity.getY()) * away;
	}

	/** Seen out of a car this frame (drawn on foot): a car next time means it just got in. */
	private static void minebloxroyale$onFoot(LivingEntity entity) {
		long[] seen = CAR_SEEN.get(entity.getId());
		if (seen != null) {
			seen[0] = -1;
			seen[1] = System.nanoTime();
		} else CAR_SEEN.put(entity.getId(), new long[] { -1, System.nanoTime(), -1 });
	}

	/** How it is moving and what it is doing, for its move (RobloxAvatarRenderer): heals and shields say heal or drink. */
	private static RobloxAvatarRenderer.Motion minebloxroyale$motion(LivingEntity e) {
		String use = null;
		if (e.isUsingItem() && e.getUseItem().getItem() instanceof ConsumableItem c) use = c.def.anim().equals("drink") ? "drink" : "heal";
		return new RobloxAvatarRenderer.Motion(e.isSprinting(), e.isCrouching(), e.onGround(), e.isInWater(), e.isSwimming(), e.onClimbable() && !e.onGround(), use,
			ClientState.ACTIONS.getOrDefault(e.getId(), 0));
	}

	/** Entities (other players, bots) that have touched the ground since their drop: never show a glider again. */
	private static final Set<Integer> LANDED = new HashSet<>();

	private static boolean gliding(LivingEntity e) {
		var mc = Minecraft.getInstance();
		if (e == mc.player) return DropControl.phase() == DropControl.Phase.GLIDE;
		if (!ClientState.inMatch() || e.getVehicle() instanceof BattleBusEntity) {
			LANDED.remove(e.getId()); // a new match: they'll drop again
			return false;
		}
		if (e.onGround() || e.isInWater()) LANDED.add(e.getId());
		if (!(e instanceof Player || e instanceof BotEntity) || mc.level == null) return false;
		double dy = e.getY() - e.yo;
		int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getBlockX(), e.getBlockZ());
		double above = e.getY() - ground;
		// Rebooted (dropping in again from high above a reboot van): the glider is back.
		if (above > 40) LANDED.remove(e.getId());
		if (LANDED.contains(e.getId())) return false;
		// Other players open and close their glider at any height: a slow, steady fall means the glider is open.
		return dy < -0.05 && dy > -0.5 && above > 1.5;
	}

	/**
	 * Skydiving: dropped from the Battle Bus and falling fast, the glider not out yet (contrails stream from the hands).
	 * You: your drop says so; others: falling faster than a glider does, well above the ground, not landed since the
	 * bus.
	 */
	private static boolean skydiving(LivingEntity e) {
		var mc = Minecraft.getInstance();
		if (e == mc.player) return DropControl.phase() == DropControl.Phase.SKYDIVE;
		if (!ClientState.inMatch() || e.getVehicle() instanceof BattleBusEntity || LANDED.contains(e.getId()) || mc.level == null) return false;
		int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getBlockX(), e.getBlockZ());
		return e.getY() - e.yo <= -0.5 && e.getY() - ground > 3;
	}

	/** Players riding the Battle Bus are inside it: don't draw them (or their held items). */
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
	private void minebloxroyale$hideInBusOrLocker(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		if (state.getDataOrDefault(RobloxAvatarRenderer.IN_BUS, false) || state.getDataOrDefault(RobloxAvatarRenderer.HIDDEN, false)) ci.cancel();
	}

	@WrapOperation(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V")
	)
	private void minebloxroyale$robloxBody(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack poseStack, RenderType renderType,
		int light, int overlay, int tint, UvMapping uv, int outline, Operation<Void> original) {
		if (state instanceof LivingEntityRenderState s && s.getData(RobloxAvatarRenderer.COLORS) != null
			&& RobloxAvatarRenderer.submit(s, poseStack, collector, light, overlay)) return;
		original.call(collector, model, state, poseStack, renderType, light, overlay, tint, uv, outline);
	}
}
