package gg.minebloxroyale.client;

import gg.minebloxroyale.Legacy;
import gg.minebloxroyale.MeltyTest;
import gg.minebloxroyale.MinebloxRoyale;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.jspecify.annotations.Nullable;

/**
 * Play opens straight into the Mineblox Royale world (created the first time), or joins a friend when Melty put their
 * address in minebloxroyale/melty.properties (join=...). A host's world opens to friends at once; e4mc gives it a
 * public address, which goes to minebloxroyale/melty.log as "Melty join address: <address>". (1.0.1.2) Without one,
 * this PC's own address and port go there instead, for Melty's relay (shareThroughMelty). (1.0.1.3) With Melty's port,
 * from the game's first second (LogWatch.shareEarly), and a friend who can't get in yet waits in their own world.
 */
public final class AutoWorld {
	public static final String WORLD = "Mineblox Royale";
	private static boolean attempted;
	private static @Nullable String joining;
	private static boolean announcedJoin;
	private static final int MAX_JOIN_TRIES = 3;
	private static int failedJoins;
	private static long retryAt;
	/**
	 * A friend's room to join (/rc <code>), and whether we're in a friend's world now (by code or by Melty's join link;
	 * leaving it goes back to your own world). homeOnly: we've left a friend's world, so don't follow Melty's join link
	 * straight back into it.
	 */
	private static @Nullable String roomTarget;
	private static boolean inRoom, leavingForRoom, homeOnly;
	/** When to go back to your own world (after leaving or failing to reach a room; 0 = not going). */
	private static long homeAt;

	private AutoWorld() {
	}

