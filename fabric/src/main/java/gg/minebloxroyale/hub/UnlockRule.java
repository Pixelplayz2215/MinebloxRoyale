package gg.minebloxroyale.hub;

/**
 * Emote and glider unlock rules (emotes and gliders sheets), shared by the server (which checks) and the client (which
 * shows locks in the Locker). Stats are the player's, over every world they've played in: lifetime kills, Victory
 * Royales, whether the obby was finished, their best shooting-range round and their lifetime bullseyes at the range
 * (each world keeps its own; the player's game adds them up, MyUnlocks).
 */
public final class UnlockRule {
	private UnlockRule() {
	}

	/**
	 * A player's unlock stats. Over several worlds: kills, wins and bullseyes add up, the best range round is the best
	 * of them, and the obby counts once finished anywhere.
	 */
	public record Stats(int kills, int wins, boolean obby, int rangeBest, int bullseyes) {
		public static final Stats NONE = new Stats(0, 0, false, 0, 0);

		public Stats plus(Stats o) {
			return new Stats(kills + o.kills, wins + o.wins, obby || o.obby, Math.max(rangeBest, o.rangeBest), bullseyes + o.bullseyes);
		}
	}

	public static boolean has(String unlock, int need, Stats s) {
		return switch (unlock) {
			case "default" -> true;
			case "obby" -> s.obby();
			case "victory" -> s.wins() >= 1;
			case "kills" -> s.kills() >= need;
			case "wins" -> s.wins() >= need;
			case "range" -> s.rangeBest() >= need;
			case "bullseyes" -> s.bullseyes() >= need;
			default -> false;
		};
	}

	/**
	 * What a locked item asks for, as its Locker button ("50 kills", "3 wins", "Range 30"...). A locked item's name
	 * stays hidden (creator: only what unlocks it shows), so this is all the button says.
	 */
	public static String needs(String unlock, int need) {
		return switch (unlock) {
			case "obby" -> "Finish obby";
			case "victory" -> "1 win";
			case "kills" -> need + " kills";
			case "wins" -> need + " wins";
			case "range" -> "Range " + need;
			case "bullseyes" -> need + " bullseyes";
			default -> "";
		};
	}

	/** The line under the Locker grid while a locked item is hovered: what unlocks it and how far along you are. */
	public static String hint(String unlock, int need, Stats s) {
		return switch (unlock) {
			case "obby" -> "Locked: finish the obby in the lobby";
			case "victory" -> "Locked: win a Victory Royale";
			case "kills" -> "Locked: get " + need + " eliminations (you have " + s.kills() + ")";
			case "wins" -> "Locked: win " + need + " Victory Royales (you have " + s.wins() + ")";
			case "range" -> "Locked: score " + need + " in one range round (best " + s.rangeBest() + ")"; // each round starts from 0
			case "bullseyes" -> "Locked: hit " + need + " range bullseyes (you have " + s.bullseyes() + ")";
			default -> "";
		};
	}
}
