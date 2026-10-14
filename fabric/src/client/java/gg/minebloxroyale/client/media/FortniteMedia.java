package gg.minebloxroyale.client.media;

import com.mojang.blaze3d.platform.NativeImage;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.sheets.MediaDef;
import gg.minebloxroyale.sheets.Sheets;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * The hub's music (the mod's own looping track) and the Fortnite splash art from the player's own install (media
 * sheet), shown on the hub billboard. Nothing is copied.
 */
public final class FortniteMedia {
	private static @Nullable SimpleSoundInstance hubLoop;
	private static long hubStartedAt;
	private static @Nullable Identifier billboard;
	private static boolean billboardTried;

	private FortniteMedia() {
	}

	public static @Nullable Path file(MediaDef m) {
		Path fn = GameDirs.fortnite();
		if (fn == null) return null;
		Path p = fn.resolve(m.file());
		return Files.isRegularFile(p) ? p : null;
	}

	/**
	 * Every client tick: while the player is in the hub, the mod's own hub track (sounds/music/hub_loop.ogg) loops with
	 * no gap (a looping sound, no delay; the track itself was made to join up seamlessly).
	 */
	public static void tick(boolean inHub) {
		if (!inHub) {
			stopHub();
			return;
		}
		var sounds = Minecraft.getInstance().getSoundManager();
		if (hubLoop != null && (sounds.isActive(hubLoop) || System.currentTimeMillis() - hubStartedAt < 1500)) return;
		hubLoop = new SimpleSoundInstance(MinebloxRoyale.id("music.hub_loop"), SoundSource.MUSIC, 0.5F, 1.0F, SoundInstance.createUnseededRandom(), true, 0,
			SoundInstance.Attenuation.NONE, 0, 0, 0, true);
		sounds.play(hubLoop);
		hubStartedAt = System.currentTimeMillis();
	}

	private static void stopHub() {
		if (hubLoop != null) Minecraft.getInstance().getSoundManager().stop(hubLoop);
		hubLoop = null;
	}

	/** The splash art (BMP) as a texture, loaded once. */
	public static @Nullable Identifier billboard() {
		if (!billboardTried) {
			billboardTried = true;
			MediaDef m = Sheets.MEDIA.stream().filter(x -> x.use().equals("billboard")).findFirst().orElse(null);
			Path p = m == null ? null : file(m);
			if (p != null) {
				try {
					BufferedImage bi = ImageIO.read(p.toFile());
					int w = Math.min(bi.getWidth(), 1024), h = Math.round(bi.getHeight() * (w / (float) bi.getWidth()));
					NativeImage img = new NativeImage(w, h, false);
					for (int y = 0; y < h; y++)
						for (int x = 0; x < w; x++)
							img.setPixel(x, y, 0xFF000000 | bi.getRGB(x * bi.getWidth() / w, y * bi.getHeight() / h));
					billboard = MinebloxRoyale.id("fortnite_splash");
					Minecraft.getInstance().getTextureManager().register(billboard, new DynamicTexture(() -> "fortnite splash", img));
					MinebloxRoyale.LOG.info("BR media: Fortnite splash loaded ({}x{})", w, h);
				} catch (Exception e) {
					MinebloxRoyale.LOG.warn("BR media: couldn't read {}", p, e);
				}
			}
		}
		return billboard;
	}

	public static void stopAll() {
		stopHub();
	}
}
