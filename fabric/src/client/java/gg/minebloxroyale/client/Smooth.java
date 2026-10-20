package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import gg.minebloxroyale.client.mixin.GuiGraphicsAccessor;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * HUD art handed to Minecraft as one GUI piece (1.2.0, from 2.0.0's Smooth HUD; creator: "lag is bad in game for
 * v1.2.0"). Minecraft places every GUI piece by checking it against those before it, so things drawn a pixel at a
 * time (the storm's rings on the minimap, the sniper scope) got slower and slower; a Batch is one piece however many
 * quads it has. Only the batch is here, not 2.0.0's whole HUD.
 */
public final class Smooth {
	private Smooth() {
	}

	/**
	 * Quads gathered to go to Minecraft as one GUI piece, in the pose of the moment: one pipeline, one texture. Not
	 * inside the screen's scissor; a batch that must stay inside a box says so itself (clip, in GUI coordinates).
	 */
	static final class Batch {
		private final RenderPipeline pipeline;
		private final TextureSetup texture;
		private @Nullable ScreenRectangle clip;
		private float[] v = new float[64]; // x, y, u, v per corner, four a quad
		private int[] c = new int[16];
		private int n;
		private float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

		Batch(RenderPipeline pipeline, TextureSetup texture) {
			this.pipeline = pipeline;
			this.texture = texture;
		}

		/** Kept inside this box (GUI coordinates, as GuiGraphicsExtractor.enableScissor takes them). */
		Batch clip(@Nullable ScreenRectangle box) {
			this.clip = box;
			return this;
		}

		/**
		 * (2.0.0) Any four-cornered shape, each corner its own colour (the storm's rings and its purple on the maps):
		 * put in the same turning order as the plain quads.
		 */
		void quad(float xa, float ya, int ca, float xb, float yb, int cb, float xc, float yc, int cc, float xd, float yd, int cd) {
			float area = (xa * yb - xb * ya) + (xb * yc - xc * yb) + (xc * yd - xd * yc) + (xd * ya - xa * yd);
			if (area > 0) {
				corner(xd, yd, 0, 0, cd);
				corner(xc, yc, 0, 0, cc);
				corner(xb, yb, 0, 0, cb);
				corner(xa, ya, 0, 0, ca);
			} else {
				corner(xa, ya, 0, 0, ca);
				corner(xb, yb, 0, 0, cb);
				corner(xc, yc, 0, 0, cc);
				corner(xd, yd, 0, 0, cd);
			}
			minX = Math.min(minX, Math.min(Math.min(xa, xb), Math.min(xc, xd)));
			minY = Math.min(minY, Math.min(Math.min(ya, yb), Math.min(yc, yd)));
			maxX = Math.max(maxX, Math.max(Math.max(xa, xb), Math.max(xc, xd)));
			maxY = Math.max(maxY, Math.max(Math.max(ya, yb), Math.max(yc, yd)));
		}

		/** A plain quad shading from colour top to bottom (as GuiGraphicsExtractor.fillGradient). */
		void quad(float x0, float y0, float x1, float y1, int top, int bottom) {
			textured(x0, y0, x1, y1, 0, 0, 0, 0, top, bottom);
		}

		/** A quad of a texture's u0..u1, v0..v1 (0..1), tinted (as GuiGraphicsExtractor.blit). */
		void textured(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int top, int bottom) {
			corner(x0, y0, u0, v0, top);
			corner(x0, y1, u0, v1, bottom);
			corner(x1, y1, u1, v1, bottom);
			corner(x1, y0, u1, v0, top);
			minX = Math.min(minX, Math.min(x0, x1));
			minY = Math.min(minY, Math.min(y0, y1));
			maxX = Math.max(maxX, Math.max(x0, x1));
			maxY = Math.max(maxY, Math.max(y0, y1));
		}

		private void corner(float x, float y, float u, float w, int color) {
			if (n == c.length) {
				v = Arrays.copyOf(v, n * 8);
				c = Arrays.copyOf(c, n * 2);
			}
			v[n * 4] = x;
			v[n * 4 + 1] = y;
			v[n * 4 + 2] = u;
			v[n * 4 + 3] = w;
			c[n++] = color;
		}

		void submit(GuiGraphicsExtractor g) {
			if (n == 0) return;
			Matrix3x2f pose = new Matrix3x2f(g.pose());
			int left = (int) Math.floor(minX), top = (int) Math.floor(minY);
			ScreenRectangle area = new ScreenRectangle(left, top, (int) Math.ceil(maxX) - left, (int) Math.ceil(maxY) - top).transformMaxBounds(pose);
			if (clip != null) {
				area = clip.intersection(area);
				if (area == null) return; // (all of it outside its box)
			}
			((GuiGraphicsAccessor) g).minebloxroyale$state().addGuiElement(new Piece(pipeline, texture, pose, Arrays.copyOf(v, n * 4), Arrays.copyOf(c, n), area, clip));
		}
	}

	/** A Batch as Minecraft draws it. */
	private record Piece(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, float[] v, int[] c, ScreenRectangle bounds,
		@Nullable ScreenRectangle clip) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer vc) {
			boolean uv = pipeline != RenderPipelines.GUI;
			for (int i = 0; i < c.length; i++) {
				VertexConsumer at = vc.addVertexWith2DPose(pose, v[i * 4], v[i * 4 + 1]);
				if (uv) at.setUv(v[i * 4 + 2], v[i * 4 + 3]);
				at.setColor(c[i]);
			}
		}

		@Override
		public @Nullable ScreenRectangle scissorArea() {
			return clip;
		}
	}
}
