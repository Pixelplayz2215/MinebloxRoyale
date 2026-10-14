package gg.minebloxroyale.client;

import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.WeaponDef;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Fortnite controls for guns: left mouse fires (held for automatic guns), right mouse aims down sights. Aiming zooms by
 * the weapon's adsZoom; a scoped sniper switches to the scope view (first person + scope overlay), easing in.
 */
public final class Aim {
	/** Scoping in takes this long (creator: "a slow zoom in", the scope fading in as it goes). */
	private static final int SCOPE_IN_TICKS = 8;
	/** The sniper's recoil: the view kicks up this far and settles back (creator: "keep in with a recoil kick"). */
	private static final float KICK_DEG = 4F;
	private static final long KICK_UP_MS = 70, KICK_SETTLE_MS = 450;
	private static final long HIT_MARKER_MS = 350;

	private static float zoom = 1F, prevZoom = 1F;
	private static int scopeTicks;
	private static boolean attackWasDown, wasScoped;
	private static long kickAt, hitAt;
	private static boolean hitHead;

	private Aim() {
	}

	public static @Nullable WeaponDef heldGun() {
		Player p = Minecraft.getInstance().player;
		return p != null && p.getMainHandItem().getItem() instanceof WeaponItem w ? w.def : null;
	}

	public static boolean aiming() {
		Player p = Minecraft.getInstance().player;
		return p != null && heldGun() != null && p.isUsingItem() && p.getUseItem().getItem() instanceof WeaponItem;
	}

	/** Aiming a scoped gun: scoping in or already in the scope. */
	private static boolean scopeIn() {
		WeaponDef w = heldGun();
		return w != null && w.scope() && aiming();
	}

	/** Looking through the scope (from the first moment of scoping in): first person, the scope overlay, no hotbar. */
	public static boolean scoped() {
		return scopeIn() && zoom > 1F;
	}

	/** Field-of-view divisor, eased in and out (this tick's value). */
	public static float zoom() {
		return zoom;
	}

	/**
	 * The field-of-view divisor for a frame, partialTicks into the current tick: blended between ticks, so zooming is
	 * smooth at any frame rate instead of stepping 20 times a second.
	 */
	public static float zoom(float partialTicks) {
		return prevZoom + (zoom - prevZoom) * partialTicks;
	}

	/** How far the scope has faded in, 0 to 1, following the zoom. */
	public static float scopeFade(float partialTicks) {
		WeaponDef w = heldGun();
		if (w == null || w.adsZoom() <= 1F) return 1F;
		return Mth.clamp((zoom(partialTicks) - 1F) / (w.adsZoom() - 1F), 0F, 1F);
	}

	/** Degrees the view is kicked up by the last sniper shot, right now (0 when settled). */
	public static float recoil() {
		long t = System.currentTimeMillis() - kickAt;
		if (kickAt == 0 || t >= KICK_UP_MS + KICK_SETTLE_MS) return 0F;
		if (t < KICK_UP_MS) {
			float u = t / (float) KICK_UP_MS;
			return KICK_DEG * (1F - (1F - u) * (1F - u));
		}
		float u = (t - KICK_UP_MS) / (float) KICK_SETTLE_MS;
		return KICK_DEG * (1F - u * u * (3F - 2F * u));
	}

	/** A shot of yours landed (the server's damage number): the scope shows a hit marker, a different one for a head. */
	public static void hit(boolean head) {
		hitAt = System.currentTimeMillis();
		hitHead = head;
	}

	/** The hit marker's strength now, 1 fading to 0 (0 = none); negative for a headshot. */
	public static float hitMarker() {
		long t = System.currentTimeMillis() - hitAt;
		if (hitAt == 0 || t >= HIT_MARKER_MS) return 0F;
		float a = 1F - t / (float) HIT_MARKER_MS;
		return hitHead ? -a : a;
	}

	public static void tick(Minecraft mc) {
		WeaponDef w = heldGun();
		prevZoom = zoom;
		if (wasScoped && !scopeIn()) {
			// Leaving the scope pops straight back out, as in Fortnite: the camera goes back over the shoulder this same
			// tick, and zooming out slowly behind your own back looked like a stutter (creator).
			zoom = prevZoom = 1F;
			scopeTicks = 0;
		} else if (scopeIn()) {
			scopeTicks = Math.min(SCOPE_IN_TICKS, scopeTicks + 1);
			float u = scopeTicks / (float) SCOPE_IN_TICKS;
			zoom = 1F + (w.adsZoom() - 1F) * u * u * (3F - 2F * u);
		} else {
			scopeTicks = 0;
			float target = w != null && aiming() ? w.adsZoom() : 1F;
			zoom += (target - zoom) * 0.45F;
			if (Math.abs(zoom - target) < 0.01F) zoom = target;
		}
		wasScoped = scoped();
		boolean down = mc.options.keyAttack.isDown() && mc.gui.screen() == null;
		if (w != null && !BuildMode.active()) {
			while (mc.options.keyAttack.consumeClick()) {
				// swallowed: guns don't punch or mine
			}
			if (down && (w.automatic() || !attackWasDown)) {
				// Send where the crosshair points, so the shot goes exactly there (the camera is over the shoulder).
				Vec3 at = Crosshair.aimPoint(mc, w.rangeBlocks());
				if (scoped() && ready(mc.player.getMainHandItem(), mc.level.getGameTime())) kickAt = System.currentTimeMillis();
				ClientPlayNetworking.send(new Net.Action(String.format(Locale.ROOT, "fire:%.3f:%.3f:%.3f", at.x, at.y, at.z)));
			}
		}
		attackWasDown = down;
	}

	/** Whether this gun fires when the trigger is pulled now (loaded, not reloading, not between shots). */
	private static boolean ready(ItemStack stack, long now) {
		return Guns.ammo(stack) > 0 && !Guns.reloading(stack, now) && Guns.tag(stack).getLongOr("nextShot", 0L) <= now;
	}
}
