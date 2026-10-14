package gg.minebloxroyale.client;

import gg.minebloxroyale.client.mixin.ContainerScreenAccessor;
import gg.minebloxroyale.client.mixin.OptionsSubScreenAccessor;
import gg.minebloxroyale.match.Loadout;
import gg.minebloxroyale.sheets.Sheets;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * Settings Mineblox Royale fixes (hud sheet: gui_scale, chat, fov): their values snap back every tick, and their
 * buttons and sliders in the Options screens are greyed out so nobody can click or drag them.
 */
public final class SettingsLock {
	private SettingsLock() {
	}

	private static List<OptionInstance<?>> locked(Options o) {
		return List.of(o.fov(), o.guiScale(), o.chatOpacity(), o.textBackgroundOpacity(), o.chatScale(), o.chatLineSpacing(), o.chatDelay(),
			o.chatWidth(), o.chatHeightFocused(), o.chatHeightUnfocused());
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			disableWidgets(mc, screen);
			// In a match, the inventory's locked slots show a dark tile with a red X (only the pickaxe, 5 item slots
			// and the fixed ammo / material slots are usable).
			if (screen instanceof AbstractContainerScreen<?> cs)
				ScreenEvents.afterExtract(screen).register((s, g, mx, my, a) -> lockedSlots(cs, g));
		});
	}

	private static void lockedSlots(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !ClientState.inMatch() || mc.player.isCreative()) return;
		var acc = (ContainerScreenAccessor) cs;
		for (var slot : cs.getMenu().slots) {
			if (slot.container != mc.player.getInventory() || slot.getContainerSlot() >= 36) continue;
			if (!Loadout.role(slot.getContainerSlot()).equals("locked")) continue;
			int x = acc.minebloxroyale$left() + slot.x, y = acc.minebloxroyale$top() + slot.y;
			g.fill(x, y, x + 16, y + 16, 0xC0202028);
			for (int k = 3; k < 13; k++) {
				g.fill(x + k, y + k, x + k + 1, y + k + 1, 0xFFD03030);
				g.fill(x + 15 - k, y + k, x + 16 - k, y + k + 1, 0xFFD03030);
			}
		}
	}

	private static void disableWidgets(Minecraft mc, Screen screen) {
		if (screen instanceof OptionsSubScreen sub && ((OptionsSubScreenAccessor) sub).minebloxroyale$list() != null) {
			for (OptionInstance<?> opt : locked(mc.options)) {
				AbstractWidget wdg = ((OptionsSubScreenAccessor) sub).minebloxroyale$list().findOption(opt);
				if (wdg != null) wdg.active = false;
			}
		}
		// The main Options screen puts the FOV slider straight on the screen, not in a list.
		String fov = Component.translatable("options.fov").getString();
		for (AbstractWidget wdg : Screens.getWidgets(screen))
			if (wdg.getMessage().getString().startsWith(fov)) wdg.active = false;
	}

	/** Every client tick: put locked values back. */
	public static void tick(Minecraft mc) {
		Options o = mc.options;
		// Chat: text size (hud chat.scale) and width in pixels (hud chat.size; the slider maps 0..1 to 40..320 px).
		var chatDef = Sheets.HUD_BY_ID.get("chat");
		double chat = chatDef.scale(), width = (chatDef.size() - 40) / 280.0;
		if (Math.abs(o.chatScale().get() - chat) > 0.001) o.chatScale().set(chat);
		if (Math.abs(o.chatWidth().get() - width) > 0.001) o.chatWidth().set(width);
		int fov = (int) Sheets.HUD_BY_ID.get("fov").scale();
		if (o.fov().get() != fov) o.fov().set(fov);
		int guiScale = (int) Sheets.HUD_BY_ID.get("gui_scale").scale();
		if (o.guiScale().get() != guiScale) {
			o.guiScale().set(guiScale);
			mc.resizeGui();
		}
	}
}
