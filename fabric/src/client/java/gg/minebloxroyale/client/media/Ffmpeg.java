package gg.minebloxroyale.client.media;

import gg.minebloxroyale.MinebloxRoyale;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Runs the bundled ffmpeg (LGPL build) to decode a file from the player's games into raw audio/video on stdout. */
public final class Ffmpeg {
	/**
	 * (1.0.0.4) ffmpeg couldn't be found or started, so it isn't tried again: Windows' Smart App Control (or an antivirus)
	 * blocks it on some PCs, and every Roblox sound then logged the whole exception (Melty read those as the mod failing
	 * to load). One plain line says so; the Roblox sounds are simply off then, and everything else works.
	 */
	private static volatile boolean unavailable;

	private Ffmpeg() {
	}

	private static @Nullable Process start(Path input, String... outputArgs) {
		if (unavailable) return null;
		Path exe = GameDirs.ffmpeg();
		if (exe == null) {
			off("it isn't in the game folder");
			return null;
		}
		List<String> cmd = new ArrayList<>(List.of(exe.toString(), "-v", "error", "-nostdin", "-i", input.toString()));
		cmd.addAll(List.of(outputArgs));
		cmd.add("-");
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectError(ProcessBuilder.Redirect.DISCARD);
			pb.redirectInput(ProcessBuilder.Redirect.from(new File("NUL")));
			return pb.start();
		} catch (IOException e) {
			String why = String.valueOf(e.getMessage());
			off(why.contains("4551") || why.contains("Application Control") ? "Windows' Smart App Control blocked it"
				: why.contains("error=5,") || why.contains("denied") ? "Windows or an antivirus didn't let it run"
				: why.contains("error=2,") ? "it's gone from the game folder (an antivirus may have removed it)" : "it wouldn't start");
			return null;
		}
	}

	/** ffmpeg can't be used this session: said once, in plain words (never the exception, see above). */
	private static synchronized void off(String why) {
		if (unavailable) return;
		unavailable = true;
		MinebloxRoyale.LOG.warn("BR media: the bundled ffmpeg can't be used ({}), so the Roblox sounds are off this time. Everything else works.", why);
	}

	/** Whole file -> 16-bit PCM (little endian) in memory. */
	public static byte @Nullable [] decodePcm(Path input, int channels, int rate) {
		Process p = start(input, "-vn", "-f", "s16le", "-acodec", "pcm_s16le", "-ac", Integer.toString(channels), "-ar", Integer.toString(rate));
		if (p == null) return null;
		try (InputStream in = p.getInputStream()) {
			byte[] data = in.readAllBytes();
			p.waitFor();
			return data.length == 0 ? null : data;
		} catch (IOException | InterruptedException e) {
			p.destroyForcibly();
			return null;
		}
	}
}
