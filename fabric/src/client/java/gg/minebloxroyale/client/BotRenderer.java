package gg.minebloxroyale.client;

import gg.minebloxroyale.bot.BotEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Bots use the humanoid pipeline (held gun, name tag); the body itself is swapped for the Roblox avatar by the mixin.
 */
public class BotRenderer extends HumanoidMobRenderer<BotEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier FALLBACK = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

	public BotRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return FALLBACK;
	}
}
