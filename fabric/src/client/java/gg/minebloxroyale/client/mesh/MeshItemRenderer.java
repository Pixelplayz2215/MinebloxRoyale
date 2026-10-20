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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * An item drawn from a Blender mesh (items/*.json: {"type": "minecraft:special", "model": {"type":
 * "minebloxroyale:mesh", "mesh": "gun_smg"}, "base": ...}; the base model gives the hold in each hand). Tint channel 1
 * takes the stack's custom model data colour (a gun's rarity, Guns.make), or the given default. In the world, its
 * lighter copies further away (Mesh.lod; 1.2.0, from 2.0.0-test3, lag).
 */
public record MeshItemRenderer(Mesh mesh, int tint) implements SpecialModelRenderer<Integer> {
	@Override
	public void submit(Integer color, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
		if (mesh.isEmpty()) return;
		int c = color == null ? tint : color;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()), (pose, vc) -> lod(mesh, pose).draw(pose, null, vc, light, overlay, c, c));
	}

	/**
	 * Which copy to draw: in the world the pose is from the camera, so its offset is how far away the item is, and its
	 * scale how big; in a GUI (an inventory slot, many times bigger than anything in the world) always the full model.
	 */
	private static Mesh lod(Mesh m, PoseStack.Pose pose) {
		Matrix4f p = pose.pose();
		float scale = (float) Math.cbrt(Math.abs(p.determinant3x3()));
		if (scale > 4) return m;
		float[] b = m.bounds;
		float size = scale * (float) Math.sqrt((b[3] - b[0]) * (b[3] - b[0]) + (b[4] - b[1]) * (b[4] - b[1]) + (b[5] - b[2]) * (b[5] - b[2]));
		return Mesh.lod(m.id, p.m30() * p.m30() + p.m31() * p.m31() + p.m32() * p.m32(), Math.max(0.1F, size));
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
