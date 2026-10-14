package gg.minebloxroyale.bot;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.match.Builds;
import gg.minebloxroyale.match.CarBlock;
import gg.minebloxroyale.match.Combat;
import gg.minebloxroyale.match.Harvest;
import gg.minebloxroyale.match.Knocked;
import gg.minebloxroyale.match.Loadout;
import gg.minebloxroyale.match.Loot;
import gg.minebloxroyale.match.LootChests;
import gg.minebloxroyale.match.MatchManager;
import gg.minebloxroyale.match.Reboot;
import gg.minebloxroyale.match.Teams;
import gg.minebloxroyale.sheets.BotDef;
import gg.minebloxroyale.sheets.HarvestRuleDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A Roblox-looking bot from the bots sheet that plays like a player: it drops from the Battle Bus (skydive, glider),
 * starts with nothing and searches chests and loots the floor, uses the best gun it has (pickaxe until then), heals
 * and shields itself only with items it carries, and harvests wood, brick and metal (trees, stone, buildings, cars) to
 * build with like a player (BotBuilder: cover, ramps, 90s, a box while healing). It opens wooden doors, climbs ladders
 * (opening the trapdoor at the top) and swims, fast swim included (creator: bots couldn't get into the shacks or up the
 * watchtowers, and drowned on lake beds). BotMovement keeps it from getting stuck anywhere (in a tree too).
 */
