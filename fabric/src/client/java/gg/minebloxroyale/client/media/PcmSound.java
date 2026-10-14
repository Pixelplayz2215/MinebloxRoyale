package gg.minebloxroyale.client.media;

import gg.minebloxroyale.MinebloxRoyale;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * A sound whose samples come from memory (decoded from the player's Roblox install), not from a resource pack: the
 * "minebloxroyale:stream" event (sounds.json) points at Fabric's empty sound with stream=true, and getAudioStream
 * supplies the PCM.
 */
public class PcmSound extends AbstractSoundInstance {
	private final AudioFormat format;
	private final byte[] pcm;

	private PcmSound(AudioFormat format, byte[] pcm, float volume, Vec3 at) {
		super(MinebloxRoyale.id("stream"), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.format = format;
		this.pcm = pcm;
		this.volume = volume;
		this.x = at.x;
		this.y = at.y;
		this.z = at.z;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.relative = false;
	}

	/** Mono clip positioned in the world (Minecraft only positions mono sounds). */
	public static PcmSound at(byte[] monoPcm, int rate, Vec3 at, float volume) {
		return new PcmSound(new AudioFormat(rate, 16, 1, true, false), monoPcm, volume, at);
	}

	@Override
	public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
		return CompletableFuture.completedFuture(new AudioStream() {
			private int pos;

			@Override
			public AudioFormat getFormat() {
				return format;
			}

			@Override
			public ByteBuffer read(int expectedSize) {
				int frame = format.getFrameSize();
				int n = Math.max(0, Math.min(Math.max(frame, expectedSize - expectedSize % frame), pcm.length - pos));
				ByteBuffer buf = ByteBuffer.allocateDirect(n);
				buf.put(pcm, pos, n).flip();
				pos += n;
				return buf;
			}

			@Override
			public void close() {
			}
		});
	}
}