	static Path dir() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("minebloxroyale");
	}

	static @Nullable String joinAddress() {
		if (roomTarget != null) return roomTarget;
		if (homeOnly) return null;
		Path file = dir().resolve("melty.properties");
		try {
			Files.createDirectories(file.getParent());
			if (!Files.exists(file)) Files.writeString(file, "# Mineblox Royale: Melty puts a friend's address after join= (empty = host your own game)\njoin=\n");
			Properties p = new Properties();
			try (var in = Files.newBufferedReader(file)) {
				p.load(in);
			}
			String j = p.getProperty("join", "").trim().replaceFirst("^[A-Za-z]+://", "").replaceAll("/+$", "");
			return j.isEmpty() ? null : j;
		} catch (IOException e) {
			return null;
		}
	}

	public static void meltyLog(String line) {
		try {
			Files.createDirectories(dir());
			Files.writeString(dir().resolve("melty.log"), line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			MinebloxRoyale.LOG.info("BR: couldn't write melty.log ({})", e.getMessage());
		}
		MinebloxRoyale.LOG.info(line);
	}

	/** melty.log from scratch, with this one line (no join address in it). */
	private static void freshMeltyLog(String line) {
		try {
			Files.createDirectories(dir());
			Files.writeString(dir().resolve("melty.log"), line + System.lineSeparator(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MinebloxRoyale.LOG.info("BR: couldn't write melty.log ({})", e.getMessage());
		}
		MinebloxRoyale.LOG.info(line);
	}

	/**
	 * (1.0.1.1) e4mc couldn't open this world to friends (LogWatch: no internet yet, or a network that blocks its relay).
	 * The game plays on; melty.log says why, and e4mc is asked to try again after 20 s, then 40, 80, 160, and every 5
	 * minutes after that, while the world is open. Players whose relay failed at start used to stay unreachable all
	 * session (friends sent by Melty: "Waiting for the host's game to open").
	 * (1.0.1.2) Meanwhile friends come in through Melty (shareThroughMelty): on a network that blocks e4mc's relay for
	 * good, retrying alone never got them in.
	 */
	static void onE4mcFailed(String why, boolean retry) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.getSingleplayerServer() == null) {
				MinebloxRoyale.LOG.info("BR: the e4mc relay couldn't be reached ({})", why);
				return;
			}
			lastDomain = null; // a new address comes with the next session
			long wait = Math.min(300, 20L << Math.min(relayTries, 4));
			relayRetryAt = retry ? System.currentTimeMillis() + wait * 1000 : 0;
			meltyLog("The e4mc relay couldn't be reached (" + why + "). Playing works as normal, and friends join through Melty meanwhile"
				+ (retry ? "; asking e4mc again in " + wait + " s." : "."));
			shareThroughMelty(mc, "e4mc's relay couldn't be reached");
		});
	}

	/**
	 * (1.0.1.2) Hosting without an e4mc address (its relay unreachable, or no answer LAN_WAIT_MS after the world opened):
	 * melty.log gets this PC's own address and the port the world is open on. Melty carries friends to that port over
	 * its own relay (which also gets through networks that only let TCP out), and friends on the same home network
	 * connect straight to it. Joiners Melty sent used to wait for an e4mc address that never came ("Waiting for the
	 * host's game to open", 2026-10-16). Once e4mc gives an address, that line follows and is the one Melty uses.
	 */
	private static void shareThroughMelty(Minecraft mc, String why) {
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null || !server.isPublished() || lanPort == 0 || lastDomain != null || lanShared != null) return;
		lanAt = 0;
		lanShared = lanAddress(lanPort);
		MinebloxRoyale.LOG.info("BR: friends join through Melty at this PC's address ({})", why);
		meltyLog("Melty join address: " + lanShared);
	}

	static String lanAddress(int port) {
		return LogWatch.lanAddress(port);
	}

	/** Asks e4mc for a new relay session, as its own "/e4mc restart" does. Server's world open to friends only. */
	private static void retryRelay(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null || !server.isPublished()) {
			relayRetryAt = 0;
			return;
		}
		relayTries++;
		try {
			ClassLoader loader = AutoWorld.class.getClassLoader();
			Field current = Class.forName("link.e4mc.E4mcClient", true, loader).getField("session");
			Object session = current.get(null);
			if (session == null) return;
			Class<?> type = session.getClass();
			String state = String.valueOf(type.getField("state").get(session));
			if (!state.equals("UNHEALTHY") && !state.equals("STARTING")) return; // working, or stopped on purpose
			Field handler = type.getDeclaredField("handler"), group = type.getDeclaredField("group");
			handler.setAccessible(true);
			group.setAccessible(true);
			for (Constructor<?> c : type.getConstructors()) {
				if (c.getParameterCount() != 2) continue;
				Object fresh = c.newInstance(handler.get(session), group.get(session));
				current.set(null, fresh);
				type.getMethod("startAsync").invoke(fresh);
				MinebloxRoyale.LOG.info("BR: asking the e4mc relay again (try {})", relayTries + 1);
				return;
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			MinebloxRoyale.LOG.info("BR: couldn't ask e4mc to try again ({}); friends can join after the game is restarted", e.getClass().getSimpleName());
		}
	}

	public static void tick(Minecraft mc) {
		if (relayRetryAt != 0 && System.currentTimeMillis() >= relayRetryAt) {
			relayRetryAt = 0;
			retryRelay(mc);
		}
		if (lanAt != 0 && System.currentTimeMillis() >= lanAt) shareThroughMelty(mc, "no e4mc address " + LAN_WAIT_MS / 1000 + " s after the world opened");
		// Back to your own world after leaving (or failing to reach) a friend's room; a "disconnected" message stays a
		// moment first.
		if (homeAt != 0) {
			if (mc.level != null) homeAt = 0;
			else if (!(mc.gui.screen() instanceof DisconnectedScreen) || System.currentTimeMillis() >= homeAt) {
				homeAt = 0;
				attempted = false;
				joining = null;
				failedJoins = 0;
				retryAt = 0;
				mc.gui.setScreen(new TitleScreen());
				return;
			} else return;
		}
		if (mc.gui.screen() instanceof DisconnectedScreen d && mc.level == null && attempted) {
			// Joining a friend failed: try again a few times, 5 s apart, then back to your own world once the message has
			// been up a moment. (1.0.1.2) Melty's join link too, which used to leave the player on the message for good;
			// the chat says what happened once they're in their own world.
			if (joining != null && failedJoins >= MAX_JOIN_TRIES) {
				if (roomTarget == null || roomTarget.equals(missedFriend)) {
					homeOnly = true;
					missedFriend = joining;
					tellMissed = true;
				}
				roomTarget = null;
				homeAt = System.currentTimeMillis() + 6000;
				return;
			}
			if (retryAt == 0) {
				failedJoins += joining != null ? 1 : 0;
				retryAt = System.currentTimeMillis() + 5000;
				MinebloxRoyale.LOG.info("BR: disconnected ({}); {}", d.getNarrationMessage().getString(),
					joining != null && failedJoins >= MAX_JOIN_TRIES ? "giving up after " + failedJoins + " tries" : "trying again in 5 s");
				return;
			}
			if (System.currentTimeMillis() < retryAt) return;
			retryAt = 0;
			attempted = false;
			joining = null;
			mc.gui.setScreen(new TitleScreen());
			return;
		}
		if (mc.level != null) failedJoins = 0;
		if (attempted || mc.level != null || mc.gui.overlay() != null) return;
		Screen screen = mc.gui.screen();
		boolean onboarding = screen != null && screen.getClass().getName().contains("Onboarding");
		// Minecraft's accessibility welcome ("press Enter to enable the narrator") is skipped to open the world straight
		// away; mark it seen, or it comes back on every start (creator). OnboardingSkipMixin normally stops it showing at
		// all; this is in case it shows anyway.
		if (onboarding && mc.options.onboardAccessibility) mc.options.onboardingAccessibilityFinished();
		// The world opens from the main menu: Minecraft's title screen, nothing on screen yet, or a launcher's own main
		// menu (Lunar Client puts its own in place of the title screen; waiting for Minecraft's left a black screen).
		boolean menu = screen == null || screen instanceof TitleScreen || onboarding || !screen.getClass().getName().startsWith("net.minecraft.");
		if (!menu) {
			if (roomTarget != null) mc.gui.setScreen(new TitleScreen());
			return;
		}
		if (screen != null && !(screen instanceof TitleScreen) && !onboarding) MinebloxRoyale.LOG.info("BR: opening from another main menu ({})", screen.getClass().getName());
		TitleScreen title = screen instanceof TitleScreen t ? t : new TitleScreen();
		attempted = true;
		String join = joinAddress();
		if (join != null) {
			joining = join;
			announcedJoin = false;
			MinebloxRoyale.LOG.info("BR: joining {}", join);
			ConnectScreen.startConnecting(title, mc, ServerAddress.parseString(join), new ServerData("Mineblox Royale", join, ServerData.Type.OTHER), false, null);
			return;
		}
		if (mc.getLevelSource().levelExists(WORLD)) {
			MinebloxRoyale.LOG.info("BR: opening the Mineblox Royale world");
			mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.gui.setScreen(title));
			return;
		}
		if (mc.getLevelSource().levelExists(Legacy.OLD_WORLD)) {
			// An older build's world that couldn't be moved to the new name: keep playing in it (room code,
			// leaderboard).
			MinebloxRoyale.LOG.info("BR: opening the world from before the rename");
			mc.createWorldOpenFlows().openWorld(Legacy.OLD_WORLD, () -> mc.gui.setScreen(title));
			return;
		}
		MinebloxRoyale.LOG.info("BR: creating the Mineblox Royale world");
		// Cheats off (creator: no OP commands or creative; CommandsLockMixin also refuses every command in older
		// worlds).
		LevelSettings settings = new LevelSettings(WORLD, GameType.ADVENTURE, new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), false, WorldDataConfiguration.DEFAULT);
		mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(WorldOptions.randomSeed(), true, false),
			registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(), title);
	}

	/**
	 * /rc <code>: leave this world (your own room or a friend's) and join the room at address. Your own world is saved
	 * and closes, so anyone in it goes back to theirs.
	 */
	public static void joinRoom(String address, String code) {
		Minecraft mc = Minecraft.getInstance();
		roomTarget = address;
		attempted = false;
		failedJoins = 0;
		retryAt = 0;
		homeAt = 0;
		if (mc.level != null) {
			leavingForRoom = true;
			mc.disconnectFromWorld(Component.literal("Joining " + (code.matches("[0-9]+") ? "room " + code : code)));
		}
	}

	/**
	 * (1.0.1.3) /rejoin, or [Join them] in the chat: try the friend Melty sent us to again (their world may not have been
	 * open yet). Your own world is saved and closes; if theirs still can't be reached, you're back in yours. False when
	 * there's no such friend.
	 */
	public static boolean rejoinFriend() {
		String friend = missedFriend;
		if (friend == null) return false;
		MinebloxRoyale.LOG.info("BR: trying the friend Melty sent us to again ({})", friend);
		joinRoom(friend, "your friend's game");
		return true;
	}

	/**
	 * /leave: out of a friend's room and back to your own world (your own world is saved, theirs carries on).
	 * False when you're already in your own world.
	 */
	public static boolean leaveRoom() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.getSingleplayerServer() != null) return false;
		MinebloxRoyale.LOG.info("BR: leaving the room, back to our own world");
		inRoom = false;
		roomTarget = null;
		homeOnly = true;
		mc.disconnectFromWorld(Component.literal("Left the room"));
		homeAt = System.currentTimeMillis();
		return true;
	}

	/**
	 * Left a world: leaving a friend's room (or being dropped from it) goes back to your own world. (1.0.1.2) Leaving
	 * your own world (back to the title screen, or into a friend's room by code) closes it to friends, and its e4mc
	 * address goes with it: melty.log starts again without one, so Melty stops sending friends to it. Opening a world
	 * again writes its new address.
	 */
	public static void onDisconnect() {
		if (hosting) {
			hosting = false;
			lastDomain = lanShared = null;
			lanPort = 0;
			lanAt = relayRetryAt = 0;
			freshMeltyLog("Mineblox Royale: the world closed; friends can join once it's open again");
		}
		if (leavingForRoom) {
			leavingForRoom = false;
			return;
		}
		if (inRoom) {
			inRoom = false;
			roomTarget = null;
			homeOnly = true;
			homeAt = System.currentTimeMillis() + 4000;
		}
	}

	/** In a world: host -> open to friends; guest -> report that we got in. */
	public static void onJoinWorld(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		inRoom = server == null;
		leavingForRoom = false;
		if (server == null) {
			if (joining != null && joining.equals(missedFriend)) {
				missedFriend = null; // in at last (/rejoin)
				meltyLog("Melty joined: " + joining);
			}
			if (roomTarget != null) MinebloxRoyale.LOG.info("BR: in a friend's room at {}", roomTarget);
			else if (joining != null && !announcedJoin) {
				announcedJoin = true;
				meltyLog("Melty joined: " + joining);
			}
			return;
		}
		if (tellMissed) {
			tellMissed = false;
			// (1.0.1.3) Most often the friend's world isn't open yet (Melty now starts us while their game still loads)
			Component again = Component.literal("[Join them]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand("/rejoin")).withHoverEvent(new HoverEvent.ShowText(Component.literal("Try your friend's game again"))));
			mc.execute(() -> {
				if (mc.player != null) mc.player.sendSystemMessage(Component.literal("Your friend's game isn't open yet (or couldn't be reached), so you're in your own world. ")
					.withStyle(ChatFormatting.YELLOW).append(again)
					.append(Component.literal(" once they're in, or type /rejoin.").withStyle(ChatFormatting.YELLOW)));
			});
		}
		hosting = true;
		lastDomain = lanShared = null; // hosting again: e4mc gives a new address
		relayTries = 0;
		relayRetryAt = 0;
		if (server.isPublished()) return;
		mc.execute(() -> {
			if (System.getenv("MINEBLOXROYALE_LAN_OFFLINE") != null) server.setUsesAuthentication(false);
			String portEnv = System.getenv("MINEBLOXROYALE_LAN_PORT");
			int port = portEnv != null && !portEnv.isBlank() ? Integer.parseInt(portEnv.trim()) : HttpUtil.getAvailablePort();
			boolean ok = server.publishServer(MinecraftServer.MultiplayerScope.LAN, false, port);
			MinebloxRoyale.LOG.info("BR: world open to friends on port {} ({})", port, ok ? "ok" : "failed");
			if (!ok) return;
			lanPort = port;
			if (System.getenv("MINEBLOXROYALE_LAN_OFFLINE") != null) {
				lanShared = "127.0.0.1:" + port;
				meltyLog("Melty join address: " + lanShared);
			} else if (lastDomain == null) {
				String early = LogWatch.takeEarly();
				if (early != null && early.equals(lanAddress(port))) lanShared = early; // in melty.log since the game started
				else if (portEnv != null && !portEnv.isBlank()) shareThroughMelty(mc, "Melty's port: friends can come in now");
				else lanAt = System.currentTimeMillis() + LAN_WAIT_MS;
			}
		});
	}

	private static final Pattern E4MC = Pattern.compile("([a-z0-9-]+\\.(?:[a-z0-9-]+\\.)*e4mc\\.link)");
	private static final String E4MC_HOSTED = "text.e4mc_minecraft.domainAssigned";
	private static @Nullable String lastDomain;
	/** When to ask e4mc's relay again after it failed (0 = not waiting), and how many times it has been asked. */
	private static long relayRetryAt;
	private static int relayTries;
	/**
	 * (1.0.1.2) Our own world is open (hosting), the port it's open to friends on, when to share this PC's address with
	 * Melty if e4mc hasn't given one by then (0 = not waiting), and the address shared (shareThroughMelty).
	 */
	private static boolean hosting;
	private static int lanPort;
	private static long lanAt;
	private static @Nullable String lanShared;
	private static final long LAN_WAIT_MS = 20_000;
	/**
	 * (1.0.1.2) Melty's join link didn't get us in, so we went to our own world: say so in the chat there (tellMissed).
	 * (1.0.1.3) The friend's address, kept for /rejoin until we get in.
	 */
	private static @Nullable String missedFriend;
	private static boolean tellMissed;

	/**
	 * A system line in the chat box. e4mc announces this world's public address there ("Local game hosted on domain
	 * [...]"); pass it on to Melty and post it under this world's room code. Only e4mc's own line counts (by its
	 * translation key), and only while hosting, so nobody can fake it by typing the same words.
	 */
	public static void onSystemChat(Component line) {
		if (Minecraft.getInstance().getSingleplayerServer() == null || !mentions(line, E4MC_HOSTED)) return;
		StringBuilder text = new StringBuilder(line.getString());
		copyValues(line, text); // e4mc can hide the domain behind "click to copy"
		Matcher m = E4MC.matcher(text);
		if (m.find()) hosted(m.group(1));
	}

	/**
	 * e4mc's own log line with this world's public address (LogWatch), from e4mc's network thread: the same as its chat
	 * line, which can come later or not at all.
	 */
	static void onE4mcDomain(String domain) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) return;
		mc.execute(() -> {
			if (mc.getSingleplayerServer() != null) hosted(domain);
		});
	}

	private static void hosted(String domain) {
		if (domain.equals(lastDomain)) return;
		lastDomain = domain;
		lanShared = null; // e4mc's address from here on (if it fails again, this PC's address is written again)
		lanAt = 0;
		relayTries = 0;
		relayRetryAt = 0;
		meltyLog("Melty join address: " + lastDomain);
		MeltyTest.pass("friends-can-join");
		RoomCodes.hosting(lastDomain);
	}

	private static boolean mentions(Component c, String key) {
		if (c.getContents() instanceof TranslatableContents t) {
			if (t.getKey().equals(key)) return true;
			for (Object arg : t.getArgs()) if (arg instanceof Component a && mentions(a, key)) return true;
		}
		for (Component s : c.getSiblings()) if (mentions(s, key)) return true;
		return false;
	}

	private static void copyValues(Component c, StringBuilder out) {
		if (c.getStyle().getClickEvent() instanceof ClickEvent.CopyToClipboard copy) out.append(' ').append(copy.value());
		if (c.getContents() instanceof TranslatableContents t)
			for (Object arg : t.getArgs()) if (arg instanceof Component a) copyValues(a, out);
		for (Component s : c.getSiblings()) copyValues(s, out);
	}
}
