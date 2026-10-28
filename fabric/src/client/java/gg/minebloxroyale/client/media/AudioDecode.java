package gg.minebloxroyale.client.media;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

/**
 * Decodes a Roblox sound file from the player's own Roblox install to 16-bit PCM, with nothing of our own to run
 * (1.2.0.1, players' Melty: "Windows' Smart App Control blocked this mashup's ffmpeg.exe because it isn't signed. Melty
 * can't start it on this PC"; the bundled ffmpeg is gone). OGG goes through stb_vorbis, which Minecraft already brings
 * (LWJGL); MP3 through Windows' own MP3 decoder (its Audio Compression Manager, msacm32), called through JNA, which
 * Minecraft also brings. Both are signed by their makers, so Smart App Control lets them be. No game classes here, so it
 * can be checked on its own against ffmpeg.
 */
public final class AudioDecode {
	private AudioDecode() {
	}

	/** Why the last decode gave nothing (for the log), or null. */
	private static volatile @Nullable String lastProblem;

	public static @Nullable String lastProblem() {
		return lastProblem;
	}

	/** Whole file -> 16-bit PCM (little endian), channels interleaved, at rate. Null if it can't be decoded. */
	public static byte @Nullable [] pcm(Path file, int channels, int rate) {
		lastProblem = null;
		try {
			byte[] data = Files.readAllBytes(file);
			String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
			Decoded d = name.endsWith(".ogg") ? ogg(data) : name.endsWith(".mp3") ? mp3(data) : null;
			if (d == null) {
				if (lastProblem == null) lastProblem = "not an OGG or MP3 file";
				return null;
			}
			return toBytes(convert(d.samples, d.channels, d.rate, channels, rate));
		} catch (IOException | RuntimeException | LinkageError e) {
			lastProblem = e.getClass().getSimpleName() + ": " + e.getMessage();
			return null;
		}
	}

	/** Interleaved 16-bit samples as read. */
	private record Decoded(short[] samples, int channels, int rate) {
	}

	// ---- OGG: stb_vorbis (LWJGL) ----

	private static @Nullable Decoded ogg(byte[] data) {
		ByteBuffer in = MemoryUtil.memAlloc(data.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			in.put(data).flip();
			IntBuffer ch = stack.mallocInt(1), rate = stack.mallocInt(1);
			ShortBuffer out = STBVorbis.stb_vorbis_decode_memory(in, ch, rate);
			if (out == null) {
				lastProblem = "stb_vorbis couldn't read it";
				return null;
			}
			try {
				short[] s = new short[out.remaining()];
				out.get(0, s); // (absolute: free() needs the buffer's start, and a relative get moves it to the end)
				return new Decoded(s, ch.get(0), rate.get(0));
			} finally {
				LibCStdlib.free(out);
			}
		} finally {
			MemoryUtil.memFree(in);
		}
	}

	// ---- MP3: Windows' own decoder (Audio Compression Manager) ----

	/** msacm32.dll, as much of it as decoding needs. */
	private interface Acm extends Library {
		int acmStreamOpen(PointerByReference stream, Pointer driver, Pointer source, Pointer dest, Pointer filter, Pointer callback, Pointer instance, int flags);

		int acmStreamSize(Pointer stream, int input, IntByReference output, int flags);

		int acmStreamPrepareHeader(Pointer stream, Pointer header, int flags);

		int acmStreamConvert(Pointer stream, Pointer header, int flags);

		int acmStreamUnprepareHeader(Pointer stream, Pointer header, int flags);

		int acmStreamClose(Pointer stream, int flags);
	}

	private static final int ACM_STREAMOPENF_NONREALTIME = 0x04, ACM_STREAMCONVERTF_BLOCKALIGN = 0x04, ACM_STREAMCONVERTF_START = 0x10,
		ACM_STREAMCONVERTF_END = 0x20;
	/** sizeof(ACMSTREAMHEADER) on 64-bit Windows, and where its fields are. */
	private static final int HEADER = 128, H_SRC = 16, H_SRC_LEN = 24, H_SRC_USED = 28, H_DST = 40, H_DST_LEN = 48, H_DST_USED = 52;
	private static volatile @Nullable Acm acm;

