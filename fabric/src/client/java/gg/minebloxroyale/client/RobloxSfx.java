package gg.minebloxroyale.client;

import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.client.media.SfxBank;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Roblox's jump, landing and plastic footstep sounds for every avatar near you (sounds sheet). */
public final class RobloxSfx {
	private record Track(boolean onGround, double stepDist) {
	}

	private static final Map<Integer, Track> LAST = new HashMap<>();

	private RobloxSfx() {
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			LAST.clear();
			return;
		}
		Map<Integer, Track> seen = new HashMap<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof Player || e instanceof BotEntity) || e.isSpectator() || e.distanceToSqr(mc.player) > 32 * 32) continue;
			LivingEntity le = (LivingEntity) e;
			Track prev = LAST.get(e.getId());
			double step = prev == null ? 0 : prev.stepDist();
			boolean ground = le.onGround();
			if (prev != null) {
				if (prev.onGround() && !ground && le.getDeltaMovement().y > 0.2) SfxBank.play("jump", le.position());
				if (!prev.onGround() && ground) SfxBank.play("land", le.position());
			}
			if (ground) {
				double dx = le.getX() - le.xo, dz = le.getZ() - le.zo;
				step += Math.sqrt(dx * dx + dz * dz);
				// Roblox's footstep file is a ~2.5 s loop of steps: start it again after ~5 blocks of walking.
				if (step > 5.0) {
					step = 0;
					if (!ClientState.inMatch()) SfxBank.play("step", le.position());
				}
			}
			seen.put(e.getId(), new Track(ground, step));
		}
		LAST.clear();
		LAST.putAll(seen);
	}
}
