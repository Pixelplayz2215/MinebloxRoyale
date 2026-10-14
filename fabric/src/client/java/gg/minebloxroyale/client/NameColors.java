package gg.minebloxroyale.client;

import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Name colours in chat: the server sends every real player's name in blue (bots stay white). On each player's own
 * screen, their own blue name becomes purple, so you always spot yourself; everyone else still sees you in blue.
 * Teammates (team modes; your party in the hub) show in their teammate colour, on their teammates' screens only
 * (creator): other teams still see them blue. Bot teammates too.
 */
public final class NameColors {
	private static final TextColor BLUE = TextColor.fromLegacyFormat(ChatFormatting.BLUE), PURPLE = TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE);

	private NameColors() {
	}

	public static Component recolor(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || message == null) return message;
		String me = mc.player.getName().getString();
		Map<String, Integer> mates = ClientState.teammateColors();
		String all = message.getString();
		boolean any = !me.isEmpty() && all.contains(me);
		for (String n : mates.keySet()) any |= all.contains(n);
		if (!any) return message;
		MutableComponent out = Component.empty();
		boolean[] changed = { false };
		message.visit((style, text) -> {
			int at = 0;
			while (true) {
				// The earliest name in what's left (the longer one when two start together).
				int best = -1;
				String bestName = null;
				TextColor bestColor = null;
				if (!me.isEmpty() && BLUE.equals(style.getColor())) {
					int i = indexOfName(text, me, at);
					if (i >= 0) {
						best = i;
						bestName = me;
						bestColor = PURPLE;
					}
				}
				for (var e : mates.entrySet()) {
					int i = indexOfName(text, e.getKey(), at);
					if (i >= 0 && (best < 0 || i < best || i == best && e.getKey().length() > bestName.length())) {
						best = i;
						bestName = e.getKey();
						bestColor = TextColor.fromRgb(e.getValue() & 0xFFFFFF);
					}
				}
				if (best < 0) break;
				if (best > at) out.append(Component.literal(text.substring(at, best)).setStyle(style));
				out.append(Component.literal(bestName).setStyle(style.withColor(bestColor)));
				at = best + bestName.length();
				changed[0] = true;
			}
			if (at < text.length()) out.append(Component.literal(text.substring(at)).setStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return changed[0] ? out : message;
	}

	/** Where this name appears on its own (not inside a longer name), from 'from' on; -1 if nowhere. */
	private static int indexOfName(String text, String name, int from) {
		if (name.isEmpty()) return -1;
		for (int i = text.indexOf(name, from); i >= 0; i = text.indexOf(name, i + 1)) {
			boolean before = i == 0 || !isNameChar(text.charAt(i - 1));
			int end = i + name.length();
			boolean after = end >= text.length() || !isNameChar(text.charAt(end));
			if (before && after) return i;
		}
		return -1;
	}

	private static boolean isNameChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}
}
