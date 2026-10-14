package gg.minebloxroyale.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.client.media.SfxBank;
import gg.minebloxroyale.net.Net;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Tab: the full island map, almost full screen but with a margin all round (like Fortnite's). Shows the storm, the next
 * circle, you, and your waypoint; in team modes also your teammates, their waypoints, reboot vans and cards. A left
 * click places (or moves) the waypoint and a click on it removes it; right-click places it too. Mouse wheel zooms
 * (towards the cursor); holding left mouse drags the map while zoomed in. Tab or Esc closes. The island picture comes
 * from the server (IslandMap) once the island is generated.
 */
public class MapScreen extends Screen {
	private static final Identifier TEX = MinebloxRoyale.id("island_map");
	private static final double MAX_ZOOM = 6;
	private static DynamicTexture texture;
	private static int res;
	private static float originX, originZ, span;
	private static boolean ready;
	// The view: zoom (1 = whole island) and the world point at the centre of the map square. Kept between openings.
	private static double zoom = 1, viewX = Double.NaN, viewZ = Double.NaN;
	private double pressX, pressY;
	private boolean dragged, removedOnPress;

	public MapScreen() {
		super(Component.literal("Map"));
	}

	/** The server sent the island picture. */
	public static void receive(Net.IslandMapImage map) {
		Minecraft mc = Minecraft.getInstance();
		NativeImage img = new NativeImage(map.res(), map.res(), false);
		byte[] b = map.rgb();
		for (int i = 0; i < map.res() * map.res(); i++)
			img.setPixel(i % map.res(), i / map.res(), 0xFF000000 | (b[i * 3] & 255) << 16 | (b[i * 3 + 1] & 255) << 8 | (b[i * 3 + 2] & 255));
		if (texture != null) texture.close();
		texture = new DynamicTexture(() -> "minebloxroyale island map", img);
		mc.getTextureManager().register(TEX, texture);
		res = map.res();
		originX = map.originX();
		originZ = map.originZ();
		span = map.span();
		zoom = 1;
		viewX = Double.NaN;
		ready = true;
	}

	/** The map square on screen (GUI pixels): x, y, size. */
	private int[] square() {
		int margin = Math.max(16, this.height / 14);
		int size = Math.min(this.width, this.height) - margin * 2;
		return new int[] { (this.width - size) / 2, (this.height - size) / 2 + 4, size };
	}

	/** Blocks shown across the square right now. */
	private static double visible() {
		return span / zoom;
	}

	/** Keeps the view inside the island picture. */
	private static void clampView() {
		if (Double.isNaN(viewX)) {
			viewX = originX + span / 2.0;
			viewZ = originZ + span / 2.0;
		}
		double half = visible() / 2;
		viewX = Math.max(originX + half, Math.min(originX + span - half, viewX));
		viewZ = Math.max(originZ + half, Math.min(originZ + span - half, viewZ));
	}

	/** World x/z under a GUI point (null outside the square). */
	private double[] worldAt(double mx, double my) {
		int[] sq = square();
		double u = (mx - sq[0]) / sq[2], v = (my - sq[1]) / sq[2];
		if (u < 0 || u > 1 || v < 0 || v > 1) return null;
		clampView();
		return new double[] { viewX + (u - 0.5) * visible(), viewZ + (v - 0.5) * visible() };
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, this.width, this.height, 0x90000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		int[] sq = square();
		int x0 = sq[0], y0 = sq[1], size = sq[2];
		g.fill(x0 - 4, y0 - 4, x0 + size + 4, y0 + size + 4, 0xF0101420);
		g.fill(x0 - 1, y0 - 1, x0 + size + 1, y0 + size + 1, 0xFFB8C4D6);
		g.centeredText(this.font, "MAP  |  Click: waypoint (click it again to remove)  |  Wheel: zoom  |  Hold left: drag (zoomed in)  |  Tab: close", this.width / 2, y0 - 14, 0xFFFFFFFF);
		if (!ready) {
			g.fill(x0, y0, x0 + size, y0 + size, 0xFF2A3A55);
			g.centeredText(this.font, "The island map is still being made...", this.width / 2, y0 + size / 2 - 4, 0xFFB8C4D6);
			return;
		}
		clampView();
		int gs = Math.round(mc.getWindow().getGuiScale());
		int S = size * gs;
		double k = S / visible(); // screen pixels per block
		double vx0 = viewX - visible() / 2, vz0 = viewZ - visible() / 2; // world point at the square's top-left
		g.enableScissor(x0, y0, x0 + size, y0 + size);
		g.pose().pushMatrix();
		g.pose().translate(x0, y0);
		g.pose().scale(1F / gs, 1F / gs);
		// The island picture, scaled and shifted for the zoom (screen pixels).
		int full = (int) Math.round(span * k);
		g.blit(RenderPipelines.GUI_TEXTURED, TEX, (int) Math.round((originX - vx0) * k), (int) Math.round((originZ - vz0) * k), 0, 0, full, full, res, res, res, res);
		// Fortnite-style grid: A-J across, 1-10 down, fixed to the island.
		int line = Math.max(1, gs / 2);
		for (int i = 1; i < 10; i++) {
			int gx = (int) Math.round((originX + span * i / 10.0 - vx0) * k), gy = (int) Math.round((originZ + span * i / 10.0 - vz0) * k);
			if (gx > 0 && gx < S) g.fill(gx, 0, gx + line, S, 0x40FFFFFF);
			if (gy > 0 && gy < S) g.fill(0, gy, S, gy + line, 0x40FFFFFF);
		}
		// Storm, rings, you and your waypoint, smooth in screen pixels.
		var hud = ClientState.hud;
		if (hud.phase().equals("LIVE") || hud.phase().equals("ENDED")) {
			double[] storm = ClientState.storm();
			double sx = (storm[0] - vx0) * k, sz = (storm[1] - vz0) * k;
			// (Fast versions: the big map would otherwise draw tens of thousands of single pixels a frame.)
			Minimap.stormFillFast(g, S, sx, sz, storm[2] * k, Math.max(2, gs));
			Minimap.ringFast(g, S, sx, sz, storm[2] * k, 0x000000, 1.6 * gs / 3.0 * 2);
			Minimap.ringFast(g, S, (hud.nextX() - vx0) * k, (hud.nextZ() - vz0) * k, hud.nextR() * k, 0x000000, 1.2 * gs / 3.0 * 2);
		}
		if (Waypoint.isSet()) {
			double wx = (Waypoint.x() - vx0) * k, wz = (Waypoint.z() - vz0) * k;
			Minimap.ring(g, S, wx, wz, 4.0 * gs / 3.0 * 2, 0xFF3030, 3.0 * gs / 3.0 * 2);
			Minimap.ring(g, S, wx, wz, 1.2 * gs / 3.0 * 2, 0xFF3030, 2.4 * gs / 3.0 * 2);
		}
		Minimap.teamMarks(g, S, k, vx0, vz0, gs); // team modes: vans, cards, teammates and their waypoints
		double yaw = Math.toRadians(mc.player.getYRot());
		Minimap.arrow(g, (mc.player.getX() - vx0) * k, (mc.player.getZ() - vz0) * k, -Math.sin(yaw), Math.cos(yaw), gs, ClientState.myColor());
		g.pose().popMatrix();
		// Grid letters and numbers along the edges, for the columns and rows in view.
		for (int i = 0; i < 10; i++) {
			int cx = x0 + (int) Math.round((originX + span * (i + 0.5) / 10.0 - vx0) * k / gs);
			int cy = y0 + (int) Math.round((originZ + span * (i + 0.5) / 10.0 - vz0) * k / gs);
			if (cx > x0 + 4 && cx < x0 + size - 4) g.centeredText(this.font, String.valueOf((char) ('A' + i)), cx, y0 + 2, 0xC0FFFFFF);
			if (cy > y0 + 6 && cy < y0 + size - 6) g.text(this.font, String.valueOf(i + 1), x0 + 2, cy - 4, 0xC0FFFFFF, true);
		}
		g.disableScissor();
		if (zoom > 1.01) g.text(this.font, String.format(Locale.ROOT, "%.1fx", zoom), x0 + size - 26, y0 + size - 11, 0xC0FFFFFF, true);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (!ready || scrollY == 0) return super.mouseScrolled(mx, my, scrollX, scrollY);
		double[] before = worldAt(mx, my);
		zoom = Math.max(1, Math.min(MAX_ZOOM, zoom * (scrollY > 0 ? 1.25 : 0.8)));
		// Zoom towards the cursor: the point under it stays under it.
		if (before != null) {
			int[] sq = square();
			double u = (mx - sq[0]) / sq[2], v = (my - sq[1]) / sq[2];
			viewX = before[0] - (u - 0.5) * visible();
			viewZ = before[1] - (v - 0.5) * visible();
		}
		clampView();
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double[] w = ready ? worldAt(event.x(), event.y()) : null;
		if (w != null) {
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
				// Right-click: place (or move) your waypoint.
				Waypoint.set(w[0], w[1]);
				click();
			} else if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				pressX = event.x();
				pressY = event.y();
				dragged = false;
				// A click on your waypoint removes it straight away (on the press, so a drag can't get in the way).
				double near = 10 * visible() / square()[2]; // 10 GUI pixels, in blocks
				removedOnPress = Waypoint.isSet() && Math.hypot(w[0] - Waypoint.x(), w[1] - Waypoint.z()) <= near;
				if (removedOnPress) {
					Waypoint.clear();
					click();
				}
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (ready && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			// Dragging only moves the map when zoomed in (at 1x the whole island is already in view).
			if (zoom > 1.01 && Math.abs(event.x() - pressX) + Math.abs(event.y() - pressY) > 3) dragged = true;
			if (dragged) {
				double blocksPerGui = visible() / square()[2];
				viewX -= dx * blocksPerGui;
				viewZ -= dy * blocksPerGui;
				clampView();
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (ready && event.button() == InputConstants.MOUSE_BUTTON_LEFT && !dragged && !removedOnPress) {
			// A left click (not a drag, not on the waypoint): places (or moves) your waypoint.
			double[] w = worldAt(event.x(), event.y());
			if (w != null) {
				Waypoint.set(w[0], w[1]);
				click();
				dragged = false;
				return true;
			}
		}
		dragged = false;
		removedOnPress = false;
		return super.mouseReleased(event);
	}

	private static void click() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) SfxBank.play("ui_click", mc.player.position());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (Minecraft.getInstance().options.keyPlayerList.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
