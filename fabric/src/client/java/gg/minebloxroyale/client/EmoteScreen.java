package gg.minebloxroyale.client;

import gg.minebloxroyale.client.media.SfxBank;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** B: the emote wheel - the 8 emotes picked in the Locker (K), in two columns. */
public class EmoteScreen extends Screen {
	public EmoteScreen() {
		super(Component.literal("Emotes"));
	}

	@Override
	protected void init() {
		List<EmoteDef> mine = ClientState.myEmotes().stream().map(Sheets.EMOTES_BY_ID::get).filter(e -> e != null && ClientState.unlocked(e)).toList();
		int rows = (mine.size() + 1) / 2, y0 = this.height / 2 - rows * 12;
		for (int i = 0; i < mine.size(); i++) {
			EmoteDef e = mine.get(i);
			int x = this.width / 2 + (i % 2 == 0 ? -152 : 2), y = y0 + (i / 2) * 24;
			this.addRenderableWidget(Button.builder(Component.literal(e.name()), btn -> {
				ClientPlayNetworking.send(new Net.Emote(0, e.id()));
				if (Minecraft.getInstance().player != null) SfxBank.play("ui_click", Minecraft.getInstance().player.position());
				this.onClose();
			}).bounds(x, y, 150, 20).build());
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int rows = (Math.max(1, ClientState.myEmotes().size()) + 1) / 2;
		g.centeredText(this.font, "EMOTES  -  pick your 8 in the Locker (K)", this.width / 2, this.height / 2 - rows * 12 - 16, 0xFFFFD42A);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
