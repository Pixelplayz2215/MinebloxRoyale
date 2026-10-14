package gg.minebloxroyale.client;

import gg.minebloxroyale.Legacy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;

/**
 * Where each player's own Mineblox Royale things are kept on their PC: outside the game folder
 * (%APPDATA%\MinebloxRoyale), per Minecraft account, so every version and every launcher on that PC shares them
 * (creator: updating from test2 to test3, a new instance, lost the unlocks). Also finds the launcher's other instances
 * next to this one, to bring over what older builds kept inside their own instance.
 */
public final class PlayerFiles {
	private PlayerFiles() {
	}

	/**
	 * Renamed from Block Royale: the old record (%APPDATA%\BlockRoyale) is copied across; kept for older builds still
	 * installed.
	 */
	static void migrate() {
		String appData = System.getenv("APPDATA");
		Path old = appData != null && !appData.isBlank() ? Path.of(appData, Legacy.OLD_APPDATA) : Path.of(System.getProperty("user.home"), Legacy.OLD_HOME);
		Legacy.migrateFolder(old, dir(), true);
	}

	public static Path dir() {
		String appData = System.getenv("APPDATA");
		return appData != null && !appData.isBlank() ? Path.of(appData, "MinebloxRoyale") : Path.of(System.getProperty("user.home"), ".minebloxroyale");
	}

	/** The signed-in Minecraft account's id (the same in every world, version and launcher). */
	public static String account() {
		return Minecraft.getInstance().getUser().getProfileId().toString();
	}

	/**
	 * This game folder and the other instances beside it: Modrinth App profiles\*, CurseForge Instances\*, Prism
	 * instances\*\minecraft (or .minecraft). Only one level of folders is listed, so it stays quick.
	 */
	public static List<Path> instances() {
		Path game = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath();
		Set<Path> roots = new LinkedHashSet<>();
		roots.add(game);
		Path p1 = game.getParent(), p2 = p1 == null ? null : p1.getParent();
		for (Path parent : new Path[] { p1, p2 }) {
			if (parent == null) continue;
			try (Stream<Path> s = Files.list(parent)) {
				for (Path c : s.filter(Files::isDirectory).toList()) {
					roots.add(c);
					roots.add(c.resolve("minecraft"));
					roots.add(c.resolve(".minecraft"));
				}
			} catch (IOException | RuntimeException ignored) {
			}
		}
		List<Path> out = new ArrayList<>();
		for (Path r : roots) if (Files.isDirectory(r)) out.add(r);
		return out;
	}
}
