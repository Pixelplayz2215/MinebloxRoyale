package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.hub.Gold;
import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.BossDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Bosses (v2.0.0, creator: players report the middle of a match gets boring with nobody to fight yet; "A boss icon on
 * the minimap; bosses drop their own custom loot"). Each match picks a few from the bosses sheet and gives each a lair
 * somewhere on the island, well apart; a boss appears at its lair when someone comes near (keeping the health it had if
 * it was there before), fights anyone it sees close by, and never strays far (BotEntity's boss mode). Everyone in the
 * match sees where the bosses are (Net.BossMarks: the minimap and map icons). Eliminated, it drops its gun (Boss rarity,
 * 2.0.0-test3) with ammo and its loot, and its eliminator gets goldBoss gold. Bosses aren't players: they don't count
 * towards players left.
 */
public final class Bosses {
	/** One boss this match: who, its lair, its entity while there, its health between visits, out or not. */
	private static final class Boss {
		final BossDef def;
		final double x, z;
		UUID entity;
		float health = -1, shield = -1;
		boolean out, lair;

		Boss(BossDef def, double x, double z) {
			this.def = def;
			this.x = x;
			this.z = z;
		}
	}

	private static final List<Boss> BOSSES = new ArrayList<>();

	private Bosses() {
	}

	/** A new match: its bosses and their lairs, between a quarter and two thirds of the way out, far apart. */
	public static void start(double cx, double cz, double radius, RandomSource rnd) {
		BOSSES.clear();
		List<BossDef> defs = new ArrayList<>(Sheets.BOSSES);
		int n = Math.min(defs.size(), Math.round(MinebloxRoyale.setting("bosses")));
		for (int i = 0; i < n; i++) {
			BossDef def = defs.remove(rnd.nextInt(defs.size()));
			double bx = cx, bz = cz;
			for (int tries = 0; tries < 60; tries++) {
				double a = rnd.nextDouble() * Math.PI * 2, d = radius * (0.25 + rnd.nextDouble() * 0.42);
				bx = cx + Math.cos(a) * d;
				bz = cz + Math.sin(a) * d;
				boolean apart = true;
				for (Boss o : BOSSES) apart &= Math.hypot(o.x - bx, o.z - bz) > radius * 0.5;
				if (apart) break;
			}
			BOSSES.add(new Boss(def, bx, bz));
		}
		MinebloxRoyale.LOG.info("BR: bosses this match: {}", BOSSES.stream().map(b -> b.def.name() + " at " + Math.round(b.x) + "," + Math.round(b.z)).toList());
	}

	/** The match is over: bosses go. */
	public static void stop(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (Boss b : BOSSES)
			if (b.entity != null && level.getEntity(b.entity) instanceof BotEntity e) e.discard();
		BOSSES.clear();
	}

	/** Each second of a live match: bosses appear at their lairs when someone is near; everyone hears where they are. */
	public static void second(MinecraftServer server) {
		if (BOSSES.isEmpty()) return;
		ServerLevel level = server.overworld();
		double near = MinebloxRoyale.setting("bossSpawnRange");
		for (Boss b : BOSSES) {
			if (b.out) continue;
			Entity e = b.entity == null ? null : level.getEntity(b.entity);
			if (e instanceof BotEntity bot && bot.isAlive()) {
				b.health = bot.getHealth();
				b.shield = Combat.shield(bot);
				continue;
			}
			b.entity = null;
			boolean someone = false;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) someone |= Math.hypot(p.getX() - b.x, p.getZ() - b.z) < near;
			int bx = Mth.floor(b.x), bz = Mth.floor(b.z);
			if (!someone || !level.hasChunk(bx >> 4, bz >> 4)) continue;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
			if (!b.lair) { // (its lair, the first time it appears: a gold-rimmed platform you can see from a way off)
				CustomStructures.bossLair(level, new BlockPos(bx, y, bz));
				b.lair = true;
			}
			BotEntity bot = BotEntity.spawnBoss(level, b.def, new Vec3(bx + 0.5, y, bz + 0.5));
			if (bot == null) continue;
			if (b.health > 0) bot.setHealth(Math.min(bot.getMaxHealth(), b.health));
			if (b.shield >= 0) Combat.setShield(bot, b.shield);
			b.entity = bot.getUUID();
		}
		// where they are (an out boss's mark goes)
		float[] marks = new float[BOSSES.size() * 2];
		int k = 0;
		for (Boss b : BOSSES) {
			if (b.out) continue;
			Entity e = b.entity == null ? null : level.getEntity(b.entity);
			marks[k * 2] = (float) (e != null ? e.getX() : b.x);
			marks[k * 2 + 1] = (float) (e != null ? e.getZ() : b.z);
			k++;
		}
		float[] send = java.util.Arrays.copyOf(marks, k * 2);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) Net.bossMarks(p, send);
	}

	/** A boss went down: its loot on the ground, gold for whoever got it, everyone told. */
	public static void died(BotEntity bot, Entity killer) {
		Boss b = null;
		for (Boss x : BOSSES) if (bot.getUUID().equals(x.entity)) b = x;
		if (b == null) return;
		b.out = true;
		b.entity = null;
		ServerLevel level = (ServerLevel) bot.level();
		double x = bot.getX(), y = bot.getY() + 0.6, z = bot.getZ();
		BossDef def = b.def;
		// its own gun (Boss rarity), plenty of ammo for it, then its loot
		ItemStack gun = Guns.make(def.weapon(), def.rarity());
		gun.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
			Component.literal(def.name() + "'s " + Guns.rarity(gun).name() + " " + Sheets.WEAPONS_BY_ID.get(def.weapon()).name()).withStyle(ChatFormatting.GOLD));
		Containers.dropItemStack(level, x, y, z, gun);
		String ammo = Sheets.WEAPONS_BY_ID.get(def.weapon()).ammo();
		Containers.dropItemStack(level, x, y, z, ModItems.ammo(ammo, Sheets.AMMO_BY_ID.get(ammo).botDrop() * 2));
		for (String part : def.loot().split("[+]")) {
			String[] kv = part.trim().split(":");
			var item = ModItems.CONSUMABLES.get(kv[0]);
			if (item == null) continue;
			Containers.dropItemStack(level, x, y, z, new ItemStack(item, kv.length > 1 ? Integer.parseInt(kv[1].trim()) : 1));
		}
		MinecraftServer server = level.getServer();
		String by = killer == null ? "the storm" : killer.getName().getString();
		Component msg = Component.literal("BOSS DOWN: ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal(def.name() + " was eliminated by " + by).withStyle(ChatFormatting.YELLOW));
		for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(msg);
		Net.sfxAll(server, "oof", bot.position());
		if (killer instanceof ServerPlayer p) Gold.award(p, "goldBoss", "boss eliminated");
		MinebloxRoyale.LOG.info("BR: boss {} eliminated by {}", def.name(), by);
	}

	/** The bosses' names, for the HUD's (and tests') info. */
	public static List<String> names() {
		return BOSSES.stream().filter(b -> !b.out).map(b -> b.def.name()).toList();
	}
}
