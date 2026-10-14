package gg.minebloxroyale.client;

import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.client.avatar.Cosmetics;
import gg.minebloxroyale.sheets.CosmeticDef;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Locker particle effects: one small particle every 'rate' ticks around a player wearing one (fewer than a Minecraft
 * potion effect gives off). Not shown while riding the Battle Bus or spectating.
 */
public final class CosmeticParticles {
	private CosmeticParticles() {
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) return;
		// Players, and bots near you (they wear particles too).
		List<LivingEntity> wearers = new ArrayList<>(mc.level.players());
		if (mc.player != null)
			for (Entity e : mc.level.entitiesForRendering())
				if (e instanceof BotEntity b && b.distanceToSqr(mc.player) < 48 * 48 && !b.outfit().isEmpty()) wearers.add(b);
		for (LivingEntity p : wearers) {
			if (p.isSpectator() || p.isInvisible() || p.getVehicle() != null) continue;
			if (p == mc.player && mc.gui.screen() instanceof LockerScreen) continue; // the Locker shows them on its preview instead
			CosmeticDef fx = Cosmetics.worn(Cosmetics.OUTFITS.get(p.getUUID()), "particle");
			if (fx == null || fx.rate() <= 0 || (p.tickCount + p.getId()) % fx.rate() != 0) continue;
			var r = mc.level.getRandom();
			double a = r.nextDouble() * Math.PI * 2, d = 0.45 + r.nextDouble() * 0.25;
			if (LockerParticles.SAME_AS_MENU.contains(fx.shape())) { // drawn exactly like on the Locker preview
				LockerParticles.Look look = LockerParticles.look(fx.shape());
				double y = p.getY() + (look.falls() ? 0.9 + r.nextDouble() * 1.2 : 0.4 + r.nextDouble() * 1.5);
				CosmeticParticle.spawn(mc.level, p.getX() + Math.cos(a) * d, y, p.getZ() + Math.sin(a) * d, look, fx.color());
				continue;
			}
			if (!(BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.withDefaultNamespace(fx.shape())) instanceof SimpleParticleType type)) continue;
			mc.level.addParticle(type, p.getX() + Math.cos(a) * d, p.getY() + 0.4 + r.nextDouble() * 1.5, p.getZ() + Math.sin(a) * d, 0, 0.015, 0);
		}
	}
}
