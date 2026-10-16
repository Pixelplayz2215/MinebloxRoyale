package gg.minebloxroyale.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import gg.minebloxroyale.client.media.SfxBank;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The results card at the end of a match (2.0.0-test5, the creator's pick of four mockups, Model Renders\Victory Royale
 * Screen v2.0.0): VICTORY ROYALE (or where you placed), your match in tiles with your total damage picked out, in team
 * modes every teammate's row and the team's totals, the gold you earned line by line and what it goes towards next, a
 * row of your emotes to do there and then (creator: "you can choose any emote from your emotes list"), and Back to hub
 * or Spectate (put the card away and watch). Drawn in the HUD's own style (Smooth), the game carrying on behind it.
 */
public class VictoryScreen extends Screen {
	private static final int GOLD = 0xFFFFC83D, PANEL = 0xF21D222B, TILE = 0xFF2C3340, MUTED = 0xFF9AA3B2, WHITE = 0xFFF4F6FA, LIGHT = 0xFFD9DEE7;
	private static final int CHIPS = 5;
	private final JsonObject d;
	private final boolean won, team;
	private final List<EmoteDef> emotes;
	private int page;
	/** Where things were drawn this frame (screen units): what a click lands on. */
	private final List<float[]> hits = new ArrayList<>();
	private final List<Runnable> actions = new ArrayList<>();

