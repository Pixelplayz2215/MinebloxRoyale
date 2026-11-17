package gg.minebloxroyale.client.avatar;

import com.mojang.blaze3d.platform.NativeImage;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.client.media.GameDirs;
import gg.minebloxroyale.sheets.AvatarPartDef;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.Sheets;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** The avatar meshes and face read from the player's Roblox install (avatar_parts sheet), loaded once into memory. */
public final class RobloxAssets {
	public static final Identifier TEXTURE = MinebloxRoyale.id("roblox_avatar");
	private static final Map<String, RobloxMesh> MESHES = new LinkedHashMap<>();
	private static boolean tried, ready;
	/**
	 * (2.1.0) The faces side by side in one texture, CELLS x CELLS of them, each 128 square: the player's own Roblox face
	 * first (cell 0, the default), then each facial expression (cosmetics sheet, slot expression) in the sheet's order.
	 */
	public static final int CELLS = 4;
	private static final Map<String, Integer> EXPRESSION_CELL = new LinkedHashMap<>();

	private RobloxAssets() {
	}

	public static boolean ready() {
		if (!tried) load();
		return ready;
	}

	public static @Nullable RobloxMesh mesh(String partId) {
		return MESHES.get(partId);
	}

	private static void load() {
		tried = true;
		Path content = GameDirs.robloxContent();
		if (content == null) {
			MinebloxRoyale.LOG.warn("BR avatar: Roblox not found; players keep the Minecraft look");
			return;
		}
		try {
			for (AvatarPartDef part : Sheets.AVATAR_PARTS) {
				RobloxMesh mesh = RobloxMesh.parse(Files.readAllBytes(content.resolve(part.mesh())));
				// Arms and legs: cut across the elbow / knee (joint space -> the mesh's own, centred on the part).
				boolean arm = part.swing().equals("arm");
				if (arm || part.swing().equals("leg")) {
					float[] planes = Bend.cuts(arm);
					for (int i = 0; i < planes.length; i++) planes[i] -= part.centerY() - part.pivotY();
					mesh = mesh.cut(planes);
				}
				MESHES.put(part.id(), mesh);
			}
			// One texture of faces (2.1.0: CELLS x CELLS of them, 128 square each): Roblox's face decal (cell 0) and the
			// facial expressions over white (tinted by the head color at draw time); the corner stays white for body parts.
			NativeImage img = new NativeImage(128 * CELLS, 128 * CELLS, false);
			img.fillRect(0, 0, 128 * CELLS, 128 * CELLS, 0xFFFFFFFF);
			Path face = content.resolve("textures/face.png");
			if (Files.isRegularFile(face)) {
				try (NativeImage f = NativeImage.read(Files.readAllBytes(face))) {
					paint(img, f, 0);
				}
			}
			EXPRESSION_CELL.clear();
			for (CosmeticDef c : Sheets.COSMETICS) {
				if (!c.slot().equals("expression") || EXPRESSION_CELL.size() + 1 >= CELLS * CELLS) continue;
				var res = Minecraft.getInstance().getResourceManager().getResource(MinebloxRoyale.id("textures/expression/" + c.shape() + ".png"));
				if (res.isEmpty()) continue;
				int cell = EXPRESSION_CELL.size() + 1;
				try (InputStream in = res.get().open(); NativeImage f = NativeImage.read(in)) {
					paint(img, f, cell);
					EXPRESSION_CELL.put(c.shape(), cell);
				}
			}
			for (int y = 0; y < 8; y++)
				for (int x = 0; x < 8; x++) img.setPixel(x, y, 0xFFFFFFFF);
			Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(() -> "roblox avatar", img));
			ready = true;
			MinebloxRoyale.LOG.info("BR avatar: Roblox R6 body loaded from {} ({} parts)", content, MESHES.size());
		} catch (Exception e) {
			MinebloxRoyale.LOG.warn("BR avatar: couldn't read Roblox avatar files", e);
		}
	}

	/** A face decal, any size, into its cell of the faces texture (128 square), over white. */
	private static void paint(NativeImage img, NativeImage f, int cell) {
		int ox = cell % CELLS * 128, oy = cell / CELLS * 128;
		for (int y = 0; y < 128; y++)
			for (int x = 0; x < 128; x++) {
				int c = f.getPixel(x * f.getWidth() / 128, y * f.getHeight() / 128);
				int a = c >>> 24;
				if (a == 0) continue;
				int r = blend((c >> 16) & 255, a), g = blend((c >> 8) & 255, a), bl = blend(c & 255, a);
				img.setPixel(ox + x, oy + y, 0xFF000000 | r << 16 | g << 8 | bl);
			}
	}

	/** (2.1.0) The cell of the faces texture for a facial expression; 0 (your Roblox face) for none or one not drawn. */
	public static int expressionCell(@Nullable CosmeticDef expression) {
		return expression == null ? 0 : EXPRESSION_CELL.getOrDefault(expression.shape(), 0);
	}

	private static int blend(int channel, int alpha) {
		return (channel * alpha + 255 * (255 - alpha)) / 255;
	}
}
