package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.match.CarBlock;
import gg.minebloxroyale.match.CarEntity;
import gg.minebloxroyale.match.Knocked;
import gg.minebloxroyale.sheets.HudElementDef;
import gg.minebloxroyale.sheets.Sheets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * First-time control tips (2.0.0-test6.3, creator: "First time control tips"): the first time something with new keys
 * comes up in a match, a short tip over the hotbar says what to press, for SHOW_TICKS of it being shown in all, then
 * never again. Kept per Minecraft account beside the unlocks (PlayerFiles, tips.txt), so every version and launcher on
 * this PC knows which have been seen. One tip at a time; a tip cut short (you walked away) comes back next time until
 * it has had its full time.
 */
public final class FirstTips {
	/** How long a tip shows in all (ticks), and how long it takes to fade in or out. */
	private static final int SHOW_TICKS = 140, FADE_TICKS = 8;
	private static @Nullable Set<String> seen;
	private static @Nullable String loadedFor;
	private static @Nullable String current;
	private static int shown, fading;
	private static @Nullable String fadingText;
	private static String text = "";

	private FirstTips() {
	}

	private static Path file() {
		return PlayerFiles.dir().resolve("tips.txt");
	}

	/** The tips this account has seen (tips.txt: "<account id> <tip>" a line). */
	private static Set<String> seen() {
		String account = PlayerFiles.account();
		if (seen != null && account.equals(loadedFor)) return seen;
		Set<String> s = new HashSet<>();
		try {
			if (Files.exists(file()))
				for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
					String[] p = line.trim().split(" ", 2);
					if (p.length == 2 && p[0].equals(account)) s.add(p[1]);
				}
		} catch (IOException e) {
			MinebloxRoyale.LOG.info("BR: couldn't read tips.txt ({})", e.getMessage());
		}
		loadedFor = account;
		return seen = s;
	}

	private static void markSeen(String tip) {
		if (!seen().add(tip)) return;
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), PlayerFiles.account() + " " + tip + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			MinebloxRoyale.LOG.info("BR: couldn't save tips.txt ({})", e.getMessage());
		}
	}

	/** A key as players know it: LEFT MOUSE / RIGHT MOUSE for the mouse buttons, else its name in capitals. */
	private static String key(KeyMapping k) {
		String name = k.getTranslatedKeyMessage().getString();
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.contains("left button")) return "LEFT MOUSE";
		if (lower.contains("right button")) return "RIGHT MOUSE";
		return name.toUpperCase(Locale.ROOT);
	}

	/** The tip that fits right now and hasn't been seen, with its words; null if none. */
	private static @Nullable String[] due(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || !ClientState.inMatch() || p.isSpectator() || DropControl.phase() != DropControl.Phase.NONE
			|| Knocked.CLIENT.contains(p.getId()))
			return null;
		Set<String> s = seen();
		var o = mc.options;
		if (p.getVehicle() instanceof CarEntity car) {
			if (car.getControllingPassenger() == p) {
				if (!s.contains("car_drive"))
					return new String[] { "car_drive", key(o.keyUp) + " " + key(o.keyLeft) + " " + key(o.keyDown) + " " + key(o.keyRight) + ": drive   "
						+ key(o.keyJump) + ": boost (4 a car)   " + key(o.keyShift) + ": get out" };
			} else if (!s.contains("car_ride")) return new String[] { "car_ride", "Riding along: your teammate drives.   " + key(o.keyShift) + ": get out" };
			return null;
		}
		ItemStack held = p.getMainHandItem();
		if (held.is(ModItems.GRENADE) && !s.contains("grenade"))
			return new String[] { "grenade", "Hold " + key(o.keyUse) + " to aim (the blue arc),  " + key(o.keyAttack) + " to throw" };
		if (held.getItem() instanceof WeaponItem w && w.def.projectile().equals("rocket") && !s.contains("rocket"))
			return new String[] { "rocket", key(o.keyAttack) + ": fire a rocket. It blows up builds and cars" };
		if (!s.contains("car_near") && nearCar(mc, p)) return new String[] { "car_near", "Press " + key(MinebloxRoyaleClient.interactKey()) + " to get in the car" };
		return null;
	}

	/** Close enough to get in (CarEntity.tryEnter's reach): a driven car, or a parked one (car blocks). */
	private static boolean nearCar(Minecraft mc, LocalPlayer p) {
		for (CarEntity c : mc.level.getEntitiesOfClass(CarEntity.class, p.getBoundingBox().inflate(4)))
			if (c.getBoundingBox().distanceToSqr(p.getEyePosition()) < 3.2 * 3.2) return true;
		BlockPos at = p.blockPosition();
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-3, -1, -3), at.offset(3, 2, 3)))
			if (mc.level.getBlockState(q).getBlock() instanceof CarBlock && q.distToCenterSqr(p.position()) < 3.0 * 3.0) return true;
		return false;
	}

	/** Every client tick: which tip shows, how long it has shown, and done once it has had its time. */
	public static void tick(Minecraft mc) {
		if (fading > 0) fading--;
		String[] d = mc.player == null ? null : due(mc);
		String id = d == null ? null : d[0];
		if (id == null || !id.equals(current)) {
			if (current != null) { // cut short: it fades, and comes back next time with the time it has left
				fading = FADE_TICKS;
				fadingText = text;
			}
			current = id;
			shown = 0;
		}
		if (current == null) return;
		text = d[1];
		if (++shown >= SHOW_TICKS) {
			markSeen(current);
			current = null;
			fading = FADE_TICKS;
			fadingText = text;
		}
	}

	/** Over the hotbar, where the Battle Bus's hint goes (never at the same time): a gold TIP chip and the words. */
	public static void draw(GuiGraphicsExtractor g, Minecraft mc) {
		String t;
		float a;
		if (current != null) {
			t = text;
			a = Math.min(1F, shown / (float) FADE_TICKS);
		} else if (fading > 0 && fadingText != null) {
			t = fadingText;
			a = fading / (float) FADE_TICKS;
		} else return;
		HudElementDef d = Sheets.HUD_BY_ID.get("tip");
		float size = 9, chip = Smooth.width("TIP", 8) + 10;
		float w = chip + 6 + Smooth.width(t, size) + 14, cx = g.guiWidth() / 2F + d.x(), y = g.guiHeight() + d.y();
		int alpha = Math.round(a * 255);
		Smooth.panel(g, cx - w / 2, y, w, 17, 6, fade(0xC0182030, a), fade(0xD00C1018, a));
		Smooth.panel(g, cx - w / 2 + 4, y + 3.5F, chip, 10, 4, fade(0xFFFFC828, a));
		Smooth.text(g, "TIP", cx - w / 2 + 4 + chip / 2, y + 4.5F, 8, (alpha << 24) | 0x1A1408, Smooth.CENTER, false);
		Smooth.text(g, t, cx - w / 2 + 4 + chip + 6, y + 4, size, (alpha << 24) | 0xFFFFFF, Smooth.LEFT, true);
	}

	private static int fade(int argb, float a) {
		return Math.round((argb >>> 24) * a) << 24 | (argb & 0xFFFFFF);
	}
}
