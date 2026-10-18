package gg.minebloxroyale.client;

import com.mojang.blaze3d.platform.InputConstants;
import gg.minebloxroyale.client.avatar.Cosmetics;
import gg.minebloxroyale.client.avatar.RobloxAvatarRenderer;
import gg.minebloxroyale.client.media.SfxBank;
import gg.minebloxroyale.hub.Locker;
import gg.minebloxroyale.hub.UnlockRule;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.GliderDef;
import gg.minebloxroyale.sheets.PresetDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * K (outside matches): the Locker, a Roblox-style catalog. Tabs: Presets (whole outfits), Hats, Hair, Face, Back,
 * Shirts, Pants, Shoes, Particles, Emotes (pick the 8 on your B wheel; hover one to see it) and Gliders. Click an item
 * to wear it (again, or "None", to take it off); locked items don't show their names, only what unlocks them (hover one
 * for your progress). While it's open your avatar in the world is hidden and a big copy of you stands on the right:
 * hold right mouse and drag to turn it round (left and right only); your particle effect floats around it (the same
 * particles as in the world). Everything is purely cosmetic.
 */
public class LockerScreen extends Screen {
	private static final String[][] TABS = { { "preset", "Presets" }, { "hat", "Hats" }, { "hair", "Hair" }, { "face", "Face" }, { "back", "Back" },
		{ "shirt", "Shirts" }, { "pants", "Pants" }, { "shoes", "Shoes" }, { "particle", "Particles" }, { "emote", "Emotes" }, { "glider", "Gliders" } };
	private static final int PANEL = 350, COLS = 3, W = 80;
	/** True only while the Locker draws its preview of you (the in-world you is hidden while the Locker is open). */
	public static boolean previewing;
	static String tab = "preset";
	private static int page;
	static float previewYaw;
	/** Preview frames drawn so far (LockerAudit waits for new ones before each screenshot). */
	static int frames;
	/** Emote buttons on this page (button, emote id): hovering an unlocked one plays it on the preview. */
	private final List<Map.Entry<Button, String>> emoteButtons = new ArrayList<>();
	/** Locked buttons on this page (button, what unlocks it): shown under the grid while hovered. */
	private final List<Map.Entry<Button, String>> lockedButtons = new ArrayList<>();
	private String hoverEmote;
	private String shown = "";
	private boolean turning;
	private final LockerParticles particles = new LockerParticles();
	private String toast = "";
	private long toastUntil;

	public LockerScreen() {
		super(Component.literal("Locker"));
	}

