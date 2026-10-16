package gg.minebloxroyale.client;

import gg.minebloxroyale.client.media.SfxBank;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * B: the emote wheel - the 8 emotes picked in the Locker (K), in two columns; in the HUD's smooth style like the Locker
 * (2.0.0-test6); 2.0.0-test6.1 (creator: "Emote wheel should center the text in the click box (also text slightly
 * bigger"): each name in the middle of its tile, a third bigger, the tiles taller.
 */
public class EmoteScreen extends Screen {
	/** A row of tiles: each tile and the gap under it. */
	private static final int ROW = 28;

	public EmoteScreen() {
		super(Component.literal("Emotes"));
	}

	@Override
	protected void init() {
		List<EmoteDef> mine = ClientState.myEmotes().stream().map(Sheets.EMOTES_BY_ID::get).filter(e -> e != null && ClientState.unlocked(e)).toList();
		int rows = (mine.size() + 1) / 2, y0 = this.height / 2 - rows * ROW / 2;
		for (int i = 0; i < mine.size(); i++) {
			EmoteDef e = mine.get(i);
			int x = this.width / 2 + (i % 2 == 0 ? -152 : 2), y = y0 + (i / 2) * ROW;
			SmoothButton b = new SmoothButton(x, y, 150, ROW - 4, e.name(), SmoothButton.Look.TILE, btn -> {
				ClientPlayNetworking.send(new Net.Emote(0, e.id()));
				if (Minecraft.getInstance().player != null) SfxBank.play("ui_click", Minecraft.getInstance().player.position());
				this.onClose();
			});
			b.scale = 1.33F;
			b.center = true;
			this.addRenderableWidget(b);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int rows = (Math.max(1, ClientState.myEmotes().size()) + 1) / 2;
		float top = this.height / 2F - rows * ROW / 2F - 32;
		Smooth.panel(g, this.width / 2F - 162, top, 324, rows * ROW + 44, 10, 0xE6161A22);
		super.extractRenderState(g, mouseX, mouseY, a);
		Smooth.text(g, "EMOTES", this.width / 2F - 152, top + 9, 12, 0xFFFFC83D, Smooth.LEFT, true);
		Smooth.text(g, "Pick your 8 in the Locker (K)", this.width / 2F + 152, top + 12, 7.5F, 0xFF9AA3B2, Smooth.RIGHT, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
