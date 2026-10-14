package gg.minebloxroyale.client.media;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.sheets.Sheets;
import gg.minebloxroyale.sheets.SoundDef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Plays sounds-sheet sounds. Roblox ones are decoded once from the player's Roblox install into memory. */
public final class SfxBank {
	static final int RATE = 44100;
	private static final Map<String, byte[]> PCM = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();

	private SfxBank() {
	}

	/** Starts decoding every Roblox sound in the background. */
	public static void preload() {
		for (SoundDef s : Sheets.SOUNDS) if (s.game().equals("roblox")) load(s);
	}

	private static void load(SoundDef s) {
		if (PCM.containsKey(s.id()) || LOADING.putIfAbsent(s.id(), true) != null) return;
		CompletableFuture.runAsync(() -> {
			Path content = GameDirs.robloxContent();
			if (content == null || !Files.isRegularFile(content.resolve(s.file()))) {
				MinebloxRoyale.LOG.warn("BR media: Roblox sound {} not found ({})", s.id(), s.file());
				return;
			}
			byte[] pcm = Ffmpeg.decodePcm(content.resolve(s.file()), 1, RATE);
			if (pcm != null) {
				PCM.put(s.id(), pcm);
				MinebloxRoyale.LOG.info("BR media: Roblox sound {} ready ({} ms)", s.id(), pcm.length * 1000L / 2 / RATE);
			}
		});
	}

	public static boolean ready(String id) {
		return PCM.containsKey(id);
	}

	public static void play(String id, Vec3 at) {
		SoundDef s = Sheets.SOUNDS_BY_ID.get(id);
		if (s == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (s.game().equals("minecraft")) {
			SoundEvent ev = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(s.file()));
			if (ev != null) mc.getSoundManager().play(new SimpleSoundInstance(ev, SoundSource.PLAYERS, s.volume(), s.pitch(), mc.level == null ? RandomSource.create() : mc.level.getRandom(), BlockPos.containing(at)));
			return;
		}
		byte[] pcm = PCM.get(id);
		if (pcm == null) {
			load(s);
			return;
		}
		mc.getSoundManager().play(PcmSound.at(pcm, RATE, at, s.volume()));
	}
}
