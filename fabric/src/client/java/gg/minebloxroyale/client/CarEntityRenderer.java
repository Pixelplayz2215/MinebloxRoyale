package gg.minebloxroyale.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.client.avatar.Contrails;
import gg.minebloxroyale.client.avatar.Cosmetics;
import gg.minebloxroyale.client.media.SfxBank;
import gg.minebloxroyale.client.mesh.Mesh;
import gg.minebloxroyale.match.CarBlockEntity;
import gg.minebloxroyale.match.CarEntity;
import gg.minebloxroyale.sheets.CosmeticDef;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A car being driven (v2.0.0, CarEntity): the same Blender car as a parked one (fabric/tools/blender/cars.py), its kind
 * and damage stage in its paint and trim, turned the way it drives; the lighter copies further away (Mesh.lod).
 *
 * 2.0.0-test3 (creator: "Cars should have a driving animation (also sounds), players should have an animation for
 * getting inside the car (Doors open, player climbs inside, door closes), Car should have a contrail when boosting"): it's
 * drawn in pieces (cars.py: the shell, four wheels, the front doors; where they go: car_rigs.json). The wheels turn with
 * how far it goes and the front ones steer with how fast it turns; the body leans out of a turn and dips its nose
 * braking (its nose up speeding off); a front door swings open, waits while someone climbs in (or out) and shuts; while
 * it boosts, its driver's boost trail streams from the exhausts (Contrails). Each player's game works this out from how
 * the car moves (CarSounds plays its engine from the same).
 */
public class CarEntityRenderer extends EntityRenderer<CarEntity, CarEntityRenderer.State> {
	/** About how big a car is (blocks), for picking its lighter copies far away. */
	static final float SIZE = 5F;
	/** Seconds a door takes to swing open (and shut), and how long it stays open between. */
	private static final float DOOR_SWING = 0.25F, DOOR_HOLD = 0.5F, DOOR_DEGREES = 65;
	/** The most the front wheels steer, the body leans and dips (degrees). */
	private static final float MAX_STEER = 28, MAX_LEAN = 5, MAX_DIP = 3.5F;

	public static class State extends EntityRenderState {
		CarBlockEntity.Kind kind = CarBlockEntity.Kind.SEDAN;
		int color, trim, stage = 1, id;
		float yaw, spin, steer, lean, dip, doorLeft, doorRight;
		boolean boosting;
		/** The driver's boost trail (cosmetics slot boosttrail), or null. */
		@Nullable CosmeticDef trail;
		/** The Locker's preview: a boost trail streaming as if boosting (no car entity behind it). */
		@Nullable CosmeticDef previewTrail;
	}

	/** Where a kind's wheels, door hinges and exhausts are (car_rigs.json, written by cars.py). */
	public record Rig(float wheelX, float wheelR, float frontZ, float backZ, float hingeX, float hingeZ, float[][] exhausts) {
	}

	private static Map<String, Rig> rigs;

	/** How each car on screen has been moving (by entity id). */
	private static final class Track {
		double x, z;
		float yaw, speed, spin, steer, lean, dip;
		long nanos;
		final List<Integer> riders = new ArrayList<>();
		long doorLeft = -1, doorRight = -1;
	}

	private static final Map<Integer, Track> TRACKS = new HashMap<>();

	public CarEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.1F;
	}

	public static Rig rig(CarBlockEntity.Kind kind) {
		if (rigs == null) {
			rigs = new HashMap<>();
			try {
				var res = Minecraft.getInstance().getResourceManager().getResource(MinebloxRoyale.id("car_rigs.json"));
				if (res.isPresent()) try (Reader r = res.get().openAsReader()) {
					JsonObject all = JsonParser.parseReader(r).getAsJsonObject();
					for (Map.Entry<String, JsonElement> e : all.entrySet()) {
						JsonObject o = e.getValue().getAsJsonObject();
						JsonArray ex = o.getAsJsonArray("exhausts");
						float[][] exhausts = new float[ex.size()][3];
						for (int i = 0; i < ex.size(); i++)
							for (int k = 0; k < 3; k++) exhausts[i][k] = ex.get(i).getAsJsonArray().get(k).getAsFloat();
						rigs.put(e.getKey(), new Rig(o.get("wheelX").getAsFloat(), o.get("wheelR").getAsFloat(), o.get("frontZ").getAsFloat(), o.get("backZ").getAsFloat(),
							o.get("hingeX").getAsFloat(), o.get("hingeZ").getAsFloat(), exhausts));
					}
				}
			} catch (Exception ex) {
				MinebloxRoyale.LOG.warn("BR: no car rigs", ex);
			}
		}
		Rig r = rigs.get(name(kind));
		return r != null ? r : new Rig(1.02F, 0.36F, 1.45F, -1.45F, 1.14F, 0.9F, new float[][] { { 0.7F, 0.46F, -2.38F }, { -0.7F, 0.46F, -2.38F } });
	}

	private static String name(CarBlockEntity.Kind kind) {
		return kind.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CarEntity car, State state, float partialTicks) {
		super.extractRenderState(car, state, partialTicks);
		state.kind = car.kind();
		state.color = car.color();
		state.trim = car.trim();
		state.stage = car.stage();
		state.yaw = car.getYRot(partialTicks);
		state.id = car.getId();
		state.boosting = car.boosting();
		Entity driver = car.getFirstPassenger();
		Map<String, String> outfit = driver == null ? null : Cosmetics.OUTFITS.get(driver.getUUID());
		state.trail = Cosmetics.worn(outfit, "boosttrail");
		Track t = TRACKS.computeIfAbsent(car.getId(), k -> new Track());
		long now = System.nanoTime();
		double x = car.getX(), z = car.getZ();
		if (t.nanos == 0) { // (just come into view: whoever's in it is already in, unless they're getting in right now)
			t.x = x;
			t.z = z;
			t.yaw = state.yaw;
			t.nanos = now;
			// (2.0.0-test3, creator: "Door does not swing open": getting into a parked car makes it a driven car with
			// them already in it, so this car is new to us with its driver in: CarEntering knows they just got in)
			for (Entity p : car.getPassengers()) t.riders.add(CarEntering.gettingIn(p.getId()) ? -1 : p.getId());
		}
		float dt = (now - t.nanos) / 1e9F;
		if (dt > 1e-3F) {
			dt = Math.min(dt, 0.1F);
			double yaw = Math.toRadians(state.yaw);
			// how far it went along its own nose (+z), and how fast it turned (degrees a second, + to the left)
			float along = (float) ((x - t.x) * -Math.sin(yaw) + (z - t.z) * Math.cos(yaw));
			float turn = wrap(t.yaw - state.yaw) / dt, speed = along / dt;
			float accel = (speed - t.speed) / dt;
			t.speed += (speed - t.speed) * Math.min(1, dt * 10);
			t.spin += along / rig(state.kind).wheelR();
			t.steer = ease(t.steer, clamp(turn * 0.35F, MAX_STEER), dt, 8);
			t.lean = ease(t.lean, clamp(turn * t.speed * 0.012F, MAX_LEAN), dt, 6);
			t.dip = ease(t.dip, clamp(-accel * 0.18F, MAX_DIP), dt, 5);
			t.x = x;
			t.z = z;
			t.yaw = state.yaw;
			t.nanos = now;
		}
		// someone getting in or out: their side's front door swings open and shut
		List<Integer> riders = new ArrayList<>();
		for (Entity p : car.getPassengers()) riders.add(p.getId());
		for (int i = 0; i < Math.max(riders.size(), t.riders.size()); i++) {
			Integer was = i < t.riders.size() ? t.riders.get(i) : null, is = i < riders.size() ? riders.get(i) : null;
			if (was != null && was.equals(is)) continue;
			boolean right = (is != null ? i : t.riders.indexOf(was)) % 2 == 1;
			if (right) t.doorRight = now;
			else t.doorLeft = now;
			SfxBank.play("car_door_open", car.position());
		}
		t.riders.clear();
		t.riders.addAll(riders);
		state.spin = t.spin;
		state.steer = t.steer;
		state.lean = t.lean;
		state.dip = t.dip;
		state.doorLeft = door(t, true, now, car);
		state.doorRight = door(t, false, now, car);
	}

	/** How far open a front door is now (0..1), and its shutting sound as it closes. */
	private static float door(Track t, boolean left, long now, CarEntity car) {
		long since = left ? t.doorLeft : t.doorRight;
		if (since < 0) return 0;
		float s = (now - since) / 1e9F;
		if (s >= DOOR_SWING * 2 + DOOR_HOLD) {
			if (left) t.doorLeft = -1;
			else t.doorRight = -1;
			SfxBank.play("car_door_close", car.position());
			return 0;
		}
		float f = s < DOOR_SWING ? s / DOOR_SWING : s < DOOR_SWING + DOOR_HOLD ? 1 : 1 - (s - DOOR_SWING - DOOR_HOLD) / DOOR_SWING;
		return f * f * (3 - 2 * f);
	}

	/** How fast a car is going (blocks a second, + forwards) and turning, as this game saw it: for CarSounds. */
	static float[] motion(int id) {
		Track t = TRACKS.get(id);
		return t == null ? new float[2] : new float[] { t.speed, t.steer / MAX_STEER };
	}

	/** Forgets cars that are gone. */
	static void forget(int id) {
		TRACKS.remove(id);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		String kind = name(state.kind);
		Rig rig = rig(state.kind);
		double distSq = state.distanceToCameraSq;
		int light = state.lightCoords, paint = state.color & 0xFFFFFF, trim = state.trim & 0xFFFFFF;
		poseStack.pushPose();
		poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
		// the wheels, turning (and the front ones steering), planted while the body leans over them
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2, left = i % 2 == 0;
			String id = "car_wheel_" + kind + (left ? front && state.stage >= 3 ? "_bare" : "" : "_r");
			Mesh wheel = Mesh.lod(id, distSq, SIZE);
			Matrix4f m = new Matrix4f().translate(left ? rig.wheelX() : -rig.wheelX(), rig.wheelR(), front ? rig.frontZ() : rig.backZ());
			if (front) m.rotateY((float) Math.toRadians(state.steer));
			m.rotateX(state.spin);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()),
				(pose, vc) -> wheel.draw(pose, m, vc, light, OverlayTexture.NO_OVERLAY, paint, trim));
		}
		// the body: leaning out of a turn (about its length, at the ground) and dipping its nose (about its width)
		Matrix4f body = new Matrix4f().rotateZ((float) Math.toRadians(state.lean)).rotateX((float) Math.toRadians(state.dip));
		Mesh shell = Mesh.lod("car_" + kind + "_" + state.stage + "_shell", distSq, SIZE);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()),
			(pose, vc) -> shell.draw(pose, body, vc, light, OverlayTexture.NO_OVERLAY, paint, trim));
		for (int side = 0; side < 2; side++) {
			float open = side == 0 ? state.doorLeft : state.doorRight;
			if (open <= 0.01F) continue;
			String r = side == 0 ? "" : "_r";
			float sx = side == 0 ? 1 : -1;
			Mesh doorway = Mesh.get("car_doorway_" + kind + r), door = Mesh.lod("car_door_" + kind + r, distSq, SIZE);
			Matrix4f hinge = new Matrix4f(body).translate(sx * rig.hingeX(), 0, rig.hingeZ()).rotateY((float) Math.toRadians(-sx * DOOR_DEGREES * open));
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()), (pose, vc) -> {
				doorway.draw(pose, body, vc, light, OverlayTexture.NO_OVERLAY, paint, trim);
				door.draw(pose, hinge, vc, light, OverlayTexture.NO_OVERLAY, paint, trim);
			});
		}
		// boosting: the driver's boost trail streams from the exhausts (in the Locker: the hovered one, as if boosting)
		Vector3f[] exhausts = new Vector3f[rig.exhausts().length];
		for (int i = 0; i < exhausts.length; i++) exhausts[i] = body.transformPosition(new Vector3f(rig.exhausts()[i]));
		if (state.previewTrail != null) Contrails.carPreview(collector, poseStack, state.previewTrail, exhausts);
		else if (state.boosting && state.trail != null) Contrails.recordCar(state.id, state.trail, poseStack.last().pose(), exhausts);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static float wrap(float deg) {
		deg %= 360;
		return deg > 180 ? deg - 360 : deg < -180 ? deg + 360 : deg;
	}

	private static float clamp(float v, float max) {
		return Math.max(-max, Math.min(max, v));
	}

	private static float ease(float from, float to, float dt, float rate) {
		return from + (to - from) * Math.min(1, dt * rate);
	}

	/** A car for the Locker's Boost Trails tab: the sports car, red, its boost trail streaming. */
	public static State preview(CosmeticDef trail) {
		State s = new State();
		s.entityType = CarEntity.TYPE;
		s.kind = CarBlockEntity.Kind.SPORTS;
		s.color = 0xFFD8352C;
		s.trim = 0xFFF2F4F8;
		s.stage = 1;
		s.lightCoords = Mesh.FULL_BRIGHT;
		s.previewTrail = trail;
		s.spin = (float) (System.nanoTime() / 1e9 * 9 % (Math.PI * 2));
		return s;
	}
}
