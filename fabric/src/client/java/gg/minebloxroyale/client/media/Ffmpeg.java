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
	private Ffmpeg() {
	}

	private static @Nullable Process start(Path input, String... outputArgs) {
		Path exe = GameDirs.ffmpeg();
		if (exe == null) {
			MinebloxRoyale.LOG.warn("BR media: no ffmpeg found, can't decode {}", input);
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
			MinebloxRoyale.LOG.warn("BR media: ffmpeg failed to start for {}", input, e);
			return null;
		}
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