public class BotEntity extends PathfinderMob {
	public static EntityType<BotEntity> TYPE;
	public static final EntityDataAccessor<String> COLORS = SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.STRING);
	/** Its Locker outfit ("slot:id,slot:id", empty = default look), drawn like a player's. */
	public static final EntityDataAccessor<String> OUTFIT = SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.STRING);

	/** It farms until it has this much material in all, never more than this of one kind. */
	private static final int FARM_UNTIL = 150, FARM_EACH = 70;

	private BotDef def;
	private BotBag bag = new BotBag();
	private LivingEntity target;
	private Vec3 wanderTo, dropTo;
	private BlockPos lootAt, harvestAt;
	private int seenFor, rethink, calmTicks, swingCooldown, lastHurt = -1000, lastPlan = -1000;
	/** Ticks on the ground without a gun (a teammate of a real player who keeps looting first stays unarmed). */
	private int unarmedTicks;
	private boolean caring;
	/** Where it's trying to get to this tick (null: standing still on purpose), for BotMovement's progress checks. */
	private Vec3 heading;
	/** Chests, loot and harvest spots it couldn't get to, left alone until the tick given. */
	private final Map<BlockPos, Integer> unreachable = new HashMap<>();
	private BotBuilder builder;
	private BotMovement movement;
	/** Team modes: its team and slot (teammate colour), the real player it sticks with (null: a bot team). */
	private int team = -1, slot;
	private UUID leader;
	private LivingEntity follow, reviving;
	/** Teammates' reboot cards it carries to a van, and how long it has stood at the van. */
	public final List<Reboot.Owner> cards = new ArrayList<>();
	private int rebootTicks;
	/** Set by finish(): who gets the elimination when it bleeds out (a simulated knocker has no entity to credit). */
	private String finishedBy;
	/** Ticks spent searching the chest it stands at (chestSearchSeconds, like a player holding F). */
	private int searchTicks;
	/** The player who last hurt it, and when: it may always fight them back. */
	private UUID provokedBy;
	private int provokedAt = -1000;
	/** How long a hit lets it fight back against anyone (a player with no gun, a player others already chase). */
	private static final int PROVOKED_TICKS = 20 * 20;
	/** Bots chasing one player at once (creator: they ganged up on a player who had just landed). */
	private static final int MAX_CHASERS = 2;

	public BotEntity(EntityType<? extends BotEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MinebloxRoyale.id("bot"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<BotEntity>of(BotEntity::new, MobCategory.MISC).sized(0.6F, 1.95F).clientTrackingRange(10).updateInterval(1).build(key)); // (every tick: skydiving bots move smoothly)
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, MinebloxRoyale.setting("maxHealth"))
			.add(Attributes.MOVEMENT_SPEED, 0.28)
			.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	/**
	 * A bot becomes real near players carrying its roster bag. dropTo != null: it is still falling from the bus and
	 * steers to that landing spot (skydive, then glider), like the player's drop.
	 */
	public static BotEntity spawn(ServerLevel level, BotDef def, String name, Vec3 at, BotBag bag, Vec3 dropTo) {
		BotEntity bot = TYPE.create(level, EntitySpawnReason.EVENT);
		if (bot == null) return null;
		bot.def = def;
		bot.bag = bag;
		bot.dropTo = dropTo;
		bot.entityData.set(COLORS, def.colors());
		bot.setCustomName(Component.literal(name));
		bot.setCustomNameVisible(true);
		bot.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(def.speed());
		bot.setDropChance(EquipmentSlot.MAINHAND, 0F); // its whole bag drops instead (die)
		bot.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360F, 0F);
		Combat.setupFighter(bot);
		bot.equipBest();
		level.addFreshEntity(bot);
		return bot;
	}

	/** Swimming (it floats up and paddles instead of sinking) and wooden doors (opened as it walks through, then shut). */
	@Override
	protected void registerGoals() {
		getNavigation().setCanOpenDoors(true);
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new OpenDoorGoal(this, true));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(COLORS, "classic");
		builder.define(OUTFIT, "");
	}

	public void setTeam(int team, int slot, UUID leader) {
		this.team = team;
		this.slot = slot;
		this.leader = leader;
	}

	public int team() {
		return team;
	}

	BotBag bag() {
		return bag;
	}

	/** BotMovement: stuck on the way to a chest, loot or something to harvest: leave that one alone for a minute. */
	void giveUpTarget() {
		for (BlockPos p : new BlockPos[] { lootAt, harvestAt }) if (p != null) unreachable.put(p, tickCount + 1200);
		lootAt = null;
		harvestAt = null;
		searchTicks = 0;
	}

	private boolean reachable(BlockPos p) {
		Integer until = unreachable.get(p);
		if (until == null) return true;
		if (until > tickCount) return false;
		unreachable.remove(p);
		return true;
	}

	boolean isKnocked() {
		return Knocked.is(this);
	}

	private BotBuilder builder() {
		if (builder == null) builder = new BotBuilder(this);
		return builder;
	}

	private BotMovement movement() {
		if (movement == null) movement = new BotMovement(this, builder());
		return movement;
	}

	public int slot() {
		return slot;
	}

	/** Knocked down (Knocked): nothing in its hands, no target; it crawls to its team. */
	public void onKnocked() {
		target = null;
		caring = false;
		lootAt = null;
		harvestAt = null;
		rebootTicks = 0;
		getNavigation().stop();
		setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	public void onRevived() {
		equipBest();
	}

	/** Eliminated while knocked (bled out, or nobody left standing): credit to the knocker. */
	public void finish(Entity credit, String byName) {
		finishedBy = byName;
		ServerLevel level = (ServerLevel) level();
		DamageSource src = credit instanceof Player p ? level.damageSources().playerAttack(p)
			: credit instanceof LivingEntity le ? level.damageSources().mobAttack(le) : level.damageSources().genericKill();
		setHealth(0);
		die(src);
	}

	/** Crawling (knocked) bots are low, like knocked players. */
	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		return pose == Pose.SWIMMING ? EntityDimensions.scalable(0.6F, 0.6F).withEyeHeight(0.4F) : super.getDefaultDimensions(pose);
	}

	/**
	 * A real player shot someone: their bot teammates close by join in on the same enemy (if they aren't busy
	 * fighting).
	 */
	public static void assist(LivingEntity player, LivingEntity enemy) {
		if (!(player.level() instanceof ServerLevel level) || !Teams.teamMode() || Teams.sameTeam(player, enemy) || !enemy.isAlive()) return;
		for (BotEntity b : level.getEntitiesOfClass(BotEntity.class, player.getBoundingBox().inflate(48),
			b -> Teams.sameTeam(b, player) && !Knocked.is(b) && (b.target == null || !b.target.isAlive())))
			b.target = enemy;
	}

	/** A real player was shot: their bot teammates close by turn on the shooter (if they aren't busy fighting). */
	public static void alertTeam(LivingEntity victim, LivingEntity attacker) {
		if (!(victim.level() instanceof ServerLevel level) || !Teams.teamMode()) return;
		for (BotEntity b : level.getEntitiesOfClass(BotEntity.class, victim.getBoundingBox().inflate(48),
			b -> Teams.sameTeam(b, victim) && !Knocked.is(b) && (b.target == null || !b.target.isAlive())))
			b.target = attacker;
	}

	public String outfit() {
		return this.entityData.get(OUTFIT);
	}

	public void setOutfit(String outfit) {
		this.entityData.set(OUTFIT, outfit == null ? "" : outfit);
	}

	/**
	 * A random look for a bot (creator: bots wear presets, cosmetics and particles too, or the default skin): about a
	 * third a whole preset, a third a random mix of items (sometimes a particle), the rest the default look.
	 */
	public static String randomOutfit(RandomSource rnd) {
		Map<String, String> o = new LinkedHashMap<>();
		float roll = rnd.nextFloat();
		if (roll < 0.35F && !Sheets.PRESETS.isEmpty()) {
			var p = Sheets.PRESETS.get(rnd.nextInt(Sheets.PRESETS.size()));
			String[][] slots = { { "hat", p.hat() }, { "hair", p.hair() }, { "face", p.face() }, { "back", p.back() }, { "shirt", p.shirt() }, { "pants", p.pants() }, { "shoes", p.shoes() } };
			for (String[] sl : slots) if (sl[1] != null && !sl[1].isEmpty() && !sl[1].equals("-")) o.put(sl[0], sl[1]);
		} else if (roll < 0.7F) {
			for (String slot : new String[] { "hat", "hair", "face", "back", "shirt", "pants", "shoes" }) {
				if (rnd.nextFloat() > 0.6F) continue;
				var items = Sheets.COSMETICS.stream().filter(c -> c.slot().equals(slot)).toList();
				if (!items.isEmpty()) o.put(slot, items.get(rnd.nextInt(items.size())).id());
			}
		}
		if (roll < 0.7F && rnd.nextFloat() < 0.25F) {
			var fx = Sheets.COSMETICS.stream().filter(c -> c.slot().equals("particle")).toList();
			if (!fx.isEmpty()) o.put("particle", fx.get(rnd.nextInt(fx.size())).id());
		}
		StringBuilder sb = new StringBuilder();
		o.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : ",").append(k).append(':').append(v));
		return sb.toString();
	}

	public String colors() {
		return this.entityData.get(COLORS);
	}

	public boolean dropping() {
		return dropTo != null;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	/** Hold the best gun; no gun: the pickaxe (melee). */
	private void equipBest() {
		ItemStack gun = bag.bestGun();
		setItemInHand(InteractionHand.MAIN_HAND, gun != null ? gun : Loadout.pickaxe());
	}

	private boolean armed() {
		return getMainHandItem().getItem() instanceof WeaponItem;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (def == null) {
			discard();
			return;
		}
		if (dropTo != null) {
			tickDrop(level);
			return;
		}
		if (Knocked.is(this)) {
			builder().stop();
			crawl(level);
			return;
		}
		heading = null;
		think(level);
		movement().swimTick(level);
		if (tickCount % 40 == 0) movement().watch(level, heading);
	}

	/** One tick of play, once it's on the ground and standing. */
	private void think(ServerLevel level) {
		builder().tick(level);
		ItemStack held = getMainHandItem();
		if (held.getItem() instanceof WeaponItem w) Guns.tickReload(held, w.def, level.getGameTime(), this);
		climbTick(level);
		// Still no gun after 30 s on the ground: it finds one in a building nearby, as the simulated bots do (creator:
		// bot teammates never fought, because the player they follow had already emptied the chests on the way).
		if (bag.bestGun() == null) {
			if (++unarmedTicks >= 600 && unarmedTicks % 300 == 0) {
				bag.addAll(Loot.chestContents(getRandom()));
				equipBest();
			}
		} else unarmedTicks = 0;
		// Drinking / bandaging: stand still until done, then back to the gun.
		if (caring) {
			getNavigation().stop();
			if (!isUsingItem()) {
				caring = false;
				equipBest();
				builder().openBox(level, MatchManager.get().safeSpot());
			}
			return;
		}
		if (--rethink <= 0) {
			rethink = 10;
			pickTarget(level);
		}
		if (target != null && target.isAlive()) {
			calmTicks = 0;
			fight(level);
			return;
		}
		seenFor = 0;
		calmTicks++;
		builder().stop(); // a ramp push or 90s ends with its fight
		// Team modes: a knocked teammate close by comes first (Fortnite's bots revive too).
		if (reviveTick(level)) return;
		// Out of a fight for a moment: heal / shield with what it carries (only if it has the right item).
		if (calmTicks > 40) {
			ItemStack care = bag.careItem(this);
			if (care != null) {
				// Shot not long ago: box up first (creator: build a box to heal in), toughest walls it can afford.
				if (tickCount - lastHurt < 200) builder().box(level);
				setItemInHand(InteractionHand.MAIN_HAND, care);
				startUsingItem(InteractionHand.MAIN_HAND);
				caring = true;
				return;
			}
		}
		MatchManager mm = MatchManager.get();
		Vec3 safe = mm.safeSpot();
		double dx = getX() - safe.x, dz = getZ() - safe.z;
		if (dx * dx + dz * dz > mm.safeRadius() * mm.safeRadius() * 0.8) {
			headForCircle(level, safe);
			return;
		}
		// Teammates' reboot cards: pick them up and take them to a van.
		if (cardTick(level)) return;
		// Stick with the team (a real teammate first), then loot (bots start with nothing), gather materials, wander. A
		// bot without a gun loots first, so it doesn't trail its player unarmed.
		if (bag.bestGun() == null && lootTick(level)) return;
		if (followTick(level)) return;
		if (lootTick(level)) return;
		if (harvestTick(level)) return;
		if (wanderTo == null || getNavigation().isDone()) {
			// Somewhere 8 to 32 blocks off (Minecraft's pathfinding can't plan much further: far spots left bots standing
			// still), kept well inside the circle.
			double a = getRandom().nextDouble() * Math.PI * 2, d = 8 + getRandom().nextDouble() * 24;
			double wx = getX() + Math.cos(a) * d, wz = getZ() + Math.sin(a) * d, ox = wx - safe.x, oz = wz - safe.z, r = Math.hypot(ox, oz), max = mm.safeRadius() * 0.7;
			if (r > max) {
				wx = safe.x + ox / r * max;
				wz = safe.z + oz / r * max;
			}
			wanderTo = new Vec3(wx, getY(), wz);
		}
		navigateTo(wanderTo);
	}

	/**
	 * Outside the circle: walk towards it in short hops (Minecraft's pathfinding gives up on far-away targets, which
	 * left bots standing still out in the storm). Getting stuck on the way is BotMovement's job.
	 */
	private void headForCircle(ServerLevel level, Vec3 safe) {
		heading = safe;
		double dx = safe.x - getX(), dz = safe.z - getZ(), d = Math.sqrt(dx * dx + dz * dz);
		if (d < 1 || tickCount % 40 != 0 && !getNavigation().isDone()) return;
		double hop = Math.min(d, 20);
		int tx = Mth.floor(getX() + dx / d * hop), tz = Mth.floor(getZ() + dz / d * hop);
		getNavigation().moveTo(tx + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz), tz + 0.5, 1.15);
	}

	/** The drop from the bus: skydive (fast fall) then glider below gliderHeight, steering to the landing spot. */
	private void tickDrop(ServerLevel level) {
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, getBlockX(), getBlockZ());
		double above = getY() - ground;
		if (onGround() || isInWater() || above < 0.6) {
			dropTo = null;
			return;
		}
		boolean glide = above < MinebloxRoyale.setting("gliderHeight");
		double steer = MinebloxRoyale.setting(glide ? "gliderSteer" : "skydiveSteer"), fall = MinebloxRoyale.setting(glide ? "gliderFall" : "skydiveFall");
		double dx = dropTo.x - getX(), dz = dropTo.z - getZ(), d = Math.hypot(dx, dz);
		double vx = d > 1 ? dx / d * Math.min(d, steer) : 0, vz = d > 1 ? dz / d * Math.min(d, steer) : 0;
		setDeltaMovement(vx, -fall, vz);
		if (d > 1) {
			float yaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
			setYRot(yaw);
			setYHeadRot(yaw);
		}
		resetFallDistance();
	}

	private void fight(ServerLevel level) {
		MatchManager mm = MatchManager.get();
		ItemStack held = getMainHandItem();
		getLookControl().setLookAt(target, 60F, 60F);
		faceTarget();
		double dist = distanceTo(target);
		planBuild();
		boolean building = builder().busy();
		if (!(held.getItem() instanceof WeaponItem w)) {
			// No gun yet: swing the pickaxe up close.
			if (!building) chase(1.1);
			if (dist < 2.6 && swingCooldown-- <= 0 && mm.botsMayShoot(level.getGameTime())) {
				swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
				target.hurtServer(level, level.damageSources().mobAttack(this), MinebloxRoyale.setting("pickaxeDamage"));
				swingCooldown = 12;
			}
			return;
		}
		float pref = w.def.botPreferredRange();
		if (!building) { // (while a ramp push or 90s runs, its legs are the plan's)
			if (dist > pref * 1.3) chase(1.0);
			else if (dist < pref * 0.6) strafe();
			else getNavigation().stop();
		}
		if (++seenFor > def.reactionTicks() && mm.botsMayShoot(level.getGameTime())) {
			boolean steady = getRandom().nextFloat() < def.accuracy();
			Guns.tryFire(this, held, w.def, steady ? 1F : 4F);
		}
	}

	private void chase(double speed) {
		heading = target.position();
		getNavigation().moveTo(target, speed);
	}

	/**
	 * Building with a plan in a fight (creator: "bots strategically build"): someone a storey or more above it gets a
	 * ramp push towards them (further off) or 90s on the spot (close by), to take the high ground.
	 */
	private void planBuild() {
		if (builder().busy() || tickCount - lastPlan < 100) return;
		double dy = target.getY() - getY(), flat = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
		if (dy < 3 || flat > 40) return;
		if (flat > 10 ? builder().startRampPush(target.position(), target.getY() + 1) : builder().startNineties(target.position(), target.getY() + 3)) lastPlan = tickCount;
	}

	/**
	 * Walk to the nearest closed chest (or loot lying on the ground) and take it: a chest is searched as long as a player
	 * takes, and its loot, popped out on the ground, picked up after. True while busy looting.
	 */
	private boolean lootTick(ServerLevel level) {
		if (bag.size() >= 6 && armed()) return false;
		if (lootAt == null || tickCount % 40 == 0 && searchTicks == 0) lootAt = findLoot(level);
		if (lootAt == null) return false;
		if (lootAt.distToCenterSqr(position()) > 2.8 * 2.8) {
			searchTicks = 0;
			if (lootAt.getY() - getY() >= 3 && ladderTo(level, lootAt)) return true; // up the watchtower
			navigateTo(Vec3.atCenterOf(lootAt));
			return true;
		}
		if (LootChests.closed(level, lootAt)) {
			getNavigation().stop();
			getLookControl().setLookAt(Vec3.atCenterOf(lootAt));
			if (++searchTicks < LootChests.searchTicks()) return true;
			searchTicks = 0;
			LootChests.open(level, lootAt, this);
			lootAt = null; // its loot is on the ground now: found and picked up next
			return true;
		}
		searchTicks = 0;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(2.5), i -> !i.hasPickUpDelay() && !Reboot.isCard(i.getItem()))) {
			bag.add(item.getItem());
			item.discard();
		}
		lootAt = null;
		equipBest();
		return true;
	}

	private BlockPos findLoot(ServerLevel level) {
		BlockPos best = null;
		double bestD = 40 * 40;
		for (BlockPos c : MatchManager.get().placedChests()) {
			double d = c.distToCenterSqr(position());
			if (d < bestD && nearTeam(c.getX(), c.getZ()) && LootChests.closed(level, c) && reachable(c)) {
				best = c;
				bestD = d;
			}
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(16), i -> !Loadout.isVanilla(i.getItem()) && !Reboot.isCard(i.getItem()))) {
			double d = item.distanceToSqr(this);
			if (d < bestD && nearTeam(item.getX(), item.getZ()) && reachable(item.blockPosition())) {
				best = item.blockPosition();
				bestD = d;
			}
		}
		return best;
	}

	/** Team modes: loot only near whoever it sticks with (else it would wander off and walk back, over and over). */
	private boolean nearTeam(double x, double z) {
		if (follow == null || !follow.isAlive()) return true;
		double dx = follow.getX() - x, dz = follow.getZ() - z;
		return dx * dx + dz * dz < 24 * 24;
	}

	/**
	 * Something to reach high above (the watchtower's lookout): walk into the ladder close by. False if there's no
	 * ladder near, so it just walks towards it as usual.
	 */
	private boolean ladderTo(ServerLevel level, BlockPos goal) {
		if (onClimbable()) return true; // climbTick takes it up
		if (Math.abs(goal.getX() + 0.5 - getX()) > 6 || Math.abs(goal.getZ() + 0.5 - getZ()) > 6) return false;
		BlockPos feet = blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(feet.offset(-5, -1, -5), feet.offset(5, 1, 5)))
			if (level.getBlockState(p).is(BlockTags.CLIMBABLE) && p.getY() <= goal.getY()) {
				heading = Vec3.atBottomCenterOf(p);
				getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 1.0);
				return true;
			}
		return false;
	}

	/**
	 * On a ladder with somewhere to be above: climb, opening a closed wooden trapdoor overhead (the watchtower's hatch),
	 * and at the top hop off towards the goal.
	 */
	private void climbTick(ServerLevel level) {
		if (!onClimbable() || lootAt == null || lootAt.getY() <= getY() + 0.5) return;
		Vec3 m = getDeltaMovement();
		for (int dy = 1; dy <= 3; dy++) {
			BlockPos p = blockPosition().above(dy);
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof TrapDoorBlock && !s.getValue(TrapDoorBlock.OPEN) && s.is(BlockTags.WOODEN_TRAPDOORS)) {
				level.setBlock(p, s.setValue(TrapDoorBlock.OPEN, true), 2);
				level.playSound(null, p, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1F, 1F);
			}
		}
		if (getY() >= lootAt.getY() - 1.6) {
			// Level with the lookout: a hop up and over, onto the planks.
			double dx = lootAt.getX() + 0.5 - getX(), dz = lootAt.getZ() + 0.5 - getZ(), d = Math.max(0.01, Math.hypot(dx, dz));
			setDeltaMovement(dx / d * 0.25, 0.42, dz / d * 0.25);
		} else {
			setDeltaMovement(m.x * 0.3, 0.2, m.z * 0.3);
		}
		resetFallDistance();
	}

	/**
	 * Low on materials: farm with the pickaxe like a player (creator: bots break metal, wood and stone): trees for wood,
	 * stone and brick walls for brick, cars, copper and iron for metal, whatever is near, the material it has least of
	 * first. A car takes several hits; the rest comes down in one.
	 */
	private boolean harvestTick(ServerLevel level) {
		if (harvestAt == null && bag.totalMaterials() >= FARM_UNTIL) return false;
		if (harvestAt == null && tickCount % 60 == 0) harvestAt = findHarvest(level);
		if (harvestAt == null) return false;
		if (Harvest.quickRule(level.getBlockState(harvestAt)) == null) { // gone (someone else broke it)
			harvestAt = null;
			equipBest();
			return false;
		}
		if (harvestAt.distToCenterSqr(position()) > 3.5 * 3.5) {
			navigateTo(Vec3.atCenterOf(harvestAt));
			if (tickCount % 200 == 0) harvestAt = null; // can't get there: give up
			return true;
		}
		getNavigation().stop();
		getLookControl().setLookAt(Vec3.atCenterOf(harvestAt));
		setItemInHand(InteractionHand.MAIN_HAND, Loadout.pickaxe());
		if (swingCooldown-- > 0) return true;
		swingCooldown = 8;
		swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		Map<String, Float> mats = Harvest.harvestAt(level, harvestAt);
		if (mats != null) mats.forEach((m, n) -> bag.addMaterial(m, Math.round(n)));
		if (!(level.getBlockState(harvestAt).getBlock() instanceof CarBlock)) {
			harvestAt = null;
			equipBest();
		}
		return true;
	}

	/**
	 * Something to harvest within 12 blocks, from its feet up (it never digs the ground away under itself), never a
	 * player's build: the closest, counting the material it has least of as half the distance.
	 */
	private BlockPos findHarvest(ServerLevel level) {
		BlockPos me = blockPosition(), best = null;
		String want = bag.scarcest();
		double bestScore = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(me.offset(-12, 0, -12), me.offset(12, 6, 12))) {
			HarvestRuleDef r = Harvest.quickRule(level.getBlockState(p));
			if (r == null || Builds.isBuilt(p) || bag.material(r.material()) >= FARM_EACH || !reachable(p)) continue;
			double score = p.distSqr(me) * (r.material().equals(want) ? 0.5 : 1);
			if (score < bestScore) {
				bestScore = score;
				best = p.immutable();
			}
		}
		return best;
	}

	private void navigateTo(Vec3 at) {
		heading = at;
		if (getNavigation().isDone() || tickCount % 40 == 0) getNavigation().moveTo(at.x, at.y, at.z, 1.0);
	}

	private void strafe() {
		if (tickCount % 30 == 0) {
			Vec3 side = new Vec3(-(target.getZ() - getZ()), 0, target.getX() - getX()).normalize().scale(getRandom().nextBoolean() ? 4 : -4);
			getNavigation().moveTo(getX() + side.x, getY(), getZ() + side.z, 1.0);
		}
	}

	private void faceTarget() {
		double dx = target.getX() - getX(), dz = target.getZ() - getZ(), dy = target.getEyeY() - 0.2 - getEyeY();
		float yaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
		float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180F / Math.PI));
		setYRot(yaw);
		setYHeadRot(yaw);
		setXRot(pitch);
	}

	private void pickTarget(ServerLevel level) {
		// (A player who switched to creative is dropped too, like one in creative is never picked.)
		if (target != null && (!target.isAlive() || target.isSpectator() || target instanceof Player tp && tp.isCreative() || Teams.sameTeam(this, target)
			|| distanceTo(target) > 64 || !hasLineOfSight(target) || target instanceof Player p && !mayChase(level, p))) target = null;
		// A visible player within botPlayerRange can be picked at any time, even while this bot is fighting another
		// bot.
		float playerRange = MinebloxRoyale.setting("botPlayerRange");
		if (target instanceof BotEntity && getRandom().nextFloat() < MinebloxRoyale.setting("botPlayerSwitchChance")) {
			Player p = nearestPlayer(level, playerRange);
			if (p != null) target = p;
		}
		if (target != null) return;
		// The nearest visible fighter, players first (a player within botPlayerRange is chased when that's fair:
		// mayChase); without a gun, other bots only up close.
		Player near = nearestPlayer(level, playerRange);
		if (near != null) {
			target = near;
			return;
		}
		double reach = armed() ? 48 : 6;
		target = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach),
				e -> e != this && e.isAlive() && !e.isSpectator() && !Teams.sameTeam(this, e) && (e instanceof BotEntity || (e instanceof Player p && !p.isCreative() && mayChase(level, p))) && hasLineOfSight(e))
			.stream().min(Comparator.comparingDouble(e -> distanceToSqr(e) * (e instanceof Player && distanceTo(e) <= playerRange ? 0.5 : 1.0))).orElse(null);
	}

	private Player nearestPlayer(ServerLevel level, float range) {
		return level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(range),
				e -> e.isAlive() && !e.isSpectator() && !e.isCreative() && !Teams.sameTeam(this, e) && distanceTo(e) <= range && hasLineOfSight(e) && mayChase(level, e))
			.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
	}

	/**
	 * Fair to go after this player (creator: bots ganged up on a player who had just landed, before they had a gun)? A
	 * player who hit it lately, always. Otherwise only with a gun of its own (it loots first after landing), only a
	 * player who has a gun too, and only while fewer than MAX_CHASERS bots closer to them are on them already.
	 */
	private boolean mayChase(ServerLevel level, Player p) {
		if (p.getUUID().equals(provokedBy) && tickCount - provokedAt < PROVOKED_TICKS) return true;
		if (!armed() || !hasGun(p)) return false;
		double mine = distanceToSqr(p);
		return level.getEntitiesOfClass(BotEntity.class, p.getBoundingBox().inflate(64),
			b -> b != this && b.target == p && b.isAlive() && b.distanceToSqr(p) < mine).size() < MAX_CHASERS;
	}

	private static boolean hasGun(Player p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++)
			if (p.getInventory().getItem(i).getItem() instanceof WeaponItem) return true;
		return false;
	}

	/** Knocked: crawl towards the nearest teammate who is standing (or stay put). */
	private void crawl(ServerLevel level) {
		if (tickCount % 20 != 0) return;
		LivingEntity mate = nearestTeammate(level, 40, false);
		if (mate != null && distanceTo(mate) > 2) getNavigation().moveTo(mate, 1.0);
		else getNavigation().stop();
	}

	/** The closest teammate (players and bots): knocked ones only, or standing ones only. */
	private LivingEntity nearestTeammate(ServerLevel level, double within, boolean knocked) {
		if (!Teams.teamMode() || team < 0) return null;
		return level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(within),
				e -> e != this && e.isAlive() && !e.isSpectator() && Teams.sameTeam(this, e) && Knocked.is(e) == knocked)
			.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
	}

	/** Waiting at a reboot van to bring teammates back (everyone's game shows it at the van's screen). */
	public boolean rebooting() {
		return rebootTicks > 0;
	}

	/** A knocked teammate within 32 blocks: go to them and revive them (reviveSeconds standing next to them). */
	private boolean reviveTick(ServerLevel level) {
		if (!Teams.teamMode()) return false;
		if (tickCount % 10 == 0 || reviving != null && (!reviving.isAlive() || !Knocked.is(reviving))) reviving = nearestTeammate(level, 32, true);
		if (reviving == null) return false;
		if (distanceTo(reviving) > MinebloxRoyale.setting("reviveRange") - 0.6) {
			navigateTo(reviving.position());
			return true;
		}
		getNavigation().stop();
		getLookControl().setLookAt(reviving, 30F, 30F);
		if (Knocked.reviveTick(reviving, this)) reviving = null;
		return true;
	}

	/** Team cards on the ground nearby: pick them up. Carrying cards: walk to the nearest ready van and reboot. */
	private boolean cardTick(ServerLevel level) {
		if (!Teams.teamMode()) return false;
		if (cards.isEmpty()) {
			if (tickCount % 10 != 0) return false;
			ItemEntity card = level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(24),
					i -> Reboot.isCard(i.getItem()) && Reboot.owners(i.getItem()).stream().allMatch(o -> o.team() == team))
				.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
			if (card == null) return false;
			if (distanceTo(card) > 1.5) navigateTo(card.position());
			else Reboot.tryPickUp(this, card);
			return true;
		}
		Reboot.Van van = Reboot.vanAt(this);
		if (van != null && Reboot.ready(van, level.getGameTime())) {
			getNavigation().stop();
			if (++rebootTicks >= MinebloxRoyale.setting("rebootSeconds") * 20) {
				rebootTicks = 0;
				MatchManager.get().botReboot(this, van);
			}
			return true;
		}
		rebootTicks = 0;
		Reboot.Van to = Reboot.nearestReady(this, 400);
		if (to == null) return false;
		// Far away: hops towards it (pathfinding gives up on far targets), like heading for the circle.
		double dx = to.x - getX(), dz = to.z - getZ(), d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
		heading = new Vec3(to.x, getY(), to.z);
		if (tickCount % 40 == 0 || getNavigation().isDone()) {
			double hop = Math.min(d, 20);
			int tx = Mth.floor(getX() + dx / d * hop), tz = Mth.floor(getZ() + dz / d * hop);
			getNavigation().moveTo(tx + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz), tz + 0.5, 1.15);
		}
		return true;
	}

	/**
	 * Stick with the team: its real player first, else the standing bot teammate with the lowest slot. Further than
	 * botFollowRange away: walk to them.
	 */
	private boolean followTick(ServerLevel level) {
		if (!Teams.teamMode() || team < 0) return false;
		if (tickCount % 20 == 0 || follow != null && (!follow.isAlive() || Knocked.is(follow))) follow = pickFollow(level);
		if (follow == null) return false;
		double range = MinebloxRoyale.setting("botFollowRange");
		if (distanceToSqr(follow) < range * range) return false;
		heading = follow.position();
		if (getNavigation().isDone() || tickCount % 20 == 0) getNavigation().moveTo(follow, distanceToSqr(follow) > 40 * 40 ? 1.2 : 1.05);
		return true;
	}

	private LivingEntity pickFollow(ServerLevel level) {
		MatchManager mm = MatchManager.get();
		if (leader != null && level.getServer().getPlayerList().getPlayer(leader) instanceof ServerPlayer p && mm.isAlive(p) && !Knocked.is(p)) return p;
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers())
			if (Teams.sameTeam(this, p) && mm.isAlive(p) && !Knocked.is(p)) return p;
		BotEntity best = null;
		for (BotEntity b : level.getEntitiesOfClass(BotEntity.class, getBoundingBox().inflate(160),
			b -> b != this && b.isAlive() && b.team == team && b.slot < slot && !Knocked.is(b) && b.dropTo == null))
			if (best == null || b.slot < best.slot) best = b;
		return best;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide()) {
			// Fortnite: an eliminated bot drops everything it looted, plus ammo for its gun.
			ItemStack gun = bag.bestGun();
			for (ItemStack s : bag.items()) Containers.dropItemStack(level(), getX(), getY() + 0.5, getZ(), s.copy());
			if (gun != null && gun.getItem() instanceof WeaponItem w)
				Containers.dropItemStack(level(), getX(), getY() + 0.5, getZ(), ModItems.ammo(w.def.ammo(), Sheets.AMMO_BY_ID.get(w.def.ammo()).botDrop()));
			for (ItemStack m : bag.materialStacks()) Containers.dropItemStack(level(), getX(), getY() + 0.5, getZ(), m);
			// Team modes: the reboot cards it carried fall where it went down.
			Reboot.dropCarried(this);
			MatchManager.get().onBotDied(this, source.getEntity(), finishedBy);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		Entity by = source.getEntity();
		if (hurt && by instanceof LivingEntity le && le != this && !Teams.sameTeam(this, le) && !Knocked.is(this)) {
			lastHurt = tickCount;
			if (le instanceof Player) {
				provokedBy = le.getUUID();
				provokedAt = tickCount;
			}
			if (target == null || getRandom().nextFloat() < 0.5F) target = le;
			if (caring) { // shot while drinking: stop and fight back (out of its box's wall on the shooter's side)
				stopUsingItem();
				caring = false;
				equipBest();
				builder().openBox(level, le.position());
			}
			cover(level, le);
		}
		return hurt;
	}

	/**
	 * Shot at: most times a wall towards the shooter (Fortnite's reflex), of the material it has most of, unless it's
	 * busy with a ramp push or 90s; a shooter above it gets one of those instead (planBuild).
	 */
	private void cover(ServerLevel level, LivingEntity from) {
		if (dropTo != null || builder().busy() || from.getY() - getY() > 3 || getRandom().nextFloat() > 0.7F) return;
		builder().wall(level, Direction.fromYRot((float) (Mth.atan2(from.getZ() - getZ(), from.getX() - getX()) * (180F / Math.PI)) - 90F));
	}
}
