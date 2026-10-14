package gg.minebloxroyale.hub;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.match.MatchManager;
import gg.minebloxroyale.sheets.HubPieceDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Things to do in the hub while the next island is made (creator: more fun): a shooting range with pop-up targets and
 * a practice rifle (timed rounds: your best round score and your bullseyes unlock emotes and gliders, RangeStats), and
 * the Sky Tower (a second obby, spiralling up), each in a neat purple-bordered zone (creator: clean and tidy). Built
 * from the hub sheet (kinds shooting_range, tower_obby); this class builds them and runs them every tick. It also keeps
 * each player in the hub empty-handed. Only players in the hub count, never those in a match.
 */
public final class HubFun {
	/** The hub's theme (creator: clean and tidy): purple and blues. */
	private static final String[] THEME = { "minecraft:purple_concrete", "minecraft:blue_concrete", "minecraft:light_blue_concrete" };

	private HubFun() {
	}

	/** A block by id (26.3 keeps coloured blocks in colour collections, not as named fields). */
	private static Block b(String id) {
		return HubBuilder.block(id);
	}

	private static void set(ServerLevel level, BlockPos p, BlockState s) {
		level.setBlock(p, s, 2);
	}

	// ---- building ----

	/** A neat purple border (one block wide) round a zone on the floor: x0..x1, z0..z1 inclusive. */
	private static void border(ServerLevel level, int hubY, int x0, int z0, int x1, int z1) {
		BlockState purple = b("minecraft:purple_concrete").defaultBlockState();
		for (int x = x0; x <= x1; x++) {
			set(level, new BlockPos(x, hubY, z0), purple);
			set(level, new BlockPos(x, hubY, z1), purple);
		}
		for (int z = z0; z <= z1; z++) {
			set(level, new BlockPos(x0, hubY, z), purple);
			set(level, new BlockPos(x1, hubY, z), purple);
		}
	}

	/** One small sign for an area: a single line, low, readable from anywhere (it turns to face you). */
	private static void sign(ServerLevel level, double x, double y, double z, String text, String colour) {
		HubBuilder.label(level.getServer(), x, y, z, text, colour, 1.1F, "center", "north", true);
	}

	/**
	 * The range laid out from its hub-sheet row, facing the way you shoot: u runs along the back wall (-hw to hw), v from
	 * the firing line's side (-hl) to the back wall (hl).
	 */
	private record Range(int cx, int cz, int hw, int hl, int fx, int fz) {
		static Range of(HubPieceDef p) {
			Direction f = Direction.byName(p.facing());
			if (f == null || f.getAxis().isVertical()) f = Direction.SOUTH;
			return new Range(Math.round(p.x()), Math.round(p.z()), Math.round(p.width() / 2F), Math.round(p.height() / 2F), f.getStepX(), f.getStepZ());
		}

		BlockPos at(int u, int v, int y) {
			return new BlockPos(cx + fz * u + fx * v, y, cz - fx * u + fz * v);
		}

		/** A point's {u, v}, measured from the middle of the centre block. */
		double[] local(double x, double z) {
			double dx = x - (cx + 0.5), dz = z - (cz + 0.5);
			return new double[] { dx * fz - dz * fx, dx * fx + dz * fz };
		}
	}

