package gg.minebloxroyale.client;

import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.WeaponDef;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Fortnite controls for guns: left mouse fires (held for automatic guns), right mouse aims down sights. Aiming zooms by
 * the weapon's adsZoom; a scoped sniper switches to the scope view (first person + scope overlay), easing in.
 */
public final class Aim {
	/** Scoping in takes this long (creator: "a slow zoom in", the scope fading in as it goes). */
	private static final int SCOPE_IN_TICKS = 8;
	/**
	 * Recoil (v2.0.0: every gun, creator: "recoil to each gun"; first the sniper's, "keep in with a recoil kick"): each
	 * shot kicks the view up by its gun's recoilDeg (weapons sheet), automatic guns a little sideways too, quickly, then
	 * it settles back; kicks from quick shots add up (to three at most).
	 */
	private static final long KICK_UP_MS = 60, KICK_SETTLE_MS = 420;
	/** Recent kicks: {when (ms), degrees up, degrees sideways}. */
	private static final java.util.ArrayDeque<float[]> KICKS = new java.util.ArrayDeque<>();
	private static long kickBase = System.currentTimeMillis();
	private static final long HIT_MARKER_MS = 350;

	private static float zoom = 1F, prevZoom = 1F;
	private static int scopeTicks;
	private static boolean attackWasDown, wasScoped;
	private static long hitAt;
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

	/** A shot of yours went off (Tracers, from the server's Shot): the view kicks. */
	public static void kick(float deg, boolean automatic) {
		var r = java.util.concurrent.ThreadLocalRandom.current();
		KICKS.addLast(new float[] { System.currentTimeMillis() - kickBase, deg, automatic ? (r.nextFloat() - 0.5F) * deg * 0.6F : 0 });
		while (KICKS.size() > 12) KICKS.removeFirst();
	}

	/** Degrees the view is kicked up by recent shots, right now (0 when settled). */
	public static float recoil() {
		return kicked(1);
	}

	/** Degrees the view is kicked sideways (automatic guns), right now. */
	public static float recoilYaw() {
		return kicked(2);
	}

	private static float kicked(int which) {
		float now = System.currentTimeMillis() - kickBase, sum = 0, cap = 0;
		for (var it = KICKS.iterator(); it.hasNext();) {
			float[] k = it.next();
			float t = now - k[0];
			if (t >= KICK_UP_MS + KICK_SETTLE_MS) {
				it.remove();
				continue;
			}
			float shape;
			if (t < KICK_UP_MS) {
				float u = t / KICK_UP_MS;
				shape = 1F - (1F - u) * (1F - u);
			} else {
				float u = (t - KICK_UP_MS) / KICK_SETTLE_MS;
				shape = 1F - u * u * (3F - 2F * u);
			}
			sum += k[which] * shape;
			cap = Math.max(cap, Math.abs(k[1]) * 3);
		}
		return Math.max(-cap, Math.min(cap, sum));
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
				ClientPlayNetworking.send(new Net.Action(String.format(Locale.ROOT, "fire:%.3f:%.3f:%.3f", at.x, at.y, at.z)));
			}
		}
		attackWasDown = down;
	}
}
