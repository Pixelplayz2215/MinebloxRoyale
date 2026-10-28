package gg.minebloxroyale.client;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/**
 * Runs before Minecraft starts (no game classes here).
 * <ul>
 * <li>melty.log starts fresh every launch, so Melty never hands friends an address from an earlier session, and the
 * file is there from the first second (a host still loading shows as starting, not as missing).</li>
 * <li>e4mc's own "Domain assigned: ..." log line is passed to AutoWorld, so the join address reaches melty.log the
 * moment the relay gives it, chat box or not.</li>
 * <li>Minecraft's checks of the Microsoft sign-in (player properties, Realms) log an ERROR with a stack trace when the
 * account is offline or its sign-in has run out (Status: 401). Nothing is wrong with the game then, so each kind becomes
 * one plain line, once. (1.0.0.3) The line says what it means without naming the exception: Melty read 1.0.0.2's lines,
 * which quoted the exceptions, as the mod's code failing to load ("Errors loading its code").</li>
 * <li>(1.0.0.3) When e4mc turns away a direct connection (Dialtone) that didn't get through its security check, Netty
 * logs it as an unhandled exception with a stack trace. The world stays open to friends, so that's one plain line too.</li>
 * <li>(1.0.0.4) While the game's window is minimized, Minecraft can't draw, and it logs a warning with the whole
 * exception ("Cannot acquire minimized window") for it. Players minimize it while their first island is made, and
 * Melty counted those warnings as the mod's code failing to load: one plain line instead.</li>
 * <li>(1.0.0.4) On a PC whose Windows performance counters are switched off, Minecraft's system report (and oshi, the
 * library it reads them with) logs warnings and errors with stack traces at every start. Only Minecraft's own reports
 * miss those details: one plain line instead.</li>
 * <li>(1.0.1.1) The minimized window is recognised by the exception's own words wherever Minecraft logs it, not only
 * after the two messages it used in 26.3's first builds.</li>
 * <li>(1.0.1.1) When e4mc can't reach its relay (no internet yet, a network that blocks it), it logs an ERROR with a
 * stack trace, and its start-up thread dies with a second one. Melty counted those as the mod's code failing to load
 * (2026-10-16, 1.0.1). The game plays on; only friends can't join yet. One plain line instead, and AutoWorld asks
 * e4mc to try again a little later.</li>
 * <li>(1.0.1.3) A game Melty started to host (it gives every Play a free port, MINEBLOXROYALE_LAN_PORT) puts its address
 * in melty.log from the first second, not once its world is open: see shareEarly.</li>
 * <li>(1.0.1.4) How the last game ended (LastRun): if it closed by itself, what crashed goes in this log and melty.log.</li>
 * </ul>
 * Everything else is logged as it is.
 */
public final class LogWatch extends AbstractFilter implements PreLaunchEntrypoint {
	private static final Logger LOG = LogManager.getLogger("MinebloxRoyale");
	private static final Pattern DOMAIN = Pattern.compile("Domain assigned: ([a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.e4mc\\.link)");
	private static final String ACCOUNT = "BR: Microsoft account settings not loaded ({}). Playing works as normal; joining friends needs a signed-in Minecraft account.";
	private static final String REALMS = "BR: Realms is off for this sign-in ({}). Mineblox Royale doesn't use Realms.";
	/** Minecraft's sign-in checks at start: the start of their message, and the line said instead (once each). */
	private static final String[][] SIGN_IN = {
		{"Failed to fetch user properties", ACCOUNT},
		{"Failed to fetch Realms feature flags", REALMS},
		{"Could not authorize you against Realms server", REALMS},
		{"Couldn't connect to realms", REALMS}};
	/** Netty's words for an exception nothing on the connection handled (e4mc's Dialtone server, see above). */
	private static final String UNHANDLED = "An exceptionCaught() event was fired, and it reached at the tail of the pipeline";
	/** The words of the exception Minecraft logs when it can't draw a frame because the window is minimized. */
	private static final String MINIMIZED = "Cannot acquire minimized window";
	/** e4mc's words when its relay session fails, and the name of the thread that starts it. */
	private static final String E4MC_ERROR = "error in e4mc", E4MC_THREAD = "e4mc_minecraft-init";
	/** When e4mc last failed (one failure logs twice: its own error, then its thread dying). */
	private static volatile long e4mcFailedAt;
	/** Minecraft's system report on a PC whose Windows performance counters are off (oshi reads them for it). */
	private static final String SYSTEM_INFO = "Failed to get system info for";
	private static final String COUNTERS = "BR: Windows didn't give Minecraft some system details (its performance counters are off on this PC). "
		+ "Only Minecraft's own reports miss them; the game is fine.";
	private static final Set<String> told = ConcurrentHashMap.newKeySet();

	public LogWatch() {
		super(Filter.Result.NEUTRAL, Filter.Result.NEUTRAL);
	}

