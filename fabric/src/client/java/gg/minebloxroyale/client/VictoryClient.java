package gg.minebloxroyale.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.client.media.SfxBank;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * The end of a match on this side (2.0.0-test5, creator: the Victory Royale screen, "Solo rounds should ONLY be option D.
 * Teams is a mix of C and D"): Solo, a winner gets the big gold banner over the game for its first seconds, the camera
 * slowly circling them as they emote, confetti falling; in Duos, Trios and Squads the winning team stands on its stage
 * (MatchManager.victoryStage) in movie bars, name plates over each, the camera sweeping across them. Then the results
 * card (VictoryScreen). Anyone else gets the card straight away, with where they placed. The server sends it all
 * (Net.Victory, MatchStats.results); held still meanwhile, a winner can only look round and emote.
 */
public final class VictoryClient {
	/** How long the banner or the stage plays before the results card (seconds). */
	static final float INTRO = 4.5F;
	private static @Nullable JsonObject data;
	private static long startNanos;
	private static boolean opened;
	/** Which way the winner faced when it began (the camera starts in front of them). */
	private static float startYaw;

	private VictoryClient() {
	}

	public static void receive(String json) {
		Minecraft mc = Minecraft.getInstance();
		data = JsonParser.parseString(json).getAsJsonObject();
		startNanos = System.nanoTime();
		opened = false;
		startYaw = mc.player == null ? 0 : mc.player.getYRot();
		if (won() && mc.player != null) SfxBank.play("victory_fanfare", mc.player.position());
	}

	static @Nullable JsonObject data() {
		return data;
	}

	static float seconds() {
		return (System.nanoTime() - startNanos) / 1e9F;
	}

	static boolean won() {
		return data != null && data.get("won").getAsBoolean();
	}

	static boolean team() {
		return data != null && data.get("team").getAsBoolean();
	}

	/** The winning team's stage (its middle, where they stand), or null (Solo, or not won). */
	static @Nullable Vec3 stage() {
		return data != null && data.has("stageX") ? new Vec3(data.get("stageX").getAsDouble(), data.get("stageY").getAsDouble(), data.get("stageZ").getAsDouble()) : null;
	}

	/** The banner or the stage is playing now (a winner's first seconds). */
	public static boolean intro() {
		return won() && seconds() < INTRO;
	}

	/** Each tick: confetti for the winners, the results card when it's time, forgotten back in the hub. */
	public static void tick(Minecraft mc) {
		if (data == null || mc.player == null || mc.level == null) return;
		if (!ClientState.inMatch() && seconds() > 2) {
			data = null;
			return;
		}
		float t = seconds();
		if (won() && t < 8) {
			Vec3 c = stage() != null ? stage() : mc.player.position();
			RandomSource r = mc.level.getRandom();
			int[] colors = { 0xFFC83D, 0xFF5A5A, 0x3AA0FF, 0x3CD070, 0xFFFFFF, 0xFF8A1F };
			for (int i = 0; i < 5; i++)
				mc.level.addParticle(new DustParticleOptions(colors[r.nextInt(colors.length)], 1.4F), c.x + r.nextGaussian() * (stage() != null ? 7 : 3), c.y + 5 + r.nextDouble() * 3,
					c.z + r.nextGaussian() * 3, 0, -0.05, 0);
		}
		if (!opened && mc.gui.screen() == null && (won() ? t >= INTRO : t >= 1.5F)) {
			opened = true;
			mc.gui.setScreen(new VictoryScreen(data));
		}
	}

	/**
	 * Where the camera is while the banner, the stage or the results card is up for a winner: {x, y, z, yaw, pitch}, or
	 * null (the usual camera; once the card is put away, they look round as they like).
	 */
	public static double @Nullable [] camera(float partialTicks) {
		Minecraft mc = Minecraft.getInstance();
		if (!won() || mc.player == null || !(intro() || mc.gui.screen() instanceof VictoryScreen)) return null;
		float t = seconds();
		Vec3 c;
		double r, h, a;
		Vec3 stage = stage();
		if (stage != null) {
			int n = Math.max(1, data.getAsJsonArray("teamRows").size());
			c = stage.add(0, 1.3, 0);
			r = 7 + 1.6 * n;
			h = 1.8;
			a = 0.5 * Math.sin(t * 0.35); // (sweeping across the front of the stage)
		} else {
			c = mc.player.getPosition(partialTicks).add(0, 1.1, 0);
			r = 4.2;
			h = 0.7;
			a = -Math.toRadians(startYaw) + t * 0.22; // (from in front of them, slowly round)
		}
		double x = c.x + Math.sin(a) * r, y = c.y + h, z = c.z + Math.cos(a) * r;
		double dx = c.x - x, dy = c.y - y, dz = c.z - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		return new double[] { x, y, z, yaw, pitch };
	}

	/**
	 * The banner (Solo) or the stage's movie bars and name plates (teams) over the game, while they play. True when it
	 * drew one: the usual HUD stays hidden meanwhile.
	 */
	public static boolean hud(GuiGraphicsExtractor g, Minecraft mc) {
		if (!intro()) return false;
		float t = seconds(), in = Math.min(1, t / 0.35F), w = g.guiWidth(), h = g.guiHeight();
		if (stage() != null) podium(g, mc, t, w, h);
		else banner(g, t, in, w, h);
		return true;
	}

	private static final int GOLD = 0xFFFFC83D, GOLD_DARK = 0xFFC98A12, INK = 0xFF3B2400;

	/** Option D's first part: rays of light behind a slanted gold band, "#1 VICTORY ROYALE" across it, the mode under it. */
	private static void banner(GuiGraphicsExtractor g, float t, float in, float w, float h) {
		float cx = w / 2, cy = h * 0.24F;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		for (int k = 0; k < 18; k++) { // the rays, turning slowly
			g.pose().pushMatrix();
			g.pose().rotate((float) Math.toRadians(k * 20 + t * 6));
			Smooth.panel(g, 0, -4, w * 0.75F, 8, 4, ((k % 2 == 0 ? 0x30 : 0x18) << 24) | 0xFFE9A8);
			g.pose().popMatrix();
		}
		// the band, sliding in from the left
		g.pose().rotate((float) Math.toRadians(-1.4));
		g.pose().translate((1 - in) * -w, 0);
		Smooth.panel(g, -w * 0.6F, -30, w * 1.2F, 60, 0, GOLD_DARK);
		Smooth.panel(g, -w * 0.6F, -27, w * 1.2F, 54, 0, GOLD);
		Smooth.panel(g, -w * 0.6F, -25, w * 1.2F, 5, 0, 0xCCFFE08A);
		g.pose().pushMatrix();
		g.pose().mul(new Matrix3x2f(1, 0, -0.2F, 1, 0, 0)); // (leaning, like the banner's lettering in Fortnite)
		float tw = Smooth.width("VICTORY ROYALE", 34), nw = Smooth.width("#1", 24);
		float left = -(tw + nw + 10) / 2;
		Smooth.text(g, "#1", left, -10, 24, INK, Smooth.LEFT, false);
		Smooth.text(g, "VICTORY ROYALE", left + nw + 10, -17, 34, INK, Smooth.LEFT, false);
		g.pose().popMatrix();
		g.pose().popMatrix();
		String mode = data.get("mode").getAsString().toUpperCase(Locale.ROOT);
		float mw = Smooth.width(mode, 9) + 20;
		Smooth.panel(g, cx - mw / 2, cy + 36, mw, 16, 8, 0xD01D222B);
		Smooth.text(g, mode, cx, cy + 39.5F, 9, 0xFFF4F6FA, Smooth.CENTER, false);
	}

	/** Option C: the team on its stage, between black movie bars, a name plate over each of them. */
	private static void podium(GuiGraphicsExtractor g, Minecraft mc, float t, float w, float h) {
		float bar = h * 0.13F * Math.min(1, t / 0.5F);
		g.fill(0, 0, (int) Math.ceil(w), (int) Math.ceil(bar), 0xFF000000);
		g.fill(0, (int) (h - bar), (int) Math.ceil(w), (int) Math.ceil(h), 0xFF000000);
		String top = data.get("mode").getAsString().toUpperCase(Locale.ROOT) + "   -   YOUR TEAM WINS";
		Smooth.text(g, top, w / 2, bar / 2 - 5, 10, 0xFFD9DEE7, Smooth.CENTER, false);
		g.pose().pushMatrix();
		g.pose().translate(w / 2, h - bar / 2);
		g.pose().mul(new Matrix3x2f(1, 0, -0.18F, 1, 0, 0));
		Smooth.text(g, "#1 VICTORY ROYALE", 0, -12, 24, GOLD, Smooth.CENTER, true);
		g.pose().popMatrix();
		// name plates: each teammate found near the stage, over their head
		Vec3 stage = stage();
		for (JsonElement el : data.getAsJsonArray("teamRows")) {
			JsonObject row = el.getAsJsonObject();
			String name = row.get("name").getAsString();
			Entity who = null;
			for (Entity e : mc.level.entitiesForRendering())
				if ((e instanceof BotEntity || e instanceof net.minecraft.world.entity.player.Player) && e.getName().getString().equals(name) && e.distanceToSqr(stage) < 30 * 30) who = e;
			if (who == null) continue;
			double[] at = Waypoint.project(mc, g, who.getX(), who.getY() + 2.7, who.getZ());
			if (at[2] < 0) continue;
			boolean you = mc.player != null && row.get("key").getAsString().equals(mc.player.getUUID().toString());
			float pw = Smooth.width(name, 8) + 16, px = (float) at[0] - pw / 2, py = (float) at[1] - 14;
			Smooth.panel(g, px, py, pw, 14, 5, 0xCC11151B);
			Smooth.panel(g, px, py, 3, 14, 0, 0xFF000000 | row.get("color").getAsInt());
			Smooth.text(g, name, px + pw / 2 + 1.5F, py + 3, 8, you ? GOLD : 0xFFF4F6FA, Smooth.CENTER, false);
		}
	}
}
