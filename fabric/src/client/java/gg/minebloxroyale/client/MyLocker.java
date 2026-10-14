package gg.minebloxroyale.client;

import gg.minebloxroyale.Legacy;
import gg.minebloxroyale.net.Net;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Your Locker outfit, picked emotes and glider, remembered per Minecraft account outside the game folder (PlayerFiles:
 * %APPDATA%\MinebloxRoyale\locker-<account>.txt) and sent to any world you join, so they never reset when you rejoin,
 * play in a friend's world or update to a new version. The first time, the newest outfit an older build kept inside its
 * instance (minebloxroyale/my_locker.txt, here or in the launcher's other instances) is brought over. Unlocks
 * themselves come from your stats in every world you've played in (MyUnlocks).
 */
public final class MyLocker {
	/** True once this session's saved outfit went to the server (from then on, what the server confirms is saved). */
	private static boolean sent;

	private MyLocker() {
	}

	private static Path file() {
		return PlayerFiles.dir().resolve("locker-" + PlayerFiles.account() + ".txt");
	}

	/** Joined a world: send the remembered outfit. */
	public static void onJoin() {
		sent = false;
		try {
			String saved = Files.exists(file()) ? Files.readString(file(), StandardCharsets.UTF_8).trim() : fromOlderBuilds();
			if (!saved.isEmpty()) ClientPlayNetworking.send(new Net.Action("lockerall:" + saved));
		} catch (Exception ignored) {
		}
		sent = true;
	}

	/** The newest outfit an older build kept in its own instance ("" if none). */
	private static String fromOlderBuilds() {
		Path newest = null;
		FileTime newestAt = null;
		for (Path instance : PlayerFiles.instances()) for (Path f : Legacy.instanceFiles(instance, "my_locker.txt")) {
			try {
				if (!Files.isRegularFile(f)) continue;
				FileTime at = Files.getLastModifiedTime(f);
				if (newestAt == null || at.compareTo(newestAt) > 0) {
					newest = f;
					newestAt = at;
				}
			} catch (Exception ignored) {
			}
		}
		try {
			return newest == null ? "" : Files.readString(newest, StandardCharsets.UTF_8).trim();
		} catch (Exception e) {
			return "";
		}
	}

	/** The server confirmed my outfit: remember it. */
	public static void onOwnOutfit(String encoded) {
		if (!sent) return;
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), encoded, StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}
}
