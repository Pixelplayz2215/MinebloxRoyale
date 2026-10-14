package gg.minebloxroyale.client;

import gg.minebloxroyale.client.avatar.Cosmetics;
import gg.minebloxroyale.hub.UnlockRule;
import gg.minebloxroyale.match.Teams;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.GliderDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/** What the server last told this client (HUD state, emotes, unlocks). */
public final class ClientState {
	public static Net.Hud hud = new Net.Hud("HUB", 0, 0, 0, 0, 0, 0, 0, 0, 0, "Step on a mode portal to play", false, 0, 0, 0, 0, 0, false, 1, "0,0,0,0");
	/** Team modes: my teammates, the reboot vans and my team's cards (null outside a Duos / Trios / Squads match). */
	public static Net.Squad squad;
	/** My party (account ids and names, in the order they joined; empty = no party). */
	public static List<String> partyIds = List.of(), partyNames = List.of();
	public static long hudAt;
	/** The storm as drawn when the last HUD update arrived (x, z, radius). */
	public static double[] stormFrom;
	/** entity id -> (emote id, start time ms) */
	public static final Map<Integer, Map.Entry<String, Long>> EMOTES = new HashMap<>();
	/**
	 * Unlock stats in this world: lifetime kills, Victory Royales, obby finished, best shooting-range round and range
	 * bullseyes (unlocks add my other worlds).
	 */
	public static int kills, wins, rangeBest, bullseyes;
	public static boolean obby;
	/** The dev test account: everything unlocked. */
	public static boolean all;

	private ClientState() {
	}

	/**
	 * The storm circle to draw now (x, z, radius). The server sends it twice a second; in between it glides from where
	 * it was drawn to the new value, so a shrinking storm moves smoothly instead of jumping.
	 */
	public static double[] storm() {
		double[] to = { hud.stormX(), hud.stormZ(), hud.stormR() };
		if (stormFrom == null) return to;
		double t = Math.min(1, (System.currentTimeMillis() - hudAt) / 500.0);
		if (Math.abs(stormFrom[2] - to[2]) > 40) return to; // a new match or phase jump: no glide
		return new double[] { stormFrom[0] + (to[0] - stormFrom[0]) * t, stormFrom[1] + (to[1] - stormFrom[1]) * t, stormFrom[2] + (to[2] - stormFrom[2]) * t };
	}

	/** My unlock stats in this world. */
	public static UnlockRule.Stats here() {
		return new UnlockRule.Stats(kills, wins, obby, rangeBest, bullseyes);
	}

	/** My unlock stats over every world I've played in. */
	public static UnlockRule.Stats career() {
		return here().plus(MyUnlocks.elsewhere());
	}

	public static boolean unlocked(EmoteDef e) {
		return all || UnlockRule.has(e.unlock(), e.need(), career());
	}

	public static boolean unlocked(GliderDef g) {
		return all || UnlockRule.has(g.unlock(), g.need(), career());
	}

	public static boolean unlocked(CosmeticDef c) {
		return all || UnlockRule.has(c.unlock(), c.need(), career());
	}

	/** My emote wheel: the 8 picked in the Locker, or (not picked yet) the first 8 unlocked. */
	public static List<String> myEmotes() {
		var mc = Minecraft.getInstance();
		Map<String, String> outfit = mc.player == null ? null : Cosmetics.OUTFITS.get(mc.player.getUUID());
		String picked = outfit == null ? null : outfit.get("emotes");
		if (picked != null) return picked.isEmpty() ? List.of() : List.of(picked.split("[+]"));
		// Not picked yet: the first 8 emotes this player has unlocked (so a new unlock shows up straight away).
		return Sheets.EMOTES.stream().filter(ClientState::unlocked).map(EmoteDef::id).limit(8).toList();
	}

	public static boolean inMatch() {
		return hud.inMatch();
	}

	/** In a Duos / Trios / Squads match right now (with teammate info). */
	public static boolean teamMatch() {
		return inMatch() && squad != null && hud.teamSize() > 1;
	}

	/** A teammate slot's colour (team_colors sheet). */
	public static int teamColor(int slot) {
		return Teams.color(slot) | 0xFF000000;
	}

	/** My own colour in a team match (my map arrow), or the classic yellow. */
	public static int myColor() {
		return teamMatch() ? teamColor(squad.mySlot()) : 0xFFFFD21F;
	}

	/** The teammate drawn as this entity, or null. */
	public static Net.Mate mateByEntity(int entityId) {
		if (!teamMatch() || entityId < 0) return null;
		for (Net.Mate m : squad.mates()) if (m.entityId() == entityId) return m;
		return null;
	}

	/**
	 * Names to colour on my screen (chat, name tags): my teammates in a team match, my party in the hub (each in their
	 * slot colour). Never myself (my own name is purple).
	 */
	public static Map<String, Integer> teammateColors() {
		Map<String, Integer> out = new LinkedHashMap<>();
		var mc = Minecraft.getInstance();
		String me = mc.player == null ? "" : mc.player.getName().getString();
		if (teamMatch()) {
			for (Net.Mate m : squad.mates()) if (!m.name().equals(me)) out.put(m.name(), teamColor(m.slot()));
		} else if (!inMatch()) {
			for (int i = 0; i < partyNames.size(); i++) if (!partyNames.get(i).equals(me)) out.put(partyNames.get(i), teamColor(i));
		}
		return out;
	}

	public static boolean inMyParty(UUID id) {
		return partyIds.contains(id.toString());
	}

	public static boolean outsideStorm(double x, double z) {
		double dx = x - hud.stormX(), dz = z - hud.stormZ();
		return hud.phase().equals("LIVE") && dx * dx + dz * dz > hud.stormR() * hud.stormR();
	}
}
