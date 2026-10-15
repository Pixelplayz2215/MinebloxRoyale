package gg.minebloxroyale.client;

import com.mojang.blaze3d.platform.InputConstants;
import gg.minebloxroyale.client.avatar.Cosmetics;
import gg.minebloxroyale.client.avatar.RobloxAvatarRenderer;
import gg.minebloxroyale.client.media.SfxBank;
import gg.minebloxroyale.hub.Locker;
import gg.minebloxroyale.hub.UnlockRule;
import gg.minebloxroyale.match.Loadout;
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
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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
	private static final String[][] TABS = { { "shop", "Shop" }, { "preset", "Presets" }, { "hat", "Hats" }, { "hair", "Hair" }, { "face", "Face" },
		{ "back", "Back" }, { "shirt", "Shirts" }, { "pants", "Pants" }, { "shoes", "Shoes" }, { "particle", "Particles" }, { "pickaxe", "Pickaxes" }, { "contrail", "Contrails" },
		{ "emote", "Emotes" }, { "glider", "Gliders" } };
	private static final int PANEL = 350, COLS = 3, W = 80;
	/** Tabs where hovering one of your items shows it on you, like the emotes (2.0.0-test3): in your hand, or skydiving. */
	private static final List<String> HOVER_TRY = List.of("pickaxe", "contrail");
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
	private int toastColor;
	/** The Shop item being bought ({id, name, its tab}), till the server says it's yours (a toast then says where it is). */
	private static String[] buying;
	/** Tiles on this page that hovering shows on your preview (button, "slot:id"): the Shop's, pickaxes and contrails. */
	private final List<Map.Entry<Button, String>> tryOnButtons = new ArrayList<>();
	/** The Shop's category titles on this page: {title, x, y}. */
	private final List<Object[]> headers = new ArrayList<>();

	public LockerScreen() {
		super(Component.literal("Locker"));
	}

	private static Map<String, String> mine() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? Map.of() : Cosmetics.OUTFITS.getOrDefault(mc.player.getUUID(), Map.of());
	}

	/**
	 * One tile in the grid (locked: what unlocks it, shown while hovered; tryOn: "slot:id" shown on your preview while
	 * hovered, the Shop; header: a category's title on a row of its own, the Shop).
	 */
	private record Tile(String label, boolean active, Runnable click, String emote, String locked, String tryOn, boolean header) {
		Tile(String label, boolean active, Runnable click) {
			this(label, active, click, null, null, null, false);
		}

		Tile(String label, boolean active, Runnable click, String emote, String locked) {
			this(label, active, click, emote, locked, null, false);
		}

		static Tile header(String title) {
			return new Tile(title, false, () -> {
			}, null, null, null, true);
		}
	}

	private List<Tile> tiles() {
		List<Tile> out = new ArrayList<>();
		Map<String, String> outfit = mine();
		UnlockRule.Stats st = ClientState.career();
		switch (tab) {
			case "shop" -> {
				// v2.0.0: what's for gold (the "shop" items of every section), with its price. 2.0.0-test2 (creator: "I have
				// no idea what any of the items are for purchase"): under its category's title, in the tabs' order, and
				// shown on you while hovered; a bought one leaves the Shop and is used from its own tab like any other.
				for (String[] t : TABS) {
					List<Tile> items = new ArrayList<>();
					if (t[0].equals("emote")) {
						for (EmoteDef e : Sheets.EMOTES)
							if (e.unlock().equals("shop") && !st.owned().contains(e.id())) items.add(shopTile(e.id(), e.name(), e.need(), "emote", st, e.id()));
					} else if (t[0].equals("glider")) {
						for (GliderDef g : Sheets.GLIDERS)
							if (g.unlock().equals("shop") && !st.owned().contains(g.id())) items.add(shopTile(g.id(), g.name(), g.need(), "glider", st, null));
					} else
						for (CosmeticDef c : Sheets.COSMETICS)
							if (c.slot().equals(t[0]) && c.unlock().equals("shop") && !st.owned().contains(c.id()))
								items.add(shopTile(c.id(), c.name(), c.need(), c.slot(), st, null));
					if (items.isEmpty()) continue;
					out.add(Tile.header(t[1]));
					out.addAll(items);
				}
				if (out.isEmpty()) out.add(Tile.header("You've bought everything!"));
			}
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
					if (e.unlock().equals("shop") && !ClientState.unlocked(e)) {
						out.add(shopTile(e.id(), e.name(), e.need(), "emote", st, e.id()));
						continue;
					}
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
					if (g.unlock().equals("shop") && !ClientState.unlocked(g)) {
						out.add(shopTile(g.id(), g.name(), g.need(), "glider", st, null));
						continue;
					}
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
					if (!has && c.unlock().equals("shop")) out.add(shopTile(c.id(), c.name(), c.need(), c.slot(), st, null));
					else if (!has) out.add(new Tile(UnlockRule.needs(c.unlock(), c.need()), false, () -> {
					}, null, UnlockRule.hint(c.unlock(), c.need(), st)));
					else out.add(new Tile(on ? "> " + c.name() + " <" : c.name(), true, () -> send("locker:" + tab + ":" + (on ? "none" : c.id())), null, null,
						HOVER_TRY.contains(tab) ? tab + ":" + c.id() : null, false));
				}
			}
		}
		return out;
	}

	/**
	 * A shop item (v2.0.0): not bought yet, its name and price, clicked to buy (its hint says how much gold you have);
	 * bought, it's used like any other (an emote goes on or off your wheel, the rest is worn). Hovered, bought or not,
	 * it's shown on you (2.0.0-test2; owned ones too since 2.0.0-test3, creator: "on hover like Emotes"): an emote plays,
	 * the rest is tried on.
	 */
	private Tile shopTile(String id, String name, int price, String kind, UnlockRule.Stats st, String emote) {
		boolean owned = ClientState.all || st.owned().contains(id);
		if (!owned)
			return new Tile(name + " - " + price + "g", true, () -> {
				if (st.gold() < price) toast("Not enough gold: " + name + " costs " + price + ", you have " + st.gold());
				else {
					buying = new String[] { id, name, tabName(kind) };
					send("locker:buy:" + id);
				}
			}, emote, UnlockRule.hint("shop", price, st), kind.equals("emote") ? null : kind + ":" + id, false); // (an emote plays instead)
		if (kind.equals("emote")) {
			List<String> wheel = new ArrayList<>(ClientState.myEmotes());
			boolean on = wheel.contains(id);
			return new Tile(on ? "> " + name + " <" : name + " (owned)", true, () -> {
				if (on) wheel.remove(id);
				else if (wheel.size() < 8) wheel.add(id);
				else {
					toast("Your wheel is full (8): take one off in the Emotes tab first");
					return;
				}
				send("locker:emotes:" + (wheel.isEmpty() ? "none" : String.join("+", wheel)));
			}, id, null);
		}
		boolean on = id.equals(mine().get(kind));
		return new Tile(on ? "> " + name + " <" : name + " (owned)", true, () -> send("locker:" + kind + ":" + (on ? "none" : id)), null, null, kind + ":" + id,
			false);
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
		tryOnButtons.clear();
		headers.clear();
		List<Tile> all = tiles();
		// Each tile's cell (row * COLS + column): a header (the Shop's categories) takes a row of its own, never a page's
		// last, and its items follow on the rows under it.
		int rows = rowsPerPage(), row = 0, col = 0;
		int[] cell = new int[all.size()];
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).header()) {
				if (col > 0) row++;
				col = 0;
				if (row % rows == rows - 1) row++;
				cell[i] = row++ * COLS;
			} else {
				cell[i] = row * COLS + col;
				if (++col == COLS) {
					row++;
					col = 0;
				}
			}
		}
		int pages = Math.max(1, ((col > 0 ? row + 1 : row) + rows - 1) / rows);
		page = Math.max(0, Math.min(page, pages - 1));
		for (int i = 0; i < all.size(); i++) {
			int r = cell[i] / COLS;
			if (r / rows != page) continue;
			Tile t = all.get(i);
			int tx = 96 + cell[i] % COLS * (W + 4), ty = 30 + r % rows * 21;
			if (t.header()) {
				headers.add(new Object[] { t.label(), tx, ty });
				continue;
			}
			Button b = Button.builder(Component.literal(t.label()), btn -> t.click().run()).bounds(tx, ty, W, 18).build();
			b.active = t.active();
			if (t.emote() != null) emoteButtons.add(Map.entry(b, t.emote()));
			if (t.locked() != null) lockedButtons.add(Map.entry(b, t.locked()));
			if (t.tryOn() != null) tryOnButtons.add(Map.entry(b, t.tryOn()));
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
		toast(text, 0xFFFF8080);
	}

	private void toast(String text, int color) {
		toast = text;
		toastColor = color;
		toastUntil = System.currentTimeMillis() + 3000;
	}

	/** A tab's name ("Emotes") from its id ("emote"). */
	private static String tabName(String id) {
		for (String[] t : TABS)
			if (t[0].equals(id)) return t[1];
		return id;
	}

	@Override
	public void tick() {
		// (2.0.0-test2) A purchase went through: say which tab the item is in now.
		if (buying != null && ClientState.career().owned().contains(buying[0])) {
			toast("Bought " + buying[1] + "! It's in " + buying[2] + " now", 0xFF7CFC7C);
			buying = null;
		}
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
		// Your gold (v2.0.0: the Shop tab spends it), over the grid's right edge.
		String gold = (ClientState.all ? "all unlocked" : ClientState.career().gold() + " gold");
		g.text(this.font, gold, PANEL - 10 - this.font.width(gold), 22, 0xFFFFC23A, true);
		String info = switch (tab) {
			case "emote" -> "Click to pick up to 8 for your wheel (B): " + ClientState.myEmotes().size() + "/8 - hover to preview";
			case "glider" -> {
				UnlockRule.Stats c = ClientState.career();
				yield "Yours: " + c.kills() + " kills, " + c.wins() + " wins, range best " + c.rangeBest() + ", " + c.bullseyes() + " bullseyes";
			}
			case "preset" -> "Whole outfits - every piece is also in its own tab (Dominus sets unlock)";
			case "shop" -> "Spend gold from matches: eliminations, top finishes, wins, bosses";
			case "pickaxe" -> "Your pickaxe's look in matches - looks only";
			case "contrail" -> "Light trails from your hands skydiving, and your glider's wingtips";
			default -> "Roblox-style catalog - looks only, no advantage";
		};
		g.text(this.font, info, 60, 12, 0xFFB8C4D6, true);
		// The Shop's category titles, each with a line on to the grid's edge.
		for (Object[] h : headers) {
			String title = (String) h[0];
			int hx = (int) h[1], hy = (int) h[2];
			g.text(this.font, title, hx + 2, hy + 5, 0xFFFFD42A, true);
			g.fill(hx + 8 + this.font.width(title), hy + 9, 96 + COLS * (W + 4) - 4, hy + 10, 0x60FFD42A);
		}
		if (System.currentTimeMillis() < toastUntil) g.text(this.font, toast, 96, this.height - 22, toastColor, true);
		else for (Map.Entry<Button, String> e : lockedButtons) {
			// (Checked by position: a locked button is inactive.)
			Button b = e.getKey();
			if (mouseX >= b.getX() && mouseX < b.getX() + b.getWidth() && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight())
				g.text(this.font, e.getValue(), 96, this.height - 22, 0xFFFFD42A, true);
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		hoverPreview(mc);
		// (2.0.0-test2) The hovered item on you (a glider open, a contrail while skydiving, a pickaxe in your hand).
		String tryOn = null;
		for (Map.Entry<Button, String> e : tryOnButtons)
			if (e.getKey().isHovered()) tryOn = e.getValue();
		int x0 = PANEL + 20, y0 = 30, x1 = this.width - 20, y1 = this.height - 20;
		boolean glider = tab.equals("glider") || tryOn != null && tryOn.startsWith("glider:");
		int size = (int) Math.min((y1 - y0) * (glider ? 0.22F : 0.36F), (x1 - x0) * 0.5F);
		// The particle effect around the preview: feet at the box's middle plus half the body (and the gliders tab
		// offset).
		CosmeticDef fx = Cosmetics.worn(mine(), "particle");
		int cx = (x0 + x1) / 2, feet = (y0 + y1) / 2 + (int) (size * (0.9F + (glider ? 0.45F : 0.0625F)));
		particles.draw(g, fx, cx, feet, size, false);
		boolean contrail = tab.equals("contrail") || tryOn != null && tryOn.startsWith("contrail:");
		preview(g, mc, x0, y0, x1, y1, size, glider, contrail, tryOn, glider || contrail ? null : heldPickaxe(tryOn));
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

	/**
	 * The pickaxe in your hand on the preview (2.0.0-test2; empty-handed in the hub, so it never showed): the hovered
	 * one, else in the Pickaxes tab and the Shop (2.0.0-test3, creator: "show the new pickaxe in hand ... while in shop")
	 * the one you have on. Its model ("" the default pickaxe), or null for none.
	 */
	private String heldPickaxe(String tryOn) {
		CosmeticDef c;
		if (tryOn != null && tryOn.startsWith("pickaxe:")) c = Sheets.COSMETICS_BY_ID.get(tryOn.substring("pickaxe:".length()));
		else if (tab.equals("pickaxe") || tab.equals("shop")) c = Cosmetics.worn(mine(), "pickaxe");
		else return null;
		return c == null ? "" : "pickaxe_" + c.shape();
	}

	/**
	 * Draws you, facing the camera, turned by previewYaw (no up/down tilt); tryOn ("slot:id"): that item worn just here
	 * (the Shop); pickaxe: that pickaxe in your hand (heldPickaxe).
	 */
	private static void preview(GuiGraphicsExtractor g, Minecraft mc, int x0, int y0, int x1, int y1, int size, boolean glider, boolean contrail, String tryOn,
		String pickaxe) {
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
				l.setData(RobloxAvatarRenderer.SKYDIVING, contrail); // Contrails tab: skydiving, trailing yours
				l.setData(RobloxAvatarRenderer.PREVIEW, true);
				if (tryOn != null) {
					int colon = tryOn.indexOf(':');
					l.setData(RobloxAvatarRenderer.TRY_ON, Map.of(tryOn.substring(0, colon), tryOn.substring(colon + 1)));
				}
			}
			if (pickaxe != null && st instanceof ArmedEntityRenderState armed) { // (in your main hand, as in a match)
				ItemStack pick = Loadout.pickaxe(pickaxe.isEmpty() ? null : pickaxe);
				boolean right = armed.mainArm != HumanoidArm.LEFT;
				if (right) armed.rightHandItemStack = pick;
				else armed.leftHandItemStack = pick;
				mc.getItemModelResolver().updateForLiving(right ? armed.rightHandItemState : armed.leftHandItemState, pick,
					right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND, mc.player);
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
