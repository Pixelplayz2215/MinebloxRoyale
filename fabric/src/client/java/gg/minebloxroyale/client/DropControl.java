package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.client.media.SfxBank;
import gg.minebloxroyale.match.BattleBusEntity;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.LoadoutDef;
import gg.minebloxroyale.sheets.Sheets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The local player's drop (match sheet numbers): Space leaves the Battle Bus; skydive with WASD steering and a capped
 * fall speed; the glider opens on its own at gliderHeight above the ground and slows the fall to a glide. Movement is
 * the client's own, so this runs here; the server only cancels fall damage and records the landing.
 * Space opens or closes the glider at any height; if it was never opened it opens by itself at gliderHeight.
 */
public final class DropControl {
	public enum Phase { NONE, BUS, SKYDIVE, GLIDE }

	private static boolean jumpWasDown;
	private static Phase phase = Phase.NONE;
	private static double heightAboveGround;
	/** Glider open (Space toggles it); autoOpened: the one automatic opening at gliderHeight has happened. */
	private static boolean gliderOpen, autoOpened;
	/** Touched the ground since leaving the bus: no more skydiving or glider this match (creator), even off a ledge. */
	private static boolean landed;
	/** The server's drop state last tick: a new drop (rebooted at a van) gives the skydive and glider back. */
	private static int lastDrop;

	private DropControl() {
	}

	public static Phase phase() {
		return phase;
	}

	public static double heightAboveGround() {
		return heightAboveGround;
	}

	public static void tick(Minecraft mc) {
		Player p = mc.player;
		if (p == null || mc.level == null) {
			phase = Phase.NONE;
			return;
		}
		int drop = ClientState.hud.drop();
		if (drop == 2 && lastDrop != 2) landed = false;
		lastDrop = drop;
		// While seated, Minecraft routes Space into the player's input; read both.
		boolean jumpDown = mc.options.keyJump.isDown() || mc.player.input.keyPresses.jump();
		if (p.getVehicle() instanceof BattleBusEntity) {
			phase = Phase.BUS;
			landed = false;
			if (jumpDown && !jumpWasDown) ClientPlayNetworking.send(new Net.Action("bus_jump"));
		} else if (!ClientState.inMatch()) {
			phase = Phase.NONE;
			landed = false;
		} else if ((p.onGround() || p.isInWater()) && (phase == Phase.SKYDIVE || phase == Phase.GLIDE)) {
			landed = true; // down: the glider is put away for good
			phase = Phase.NONE;
		} else if (!landed && ClientState.hud.drop() == 2 && !p.onGround() && !p.isInWater()) {
			int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getBlockX(), p.getBlockZ());
			heightAboveGround = p.getY() - ground;
			if (phase != Phase.SKYDIVE && phase != Phase.GLIDE) {
				gliderOpen = false; // just left the bus
				autoOpened = false;
			}
			if (jumpDown && !jumpWasDown) setGlider(mc, !gliderOpen);
			if (!autoOpened && heightAboveGround < MinebloxRoyale.setting("gliderHeight")) {
				autoOpened = true;
				if (!gliderOpen) setGlider(mc, true);
			}
			boolean glide = gliderOpen;
			phase = glide ? Phase.GLIDE : Phase.SKYDIVE;
			steer(mc, p, glide);
		} else {
			phase = Phase.NONE;
		}
		jumpWasDown = jumpDown;
		// Fortnite's hotbar: pickaxe + 5 item slots; the last slots are locked.
		if (ClientState.inMatch()) {
			int max = Sheets.LOADOUT.stream().filter(d -> !d.role().equals("locked")).mapToInt(LoadoutDef::slot).max().orElse(8);
			if (p.getInventory().getSelectedSlot() > max) p.getInventory().setSelectedSlot(max);
		}
	}

	private static void setGlider(Minecraft mc, boolean open) {
		gliderOpen = open;
		if (open) SfxBank.play("glider", mc.player.position());
	}

	private static void steer(Minecraft mc, Player p, boolean glide) {
		float fwd = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
		float side = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
		if (glide && fwd == 0 && side == 0) fwd = 0.5F; // a glider always drifts forward
		double yaw = Math.toRadians(p.getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = fz, sz = -fx;
		double speed = MinebloxRoyale.setting(glide ? "gliderSteer" : "skydiveSteer");
		double hx = (fx * fwd + sx * side) * speed, hz = (fz * fwd + sz * side) * speed;
		Vec3 v = p.getDeltaMovement();
		double maxFall = MinebloxRoyale.setting(glide ? "gliderFall" : "skydiveFall");
		double vy = Math.max(v.y, -maxFall);
		// Ease towards the wanted horizontal speed (air control).
		p.setDeltaMovement(v.x + (hx - v.x) * 0.25, vy, v.z + (hz - v.z) * 0.25);
		p.resetFallDistance();
	}
}
