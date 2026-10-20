package gg.minebloxroyale.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a frame's GUI pieces go, so the HUD can hand over a whole panel or line of lettering as one (Smooth.Batch, 1.2.0). */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccessor {
	@Accessor("guiRenderState")
	GuiRenderState minebloxroyale$state();
}
