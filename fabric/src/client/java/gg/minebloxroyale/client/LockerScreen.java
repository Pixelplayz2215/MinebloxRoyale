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
 * particles as in the world). Everything is purely cosmetic. 2.0.0-test6 (creator: "make the locker UI/HUD cleaner,
 * minimalistic to match the rest of the HUD"): drawn in the HUD's smooth style (SmoothButton, Smooth): the tabs a lit
 * list, the items rounded tiles with their names in full (what you have on edged in gold, locked ones dimmed with a
 * lock, the Shop's prices with the coin), a tidy header, and the match HUD out of the way while it's open.
 */
public class LockerScreen extends Screen {
	private static final String[][] TABS = { { "shop", "Shop" }, { "preset", "Presets" }, { "hat", "Hats" }, { "hair", "Hair" }, { "face", "Face" },
		{ "back", "Back" }, { "shirt", "Shirts" }, { "pants", "Pants" }, { "shoes", "Shoes" }, { "particle", "Particles" }, { "pickaxe", "Pickaxes" }, { "contrail", "Contrails" },
		{ "boosttrail", "Boost Trails" }, { "emote", "Emotes" }, { "glider", "Gliders" } };
	private static final int COLS = 3;
	/**
	 * The layout's size (2.0.0-test6.1, creator, with a screenshot of the Presets tab: "text is a bit small"): laid out
	 * for the smallest window at k = 1, the whole Locker (panel, tabs, tiles and their lettering) grows with the window
	 * when there's room, up to 1.3 times: init() works it out from the screen's size.
	 */
	private float k = 1;
	private int panel = 350, tileW = 80, tileH = 18, rowH = 21, gx = 98, tabW = 82, top = 40;
	/** Tabs where hovering one of your items shows it on you, like the emotes (2.0.0-test3): in your hand, or skydiving. */
	private static final List<String> HOVER_TRY = List.of("pickaxe", "contrail", "boosttrail");
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
	/**
	 * Tiles on this page that hovering shows on your preview (button, "slot:id"; "preset:id" the whole outfit): the
	 * Shop's, the presets, pickaxes and contrails.
	 */
	private final List<Map.Entry<Button, String>> tryOnButtons = new ArrayList<>();
	/** (2.0.0) Tiles on this page that are new (button, item): hovering one clears it (NewUnlocks). */
	private final List<Map.Entry<SmoothButton, String>> newButtons = new ArrayList<>();
	/** The Shop's category titles on this page: {title, x, y}. */
	private final List<Object[]> headers = new ArrayList<>();
	/** "Page 2 of 3" under the grid, when there's more than one. */
	private String pageLine = "";
	private static final int GOLD = 0xFFFFC83D, MUTED = 0xFF9AA3B2, WHITE = 0xFFF4F6FA;

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
	private record Tile(String label, boolean active, Runnable click, String emote, String locked, String tryOn, boolean header, String item) {
		Tile(String label, boolean active, Runnable click) {
			this(label, active, click, null, null, null, false, null);
		}

		Tile(String label, boolean active, Runnable click, String emote, String locked) {
			this(label, active, click, emote, locked, null, false, null);
		}

		Tile(String label, boolean active, Runnable click, String emote, String locked, String tryOn, boolean header) {
			this(label, active, click, emote, locked, tryOn, header, null);
		}

		/** An unlocked item that can be new (2.0.0, NewUnlocks: "emote:id", "glider:id", "cos:id", "preset:id"; never gold). */
		Tile item(String key) {
			return new Tile(label, active, click, emote, locked, tryOn, header, key);
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
					if (t[0].equals("preset")) { // (2.0.0-test3: whole outfits for gold)
						for (PresetDef p : Sheets.PRESETS)
							if (p.unlock().equals("shop") && !st.owned().contains(p.id())) items.add(shopTile(p.id(), p.name(), p.need(), "preset", st, null));
					} else if (t[0].equals("emote")) {
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
					if (p.unlock().equals("shop") && !ClientState.all && !st.owned().contains(p.id())) { // (2.0.0-test3: for gold)
						out.add(shopTile(p.id(), p.name(), p.need(), "preset", st, null));
						continue;
					}
					// A preset made of locked pieces (the Dominus sets) is locked until they are.
					CosmeticDef lock = null;
					for (String[] piece : Locker.pieces(p)) {
						CosmeticDef c = Sheets.COSMETICS_BY_ID.get(piece[1]);
						if (lock == null && c != null && !ClientState.unlocked(c)) lock = c;
					}
					if (lock == null) out.add(new Tile(p.name(), true, () -> send("locker:preset:" + p.id()), null, null, "preset:" + p.id(), false)
						.item(p.unlock().equals("shop") ? null : "preset:" + p.id()));
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
					}, has ? e.id() : null, has ? null : UnlockRule.hint(e.unlock(), e.need(), st)).item(has ? "emote:" + e.id() : null)); // hovering an unlocked one shows it on the preview
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
					out.add(new Tile(label, has, () -> send("locker:glider:" + g.id()), null, has ? null : UnlockRule.hint(g.unlock(), g.need(), st))
						.item(has && !g.unlock().equals("shop") ? "glider:" + g.id() : null));
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
						HOVER_TRY.contains(tab) ? tab + ":" + c.id() : null, false).item(c.unlock().equals("shop") ? null : "cos:" + c.id()));
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
		return Math.max(3, (this.height - top - Math.round(50 * k)) / rowH);
	}

	@Override
	protected void init() {
		// (k: as big as the window has room for: the panel and a preview beside it across, the tabs and Done down)
		k = Math.max(1F, Math.min(1.3F, Math.min(this.width / 560F, this.height / 344F)));
		panel = Math.round(350 * k);
		tileW = Math.round(80 * k);
		tileH = Math.round(18 * k);
		rowH = Math.round(21 * k);
		gx = Math.round(98 * k);
		tabW = Math.round(82 * k);
		top = Math.round(40 * k);
		// (the tabs packed closer on a small window, down to 10 apart, so the last stays clear of Done)
		int y = top, pitch = Math.max(10, Math.min(Math.round(18 * k), (this.height - top - 8 - tileH - 4) / TABS.length));
		for (String[] t : TABS) {
			SmoothButton b = new SmoothButton(8, y, tabW, pitch - 2, t[1], SmoothButton.Look.TAB, btn -> {
				tab = t[0];
				page = 0;
				this.rebuildWidgets();
			});
			b.on = tab.equals(t[0]);
			b.scale = k;
			b.fresh = NewUnlocks.anyIn(t[0]); // (2.0.0: a red dot on a tab with something new in it)
			this.addRenderableWidget(b);
			y += pitch;
		}
		emoteButtons.clear();
		lockedButtons.clear();
		tryOnButtons.clear();
		newButtons.clear();
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
			int tx = gx + cell[i] % COLS * (tileW + 4), ty = top + r % rows * rowH;
			if (t.header()) {
				headers.add(new Object[] { t.label(), tx, ty });
				continue;
			}
			// (the tile's look from its label: "> name <" is what you have on, "name - 400g" for sale, "name (owned)")
			String label = t.label(), price = null;
			boolean on = label.startsWith("> ") && label.endsWith(" <"), owned = label.endsWith(" (owned)");
			if (on) label = label.substring(2, label.length() - 2);
			if (owned) label = label.substring(0, label.length() - " (owned)".length());
			int dash = label.lastIndexOf(" - ");
			if (dash > 0 && label.endsWith("g") && label.substring(dash + 3, label.length() - 1).chars().allMatch(Character::isDigit)) {
				price = label.substring(dash + 3, label.length() - 1);
				label = label.substring(0, dash);
			}
			SmoothButton b = new SmoothButton(tx, ty, tileW, tileH, label, SmoothButton.Look.TILE, btn -> t.click().run());
			b.scale = k;
			b.center = true; // (every item's name in the middle of its tile: 2.0.0-test6.1, creator)
			b.on = on;
			b.owned = owned;
			b.price = price;
			b.locked = !t.active() && t.locked() != null;
			b.active = t.active();
			if (t.emote() != null) emoteButtons.add(Map.entry(b, t.emote()));
			if (t.locked() != null) lockedButtons.add(Map.entry(b, t.locked()));
			if (t.tryOn() != null) tryOnButtons.add(Map.entry(b, t.tryOn()));
			if (t.item() != null && t.active() && NewUnlocks.isNew(t.item())) {
				b.fresh = true;
				newButtons.add(Map.entry(b, t.item()));
			}
			this.addRenderableWidget(b);
		}
		if (pages > 1) {
			int py = top + rowsPerPage() * rowH + 2, aw = Math.round(30 * k), ah = Math.round(14 * k);
			SmoothButton prev = new SmoothButton(gx, py, aw, ah, "<", SmoothButton.Look.ARROW, btn -> {
				page--;
				this.rebuildWidgets();
			});
			prev.active = page > 0;
			SmoothButton next = new SmoothButton(gx + COLS * (tileW + 4) - 4 - aw, py, aw, ah, ">", SmoothButton.Look.ARROW, btn -> {
				page++;
				this.rebuildWidgets();
			});
			next.active = page < pages - 1;
			prev.scale = next.scale = k;
			this.addRenderableWidget(prev);
			this.addRenderableWidget(next);
			pageLine = "Page " + (page + 1) + " of " + pages;
		} else pageLine = "";
		SmoothButton done = new SmoothButton(8, this.height - 8 - tileH, tabW, tileH, "Done", SmoothButton.Look.MAIN, btn -> this.onClose());
		done.scale = k;
		this.addRenderableWidget(done);
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
		// The catalog on a dark rounded panel on the left; your avatar on the right, in a faint frame.
		Smooth.panel(g, 2, 2, panel - 4, this.height - 4, 10, 0xEC161A22);
		Smooth.panel(g, panel + 20, top - 8, this.width - panel - 40, this.height - top - 12, 12, 0x40101420);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		// The header: LOCKER and the tab, your gold in a chip on the right (or what's picked for the wheel), one hint line.
		float lw = Smooth.text(g, "LOCKER", 10, 8 * k, 13 * k, GOLD, Smooth.LEFT, true);
		Smooth.text(g, tabName(tab), 10 + lw + 8 * k, 11 * k, 9 * k, WHITE, Smooth.LEFT, false);
		String chip = tab.equals("emote") ? ClientState.myEmotes().size() + " / 8 on your wheel" : ClientState.all ? "All unlocked" : ClientState.career().gold() + "";
		boolean coin = !tab.equals("emote") && !ClientState.all;
		float cw = Smooth.width(chip, 7 * k) + (coin ? 22 : 12) * k;
		Smooth.panel(g, panel - 10 - cw, 7 * k, cw, 15 * k, 7 * k, 0xFF2C3340);
		if (coin) Smooth.icon(g, "gold", panel - 10 - cw + 5 * k, 9.5F * k, 10 * k, -1);
		Smooth.text(g, chip, panel - 10 - 6 * k, 10.5F * k, 7 * k, GOLD, Smooth.RIGHT, false);
		String info = switch (tab) {
			case "emote" -> "Click to put an emote on your wheel (B), or take it off; hover to see it";
			case "glider" -> {
				UnlockRule.Stats c = ClientState.career();
				yield "Yours: " + c.kills() + " kills, " + c.wins() + " wins, range best " + c.rangeBest() + ", " + c.bullseyes() + " bullseyes";
			}
			case "preset" -> "Whole outfits - every piece is also in its own tab (Dominus sets unlock)";
			case "shop" -> "Spend gold from matches: eliminations, top finishes, wins, bosses";
			case "pickaxe" -> "Your pickaxe's look in matches - looks only";
			case "contrail" -> "Light trails from your hands skydiving, and your glider's wingtips";
			case "boosttrail" -> "Streams from the back of any car you drive while it boosts (Space)";
			default -> "Roblox-style catalog - looks only, no advantage";
		};
		float is = 6.5F * k;
		while (is > 4.5F && Smooth.width(info, is) > panel - 22) is -= 0.25F;
		Smooth.text(g, info, 10, 26 * k, is, MUTED, Smooth.LEFT, false);
		// The Shop's category titles, each with a line on to the grid's edge.
		for (Object[] h : headers) {
			String title = (String) h[0];
			int hx = (int) h[1], hy = (int) h[2];
			float tw = Smooth.text(g, title.toUpperCase(java.util.Locale.ROOT), hx + 2, hy + 6 * k, 7 * k, GOLD, Smooth.LEFT, false);
			Smooth.panel(g, hx + 8 + tw, hy + 9.5F * k, gx + COLS * (tileW + 4) - 6 - (hx + 8 + tw), 0.8F, 0, 0x60FFC83D);
		}
		if (!pageLine.isEmpty()) Smooth.text(g, pageLine, gx + COLS * (tileW + 4) / 2F, top + rowsPerPage() * rowH + 2 + 3 * k, 6.5F * k, MUTED, Smooth.CENTER, false);
		// Under the grid: a message (bought, not enough gold...), else what unlocks the locked item under the mouse.
		String line = null;
		int lineColor = GOLD;
		if (System.currentTimeMillis() < toastUntil) {
			line = toast;
			lineColor = toastColor;
		} else for (Map.Entry<Button, String> e : lockedButtons) {
			// (Checked by position: a locked button is inactive.)
			Button b = e.getKey();
			if (mouseX >= b.getX() && mouseX < b.getX() + b.getWidth() && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight()) line = e.getValue();
		}
		if (line != null) {
			float ls = 7 * k;
			while (ls > 4.5F && Smooth.width(line, ls) > panel - gx - 12) ls -= 0.25F;
			Smooth.text(g, line, gx, this.height - 8 - tileH / 2F - ls / 2, ls, lineColor, Smooth.LEFT, false);
		}
		// (2.0.0) A new item hovered: seen now, its NEW badge goes (and its tab's dot, once nothing else there is new).
		for (Map.Entry<SmoothButton, String> e : newButtons)
			if (e.getKey().fresh && e.getKey().isHovered()) {
				e.getKey().fresh = false;
				NewUnlocks.markSeen(e.getValue());
				for (var w : this.children())
					if (w instanceof SmoothButton tb && tb.getMessage().getString().equals(tabName(tab))) tb.fresh = NewUnlocks.anyIn(tab);
			}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		hoverPreview(mc);
		// (2.0.0-test2) The hovered item on you (a glider open, a contrail while skydiving, a pickaxe in your hand).
		String tryOn = null;
		for (Map.Entry<Button, String> e : tryOnButtons)
			if (e.getKey().isHovered()) tryOn = e.getValue();
		int x0 = panel + 20, y0 = top - 4, x1 = this.width - 20, y1 = this.height - 20;
		// (2.0.0-test3) Boost Trails (or one hovered in the Shop): a car instead of you, its trail streaming
		if (tryOn != null ? tryOn.startsWith("boosttrail:") : tab.equals("boosttrail")) {
			CosmeticDef trail = tryOn != null ? Sheets.COSMETICS_BY_ID.get(tryOn.substring("boosttrail:".length())) : Cosmetics.worn(mine(), "boosttrail");
			carPreview(g, x0, y0, x1, y1, trail);
			frames++;
			Smooth.text(g, trail == null ? "No boost trail on" : trail.name() + " - on any car you drive", (x0 + x1) / 2F, y1 - 16 * k, 8 * k, WHITE, Smooth.CENTER, true);
			if (!LockerAudit.active()) Smooth.text(g, "Hold right mouse and drag to turn", (x0 + x1) / 2F, y0 + 2, 6.5F * k, 0xB0FFFFFF, Smooth.CENTER, false);
			return;
		}
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
		Smooth.text(g, mc.player.getName().getString(), (x0 + x1) / 2F, y1 - 16 * k, 8.5F * k, WHITE, Smooth.CENTER, true);
		if (!LockerAudit.active()) Smooth.text(g, "Hold right mouse and drag to turn", (x0 + x1) / 2F, y0 + 2, 6.5F * k, 0xB0FFFFFF, Smooth.CENTER, false);
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
					String slot = tryOn.substring(0, colon), id = tryOn.substring(colon + 1);
					PresetDef preset = slot.equals("preset") ? Sheets.PRESETS_BY_ID.get(id) : null;
					Map<String, String> worn = new java.util.HashMap<>();
					if (preset != null) for (String[] piece : Locker.pieces(preset)) worn.put(piece[0], piece[1]); // ("-": nothing there)
					else worn.put(slot, id);
					l.setData(RobloxAvatarRenderer.TRY_ON, worn);
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

	/**
	 * The Boost Trails tab's preview (2.0.0-test3, creator: "When viewing car contrails, a car should show up in a angles
	 * back/side view and hovering over boosting contrail will show on the car"): the sports car seen from behind and to
	 * the side, a little from above, the trail (the hovered one, else yours) streaming from its exhausts as if boosting;
	 * right-drag turns it as it does you.
	 */
	private static void carPreview(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, @org.jspecify.annotations.Nullable CosmeticDef trail) {
		var st = CarEntityRenderer.preview(trail);
		int size = (int) Math.min((y1 - y0) * 0.16F, (x1 - x0) * 0.12F);
		Quaternionf view = new Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(-24)).rotateY((float) Math.toRadians(145 + previewYaw));
		g.entity(st, size, new Vector3f(0, 0.6F, -1.2F), view, new Quaternionf(), x0, y0, x1, y1);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && event.x() > panel) {
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
