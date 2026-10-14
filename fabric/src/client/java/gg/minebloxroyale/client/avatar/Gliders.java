package gg.minebloxroyale.client.avatar;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.minebloxroyale.client.WorldVisuals;
import gg.minebloxroyale.client.mesh.Mesh;
import gg.minebloxroyale.sheets.GliderDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * The 46 gliders (gliders sheet), each a Blender model (fabric/tools/blender/gliders.py: nine shapes, each glider's own
 * decoration, bigger than before), drawn above a gliding player in Roblox root space on the frame its raised hands hold
 * (RobloxAvatarRenderer), in the glider's two colours; the lighter copy far away.
 */
final class Gliders {
	private Gliders() {
	}

	/** The player's chosen glider; bots (and players who never picked) get one of the free ones. */
	static GliderDef of(Map<String, String> outfit, int entityId) {
		String id = outfit == null ? null : outfit.get("glider");
		GliderDef g = id == null ? null : Sheets.GLIDERS_BY_ID.get(id);
		if (g != null) return g;
		List<GliderDef> free = Sheets.GLIDERS.stream().filter(x -> x.unlock().equals("default")).toList();
		// No glider picked (bots, new players): one of the free ones (bots get a different one each).
		return !free.isEmpty() ? free.get(Math.floorMod(entityId, free.size())) : Sheets.GLIDERS.get(0);
	}

	static void submit(SubmitNodeCollector collector, PoseStack poseStack, GliderDef g, boolean far, int light, int overlay) {
		Mesh mesh = Mesh.get(g.id() + (far ? "_far" : ""));
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()),
			(pose, vc) -> mesh.draw(pose, RobloxAvatarRenderer.ROOT, vc, light, overlay, g.color() & 0xFFFFFF, g.color2() & 0xFFFFFF));
	}
}
