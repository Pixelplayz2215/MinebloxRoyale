package gg.minebloxroyale.match;

import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.sheets.ModeDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The teams of the match being played (modes sheet: Solo, Duos, Trios, Squads). Every fighter has a team number and a
 * slot in it (teammate colours, team_colors sheet). In Solo everyone is a team of one, so nobody is a teammate.
 * Real players' teams are made from their parties at the bus (Parties.teamsFor); bots keep theirs on the roster.
 */
public final class Teams {
	private static ModeDef mode = Sheets.MODES.get(0);
	private static final Map<UUID, Integer> TEAM = new HashMap<>(), SLOT = new HashMap<>();
	private static final Map<UUID, String> NAMES = new LinkedHashMap<>();

	private Teams() {
	}

	public static int size() {
		return mode.teamSize();
	}

	/** Duos, Trios or Squads: teammates, knocked, reboot cards. */
	public static boolean teamMode() {
		return mode.teamSize() > 1;
	}

	static void start(ModeDef m) {
		mode = m;
		TEAM.clear();
		SLOT.clear();
		NAMES.clear();
	}

	static void addPlayer(ServerPlayer p, int team, int slot) {
		TEAM.put(p.getUUID(), team);
		SLOT.put(p.getUUID(), slot);
		NAMES.put(p.getUUID(), p.getName().getString());
	}

	/** -1 = not in this match. */
	public static int teamOfPlayer(UUID id) {
		return TEAM.getOrDefault(id, -1);
	}

	public static int slotOfPlayer(UUID id) {
		return SLOT.getOrDefault(id, 0);
	}

	public static int teamOf(Entity e) {
		if (e instanceof ServerPlayer p) return teamOfPlayer(p.getUUID());
		if (e instanceof BotEntity b) return b.team();
		return -1;
	}

	/** Teammates (Duos and up only; never yourself). */
	public static boolean sameTeam(Entity a, Entity b) {
		if (!teamMode() || a == null || b == null || a == b) return false;
		int t = teamOf(a);
		return t >= 0 && t == teamOf(b);
	}

	/** Real players of a team, in slot order. */
	public static List<UUID> playersOf(int team) {
		List<UUID> out = new ArrayList<>();
		for (var e : TEAM.entrySet()) if (e.getValue() == team) out.add(e.getKey());
		out.sort((a, b) -> Integer.compare(slotOfPlayer(a), slotOfPlayer(b)));
		return out;
	}

	public static String nameOf(UUID player) {
		return NAMES.getOrDefault(player, "?");
	}

	/** A teammate slot's colour (team_colors sheet: slot 1 is the first). */
	public static int color(int slot) {
		var c = Sheets.TEAM_COLORS;
		return c.get(Math.floorMod(slot, c.size())).color();
	}
}
