package gg.minebloxroyale.client.media;

import gg.minebloxroyale.MinebloxRoyale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * Battle music (v2.0.0, creator: "Lets add some battle music when in game"): the mod's own two tracks
 * (fabric/tools/audio/make_audio.py, made from scratch; nothing from Fortnite), looping under a live match: the steadier
 * one (music/battle_1) to begin with, the harder one (music/battle_2) once the storm is down to its last circles or ten
 * are left, the two cross-faded. Kept under the guns and footsteps, on the Music slider; faded out when the match ends.
 * Quiet while you're dancing an emote with music of its own (2.0.0-test5: EmoteMusic), back when you stop.
 */
public final class BattleMusic {
	/** How loud at full (the Music slider scales it); a loop fades in or out over about two seconds (Loop.tick). */
	private static final float VOLUME = 0.35F;
	/** The harder track from this storm circle on (of eight), or with this many left. */
	private static final int HARD_PHASE = 6, HARD_ALIVE = 10;
	private static @Nullable Loop calm, hard;

	private BattleMusic() {
	}

	/**
	 * One looping track that fades towards where it's told, and stops itself once faded out; held silent (still going,
	 * so it carries on where it was) while your emote's music plays (the hub's track is one too: FortniteMedia).
	 */
	static final class Loop extends AbstractTickableSoundInstance {
		private float level;
		float target;
		private final float full;
		final long started = System.currentTimeMillis();

		Loop(String track) {
			this(track, VOLUME);
		}

		Loop(String track, float full) {
			super(SoundEvent.createVariableRangeEvent(MinebloxRoyale.id(track)), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			looping = true;
			delay = 0;
			attenuation = SoundInstance.Attenuation.NONE;
			relative = true;
			volume = 0;
			this.full = full;
			target = full;
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			boolean quiet = EmoteMusic.quietGameMusic();
			float fade = full / 40, goal = quiet ? 0 : target;
			level += Math.max(quiet ? -4 * fade : -fade, Math.min(fade, goal - level)); // (quick to go quiet for a dance)
			volume = level;
			if (target == 0 && level <= 0) stop();
		}
	}

	/** Every client tick: on = a live match the player is in; intense from the storm circle and players left. */
	public static void tick(boolean on, int stormPhase, int alive) {
		boolean intense = stormPhase >= HARD_PHASE || alive > 0 && alive <= HARD_ALIVE;
		calm = want(calm, "music.battle_1", on && !intense);
		hard = want(hard, "music.battle_2", on && intense);
		if (calm != null || hard != null) Minecraft.getInstance().getMusicManager().stopPlaying(); // (no Minecraft music over it)
	}

	private static @Nullable Loop want(@Nullable Loop loop, String track, boolean play) {
		if (play) {
			var sounds = Minecraft.getInstance().getSoundManager();
			// (a new one when there's none, or the game's sound engine dropped it: not playing a second and a half on)
			if (loop == null || loop.isStopped() || !sounds.isActive(loop) && System.currentTimeMillis() - loop.started > 1500) {
				if (loop != null) sounds.stop(loop);
				loop = new Loop(track);
				sounds.play(loop);
			}
			loop.target = VOLUME;
			return loop;
		}
		if (loop == null) return null;
		loop.target = 0;
		boolean gone = loop.isStopped() || !Minecraft.getInstance().getSoundManager().isActive(loop) && System.currentTimeMillis() - loop.started > 1500;
		return gone ? null : loop;
	}

	public static void stopAll() {
		var sounds = Minecraft.getInstance().getSoundManager();
		if (calm != null) sounds.stop(calm);
		if (hard != null) sounds.stop(hard);
		calm = hard = null;
	}
}
