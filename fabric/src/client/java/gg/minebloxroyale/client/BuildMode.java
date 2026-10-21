package gg.minebloxroyale.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import gg.minebloxroyale.match.Builds;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.BuildPieceDef;
import gg.minebloxroyale.sheets.MaterialDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Fortnite build mode (key G, builds sheet). While on: number keys 1-3 pick Wall / Floor / Ramp, left mouse places
 * (hold to keep placing), right mouse switches Wood / Brick / Metal, and a blue ghost shows where the piece goes.
 * G again goes back to weapons, on the slot you had.
 *
 * Every one of those keys is the player's to change (2.0.0, Ultraslayerz via the creator: "custom keybinds for building
 * (changing mats, open build gui, placing, changing type of build, etc."): Options > Controls > Mineblox Royale: Building.
 * Each starts on the key it always used. While building, a build key wins over anything else on the same key (drop,
 * inventory, the hotbar...), so they can go on Q, E, F, C and so on as in Fortnite.
 */
public final class BuildMode {
	private static boolean active;
	private static int savedSlot, material, placeCooldown;
	/** Place a piece, switch material, and one key per piece (by its id), in Controls. */
	private static KeyMapping place, switchMaterial;
	private static final Map<String, KeyMapping> PIECE_KEYS = new LinkedHashMap<>();

	private BuildMode() {
	}

	public static boolean active() {
		return active;
	}

	/** The building keys, in their own Controls section: each on the key it always used (left / right mouse, 1-3). */
	public static void registerKeys(KeyMapping.Category cat) {
		place = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minebloxroyale.build_place", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_LEFT, cat));
		switchMaterial = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minebloxroyale.build_material", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT, cat));
		for (BuildPieceDef p : pieces()) {
			String name = switch (p.id()) { // (written out, so each has its name in en_us.json)
				case "wall" -> "key.minebloxroyale.build_wall";
				case "floor" -> "key.minebloxroyale.build_floor";
				case "ramp" -> "key.minebloxroyale.build_ramp";
				default -> null; // (a new piece: picked by its hotbar number until it has a name here)
			};
			if (name != null) PIECE_KEYS.put(p.id(), KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.KEY_1 + p.slot() - 1, cat)));
		}
	}

	/**
	 * Every tick before Minecraft handles its keys: while building, presses of anything else on a build key's key are
	 * dropped (Q drops nothing, E opens no inventory...), so the build key alone acts. Attack and use are already off in
	 * build mode (MinecraftAttackMixin).
	 */
	public static void beforeKeys(Minecraft mc) {
		if (!active || place == null) return;
		List<KeyMapping> mine = new java.util.ArrayList<>(PIECE_KEYS.values());
		mine.add(place);
		mine.add(switchMaterial);
		for (KeyMapping other : mc.options.keyMappings) {
			if (mine.contains(other) || other == mc.options.keyAttack || other == mc.options.keyUse) continue;
			for (KeyMapping m : mine)
				if (m.same(other)) {
					while (other.consumeClick()) {
					}
					break;
				}
		}
	}

	/** A key's name as Controls shows it ("1", "Q", "Right Button"). */
	private static String keyName(KeyMapping k) {
		return k == null ? "?" : k.getTranslatedKeyMessage().getString();
	}

	private static List<BuildPieceDef> pieces() {
		return Sheets.BUILDS.stream().sorted(Comparator.comparingInt(BuildPieceDef::slot)).toList();
	}

	public static BuildPieceDef piece() {
		Minecraft mc = Minecraft.getInstance();
		List<BuildPieceDef> all = pieces();
		return all.get(Math.min(all.size() - 1, mc.player.getInventory().getSelectedSlot()));
	}

	public static MaterialDef material() {
		return Sheets.MATERIALS.get(material % Sheets.MATERIALS.size());
	}

	public static void toggle(Minecraft mc) {
		if (mc.player == null) return;
		if (!active) {
			if (!ClientState.inMatch() || mc.player.isSpectator()) return;
			if (ClientState.noBuild()) {
				mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("No Build match: no building, just fight"));
				return;
			}
			active = true;
			savedSlot = mc.player.getInventory().getSelectedSlot();
			mc.player.getInventory().setSelectedSlot(0);
		} else {
			exit(mc);
		}
	}

	private static void exit(Minecraft mc) {
		active = false;
		if (mc.player != null) mc.player.getInventory().setSelectedSlot(savedSlot);
	}

	public static Builds.Placement target(Minecraft mc) {
		var p = mc.player;
		return Builds.target(mc.level, piece(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()); // (the level: ramps carry on up, 2.0.0)
	}

	/** Every client tick (after Minecraft has handled its keys). */
	public static void tick(Minecraft mc) {
		if (!active) { // (presses while not building don't count later)
			if (place != null) {
				while (place.consumeClick()) {
				}
				while (switchMaterial.consumeClick()) {
				}
				for (KeyMapping k : PIECE_KEYS.values())
					while (k.consumeClick()) {
					}
			}
			return;
		}
		if (mc.player == null || !ClientState.inMatch() || mc.player.isSpectator()) {
			active = false;
			return;
		}
		// Only as many hotbar slots as there are pieces.
		int max = pieces().size() - 1;
		if (mc.player.getInventory().getSelectedSlot() > max) mc.player.getInventory().setSelectedSlot(max);
		if (mc.gui.screen() != null) return;
		// The piece keys (wall, floor, ramp: 1-3 at first).
		List<BuildPieceDef> all = pieces();
		for (int i = 0; i < all.size(); i++) {
			KeyMapping k = PIECE_KEYS.get(all.get(i).id());
			while (k != null && k.consumeClick()) mc.player.getInventory().setSelectedSlot(i);
		}
		while (switchMaterial.consumeClick()) material = (material + 1) % Sheets.MATERIALS.size();
		while (mc.options.keyAttack.consumeClick()) {
			// swallowed: in build mode left mouse only does what the place key says
		}
		// Out of this material: on to the next one you have, no right-click needed (2.0.0, creator: "when you build and
		// run out of mats it should automatically switch to whatever mats you have available").
		if (Builds.materialCount(mc.player, material().id()) < piece().cost()) {
			String next = Builds.nextMaterial(mc.player, material().id(), piece().cost());
			for (int i = 0; next != null && i < Sheets.MATERIALS.size(); i++) if (Sheets.MATERIALS.get(i).id().equals(next)) material = i;
		}
		while (place.consumeClick()) {
			placeCooldown = 0; // a fresh click places straight away
		}
		if (placeCooldown > 0) placeCooldown--;
		if (place.isDown() && placeCooldown == 0) {
			Builds.Placement t = target(mc);
			ClientPlayNetworking.send(new Net.Action("build:" + piece().id() + ":" + material().id() + ":" + t.tx() + ":" + t.ty() + ":" + t.tz() + ":" + t.dir().getName()));
			placeCooldown = 4; // holding the button keeps building, like Fortnite's turbo build
		}
	}

	/** The ghost of the piece you'd place: blue if you have the materials, red if not. */
	static void preview(PoseStack ps, SubmitNodeCollector out, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		if (!active || mc.player == null || mc.level == null) return;
		Map<BlockPos, BlockState> shape = Builds.shape(piece(), material(), target(mc));
		boolean afford = Builds.materialCount(mc.player, material().id()) >= piece().cost();
		int col = afford ? 0x704C9BFF : 0x70FF4040;
		out.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(WorldVisuals.white()), (pose, vc) -> {
			for (BlockPos b : shape.keySet()) {
				BlockState there = mc.level.getBlockState(b);
				if (!there.isAir() && !there.canBeReplaced()) continue;
				double x0 = b.getX() + 0.02, x1 = b.getX() + 0.98, y0 = b.getY() + 0.02, y1 = b.getY() + 0.98, z0 = b.getZ() + 0.02, z1 = b.getZ() + 0.98;
				WorldVisuals.quad(vc, pose, cam, x0, z0, x1, z0, y0, y1, 0, -1, col);
				WorldVisuals.quad(vc, pose, cam, x1, z1, x0, z1, y0, y1, 0, 1, col);
				WorldVisuals.quad(vc, pose, cam, x0, z1, x0, z0, y0, y1, -1, 0, col);
				WorldVisuals.quad(vc, pose, cam, x1, z0, x1, z1, y0, y1, 1, 0, col);
			}
		});
	}

	/** Build bar above the hotbar: the pieces, the material and how much of it you have. */
	static void hud(GuiGraphicsExtractor g, Minecraft mc) {
		if (!active) return;
		StringBuilder s = new StringBuilder("BUILD   ");
		BuildPieceDef current = piece();
		// (each piece with the key that picks it, as set in Controls)
		for (BuildPieceDef p : pieces()) {
			String key = keyName(PIECE_KEYS.get(p.id()));
			s.append(p == current ? "[" + key + " " + p.name().toUpperCase(Locale.ROOT) + "]" : " " + key + " " + p.name() + " ").append("  ");
		}
		MaterialDef m = material();
		String sw = switchMaterial == null || switchMaterial.isDefault() ? "right-click" : keyName(switchMaterial);
		s.append("|  ").append(m.name()).append(" ").append(Builds.materialCount(mc.player, m.id())).append(" (" + sw + ": switch)  |  ")
			.append(keyName(MinebloxRoyaleClient.buildKey())).append(": weapons");
		String text = s.toString();
		int w = mc.font.width(text) + 16, x = g.guiWidth() / 2 - w / 2, y = g.guiHeight() - 62;
		g.fill(x, y, x + w, y + 14, 0xC0101420);
		g.fill(x, y + 13, x + w, y + 14, 0xFF000000 | m.color());
		g.centeredText(mc.font, text, g.guiWidth() / 2, y + 3, 0xFFFFFFFF);
	}
}
