package gg.minebloxroyale.client.mixin;

import gg.minebloxroyale.client.AutoWorld;
import gg.minebloxroyale.client.BRHud;
import gg.minebloxroyale.client.NameColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every chat line passes through NameColors: your own name turns purple on your screen only. System lines (never
 * players' chat) also go to AutoWorld, which picks out e4mc's "hosted on domain" line: e4mc puts it straight into the
 * chat box, so no chat event ever sees it. The chat sits just above the health and shield panel (BRHud.chatLift), drawn
 * and clicked alike.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component minebloxroyale$ownNamePurple(Component contents) {
		return NameColors.recolor(contents);
	}

	/** The chat's bottom edge comes from the screen's height: a little less of it lifts every line, and its clicks with it. */
	@ModifyVariable(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int minebloxroyale$aboveHealth(int screenHeight) {
		return screenHeight - BRHud.chatLift(Minecraft.getInstance().getWindow().getGuiScaledWidth());
	}

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"))
	private void minebloxroyale$systemLine(Component contents, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
		if (source != GuiMessageSource.PLAYER) AutoWorld.onSystemChat(contents);
	}
}
