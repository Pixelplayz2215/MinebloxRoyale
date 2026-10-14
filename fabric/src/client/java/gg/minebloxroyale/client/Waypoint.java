package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.minebloxroyale.net.Net;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Your own map marker (placed on the full map, Tab): a red beacon beam, a red mark on the compass bar and an on-screen
 * marker with the distance. In Solo it stays private. In Duos, Trios and Squads it's shared with your teammates, and
 * theirs show on your screen in their teammate colours, like Fortnite's (creator): map, beacon and compass bar. Your
 * team's reboot cards lying on the ground get a cyan-blue one each, marked CARD (creator: a colour no teammate has).
 */
public final class Waypoint {
	/** Cyan-blue, for reboot cards: not a teammate colour (yellow, orange, pink and bright cyan). */
	static final int CARD = 0xFF1E90FF;
	private static Double x, z;

	private Waypoint() {
	}

	public static boolean isSet() {
		return x != null;
	}

	public static void set(double wx, double wz) {
		x = wx;
		z = wz;
		if (ClientState.inMatch()) ClientPlayNetworking.send(new Net.Action(String.format(Locale.ROOT, "waypoint:%.1f:%.1f", wx, wz)));
	}

	public static void clear() {
		if (x != null && ClientState.inMatch()) ClientPlayNetworking.send(new Net.Action("waypoint:clear"));
		x = null;
		z = null;
	}

	/** Your team's reboot cards on the ground (team modes): {x, z, colour} each. */
	static List<double[]> cards() {
		List<double[]> out = new ArrayList<>();
		if (!ClientState.teamMatch()) return out;
		for (Net.CardMark c : ClientState.squad.cards()) out.add(new double[] { c.x(), c.z(), CARD });
		return out;
	}

	/** Teammates' waypoints: {x, z, colour} each. */
	static List<double[]> teammates() {
		List<double[]> out = new ArrayList<>();
		if (!ClientState.teamMatch()) return out;
		for (Net.Mate m : ClientState.squad.mates()) if (m.waypoint()) out.add(new double[] { m.wx(), m.wz(), ClientState.teamColor(m.slot()) });
		return out;
	}

	private static double groundY(Minecraft mc, double wx, double wz) {
		int bx = (int) Math.floor(wx), bz = (int) Math.floor(wz);
		if (mc.level.hasChunk(bx >> 4, bz >> 4)) return mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
		return mc.player.getY();
	}

	/** Compass bearing from the player to a point: 0 = north, 90 = east. */
	public static float bearingTo(Minecraft mc, double wx, double wz) {
		double dx = wx - mc.player.getX(), dz = wz - mc.player.getZ();
		return (float) ((Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360);
	}

	public static double x() {
		return x;
	}

	public static double z() {
		return z;
	}

	/** Compass bearing to the marker: 0 = north, 90 = east (same as the compass bar). */
	public static float bearing(Minecraft mc) {
		return bearingTo(mc, x, z);
	}

	/**
	 * The red beacon beam (yours) and your teammates' in their colours: a bright core and a soft glow, ground to sky.
	 */
	static void beacon(PoseStack ps, SubmitNodeCollector out, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		List<double[]> beams = new ArrayList<>(teammates());
		beams.addAll(cards());
		if (isSet()) beams.add(new double[] { x, z, 0xFFFF2A2A });
		if (beams.isEmpty()) return;
		out.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(WorldVisuals.white()), (pose, vc) -> {
			for (double[] b : beams) {
				int rgb = (int) b[2] & 0xFFFFFF;
				double y0 = groundY(mc, b[0], b[1]);
				column(vc, pose, cam, b[0], b[1], 0.25, y0, 330, 0xE0000000 | rgb);
				column(vc, pose, cam, b[0], b[1], 0.7, y0, 330, 0x50000000 | rgb);
			}
		});
	}

	private static void column(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, double x, double z, double r, double y0, double y1, int color) {
		double[][] c = { { x - r, z - r }, { x + r, z - r }, { x + r, z + r }, { x - r, z + r } };
		for (int i = 0; i < 4; i++) {
			double[] a = c[i], b = c[(i + 1) % 4];
			float nx = (float) Math.signum(((a[0] + b[0]) / 2) - x), nz = (float) Math.signum(((a[1] + b[1]) / 2) - z);
			WorldVisuals.quad(vc, pose, cam, a[0], a[1], b[0], b[1], y0, y1, nx, nz, color);
		}
	}

	/** World point -> GUI position {x, y, depth}; depth < 0 means behind the camera (x tells which side). */
	public static double[] project(Minecraft mc, GuiGraphicsExtractor g, double x, double y, double z) {
		Camera cam = mc.gameRenderer.mainCamera();
		Vec3 p = cam.position();
		double dx = x - p.x, dy = y - p.y, dz = z - p.z;
		Vector3fc f = cam.forwardVector(), u = cam.upVector(), l = cam.leftVector();
		double xc = -(dx * l.x() + dy * l.y() + dz * l.z()), yc = dx * u.x() + dy * u.y() + dz * u.z(), zc = dx * f.x() + dy * f.y() + dz * f.z();
		int w = g.guiWidth(), h = g.guiHeight();
		if (zc <= 0.1) return new double[] { xc >= 0 ? w : 0, h / 2.0, -1 };
		double focal = (h / 2.0) / Math.tan(Math.toRadians(cam.getFov()) / 2);
		return new double[] { w / 2.0 + xc / zc * focal, h / 2.0 - yc / zc * focal, zc };
	}

	/**
	 * On-screen markers: yours (red), your teammates' (their colours) and your team's reboot cards, each with the
	 * distance in metres.
	 */
	static void screenMarker(GuiGraphicsExtractor g, Minecraft mc) {
		for (double[] t : teammates()) marker(g, mc, t[0], t[1], (int) t[2], "");
		for (double[] c : cards()) marker(g, mc, c[0], c[1], CARD, "CARD ");
		if (isSet()) marker(g, mc, x, z, 0xFFFF3030, "");
	}

	private static void marker(GuiGraphicsExtractor g, Minecraft mc, double x, double z, int color, String label) {
		int w = g.guiWidth(), h = g.guiHeight();
		double[] s = project(mc, g, x, groundY(mc, x, z) + 2, z);
		double sx = s[0], sy = s[1];
		if (s[2] < 0) sx = s[0] >= w / 2.0 ? w : 0; // behind you: pin it to the side it's on
		int mx = (int) Math.max(14, Math.min(w - 14, sx)), my = (int) Math.max(24, Math.min(h - 40, sy));
		// Red diamond with a dark outline.
		for (int i = -5; i <= 5; i++) {
			int half = 5 - Math.abs(i);
			g.fill(mx - half - 1, my + i, mx + half + 2, my + i + 1, 0xC0101010);
		}
		for (int i = -4; i <= 4; i++) {
			int half = 4 - Math.abs(i);
			g.fill(mx - half, my + i, mx + half + 1, my + i + 1, color);
		}
		g.centeredText(mc.font, label + Math.round(Math.hypot(x - mc.player.getX(), z - mc.player.getZ())) + "m", mx, my + 8, label.isEmpty() ? 0xFFFFFFFF : color);
	}
}
