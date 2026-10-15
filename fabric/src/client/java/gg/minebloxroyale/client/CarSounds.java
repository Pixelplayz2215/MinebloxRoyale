package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.match.CarEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Cars' engines and tyres (2.0.0-test3, creator: "Cars should have a driving animation (also sounds)"): each car near you
 * with someone in it hums its engine (the car.engine loop, its pitch and loudness rising with its speed, louder still
 * boosting), and its tyres squeal (car.skid) taking a turn fast. How it's moving comes from CarEntityRenderer, which
 * works that out each frame; the boost and the doors are one-off sounds (CarEntity, CarEntityRenderer).
 */
public final class CarSounds {
	private static final SoundEvent ENGINE = SoundEvent.createVariableRangeEvent(MinebloxRoyale.id("car.engine")),
		SKID = SoundEvent.createVariableRangeEvent(MinebloxRoyale.id("car.skid"));
	/** How far off a car's engine is heard (blocks). */
	private static final double RANGE = 48;
	private static final Map<Integer, Loop[]> PLAYING = new HashMap<>();

	private CarSounds() {
	}

	/** Each client tick: a sound for every car near you that someone's in; what's gone stops itself. */
	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			PLAYING.clear();
			return;
		}
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof CarEntity car) || car.getPassengers().isEmpty() || car.distanceToSqr(mc.player) > RANGE * RANGE) continue;
			Loop[] loops = PLAYING.get(car.getId());
			if (loops != null && !loops[0].isStopped()) continue;
			loops = new Loop[] { new Loop(car, false), new Loop(car, true) };
			PLAYING.put(car.getId(), loops);
			for (Loop l : loops) mc.getSoundManager().play(l);
		}
		PLAYING.values().removeIf(l -> l[0].isStopped());
	}

	/** A car's engine (or its tyres) going round and round, following it, changing with how it moves. */
	private static final class Loop extends AbstractTickableSoundInstance {
		private final CarEntity car;
		private final boolean tyres;

		Loop(CarEntity car, boolean tyres) {
			super(tyres ? SKID : ENGINE, SoundSource.NEUTRAL, RandomSource.create());
			this.car = car;
			this.tyres = tyres;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01F;
			this.pitch = 1;
			this.x = car.getX();
			this.y = car.getY();
			this.z = car.getZ();
		}

		@Override
		public void tick() {
			Minecraft mc = Minecraft.getInstance();
			if (car.isRemoved() || car.getPassengers().isEmpty() || mc.player == null || car.distanceToSqr(mc.player) > RANGE * RANGE * 1.2) {
				stop();
				return;
			}
			x = car.getX();
			y = car.getY() + 0.6;
			z = car.getZ();
			float[] m = CarEntityRenderer.motion(car.getId());
			float speed = Math.abs(m[0]), fast = Math.min(1, speed / 17F); // (blocks a second; boosting is about 17)
			if (tyres) {
				float squeal = Math.max(0, Math.abs(m[1]) - 0.45F) / 0.55F * Math.min(1, speed / 8F);
				volume += (squeal * 0.55F - volume) * 0.3F;
				pitch = 0.9F + 0.2F * fast;
				return;
			}
			float want = 0.35F + 0.5F * fast + (car.boosting() ? 0.25F : 0);
			volume += (want - volume) * 0.25F;
			pitch += (0.55F + 1.35F * fast + (car.boosting() ? 0.15F : 0) - pitch) * 0.25F;
		}
	}
}