	private static Acm acm() {
		Acm a = acm;
		if (a == null) acm = a = Native.load("msacm32", Acm.class);
		return a;
	}

	/** An MP3 frame header: where it is, and what the stream is. */
	private record Frame(int at, int length, int rate, int channels, int kbps) {
	}

	private static final int[][] KBPS = { { 0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320 }, // MPEG-1 layer III
		{ 0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160 } }; // MPEG-2 / 2.5 layer III
	private static final int[] RATES = { 44100, 48000, 32000 };

	/** The first MP3 frame from 'from' on (after an ID3v2 tag, if any), or null. */
	static @Nullable Frame firstFrame(byte[] d, int from) {
		for (int i = from; i + 4 <= d.length; i++) {
			int b1 = d[i] & 0xFF, b2 = d[i + 1] & 0xFF, b3 = d[i + 2] & 0xFF, b4 = d[i + 3] & 0xFF;
			if (b1 != 0xFF || (b2 & 0xE0) != 0xE0) continue;
			int version = (b2 >> 3) & 3, layer = (b2 >> 1) & 3, bitrate = b3 >> 4, rateIndex = (b3 >> 2) & 3, pad = (b3 >> 1) & 1;
			if (version == 1 || layer != 1 || bitrate == 0 || bitrate == 15 || rateIndex == 3) continue; // layer III, a real bitrate and rate
			boolean mpeg1 = version == 3;
			int rate = RATES[rateIndex] >> (mpeg1 ? 0 : version == 2 ? 1 : 2), kbps = KBPS[mpeg1 ? 0 : 1][bitrate];
			int length = (mpeg1 ? 144 : 72) * kbps * 1000 / rate + pad;
			return new Frame(i, length, rate, ((b4 >> 6) & 3) == 3 ? 1 : 2, kbps);
		}
		return null;
	}

	/** Where the audio starts: past an ID3v2 tag if the file has one. */
	static int afterId3(byte[] d) {
		if (d.length < 10 || d[0] != 'I' || d[1] != 'D' || d[2] != '3') return 0;
		int size = (d[6] & 0x7F) << 21 | (d[7] & 0x7F) << 14 | (d[8] & 0x7F) << 7 | (d[9] & 0x7F);
		return 10 + size + ((d[5] & 0x10) != 0 ? 10 : 0);
	}

