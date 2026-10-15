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
 * account is offline or its sign-in has run out (Status: 401). Nothing is wrong with the game then, so each becomes one
 * plain line.</li>
 * </ul>
 */
public final class LogWatch extends AbstractFilter implements PreLaunchEntrypoint {
	private static final Logger LOG = LogManager.getLogger("MinebloxRoyale");
	private static final Pattern DOMAIN = Pattern.compile("Domain assigned: ([a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.e4mc\\.link)");
	/** Minecraft's sign-in checks at start: their message, and what the one line says instead. */
	private static final String[][] SIGN_IN = {
		{"Failed to fetch user properties", "Minecraft couldn't load this account's settings from Microsoft", "Playing works as normal; joining friends needs a signed-in Minecraft account."},
		{"Failed to fetch Realms feature flags", "Minecraft couldn't reach Realms", "Mineblox Royale doesn't use Realms."},
		{"Could not authorize you against Realms server", "Minecraft couldn't sign in to Realms", "Mineblox Royale doesn't use Realms."}};
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
		String msg = event.getMessage() == null ? null : event.getMessage().getFormattedMessage();
		if (msg == null) return Filter.Result.NEUTRAL;
		if (msg.startsWith("Domain assigned: ")) {
			Matcher m = DOMAIN.matcher(msg);
			if (m.find()) AutoWorld.onE4mcDomain(m.group(1));
			return Filter.Result.NEUTRAL;
		}
		for (String[] s : SIGN_IN) {
			if (!msg.startsWith(s[0])) continue;
			if (told.add(s[0])) LOG.info("BR: {} ({}). {}", s[1], reason(event.getThrown(), msg), s[2]);
			return Filter.Result.DENY;
		}
		return Filter.Result.NEUTRAL;
	}

	/** The short reason: "Status: 401" and the like, from the exception or the message itself. */
	private static String reason(Throwable t, String msg) {
		for (Throwable c = t; c != null; c = c.getCause())
			if (c.getMessage() != null && !c.getMessage().isBlank()) return c.getClass().getSimpleName() + ": " + c.getMessage().lines().findFirst().orElse("");
		int colon = msg.indexOf(": ");
		return colon > 0 ? msg.substring(colon + 2) : "no reason given";
	}
}
