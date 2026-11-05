package gg.minebloxroyale.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * (2.0.0, players' Melty: games on PCs whose memory was all used up closed by themselves, and the player was never told
 * why) The last game ran out of memory (LastRun): a popup in the middle of the screen once you're in a world, saying
 * what happened and what to do, until OK (creator: "Rather than in the chat, make a popup on screen so they can't ignore
 * it"). Shown by UpdateCheck.tick, like the update popup.
 */
final class MemoryPopup extends Screen {
	private static final String HEADING = "YOUR LAST GAME RAN OUT OF MEMORY";
	/** What happened, then what to do ("": a gap). */
	private static final List<String> LINES = List.of(
		"Windows had no memory left to give it, so it closed.",
		"",
		"Before you play, close other programs: web browsers,",
		"Discord and other games use a lot of memory.",
		"",
		"Still happening? Let Windows size its page file: search",
		"Start for \"Adjust the appearance and performance of",
		"Windows\", then Advanced > Change, and tick",
		"\"Automatically manage paging file size\".");
	private static final float HEAD_SIZE = 12, LINE_SIZE = 7.5F, LINE_STEP = 11;

	MemoryPopup() {
		super(Component.literal("Your last game ran out of memory"));
	}

	/** As wide as the widest line, at least 300. */
	private static float panelWidth() {
		float w = Smooth.width(HEADING, HEAD_SIZE);
		for (String l : LINES) w = Math.max(w, Smooth.width(l, LINE_SIZE));
		return Math.max(300, w + 28);
	}

	private static float panelHeight() {
		return 30 + LINES.size() * LINE_STEP + 8 + 20 + 10;
	}

	private float top() {
		return this.height / 2F - panelHeight() / 2;
	}

	@Override
	protected void init() {
		int bw = 90, y = Math.round(top() + panelHeight() - 10 - 20);
		SmoothButton ok = new SmoothButton(this.width / 2 - bw / 2, y, bw, 20, "OK", SmoothButton.Look.MAIN, b -> this.onClose());
		ok.center = true;
		this.addRenderableWidget(ok);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		float w = panelWidth(), top = top();
		Smooth.panel(g, this.width / 2F - w / 2, top, w, panelHeight(), 10, 0xEE161A22);
		super.extractRenderState(g, mouseX, mouseY, a);
		Smooth.text(g, HEADING, this.width / 2F, top + 10, HEAD_SIZE, 0xFFFFC83D, Smooth.CENTER, true);
		for (int i = 0; i < LINES.size(); i++)
			Smooth.text(g, LINES.get(i), this.width / 2F, top + 30 + i * LINE_STEP, LINE_SIZE, i == 0 ? 0xFFF4F6FA : 0xFFB8C0CC, Smooth.CENTER, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
