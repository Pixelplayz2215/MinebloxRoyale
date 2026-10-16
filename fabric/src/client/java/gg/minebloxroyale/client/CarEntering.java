package gg.minebloxroyale.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Who is getting into a car (2.0.0-test3): an avatar drawn on foot a moment ago that's now in a car climbs in over
 * ENTER_SECONDS (LivingEntityRendererMixin plays the car_enter move), and the car swings that side's front door open
 * for it (CarEntityRenderer). Worked out from what's drawn, so it's the same for players and bots, and for a parked car
 * that becomes a driven one with them already in it.
 */
public final class CarEntering {
	public static final float ENTER_SECONDS = 1.0F;
	/** Drawn on foot this recently, then in a car: getting in, not already sitting there. */
	private static final long JUST = 500_000_000L;
	/** Each avatar's car (entity id, or -1), when it was last drawn, and since when it has been getting in (or -1). */
	private static final Map<Integer, long[]> SEEN = new HashMap<>();

	private CarEntering() {
	}

	/**
	 * Drawn in this car now: how far through getting in it is (0..1), or 1 if it's simply sitting (it was already in when
	 * it came into view, or it's been in a while).
	 */
	public static float inCar(int entity, int car) {
		long now = System.nanoTime();
		long[] seen = SEEN.get(entity);
		if (seen == null) {
			SEEN.put(entity, new long[] { car, now, -1 });
			return 1;
		}
		if (seen[0] != car) seen[2] = now - seen[1] < JUST ? now : -1;
		seen[0] = car;
		seen[1] = now;
		return seen[2] < 0 ? 1 : Math.min(1, (now - seen[2]) / 1e9F / ENTER_SECONDS);
	}

	/** Drawn out of a car now: a car next time means it just got in. */
	public static void onFoot(int entity) {
		long[] seen = SEEN.get(entity);
		if (seen != null) {
			seen[0] = -1;
			seen[1] = System.nanoTime();
		} else SEEN.put(entity, new long[] { -1, System.nanoTime(), -1 });
	}

	/**
	 * For a car seen for the first time with this avatar in it: is it getting in right now (on foot a moment ago, or its
	 * climb already started), rather than already sitting there?
	 */
	public static boolean gettingIn(int entity) {
		long[] seen = SEEN.get(entity);
		if (seen == null) return false;
		long now = System.nanoTime();
		return seen[0] == -1 ? now - seen[1] < JUST : seen[2] >= 0 && now - seen[2] < ENTER_SECONDS * 1e9F;
	}
}
