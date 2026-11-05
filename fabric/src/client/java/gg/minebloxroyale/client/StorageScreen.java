package gg.minebloxroyale.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * (2.0.0) World storage (WorldStorage): which drive the world and its islands are kept on. Picking one asks first, then
 * the world saves and closes, moves (a bar shows how far), and opens again from there. Also says when the drive picked
 * before can't be found at start (a drive that isn't plugged in), with Try again or the game folder instead.
 */
final class StorageScreen extends Screen {
	private enum Mode {
		PICK, CONFIRM, MOVING, MISSING
	}

	private static final int GOLD = 0xFFFFC83D, WHITE = 0xFFF4F6FA, GREY = 0xFFB8C0CC, RED = 0xFFFF6B6B;
	private static final float HEAD_SIZE = 12, LINE_SIZE = 7.5F, LINE_STEP = 11;
	private final @Nullable Screen parent;
	private Mode mode;
	private WorldStorage.@Nullable Place chosen;
	private List<WorldStorage.Place> places = List.of();
	private long size;
	private @Nullable String note;

	StorageScreen(@Nullable Screen parent) {
		this(parent, Mode.PICK);
	}

	private StorageScreen(@Nullable Screen parent, Mode mode) {
		super(Component.literal("World storage"));
		this.parent = parent;
		this.mode = mode;
	}

	static StorageScreen moving() {
		return new StorageScreen(null, Mode.MOVING);
	}

	static StorageScreen missingDrive() {
		return new StorageScreen(null, Mode.MISSING);
	}

	private String heading() {
		return switch (mode) {
			case PICK -> "WORLD STORAGE";
			case CONFIRM -> "MOVE YOUR WORLD?";
			case MOVING -> WorldStorage.moveError != null ? "THE WORLD COULDN'T MOVE" : "MOVING YOUR WORLD";
			case MISSING -> "YOUR WORLD'S DRIVE ISN'T HERE";
		};
	}

	/** The lines under the heading ("": a gap). */
	private List<String> lines() {
		List<String> l = new ArrayList<>();
		switch (mode) {
			case PICK -> {
				l.add("Your world and its islands (" + WorldStorage.gb(size) + ") are kept on " + WorldStorage.name(WorldStorage.folder()) + ".");
				l.add("Islands take more room while the next one is built.");
				l.add("Pick where to keep them:");
			}
			case CONFIRM -> {
				WorldStorage.Place p = chosen;
				int friends = this.minecraft.getSingleplayerServer() == null ? 0 : Math.max(0, this.minecraft.getSingleplayerServer().getPlayerCount() - 1);
				l.add("Your world (" + WorldStorage.gb(size) + ") moves to " + (p == null ? "" : p.name()) + ":");
				if (p != null) l.add(p.folder().toString());
				l.add("");
				l.add("It saves and closes, moves, then opens again. This takes");
				l.add("from a few seconds to a minute.");
				if (friends > 0) l.add(friends == 1 ? "Your friend in your room goes back to their own world." : "Your " + friends + " friends in your room go back to their own worlds.");
			}
			case MOVING -> {
				String err = WorldStorage.moveError;
				if (err != null && WorldStorage.restartNeeded()) {
					l.add(err);
				} else if (err != null) {
					l.add("Your world is safe where it was: nothing was removed.");
					l.add("");
					l.add(err);
				} else {
					long done = WorldStorage.movedBytes, total = WorldStorage.totalBytes;
					l.add(total <= 0 ? "Saving your world..." : WorldStorage.gb(done) + " of " + WorldStorage.gb(total) + " copied");
					l.add("Don't close the game until it's done.");
				}
			}
			case MISSING -> {
				Path f = WorldStorage.missing();
				l.add("Your world is kept on " + (f == null ? "another drive" : WorldStorage.drive(f)) + ", which can't be found.");
				if (f != null) l.add(f.toString());
				l.add("");
				l.add("Plug the drive back in and press Try again, or play from this");
				l.add("PC's game folder instead (the copy on that drive stays there).");
			}
		}
		return l;
	}

	private float panelWidth() {
		float w = Smooth.width(heading(), HEAD_SIZE);
		for (String s : lines()) w = Math.max(w, Smooth.width(s, LINE_SIZE));
		return Math.min(this.width - 16, Math.max(320, w + 28));
	}

	/** Rows of buttons under the lines. */
	private int rows() {
		return switch (mode) {
			case PICK -> places.size() + 1;
			case CONFIRM, MISSING -> 1;
			case MOVING -> WorldStorage.moveError != null ? 1 : 0;
		};
	}

	private float panelHeight() {
		float h = 30 + lines().size() * LINE_STEP + 6;
		if (mode == Mode.MOVING && WorldStorage.moveError == null) h += 14; // (the bar)
		if (note != null) h += LINE_STEP;
		return h + rows() * 24 + 6;
	}

	private float top() {
		return Math.max(4, this.height / 2F - panelHeight() / 2);
	}

