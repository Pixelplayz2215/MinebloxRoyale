package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;

/**
 * The badge by each player's name for the launcher they play from (2.0.0-test6.2, creator: "Icons next to players names
 * based on the launcher they're using. PLayer using modrinth launcher should show the modringth logo in the nametag (shows
 * for everybody), Lunar will show Lunar icon, If launched Prism standalone shows Prism icon, etc. If launched via Melty's
 * prism then show Melty icon"). Our own badges in each launcher's colours (fabric/tools/branding/make_launcher_badges.py),
 * one character each in the font minebloxroyale:launchers (U+E000 on, in IDS' order). Your game works out its launcher
 * when it starts and tells the world on joining ("launcher:<id>"); the world tells everyone (Net.Launchers).
 */
public final class LauncherBadge {
	/** The badges, in the font's order (make_launcher_badges.py BADGES). */
	public static final List<String> IDS = List.of("melty", "prism", "multimc", "modrinth", "lunar", "curseforge", "atlauncher", "gdlauncher", "minecraft", "dev");
	/** Everyone's launcher in this world, by account id (from the world). */
	public static final Map<String, String> BY_PLAYER = new ConcurrentHashMap<>();
	private static final FontDescription FONT = new FontDescription.Resource(MinebloxRoyale.id("launchers"));
	private static String mine;

	private LauncherBadge() {
	}

	/**
	 * This game's launcher: Melty (its own Prism, or the copy of our package it installed before 1.0.1.1), the launchers
	 * that start the game with their own entry point (Prism, MultiMC, Lunar), those known by where their games live
	 * (Modrinth App, CurseForge, ATLauncher, GDLauncher), Mojang's own launcher by the brand it passes, the development
	 * game; "" if it can't tell.
	 */
	public static String mine() {
		if (mine != null) return mine;
		String dir = FabricLoader.getInstance().getGameDir().toAbsolutePath().toString().replace('\\', '/').toLowerCase(Locale.ROOT) + "/";
		String cmd = System.getProperty("sun.java.command", "").toLowerCase(Locale.ROOT);
		String brand = System.getProperty("minecraft.launcher.brand", "").toLowerCase(Locale.ROOT);
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) mine = "dev";
		else if (dir.contains("/melty/mods/") || dir.contains("/appdata/local/minebloxroyale/prism launcher/")) mine = "melty";
		else if (dir.contains("/curseforge/")) mine = "curseforge";
		else if (dir.contains("/modrinthapp/") || dir.contains("com.modrinth.theseus") || brand.contains("modrinth") || brand.contains("theseus")) mine = "modrinth";
		else if (dir.contains("/.lunarclient/") || cmd.contains("moonsworth") || brand.contains("lunar")) mine = "lunar";
		else if (dir.contains("/atlauncher/") || brand.contains("atlauncher")) mine = "atlauncher";
		else if (dir.contains("gdlauncher") || brand.contains("gdlauncher")) mine = "gdlauncher";
		else if (cmd.contains("org.prismlauncher") || brand.contains("prism")) mine = "prism";
		else if (cmd.contains("org.multimc") || brand.contains("multimc")) mine = "multimc";
		else if (brand.contains("minecraft-launcher")) mine = "minecraft";
		else mine = "";
		MinebloxRoyale.LOG.info("BR: launcher badge '{}' (brand '{}', game folder {})", mine, brand, dir);
		return mine;
	}

	/**
	 * The badge for an account id and a space after it, to put the name after (empty: none known). Only the badge is in
	 * the badges' font: the root and what's added after it keep the normal one.
	 */
	public static MutableComponent badge(String uuid) {
		int i = IDS.indexOf(BY_PLAYER.getOrDefault(uuid, ""));
		if (i < 0) return Component.empty();
		return Component.empty().append(Component.literal(String.valueOf((char) (0xE000 + i))).withStyle(s -> s.withFont(FONT).withColor(0xFFFFFF)))
			.append(Component.literal(" "));
	}
}