	private static Map<String, String> mine() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? Map.of() : Cosmetics.OUTFITS.getOrDefault(mc.player.getUUID(), Map.of());
	}

	/** One tile in the grid (locked: what unlocks it, shown while hovered). */
	private record Tile(String label, boolean active, Runnable click, String emote, String locked) {
		Tile(String label, boolean active, Runnable click) {
			this(label, active, click, null, null);
		}
	}

	private List<Tile> tiles() {
		List<Tile> out = new ArrayList<>();
		Map<String, String> outfit = mine();
		UnlockRule.Stats st = ClientState.career();
		switch (tab) {
			case "preset" -> {
				for (PresetDef p : Sheets.PRESETS) {
					// A preset made of locked pieces (the Dominus sets) is locked until they are.
					CosmeticDef lock = null;
					for (String[] piece : Locker.pieces(p)) {
						CosmeticDef c = Sheets.COSMETICS_BY_ID.get(piece[1]);
						if (lock == null && c != null && !ClientState.unlocked(c)) lock = c;
					}
					if (lock == null) out.add(new Tile(p.name(), true, () -> send("locker:preset:" + p.id())));
					else out.add(new Tile(UnlockRule.needs(lock.unlock(), lock.need()), false, () -> {
					}, null, UnlockRule.hint(lock.unlock(), lock.need(), st)));
				}
			}
			case "emote" -> {
				List<String> wheel = new ArrayList<>(ClientState.myEmotes());
				for (EmoteDef e : Sheets.EMOTES) {
					boolean has = ClientState.unlocked(e), on = wheel.contains(e.id());
					// Locked: the name stays hidden, only what unlocks it shows.
					String label = !has ? UnlockRule.needs(e.unlock(), e.need()) : on ? "> " + e.name() + " <" : e.name();
					out.add(new Tile(label, has, () -> {
						List<String> next = new ArrayList<>(wheel);
						if (on) next.remove(e.id());
						else if (next.size() < 8) next.add(e.id());
						else {
							toast("Your wheel is full (8): click one marked > < to take it off first");
							return;
						}
						send("locker:emotes:" + (next.isEmpty() ? "none" : String.join("+", next)));
					}, has ? e.id() : null, has ? null : UnlockRule.hint(e.unlock(), e.need(), st))); // hovering an unlocked one shows it on the preview
				}
			}
			case "glider" -> {
				String worn = mine().get("glider");
				for (GliderDef g : Sheets.GLIDERS) {
					boolean has = ClientState.unlocked(g), on = g.id().equals(worn) || worn == null && g == Sheets.GLIDERS.get(0);
					String label = !has ? UnlockRule.needs(g.unlock(), g.need()) : on ? "> " + g.name() + " <" : g.name();
					out.add(new Tile(label, has, () -> send("locker:glider:" + g.id()), null, has ? null : UnlockRule.hint(g.unlock(), g.need(), st)));
				}
			}
			default -> {
				String worn = outfit.get(tab);
				out.add(new Tile(worn == null ? "> None <" : "None", true, () -> send("locker:" + tab + ":none")));
				for (CosmeticDef c : Sheets.COSMETICS) {
					if (!c.slot().equals(tab)) continue;
					boolean on = c.id().equals(worn), has = ClientState.unlocked(c);
					if (!has) out.add(new Tile(UnlockRule.needs(c.unlock(), c.need()), false, () -> {
					}, null, UnlockRule.hint(c.unlock(), c.need(), st)));
					else out.add(new Tile(on ? "> " + c.name() + " <" : c.name(), true, () -> send("locker:" + tab + ":" + (on ? "none" : c.id()))));
				}
			}
		}
		return out;
	}

	private int rowsPerPage() {
		return Math.max(3, (this.height - 30 - 48) / 21);
	}

	@Override
	protected void init() {
		int y = 30;
		for (String[] t : TABS) {
			Button b = Button.builder(Component.literal(t[1]), btn -> {
				tab = t[0];
				page = 0;
				this.rebuildWidgets();
			}).bounds(10, y, 78, 18).build();
			b.active = !tab.equals(t[0]);
			this.addRenderableWidget(b);
			y += 20;
		}
		emoteButtons.clear();
		lockedButtons.clear();
		List<Tile> all = tiles();
		int per = rowsPerPage() * COLS, pages = Math.max(1, (all.size() + per - 1) / per);
		page = Math.max(0, Math.min(page, pages - 1));
		for (int i = page * per, n = 0; i < Math.min(all.size(), (page + 1) * per); i++, n++) {
			Tile t = all.get(i);
			Button b = Button.builder(Component.literal(t.label()), btn -> t.click().run()).bounds(96 + (n % COLS) * (W + 4), 30 + (n / COLS) * 21, W, 18).build();
			b.active = t.active();
			if (t.emote() != null) emoteButtons.add(Map.entry(b, t.emote()));
			if (t.locked() != null) lockedButtons.add(Map.entry(b, t.locked()));
			this.addRenderableWidget(b);
		}
		if (pages > 1) {
			int py = 30 + rowsPerPage() * 21 + 2;
			Button prev = Button.builder(Component.literal("<"), btn -> {
				page--;
				this.rebuildWidgets();
			}).bounds(96, py, 30, 16).build();
			prev.active = page > 0;
			Button next = Button.builder(Component.literal(">"), btn -> {
				page++;
				this.rebuildWidgets();
			}).bounds(96 + COLS * (W + 4) - 34, py, 30, 16).build();
			next.active = page < pages - 1;
			this.addRenderableWidget(prev);
			this.addRenderableWidget(next);
		}
		this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> this.onClose()).bounds(10, this.height - 26, 78, 18).build());
	}

	/** Rebuilds the tab buttons and the grid (after the tab was changed from outside, e.g. LockerAudit). */
	void refresh() {
		this.rebuildWidgets();
	}

	private void send(String action) {
		ClientPlayNetworking.send(new Net.Action(action));
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) SfxBank.play("ui_click", mc.player.position());
	}

	private void toast(String text) {
		toast = text;
		toastUntil = System.currentTimeMillis() + 3000;
	}

	@Override
	public void tick() {
		// Redraw the lists when the server confirms a change (the > marker < moves) or the unlocks change.
		String now = String.valueOf(mine()) + ClientState.here();
		if (!now.equals(shown)) {
			shown = now;
			this.rebuildWidgets();
		}
		// Preview particles: the same textures and spawning as in the world (LockerParticles).
		particles.tick(Cosmetics.worn(mine(), "particle"));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// Only the left side is shaded: your avatar stays visible on the right.
		g.fill(0, 0, PANEL, this.height, 0xB0101420);
		g.fill(PANEL + 20, 30, this.width - 20, this.height - 20, 0x50101420);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.text(this.font, "LOCKER", 10, 12, 0xFFFFD42A, true);
		String info = switch (tab) {
			case "emote" -> "Click to pick up to 8 for your wheel (B): " + ClientState.myEmotes().size() + "/8 - hover to preview";
			case "glider" -> {
				UnlockRule.Stats c = ClientState.career();
				yield "Yours: " + c.kills() + " kills, " + c.wins() + " wins, range best " + c.rangeBest() + ", " + c.bullseyes() + " bullseyes";
			}
			case "preset" -> "Whole outfits - every piece is also in its own tab (Dominus sets unlock)";
			default -> "Roblox-style catalog - looks only, no advantage";
		};
		g.text(this.font, info, 60, 12, 0xFFB8C4D6, true);
		if (System.currentTimeMillis() < toastUntil) g.text(this.font, toast, 96, this.height - 22, 0xFFFF8080, true);
		else for (Map.Entry<Button, String> e : lockedButtons) {
			// (Checked by position: a locked button is inactive.)
			Button b = e.getKey();
			if (mouseX >= b.getX() && mouseX < b.getX() + b.getWidth() && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight())
				g.text(this.font, e.getValue(), 96, this.height - 22, 0xFFFFD42A, true);
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		hoverPreview(mc);
		int x0 = PANEL + 20, y0 = 30, x1 = this.width - 20, y1 = this.height - 20;
		boolean glider = tab.equals("glider");
		int size = (int) Math.min((y1 - y0) * (glider ? 0.22F : 0.36F), (x1 - x0) * 0.5F);
		// (1.0.1.5) A window with no room for the preview (narrower than the list beside it, as Minecraft's own 854 x 480
		// is at our GUI scale, or minimized): none, rather than asking the graphics card for a picture of no size, which
		// crashed the game (players' Melty, 1.0.1.4: OpenGL error 1281 in createTexture).
		if (x1 - x0 < 24 || y1 - y0 < 24 || size < 4) return;
		// The particle effect around the preview: feet at the box's middle plus half the body (and the gliders tab
		// offset).
		CosmeticDef fx = Cosmetics.worn(mine(), "particle");
		int cx = (x0 + x1) / 2, feet = (y0 + y1) / 2 + (int) (size * (0.9F + (glider ? 0.45F : 0.0625F)));
		particles.draw(g, fx, cx, feet, size, false);
		preview(g, mc, x0, y0, x1, y1, size, glider);
		frames++;
		particles.draw(g, fx, cx, feet, size, true);
		g.centeredText(this.font, mc.player.getName().getString(), (x0 + x1) / 2, y1 - 14, 0xFFFFFFFF);
		if (!LockerAudit.active()) g.centeredText(this.font, "Hold right mouse and drag to turn", (x0 + x1) / 2, y0 + 4, 0x90FFFFFF);
	}

	/**
	 * Emotes tab: hovering an unlocked emote plays it on the preview (only on your screen), over and over while the
	 * mouse stays on it, so you can see it without changing your wheel.
	 */
	private void hoverPreview(Minecraft mc) {
		String id = null;
		for (Map.Entry<Button, String> e : emoteButtons)
			if (e.getKey().isHovered()) id = e.getValue();
		int me = mc.player.getId();
		if (id == null) {
			stopHover(me);
			return;
		}
		Map.Entry<String, Long> playing = ClientState.EMOTES.get(me);
		if (!id.equals(hoverEmote) || playing == null) ClientState.EMOTES.put(me, Map.entry(id, System.currentTimeMillis()));
		hoverEmote = id;
	}

	private void stopHover(int me) {
		if (hoverEmote == null) return;
		Map.Entry<String, Long> playing = ClientState.EMOTES.get(me);
		if (playing != null && playing.getKey().equals(hoverEmote)) ClientState.EMOTES.remove(me);
		hoverEmote = null;
	}

	@Override
	public void removed() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) stopHover(mc.player.getId());
		super.removed();
	}

	/** Draws you, facing the camera, turned by previewYaw (no up/down tilt). */
	private static void preview(GuiGraphicsExtractor g, Minecraft mc, int x0, int y0, int x1, int y1, int size, boolean glider) {
		previewing = true;
		try {
			var renderer = mc.getEntityRenderDispatcher().getRenderer(mc.player);
			EntityRenderState st = renderer.createRenderState(mc.player, 1.0F);
			st.shadowPieces.clear();
			st.outlineColor = 0;
			if (st instanceof LivingEntityRenderState l) {
				l.bodyRot = 180.0F + previewYaw;
				l.yRot = 0;
				l.xRot = 0;
				l.boundingBoxWidth = l.boundingBoxWidth / l.scale;
				l.boundingBoxHeight = l.boundingBoxHeight / l.scale;
				l.scale = 1.0F;
				l.setData(RobloxAvatarRenderer.GLIDING, glider); // Gliders tab: show your glider
			}
			Vector3f translation = new Vector3f(0, st.boundingBoxHeight / 2.0F + (glider ? 0.45F : 0.0625F), 0);
			// Gliders tab: seen a little from above (as when gliding), so flat gliders don't show edge-on as a line.
			Quaternionf view = new Quaternionf().rotateZ((float) Math.PI);
			if (glider) view.rotateX((float) Math.toRadians(-22));
			g.entity(st, size, translation, view, new Quaternionf(), x0, y0, x1, y1);
		} finally {
			previewing = false;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && event.x() > PANEL) {
			turning = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (turning && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			previewYaw = (previewYaw - (float) dx * 2.0F) % 360F; // left and right only, all the way round
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) turning = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
