package gg.minebloxroyale.client.avatar;

import gg.minebloxroyale.sheets.MoveDef;
import gg.minebloxroyale.sheets.MoveKeyDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The avatar's moves when it isn't emoting (moves and move_keys sheets: fabric/tools/blender/moves.py, animated in
 * Blender like the emotes, elbows and knees included): walking, running, sneaking, jumping and falling, swimming,
 * climbing, gliding, aiming, swinging the pickaxe, healing, drinking, reviving, rebooting, searching a chest and the
 * knocked crawl. Each is sampled along its own t (moves sheet: a walk cycle's phase, a loop's time, the look's pitch or a
 * swing's progress), straight lines between the sheet's rows.
 */
final class Moves {
	private static final Map<String, List<MoveKeyDef>> KEYS = new HashMap<>();

	private Moves() {
	}

	/** The move at t (0..1 along it; loops wrap round, the others stop at their ends). */
	static RobloxAvatarRenderer.Pose at(String move, float t) {
		MoveDef def = Sheets.MOVES_BY_ID.get(move);
		List<MoveKeyDef> keys = KEYS.computeIfAbsent(move,
			id -> Sheets.MOVE_KEYS.stream().filter(k -> k.move().equals(id)).sorted(Comparator.comparingDouble(MoveKeyDef::t)).toList());
		if (def == null || keys.isEmpty()) return RobloxAvatarRenderer.Pose.ZERO;
		boolean loops = def.axis().equals("phase") || def.axis().equals("time");
		t = loops ? t - (float) Math.floor(t) : Math.max(0, Math.min(1, t));
		int lo = 0, hi = keys.size() - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (keys.get(mid).t() <= t) lo = mid;
			else hi = mid;
		}
		MoveKeyDef a = keys.get(lo), b = keys.get(hi);
		float f = b.t() > a.t() ? Math.max(0, Math.min(1, (t - a.t()) / (b.t() - a.t()))) : 0;
		return pose(a).lerp(pose(b), f);
	}

	/** A loop of a move that runs on time (seconds since anything; its loopSeconds a round). */
	static RobloxAvatarRenderer.Pose loop(String move, float seconds) {
		MoveDef def = Sheets.MOVES_BY_ID.get(move);
		return at(move, def == null ? 0 : seconds / def.loopSeconds());
	}

	private static RobloxAvatarRenderer.Pose pose(MoveKeyDef k) {
		return new RobloxAvatarRenderer.Pose(k.rightArmX(), k.rightArmZ(), k.leftArmX(), k.leftArmZ(), k.rightLegX(), k.leftLegX(), k.bodyYaw(), k.bounce(),
			k.rightLegZ(), k.leftLegZ(), k.headX(), k.headY(), k.headZ(), k.lean(), k.tilt(), k.twist(), k.rightElbow(), k.leftElbow(), k.rightKnee(),
			k.leftKnee(), k.rightArmY(), k.leftArmY(), k.flip());
	}
}
