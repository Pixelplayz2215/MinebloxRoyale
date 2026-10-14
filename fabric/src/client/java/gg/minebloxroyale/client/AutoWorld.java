package gg.minebloxroyale.client;

import gg.minebloxroyale.Legacy;
import gg.minebloxroyale.MeltyTest;
import gg.minebloxroyale.MinebloxRoyale;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * public address, which goes to minebloxroyale/melty.log as "Melty join address: <address>".
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
			MinebloxRoyale.LOG.warn("BR: couldn't write melty.log", e);
		}
		MinebloxRoyale.LOG.info(line);
	}

	public static void tick(Minecraft mc) {
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
			// Joining a friend failed: try again a few times, 5 s apart, then stay on the message so it can be read
			// (a room joined by code goes back to your own world instead).
			if (joining != null && failedJoins >= MAX_JOIN_TRIES) {
				if (roomTarget != null) {
					roomTarget = null;
					homeAt = System.currentTimeMillis() + 4000;
				}
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
			mc.disconnectFromWorld(Component.literal("Joining room " + code));
		}
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

	/** Left a world: leaving a friend's room (or being dropped from it) goes back to your own world. */
	public static void onDisconnect() {
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
			if (roomTarget != null) MinebloxRoyale.LOG.info("BR: in a friend's room at {}", roomTarget);
			else if (joining != null && !announcedJoin) {
				announcedJoin = true;
				meltyLog("Melty joined: " + joining);
			}
			return;
		}
		lastDomain = null; // hosting again: e4mc gives a new address
		if (server.isPublished()) return;
		mc.execute(() -> {
			if (System.getenv("MINEBLOXROYALE_LAN_OFFLINE") != null) server.setUsesAuthentication(false);
			String portEnv = System.getenv("MINEBLOXROYALE_LAN_PORT");
			int port = portEnv != null && !portEnv.isBlank() ? Integer.parseInt(portEnv.trim()) : HttpUtil.getAvailablePort();
			boolean ok = server.publishServer(MinecraftServer.MultiplayerScope.LAN, false, port);
			MinebloxRoyale.LOG.info("BR: world open to friends on port {} ({})", port, ok ? "ok" : "failed");
			if (ok && System.getenv("MINEBLOXROYALE_LAN_OFFLINE") != null) meltyLog("Melty join address: 127.0.0.1:" + port);
		});
	}

	private static final Pattern E4MC = Pattern.compile("([a-z0-9-]+\\.(?:[a-z0-9-]+\\.)*e4mc\\.link)");
	private static final String E4MC_HOSTED = "text.e4mc_minecraft.domainAssigned";
	private static @Nullable String lastDomain;

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
		if (!m.find() || m.group(1).equals(lastDomain)) return;
		lastDomain = m.group(1);
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
