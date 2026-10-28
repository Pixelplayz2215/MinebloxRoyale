package gg.minebloxroyale.hub;

import java.util.HashSet;
import java.util.Set;

/**
 * Emote and glider unlock rules (emotes and gliders sheets), shared by the server (which checks) and the client (which
 * shows locks in the Locker). v2.0.0: "shop" items are bought with gold (Gold), need being the price. Stats are the player's, over every world they've played in: lifetime kills, Victory
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
	public record Stats(int kills, int wins, boolean obby, int rangeBest, int bullseyes, int goldEarned, int goldSpent, Set<String> owned) {
		public static final Stats NONE = new Stats(0, 0, false, 0, 0);

		/** Without gold (worlds and reports from before v2.0.0). */
		public Stats(int kills, int wins, boolean obby, int rangeBest, int bullseyes) {
			this(kills, wins, obby, rangeBest, bullseyes, 0, 0, Set.of());
		}

		public Stats plus(Stats o) {
			Set<String> both = new HashSet<>(owned);
			both.addAll(o.owned);
			return new Stats(kills + o.kills, wins + o.wins, obby || o.obby, Math.max(rangeBest, o.rangeBest), bullseyes + o.bullseyes, goldEarned + o.goldEarned,
				goldSpent + o.goldSpent, both);
		}

		/** Gold to spend (v2.0.0, Gold): everything earned less everything spent, over every world. */
		public int gold() {
			return goldEarned - goldSpent;
		}
	}

	/** An unlock that isn't bought (a shop item needs its id: the four-argument has). */
	public static boolean has(String unlock, int need, Stats s) {
		return has(unlock, need, s, null);
	}

	/**
	 * Does the player have item id (shop: bought it with gold; preset: bought the Shop preset it comes with, which makes
	 * every piece theirs, 2.0.0-test3; tower and obbytime, 2.0.0: earned a preset by reaching a Sky Tower's top or by a
	 * fast obby run, which makes it and every piece theirs the same way, Gold.grant; the rest: their stats).
	 */
	public static boolean has(String unlock, int need, Stats s, String id) {
		return switch (unlock) {
			case "shop", "preset", "tower", "obbytime" -> id != null && s.owned().contains(id);
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
			case "shop" -> need + " gold";
			case "preset" -> "Shop preset";
			case "tower" -> tower(need) + " Sky Tower";
			case "obbytime" -> "Obby in " + need + " s";
			case "obby" -> "Finish obby";
			case "victory" -> "1 win";
			case "kills" -> need + " kills";
			case "wins" -> need + " wins";
			case "range" -> "Range " + need;
			case "bullseyes" -> need + " bullseyes";
			default -> "";
		};
	}

	/** A Sky Tower's name by its number (presets sheet unlock tower, need 1 to 3; HubFun.TOWERS). */
	public static String tower(int n) {
		return switch (n) {
			case 1 -> "Easy";
			case 2 -> "Normal";
			default -> "Hard";
		};
	}

	/** The line under the Locker grid while a locked item is hovered: what unlocks it and how far along you are. */
	public static String hint(String unlock, int need, Stats s) {
		return switch (unlock) {
			case "shop" -> "Shop: " + need + " gold (you have " + s.gold() + ") - click to buy";
			case "preset" -> "Locked: comes with a preset from the Shop (buy the whole outfit with gold)";
			case "tower" -> "Locked: reach the top of the " + tower(need) + " Sky Tower in the hub";
			case "obbytime" -> "Locked: finish the obby in " + need + " seconds or less (the timer starts at the first red jump)";
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
