package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.bot.BotEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Bots use the humanoid pipeline (held gun, name tag); the body itself is swapped for the Roblox avatar by the mixin. A
 * boss has its bar over its name.
 */
public class BotRenderer extends HumanoidMobRenderer<BotEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier FALLBACK = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
	/** A boss's bar: {health, most health, shield, most shield}; other bots have none. */
	private static final RenderStateDataKey<float[]> BOSS_BAR = RenderStateDataKey.create(() -> "minebloxroyale:boss_bar");
	private static final int FULL_BRIGHT = 0xF000F0;

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

	@Override
	public void extractRenderState(BotEntity bot, HumanoidRenderState state, float partialTicks) {
		super.extractRenderState(bot, state, partialTicks);
		float shield = bot.bossShield();
		if (shield >= 0) state.setData(BOSS_BAR, new float[] { bot.getHealth(), bot.getMaxHealth(), shield, MinebloxRoyale.setting("maxShield") });
	}

	@Override
	public void submit(HumanoidRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		float[] bar = state.getDataOrDefault(BOSS_BAR, null);
		if (bar != null && state.nameTag != null && state.nameTagAttachment != null) bossBar(state.nameTagAttachment, bar, poseStack, collector, camera);
	}

	/**
	 * A boss's bar over its name tag (2.0.0-test3, creator: "Boss's should have a healthbar above the nametag"), Fortnite
	 * style: its shield (blue) over its health (green), each on a dark track, facing you like the name. In the name tag's
	 * own units (its letters 9 high, y down): the name sits from 0 to 9, the bar just above it.
	 */
	private static void bossBar(Vec3 at, float[] bar, PoseStack ps, SubmitNodeCollector out, CameraRenderState camera) {
		float health = Mth.clamp(bar[0] / Math.max(1, bar[1]), 0, 1), shield = Mth.clamp(bar[2] / Math.max(1, bar[3]), 0, 1);
		ps.pushPose();
		ps.translate(at.x, at.y + 0.5, at.z); // (where Minecraft puts the name)
		ps.rotate(camera.orientation);
		ps.scale(0.025F, -0.025F, 0.025F);
		out.submitCustomGeometry(ps, RenderTypes.text(WorldVisuals.white()), (pose, vc) -> {
			// each layer a little nearer you than the one under it, so they never fight over who shows
			rect(vc, pose, -22, -13.5F, 22, -2, 0, 0xB0000000);
			rect(vc, pose, -20, -11.5F, 20, -8, 0.5F, 0xFF16202E);
			rect(vc, pose, -20, -6.5F, 20, -4, 0.5F, 0xFF16202E);
			if (shield > 0) rect(vc, pose, -20, -11.5F, -20 + 40 * shield, -8, 1, 0xFF2E8CFF);
			if (health > 0) rect(vc, pose, -20, -6.5F, -20 + 40 * health, -4, 1, 0xFF3DD64C);
		});
		ps.popPose();
	}

	private static void rect(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z, int color) {
		vc.addVertex(pose, x0, y0, z).setColor(color).setUv(0, 0).setLight(FULL_BRIGHT);
		vc.addVertex(pose, x0, y1, z).setColor(color).setUv(0, 1).setLight(FULL_BRIGHT);
		vc.addVertex(pose, x1, y1, z).setColor(color).setUv(1, 1).setLight(FULL_BRIGHT);
		vc.addVertex(pose, x1, y0, z).setColor(color).setUv(1, 0).setLight(FULL_BRIGHT);
	}
}
