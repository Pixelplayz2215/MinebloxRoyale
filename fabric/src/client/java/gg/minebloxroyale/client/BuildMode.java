package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.minebloxroyale.match.Builds;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.BuildPieceDef;
import gg.minebloxroyale.sheets.MaterialDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Fortnite build mode (key G, builds sheet). While on: number keys 1-3 pick Wall / Floor / Ramp, left mouse places
 * (hold to keep placing), right mouse switches Wood / Brick / Metal, and a blue ghost shows where the piece goes.
 * G again goes back to weapons, on the slot you had.
 */
public final class BuildMode {
	private static boolean active, useWasDown;
	private static int savedSlot, material, placeCooldown;

	private BuildMode() {
	}

	public static boolean active() {
		return active;
	}

	private static List<BuildPieceDef> pieces() {
		return Sheets.BUILDS.stream().sorted(Comparator.comparingInt(BuildPieceDef::slot)).toList();
	}

	public static BuildPieceDef piece() {
		Minecraft mc = Minecraft.getInstance();
		List<BuildPieceDef> all = pieces();
		return all.get(Math.min(all.size() - 1, mc.player.getInventory().getSelectedSlot()));
	}

	public static MaterialDef material() {
		return Sheets.MATERIALS.get(material % Sheets.MATERIALS.size());
	}

	public static void toggle(Minecraft mc) {
		if (mc.player == null) return;
		if (!active) {
			if (!ClientState.inMatch() || mc.player.isSpectator()) return;
			active = true;
			savedSlot = mc.player.getInventory().getSelectedSlot();
			mc.player.getInventory().setSelectedSlot(0);
		} else {
			exit(mc);
		}
	}

	private static void exit(Minecraft mc) {
		active = false;
		if (mc.player != null) mc.player.getInventory().setSelectedSlot(savedSlot);
	}

	public static Builds.Placement target(Minecraft mc) {
		var p = mc.player;
		return Builds.target(piece(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
	}

	/** Every client tick (after Minecraft has handled its keys). */
	public static void tick(Minecraft mc) {
		if (!active) return;
		if (mc.player == null || !ClientState.inMatch() || mc.player.isSpectator()) {
			active = false;
			return;
		}
		// Only as many hotbar slots as there are pieces.
		int max = pieces().size() - 1;
		if (mc.player.getInventory().getSelectedSlot() > max) mc.player.getInventory().setSelectedSlot(max);
		if (mc.gui.screen() != null) return;
		boolean use = mc.options.keyUse.isDown();
		if (use && !useWasDown) material = (material + 1) % Sheets.MATERIALS.size();
		useWasDown = use;
		while (mc.options.keyAttack.consumeClick()) {
			placeCooldown = 0; // a fresh click places straight away
		}
		if (placeCooldown > 0) placeCooldown--;
		if (mc.options.keyAttack.isDown() && placeCooldown == 0) {
			Builds.Placement t = target(mc);
			ClientPlayNetworking.send(new Net.Action("build:" + piece().id() + ":" + material().id() + ":" + t.tx() + ":" + t.ty() + ":" + t.tz() + ":" + t.dir().getName()));
			placeCooldown = 4; // holding the button keeps building, like Fortnite's turbo build
		}
	}

	/** The ghost of the piece you'd place: blue if you have the materials, red if not. */
	static void preview(PoseStack ps, SubmitNodeCollector out, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		if (!active || mc.player == null || mc.level == null) return;
		Map<BlockPos, BlockState> shape = Builds.shape(piece(), material(), target(mc));
		boolean afford = Builds.materialCount(mc.player, material().id()) >= piece().cost();
		int col = afford ? 0x704C9BFF : 0x70FF4040;
		out.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(WorldVisuals.white()), (pose, vc) -> {
			for (BlockPos b : shape.keySet()) {
				BlockState there = mc.level.getBlockState(b);
				if (!there.isAir() && !there.canBeReplaced()) continue;
				double x0 = b.getX() + 0.02, x1 = b.getX() + 0.98, y0 = b.getY() + 0.02, y1 = b.getY() + 0.98, z0 = b.getZ() + 0.02, z1 = b.getZ() + 0.98;
				WorldVisuals.quad(vc, pose, cam, x0, z0, x1, z0, y0, y1, 0, -1, col);
				WorldVisuals.quad(vc, pose, cam, x1, z1, x0, z1, y0, y1, 0, 1, col);
				WorldVisuals.quad(vc, pose, cam, x0, z1, x0, z0, y0, y1, -1, 0, col);
				WorldVisuals.quad(vc, pose, cam, x1, z0, x1, z1, y0, y1, 1, 0, col);
			}
		});
	}

	/** Build bar above the hotbar: the pieces, the material and how much of it you have. */
	static void hud(GuiGraphicsExtractor g, Minecraft mc) {
		if (!active) return;
		StringBuilder s = new StringBuilder("BUILD   ");
		BuildPieceDef current = piece();
		for (BuildPieceDef p : pieces()) s.append(p == current ? "[" + p.slot() + " " + p.name().toUpperCase(Locale.ROOT) + "]" : " " + p.slot() + " " + p.name() + " ").append("  ");
		MaterialDef m = material();
		s.append("|  ").append(m.name()).append(" ").append(Builds.materialCount(mc.player, m.id())).append(" (right-click: switch)  |  G: weapons");
		String text = s.toString();
		int w = mc.font.width(text) + 16, x = g.guiWidth() / 2 - w / 2, y = g.guiHeight() - 62;
		g.fill(x, y, x + w, y + 14, 0xC0101420);
		g.fill(x, y + 13, x + w, y + 14, 0xFF000000 | m.color());
		g.centeredText(mc.font, text, g.guiWidth() / 2, y + 3, 0xFFFFFFFF);
	}
}
