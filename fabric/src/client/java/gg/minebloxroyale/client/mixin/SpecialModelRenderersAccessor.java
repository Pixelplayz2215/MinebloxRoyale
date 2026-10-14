package gg.minebloxroyale.client.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minecraft's list of special item renderer types, so the Blender meshes can add theirs (MeshItemRenderer). */
@Mixin(SpecialModelRenderers.class)
public interface SpecialModelRenderersAccessor {
	@Accessor("ID_MAPPER")
	static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> minebloxroyale$types() {
		throw new AssertionError();
	}
}