	/**
	 * (1.2.0.1) The ffmpeg an older build brought (minebloxroyale/ffmpeg): no longer used (Roblox sounds decode through
	 * AudioDecode), and Windows' Smart App Control blocks it as unsigned, so it goes. Never in the way of starting.
	 */
	private static void dropOldFfmpeg(Path folder) {
		if (!Files.isDirectory(folder)) return;
		try (var files = Files.list(folder)) {
			for (Path f : files.toList()) Files.deleteIfExists(f);
			Files.deleteIfExists(folder);
			LOG.info("BR: the old bundled ffmpeg is gone from the game folder (Roblox sounds no longer need it)");
		} catch (IOException | RuntimeException e) {
			LOG.info("BR: couldn't remove the old bundled ffmpeg ({}); it isn't used any more", e.toString());
		}
	}

	static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve("minebloxroyale");
	}

	@Override
	public void onPreLaunch() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
		OldInstall.moveTo(FabricLoader.getInstance().getGameDir());
		E4mcNatives.use(dir().resolve("e4mc"));
		dropOldFfmpeg(dir().resolve("ffmpeg"));
		LastRun.check(FabricLoader.getInstance().getGameDir(), dir()); // (1.0.1.4: how the last game ended)
		try {
			Files.createDirectories(dir());
			String last = LastRun.cause();
			Files.writeString(dir().resolve("melty.log"), "Mineblox Royale started " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
				+ "; friends can join once the world is open" + System.lineSeparator() + (last == null ? "" : "Mineblox Royale: " + last + System.lineSeparator()), StandardCharsets.UTF_8);
			shareEarly();
		} catch (IOException e) {
			LOG.warn("BR: couldn't start melty.log", e);
		}
		try {
			LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
			ctx.getConfiguration().getRootLogger().addFilter(this);
			ctx.updateLoggers();
		} catch (RuntimeException | LinkageError e) { // another logging setup: everything still works, just noisier
			LOG.info("BR: log watch not available ({})", e.toString());
		}
	}

	/** (1.0.1.3) The address shareEarly put in melty.log, until the world first opens on it (AutoWorld.takeEarly). */
	private static volatile String early;

	/**
	 * (1.0.1.3) Melty only starts a friend's game once the host's melty.log has an address. Friends who pressed Join
	 * while the host's game was still loading got Melty's "Waiting for the host's game to open" and no game at all
	 * (players' Melty, 1.0.0 to 1.0.1.1; creator: "add a fallback so where if it's waiting on host then it auto joins own
	 * private world"). With Melty's port, a game that isn't joining anyone knows where its world will open before it
	 * loads, so this PC's address and that port go in at once: the friend's game starts, tries the host a few times, and
	 * if the world isn't open yet puts them in their own world with a button to try again (AutoWorld). e4mc's address
	 * follows once the world is open, and Melty uses the newest.
	 */
	private static void shareEarly() {
		String port = System.getenv("MINEBLOXROYALE_LAN_PORT");
		if (port == null || !port.trim().matches("[0-9]{1,5}") || joining()) return;
		String address = lanAddress(Integer.parseInt(port.trim()));
		try {
			Files.writeString(dir().resolve("melty.log"), "Melty join address: " + address + System.lineSeparator(), StandardCharsets.UTF_8,
				java.nio.file.StandardOpenOption.APPEND);
			early = address;
			LOG.info("BR: friends can press Join from now on ({}); until the world is open they wait in their own", address);
		} catch (IOException e) {
			LOG.info("BR: couldn't write melty.log ({})", e.getMessage());
		}
	}

	/** The address shareEarly wrote, once: the world opening on it (later openings write their own). */
	static String takeEarly() {
		String a = early;
		early = null;
		return a;
	}

	/** Melty put a friend's address in melty.properties (join=...): this game joins them rather than hosting. */
	private static boolean joining() {
		Path file = dir().resolve("melty.properties");
		if (!Files.exists(file)) return false;
		Properties p = new Properties();
		try (var in = Files.newBufferedReader(file)) {
			p.load(in);
		} catch (IOException | RuntimeException e) {
			return false;
		}
		return !p.getProperty("join", "").isBlank();
	}

	/**
	 * This PC's address on its own network (the one its internet goes out through), with the port; 127.0.0.1 when it
	 * has none (Melty's relay reaches the world there too). Connecting a UDP socket only picks the route: nothing is sent.
	 */
	static String lanAddress(int port) {
		String ip = "127.0.0.1";
		try (DatagramSocket s = new DatagramSocket()) {
			s.connect(InetAddress.getByAddress(new byte[] { (byte) 192, 0, 2, 1 }), 9); // TEST-NET-1: any outside address
			InetAddress local = s.getLocalAddress();
			if (local instanceof Inet4Address && !local.isAnyLocalAddress() && !local.isLoopbackAddress()) ip = local.getHostAddress();
		} catch (IOException | RuntimeException e) { // no network at all
		}
		return ip + ":" + port;
	}

	@Override
	public Filter.Result filter(LogEvent event) {
		try {
			String msg = event.getMessage() == null ? null : event.getMessage().getFormattedMessage();
			if (msg == null) return Filter.Result.NEUTRAL;
			if (msg.startsWith("Domain assigned: ")) {
				Matcher m = DOMAIN.matcher(msg);
				if (m.find()) AutoWorld.onE4mcDomain(m.group(1));
				return Filter.Result.NEUTRAL;
			}
			for (String[] s : SIGN_IN) {
				if (!msg.startsWith(s[0])) continue;
				if (told.add(s[1])) LOG.info(s[1], signIn(event.getThrown(), msg));
				return Filter.Result.DENY;
			}
			Throwable thrown = event.getThrown();
			if ((thrown != null || msg.contains(MINIMIZED)) && everything(thrown, msg).contains(MINIMIZED)) {
				if (told.add(MINIMIZED)) LOG.info("BR: the game window was minimized, so Minecraft drew nothing until it was back. Nothing is wrong.");
				return Filter.Result.DENY;
			}
			if (msg.equals(E4MC_ERROR) || msg.startsWith("Uncaught exception in thread") && (msg.contains(E4MC_THREAD) || fromE4mc(thrown))) {
				e4mcFailed(thrown);
				return Filter.Result.DENY;
			}
			String logger = event.getLoggerName();
			if (logger != null && logger.startsWith("oshi.") && event.getLevel().isMoreSpecificThan(Level.WARN)
				|| msg.startsWith(SYSTEM_INFO) && everything(event.getThrown(), msg).contains("oshi.")) {
				if (told.add(COUNTERS)) LOG.info(COUNTERS);
				return Filter.Result.DENY;
			}
			if (msg.startsWith(UNHANDLED) && fromE4mc(event.getThrown())) {
				String why = everything(event.getThrown(), "").contains("authentication") ? "it didn't pass the security check" : "it was cut off";
				if (told.add("e4mc " + why)) LOG.info("BR: e4mc turned away a direct connection ({}). The world stays open to friends.", why);
				return Filter.Result.DENY;
			}
		} catch (RuntimeException e) { // never in the way of the log itself
		}
		return Filter.Result.NEUTRAL;
	}

	/**
	 * e4mc couldn't open the world to friends: why, in a few words (never the exception itself, see above), to AutoWorld,
	 * which writes it to melty.log and asks e4mc to try again later. Its own library failing to load can't be tried
	 * again (e4mc keeps that failure until the game restarts).
	 */
	private static void e4mcFailed(Throwable t) {
		long now = System.currentTimeMillis();
		if (now - e4mcFailedAt < 3000) return; // the same failure's second line
		e4mcFailedAt = now;
		String all = everything(t, "");
		boolean library = all.contains("UnsatisfiedLinkError") || all.contains("native library");
		String why = library ? "its relay library couldn't load"
			: all.contains("UnresolvedAddress") || all.contains("UnknownHost") || all.contains("ConnectException") || all.contains("NoRouteToHost")
				|| all.contains("imed out") || all.contains("Timeout") ? "no internet, or this network blocks the relay"
			: "the relay didn't answer";
		try {
			AutoWorld.onE4mcFailed(why, !library);
		} catch (RuntimeException | LinkageError e) { // the game isn't up yet
			LOG.info("BR: friends can't join yet: the e4mc relay couldn't be reached ({}). Playing works as normal.", why);
		}
	}

	/** Why the sign-in check came back empty, in a few words (never the exception itself, see above). */
	private static String signIn(Throwable t, String msg) {
		String all = everything(t, msg);
		if (all.contains("SignedJWT")) return "offline account";
		if (all.contains("401")) return "signed out, 401";
		if (all.contains("UnknownHost") || all.contains("Timeout") || all.contains("ConnectException") || all.contains("NoRouteToHost"))
			return "Microsoft not reachable";
		return "no answer from Microsoft";
	}

	/** The message and each cause's class and message, as one string to look through. */
	private static String everything(Throwable t, String msg) {
		StringBuilder all = new StringBuilder(msg);
		int depth = 0;
		for (Throwable c = t; c != null && depth++ < 10; c = c.getCause()) all.append(' ').append(c.getClass().getName()).append(' ').append(c.getMessage());
		return all.toString();
	}

	/** Thrown by e4mc (its exception, or one passed on from inside its code). */
	private static boolean fromE4mc(Throwable t) {
		int depth = 0;
		for (Throwable c = t; c != null && depth++ < 10; c = c.getCause()) {
			if (c.getClass().getName().startsWith("link.e4mc.")) return true;
			for (StackTraceElement f : c.getStackTrace())
				if (f.getClassName().startsWith("link.e4mc.")) return true;
		}
		return false;
	}
}