	/**
	 * The shooting range: a light-blue firing line on the near side, a black back wall with target slots on the far side
	 * (facing = the direction you shoot), its border and its sign. Where the hub's floor doesn't reach (creator: the
	 * range on its own deck off the hub's west edge), it lays its own, like the hub's: 4 blocks thick, with grid lines.
	 */
	static void buildRange(ServerLevel level, HubPieceDef p, int hubY) {
		Range r = Range.of(p);
		BlockState floor = b(HubBuilder.piece("baseplate").block()).defaultBlockState(), grid = b("minecraft:gray_concrete").defaultBlockState();
		for (int u = -r.hw() - 1; u <= r.hw() + 1; u++)
			for (int v = -r.hl() - 1; v <= r.hl() + 1; v++) {
				BlockPos q = r.at(u, v, hubY);
				if (!level.getBlockState(q).isAir()) continue;
				set(level, q, Math.floorMod(q.getX(), 8) == 0 || Math.floorMod(q.getZ(), 8) == 0 ? grid : floor);
				for (int d = 1; d <= 3; d++) set(level, q.below(d), floor);
			}
		BlockState line = b("minecraft:light_blue_concrete").defaultBlockState(), wall = b("minecraft:black_concrete").defaultBlockState(),
			frame = b("minecraft:purple_concrete").defaultBlockState();
		for (int u = -r.hw() + 1; u <= r.hw() - 1; u++)
			for (int v = -r.hl() + 1; v <= -r.hl() + 2; v++) set(level, r.at(u, v, hubY), line);
		// The back wall: black, framed in purple.
		for (int u = -r.hw(); u <= r.hw(); u++)
			for (int y = 1; y <= 8; y++) set(level, r.at(u, r.hl(), hubY + y), Math.abs(u) == r.hw() || y == 8 ? frame : wall);
		BlockPos c0 = r.at(-r.hw(), -r.hl(), hubY), c1 = r.at(r.hw(), r.hl(), hubY);
		border(level, hubY, Math.min(c0.getX(), c1.getX()), Math.min(c0.getZ(), c1.getZ()), Math.max(c0.getX(), c1.getX()), Math.max(c0.getZ(), c1.getZ()));
		BlockPos s = r.at(0, -r.hl(), hubY);
		sign(level, s.getX() + 0.5, hubY + 1.9, s.getZ() + 0.5, "SHOOTING RANGE", "aqua");
		HubBuilder.label(level.getServer(), s.getX() + 0.5, hubY + 1.45, s.getZ() + 0.5, Math.round(MinebloxRoyale.setting("rangeRoundSeconds"))
			+ " s rounds - your best score and bullseyes unlock emotes & gliders", "white", 0.65F, "center", "north", true);
	}

	/** Target slots on the range's back wall. */
	private static List<BlockPos> targetSlots(HubPieceDef p) {
		Range r = Range.of(p);
		List<BlockPos> out = new ArrayList<>();
		for (int u = -r.hw() + 3; u <= r.hw() - 3; u += 4)
			for (int y : new int[] { 2, 4, 6 }) out.add(r.at(u, r.hl(), HubBuilder.hubY() + y));
		return out;
	}

	/**
	 * The Sky Tower: a white pillar with purple and blue platforms spiralling up round it, one block per step, a golden
	 * platform on top. The last step is one block below the gold (creator: the old last jump was two blocks, too high).
	 * The steps sit outside the gold's 5 x 5, so nobody bumps their head under it.
	 */
	static void buildTower(ServerLevel level, HubPieceDef p, int hubY) {
		int cx = Math.round(p.x()), cz = Math.round(p.z()), top = Math.round(p.height());
		for (int y = 1; y <= top; y++)
			for (int dx = -1; dx <= 1; dx++)
				for (int dz = -1; dz <= 1; dz++) set(level, new BlockPos(cx + dx, hubY + y, cz + dz), Blocks.QUARTZ_BLOCK.defaultBlockState());
		for (int i = 0; i < top; i++) {
			double a = Math.toRadians(i * 40);
			int px = cx + (int) Math.round(Math.cos(a) * 3.6), pz = cz + (int) Math.round(Math.sin(a) * 3.6);
			BlockState c = b(THEME[i % THEME.length]).defaultBlockState();
			for (int dx = 0; dx <= 1; dx++)
				for (int dz = 0; dz <= 1; dz++) set(level, new BlockPos(px + dx - (px < cx ? 1 : 0), hubY + 1 + i, pz + dz - (pz < cz ? 1 : 0)), c);
		}
		for (int dx = -2; dx <= 2; dx++)
			for (int dz = -2; dz <= 2; dz++) set(level, new BlockPos(cx + dx, hubY + top + 1, cz + dz), Blocks.GOLD_BLOCK.defaultBlockState());
		border(level, hubY, cx - 6, cz - 6, cx + 6, cz + 6);
		sign(level, cx + 0.5, hubY + 1.9, cz - 6.5, "SKY TOWER", "light_purple");
	}

	// ---- every tick ----

	private static final Map<BlockPos, Integer> ACTIVE = new HashMap<>(); // target -> ticks left
	/** A player's range round: its score (1 a hit, 2 a bullseye), its bullseyes, and the server tick it ends on. */
	private static final class Round {
		int score, bulls;
		long ends;
	}

