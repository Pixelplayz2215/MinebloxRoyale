package gg.minebloxroyale.client;

import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * What the crosshair points at. Fortnite's camera sits behind and to the right of the player, so the middle of the
 * screen and the player's own line of sight are not the same line: shots, chests and blocks follow the crosshair
 * (a ray from the camera through the middle of the screen), never where the character's head happens to point.
 */
public final class Crosshair {
	private Crosshair() {
	}

	/**
	 * The first block or entity (not you) along the crosshair ray, out to range blocks from the camera; null if none.
	 * The ray only counts from level with your eyes: leaves, trunks and walls between the over-the-shoulder camera and
	 * you are behind you, and were being picked instead of the log or wall in front (so nothing broke). If the
	 * camera's shoulder offset puts that start inside a block beside you, the ray carries on from where it leaves it.
	 */
	public static HitResult trace(Minecraft mc, double range, boolean outlines) {
		if (mc.player == null || mc.level == null) return null;
		Camera cam = mc.gameRenderer.mainCamera();
		Vector3fc f = cam.forwardVector();
		Vec3 dir = new Vec3(f.x(), f.y(), f.z()).normalize();
		Vec3 to = cam.position().add(dir.scale(range));
		Vec3 from = cam.position();
		double behind = mc.player.getEyePosition().subtract(from).dot(dir);
		if (behind > 0) from = from.add(dir.scale(Math.min(behind, range)));
		ClipContext.Block shapes = outlines ? ClipContext.Block.OUTLINE : ClipContext.Block.COLLIDER;
		BlockHitResult block = mc.level.clip(new ClipContext(from, to, shapes, ClipContext.Fluid.NONE, mc.player));
		for (int i = 0; i < 3 && block.getType() != HitResult.Type.MISS && block.isInside(); i++) {
			Optional<Vec3> out = new AABB(block.getBlockPos()).clip(to, from);
			if (out.isEmpty()) break;
			from = out.get().add(dir.scale(1.0E-3));
			block = mc.level.clip(new ClipContext(from, to, shapes, ClipContext.Fluid.NONE, mc.player));
		}
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		EntityHitResult ent = ProjectileUtil.getEntityHitResult(mc.player, from, end, new AABB(from, end).inflate(1.0),
			e -> !e.isSpectator() && e.isPickable() && e != mc.player && !(mc.player.getVehicle() == e), from.distanceToSqr(end));
		if (ent != null) return ent;
		return block.getType() == HitResult.Type.MISS ? null : block;
	}

	/** Where a shot should go: what the crosshair hits within range, or the far point straight ahead. */
	public static Vec3 aimPoint(Minecraft mc, double range) {
		HitResult h = trace(mc, range, false);
		if (h != null) return h.getLocation();
		Camera cam = mc.gameRenderer.mainCamera();
		Vector3fc f = cam.forwardVector();
		return cam.position().add(new Vec3(f.x(), f.y(), f.z()).normalize().scale(range));
	}

	/**
	 * The block or entity to mine, open or hit: along the crosshair, but only within your normal reach of your eyes.
	 * Null keeps Minecraft's own result (first person, e.g. a scoped sniper).
	 */
	public static HitResult interaction(Minecraft mc, double blockReach, double entityReach) {
		Camera cam = mc.gameRenderer.mainCamera();
		if (mc.player == null || !cam.isDetached()) return null;
		Vec3 eye = mc.player.getEyePosition();
		double range = cam.position().distanceTo(eye) + Math.max(blockReach, entityReach) + 1;
		HitResult h = trace(mc, range, true);
		if (h instanceof EntityHitResult e) {
			Entity t = e.getEntity();
			if (t.getBoundingBox().distanceToSqr(eye) <= entityReach * entityReach) return h;
		} else if (h instanceof BlockHitResult b && b.getLocation().distanceToSqr(eye) <= blockReach * blockReach) return h;
		Vector3fc f = cam.forwardVector();
		Vec3 dir = new Vec3(f.x(), f.y(), f.z());
		return BlockHitResult.miss(eye.add(dir.scale(blockReach)), Direction.getApproximateNearest(dir.x, dir.y, dir.z), mc.player.blockPosition());
	}
}