	public VictoryScreen(JsonObject data) {
		super(Component.literal("Results"));
		this.d = data;
		this.won = data.get("won").getAsBoolean();
		this.team = data.get("team").getAsBoolean();
		this.emotes = Sheets.EMOTES.stream().filter(ClientState::unlocked).toList();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Mouse over the game: no blur or dark wash of Minecraft's own; the card dims what it needs to. */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		hits.clear();
		actions.clear();
		g.fill(0, 0, this.width, this.height, 0x8C05070A);
		float W = team ? 450 : 312, H = team ? 318 : 282;
		float k = Math.min(1F, Math.min((this.height - 16) / H, (this.width - 16) / W));
		float x0 = (this.width - W * k) / 2, y0 = (this.height - H * k) / 2;
		g.pose().pushMatrix();
		g.pose().translate(x0, y0);
		g.pose().scale(k, k);
		card(g, W, H, mouseX - x0, mouseY - y0, k);
		g.pose().popMatrix();
		// (the click areas, from card units to the screen's)
		for (float[] r : hits) {
			r[0] = x0 + r[0] * k;
			r[1] = y0 + r[1] * k;
			r[2] *= k;
			r[3] *= k;
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	private void card(GuiGraphicsExtractor g, float W, float H, float mx, float my, float k) {
		Smooth.panel(g, -2, -2, W + 4, H + 4, 14, won ? 0xE6FFC83D : 0xC05B6577);
		Smooth.panel(g, 0, 0, W, H, 12, PANEL);
		JsonObject me = d.getAsJsonObject("me");
		String mode = d.get("mode").getAsString();
		int place = d.get("place").getAsInt(), of = d.get("of").getAsInt();
		// the title
		if (won) Smooth.text(g, "VICTORY ROYALE", W / 2, 12, 24, GOLD, Smooth.CENTER, true);
		else Smooth.text(g, "#" + place, W / 2, 10, 26, WHITE, Smooth.CENTER, true);
		String sub = mode + "   -   " + (team ? "your team " : "") + "#" + place + " of " + of + (team ? " teams" : "");
		if (!won) {
			List<String> w = new ArrayList<>();
			for (JsonElement e : d.getAsJsonArray("winners")) w.add(e.getAsString());
			if (!w.isEmpty()) sub += "   -   won by " + String.join(", ", w);
		}
		Smooth.text(g, sub, W / 2, 41, 8, MUTED, Smooth.CENTER, false);
		// your match
		float y = 58;
		Smooth.text(g, "YOUR MATCH", 14, y, 6.5F, MUTED, Smooth.LEFT, false);
		long secs = d.get("seconds").getAsLong();
		String time = secs / 60 + ":" + String.format(Locale.ROOT, "%02d", secs % 60);
		String[][] tiles = team
			? new String[][] { { "ELIMINATIONS", me.get("kills").getAsString() }, { "TOTAL DAMAGE", num(me.get("damage").getAsInt()) },
				{ "REVIVES", me.get("revives").getAsString() }, { "REBOOTS", me.get("reboots").getAsString() }, { "TIME SURVIVED", time } }
			: new String[][] { { "ELIMINATIONS", me.get("kills").getAsString() }, { "TOTAL DAMAGE", num(me.get("damage").getAsInt()) }, { "TIME SURVIVED", time },
				{ "PLACEMENT", "#" + place } };
		float gap = 6, tw = (W - 28 - gap * (tiles.length - 1)) / tiles.length;
		for (int i = 0; i < tiles.length; i++) {
			float tx = 14 + i * (tw + gap);
			boolean dmg = tiles[i][0].equals("TOTAL DAMAGE");
			Smooth.panel(g, tx, y + 9, tw, 40, 7, dmg ? 0xFF3A3320 : TILE);
			if (dmg) Smooth.panel(g, tx, y + 46, tw, 3, 0, GOLD);
			Smooth.text(g, tiles[i][1], tx + tw / 2, y + 14, 15, dmg ? GOLD : WHITE, Smooth.CENTER, false);
			Smooth.text(g, tiles[i][0], tx + tw / 2, y + 36, 5.5F, MUTED, Smooth.CENTER, false);
		}
		y += 60;
		// the team's rows (team modes), then the gold
		float gx = 14, gw = W - 28, boxH = 104;
		if (team) {
			float tx = 14, tableW = 268;
			Smooth.text(g, "TEAM RESULTS", tx, y, 6.5F, MUTED, Smooth.LEFT, false);
			var rows = d.getAsJsonArray("teamRows");
			boxH = Math.max(104, 22 + 13 * (rows.size() + 1) + 6);
			Smooth.panel(g, tx, y + 9, tableW, boxH, 7, TILE);
			float[] cx = { tx + 10, tx + 150, tx + 188, tx + 226, tx + 256 };
			String[] head = { "PLAYER", "ELIMS", "DAMAGE", "REVIVES", "REBOOTS" };
			for (int i = 0; i < head.length; i++) Smooth.text(g, head[i], cx[i], y + 14, 5.5F, MUTED, i == 0 ? Smooth.LEFT : Smooth.CENTER, false);
			int[] tot = new int[4];
			for (int r = 0; r < rows.size(); r++) {
				JsonObject row = rows.get(r).getAsJsonObject();
				float ry = y + 24 + r * 13;
				boolean you = Minecraft.getInstance().player != null && row.get("key").getAsString().equals(Minecraft.getInstance().player.getUUID().toString());
				if (you) Smooth.panel(g, tx + 3, ry - 1.5F, tableW - 6, 12, 4, 0xFF3A3320);
				Smooth.panel(g, cx[0], ry + 1.5F, 6, 6, 1.5F, 0xFF000000 | row.get("color").getAsInt());
				String name = row.get("name").getAsString() + (you ? "  (you)" : "");
				Smooth.text(g, name, cx[0] + 10, ry, 7, you ? GOLD : WHITE, Smooth.LEFT, false);
				int[] v = { row.get("kills").getAsInt(), row.get("damage").getAsInt(), row.get("revives").getAsInt(), row.get("reboots").getAsInt() };
				for (int i = 0; i < 4; i++) {
					tot[i] += v[i];
					Smooth.text(g, i == 1 ? num(v[i]) : String.valueOf(v[i]), cx[i + 1], ry, 7, WHITE, Smooth.CENTER, false);
				}
			}
			float ty = y + 24 + rows.size() * 13 + 2;
			Smooth.panel(g, tx + 4, ty - 2, tableW - 8, 0.8F, 0, 0xFF4A5366);
			Smooth.text(g, "Team total", cx[0], ty + 1, 7.5F, WHITE, Smooth.LEFT, true);
			for (int i = 0; i < 4; i++) Smooth.text(g, i == 1 ? num(tot[i]) : String.valueOf(tot[i]), cx[i + 1], ty + 1, i == 1 ? 8 : 7.5F, i == 1 ? GOLD : WHITE, Smooth.CENTER, true);
			gx = tx + tableW + 8;
			gw = W - 14 - gx;
		}
		gold(g, gx, y, gw, boxH);
		y += boxH + 16;
		// your emotes, a page at a time
		Smooth.text(g, "EMOTE", 14, y, 6.5F, MUTED, Smooth.LEFT, false);
		float cy = y + 9, chipGap = 5, arrow = 14, cw = (W - 28 - 2 * (arrow + chipGap) - chipGap * (CHIPS - 1)) / CHIPS;
		int pages = Math.max(1, (emotes.size() + CHIPS - 1) / CHIPS);
		page = Math.floorMod(page, pages);
		button(g, 14, cy, arrow, 16, "<", false, mx, my, () -> page--);
		for (int i = 0; i < CHIPS; i++) {
			int idx = page * CHIPS + i;
			if (idx >= emotes.size()) break;
			EmoteDef e = emotes.get(idx);
			button(g, 14 + arrow + chipGap + i * (cw + chipGap), cy, cw, 16, e.name(), false, mx, my, () -> {
				ClientPlayNetworking.send(new Net.Emote(0, e.id()));
				if (Minecraft.getInstance().player != null) SfxBank.play("ui_click", Minecraft.getInstance().player.position());
			});
		}
		button(g, W - 14 - arrow, cy, arrow, 16, ">", false, mx, my, () -> page++);
		// leave or watch
		float by = H - 30;
		button(g, W / 2 - 110, by, 104, 20, "Back to hub", true, mx, my, () -> {
			ClientPlayNetworking.send(new Net.Action("hub"));
			this.onClose();
		});
		button(g, W / 2 + 6, by, 104, 20, "Spectate", false, mx, my, this::onClose);
	}

	/** The gold earned, each reason on its own line, and what it goes towards in the Shop. */
	private void gold(GuiGraphicsExtractor g, float x, float y, float w, float h) {
		Smooth.text(g, "GOLD EARNED", x, y, 6.5F, MUTED, Smooth.LEFT, false);
		Smooth.panel(g, x, y + 9, w, h, 7, TILE);
		int total = 0;
		var lines = d.getAsJsonArray("gold");
		for (JsonElement e : lines) total += e.getAsJsonObject().get("gold").getAsInt();
		Smooth.icon(g, "gold", x + 8, y + 15, 16, -1);
		Smooth.text(g, "+" + total, x + 28, y + 14, 17, GOLD, Smooth.LEFT, true);
		boolean side = !team; // (Solo: the lines beside the total; teams: under it)
		float lx = side ? x + w / 2 + 4 : x + 8, ly = side ? y + 15 : y + 38;
		int i = 0;
		for (JsonElement e : lines) {
			JsonObject o = e.getAsJsonObject();
			float yy = ly + i++ * 10.5F;
			if (yy > y + h) break;
			Smooth.text(g, o.get("why").getAsString(), lx, yy, 6.5F, LIGHT, Smooth.LEFT, false);
			Smooth.text(g, "+" + o.get("gold").getAsInt(), x + w - 8, yy, 6.5F, GOLD, Smooth.RIGHT, false);
		}
		String next = d.get("nextItem").getAsString();
		int price = d.get("nextPrice").getAsInt(), have = d.get("goldNow").getAsInt();
		if (side && !next.isEmpty()) {
			float bw = w / 2 - 16;
			Smooth.text(g, "Next in the Shop:  " + next, x + 8, y + 50, 6.5F, LIGHT, Smooth.LEFT, false);
			Smooth.bar(g, x + 8, y + 62, bw, 5, Math.min(1F, have / (float) Math.max(1, price)), GOLD, 1);
			Smooth.text(g, have >= price ? "You can buy it now (" + have + " gold)" : have + " / " + price + " gold", x + 8, y + 71, 6, MUTED, Smooth.LEFT, false);
		}
	}

	/** A button (gold when it's the main one), lit under the mouse; what it does goes on the click list. */
	private void button(GuiGraphicsExtractor g, float x, float y, float w, float h, String label, boolean main, float mx, float my, Runnable act) {
		boolean over = mx >= x && mx <= x + w && my >= y && my <= y + h;
		if (main) Smooth.panel(g, x, y, w, h, 6, over ? 0xFFFFD86B : GOLD);
		else {
			Smooth.panel(g, x, y, w, h, 6, over ? 0xFF8A94A6 : 0xFF5B6577);
			Smooth.panel(g, x + 1, y + 1, w - 2, h - 2, 5, over ? 0xFF2C3340 : 0xFF1D222B);
		}
		float size = Math.min(8.5F, h * 0.45F);
		while (size > 4.5F && Smooth.width(label, size) > w - 6) size -= 0.5F;
		Smooth.text(g, label, x + w / 2, y + (h - size) / 2 - 0.5F, size, main ? 0xFF3B2400 : WHITE, Smooth.CENTER, false);
		hits.add(new float[] { x, y, w, h });
		actions.add(act);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT)
			for (int i = hits.size() - 1; i >= 0; i--) {
				float[] r = hits.get(i);
				if (event.x() >= r[0] && event.x() <= r[0] + r[2] && event.y() >= r[1] && event.y() <= r[1] + r[3]) {
					actions.get(i).run();
					return true;
				}
			}
		return super.mouseClicked(event, doubleClick);
	}

	/** The emote key (B) still opens the emote wheel over it. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (MinebloxRoyaleClient.emoteKey().matches(event)) {
			Minecraft.getInstance().gui.setScreen(new EmoteScreen());
			return true;
		}
		return super.keyPressed(event);
	}

	private static String num(int n) {
		return String.format(Locale.ROOT, "%,d", n);
	}
}
