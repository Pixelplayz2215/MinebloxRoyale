package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * A test build's "what to test" list in chat (creator: quicker playtests), once per game start, a few seconds after the
 * first world opens: assets/minebloxroyale/what_to_test.txt, one line per thing. Test builds only (version "...-testN").
 */
public final class WhatToTest {
	private static final int DELAY_TICKS = 100;
	private static boolean shown;
	private static int wait = -1;

	private WhatToTest() {
	}

	/** A world was joined: show the list soon, if it hasn't been shown since the game started. */
	static void onJoin() {
		if (!shown) wait = DELAY_TICKS;
	}

	static void tick(Minecraft mc) {
		if (wait < 0 || mc.player == null || --wait > 0) return;
		wait = -1;
		shown = true;
		String version = MinebloxRoyale.version();
		if (!version.contains("-test")) return;
		String text;
		try (InputStream in = WhatToTest.class.getResourceAsStream("/assets/minebloxroyale/what_to_test.txt")) {
			if (in == null) return;
			text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			return;
		}
		mc.player.sendSystemMessage(Component.literal("What to test in " + version + ":").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (String line : text.split("\\R")) if (!line.isBlank()) mc.player.sendSystemMessage(Component.literal(" - " + line.trim()).withStyle(ChatFormatting.YELLOW));
	}
}
