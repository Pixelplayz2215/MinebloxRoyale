package gg.minebloxroyale.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.net.VersionCheck;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * (2.0.0, creator: "a github catcher on the mod (starting in v2.0.0) that will have a popup screen to say outdated mod
 * with a link to the github to update") Two things for this side of the game:
 * <ul>
 * <li>At start, the newest release on GitHub; when it's newer than this game, a popup once you're in your world says
 * so, with Melty's and GitHub's pages.</li>
 * <li>Joining a room: answering the host's version (VersionCheck), and when the two differ, not trying again but
 * back to your own world with a popup saying why. A host older than 2.0.0 never asks: this side turns itself away
 * from one (it's a Mineblox Royale room when it takes the mod's messages).</li>
 * </ul>
 */
public final class UpdateCheck {
	private UpdateCheck() {
	}

	private static final String LATEST = "https://api.github.com/repos/Pixelplayz2215/MinebloxRoyale/releases/latest";
	/** The newest release on GitHub when it's newer than this game ("2.1.0"), else null. */
	private static volatile @Nullable String newest;
	private static boolean shownNewest;
	/** The room we were last turned away from for its version: its version ("" when older than 2.0.0). */
	private static @Nullable String refusedBy;
	private static boolean hostAsked;
	private static long inWorldSince;

	public static void init() {
		ClientConfigurationConnectionEvents.INIT.register((handler, mc) -> hostAsked = false);
		ClientConfigurationNetworking.registerGlobalReceiver(VersionCheck.Hello.TYPE, (p, ctx) -> {
			hostAsked = true;
			String mine = MinebloxRoyale.version();
			if (!p.version().equals(mine)) {
				refusedBy = p.version();
				MinebloxRoyale.LOG.info("BR: this room is on Mineblox Royale {}, this game {}: not joining", p.version(), mine);
			}
			ctx.responseSender().sendPacket(new VersionCheck.Hello(mine));
		});
	}

	/** At start: ask GitHub for the newest release, in the background (nothing happens without the internet). */
	public static void checkGitHub() {
		Thread t = new Thread(() -> {
			try {
				HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
				HttpRequest req = HttpRequest.newBuilder(URI.create(LATEST)).timeout(Duration.ofSeconds(10))
					.header("Accept", "application/vnd.github+json").header("User-Agent", "MinebloxRoyale/" + MinebloxRoyale.version()).GET().build();
				HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() != 200) return;
				JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
				if (!o.has("tag_name")) return;
				String tag = o.get("tag_name").getAsString();
				String v = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
				if (VersionCheck.newer(v, MinebloxRoyale.version())) {
					newest = v;
					MinebloxRoyale.LOG.info("BR: Mineblox Royale {} is out (this game: {})", v, MinebloxRoyale.version());
				}
			} catch (Exception e) {
				MinebloxRoyale.LOG.info("BR: couldn't check GitHub for a newer version ({})", e.toString());
			}
		}, "Mineblox Royale update check");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * In a world: an older host's room (it takes the mod's messages but never asked our version) is left at once, the
	 * same way a newer one turns us away.
	 */
	public static void onJoinWorld(ClientPacketListener handler, Minecraft mc) {
		inWorldSince = System.currentTimeMillis();
		if (hostAsked || mc.getSingleplayerServer() != null || !ClientPlayNetworking.canSend(Net.Action.TYPE)) return;
		refusedBy = "";
		MinebloxRoyale.LOG.info("BR: this room is on Mineblox Royale older than 2.0.0: leaving it");
		handler.getConnection().disconnect(Component.literal("")
			.append(Component.literal("Can't join: different versions of Mineblox Royale\n\n").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD))
			.append(Component.literal("This room is on an older version. You have v" + MinebloxRoyale.version() + ".\n").withStyle(net.minecraft.ChatFormatting.WHITE))
			.append(Component.literal("Ask your friend to update to the newest version.\n\n").withStyle(net.minecraft.ChatFormatting.YELLOW))
			.append(Component.literal("Get the newest version on Melty or GitHub:\n" + VersionCheck.MELTY + "\n" + VersionCheck.GITHUB).withStyle(net.minecraft.ChatFormatting.GRAY)));
	}

	/** Whether the last join was turned away for its version (AutoWorld: then no trying again, home after the message). */
	public static boolean refused() {
		return refusedBy != null;
	}

	/** Each tick: a popup to show, once in your own world (not in a match, nothing else on screen, settled a moment). */
	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.gui.screen() != null || ClientState.inMatch()) return;
		if (System.currentTimeMillis() - inWorldSince < 3000) return;
		if (refusedBy != null && mc.getSingleplayerServer() != null) {
			String room = refusedBy;
			refusedBy = null;
			mc.gui.setScreen(new Popup(true, room));
			return;
		}
		if (newest != null && !shownNewest) {
			shownNewest = true;
			mc.gui.setScreen(new Popup(false, newest));
		}
	}

	/** The popup: a newer version is out, or a room was on a different version; Melty's and GitHub's pages, and OK. */
	static final class Popup extends Screen {
		private static final int W = 300;
		private final boolean refused;
		private final List<String> lines = new ArrayList<>();
		private final String heading;

		Popup(boolean refused, String version) {
			super(Component.literal(refused ? "Couldn't join" : "Update available"));
			this.refused = refused;
			String mine = MinebloxRoyale.version();
			if (refused) {
				heading = "COULDN'T JOIN THAT ROOM";
				lines.add(version.isEmpty() ? "That room is on an older version of Mineblox Royale." : "That room is on Mineblox Royale v" + version + ".");
				lines.add("You have v" + mine + ". Only the same version can play together.");
				lines.add(!version.isEmpty() && VersionCheck.newer(version, mine) ? "Update your game to join them." : "Ask your friend to update to the newest version.");
			} else {
				heading = "UPDATE AVAILABLE";
				lines.add("Mineblox Royale v" + version + " is out!");
				lines.add("You have v" + mine + ". Update to get the newest");
				lines.add("features and fixes, and to play with friends on it.");
			}
		}

		@Override
		protected void init() {
			int y = this.height / 2 + 22, x = this.width / 2 - W / 2 + 10, bw = (W - 20 - 8) / 3;
			this.addRenderableWidget(new SmoothButton(x, y, bw, 20, "Melty", SmoothButton.Look.MAIN, b -> ConfirmLinkScreen.confirmLinkNow(this, URI.create(VersionCheck.MELTY))));
			this.addRenderableWidget(new SmoothButton(x + bw + 4, y, bw, 20, "GitHub", SmoothButton.Look.MAIN, b -> ConfirmLinkScreen.confirmLinkNow(this, URI.create(VersionCheck.GITHUB))));
			SmoothButton ok = new SmoothButton(x + 2 * (bw + 4), y, bw, 20, refused ? "OK" : "Later", SmoothButton.Look.TILE, b -> this.onClose());
			ok.center = true; // (creator: "The "ok" box should be centered like the other 2")
			this.addRenderableWidget(ok);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			float top = this.height / 2F - 58;
			Smooth.panel(g, this.width / 2F - W / 2F, top, W, 112, 10, 0xEE161A22);
			super.extractRenderState(g, mouseX, mouseY, a);
			Smooth.text(g, heading, this.width / 2F, top + 10, 12, 0xFFFFC83D, Smooth.CENTER, true);
			for (int i = 0; i < lines.size(); i++)
				Smooth.text(g, lines.get(i), this.width / 2F, top + 30 + i * 11, 7.5F, i == 0 ? 0xFFF4F6FA : 0xFFB8C0CC, Smooth.CENTER, false);
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}
	}
}
