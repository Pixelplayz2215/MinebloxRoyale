package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * (2.1.0) Sounds that go on round you, made by make_audio.py: the wind while you skydive or glide (creator: skydiving
 * sounds): sky.wind, louder and higher the faster you fall, louder still diving head down; softer under the glider. (The
 * whoosh out of the bus and into a dive, and the glider opening and closing, are one-off sounds: DropControl.) It is a
 * looping sound that fades in and out rather than starting and stopping.
 * <p>
 * (2.1.0-test5, creator: "the whirling sounds is annoying when it closes in. just make the warning out urgent, remove the
 * whirling sound the storm makes": the storm's own loop while it closed in is gone; the storm_warning played as each
 * phase starts to shrink is the urgent one now.)
 */
public final class AmbientSounds {
	private static final SoundEvent WIND = SoundEvent.createVariableRangeEvent(MinebloxRoyale.id("sky.wind"));
	private static Loop wind;

	private AmbientSounds() {
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			wind = null;
			return;
		}
		wind = keep(mc, wind, WIND, windWant(mc));
	}

	/** The loop, started when it's wanted and not playing; told how loud and how high to be. */
	private static Loop keep(Minecraft mc, Loop loop, SoundEvent ev, float[] want) {
		if (loop != null && loop.isStopped()) loop = null;
		if (loop == null && want[0] > 0) {
			loop = new Loop(ev);
			mc.getSoundManager().play(loop);
		}
		if (loop != null) {
			loop.wantVolume = want[0];
			loop.wantPitch = want[1];
		}
		return loop;
	}

	/** {volume, pitch} for the wind: falling fast is loud and high; head down a little more; the glider softer. */
	private static float[] windWant(Minecraft mc) {
		DropControl.Phase phase = DropControl.phase();
		if (phase != DropControl.Phase.SKYDIVE && phase != DropControl.Phase.GLIDE) return new float[] { 0, 1 };
		var v = mc.player.getDeltaMovement();
		float fall = (float) Math.max(0, -v.y), across = (float) Math.hypot(v.x, v.z);
		if (phase == DropControl.Phase.GLIDE)
			return new float[] { 0.22F + 0.25F * Math.min(1, across / 0.6F), 0.78F + 0.12F * Math.min(1, across / 0.6F) };
		float fast = Math.min(1, fall / Math.max(0.1F, MinebloxRoyale.setting("skydiveFall")));
		float down = Mth.clamp((mc.player.getXRot() - 20) / 60F, 0, 1); // (looking down: diving)
		return new float[] { 0.3F + 0.5F * fast + 0.2F * down, 0.85F + 0.25F * fast + 0.1F * down };
	}

	/** A looping sound in your head (not placed in the world), easing to the volume and pitch it's told. */
	private static final class Loop extends AbstractTickableSoundInstance {
		float wantVolume, wantPitch = 1;

		Loop(SoundEvent ev) {
			super(ev, SoundSource.AMBIENT, RandomSource.create());
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01F;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		@Override
		public void tick() {
			volume += (wantVolume - volume) * 0.12F;
			pitch += (wantPitch - pitch) * 0.12F;
			if (wantVolume <= 0 && volume < 0.02F) stop();
		}
	}
}
