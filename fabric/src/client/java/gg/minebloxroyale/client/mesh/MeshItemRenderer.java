package gg.minebloxroyale.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import gg.minebloxroyale.client.WorldVisuals;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * An item drawn from a Blender mesh (items/*.json: {"type": "minecraft:special", "model": {"type":
 * "minebloxroyale:mesh", "mesh": "gun_smg"}, "base": ...}; the base model gives the hold in each hand). Tint channel 1
 * takes the stack's custom model data colour (a gun's rarity, Guns.make), or the given default.
 */
public record MeshItemRenderer(Mesh mesh, int tint) implements SpecialModelRenderer<Integer> {
	@Override
	public void submit(Integer color, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
		if (mesh.isEmpty()) return;
		int c = color == null ? tint : color;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()), (pose, vc) -> mesh.draw(pose, null, vc, light, overlay, c, c));
	}

	@Override
	public void getExtents(Consumer<Vector3fc> out) {
		float[] b = mesh.bounds;
		for (int i = 0; i < 8; i++) out.accept(new Vector3f(b[(i & 1) * 3], b[1 + (i >> 1 & 1) * 3], b[2 + (i >> 2 & 1) * 3]));
	}

	@Override
	public Integer extractArgument(ItemStack stack) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		Integer c = data == null ? null : data.getColor(0);
		return c == null ? tint : c & 0xFFFFFF;
	}

	public record Unbaked(String mesh, int tint) implements SpecialModelRenderer.Unbaked<Integer> {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.fieldOf("mesh").forGetter(Unbaked::mesh),
			Codec.INT.optionalFieldOf("tint", 0xFFFFFF).forGetter(Unbaked::tint)).apply(i, Unbaked::new));

		@Override
		public SpecialModelRenderer<Integer> bake(SpecialModelRenderer.BakingContext context) {
			return new MeshItemRenderer(Mesh.get(mesh), tint);
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
