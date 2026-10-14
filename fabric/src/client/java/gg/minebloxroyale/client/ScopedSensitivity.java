package gg.minebloxroyale.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * "Scoped Sensitivity" (creator): how fast the mouse turns the view while looking through a sniper scope, as a share of
 * the normal mouse sensitivity (at 4x zoom full speed feels jumpy). A slider in Options > Controls > Mouse Settings,
 * remembered per Minecraft account beside the Locker outfit (PlayerFiles: scoped-sensitivity-<account>.txt).
 */
public final class ScopedSensitivity {
	/** The slider runs from 10% to 100% of the normal sensitivity; half by default. */
	private static final double MIN = 0.1, DEFAULT = 0.5;
	private static OptionInstance<Double> option;

	private ScopedSensitivity() {
	}

	public static OptionInstance<Double> option() {
		if (option == null) option = new OptionInstance<>("options.minebloxroyale.scopedSensitivity",
			OptionInstance.cachedConstantTooltip(Component.translatable("options.minebloxroyale.scopedSensitivity.tooltip")),
			(caption, slider) -> Options.genericValueLabel(caption, Component.literal(Math.round(share(slider) * 100) + "%")),
			OptionInstance.UnitDouble.INSTANCE, (load() - MIN) / (1 - MIN), slider -> save(share(slider)));
		return option;
	}

	/** The mouse's turning is multiplied by this while scoped. */
	public static double factor() {
		return share(option().get());
	}

	private static double share(double slider) {
		return MIN + (1 - MIN) * slider;
	}

	private static Path file() {
		return PlayerFiles.dir().resolve("scoped-sensitivity-" + PlayerFiles.account() + ".txt");
	}

	private static double load() {
		try {
			if (Files.exists(file())) return Mth.clamp(Double.parseDouble(Files.readString(file(), StandardCharsets.UTF_8).trim()), MIN, 1);
		} catch (Exception ignored) {
		}
		return DEFAULT; // none saved yet, or unreadable
	}

	private static void save(double share) {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), String.format(Locale.ROOT, "%.3f", share), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}
}
