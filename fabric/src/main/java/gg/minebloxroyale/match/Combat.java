package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.hub.Obby;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.ConsumableDef;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** Fortnite health rules: 100 HP + 100 shield, shield takes gun damage first, eliminations instead of deaths. */
public final class Combat {
	private static final Map<UUID, Float> SHIELD = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);
	/** Set while a gun hit is being applied (so the melee damage-number path skips it). */
	private static final ThreadLocal<Boolean> GUN_HIT = ThreadLocal.withInitial(() -> false);
	/** Per fighter: how many of each maxUses item were used since the shield last broke. */
	private static final Map<UUID, Map<String, Integer>> USES = new ConcurrentHashMap<>();
	private static final Map<UUID, Regen> REGEN = new ConcurrentHashMap<>();

	/**
	 * Slurp-style regeneration in progress: health and shield points still to give, the tick interval, the shield cap.
	 */
	private record Regen(float healthLeft, float shieldLeft, int every, float cap) {
	}

	private Combat() {
	}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (source.getEntity() instanceof LivingEntity attacker && attacker != entity) {
				// Team modes: no friendly fire; a knocked fighter can't hurt anyone.
				if (Teams.sameTeam(attacker, entity) || Knocked.is(attacker)) return false;
				// A real player under fire: their bot teammates close by turn on the shooter.
				if (entity instanceof ServerPlayer && Teams.teamMode() && MatchManager.get().state() == MatchManager.State.LIVE) BotEntity.alertTeam(entity, attacker);
				// A real player shooting someone: their bot teammates close by join in on the same enemy.
				if (attacker instanceof ServerPlayer && Teams.teamMode() && MatchManager.get().state() == MatchManager.State.LIVE) BotEntity.assist(attacker, entity);
			}
			// No PvP in the hub: players only hurt each other in a match.
			if (entity instanceof ServerPlayer victim && source.getEntity() instanceof Player && !MatchManager.get().isAlive(victim)) return false;
			// No fall damage in the hub (the Sky Tower, the obby).
			if (entity instanceof ServerPlayer victim && !MatchManager.get().isAlive(victim) && source.is(DamageTypeTags.IS_FALL)) return false;
			// Pickaxe (melee) hits: the hitter alone sees the damage number.
			if (!BYPASS.get() && !GUN_HIT.get() && isFighter(entity) && source.getEntity() instanceof ServerPlayer hitter && source.getDirectEntity() == hitter && hitter != entity)
				Net.damageNumberTo(hitter, entity, amount, shield(entity) > 0 ? 1 : 0);
			if (BYPASS.get() || !isFighter(entity)) return true;
			float shield = shield(entity);
			if (shield <= 0) return true;
			float absorbed = Math.min(shield, amount);
			SHIELD.put(entity.getUUID(), shield - absorbed);
			if (shield - absorbed <= 0) USES.remove(entity.getUUID()); // shield broke: Mini Shields usable again
			float rest = amount - absorbed;
			if (rest > 0 && entity.level() instanceof ServerLevel level) hurtBypassingShield(level, entity, source, rest);
			else entity.hurtTime = entity.hurtDuration = 10;
			return false;
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			Entity killer = source.getEntity();
			if (entity instanceof ServerPlayer player) {
				entity.setHealth(entity.getMaxHealth());
				if (MatchManager.get().isAlive(player)) {
					if (Knocked.is(player)) {
						// Finished off while knocked (the storm finishing them credits whoever knocked them).
						Entity credit = killer != null ? killer : Knocked.knocker(player.level().getServer(), player);
						String by = killer != null ? null : Knocked.knockerName(player);
						Knocked.up(player);
						MatchManager.get().eliminate(player, credit, by);
					} else if (!Knocked.tryKnock(player, killer)) {
						MatchManager.get().eliminate(player, killer);
					}
				} else {
					Obby.respawnAtCheckpoint(player);
				}
				return false;
			}
			if (entity instanceof BotEntity bot) {
				if (Knocked.is(bot)) {
					Knocked.up(bot); // finished off: dies (BotEntity.die)
					return true;
				}
				return !Knocked.tryKnock(bot, killer);
			}
			return true;
		});
	}

	static boolean isFighter(LivingEntity e) {
		return e instanceof Player || e instanceof BotEntity;
	}

	public static void setupFighter(LivingEntity e) {
		AttributeInstance max = e.getAttribute(Attributes.MAX_HEALTH);
		float hp = MinebloxRoyale.setting("maxHealth");
		if (max != null && max.getBaseValue() != hp) {
			max.setBaseValue(hp);
			e.setHealth(hp);
		}
	}

	public static float shield(LivingEntity e) {
		return SHIELD.getOrDefault(e.getUUID(), 0F);
	}

	public static void setShield(LivingEntity e, float v) {
		if (v <= 0) USES.remove(e.getUUID());
		SHIELD.put(e.getUUID(), Math.max(0, Math.min(MinebloxRoyale.setting("maxShield"), v)));
	}

	/** A knocked fighter's Slurp-style regeneration stops. */
	static void stopRegen(LivingEntity e) {
		REGEN.remove(e.getUUID());
	}

	public static void reset(LivingEntity e) {
		SHIELD.remove(e.getUUID());
		USES.remove(e.getUUID());
		REGEN.remove(e.getUUID());
		e.setHealth(e.getMaxHealth());
	}

	public static void gunDamage(ServerLevel level, LivingEntity shooter, LivingEntity target, float amount, boolean headshot) {
		if (Teams.sameTeam(shooter, target) || Knocked.is(shooter)) return; // no friendly fire (and no numbers for it)
		DamageSource source = shooter instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(shooter);
		if (shooter instanceof BotEntity) amount *= MinebloxRoyale.setting("botDamageMult");
		// The shooter alone sees how much the hit did (yellow for a headshot, blue while it's still shield).
		if (shooter instanceof ServerPlayer sp) Net.damageNumberTo(sp, target, amount, headshot ? 2 : shield(target) > 0 ? 1 : 0);
		GUN_HIT.set(true);
		try {
			target.setInvulnerableTime(0);
			target.hurtServer(level, source, amount);
			target.setInvulnerableTime(0);
		} finally {
			GUN_HIT.set(false);
		}
	}

	/** Storm damage: the shield takes it first, then health (creator), like any other hit. */
	public static void hurtStorm(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
		target.setInvulnerableTime(0);
		target.hurtServer(level, source, amount);
	}

	/** Damage that ignores shields: the rest of a hit after the shield broke. */
	public static void hurtBypassingShield(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
		BYPASS.set(true);
		try {
			target.setInvulnerableTime(0);
			target.hurtServer(level, source, amount);
		} finally {
			BYPASS.set(false);
		}
	}

	/** Fortnite: an item that would do nothing can't be used (bandages above 75 HP, minis at 50 shield...). */
	public static boolean canUse(LivingEntity e, ConsumableDef def) {
		return whyNot(e, def) == null;
	}

	/** Why this item can't be used right now (shown to the player), or null when it can. */
	public static String whyNot(LivingEntity e, ConsumableDef def) {
		if (Knocked.is(e)) return "You can't use items while knocked";
		if (def.maxUses() > 0) {
			if (shield(e) <= 0) USES.remove(e.getUUID()); // the shield broke: Mini Shields count again from zero
			// Slurp Juice: usable again once the shield is down to reuseShield (25) or less.
			Map<String, Integer> mine = USES.get(e.getUUID());
			if (mine != null && def.reuseShield() > 0 && shield(e) <= def.reuseShield()) mine.remove(def.id());
			int used = mine == null ? 0 : mine.getOrDefault(def.id(), 0);
			if (used >= def.maxUses()) return def.reuseShield() > 0
				? "Only " + def.maxUses() + " " + def.name() + " until your shield is " + Math.round(def.reuseShield()) + " or less"
				: "Only " + def.maxUses() + " " + def.name() + "s until your shield breaks";
		}
		return switch (def.kind()) {
			case "heal" -> e.getHealth() < def.cap() ? null : "Health is already high enough for " + def.name();
			case "shield" -> shield(e) < def.cap() ? null : "Shield is already full enough for " + def.name();
			default -> e.getHealth() < e.getMaxHealth() || shield(e) < MinebloxRoyale.setting("maxShield") ? null : "You are already at full health and shield";
		};
	}

	public static void applyConsumable(LivingEntity e, ConsumableDef def) {
		if (def.maxUses() > 0) USES.computeIfAbsent(e.getUUID(), k -> new ConcurrentHashMap<>()).merge(def.id(), 1, Integer::sum);
		if (def.overTimeTicks() > 0) {
			REGEN.put(e.getUUID(), new Regen(def.kind().equals("shield") ? 0 : def.amount(), def.kind().equals("heal") ? 0 : def.amount(), def.overTimeTicks(), def.cap()));
			return;
		}
		switch (def.kind()) {
			case "heal" -> e.setHealth(Math.min(Math.max(e.getHealth(), Math.min(def.cap(), e.getHealth() + def.amount())), e.getMaxHealth()));
			case "shield" -> setShield(e, Math.max(shield(e), Math.min(def.cap(), shield(e) + def.amount())));
			default -> {
				float toHealth = Math.min(def.amount(), e.getMaxHealth() - e.getHealth());
				e.setHealth(e.getHealth() + toHealth);
				setShield(e, Math.min(def.cap(), shield(e) + (def.amount() - toHealth)));
			}
		}
	}

	/**
	 * One step of a stepped item (Chug Jug): up to {@code points} into health first; shield only once health is full.
	 * Returns the points actually given (so the jug keeps the rest).
	 */
	public static float applyStep(LivingEntity e, ConsumableDef def, float points) {
		boolean healthOk = !def.kind().equals("shield");
		boolean shieldOk = !def.kind().equals("heal");
		float maxHp = def.kind().equals("heal") ? Math.min(def.cap(), e.getMaxHealth()) : e.getMaxHealth();
		if (healthOk && e.getHealth() < maxHp) {
			float give = Math.min(points, maxHp - e.getHealth());
			e.setHealth(e.getHealth() + give);
			return give;
		}
		if (shieldOk) {
			float before = shield(e);
			setShield(e, Math.min(Math.min(def.cap(), MinebloxRoyale.setting("maxShield")), before + points));
			return shield(e) - before;
		}
		return 0;
	}

	/** Every server tick: Slurp Juice gives +1 health and +1 shield per interval, each from its own 75-point pool. */
	public static void tick(MinecraftServer server) {
		if (REGEN.isEmpty()) return;
		long now = server.overworld().getGameTime();
		for (var entry : REGEN.entrySet()) {
			Regen r = entry.getValue();
			if (now % r.every() != 0) continue;
			LivingEntity e = server.overworld().getEntity(entry.getKey()) instanceof LivingEntity le ? le : null; // players and bots
			boolean wantHealth = r.healthLeft() > 0 && e != null && e.getHealth() < e.getMaxHealth();
			boolean wantShield = r.shieldLeft() > 0 && e != null && shield(e) < Math.min(r.cap(), MinebloxRoyale.setting("maxShield"));
			if (e == null || !e.isAlive() || !wantHealth && !wantShield) {
				REGEN.remove(entry.getKey());
				continue;
			}
			if (wantHealth) e.setHealth(Math.min(e.getMaxHealth(), e.getHealth() + 1));
			if (wantShield) setShield(e, shield(e) + 1);
			REGEN.put(entry.getKey(), new Regen(r.healthLeft() - (wantHealth ? 1 : 0), r.shieldLeft() - (wantShield ? 1 : 0), r.every(), r.cap()));
		}
	}
}
