package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.sheets.NameBadgeDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A badge on its own line over some players' name tags, for everyone to see (2.0.0, creator: "Give creaper89O1 a Beta
 * Tester badge in the nametag / Give Pixel2215YT a Owner Badge in the nametag", then option C of Mockups\Name Badges:
 * "Badge on it's own line over the name tag. The nametag doesnt change"). Who has which is in the name_badges sheet, by
 * Minecraft account id only, so nobody gets one by taking the name. The badges are pictures in the font
 * minebloxroyale:name_badges (fabric/tools/branding/make_name_badges.py), drawn like a name tag but with no dark box
 * behind them (EntityRendererMixin).
 */
public final class NameBadge {
	/** The badges, in the font's order (make_name_badges.py BADGES). */
	public static final List<String> IDS = List.of("owner", "beta_tester");
	/** The badge over this player's name tag ("" for none), set as the frame's state is made (LivingEntityRendererMixin). */
	public static final RenderStateDataKey<String> KEY = RenderStateDataKey.create(() -> "minebloxroyale:name_badge");
	private static final FontDescription FONT = new FontDescription.Resource(MinebloxRoyale.id("name_badges"));
	/**
	 * In the development game only (never in a package), the Dev and Friend clients show them, so both can be seen in a
	 * two-player test: their offline account ids aren't the real ones.
	 */
	private static final Map<String, String> DEV = Map.of("MinebloxDev", "owner", "BRFriend", "beta_tester");

	private NameBadge() {
	}

	/** This player's badge ("" for none). */
	public static String of(Player player) {
		String uuid = player.getUUID().toString();
		for (NameBadgeDef b : Sheets.NAME_BADGES) if (b.uuid().equalsIgnoreCase(uuid)) return b.badge();
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) return DEV.getOrDefault(player.getGameProfile().name(), "");
		return "";
	}

	/**
	 * The badge one line over the name tag (the pose already where the name tag went), drawn as Minecraft draws a name
	 * tag (SubmitNodeCollection.submitNameTag: facing the camera, also faintly through walls unless sneaking) but with no
	 * dark box behind it.
	 */
	public static void submit(String badge, PoseStack poseStack, SubmitNodeCollector collector, Vec3 attachment, int offset, boolean seeThrough, int light,
		CameraRenderState camera) {
		int i = IDS.indexOf(badge);
		if (i < 0) return;
		Component text = Component.literal(String.valueOf((char) (0xE000 + i))).withStyle(s -> s.withFont(FONT).withColor(0xFFFFFF));
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		poseStack.pushPose();
		poseStack.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F); // (one line up, as Minecraft puts the name over a score)
		poseStack.translate(attachment.x, attachment.y + 0.5, attachment.z);
		poseStack.rotate(camera.orientation);
		poseStack.scale(0.025F, -0.025F, 0.025F);
		float x = -font.width(text) / 2.0F;
		float backgroundAlpha = mc.gameRenderer.gameRenderState().optionsRenderState.getBackgroundOpacity(0.25F);
		int faint = ARGB.color(Math.max((backgroundAlpha + 0.75F) * 0.5F, 0.5F), -1);
		FormattedCharSequence chars = text.getVisualOrderText();
		if (seeThrough) {
			collector.submitText(poseStack, x, offset, chars, false, Font.DisplayMode.NORMAL, LightCoordsUtil.lightCoordsWithEmission(light, 2), -1, 0, 0);
			collector.submitText(poseStack, x, offset, chars, false, Font.DisplayMode.SEE_THROUGH, light, faint, 0, 0);
		} else {
			collector.submitText(poseStack, x, offset, chars, false, Font.DisplayMode.NORMAL, light, faint, 0, 0);
		}
		poseStack.popPose();
	}
}
