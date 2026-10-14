package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.net.Net;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Reboot cards and reboot vans (creator: yes, vans in the Battle Bus's art style). In Duos and up, a fighter eliminated
 * while a teammate still stands drops a reboot card. A teammate picks it up (even with a full inventory: it goes in
 * the offhand, seen in the inventory screen, never drawn on the player) within rebootCardSeconds and holds the interact
 * key (F) at a reboot van for rebootSeconds: everyone whose card they carry drops back in from above the van with the
 * glider, 100 health and nothing but the pickaxe. The van then recharges for rebootVanCooldownSeconds. Bots carry and
 * use their team's cards too.
 */
public final class Reboot {
	/**
	 * One card's owner: a player (their account id) or a bot ("bot:" + name), their team, and when the card runs out.
	 */
	public record Owner(String key, String name, int team, long until) {
		CompoundTag save() {
			CompoundTag t = new CompoundTag();
			t.putString("key", key);
			t.putString("name", name);
			t.putInt("team", team);
			t.putLong("until", until);
			return t;
		}

		static Owner load(CompoundTag t) {
			return new Owner(t.getStringOr("key", ""), t.getStringOr("name", "?"), t.getIntOr("team", -1), t.getLongOr("until", 0L));
		}

		public boolean bot() {
			return key.startsWith("bot:");
		}
	}

	/** A reboot van on the island: where it stands, which way it faces, and when it can be used again. */
	public static final class Van {
		public final double x, z;
		public double y;
		public final float yaw;
		public long readyAt;
		boolean placed;

		Van(double x, double z, float yaw) {
			this.x = x;
			this.z = z;
			this.yaw = yaw;
		}

		public boolean placed() {
			return placed;
		}
	}

	private static final List<Van> VANS = new ArrayList<>();
	/** Cards lying on the ground, by team: {x, z, seconds left}; worked out once a second for the map. */
	private static final Map<Integer, List<float[]>> GROUND = new HashMap<>();
	/** Seconds left on each owner's card, wherever it is (ground or carried), worked out once a second. */
	private static final Map<String, Float> LEFT = new HashMap<>();

	private Reboot() {
	}

	// ---- vans ----

	/** A new island: van spots spread inside the first circle (team modes only). Built as their chunks load. */
	static void start(double cx, double cz, float startR, RandomSource rnd) {
		VANS.clear();
		GROUND.clear();
		if (!Teams.teamMode()) return;
		int n = Math.round(MinebloxRoyale.setting("rebootVans"));
		for (int i = 0; i < n; i++) {
			// Spread round the island (one per slice), not bunched up.
			double a = (i + rnd.nextDouble() * 0.7) / n * Math.PI * 2, d = (0.25 + rnd.nextDouble() * 0.55) * startR;
			VANS.add(new Van(Math.floor(cx + Math.cos(a) * d) + 0.5, Math.floor(cz + Math.sin(a) * d) + 0.5, rnd.nextInt(4) * 90F));
		}
	}

	public static List<Van> vans() {
		return VANS;
	}

	/**
	 * Vans whose chunks have loaded go on the ground: invisible barrier blocks give the van its body (the island puts
	 * the spot back after the match); the van itself is drawn by each player's game.
	 */
	static void prepare(ServerLevel level) {
		for (Van v : VANS) {
			if (v.placed) continue;
			int bx = (int) Math.floor(v.x), bz = (int) Math.floor(v.z);
			if (!level.hasChunk((bx - 3) >> 4, (bz - 3) >> 4) || !level.hasChunk((bx + 3) >> 4, (bz + 3) >> 4)
				|| !level.hasChunk((bx - 3) >> 4, (bz + 3) >> 4) || !level.hasChunk((bx + 3) >> 4, (bz - 3) >> 4)) continue;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
			v.y = y;
			v.placed = true;
			boolean alongX = Math.round(v.yaw / 90F) % 2 == 1;
			for (int a = -2; a <= 2; a++)
				for (int b = -1; b <= 1; b++)
					for (int h = 0; h < 3; h++) {
						BlockPos p = new BlockPos(bx + (alongX ? a : b), y + h, bz + (alongX ? b : a));
						if (!level.getBlockState(p).isAir()) continue;
						IslandRestore.remember(level, p);
						level.setBlock(p, Blocks.BARRIER.defaultBlockState(), 2);
					}
			MinebloxRoyale.LOG.info("BR: reboot van at {} {} {}", bx, y, bz);
		}
	}

	/** The van this fighter stands at (within rebootRange of its middle), or null. */
	public static Van vanAt(Entity e) {
		double r = MinebloxRoyale.setting("rebootRange");
		for (Van v : VANS) {
			if (!v.placed) continue;
			double dx = e.getX() - v.x, dz = e.getZ() - v.z;
			if (dx * dx + dz * dz <= r * r && Math.abs(e.getY() - v.y) < 4) return v;
		}
		return null;
	}

	public static boolean ready(Van v, long now) {
		return now >= v.readyAt;
	}

	/** The nearest van that's ready, or null (bots carrying cards head there). */
	public static Van nearestReady(Entity e, double within) {
		long now = e.level().getGameTime();
		Van best = null;
		double bestD = within * within;
		for (Van v : VANS) {
			if (!v.placed || !ready(v, now)) continue;
			double d = (e.getX() - v.x) * (e.getX() - v.x) + (e.getZ() - v.z) * (e.getZ() - v.z);
			if (d < bestD) {
				best = v;
				bestD = d;
			}
		}
		return best;
	}

	// ---- cards ----

	public static boolean isCard(ItemStack s) {
		return !s.isEmpty() && s.is(ModItems.REBOOT_CARD);
	}

	public static List<Owner> owners(ItemStack s) {
		List<Owner> out = new ArrayList<>();
		if (!isCard(s)) return out;
		CustomData data = s.get(DataComponents.CUSTOM_DATA);
		if (data == null) return out;
		ListTag list = data.copyTag().getListOrEmpty("owners");
		for (int i = 0; i < list.size(); i++) out.add(Owner.load(list.getCompoundOrEmpty(i)));
		return out;
	}

	/** A card stack for these owners (one card each: the count shows how many). */
	static ItemStack card(List<Owner> owners) {
		if (owners.isEmpty()) return ItemStack.EMPTY;
		ItemStack s = new ItemStack(ModItems.REBOOT_CARD, Math.min(4, owners.size()));
		ListTag list = new ListTag();
		for (Owner o : owners) list.add(o.save());
		CompoundTag tag = new CompoundTag();
		tag.put("owners", list);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, Component.literal((owners.size() == 1 ? "Reboot card: " : "Reboot cards: ") + String.join(", ", owners.stream().map(Owner::name).toList()))
			.withStyle(Style.EMPTY.withColor(0x5CE1FF).withItalic(false)));
		return s;
	}

	/** Eliminated in a team match with a teammate still standing: their card falls where they went down. */
	static void dropCardFor(LivingEntity victim, String key) {
		MatchManager mm = MatchManager.get();
		if (!Teams.teamMode() || !mm.hasStandingTeammate(victim)) return;
		long until = victim.level().getGameTime() + Math.round(MinebloxRoyale.setting("rebootCardSeconds") * 20);
		spawn(victim.level(), victim.getX(), victim.getY() + 0.5, victim.getZ(), List.of(new Owner(key, victim.getName().getString(), Teams.teamOf(victim), until)));
	}

	private static void spawn(Level level, double x, double y, double z, List<Owner> owners) {
		ItemStack s = card(owners);
		if (s.isEmpty()) return;
		ItemEntity item = new ItemEntity(level, x, y, z, s);
		item.setPickUpDelay(20);
		item.setUnlimitedLifetime(); // it runs out when the card does
		level.addFreshEntity(item);
	}

	/** Cards someone carries (a player's offhand, a bot's pocket). */
	public static List<Owner> carried(LivingEntity e) {
		if (e instanceof BotEntity bot) return bot.cards;
		return owners(e.getItemBySlot(EquipmentSlot.OFFHAND));
	}

	private static void setCarried(LivingEntity e, List<Owner> owners) {
		if (e instanceof BotEntity bot) {
			bot.cards.clear();
			bot.cards.addAll(owners);
		} else {
			e.setItemSlot(EquipmentSlot.OFFHAND, card(owners));
		}
	}

	/** A card stack that ended up in the wrong slot (moved in the inventory screen): back into the offhand, merged. */
	static void toOffhand(ServerPlayer p, ItemStack stack) {
		List<Owner> have = new ArrayList<>(carried(p));
		for (Owner o : owners(stack)) if (have.stream().noneMatch(h -> h.key().equals(o.key()))) have.add(o);
		ItemStack off = p.getOffhandItem();
		if (!off.isEmpty() && !isCard(off)) {
			p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			Loadout.place(p, off);
		}
		setCarried(p, have);
	}

	/** The carrier was eliminated: the cards they held fall to the ground, still running. */
	public static void dropCarried(LivingEntity e) {
		List<Owner> owners = new ArrayList<>(carried(e));
		if (owners.isEmpty()) return;
		setCarried(e, List.of());
		spawn(e.level(), e.getX(), e.getY() + 0.5, e.getZ(), owners);
	}

	/**
	 * Touching a card on the ground (ItemEntityMixin for players, bots walking over it): only that card's team picks it
	 * up, and only a standing fighter. Goes in the offhand whatever else is carried. True = picked up.
	 */
	public static boolean tryPickUp(LivingEntity who, ItemEntity item) {
		List<Owner> owners = owners(item.getItem());
		if (owners.isEmpty() || item.hasPickUpDelay() || Knocked.is(who) || !MatchManager.get().inMatchAndAlive(who)) return false;
		int team = Teams.teamOf(who);
		if (!Teams.teamMode() || team < 0 || owners.stream().anyMatch(o -> o.team() != team)) return false;
		List<Owner> have = new ArrayList<>(carried(who));
		for (Owner o : owners) if (have.stream().noneMatch(h -> h.key().equals(o.key()))) have.add(o);
		if (!(who instanceof BotEntity)) {
			ItemStack off = who.getItemBySlot(EquipmentSlot.OFFHAND);
			if (!off.isEmpty() && !isCard(off) && who instanceof ServerPlayer sp) {
				// Something else in the offhand (it shouldn't be): it goes back into the inventory to make room.
				who.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
				Loadout.place(sp, off);
			}
		}
		setCarried(who, have);
		item.discard();
		if (who instanceof ServerPlayer sp) {
			Net.sfxTo(sp, "card_pickup", sp.position());
			sp.sendSystemMessage(Component.literal("Picked up " + String.join(", ", owners.stream().map(Owner::name).toList())
				+ "'s reboot card - hold F at a reboot van to bring them back.").withStyle(ChatFormatting.AQUA), true);
		}
		return true;
	}

	/**
	 * Once a second: cards run out (and anyone who went back to the hub or left can't come back), and the map list of
	 * cards lying on the ground is updated.
	 */
	static void secondTick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		MatchManager mm = MatchManager.get();
		GROUND.clear();
		LEFT.clear();
		for (ItemEntity item : level.getEntities(EntityTypes.ITEM, i -> isCard(i.getItem()))) {
			List<Owner> before = owners(item.getItem()), after = valid(server, before, now);
			if (after.isEmpty()) {
				item.discard();
				continue;
			}
			if (after.size() != before.size()) item.setItem(card(after));
			for (Owner o : after) {
				GROUND.computeIfAbsent(o.team(), k -> new ArrayList<>()).add(new float[] { (float) item.getX(), (float) item.getZ(), (o.until() - now) / 20F });
				LEFT.put(o.key(), (o.until() - now) / 20F);
			}
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			List<Owner> before = owners(p.getOffhandItem());
			if (before.isEmpty()) continue;
			List<Owner> after = mm.inMatchAndAlive(p) ? valid(server, before, now) : List.of();
			if (after.size() != before.size()) setCarried(p, after);
			for (Owner o : after) LEFT.put(o.key(), (o.until() - now) / 20F);
		}
		for (BotEntity b : level.getEntities(BotEntity.TYPE, b -> !b.cards.isEmpty())) {
			List<Owner> after = valid(server, b.cards, now);
			if (after.size() != b.cards.size()) setCarried(b, after);
			for (Owner o : after) LEFT.put(o.key(), (o.until() - now) / 20F);
		}
	}

	/** Owners whose card still counts: not run out, and (players) still waiting in the match to come back. */
	private static List<Owner> valid(MinecraftServer server, List<Owner> owners, long now) {
		List<Owner> out = new ArrayList<>();
		for (Owner o : owners) {
			boolean ok = o.until() > now && MatchManager.get().canComeBack(o);
			if (ok) out.add(o);
			else if (o.until() <= now && !o.bot()) {
				ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(o.key()));
				if (p != null) p.sendSystemMessage(Component.literal("Your reboot card ran out - you're out for this match. Press H for the hub.").withStyle(ChatFormatting.GRAY));
			}
		}
		return out;
	}

	/** Seconds left on this owner's card (null: no card any more). */
	public static Float cardLeft(String key) {
		return LEFT.get(key);
	}

	/** Cards of this team on the ground: {x, z, seconds left} each. */
	public static List<float[]> groundCards(int team) {
		return GROUND.getOrDefault(team, List.of());
	}

	/** Holding F at a ready van with cards: everyone on them comes back. True if anyone did. */
	static boolean reboot(LivingEntity carrier, Van van) {
		MinecraftServer server = carrier.level().getServer();
		long now = carrier.level().getGameTime();
		if (!ready(van, now)) return false;
		List<Owner> owners = valid(server, carried(carrier), now);
		setCarried(carrier, List.of());
		List<String> back = new ArrayList<>();
		for (Owner o : owners) if (MatchManager.get().rebootOwner(server, o, van)) back.add(o.name());
		if (back.isEmpty()) return false;
		van.readyAt = now + Math.round(MinebloxRoyale.setting("rebootVanCooldownSeconds") * 20);
		MatchManager.broadcast(server, Component.empty().append(MatchManager.who(carrier)).append(" rebooted " + String.join(", ", back)).withStyle(ChatFormatting.AQUA));
		for (ServerPlayer p : server.getPlayerList().getPlayers())
			if (Teams.teamOf(p) == Teams.teamOf(carrier)) Net.sfxTo(p, "rebooted", p.position()); // the carrier and their team
		MinebloxRoyale.LOG.info("BR: {} rebooted {} at the van {} {}", carrier.getName().getString(), back, (int) van.x, (int) van.z);
		return true;
	}

	/** Can this fighter reboot right now (standing at a ready van with a card)? */
	static boolean canReboot(LivingEntity e) {
		Van v = vanAt(e);
		return v != null && ready(v, e.level().getGameTime()) && !carried(e).isEmpty();
	}

	/** The player went back to the hub or left: their card is void, wherever it is. */
	static void forfeit(MinecraftServer server, String key) {
		ServerLevel level = server.overworld();
		for (ItemEntity item : level.getEntities(EntityTypes.ITEM, i -> isCard(i.getItem()))) {
			List<Owner> owners = new ArrayList<>(owners(item.getItem()));
			if (owners.removeIf(o -> o.key().equals(key))) {
				if (owners.isEmpty()) item.discard();
				else item.setItem(card(owners));
			}
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			List<Owner> owners = new ArrayList<>(owners(p.getOffhandItem()));
			if (owners.removeIf(o -> o.key().equals(key))) setCarried(p, owners);
		}
		for (BotEntity b : level.getEntities(BotEntity.TYPE, b -> !b.cards.isEmpty())) b.cards.removeIf(o -> o.key().equals(key));
	}

	/** The match is over: every card goes, and the vans (their barrier blocks are put back with the island). */
	static void clearAll(ServerLevel level) {
		for (ItemEntity item : level.getEntities(EntityTypes.ITEM, i -> isCard(i.getItem()))) item.discard();
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) if (isCard(p.getOffhandItem())) p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		VANS.clear();
		GROUND.clear();
		LEFT.clear();
	}
}
