package gg.minebloxroyale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.match.CustomStructures;
import gg.minebloxroyale.match.LootChestBlock;
import gg.minebloxroyale.match.MatchManager;
import gg.minebloxroyale.match.Reboot;
import gg.minebloxroyale.sheets.ModeDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Testing helpers (creator: "biggest annoyance is play tests"), for the development account only (MinebloxDev in the
 * development run; every other player's commands are off, CommandsLockMixin) and the server console:
 * <ul>
 * <li>/br start [solo|duos|trios|squads] [bots]: a match now, without standing on a portal, with that many bots;</li>
 * <li>/br give &lt;item&gt; [rarity or count]: any gun (with a stack of its ammo) at any rarity, or any other item;</li>
 * <li>/br tp &lt;parking_lot|container_yard|shack|watchtower|supply_depot|ruins|chest|van|bot&gt;: to the nearest one;</li>
 * <li>/br storm: the storm skips ahead one step;</li>
 * <li>/br hub, /br status.</li>
 * </ul>
 */
public final class DevCommands {
	private DevCommands() {
	}

	/** Whether a command (without its "/") is one of these, which the development account may run. */
	public static boolean isDevCommand(String command) {
		String c = command.trim().toLowerCase(Locale.ROOT);
		return c.equals("br") || c.startsWith("br ");
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("br")
			.requires(src -> src.getPlayer() == null || MinebloxRoyale.devAccount(src.getPlayer()))
			.then(Commands.literal("start")
				.executes(c -> start(c, null, -1))
				.then(Commands.argument("mode", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Sheets.MODES.stream().map(ModeDef::id), b))
					.executes(c -> start(c, StringArgumentType.getString(c, "mode"), -1))
					.then(Commands.argument("bots", IntegerArgumentType.integer(0, 99))
						.executes(c -> start(c, StringArgumentType.getString(c, "mode"), IntegerArgumentType.getInteger(c, "bots"))))))
			.then(Commands.literal("give")
				.then(Commands.argument("item", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(itemNames(), b))
					.executes(c -> give(c, StringArgumentType.getString(c, "item"), ""))
					.then(Commands.argument("rarity_or_count", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(Sheets.RARITIES.stream().map(r -> r.id()), b))
						.executes(c -> give(c, StringArgumentType.getString(c, "item"), StringArgumentType.getString(c, "rarity_or_count"))))))
			.then(Commands.literal("tp")
				.then(Commands.argument("what", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(tpNames(), b))
					.executes(c -> tp(c, StringArgumentType.getString(c, "what")))))
			.then(Commands.literal("storm").executes(c -> reply(c, MatchManager.get().skipStorm())))
			.then(Commands.literal("hub").executes(c -> {
				if (c.getSource().getPlayer() != null) MatchManager.get().sendToHub(c.getSource().getPlayer());
				return 1;
			}))
			.then(Commands.literal("status").executes(c -> reply(c, MatchManager.get().describe()))));
	}

	private static int reply(CommandContext<CommandSourceStack> c, String text) {
		c.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> c, String text) {
		c.getSource().sendFailure(Component.literal(text));
		return 0;
	}

	// ---- /br start ----

	private static int start(CommandContext<CommandSourceStack> c, String modeId, int bots) {
		ModeDef mode = modeId == null ? Sheets.MODES.get(0) : Sheets.MODES_BY_ID.get(modeId.toLowerCase(Locale.ROOT));
		if (mode == null) return fail(c, "No mode \"" + modeId + "\" (solo, duos, trios or squads).");
		if (!MatchManager.get().devStart(c.getSource().getServer(), mode, bots)) return fail(c, "Can't start now: a match is on, or the next island isn't ready yet.");
		return reply(c, "Starting " + mode.name() + (bots >= 0 ? " with " + bots + " bots" : "") + ".");
	}

	// ---- /br give ----

	/** Guns by weapon id, heals and shields by id, ammo_<type>, the materials (wood, brick, metal) and reboot_card. */
	private static List<String> itemNames() {
		List<String> out = new ArrayList<>(ModItems.WEAPONS.keySet());
		out.addAll(ModItems.CONSUMABLES.keySet());
		for (String a : ModItems.AMMO.keySet()) out.add("ammo_" + a);
		out.addAll(ModItems.MATERIALS.keySet());
		out.add("reboot_card");
		return out;
	}

	private static int give(CommandContext<CommandSourceStack> c, String name, String extra) {
		ServerPlayer p = c.getSource().getPlayer();
		if (p == null) return fail(c, "Only a player can be given items.");
		name = name.toLowerCase(Locale.ROOT);
		int count = extra.matches("\\d{1,3}") ? Integer.parseInt(extra) : -1;
		List<ItemStack> stacks = new ArrayList<>();
		if (ModItems.WEAPONS.containsKey(name)) {
			String rarity = extra.isEmpty() ? "common" : extra.toLowerCase(Locale.ROOT);
			if (!Sheets.RARITIES_BY_ID.containsKey(rarity)) return fail(c, "No rarity \"" + extra + "\".");
			stacks.add(Guns.make(name, rarity));
			stacks.add(ModItems.ammo(Sheets.WEAPONS_BY_ID.get(name).ammo(), 60));
		} else if (ModItems.CONSUMABLES.containsKey(name)) {
			stacks.add(new ItemStack(ModItems.CONSUMABLES.get(name), count > 0 ? count : 1));
		} else if (name.startsWith("ammo_") && ModItems.AMMO.containsKey(name.substring(5))) {
			stacks.add(ModItems.ammo(name.substring(5), count > 0 ? count : 60));
		} else if (ModItems.MATERIALS.containsKey(name)) {
			stacks.add(new ItemStack(ModItems.MATERIALS.get(name), count > 0 ? count : 99));
		} else if (name.equals("reboot_card")) {
			stacks.add(new ItemStack(ModItems.REBOOT_CARD));
		} else {
			return fail(c, "No item \"" + name + "\".");
		}
		for (ItemStack s : stacks) p.getInventory().placeItemBackInInventory(s, Prediction.SERVER_ONLY); // (dropped at their feet if full)
		return reply(c, "Given: " + name + (extra.isEmpty() ? "" : " " + extra) + ".");
	}

	// ---- /br tp ----

	private static List<String> tpNames() {
		List<String> out = new ArrayList<>(CustomStructures.TP_NAMES);
		out.addAll(List.of("chest", "van", "bot"));
		return out;
	}

	private static int tp(CommandContext<CommandSourceStack> c, String what) {
		ServerPlayer p = c.getSource().getPlayer();
		if (p == null) return fail(c, "Only a player can be teleported.");
		ServerLevel level = p.level();
		what = what.toLowerCase(Locale.ROOT);
		Vec3 to = null;
		int kind = CustomStructures.TP_NAMES.indexOf(what);
		if (kind >= 0) {
			BlockPos b = CustomStructures.nearest(kind, p.getX(), p.getZ());
			if (b != null) to = Vec3.atBottomCenterOf(b).add(0, 1, 0);
			else return fail(c, "No " + what.replace('_', ' ') + " built yet on this island (buildings appear as their area loads: fly round in creative).");
		} else if (what.equals("chest")) {
			BlockPos best = null;
			for (BlockPos b : MatchManager.get().placedChests()) {
				BlockState s = level.getBlockState(b);
				if (!(s.getBlock() instanceof LootChestBlock) || s.getValue(LootChestBlock.OPEN)) continue;
				if (best == null || b.distToCenterSqr(p.position()) < best.distToCenterSqr(p.position())) best = b;
			}
			if (best == null) return fail(c, "No closed chest placed yet (chests appear as their area loads).");
			to = Vec3.atBottomCenterOf(best).add(0, 1, 0);
		} else if (what.equals("van")) {
			Reboot.Van best = null;
			for (Reboot.Van v : Reboot.vans())
				if (v.placed() && (best == null || p.distanceToSqr(v.x, p.getY(), v.z) < p.distanceToSqr(best.x, p.getY(), best.z))) best = v;
			if (best == null) return fail(c, "No reboot van placed yet (team modes only; vans appear as their area loads).");
			to = new Vec3(best.x + 3, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(best.x + 3), (int) Math.floor(best.z)), best.z);
		} else if (what.equals("bot")) {
			BotEntity best = null;
			for (BotEntity b : level.getEntities(BotEntity.TYPE, e -> e.isAlive()))
				if (best == null || p.distanceToSqr(b) < p.distanceToSqr(best)) best = b;
			if (best == null) return fail(c, "No bot near any player right now (far-away bots are simulated).");
			to = best.position().add(best.getLookAngle().scale(-3)).add(0, 1, 0);
		} else {
			return fail(c, "Can't teleport to \"" + what + "\".");
		}
		p.teleportTo(to.x, to.y, to.z);
		return reply(c, String.format(Locale.ROOT, "Teleported to the nearest %s at %d %d %d.", what.replace('_', ' '), (int) Math.floor(to.x), (int) Math.floor(to.y), (int) Math.floor(to.z)));
	}
}
