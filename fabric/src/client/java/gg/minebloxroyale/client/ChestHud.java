package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.match.Knocked;
import gg.minebloxroyale.match.LootChests;
import gg.minebloxroyale.sheets.HudElementDef;
import gg.minebloxroyale.sheets.Sheets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

/**
 * Under the crosshair, at a closed chest (every mode): "Hold F to search", and while F is held a bar filling over
 * chestSearchSeconds (creator: Fortnite's chests). The chest picked is the one the server will search (LootChests.target);
 * the bar runs on this game's own clock, and the chest opening is the server's.
 */
final class ChestHud {
	private static BlockPos searching;
	private static long since;

	private ChestHud() {
	}

	static void draw(GuiGraphicsExtractor g, Minecraft mc) {
		if (mc.player == null || mc.player.isSpectator() || Knocked.is(mc.player)) return;
		BlockPos chest = LootChests.target(mc.player);
		if (chest == null) {
			searching = null;
			return;
		}
		boolean held = MinebloxRoyaleClient.interactKey().isDown() && mc.gui.screen() == null;
		long now = System.currentTimeMillis();
		if (!held) searching = null;
		else if (!chest.equals(searching)) {
			searching = chest;
			since = now;
		}
		HudElementDef d = Sheets.HUD_BY_ID.get("interact");
		int cx = g.guiWidth() / 2 + d.x(), y = g.guiHeight() / 2 + d.y();
		if (searching == null) {
			SquadHud.prompt(g, mc, cx, y, "Hold " + SquadHud.key() + " to search");
			return;
		}
		float frac = (now - since) / (MinebloxRoyale.setting("chestSearchSeconds") * 1000F);
		SquadHud.progress(g, mc, cx, y, d.size(), "Searching", Math.min(1F, frac), 0xFFFFD45A);
	}
}
