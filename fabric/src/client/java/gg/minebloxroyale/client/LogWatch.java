package gg.minebloxroyale.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * </ul>
 * Everything else, e4mc's other problems included, is logged as it is.
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
	/** Minecraft's words when it can't draw a frame (Minecraft.renderFrame); its exception's, when the window is minimized. */
	private static final String[] SURFACE = {"Couldn't acquire next surface texture", "Couldn't configure surface"};
	private static final String MINIMIZED = "Cannot acquire minimized window";
	/** Minecraft's system report on a PC whose Windows performance counters are off (oshi reads them for it). */
	private static final String SYSTEM_INFO = "Failed to get system info for";
	private static final String COUNTERS = "BR: Windows didn't give Minecraft some system details (its performance counters are off on this PC). "
		+ "Only Minecraft's own reports miss them; the game is fine.";
	private static final Set<String> told = ConcurrentHashMap.newKeySet();

	public LogWatch() {
		super(Filter.Result.NEUTRAL, Filter.Result.NEUTRAL);
	}

	static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve("minebloxroyale");
	}

	@Override
	public void onPreLaunch() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
		try {
			Files.createDirectories(dir());
			Files.writeString(dir().resolve("melty.log"), "Mineblox Royale started " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
				+ "; friends can join once the world is open" + System.lineSeparator(), StandardCharsets.UTF_8);
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
			for (String s : SURFACE) {
				if (!msg.startsWith(s) || !everything(event.getThrown(), msg).contains(MINIMIZED)) continue;
				if (told.add(MINIMIZED)) LOG.info("BR: the game window was minimized, so Minecraft drew nothing until it was back. Nothing is wrong.");
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