	private static @Nullable Decoded mp3(byte[] data) {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
			lastProblem = "MP3s are decoded by Windows (not this system)";
			return null;
		}
		Frame f = firstFrame(data, afterId3(data));
		if (f == null) {
			lastProblem = "no MP3 frame in it";
			return null;
		}
		// MPEGLAYER3WAVEFORMAT in, WAVEFORMATEX PCM out (mmreg.h, packed).
		Memory src = new Memory(30), dst = new Memory(18);
		src.clear();
		dst.clear();
		src.setShort(0, (short) 0x55); // WAVE_FORMAT_MPEGLAYER3
		src.setShort(2, (short) f.channels);
		src.setInt(4, f.rate);
		src.setInt(8, f.kbps * 1000 / 8);
		src.setShort(12, (short) 1);
		src.setShort(14, (short) 0);
		src.setShort(16, (short) 12); // its 12 extra bytes:
		src.setShort(18, (short) 1); // MPEGLAYER3_ID_MPEG
		src.setInt(20, 2); // MPEGLAYER3_FLAG_PADDING_OFF
		src.setShort(24, (short) f.length);
		src.setShort(26, (short) 1);
		src.setShort(28, (short) 1393);
		dst.setShort(0, (short) 1); // WAVE_FORMAT_PCM
		dst.setShort(2, (short) f.channels);
		dst.setInt(4, f.rate);
		dst.setInt(8, f.rate * f.channels * 2);
		dst.setShort(12, (short) (f.channels * 2));
		dst.setShort(14, (short) 16);
		Acm a = acm();
		PointerByReference ref = new PointerByReference();
		int r = a.acmStreamOpen(ref, null, src, dst, null, null, null, ACM_STREAMOPENF_NONREALTIME);
		if (r != 0) {
			lastProblem = "Windows' MP3 decoder wouldn't open (" + r + ")";
			return null;
		}
		Pointer stream = ref.getValue();
		int chunk = 32 * 1024;
		Memory in = new Memory(chunk), header = new Memory(HEADER);
		IntByReference outSize = new IntByReference();
		boolean prepared = false;
		ByteArrayOutputStream pcm = new ByteArrayOutputStream(data.length * 12);
		try {
			if (a.acmStreamSize(stream, chunk, outSize, 0) != 0) {
				lastProblem = "Windows' MP3 decoder gave no size";
				return null;
			}
			Memory out = new Memory(Math.max(outSize.getValue(), 4096));
			header.clear();
			header.setInt(0, HEADER);
			header.setPointer(H_SRC, in);
			header.setInt(H_SRC_LEN, chunk);
			header.setPointer(H_DST, out);
			header.setInt(H_DST_LEN, (int) out.size());
			if (a.acmStreamPrepareHeader(stream, header, 0) != 0) {
				lastProblem = "Windows' MP3 decoder wouldn't take the buffers";
				return null;
			}
			prepared = true;
			int pos = f.at;
			boolean first = true;
			while (pos < data.length) {
				int n = Math.min(chunk, data.length - pos);
				in.write(0, data, pos, n);
				header.setInt(H_SRC_LEN, n);
				if (a.acmStreamConvert(stream, header, ACM_STREAMCONVERTF_BLOCKALIGN | (first ? ACM_STREAMCONVERTF_START : 0)) != 0) break;
				first = false;
				int used = header.getInt(H_SRC_USED), got = header.getInt(H_DST_USED);
				if (got > 0) pcm.writeBytes(out.getByteArray(0, got));
				if (used <= 0) break; // (only part of a frame left)
				pos += used;
			}
			header.setInt(H_SRC_LEN, 0);
			if (a.acmStreamConvert(stream, header, ACM_STREAMCONVERTF_END) == 0) {
				int got = header.getInt(H_DST_USED);
				if (got > 0) pcm.writeBytes(out.getByteArray(0, got));
			}
			header.setInt(H_SRC_LEN, chunk); // (unprepared as it was prepared)
		} finally {
			if (prepared) a.acmStreamUnprepareHeader(stream, header, 0);
			a.acmStreamClose(stream, 0);
		}
		byte[] bytes = pcm.toByteArray();
		if (bytes.length == 0) {
			lastProblem = "Windows' MP3 decoder gave no sound";
			return null;
		}
		short[] s = new short[bytes.length / 2];
		ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s);
		return new Decoded(s, f.channels, f.rate);
	}

	// ---- to the channels and rate asked for ----

	/** Mixed to 'outCh' channels (1 or 2) and resampled to 'outRate' (linear, fine for short effects). */
	static short[] convert(short[] in, int inCh, int inRate, int outCh, int outRate) {
		int frames = in.length / Math.max(1, inCh);
		int outFrames = (int) ((long) frames * outRate / inRate);
		short[] out = new short[outFrames * outCh];
		double step = (double) inRate / outRate;
		for (int i = 0; i < outFrames; i++) {
			double at = i * step;
			int a = (int) at, b = Math.min(a + 1, frames - 1);
			double t = at - a;
			for (int c = 0; c < outCh; c++) {
				double va = sample(in, inCh, a, c, outCh), vb = sample(in, inCh, b, c, outCh);
				out[i * outCh + c] = (short) Math.round(va + (vb - va) * t);
			}
		}
		return out;
	}

	/** Frame 'f' of channel 'c' when going to outCh channels: mono out is the mean of every channel in. */
	private static double sample(short[] in, int inCh, int f, int c, int outCh) {
		if (outCh == 1) {
			double sum = 0;
			for (int k = 0; k < inCh; k++) sum += in[f * inCh + k];
			return sum / inCh;
		}
		return in[f * inCh + Math.min(c, inCh - 1)];
	}

	private static byte[] toBytes(short[] s) {
		byte[] b = new byte[s.length * 2];
		for (int i = 0; i < s.length; i++) {
			b[i * 2] = (byte) s[i];
			b[i * 2 + 1] = (byte) (s[i] >> 8);
		}
		return b;
	}
}
