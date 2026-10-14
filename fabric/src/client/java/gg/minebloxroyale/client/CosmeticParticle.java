package gg.minebloxroyale.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

/**
 * A Locker particle effect drawn in the world exactly like on the Locker preview (LockerParticles): the same particle
 * texture(s), tint, size and drift (up, or down for snow / petals / portal), fading out over its life.
 */
final class CosmeticParticle extends SingleQuadParticle {
	private final LockerParticles.Look look;
	private final int pick;

	private CosmeticParticle(ClientLevel level, double x, double y, double z, LockerParticles.Look look, int rgb, TextureAtlasSprite first) {
		super(level, x, y, z, first);
		this.look = look;
		this.pick = this.random.nextInt(1000);
		this.hasPhysics = false;
		this.gravity = 0;
		this.friction = 1;
		this.xd = 0;
		this.zd = 0;
		this.yd = look.falls() ? -0.02 : 0.015;
		this.lifetime = 20 + this.random.nextInt(20);
		this.quadSize = look.size() / 2;
		if (look.tinted()) setColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
		setSprite(sprite(LockerParticles.frame(look, 0, lifetime, pick)));
	}

	static void spawn(ClientLevel level, double x, double y, double z, LockerParticles.Look look, int rgb) {
		TextureAtlasSprite first = sprite(LockerParticles.frame(look, 0, 1, 0));
		Minecraft.getInstance().particleEngine.add(new CosmeticParticle(level, x, y, z, look, rgb, first));
	}

	private static TextureAtlasSprite sprite(String name) {
		return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(Identifier.withDefaultNamespace(name));
	}

	@Override
	public void tick() {
		super.tick();
		setAlpha(Math.max(0, 1 - age / (float) lifetime));
		if (look.animated()) setSprite(sprite(LockerParticles.frame(look, age, lifetime, pick)));
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}
}
