package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * The reboot van, in the Battle Bus's smooth Roblox "plastic" style (creator: same art style as the bus; no Fortnite
 * asset is used): a white van with the bus's blue skirt, round wheels, a satellite dish on the roof, a light bar and a
 * terminal screen on its side that glow green when it's ready and red while it recharges. Block units, front along +Z,
 * origin on the ground in the middle (it stands on the invisible blocks the server puts there).
 */
public final class VanModel {
	private static final int WHITE = 0xFFE9EDF2, BLUE = 0xFF2F6BD8, DARK_BLUE = 0xFF1E4797, GLASS = 0xFF9FD8F2, TIRE = 0xFF26262B, HUB = 0xFFC9CDD4,
		LIGHT = 0xFFFFE07A, GREY = 0xFF9AA3AE, PANEL = 0xFF1B2230, GREEN = 0xFF4CD964, RED = 0xFFE03A3A;

	private VanModel() {
	}

	public static void draw(PoseStack.Pose pose, VertexConsumer vc, int light, boolean ready) {
		int state = ready ? GREEN : RED;
		// Body: a tall rounded box, the bus's blue along the bottom, a darker sill.
		BusModel.roundedBox(pose, vc, light, 0, 1.62F, 0, 1.25F, 1.05F, 2.35F, 0.38F, WHITE);
		BusModel.roundedBox(pose, vc, light, 0, 0.82F, 0, 1.28F, 0.26F, 2.38F, 0.18F, BLUE);
		BusModel.roundedBox(pose, vc, light, 0, 0.56F, 0, 1.2F, 0.08F, 2.3F, 0.06F, DARK_BLUE);
		// A blue stripe round the middle.
		BusModel.roundedBox(pose, vc, light, 0, 1.5F, 0, 1.27F, 0.07F, 2.37F, 0.05F, BLUE);
		// Windscreen and the cab's side windows.
		BusModel.roundedBox(pose, vc, light, 0, 2.05F, 2.33F, 1.0F, 0.36F, 0.05F, 0.04F, GLASS);
		for (float side : new float[] { -1, 1 }) BusModel.roundedBox(pose, vc, light, side * 1.26F, 2.05F, 1.55F, 0.04F, 0.32F, 0.5F, 0.03F, GLASS);
		// Headlights, bumper.
		for (float side : new float[] { -1, 1 }) BusModel.roundedBox(pose, vc, light, side * 0.8F, 1.1F, 2.38F, 0.22F, 0.14F, 0.05F, 0.05F, LIGHT);
		BusModel.roundedBox(pose, vc, light, 0, 0.7F, 2.48F, 1.15F, 0.12F, 0.1F, 0.08F, GREY);
		// The reboot terminal on both sides: a dark screen with a glowing card slot (green ready, red recharging).
		for (float side : new float[] { -1, 1 }) {
			BusModel.roundedBox(pose, vc, light, side * 1.27F, 1.75F, -0.7F, 0.04F, 0.5F, 0.75F, 0.04F, PANEL);
			BusModel.roundedBox(pose, vc, light, side * 1.3F, 1.82F, -0.7F, 0.03F, 0.32F, 0.55F, 0.03F, state);
			BusModel.roundedBox(pose, vc, light, side * 1.32F, 1.4F, -0.7F, 0.03F, 0.05F, 0.3F, 0.02F, 0xFF101010);
		}
		// Round wheels with hubcaps.
		for (float z : new float[] { -1.5F, 1.5F })
			for (float side : new float[] { -1, 1 }) {
				BusModel.wheel(pose, vc, light, side * 1.2F, 0.42F, z, 0.42F, 0.24F, TIRE);
				BusModel.wheel(pose, vc, light, side * 1.36F, 0.42F, z, 0.2F, 0.05F, HUB);
			}
		// Roof: a light bar over the cab and a satellite dish on a mast.
		BusModel.roundedBox(pose, vc, light, 0, 2.74F, 1.35F, 0.75F, 0.08F, 0.18F, 0.07F, GREY);
		BusModel.roundedBox(pose, vc, light, 0, 2.87F, 1.35F, 0.65F, 0.07F, 0.13F, 0.06F, state);
		BusModel.roundedBox(pose, vc, light, 0, 3.02F, -1.0F, 0.07F, 0.35F, 0.07F, 0.05F, GREY);
		BusModel.wheel(pose, vc, light, 0, 3.52F, -1.0F, 0.62F, 0.06F, WHITE);
		BusModel.wheel(pose, vc, light, 0.08F, 3.52F, -1.0F, 0.18F, 0.05F, GREY);
		BusModel.roundedBox(pose, vc, light, 0.3F, 3.52F, -1.0F, 0.25F, 0.03F, 0.03F, 0.02F, GREY);
	}
}
