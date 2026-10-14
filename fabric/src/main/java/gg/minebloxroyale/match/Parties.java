package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.net.Net;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Parties (creator's choices): /party invite <name>, /party accept, /party leave and /party, typed in each player's own
 * game (the command lock refuses server commands) and sent here. No leader: any member can invite. At most 4 players.
 * A party lives inside one room: leaving the room (/rc, /leave, closing the game) leaves it. At the bus, each party is
 * made into teams of the mode's size (Parties.teamsFor); a short team gets bot teammates.
 */
public final class Parties {
	private static final class Party {
		final List<UUID> members = new ArrayList<>();
	}

	private record Invite(UUID from, String fromName, Party party, long until) {
	}

	private static final Map<UUID, Party> OF = new HashMap<>();
	/** Invitee -> their invites, newest last. */
	private static final Map<UUID, List<Invite>> INVITES = new HashMap<>();

	private Parties() {
	}

	private static int max() {
		return Math.round(MinebloxRoyale.setting("partyMax"));
	}

	/** Everyone in this player's party, in the order they joined (just them when they have none). */
	public static List<UUID> members(UUID player) {
		Party p = OF.get(player);
		return p == null ? List.of(player) : List.copyOf(p.members);
	}

	public static boolean sameParty(UUID a, UUID b) {
		Party p = OF.get(a);
		return a.equals(b) || p != null && p.members.contains(b);
	}

	/** party:<invite:name | accept[:name] | leave | list> from a player's own game. */
	public static void handle(ServerPlayer p, String arg) {
		MinecraftServer server = p.level().getServer();
		String[] a = arg.split(":", 2);
		switch (a[0]) {
			case "invite" -> invite(server, p, a.length > 1 ? a[1].trim() : "");
			case "accept" -> accept(server, p, a.length > 1 ? a[1].trim() : "");
			case "leave" -> leave(server, p.getUUID(), true);
			default -> list(server, p);
		}
	}

	private static void invite(MinecraftServer server, ServerPlayer from, String name) {
		ServerPlayer to = name.isEmpty() ? null : server.getPlayerList().getPlayerByName(name);
		if (to == null) {
			for (ServerPlayer q : server.getPlayerList().getPlayers()) if (q.getName().getString().equalsIgnoreCase(name)) to = q;
		}
		if (to == null) {
			say(from, "Nobody called '" + name + "' is in this room. (Party invites only reach players in the same room.)", ChatFormatting.RED);
			return;
		}
		if (to == from) {
			say(from, "You can't invite yourself.", ChatFormatting.RED);
			return;
		}
		Party party = OF.get(from.getUUID());
		if (party != null && party.members.contains(to.getUUID())) {
			say(from, to.getName().getString() + " is already in your party.", ChatFormatting.YELLOW);
			return;
		}
		if (party != null && party.members.size() >= max()) {
			say(from, "Your party is full (" + max() + " players).", ChatFormatting.RED);
			return;
		}
		if (party == null) {
			party = new Party();
			party.members.add(from.getUUID());
			// Not registered until someone accepts: an unanswered invite doesn't make a party of one.
		}
		long until = server.overworld().getGameTime() + Math.round(MinebloxRoyale.setting("partyInviteSeconds") * 20);
		List<Invite> list = INVITES.computeIfAbsent(to.getUUID(), k -> new ArrayList<>());
		list.removeIf(i -> i.from().equals(from.getUUID()));
		list.add(new Invite(from.getUUID(), from.getName().getString(), party, until));
		MutableComponent accept = Component.literal("[Accept]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
			.withClickEvent(new ClickEvent.SuggestCommand("/party accept " + from.getName().getString()))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click, then press Enter"))));
		to.sendSystemMessage(Component.empty().append(MatchManager.who(from)).append(Component.literal(" invited you to their party. ").withStyle(ChatFormatting.WHITE))
			.append(accept).append(Component.literal(" (or type /party accept)").withStyle(ChatFormatting.GRAY)));
		say(from, "Party invite sent to " + to.getName().getString() + ".", ChatFormatting.GRAY);
	}

	private static void accept(MinecraftServer server, ServerPlayer p, String name) {
		long now = server.overworld().getGameTime();
		List<Invite> list = INVITES.getOrDefault(p.getUUID(), List.of());
		Invite inv = null;
		for (Invite i : list)
			if (i.until() >= now && (name.isEmpty() || i.fromName().equalsIgnoreCase(name))) inv = i; // the newest that matches
		if (inv == null) {
			say(p, name.isEmpty() ? "You have no party invite right now." : "No party invite from " + name + " right now.", ChatFormatting.RED);
			return;
		}
		ServerPlayer inviter = server.getPlayerList().getPlayer(inv.from());
		Party party = OF.get(inv.from());
		if (party == null) {
			if (inviter == null) {
				say(p, inv.fromName() + " has left the room.", ChatFormatting.RED);
				list.remove(inv);
				return;
			}
			party = inv.party();
			if (!party.members.contains(inv.from())) party.members.add(0, inv.from());
			OF.put(inv.from(), party);
		}
		if (party.members.contains(p.getUUID())) {
			say(p, "You're already in that party.", ChatFormatting.YELLOW);
			list.remove(inv);
			return;
		}
		if (party.members.size() >= max()) {
			say(p, "That party is full (" + max() + " players).", ChatFormatting.RED);
			return;
		}
		leave(server, p.getUUID(), false);
		party.members.add(p.getUUID());
		OF.put(p.getUUID(), party);
		list.remove(inv);
		boolean inMatch = MatchManager.get().state() != MatchManager.State.HUB && MatchManager.get().state() != MatchManager.State.COUNTDOWN;
		for (UUID m : party.members) {
			ServerPlayer q = server.getPlayerList().getPlayer(m);
			if (q != null) q.sendSystemMessage(Component.empty().append(MatchManager.who(p)).append(Component.literal(" joined the party (" + party.members.size() + "/" + max() + ")"
				+ (inMatch ? " - you team up from the next match." : ".")).withStyle(ChatFormatting.GREEN)));
		}
		MinebloxRoyale.LOG.info("BR: {} joined {}'s party ({} players)", p.getName().getString(), inv.fromName(), party.members.size());
		sendAll(server);
	}

	/** Leaves the party (the command, or leaving the room). A party of one is no party. */
	public static void leave(MinecraftServer server, UUID who, boolean tell) {
		Party party = OF.remove(who);
		INVITES.remove(who);
		if (party == null) {
			if (tell) {
				ServerPlayer p = server.getPlayerList().getPlayer(who);
				if (p != null) say(p, "You're not in a party. Invite someone with /party invite <name>.", ChatFormatting.YELLOW);
			}
			return;
		}
		party.members.remove(who);
		String name = Teams.nameOf(who);
		ServerPlayer leaver = server.getPlayerList().getPlayer(who);
		if (leaver != null) name = leaver.getName().getString();
		if (tell && leaver != null) say(leaver, "You left the party.", ChatFormatting.GRAY);
		for (UUID m : party.members) {
			ServerPlayer q = server.getPlayerList().getPlayer(m);
			if (q != null) say(q, name + " left the party.", ChatFormatting.GRAY);
		}
		if (party.members.size() <= 1) for (UUID m : party.members) OF.remove(m);
		sendAll(server);
	}

	private static void list(MinecraftServer server, ServerPlayer p) {
		Party party = OF.get(p.getUUID());
		if (party == null) {
			say(p, "You're not in a party. /party invite <name> asks someone in this room to team up (up to " + max() + ").", ChatFormatting.YELLOW);
			return;
		}
		MutableComponent line = Component.literal("Party (" + party.members.size() + "/" + max() + "): ").withStyle(ChatFormatting.GRAY);
		for (int i = 0; i < party.members.size(); i++) {
			if (i > 0) line.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
			ServerPlayer q = server.getPlayerList().getPlayer(party.members.get(i));
			int color = Teams.color(i) & 0xFFFFFF;
			line.append(Component.literal(q == null ? "?" : q.getName().getString()).withStyle(s -> s.withColor(color)));
		}
		p.sendSystemMessage(line);
		say(p, "/party invite <name> - /party leave", ChatFormatting.DARK_GRAY);
	}

	/** Leaving the room leaves the party (creator: parties live inside one room). */
	public static void onLeave(ServerPlayer p) {
		leave(p.level().getServer(), p.getUUID(), false);
	}

	/**
	 * The match's real teams: each party split into teams of `size` (in the order they joined), everyone else alone.
	 * In Solo every player is their own team.
	 */
	static List<List<ServerPlayer>> teamsFor(List<ServerPlayer> players, int size) {
		List<List<ServerPlayer>> teams = new ArrayList<>();
		List<UUID> placed = new ArrayList<>();
		for (ServerPlayer p : players) {
			if (placed.contains(p.getUUID())) continue;
			List<ServerPlayer> group = new ArrayList<>();
			for (UUID m : members(p.getUUID()))
				for (ServerPlayer q : players)
					if (q.getUUID().equals(m) && !placed.contains(m)) {
						group.add(q);
						placed.add(m);
					}
			if (group.isEmpty()) {
				group.add(p);
				placed.add(p.getUUID());
			}
			for (int i = 0; i < group.size(); i += Math.max(1, size)) teams.add(new ArrayList<>(group.subList(i, Math.min(group.size(), i + Math.max(1, size)))));
		}
		return teams;
	}

	/** Every player's party, to their own game (hub list, teammate colours, the spectator teleport menu). */
	public static void sendAll(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) sendTo(server, p);
	}

	public static void sendTo(MinecraftServer server, ServerPlayer p) {
		List<String> ids = new ArrayList<>(), names = new ArrayList<>();
		Party party = OF.get(p.getUUID());
		if (party != null)
			for (UUID m : party.members) {
				ServerPlayer q = server.getPlayerList().getPlayer(m);
				ids.add(m.toString());
				names.add(q == null ? "?" : q.getName().getString());
			}
		ServerPlayNetworking.send(p, new Net.PartyInfo(ids, names));
	}

	/** Expired invites go (once a second). */
	static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		INVITES.values().forEach(l -> l.removeIf(i -> i.until() < now));
		INVITES.values().removeIf(List::isEmpty);
	}

	/** Leaving the world clears every party (a party never outlives its room). */
	public static void clear() {
		OF.clear();
		INVITES.clear();
	}

	private static void say(ServerPlayer p, String text, ChatFormatting color) {
		p.sendSystemMessage(Component.literal(text).withStyle(color));
	}
}
