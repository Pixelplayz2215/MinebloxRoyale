package gg.minebloxroyale.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * (1.0.1.1) e4mc's two native libraries come with the game (minebloxroyale/e4mc). e4mc downloads them from
 * natives.e4mc.link the first time a world opens to friends, and on players' PCs where that download is blocked or times
 * out, e4mc fails and the world never gets an address: friends sent by Melty waited for ever ("Waiting for the host's
 * game to open"), and the failure logged as errors (Melty, 2026-10-16: "Connection timed out" in Quiche.loadNativeLibrary).
 * e4mc reads a ready library from these two system properties instead of downloading; each is only set when the file
 * is byte for byte the one e4mc itself checks for (the same SHA-256 as natives.e4mc.link's). Runs before Minecraft
 * starts (no game classes here).
 */
final class E4mcNatives {
	private static final Logger LOG = LogManager.getLogger("MinebloxRoyale");
	/** e4mc's property, the library's file name, and its SHA-256 (e4mc 6.2.2's own list). */
	private static final String[][] NATIVES = {
		{"link.e4mc.native_path", "netty_quiche_windows_x86_64_74.dll", "cca45c8f4c08525a4edf032b1c77dad9fa03ab99276f9df02a7a13126b600804"},
		{"link.e4mc.dialtone.native_path", "iroh_java_windows_x86_64_57caf9a.dll", "534286d02cdc1596ea6064d4193293228baef694d088dc7ddf931a3bdd9106eb"}};

	private E4mcNatives() {
	}

	static void use(Path dir) {
		int used = 0;
		for (String[] n : NATIVES) {
			if (System.getProperty(n[0]) != null) continue; // set by the launcher: left as it is
			Path file = dir.resolve(n[1]).toAbsolutePath();
			if (!Files.isRegularFile(file)) continue; // another system, or a package without them: e4mc downloads as before
			try {
				if (!n[2].equals(sha256(file))) {
					LOG.info("BR: {} isn't the copy e4mc expects; e4mc downloads its own", n[1]);
					continue;
				}
				System.setProperty(n[0], file.toString());
				used++;
			} catch (IOException e) {
				LOG.info("BR: couldn't read {} ({}); e4mc downloads its own", n[1], e.getMessage());
			}
		}
		if (used > 0) LOG.info("BR: e4mc uses the {} relay libraries that come with the game (nothing to download)", used);
	}

	private static String sha256(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] buf = new byte[1 << 16];
			for (int r; (r = in.read(buf)) > 0;) md.update(buf, 0, r);
			return HexFormat.of().formatHex(md.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}
}
