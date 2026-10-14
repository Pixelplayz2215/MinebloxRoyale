package gg.minebloxroyale.match;

import gg.minebloxroyale.MeltyTest;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.hub.HubBuilder;
import gg.minebloxroyale.hub.Leaderboard;
import gg.minebloxroyale.hub.Locker;
import gg.minebloxroyale.hub.Obby;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.net.RoomCode;
import gg.minebloxroyale.sheets.ModeDef;
import gg.minebloxroyale.sheets.Sheets;
import gg.minebloxroyale.sheets.StormPhaseDef;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.TeamColor;

/**
 * One match at a time: HUB -> COUNTDOWN -> LIVE (the Battle Bus, then drop, loot, storm) -> ENDED -> HUB. Fortnite's
 * rules (storm phases from the storm sheet, last one standing wins) run on Minecraft terrain far from the hub. Modes
 * (creator): Solo, Duos, Trios and Squads, picked by the portal everyone stands on; in a team mode the last team
 * standing wins and every member gets the Victory Royale.
 */
public final class MatchManager {
	public enum State { HUB, COUNTDOWN, LIVE, ENDED }

	private static final MatchManager INSTANCE = new MatchManager();
	/** Test mode (MINEBLOXROYALE_TEST, or a Melty test run): matches start by themselves, on faster clocks. */
	private static final boolean TEST = System.getenv("MINEBLOXROYALE_TEST") != null || MeltyTest.active();
	/**
	 * Test mode's match mode: MINEBLOXROYALE_TEST=solo, duos, trios or squads; "all" takes turns; anything else: the
	 * current one.
	 */
	private static final String TEST_MODE = System.getenv("MINEBLOXROYALE_TEST") == null ? "" : System.getenv("MINEBLOXROYALE_TEST").trim().toLowerCase(Locale.ROOT);

	public static MatchManager get() {
		return INSTANCE;
	}

	private State state = State.HUB;
	/** A match started by /br start: no portal needed for the countdown, and its bot count (-1 = the usual). */
	private boolean devStarted;
	private int devBots = -1;
	private int timer;
	private int matchIndex;
	/** The mode of the countdown or match (the portal everyone stood on). */
	private ModeDef mode = Sheets.MODES.get(0);
	private final Set<UUID> players = new LinkedHashSet<>();
	private final Set<UUID> alivePlayers = new LinkedHashSet<>();
	private final Set<UUID> dropping = new HashSet<>();
	private final Map<UUID, Integer> kills = new HashMap<>();
	/** Waypoints players placed (shared with their teammates in team modes; Solo: nobody else gets them). */
	private final Map<UUID, double[]> waypoints = new HashMap<>();
	private final BotRoster roster = new BotRoster();
	private BattleBusEntity bus;
	/**
	 * The bus route runs on the match clock (from, unit direction, length, launch time), not on the bus entity: an
	 * entity nobody is near gets frozen or unloaded by Minecraft, and the bots still aboard used to wait for a jump
	 * that never came (creator: "18 people left" after the final storm; all 18 were still in the bus).
	 */
	private Vec3 busFrom, busDir;
	private double busLength;
	private long busLaunched = -1;
	private final Set<UUID> gliding = new HashSet<>();
	private final Set<UUID> leftBus = new HashSet<>();
	/** Winners still in the match: fireworks over them at the end. */
	private final Set<UUID> fireworksFor = new HashSet<>();
	private final List<BlockPos> chestSpots = new ArrayList<>();
	private final List<BlockPos> placedChests = new ArrayList<>();
	/** Where this match's custom buildings go (built as their chunks load). */
	private final List<BlockPos> structureSpots = new ArrayList<>();
	private int chestCursor;
	private double cx, cz, fromX, fromZ, toX, toZ;
	private float radius, fromR, toR;
	private int phase = -1;
	private boolean shrinking;
	private int phaseTimer;
	/** Game time the storm closed completely (0: not yet). */
	private long closedAt;
	/** Who each eliminated player was last watching (to tell "they went out" from "I let go with sneak"). */
	private final Map<UUID, Entity> watching = new HashMap<>();
	private String winnerName = "";
	private int testStartIn = -1;
	private long liveSince;
	private MinecraftServer server;

	private MatchManager() {
	}

	// ---- lifecycle ----

	public void onServerStarted(MinecraftServer server) {
		this.server = server;
		state = State.HUB;
		matchIndex = readIndex(server);
		Leaderboard.reset();
		Parties.clear(); // parties live inside one room, while it's open
		IslandRestore.loadAndRestore(server); // repairs the island if the last session stopped mid-match
		if (TEST) testStartIn = 20 * 25;
	}

	public void onServerStopping(MinecraftServer server) {
		roster.clear();
		Parties.clear();
	}

	public void onJoin(ServerPlayer player) {
		MeltyTest.pass("world-opens");
		Combat.setupFighter(player);
		setTeam(player, alivePlayers.contains(player.getUUID())); // blue name; pushing only in a match
		// Crafting is off: with limited_crafting on, a player who knows no recipes can't craft anything.
		player.resetRecipes(player.level().getServer().getRecipeManager().getRecipes());
		Obby.sendUnlocks(player);
		IslandMap.sendTo(player); // the full map (Tab), once the island is ready
		Locker.onJoin(player); // outfits: theirs to everyone, everyone's to them
		RoomCode.sendTo(player, RoomCode.of(player.level().getServer())); // the room's 8-digit code (/rc)
		Parties.sendTo(player.level().getServer(), player);
		ServerPlayNetworking.send(player, new Net.Knocks(Knocked.ids(player.level().getServer())));
		if (!alivePlayers.contains(player.getUUID())) sendToHub(player);
	}

