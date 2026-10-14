package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.net.Net;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a player's game needs to know about their team twice a second (Net.Squad): HUD panel, map, beacons, F prompts.
 */
final class SquadInfo {
	private SquadInfo() {
	}

	static Net.Squad build(MinecraftServer server, MatchManager mm, ServerPlayer me) {
		int team = Teams.teamOfPlayer(me.getUUID());
		long now = server.overworld().getGameTime();
		List<Net.Mate> mates = new ArrayList<>();
		for (UUID id : Teams.playersOf(team)) {
			if (id.equals(me.getUUID())) continue;
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			boolean alive = p != null && mm.alivePlayers().contains(id);
			int status;
			float secs = 0;
			if (alive && Knocked.is(p)) {
				status = 1;
				secs = p.getHealth() / Math.max(0.01F, Knocked.bleedPerSecond(p));
			} else if (alive) {
				status = 0;
			} else {
				Float left = Reboot.cardLeft(id.toString());
				status = left != null ? 2 : 3;
				secs = left == null ? 0 : left;
			}
			double[] w = mm.waypoint(id);
			mates.add(new Net.Mate(id.toString(), Teams.nameOf(id), Teams.slotOfPlayer(id), alive ? p.getId() : -1,
				p == null ? 0 : (float) p.getX(), p == null ? 0 : (float) p.getY(), p == null ? 0 : (float) p.getZ(),
				alive ? p.getHealth() : 0, alive ? Combat.shield(p) : 0, status, secs, w != null, w == null ? 0 : (float) w[0], w == null ? 0 : (float) w[1],
				mm.kills(id)));
		}
		for (BotRoster.Sim s : mm.roster().team(team)) {
			boolean real = s.entity != null && s.entity.isAlive();
			int status;
			float secs = 0, hp = real ? s.entity.getHealth() : s.health;
			if (s.alive && BotRoster.knocked(s)) {
				status = 1;
				float bleed = real ? Knocked.bleedPerSecond(s.entity) : s.bleedPerSecond;
				secs = hp / Math.max(0.01F, bleed);
			} else if (s.alive) {
				status = 0;
			} else {
				Float left = Reboot.cardLeft("bot:" + s.name);
				status = left != null ? 2 : 3;
				secs = left == null ? 0 : left;
			}
			mates.add(new Net.Mate("bot:" + s.name, s.name, s.slot, real ? s.entity.getId() : -1,
				(float) (real ? s.entity.getX() : s.x), (float) (real ? s.entity.getY() : s.y), (float) (real ? s.entity.getZ() : s.z),
				s.alive ? hp : 0, s.alive ? (real ? Combat.shield(s.entity) : s.shield) : 0, status, secs, false, 0, 0, s.kills));
		}
		mates.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
		List<Net.VanMark> vans = new ArrayList<>();
		for (Reboot.Van v : Reboot.vans())
			if (v.placed()) vans.add(new Net.VanMark((float) v.x, (float) v.y, (float) v.z, v.yaw, (int) Math.max(0, (v.readyAt - now + 19) / 20)));
		List<Net.CardMark> cards = new ArrayList<>();
		for (float[] c : Reboot.groundCards(team)) cards.add(new Net.CardMark(c[0], c[1], c[2]));
		Object[] act = Interact.current(me.getUUID());
		Knocked.Revive r = Knocked.reviving(me.getUUID());
		boolean knocked = Knocked.is(me);
		return new Net.Squad(Teams.size(), Teams.slotOfPlayer(me.getUUID()), mates, vans, cards,
			act == null ? "" : (String) act[0], act == null ? 0 : (Float) act[1],
			r == null || !knocked ? "" : r.byName(), r == null || !knocked ? 0 : r.ticks() / (MinebloxRoyale.setting("reviveSeconds") * 20),
			knocked ? me.getHealth() / Math.max(0.01F, Knocked.bleedPerSecond(me)) : 0);
	}
}
