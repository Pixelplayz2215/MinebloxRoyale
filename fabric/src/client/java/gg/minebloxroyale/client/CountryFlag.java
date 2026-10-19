package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.net.Net;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;

/**
 * A country flag after each player's name (2.0.0, creator: "Is it possible to add a region flag on the username depending
 * on local public IP Address?", then "add it to v2.0.0 ... and how to hide them"; Mockups\Name Badges\Country flags
 * v2.0.0). When the game starts it asks Cloudflare which country this PC's internet address is in (its trace page answers
 * with the country itself); only that two-letter country is kept and shared, never the address. It goes to the world
 * like the launcher badge ("flag:<CC>", Net.Launchers), and everyone's comes back. /flag off hides yours (it sends none),
 * /flag on shows it again; the choice is kept per Minecraft account beside the unlocks (PlayerFiles, flags.txt), and the
 * first time a flag shows the chat says so and how to hide it. The flags are pictures in the font minebloxroyale:flags
 * (fabric/tools/branding/make_flags.py; flags_order.txt lists the countries in the font's order); a country with no
 * picture shows its two letters.
 */
public final class CountryFlag {
	/** Everyone's country in this world, by account id (from the world). */
	public static final Map<String, String> BY_PLAYER = new ConcurrentHashMap<>();
	private static final String TRACE = "https://www.cloudflare.com/cdn-cgi/trace";
	private static final FontDescription FONT = new FontDescription.Resource(MinebloxRoyale.id("flags"));
	private static final List<String> ORDER = order();
	/** This PC's country ("" until found, or if it can't be). */
	private static volatile String mine = "";
	private static volatile boolean looked;

	private CountryFlag() {
	}

	public static void init() {
		Thread t = new Thread(CountryFlag::lookUp, "MinebloxRoyale country");
		t.setDaemon(true);
		t.start();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal("flag")
			.executes(c -> status(c.getSource()))
			.then(ClientCommands.literal("off").executes(c -> show(c.getSource(), false)))
			.then(ClientCommands.literal("on").executes(c -> show(c.getSource(), true)))));
	}

	private static void lookUp() {
		try {
			HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
			HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(TRACE)).timeout(Duration.ofSeconds(10)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
			// (the page also says this PC's address: only the "loc=" line is read, nothing else is kept)
			for (String line : r.body().split("\n")) {
				if (!line.startsWith("loc=")) continue;
				String cc = line.substring(4).trim().toUpperCase(Locale.ROOT);
				if (cc.matches("[A-Z]{2}") && !cc.equals("XX") && !cc.equals("T1")) mine = cc; // (XX: unknown; T1: Tor)
			}
			MinebloxRoyale.LOG.info("BR: country flag: {}", mine.isEmpty() ? "none (no country in the answer)" : mine);
		} catch (IOException | RuntimeException e) {
			MinebloxRoyale.LOG.info("BR: country flag: none (couldn't reach Cloudflare: {})", e.getClass().getSimpleName());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		looked = true;
		Minecraft.getInstance().execute(CountryFlag::send); // (already in a world: send it now)
	}

	/** Joined a world: our flag (or none) goes to it. */
	public static void onJoin() {
		send();
	}

	/** Our flag to the world we're in (none while hidden or not found yet), and once ever, a word on how to hide it. */
	static void send() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return;
		boolean shown = !hidden();
		ClientPlayNetworking.send(new Net.Action("flag:" + (shown ? mine : "")));
		if (shown && !mine.isEmpty() && mc.player != null && !told()) {
			remember("told");
			mc.player.sendSystemMessage(Component.literal("Your country's flag (" + mine + ") now shows after your name for other players. Type /flag off to hide it.")
				.withStyle(ChatFormatting.YELLOW));
		}
	}

	private static int status(FabricClientCommandSource src) {
		if (hidden()) src.sendFeedback(Component.literal("Your flag is hidden. Type /flag on to show it."));
		else if (!mine.isEmpty()) src.sendFeedback(Component.literal("Your flag (" + mine + ") shows after your name. Type /flag off to hide it."));
		else src.sendFeedback(Component.literal(looked ? "Your country couldn't be found, so no flag shows." : "Still finding your country..."));
		return 1;
	}

	private static int show(FabricClientCommandSource src, boolean on) {
		if (on) forget("off");
		else remember("off");
		send();
		src.sendFeedback(Component.literal(on ? (mine.isEmpty() ? "Your flag will show once your country is found." : "Your flag (" + mine + ") shows again.")
			: "Your flag is hidden. Type /flag on to show it again.").withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	/** A player's flag for their name tag: its picture, or its two letters if there's no picture for it. */
	public static Component glyph(String cc) {
		int i = ORDER.indexOf(cc);
		if (i >= 0) return Component.literal(String.valueOf((char) (0xE000 + i))).withStyle(s -> s.withFont(FONT).withColor(0xFFFFFF));
		return Component.literal(cc).withStyle(s -> s.withColor(0xB8B8B8));
	}

	// ---- the per-account choice (flags.txt: "<account> off" while hidden, "<account> told" once told how to hide it) ----

	private static Path file() {
		return PlayerFiles.dir().resolve("flags.txt");
	}

	private static boolean hidden() {
		return lines().contains(PlayerFiles.account() + " off");
	}

	private static boolean told() {
		return lines().contains(PlayerFiles.account() + " told");
	}

	private static List<String> lines() {
		try {
			if (Files.exists(file())) return Files.readAllLines(file(), StandardCharsets.UTF_8).stream().map(String::trim).toList();
		} catch (IOException e) {
			MinebloxRoyale.LOG.info("BR: couldn't read flags.txt ({})", e.getMessage());
		}
		return List.of();
	}

	private static void remember(String what) {
		if (lines().contains(PlayerFiles.account() + " " + what)) return;
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), PlayerFiles.account() + " " + what + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
		} catch (IOException e) {
			MinebloxRoyale.LOG.info("BR: couldn't save flags.txt ({})", e.getMessage());
		}
	}

	private static void forget(String what) {
		List<String> keep = new ArrayList<>(lines());
		if (!keep.remove(PlayerFiles.account() + " " + what)) return;
		try {
			Files.write(file(), keep, StandardCharsets.UTF_8);
		} catch (IOException e) {
			MinebloxRoyale.LOG.info("BR: couldn't save flags.txt ({})", e.getMessage());
		}
	}

	/** The countries in the font's order (make_flags.py writes it beside the font). */
	private static List<String> order() {
		List<String> out = new ArrayList<>();
		try (InputStream in = CountryFlag.class.getResourceAsStream("/assets/minebloxroyale/font/flags_order.txt")) {
			if (in != null) for (String s : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\s+")) if (!s.isBlank()) out.add(s.trim());
		} catch (IOException e) {
			MinebloxRoyale.LOG.info("BR: couldn't read flags_order.txt ({})", e.getMessage());
		}
		return List.copyOf(out);
	}
}
