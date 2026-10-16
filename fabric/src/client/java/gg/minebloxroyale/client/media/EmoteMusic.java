package gg.minebloxroyale.client.media;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.client.ClientState;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.Sheets;
import gg.minebloxroyale.sheets.SoundDef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Emote music (2.0.0-test5, creator: "Add emote music! (When emote music is played, private regular music is muted until
 * emote is done. If I'm playing an emote do not mute game music for my friend, but let them still hear my emote
 * music"): each emote's own loop (emotes sheet music, a sounds-sheet row; fabric/tools/audio/emote_music.py) plays for
 * as long as it's danced, starting with it so the beat keeps in step. Yours plays as it is, not from a place, and your
 * own game music (the battle music, the hub's) goes quiet while it does, coming back when you stop; everyone near
 * hears yours coming from you, fainter further off, with their own game music carrying on under it. The Locker's
 * preview (hovering an emote) plays it too. A few at a time: yours and the two nearest others (a whole team dancing on
 * the Victory Royale stage would only be a din).
 */
public final class EmoteMusic {
	/** Other dancers heard at once (the nearest), and how far off (blocks: the events' attenuation_distance). */
	private static final int OTHERS = 2;
	private static final double RANGE = 24;
	private static final Map<Integer, Track> PLAYING = new HashMap<>();
	private static boolean mine;

	private EmoteMusic() {
	}

	/** Whether your own emote's music is playing: your game music keeps quiet while it is. */
	public static boolean quietGameMusic() {
		return mine;
	}

	/** One dancer's loop: yours as it is; anyone else's following them about. */
	private static final class Track extends AbstractTickableSoundInstance {
		final String emote;
		final long started;
		final int entityId;
		final boolean own;

		Track(SoundDef s, String emote, long started, int entityId, boolean own, @Nullable Entity e) {
			super(SoundEvent.createVariableRangeEvent(MinebloxRoyale.id(s.file())), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.emote = emote;
			this.started = started;
			this.entityId = entityId;
			this.own = own;
			looping = true;
			delay = 0;
			volume = s.volume();
			pitch = s.pitch();
			if (own || e == null) {
				relative = true;
				attenuation = SoundInstance.Attenuation.NONE;
			} else {
				attenuation = SoundInstance.Attenuation.LINEAR;
				follow(e);
			}
		}

		private void follow(Entity e) {
			x = e.getX();
			y = e.getY() + 1;
			z = e.getZ();
		}

		@Override
		public void tick() {
			if (own) return;
			var level = Minecraft.getInstance().level;
			Entity e = level == null ? null : level.getEntity(entityId);
			if (e == null || e.isRemoved()) stop();
			else follow(e);
		}

		void end() {
			stop();
		}
	}

	private static @Nullable SoundDef musicOf(String emote) {
		EmoteDef def = Sheets.EMOTES_BY_ID.get(emote);
		if (def == null || def.music().isBlank()) return null;
		SoundDef s = Sheets.SOUNDS_BY_ID.get(def.music());
		return s != null && s.game().equals("mod") ? s : null;
	}

	/** Every client tick: each dancer heard has their emote's loop going, from the start of their dance. */
	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			stopAll();
			return;
		}
		int me = mc.player.getId();
		Map<Integer, Map.Entry<String, Long>> want = new HashMap<>();
		List<Map.Entry<Entity, Map.Entry<String, Long>>> others = new ArrayList<>();
		for (var e : ClientState.EMOTES.entrySet()) {
			if (musicOf(e.getValue().getKey()) == null) continue;
			if (e.getKey() == me) {
				want.put(me, e.getValue());
				continue;
			}
			Entity who = mc.level.getEntity(e.getKey());
			if (who != null && who.distanceToSqr(mc.player) < RANGE * RANGE) others.add(Map.entry(who, e.getValue()));
		}
		others.sort(Comparator.comparingDouble(o -> o.getKey().distanceToSqr(mc.player)));
		for (var o : others.subList(0, Math.min(OTHERS, others.size()))) want.put(o.getKey().getId(), o.getValue());
		var sounds = mc.getSoundManager();
		PLAYING.entrySet().removeIf(p -> {
			Map.Entry<String, Long> w = want.get(p.getKey());
			Track t = p.getValue();
			boolean keep = w != null && w.getKey().equals(t.emote) && w.getValue() == t.started && !t.isStopped();
			if (!keep) {
				t.end();
				sounds.stop(t);
			}
			return !keep;
		});
		for (var w : want.entrySet()) {
			if (PLAYING.containsKey(w.getKey())) continue;
			SoundDef s = musicOf(w.getValue().getKey());
			boolean own = w.getKey() == me;
			Track t = new Track(s, w.getValue().getKey(), w.getValue().getValue(), w.getKey(), own, own ? null : mc.level.getEntity(w.getKey()));
			sounds.play(t);
			PLAYING.put(w.getKey(), t);
		}
		mine = PLAYING.containsKey(me);
		if (mine) mc.getMusicManager().stopPlaying(); // (no Minecraft music over yours either)
	}

	public static void stopAll() {
		var sounds = Minecraft.getInstance().getSoundManager();
		for (Track t : PLAYING.values()) {
			t.end();
			sounds.stop(t);
		}
		PLAYING.clear();
		mine = false;
	}
}