	public void onLeave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		UUID id = player.getUUID();
		// Quitting mid-match counts as a lost match on the leaderboard.
		if (state == State.LIVE && players.contains(id))
			Leaderboard.record(server, id, player.getName().getString(), false, kills.getOrDefault(id, 0));
		if (alivePlayers.contains(id)) Reboot.dropCarried(player); // teammates' cards they carried stay in the match
		Knocked.forget(id);
		Interact.set(player, false);
		alivePlayers.remove(id);
		players.remove(id);
		dropping.remove(id);
		waypoints.remove(id);
		Reboot.forfeit(server, id.toString());
		Parties.onLeave(player); // leaving the room leaves the party
	}

	public boolean isAlive(ServerPlayer p) {
		return alivePlayers.contains(p.getUUID()) && state == State.LIVE;
	}

	/** A fighter still in the live match (players: not eliminated; bots: alive). */
	public boolean inMatchAndAlive(LivingEntity e) {
		if (e instanceof ServerPlayer p) return isAlive(p);
		return e instanceof BotEntity && state == State.LIVE && e.isAlive();
	}

	/**
	 * Is this player in the hub, i.e. not taking part in the match going on (or there is none)? Eliminated players
	 * still watching the match aren't, until they press H or the match ends.
	 */
	public boolean inHub(ServerPlayer p) {
		return state == State.HUB || state == State.COUNTDOWN || !players.contains(p.getUUID());
	}

	public State state() {
		return state;
	}

	/** The Battle Bus right now (bots ride it too), or null once it has gone. */
	public BattleBusEntity bus() {
		return bus;
	}

	/** The bus is still on its route (by the match clock). */
	public boolean busFlying(long now) {
		return busLaunched >= 0 && busTravelled(now) < busLength;
	}

	private double busTravelled(long now) {
		return Math.max(0, now - busLaunched) * (double) MinebloxRoyale.setting("busSpeed");
	}

	/** 0..1 along the route. */
	public float busProgress(long now) {
		return busLength <= 0 ? 1 : (float) Math.min(1, busTravelled(now) / busLength);
	}

	/** Where the bus is on its route now. */
	public Vec3 busPos(long now) {
		double t = Math.min(busTravelled(now), busLength);
		return new Vec3(busFrom.x + busDir.x * t, busFrom.y, busFrom.z + busDir.z * t);
	}

	/** The doors are open (busLeadSeconds after launch). */
	public boolean busDoorsOpen(long now) {
		return busLaunched < 0 || now - busLaunched >= MinebloxRoyale.setting("busLeadSeconds") * 20;
	}

	/** Has this player jumped out of the bus (their bot teammates follow them down)? */
	public boolean leftBus(UUID player) {
		return leftBus.contains(player);
	}

	/** Chests on the island: placed ones (bots loot these) and spots still waiting for their chunk. */
	public List<BlockPos> placedChests() {
		return placedChests;
	}

	public List<BlockPos> chestSpots() {
		return chestSpots;
	}

	/**
	 * The storm's damage per second: its phase's, and once it has closed completely it keeps rising (stormClosedRamp
	 * more every 10 s) so a match always ends.
	 */
	float stormDps(long now) {
		float dps = Sheets.STORM.get(phase).damagePerSecond();
		if (closedAt > 0 && now > closedAt) dps += MinebloxRoyale.setting("stormClosedRamp") * ((now - closedAt) / 200);
		return dps;
	}

	/** A real player is still in the match (standing or knocked). */
	boolean anyPlayerIn() {
		return !alivePlayers.isEmpty();
	}

	/** Where bots head: the next circle's centre while it is known, else the current one. */
	public Vec3 safeSpot() {
		return new Vec3(toX, 0, toZ);
	}

	/** Bots hold fire for the first botGraceSeconds after the drop, while everyone loots. */
	public boolean botsMayShoot(long gameTime) {
		return state == State.LIVE && gameTime - liveSince > MinebloxRoyale.setting("botGraceSeconds") * 20;
	}

	public float safeRadius() {
		return Math.min(radius, Math.max(toR, 4F));
	}

	public String describe() {
		return "state=" + state + " mode=" + mode.id() + " match=" + matchIndex + " players=" + alivePlayers.size() + "/" + players.size() + " bots=" + roster.alive()
			+ " (real " + roster.realCount() + ") knocked=" + (server == null ? 0 : Knocked.ids(server).length)
			+ " phase=" + phase + " radius=" + Math.round(radius);
	}

	// ---- teams ----

	/** Does this fighter's team still have someone standing (alive and not knocked), apart from them? */
	public boolean hasStandingTeammate(LivingEntity e) {
		int team = Teams.teamOf(e);
		if (!Teams.teamMode() || team < 0) return false;
		MinecraftServer server = e.level().getServer();
		for (UUID id : alivePlayers) {
			if (id.equals(e.getUUID()) || Teams.teamOfPlayer(id) != team) continue;
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && !Knocked.is(p)) return true;
		}
		return roster.standing(team, e);
	}

	/** A real player of this team is still standing (simulated bots check this before being knocked). */
	boolean playerStanding(int team) {
		if (server == null) return false;
		for (UUID id : alivePlayers) {
			if (Teams.teamOfPlayer(id) != team) continue;
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && !Knocked.is(p)) return true;
		}
		return false;
	}

	/** Teams with anyone still in the match (standing or knocked). */
	private Set<Integer> teamsAlive() {
		Set<Integer> out = new HashSet<>(roster.aliveTeams());
		for (UUID id : alivePlayers) out.add(Teams.teamOfPlayer(id));
		return out;
	}

	private boolean teamAlive(int team) {
		return teamsAlive().contains(team);
	}

	/** waypoint:<x>:<z> or waypoint:clear from a player's map (shared with teammates only). */
	public void setWaypoint(ServerPlayer p, String arg) {
		if (arg.equals("clear") || !players.contains(p.getUUID())) {
			waypoints.remove(p.getUUID());
			return;
		}
		String[] xz = arg.split(":");
		try {
			if (xz.length == 2) waypoints.put(p.getUUID(), new double[] { Double.parseDouble(xz[0]), Double.parseDouble(xz[1]) });
		} catch (NumberFormatException ignored) {
		}
	}

	double[] waypoint(UUID player) {
		return waypoints.get(player);
	}

	BotRoster roster() {
		return roster;
	}

	Set<UUID> alivePlayers() {
		return alivePlayers;
	}

	Set<UUID> players() {
		return players;
	}

	// ---- starting ----

	/** Test mode: the mode last picked (Solo at first). */
	public void requestStart(MinecraftServer server, String why) {
		requestStart(server, why, mode);
	}

	/**
	 * /br start (DevCommands): a match in this mode now, without everyone standing on its portal, with this many bots
	 * (-1: the usual, up to match fillTo fighters). False if it couldn't start (a match is on, or the island isn't ready).
	 */
	public boolean devStart(MinecraftServer server, ModeDef m, int bots) {
		if (state != State.HUB) return false;
		devBots = bots;
		devStarted = true;
		requestStart(server, "development command", m);
		if (state == State.COUNTDOWN) return true;
		devBots = -1; // the island isn't ready yet: nothing started
		devStarted = false;
		return false;
	}

	/** /br storm (DevCommands): the storm skips ahead one step (the wait, or the shrink). Returns what happened. */
	public String skipStorm() {
		if (state != State.LIVE || phaseTimer == Integer.MAX_VALUE) return "No storm to skip (no match, or the storm has fully closed).";
		String step = shrinking ? "shrink to radius " + Math.round(toR) : "wait before phase " + (phase + 1) + "'s shrink";
		phaseTimer = 1;
		return "Storm: skipped the " + step + ".";
	}

	public void requestStart(MinecraftServer server, String why, ModeDef m) {
		if (state != State.HUB) return;
		// A new island every match, made while the last one is played: wait if it isn't ready yet (the hub shows
		// "Island N% - m:ss left").
		if (!IslandPregen.ready()) {
			if (server.getTickCount() % 100 == 0)
				broadcast(server, Component.literal("The next island is still being made (" + IslandPregen.percent() + "%" + (IslandPregen.timeLeft().isEmpty() ? "" : ", " + IslandPregen.timeLeft()) + ") - the match starts as soon as it's ready.").withStyle(ChatFormatting.YELLOW));
			return;
		}
		mode = m;
		state = State.COUNTDOWN;
		timer = Math.round(MinebloxRoyale.setting("countdownSeconds") * 20);
		MinebloxRoyale.LOG.info("BR: match {} countdown, {} ({})", matchIndex + 1, mode.name(), why);
		for (ServerPlayer p : server.getPlayerList().getPlayers())
			title(p, Component.literal(mode.name().toUpperCase(Locale.ROOT)).withStyle(s -> s.withColor(mode.color())), Component.literal("The Battle Bus leaves in " + (timer / 20) + "..."), 5, 40, 10);
	}

	/**
	 * Fortnite's start: everyone boards the Battle Bus, which flies a straight line over the island at the height
	 * limit. The storm clock, bots and loot start now; players jump out with Space (or get dropped at the end of the
	 * route).
	 */
	private void launchBus(MinecraftServer server) {
		ServerLevel level = server.overworld();
		state = State.LIVE;
		liveSince = level.getGameTime();
		players.clear();
		alivePlayers.clear();
		kills.clear();
		dropping.clear();
		leftBus.clear();
		gliding.clear();
		waypoints.clear();
		Knocked.clearAll();
		Interact.clear();
		// A new island every match (generated in the background); the next one is made while this one is played.
		cx = IslandPregen.centerX();
		cz = IslandPregen.centerZ();
		IslandPregen.matchStarted(server);
		matchIndex++;
		writeIndex(server);
		// A Melty test run lays the match out from its seed.
		RandomSource rnd = MeltyTest.active() ? RandomSource.create(MeltyTest.seed() + matchIndex) : level.getRandom();
		float startR = MinebloxRoyale.setting("startRadius");
		chestSpots.clear();
		placedChests.clear();
		chestCursor = 0;
		for (int i = 0; i < (int) MinebloxRoyale.setting("chests"); i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * startR * 0.9;
			chestSpots.add(BlockPos.containing(cx + Math.cos(a) * d, 0, cz + Math.sin(a) * d));
		}
		structureSpots.clear();
		CustomStructures.clearAll();
		CustomStructures.plan(rnd);
		for (int i = 0; i < (int) MinebloxRoyale.setting("structureSpots"); i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * startR * 0.85;
			structureSpots.add(BlockPos.containing(cx + Math.cos(a) * d, 0, cz + Math.sin(a) * d));
		}
		// The bus route: a random line through the island that starts and ends inside it (never in the storm).
		double a = rnd.nextDouble() * Math.PI * 2;
		double off = (rnd.nextDouble() - 0.5) * startR * 0.6;
		double reach = Math.sqrt(Math.max(0, Math.pow(startR - MinebloxRoyale.setting("busInset"), 2) - off * off));
		double px = -Math.sin(a) * off, pz = Math.cos(a) * off;
		double busY = level.getMaxY() - MinebloxRoyale.setting("busBelowTop");
		Vec3 from = new Vec3(cx + px + Math.cos(a) * reach, busY, cz + pz + Math.sin(a) * reach);
		Vec3 to = new Vec3(cx + px - Math.cos(a) * reach, busY, cz + pz - Math.sin(a) * reach);
		level.getChunk(Mth.floor(from.x) >> 4, Mth.floor(from.z) >> 4);
		bus = new BattleBusEntity(BattleBusEntity.TYPE, level);
		bus.route(from, to);
		level.addFreshEntity(bus);
		busFrom = from;
		busLength = Math.hypot(to.x - from.x, to.z - from.z);
		busDir = new Vec3((to.x - from.x) / busLength, 0, (to.z - from.z) / busLength);
		busLaunched = level.getGameTime();
		List<ServerPlayer> boarding = new ArrayList<>(server.getPlayerList().getPlayers());
		for (ServerPlayer p : boarding) {
			players.add(p.getUUID());
			alivePlayers.add(p.getUUID());
			setTeam(p, true); // players push each other in a match
			p.getInventory().clearContent();
			Combat.reset(p);
			p.removeAllEffects();
			p.setGameMode(GameType.SURVIVAL);
			p.setCamera(p);
			p.teleportTo(level, from.x, from.y, from.z, Set.of(), bus.getYRot(), 10F, true);
			p.startRiding(bus, true, false);
			title(p, Component.literal("BATTLE BUS").withStyle(ChatFormatting.AQUA), Component.literal(mode.name()), 5, 40, 10);
		}
		// Teams (modes sheet): each party split into teams of the mode's size, everyone else alone; a short team gets
		// bot teammates; the rest of the 100 are bot teams of the same size (Solo: every bot alone).
		int size = mode.teamSize();
		Teams.start(mode);
		List<BotRoster.Seat> seats = new ArrayList<>();
		int team = 0;
		for (List<ServerPlayer> t : Parties.teamsFor(boarding, size)) {
			for (int s = 0; s < t.size(); s++) Teams.addPlayer(t.get(s), team, s);
			for (int s = t.size(); s < size; s++) seats.add(new BotRoster.Seat(team, s, t.get(0).getUUID()));
			team++;
		}
		// (A /br start match: its players plus that many bots, rounded up to whole teams.)
		int teams = Math.max(team, devBots >= 0 ? (boarding.size() + devBots + size - 1) / size : (int) MinebloxRoyale.setting("fillTo") / size);
		devBots = -1;
		devStarted = false;
		for (; team < teams; team++)
			for (int s = 0; s < size; s++) seats.add(new BotRoster.Seat(team, s, null));
		roster.start(seats, cx, cz, startR, rnd);
		Reboot.start(cx, cz, startR, rnd);
		if (Teams.teamMode())
			for (ServerPlayer p : boarding) {
				int t = Teams.teamOfPlayer(p.getUUID());
				List<String> names = new ArrayList<>();
				for (UUID m : Teams.playersOf(t)) if (!m.equals(p.getUUID())) names.add(Teams.nameOf(m));
				for (String b : roster.names(t)) names.add(b + " (bot)");
				p.sendSystemMessage(Component.literal(mode.name() + " - your team: " + (names.isEmpty() ? "just you" : String.join(", ", names))).withStyle(s -> s.withColor(mode.color())));
			}
		phase = -1;
		closedAt = 0;
		radius = startR;
		fromR = toR = startR;
		fromX = toX = cx;
		fromZ = toZ = cz;
		nextPhase(level);
		MinebloxRoyale.LOG.info("BR: match {} ({}) bus launched at y={}, centre {} {}, {} players, {} bots, {} teams", matchIndex, mode.name(), (int) busY, (int) cx, (int) cz,
			players.size(), roster.alive(), teams);
		if (players.isEmpty() || roster.alive() == 0) MeltyTest.fail("match-starts", players.size() + " players and " + roster.alive() + " bots on the bus");
		else MeltyTest.pass("match-starts");
	}

	/** Chests appear as their chunk loads (no forced world generation): checked a few per tick. */
	private void prepareArena(ServerLevel level) {
		Reboot.prepare(level); // reboot vans, once their chunks are there
		// Custom buildings, once the chunks around their spot exist (one per tick at most), until the island has them all.
		if (!CustomStructures.wantsMore()) structureSpots.clear();
		for (int i = 0; i < structureSpots.size(); i++) {
			BlockPos c = structureSpots.get(i);
			// (12 blocks round: a big parking lot reaches 11 from its middle.)
			if (!level.hasChunk((c.getX() - 12) >> 4, (c.getZ() - 12) >> 4) || !level.hasChunk((c.getX() + 12) >> 4, (c.getZ() + 12) >> 4)
				|| !level.hasChunk((c.getX() - 12) >> 4, (c.getZ() + 12) >> 4) || !level.hasChunk((c.getX() + 12) >> 4, (c.getZ() - 12) >> 4)) continue;
			structureSpots.remove(i);
			BlockPos loot = CustomStructures.build(level, c.getX(), c.getZ(), level.getRandom());
			if (loot != null) placedChests.add(loot);
			break;
		}
		if (chestSpots.isEmpty()) return;
		for (int n = 0; n < 24 && !chestSpots.isEmpty(); n++) {
			chestCursor = (chestCursor + 1) % chestSpots.size();
			BlockPos c = chestSpots.get(chestCursor);
			if (!level.hasChunk(c.getX() >> 4, c.getZ() >> 4)) continue;
			chestSpots.remove(chestCursor);
			// On bare ground near the spot (creator: never on a roof, or in or on water); some are the rare blue chest.
			BlockPos at = LootChests.groundSpot(level, c.getX(), c.getZ(), level.getRandom());
			boolean rare = level.getRandom().nextFloat() < MinebloxRoyale.setting("enderChestWild");
			if (at != null && LootChests.place(level, at, rare, level.getRandom())) placedChests.add(at);
			if (chestSpots.isEmpty()) break;
			chestCursor = Math.max(0, chestCursor - 1);
		}
	}

	/** Space while riding (or the route ending): leave the bus and skydive. */
	public void jumpFromBus(ServerPlayer p) {
		if (bus == null || state != State.LIVE || p.getVehicle() != bus) return;
		if (!busDoorsOpen(p.level().getGameTime())) {
			p.sendSystemMessage(Component.literal("The bus doors are still closed...").withStyle(ChatFormatting.YELLOW), true);
			return;
		}
		p.stopRiding();
		startDrop(p);
	}

	private void startDrop(ServerPlayer p) {
		if (!leftBus.add(p.getUUID())) return;
		dropping.add(p.getUUID());
		Net.sfxTo(p, "falling", p.position());
		MinebloxRoyale.LOG.info("BR: {} jumped from the bus at y={}", p.getName().getString(), (int) p.getY());
		MeltyTest.pass("bus-drop");
	}

	private void tickBus(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (busLaunched >= 0 && !busFlying(now) && bus == null) {
			busLaunched = -1;
			MinebloxRoyale.LOG.info("BR: the Battle Bus finished its route (with nobody aboard to keep it loaded)");
		}
		if (bus != null && bus.isRemoved()) {
			// Unloaded (nobody near it, so nobody aboard): the route carries on by the match clock without it.
			bus = null;
			MinebloxRoyale.LOG.info("BR: the Battle Bus was unloaded (no players near it); its route carries on");
		}
		if (bus == null) return;
		// Keep the entity in step with the clock (one that stopped ticking far from everyone catches up).
		Vec3 at = busPos(now);
		if (bus.position().distanceToSqr(at) > 4 * 4) bus.catchUp(at, busTravelled(now));
		for (UUID id : alivePlayers) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null || leftBus.contains(id) || p.getVehicle() == bus) continue;
			// Left the bus some other way (sneak): before the doors open, back in; afterwards it counts as a jump.
			if (!busDoorsOpen(now) && bus.isAlive()) p.startRiding(bus, true, false);
			else startDrop(p);
		}
		if (bus.arrived() || !busFlying(now)) {
			for (var passenger : List.copyOf(bus.getPassengers())) {
				passenger.stopRiding();
				if (passenger instanceof ServerPlayer p) {
					p.sendSystemMessage(Component.literal("End of the line - everybody out!").withStyle(ChatFormatting.AQUA), true);
					startDrop(p);
				}
			}
			bus.discard();
			bus = null;
			busLaunched = -1;
			MinebloxRoyale.LOG.info("BR: the Battle Bus finished its route");
		}
	}

	// ---- storm ----

	private void nextPhase(ServerLevel level) {
		phase++;
		if (phase >= Sheets.STORM.size()) {
			phase = Sheets.STORM.size() - 1;
			shrinking = false;
			phaseTimer = Integer.MAX_VALUE;
			closedAt = level.getGameTime();
			return;
		}
		StormPhaseDef def = Sheets.STORM.get(phase);
		shrinking = false;
		phaseTimer = scaled(def.waitSeconds());
		fromX = toX;
		fromZ = toZ;
		fromR = radius;
		// Fortnite: the next circle lies somewhere inside the current one.
		RandomSource rnd = level.getRandom();
		float slack = Math.max(0, radius - def.endRadius());
		double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * slack;
		toX = fromX + Math.cos(a) * d;
		toZ = fromZ + Math.sin(a) * d;
		toR = def.endRadius();
		broadcast(level.getServer(), Component.literal("Storm eye forming! Shrinks in " + def.waitSeconds() + "s").withStyle(ChatFormatting.LIGHT_PURPLE));
		MinebloxRoyale.LOG.info("BR: storm phase {} -> radius {}", phase + 1, toR);
	}

	private int scaled(int seconds) {
		return TEST ? seconds * 20 / 6 : seconds * 20;
	}

	private void tickStorm(ServerLevel level) {
		StormPhaseDef def = Sheets.STORM.get(phase);
		if (phaseTimer != Integer.MAX_VALUE) phaseTimer--;
		if (!shrinking && phaseTimer <= 0) {
			shrinking = true;
			phaseTimer = scaled(def.shrinkSeconds());
			MeltyTest.pass("storm-moves");
			// No storm music: the on-screen "STORM EYE SHRINKING" banner (BRHud) plus one short warning sound per
			// shrink.
			for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) Net.sfxTo(p, "storm_warning", p.position());
		} else if (shrinking) {
			float t = 1F - phaseTimer / (float) scaled(def.shrinkSeconds());
			radius = Mth.lerp(t, fromR, toR);
			cx = Mth.lerp(t, fromX, toX);
			cz = Mth.lerp(t, fromZ, toZ);
			if (phaseTimer <= 0) {
				radius = toR;
				cx = toX;
				cz = toZ;
				nextPhase(level);
			}
		}
		if (level.getGameTime() % 20 == 0) {
			float dps = stormDps(level.getGameTime());
			for (LivingEntity e : fighters(level.getServer())) {
				double dx = e.getX() - cx, dz = e.getZ() - cz;
				if (dx * dx + dz * dz > radius * radius) Combat.hurtStorm(level, e, level.damageSources().magic(), dps);
			}
		}
	}

	private List<LivingEntity> fighters(MinecraftServer server) {
		List<LivingEntity> out = new ArrayList<>();
		for (UUID id : alivePlayers) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) out.add(p);
		}
		for (BotEntity b : server.overworld().getEntities(BotEntity.TYPE, e -> e.isAlive())) out.add(b);
		return out;
	}

	// ---- eliminations ----

	public void eliminate(ServerPlayer victim, Entity killer) {
		eliminate(victim, killer, null);
	}

	/**
	 * A player is out (byName: who gets the credit when there's no entity, e.g. a simulated bot that knocked them). In
	 * a team mode a teammate still standing means their reboot card drops and they watch a teammate; when the whole
	 * team is out, everyone in it sees the team's placement.
	 */
	public void eliminate(ServerPlayer victim, Entity killer, String byName) {
		MinecraftServer server = victim.level().getServer();
		Knocked.forget(victim.getUUID());
		Interact.set(victim, false);
		alivePlayers.remove(victim.getUUID());
		dropping.remove(victim.getUUID());
		if (!(killer != null && Teams.sameTeam(killer, victim))) countKill(killer, byName);
		Net.sfxAll(server, "oof", victim.position());
		Reboot.dropCarried(victim); // teammates' cards they carried
		Loot.dropInventory(victim);
		Reboot.dropCardFor(victim, victim.getUUID().toString()); // their own card, if a teammate still stands
		victim.setGameMode(GameType.SPECTATOR);
		Combat.reset(victim);
		String by = byName != null ? byName : killer == null ? "the storm" : killer.getName().getString();
		broadcast(server, Component.empty().append(who(victim)).append(" was eliminated by ").append(killer == null ? Component.literal(by) : who(killer)).withStyle(ChatFormatting.RED));
		int team = Teams.teamOfPlayer(victim.getUUID());
		if (Teams.teamMode() && teamAlive(team)) {
			LivingEntity mate = spectateTarget(server, victim);
			if (mate != null) {
				victim.setCamera(mate);
				watching.put(victim.getUUID(), mate);
			}
			title(victim, Component.literal("ELIMINATED").withStyle(ChatFormatting.RED),
				Component.literal("by " + by + " - your team is still in: they can reboot you (H: hub)"), 5, 60, 10);
		} else {
			int place = teamsAlive().size() + 1;
			if (Teams.teamMode()) {
				for (UUID m : Teams.playersOf(team)) {
					ServerPlayer q = server.getPlayerList().getPlayer(m);
					if (q != null && players.contains(m))
						title(q, Component.literal("#" + place).withStyle(ChatFormatting.GOLD), Component.literal("Your team was eliminated - press H to return to the hub"), 5, 60, 10);
				}
			} else {
				title(victim, Component.literal("#" + place).withStyle(ChatFormatting.GOLD), Component.literal("Eliminated by " + by + " - press H to return to the hub"), 5, 60, 10);
			}
		}
		MinebloxRoyale.LOG.info("BR: {} eliminated by {}", victim.getName().getString(), by);
		MeltyTest.pass("eliminations");
	}

	public void onBotDied(BotEntity bot, Entity killer, String byName) {
		if (state != State.LIVE) return;
		MinecraftServer server = bot.level().getServer();
		Knocked.forget(bot.getUUID());
		// Its card, if a teammate still stands (checked before the roster marks it out).
		Reboot.dropCardFor(bot, "bot:" + bot.getName().getString());
		roster.onEntityDied(bot);
		if (!(killer != null && Teams.sameTeam(killer, bot))) countKill(killer, byName);
		Net.sfxAll(server, "oof", bot.position());
		String by = byName != null ? byName : killer == null ? "the storm" : killer.getName().getString();
		broadcast(server, Component.empty().append(who(bot)).append(" was eliminated by ").append(killer == null ? Component.literal(by) : who(killer)).withStyle(ChatFormatting.GRAY));
		MinebloxRoyale.LOG.info("BR: bot {} eliminated by {}", bot.getName().getString(), by);
		MeltyTest.pass("eliminations");
	}

	/** Can this card's owner still come back (players: eliminated but still in the match; bots: out)? */
	boolean canComeBack(Reboot.Owner o) {
		if (state != State.LIVE) return false;
		if (o.bot()) return roster.isOut(o.name());
		UUID id = UUID.fromString(o.key());
		return players.contains(id) && !alivePlayers.contains(id) && server != null && server.getPlayerList().getPlayer(id) != null;
	}

	/** A reboot van brings this card's owner back: they drop in from above the van with the glider and nothing else. */
	boolean rebootOwner(MinecraftServer server, Reboot.Owner o, Reboot.Van van) {
		if (!canComeBack(o)) return false;
		ServerLevel level = server.overworld();
		double y = van.y + MinebloxRoyale.setting("rebootDropHeight");
		RandomSource rnd = level.getRandom();
		if (o.bot()) return roster.reboot(o.name(), van.x, y, van.z, rnd);
		UUID id = UUID.fromString(o.key());
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (p == null) return false;
		alivePlayers.add(id);
		p.setCamera(p);
		p.getInventory().clearContent();
		Combat.reset(p);
		p.removeAllEffects();
		p.setGameMode(GameType.SURVIVAL);
		p.teleportTo(level, van.x + rnd.nextInt(7) - 3, y, van.z + rnd.nextInt(7) - 3, Set.of(), p.getYRot(), 10F, true);
		dropping.add(id); // a fresh drop: skydive and glider again
		Net.sfxTo(p, "falling", p.position());
		title(p, Component.literal("REBOOTED").withStyle(ChatFormatting.AQUA), Component.literal("Back in - SPACE opens your glider"), 5, 40, 10);
		MinebloxRoyale.LOG.info("BR: {} rebooted", p.getName().getString());
		return true;
	}

	/** A bot finished rebooting at a van (BotEntity). */
	public void botReboot(BotEntity bot, Reboot.Van van) {
		Reboot.reboot(bot, van);
	}

	/** The teammate an eliminated player watches: the closest one still in the match (players first). */
	private LivingEntity spectateTarget(MinecraftServer server, ServerPlayer p) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (UUID id : alivePlayers) {
			ServerPlayer q = server.getPlayerList().getPlayer(id);
			if (q == null || q == p || !Teams.sameTeam(p, q)) continue;
			double d = q.distanceToSqr(p);
			if (d < bestD) {
				best = q;
				bestD = d;
			}
		}
		if (best != null) return best;
		for (BotEntity b : server.overworld().getEntities(BotEntity.TYPE, b -> b.isAlive() && Teams.sameTeam(b, p))) {
			double d = b.distanceToSqr(p);
			if (d < bestD) {
				best = b;
				bestD = d;
			}
		}
		return best;
	}

	/**
	 * Once a second: an eliminated player whose teammate they watched went out watches another one. (Minecraft drops
	 * the camera itself when the watched one goes; letting go with sneak means looking round freely, and is left
	 * alone.)
	 */
	private void tickSpectators(MinecraftServer server) {
		if (!Teams.teamMode()) return;
		for (UUID id : players) {
			if (alivePlayers.contains(id)) {
				watching.remove(id);
				continue;
			}
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null || !p.isSpectator()) continue;
			Entity cam = p.getCamera();
			if (cam != p && cam.isAlive() && !cam.isRemoved()) {
				watching.put(id, cam);
				continue;
			}
			Entity last = watching.remove(id);
			if (last == null || last.isAlive() && !last.isRemoved()) continue; // let go on purpose
			LivingEntity next = spectateTarget(server, p);
			if (next != null) {
				p.setCamera(next);
				watching.put(id, next);
			}
		}
	}

	/**
	 * A name for chat: real players are blue for everyone (chat name too, through the br_players team); each player's
	 * own copy of the game turns their own name purple (NameColors).
	 */
	public static MutableComponent who(Entity e) {
		var name = Component.literal(e.getName().getString());
		return e instanceof ServerPlayer ? name.withStyle(ChatFormatting.BLUE) : name;
	}

	/**
	 * Real players are on a team that makes their names blue (chat name too). Two such teams, so pushing can differ: in
	 * the hub nobody pushes anyone (creator: no shoving each other off the obby or the Sky Tower), in a match players
	 * push as normal. Minecraft's own team collision rule, so each player's game predicts it too (no rubber-banding).
	 */
	private static void setTeam(ServerPlayer player, boolean match) {
		var board = player.level().getServer().getScoreboard();
		String name = match ? "br_players" : "br_hub";
		var team = board.getPlayerTeam(name);
		if (team == null) team = board.addPlayerTeam(name);
		if (!team.getColor().equals(Optional.of(TeamColor.BLUE))) team.setColor(Optional.of(TeamColor.BLUE));
		Team.CollisionRule rule = match ? Team.CollisionRule.ALWAYS : Team.CollisionRule.NEVER;
		if (team.getCollisionRule() != rule) team.setCollisionRule(rule);
		if (board.getPlayersTeam(player.getScoreboardName()) != team) board.addPlayerToTeam(player.getScoreboardName(), team);
	}

	/** An elimination for the killer: a player, a real bot, or a simulated bot known only by name (byName). */
	private void countKill(Entity killer, String byName) {
		if (killer instanceof ServerPlayer sp) kills.merge(sp.getUUID(), 1, Integer::sum);
		else if (killer instanceof BotEntity b) roster.creditKill(b.getName().getString());
		else if (killer == null && byName != null) roster.creditKill(byName);
	}

	/** A player's eliminations this match (their teammates see them: SquadInfo). */
	int kills(UUID player) {
		return kills.getOrDefault(player, 0);
	}

	/**
	 * The match ends when no real player is left in it, or only one team (or player) is left. In a team mode a team
	 * counts as long as anyone in it is still in (knocked included; a knocked team with nobody standing is out within a
	 * tick).
	 */
	private void checkWinner(MinecraftServer server) {
		Set<Integer> alive = teamsAlive();
		// Test mode plays on to the last team even with every player out (a soak test of the bots to the very end).
		if (alivePlayers.isEmpty() && !(TEST && alive.size() > 1)) {
			int t = alive.size() == 1 ? alive.iterator().next() : -1;
			end(server, t);
		} else if (alive.size() <= 1) {
			end(server, alive.isEmpty() ? -1 : alive.iterator().next());
		}
	}

	private void end(MinecraftServer server, int winTeam) {
		state = State.ENDED;
		timer = Math.round(MinebloxRoyale.setting("endScreenSeconds") * 20);
		fireworksFor.clear();
		// Every real player of the winning team wins, including one eliminated earlier (Fortnite).
		Set<UUID> winners = new HashSet<>();
		if (winTeam >= 0) for (UUID id : players) if (Teams.teamOfPlayer(id) == winTeam) winners.add(id);
		List<String> names = new ArrayList<>();
		if (winTeam >= 0) {
			for (UUID id : Teams.playersOf(winTeam)) names.add(Teams.nameOf(id));
			for (String b : roster.names(winTeam)) if (names.isEmpty() || Teams.teamMode()) names.add(b);
		}
		winnerName = names.isEmpty() ? "" : names.size() == 1 ? names.get(0) : String.join(" & ", names);
		Knocked.clearAll();
		for (UUID id : players) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) Leaderboard.record(server, id, p.getName().getString(), winners.contains(id), kills.getOrDefault(id, 0));
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (winners.contains(p.getUUID())) {
				title(p, Component.literal("#1 VICTORY ROYALE").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					Component.literal(Teams.teamMode() ? "Your team is the last one standing!" : "You are the last one standing!"), 5, 100, 20);
				if (alivePlayers.contains(p.getUUID())) {
					fireworksFor.add(p.getUUID());
					Net.emoteAll(server, p, "victory_dance");
				}
			} else if (players.contains(p.getUUID())) {
				// A short title and the winner underneath (a whole team's names in the big title ran off the screen).
				title(p, Component.literal("MATCH OVER").withStyle(ChatFormatting.YELLOW), Component.literal(winnerLine(names)).withStyle(ChatFormatting.YELLOW), 5, 80, 20);
				p.sendSystemMessage(Component.literal("Back to the hub soon").withStyle(ChatFormatting.GRAY), true);
			}
		}
		if (!names.isEmpty())
			broadcast(server, Component.literal((names.size() > 1 ? "Winners: " : "Winner: ") + String.join(", ", names)).withStyle(ChatFormatting.GOLD));
		MinebloxRoyale.LOG.info("BR: match {} ({}) over, winner: {}", matchIndex, mode.name(), winnerName.isEmpty() ? "none (all players out)" : winnerName);
	}

	/**
	 * Under the "MATCH OVER" title: "Bacon_Bot won", or for a team "Bacon_Bot's squad won" (the full list goes to
	 * chat).
	 */
	private String winnerLine(List<String> names) {
		if (names.isEmpty()) return "Nobody won";
		if (names.size() == 1) return names.get(0) + " won";
		String team = switch (mode.teamSize()) {
			case 2 -> "duo";
			case 3 -> "trio";
			default -> "squad";
		};
		return names.get(0) + "'s " + team + " won";
	}

	private void backToHub(MinecraftServer server) {
		if (bus != null) bus.discard();
		bus = null;
		busLaunched = -1;
		fireworksFor.clear();
		roster.clear();
		ServerLevel level = server.overworld();
		for (BlockPos c : placedChests) Loot.clearChest(level, c);
		placedChests.clear();
		structureSpots.clear();
		CustomStructures.clearAll();
		Reboot.clearAll(level); // cards; the vans' blocks go back with the island below
		Builds.clearAll(level); // every built wall, floor and ramp goes
		IslandRestore.restoreAll(level); // harvested trees and walls come back (the old island is left as it was)
		IslandPregen.matchOver(server); // the next island (made during the match) goes on the map, and is finished at full speed
		Knocked.clearAll();
		Interact.clear();
		waypoints.clear();
		watching.clear();
		for (UUID id : players) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) sendToHub(p);
		}
		players.clear();
		alivePlayers.clear();
		dropping.clear();
		state = State.HUB;
		phase = -1;
		closedAt = 0;
		MinebloxRoyale.LOG.info("BR: everyone back in the hub");
		if (TEST) testStartIn = 20 * 20;
	}

	public void sendToHub(ServerPlayer p) {
		p.stopRiding(); // a rider snaps back to its vehicle after a teleport (a game closed on the bus reopens on it)
		p.setCamera(p);
		p.getInventory().clearContent();
		Combat.reset(p);
		p.removeAllEffects();
		p.setGameMode(GameType.ADVENTURE);
		setTeam(p, false); // nobody pushes anyone in the hub
		Vec3 spawn = HubBuilder.spawnPos();
		p.teleportTo(p.level().getServer().overworld(), spawn.x, spawn.y, spawn.z, Set.of(), 180F, 0F, true);
		Obby.resetProgress(p);
	}

	public void leaveToHub(ServerPlayer p) {
		if (state != State.HUB && !alivePlayers.contains(p.getUUID())) {
			players.remove(p.getUUID());
			waypoints.remove(p.getUUID());
			Reboot.forfeit(p.level().getServer(), p.getUUID().toString()); // gone to the hub: their card no longer counts
			sendToHub(p);
		}
	}

	// ---- tick ----

	public void tick(MinecraftServer server) {
		this.server = server;
		ServerLevel level = server.overworld();
		if (server.getTickCount() % 100 == 0) IslandRestore.save(server);
		if (server.getTickCount() % 20 == 0) {
			Leaderboard.tick(server);
			Parties.tick(server);
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.getFoodData().setFoodLevel(20);
			Combat.setupFighter(p);
		}
		switch (state) {
			case HUB -> {
				ModeDef m = portalMode(server);
				if (m != null) requestStart(server, "everyone is on the " + m.name() + " portal", m);
				if (IslandPregen.ready()) MeltyTest.pass("island-ready");
				if (testStartIn > 0 && !server.getPlayerList().getPlayers().isEmpty() && --testStartIn == 0) {
					requestStart(server, "test mode", Sheets.MODES_BY_ID.getOrDefault(TEST_MODE, TEST_MODE.equals("all") ? Sheets.MODES.get(matchIndex % Sheets.MODES.size()) : mode));
					if (state == State.HUB) testStartIn = 20; // the island isn't ready yet: try again in a second
				}
			}
			case COUNTDOWN -> {
				if (!TEST && !devStarted && portalMode(server) != mode) {
					state = State.HUB;
					broadcast(server, Component.literal("Countdown cancelled - everyone has to stand on the same portal.").withStyle(ChatFormatting.YELLOW));
					for (ServerPlayer p : server.getPlayerList().getPlayers()) title(p, Component.empty(), Component.empty(), 0, 1, 0);
					MinebloxRoyale.LOG.info("BR: countdown cancelled (someone left the portal or changed portals)");
				} else if (--timer <= 0) launchBus(server);
				else if (timer % 20 == 0) for (ServerPlayer p : server.getPlayerList().getPlayers())
					title(p, Component.literal("" + timer / 20).withStyle(ChatFormatting.LIGHT_PURPLE), Component.literal(mode.name() + " - Battle Bus boarding"), 0, 25, 0);
			}
			case LIVE -> {
				for (UUID id : alivePlayers) {
					ServerPlayer lp = server.getPlayerList().getPlayer(id);
					if (lp != null) Loadout.enforce(lp);
				}
				prepareArena(level);
				tickBus(server);
				tickDrops(server);
				tickStorm(level);
				roster.reclaim(); // bots Minecraft removed go back to the simulation before anything looks at them
				Knocked.tick(server);
				Interact.tick(server);
				if (level.getGameTime() % 600 == 0) MinebloxRoyale.LOG.info("BR: {} bots {}, {} players in", mode.name(), roster.census(), alivePlayers.size());
				if (level.getGameTime() % 20 == 0) {
					Reboot.secondTick(server);
					tickSpectators(server);
				}
				// Bots become real near ANY player in the world (eliminated, spectating or flying in creative too), so
				// the ones still in the match can always be found and watched.
				roster.tick(server, new ArrayList<>(server.getPlayerList().getPlayers()), new Vec3(cx, 0, cz), radius, stormDps(level.getGameTime()),
					safeSpot(), safeRadius(), true);
				if (state == State.LIVE) checkWinner(server);
			}
			case ENDED -> {
				if (timer % 8 == 0)
					for (UUID id : fireworksFor) {
						ServerPlayer w = server.getPlayerList().getPlayer(id);
						if (w == null) continue;
						RandomSource r = level.getRandom();
						level.sendParticles(ParticleTypes.FIREWORK, w.getX() + r.nextGaussian() * 3, w.getY() + 4 + r.nextDouble() * 4, w.getZ() + r.nextGaussian() * 3, 40, 0.1, 0.1, 0.1, 0.25);
					}
				if (--timer <= 0) backToHub(server);
			}
		}
		if (level.getGameTime() % 10 == 0) sendHud(server);
		if (Knocked.takeChanged() || level.getGameTime() % 40 == 0) {
			Net.Knocks knocks = new Net.Knocks(Knocked.ids(server));
			for (ServerPlayer p : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(p, knocks);
		}
		// Anyone in the hub (not in a match) who drops below it goes back to their obby checkpoint, or the spawn pad if
		// they have none. (Sending them to the spawn pad also wiped their checkpoint, before the obby's own fall check,
		// lower down, could act: creator, "the obby does not save checkpoints".)
		for (ServerPlayer p : server.getPlayerList().getPlayers())
			if (!alivePlayers.contains(p.getUUID()) && !p.isSpectator() && !p.isCreative() && p.getY() < HubBuilder.hubY() - 6) Obby.respawnAtCheckpoint(p);
	}

	/**
	 * The mode every player in the hub (not spectating) is standing on, or null: the match starts only when all of them
	 * stand on the same mode portal (creator: Solo, Duos, Trios and Squads portals).
	 */
	private static ModeDef portalMode(MinecraftServer server) {
		var list = server.getPlayerList().getPlayers().stream().filter(p -> !p.isSpectator()).toList();
		if (list.isEmpty()) return null;
		ModeDef m = HubBuilder.portalMode(list.get(0));
		if (m == null) return null;
		for (ServerPlayer p : list) if (HubBuilder.portalMode(p) != m) return null;
		return m;
	}

	/**
	 * Skydive and glider physics run on the player's own client (smooth); the server tracks landing and cancels fall
	 * damage.
	 */
	private void tickDrops(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (UUID id : new ArrayList<>(dropping)) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				dropping.remove(id);
				continue;
			}
			p.fallDistance = 0;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getBlockX(), p.getBlockZ());
			double above = p.getY() - ground;
			if ((p.onGround() || p.isInWater()) && above < 2.5) {
				dropping.remove(id);
				gliding.remove(id);
				MinebloxRoyale.LOG.info("BR: {} landed", p.getName().getString());
			}
			// The glider itself (open/close with Space, auto-open at gliderHeight) is client-side: DropControl.
		}
	}

	/**
	 * The hub's island line (creator: a countdown next to the %): "Island 45% - 1:20 left", "Island ready", or still
	 * searching.
	 */
	private static String islandProgress() {
		if (IslandPregen.ready()) return "Island ready";
		String left = IslandPregen.timeLeft();
		if (left.isEmpty() && IslandPregen.percent() == 0) return "Finding the next island...";
		return "Island " + IslandPregen.percent() + "%" + (left.isEmpty() ? "" : " - " + left);
	}

	private void sendHud(MinecraftServer server) {
		int alive = alivePlayers.size() + roster.alive();
		String label;
		int seconds;
		switch (state) {
			case COUNTDOWN -> { label = mode.name() + " - Bus boarding"; seconds = timer / 20; }
			case LIVE -> { label = shrinking ? "Storm shrinking" : "Storm eye shrinks in"; seconds = phaseTimer == Integer.MAX_VALUE ? 0 : phaseTimer / 20; }
			case ENDED -> { label = winnerName.isEmpty() ? "Match over" : winnerName + " won"; seconds = timer / 20; }
			default -> { label = islandProgress(); seconds = 0; }
		}
		// How many stand on each mode portal (modes sheet order), for the hub banner.
		int hubCount = 0, onPortal = 0;
		int[] pads = new int[Sheets.MODES.size()];
		for (ServerPlayer p : server.getPlayerList().getPlayers())
			if (!p.isSpectator() && !alivePlayers.contains(p.getUUID())) {
				hubCount++;
				ModeDef m = HubBuilder.portalMode(p);
				if (m != null) {
					onPortal++;
					pads[Sheets.MODES.indexOf(m)]++;
				}
			}
		StringBuilder padText = new StringBuilder();
		for (int i = 0; i < pads.length; i++) padText.append(i == 0 ? "" : ",").append(pads[i]);
		boolean live = state == State.LIVE || state == State.ENDED;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			boolean in = players.contains(p.getUUID()) && live;
			ServerPlayNetworking.send(p, new Net.Hud(state.name(), alive, Combat.shield(p), (float) cx, (float) cz, radius, (float) toX, (float) toZ, toR, seconds, label, in, kills.getOrDefault(p.getUUID(), 0),
				p.getVehicle() instanceof BattleBusEntity ? 1 : dropping.contains(p.getUUID()) ? 2 : 0, onPortal, hubCount, phase + 1, shrinking,
				live || state == State.COUNTDOWN ? mode.teamSize() : 1, padText.toString()));
			if (in && Teams.teamMode()) ServerPlayNetworking.send(p, SquadInfo.build(server, this, p));
		}
	}

	// ---- helpers ----

	static void title(ServerPlayer p, Component title, Component sub, int in, int stay, int out) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
		p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
		p.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	static void broadcast(MinecraftServer server, Component msg) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(msg);
	}

	private static Path indexFile(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("minebloxroyale_match.txt");
	}

	private static int readIndex(MinecraftServer server) {
		try {
			return Integer.parseInt(Files.readString(indexFile(server)).trim());
		} catch (IOException | NumberFormatException e) {
			return 0;
		}
	}

	private void writeIndex(MinecraftServer server) {
		try {
			Files.writeString(indexFile(server), Integer.toString(matchIndex));
		} catch (IOException e) {
			MinebloxRoyale.LOG.warn("BR: couldn't save match index", e);
		}
	}
}