	private static final Map<UUID, Round> ROUNDS = new HashMap<>();
	/** Players whose round ran out: no rifle and no new round until they step off the range and back on (creator). */
	private static final Set<UUID> RESTING = new HashSet<>();
	private static final Set<UUID> ON_TOP = new HashSet<>();
	private static int spawnTimer;

	public static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		MatchManager mm = MatchManager.get();
		HubPieceDef range = HubBuilder.piece("shooting_range"), tower = HubBuilder.piece("sky_tower");
		int hubY = HubBuilder.hubY();
		boolean anyoneShooting = false;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!mm.inHub(p) || p.isSpectator()) continue;
			// The shooting range: a practice rifle on the firing line, taken back when you leave the range.
			boolean inRange = false;
			Inventory inv = p.getInventory();
			if (range != null) {
				Range r = Range.of(range);
				double[] at = r.local(p.getX(), p.getZ());
				inRange = Math.abs(at[0]) <= r.hw() + 0.5 && at[1] >= -r.hl() - 2.5 && at[1] <= r.hl() - 0.5;
				boolean onLine = inRange && at[1] <= -r.hl() + 1.5;
				if (!inRange) RESTING.remove(p.getUUID()); // stepped off: the next round can start
				if (onLine && !RESTING.contains(p.getUUID())) {
					anyoneShooting = true;
					if (!(inv.getItem(0).getItem() instanceof WeaponItem)) {
						inv.setItem(0, Guns.make("assault_rifle", "rare"));
						inv.setSelectedSlot(0);
						p.sendSystemMessage(Component.literal("Practice rifle! Hit the targets on the back wall.").withStyle(ChatFormatting.GREEN), true);
					}
					if (inv.getItem(0).getItem() instanceof WeaponItem w && (inv.getItem(9).getCount() < 60 || !inv.getItem(9).is(ModItems.AMMO.get(w.def.ammo()))))
						inv.setItem(9, ModItems.ammo(w.def.ammo(), 99)); // endless ammo on the range
				}
			}
			// Nobody carries anything in the hub (creator): this player's own inventory is emptied, never anyone
			// else's, apart from the practice rifle and its ammo while they're in the range. (Creative is left alone.)
			if (!p.isCreative()) {
				boolean hadRifle = false;
				for (int i = 0; i < inv.getContainerSize(); i++) {
					ItemStack s = inv.getItem(i);
					if (s.isEmpty() || inRange && !RESTING.contains(p.getUUID()) && (i == 0 && s.getItem() instanceof WeaponItem || i == 9 && ModItems.isAmmo(s))) continue;
					hadRifle |= s.getItem() instanceof WeaponItem;
					inv.setItem(i, ItemStack.EMPTY);
				}
				if (!p.containerMenu.getCarried().isEmpty() && !inRange) p.containerMenu.setCarried(ItemStack.EMPTY);
				if (hadRifle && !inRange) endRound(p); // leaving the range ends your round (its score still counts)
			}
			// A round runs out: its score counts (best round, for unlocks); every second the time left shows.
			Round round = ROUNDS.get(p.getUUID());
			long now = server.getTickCount();
			if (round != null && now >= round.ends) {
				// Time's up: the rifle goes, and the next round waits until they've stepped off the range and back on.
				endRound(p);
				RESTING.add(p.getUUID());
				p.sendSystemMessage(Component.literal("Time's up! Step off the range and back on for another round.").withStyle(ChatFormatting.AQUA), true);
			} else if (round != null && (round.ends - now) % 20 == 0)
				p.sendSystemMessage(Component.literal("Range round: " + round.score + " points - " + (round.ends - now) / 20 + " s left").withStyle(ChatFormatting.AQUA), true);
			else if (round == null && inRange && RESTING.contains(p.getUUID()) && now % 40 == 0)
				p.sendSystemMessage(Component.literal("Step off the range and back on for another round").withStyle(ChatFormatting.AQUA), true);
			// The Sky Tower's top: a little celebration, once per climb.
			if (tower != null) {
				int tx = Math.round(tower.x()), tz = Math.round(tower.z()), top = hubY + Math.round(tower.height()) + 2;
				// Standing on the gold itself (the last step is just one block lower, so only the exact height counts).
				boolean atTop = p.onGround() && Math.abs(p.getX() - (tx + 0.5)) < 3 && Math.abs(p.getZ() - (tz + 0.5)) < 3 && Math.abs(p.getY() - top) < 0.5;
				if (atTop && ON_TOP.add(p.getUUID())) {
					p.sendSystemMessage(Component.literal("You climbed the Sky Tower!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
					level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1F, 1F);
					level.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 1.5, p.getZ(), 60, 0.6, 0.6, 0.6, 0.2);
				} else if (!atTop && p.getY() < top - 4) ON_TOP.remove(p.getUUID());
			}
		}
		if (range == null) return;
		// Pop-up targets: up to three at once while someone is on the firing line, each up for 3 seconds.
		for (var it = ACTIVE.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			if (e.getValue() <= 1 || !anyoneShooting) {
				set(level, e.getKey(), b("minecraft:black_concrete").defaultBlockState());
				it.remove();
			} else e.setValue(e.getValue() - 1);
		}
		if (anyoneShooting && --spawnTimer <= 0 && ACTIVE.size() < 3) {
			spawnTimer = 15;
			List<BlockPos> slots = targetSlots(range);
			RandomSource r = level.getRandom();
			BlockPos t = slots.get(r.nextInt(slots.size()));
			if (!ACTIVE.containsKey(t)) {
				ACTIVE.put(t, 60);
				set(level, t, Blocks.TARGET.defaultBlockState());
				level.playSound(null, t, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.7F, 1.4F);
			}
		}
	}

	/**
	 * A bullet hit a block: a pop-up target scores (a bullseye if near its middle: 2 points) and drops away. The first
	 * hit starts a round of rangeRoundSeconds; when it ends, its score is your round score (RangeStats keeps your best,
	 * and every bullseye, for unlocks).
	 */
	public static void onBulletHit(ServerLevel level, ServerPlayer shooter, BlockPos pos, Vec3 at) {
		if (!ACTIVE.containsKey(pos) || RESTING.contains(shooter.getUUID())) return;
		ACTIVE.remove(pos);
		set(level, pos, b("minecraft:black_concrete").defaultBlockState());
		HubPieceDef range = HubBuilder.piece("shooting_range");
		Range shape = Range.of(range);
		// A bullseye is near the target's middle, measured across its face (x for a wall facing north or south, else z).
		double across = shape.fx() == 0 ? at.x - (pos.getX() + 0.5) : at.z - (pos.getZ() + 0.5);
		boolean bull = Math.abs(across) < 0.18 && Math.abs(at.y - (pos.getY() + 0.5)) < 0.18;
		long now = level.getServer().getTickCount();
		Round r = ROUNDS.get(shooter.getUUID());
		if (r == null || now >= r.ends) {
			if (r != null) endRound(shooter);
			r = new Round();
			int secs = Math.round(MinebloxRoyale.setting("rangeRoundSeconds"));
			r.ends = now + secs * 20L;
			ROUNDS.put(shooter.getUUID(), r);
			shooter.sendSystemMessage(Component.literal("Range round started: " + secs + " seconds - hit as many targets as you can (bullseyes score 2)!").withStyle(ChatFormatting.AQUA));
		}
		r.score += bull ? 2 : 1;
		if (bull) {
			r.bulls++;
			RangeStats.bullseye(shooter); // (bullseyes count for unlocks straight away)
		}
		level.playSound(null, pos, bull ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1F, bull ? 1.6F : 1.2F);
		level.sendParticles(bull ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.CRIT, pos.getX() + 0.5 - shape.fx() * 0.6, pos.getY() + 0.5, pos.getZ() + 0.5 - shape.fz() * 0.6, 12, 0.2, 0.2,
			0.1, 0.1);
		shooter.sendSystemMessage(Component.literal((bull ? "BULLSEYE! " : "Hit! ") + "Score " + r.score + " - " + Math.max(0, (r.ends - now) / 20) + " s left")
			.withStyle(bull ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
	}

	/** A player's round is over (time, or they left the range): its score counts towards their best. */
	private static void endRound(ServerPlayer p) {
		Round r = ROUNDS.remove(p.getUUID());
		if (r == null || r.score == 0) return;
		int best = RangeStats.get(p.level().getServer(), p.getUUID())[0];
		p.sendSystemMessage(Component.literal("Range round over: " + r.score + " points (" + r.bulls + " bullseyes)" + (r.score > best ? " - new best!" : " - your best is " + best))
			.withStyle(ChatFormatting.GOLD));
		RangeStats.roundScore(p, r.score);
	}
}