	@Override
	protected void init() {
		if (mode == Mode.PICK || mode == Mode.CONFIRM) {
			size = WorldStorage.size(WorldStorage.folder());
			places = WorldStorage.places(size);
			note = WorldStorage.cantMove(this.minecraft);
		}
		build();
	}

	private void build() {
		this.clearWidgets();
		float w = panelWidth();
		int x = Math.round(this.width / 2F - w / 2 + 10), bw = Math.round(w - 20);
		int y = Math.round(top() + 30 + lines().size() * LINE_STEP + 6 + (note != null ? LINE_STEP : 0) + (mode == Mode.MOVING && WorldStorage.moveError == null ? 14 : 0));
		switch (mode) {
			case PICK -> {
				for (WorldStorage.Place p : places) {
					String label = Character.toUpperCase(p.name().charAt(0)) + p.name().substring(1) + "   " + WorldStorage.gb(p.free()) + " free" + (p.inUse() ? "   (in use)" : p.why() != null ? "   (" + p.why() + ")" : "");
					SmoothButton b = new SmoothButton(x, y, bw, 20, label, SmoothButton.Look.TILE, btn -> {
						chosen = p;
						mode = Mode.CONFIRM;
						build();
					});
					b.on = p.inUse();
					b.locked = p.why() != null;
					b.active = !p.inUse() && p.why() == null && note == null;
					this.addRenderableWidget(b);
					y += 24;
				}
				SmoothButton back = new SmoothButton(this.width / 2 - 45, y, 90, 20, "Back", SmoothButton.Look.TILE, btn -> this.onClose());
				back.center = true;
				this.addRenderableWidget(back);
			}
			case CONFIRM -> {
				int half = (bw - 4) / 2;
				this.addRenderableWidget(new SmoothButton(x, y, half, 20, "Move it", SmoothButton.Look.MAIN, btn -> {
					WorldStorage.Place p = chosen;
					if (p != null && WorldStorage.cantMove(this.minecraft) == null) WorldStorage.move(this.minecraft, p.folder());
				}));
				SmoothButton cancel = new SmoothButton(x + half + 4, y, half, 20, "Cancel", SmoothButton.Look.TILE, btn -> {
					mode = Mode.PICK;
					build();
				});
				cancel.center = true;
				this.addRenderableWidget(cancel);
			}
			case MOVING -> {
				if (WorldStorage.moveError != null) {
					SmoothButton ok = new SmoothButton(this.width / 2 - 45, y, 90, 20, "OK", SmoothButton.Look.MAIN, btn -> WorldStorage.afterFailedMove(this.minecraft));
					this.addRenderableWidget(ok);
				}
			}
			case MISSING -> {
				int half = (bw - 4) / 2;
				this.addRenderableWidget(new SmoothButton(x, y, half, 20, "Try again", SmoothButton.Look.MAIN, btn -> {
					if (!WorldStorage.retryMissing(this.minecraft)) {
						note = "Still not found.";
						build();
					}
				}));
				SmoothButton here = new SmoothButton(x + half + 4, y, half, 20, "Use the game folder", SmoothButton.Look.TILE, btn -> WorldStorage.forgetMissing(this.minecraft));
				here.center = true;
				this.addRenderableWidget(here);
			}
		}
	}

	private boolean hadError;

	@Override
	public void tick() {
		// (the move finished with a problem: the OK button appears)
		if (mode == Mode.MOVING && WorldStorage.moveError != null && !hadError) {
			hadError = true;
			build();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		float w = panelWidth(), top = top(), x = this.width / 2F;
		Smooth.panel(g, x - w / 2, top, w, panelHeight(), 10, 0xEE161A22);
		super.extractRenderState(g, mouseX, mouseY, a);
		Smooth.text(g, heading(), x, top + 10, HEAD_SIZE, GOLD, Smooth.CENTER, true);
		List<String> l = lines();
		boolean failed = mode == Mode.MOVING && WorldStorage.moveError != null;
		for (int i = 0; i < l.size(); i++) {
			float size = LINE_SIZE;
			while (size > 5 && Smooth.width(l.get(i), size) > w - 16) size -= 0.25F; // (a long folder name)
			int color = failed && i == l.size() - 1 ? RED : i == 0 ? WHITE : GREY;
			Smooth.text(g, l.get(i), x, top + 30 + i * LINE_STEP, size, color, Smooth.CENTER, false);
		}
		float y = top + 30 + l.size() * LINE_STEP + 2;
		if (mode == Mode.MOVING && !failed) {
			long total = WorldStorage.totalBytes;
			Smooth.bar(g, x - w / 2 + 16, y, w - 32, 7, total <= 0 ? 0 : WorldStorage.movedBytes / (float) total, GOLD, 1);
		}
		if (note != null) Smooth.text(g, note, x, y + 2, LINE_SIZE, RED, Smooth.CENTER, false);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return mode == Mode.PICK || mode == Mode.CONFIRM;
	}

	@Override
	public void onClose() {
		if (mode == Mode.CONFIRM) {
			mode = Mode.PICK;
			build();
			return;
		}
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return mode == Mode.PICK || mode == Mode.CONFIRM;
	}
}
