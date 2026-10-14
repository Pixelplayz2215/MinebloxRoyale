package gg.minebloxroyale;

import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.hub.HubBuilder;
import gg.minebloxroyale.hub.HubFun;
import gg.minebloxroyale.hub.Obby;
import gg.minebloxroyale.match.BattleBusEntity;
import gg.minebloxroyale.match.Builds;
import gg.minebloxroyale.match.Combat;
import gg.minebloxroyale.match.Harvest;
import gg.minebloxroyale.match.IslandPregen;
import gg.minebloxroyale.match.IslandRestore;
import gg.minebloxroyale.match.Knocked;
import gg.minebloxroyale.match.Loadout;
import gg.minebloxroyale.match.Loot;
import gg.minebloxroyale.match.LootChests;
import gg.minebloxroyale.match.MatchManager;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.Sheets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mineblox Royale: a Minecraft battle royale with a Roblox-style hub, Roblox avatars/sounds and Fortnite's rules. */
public final class MinebloxRoyale implements ModInitializer {
	public static final String MOD_ID = "minebloxroyale";
	public static final Logger LOG = LoggerFactory.getLogger("MinebloxRoyale");
	private static long stoppingAt;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/**
	 * The development account (MinebloxDev, only in the development run): every unlock, and its own game-mode commands.
	 */
	public static boolean devAccount(ServerPlayer p) {
		return FabricLoader.getInstance().isDevelopmentEnvironment() && p.getGameProfile().name().equals("MinebloxDev");
	}

	/** This build's version, as in fabric.mod.json ("0.1.0-test12"). */
	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID).map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
	}

	/**
	 * The version as players see it (the main board in the hub): a release is "v" and its number; a test build of one
	 * is "v1.0.1 - TEST 1", and the test builds before 1.0.0 were "PRE-ALPHA - TEST 12" (creator: "you can name the
	 * version since this is still in dev testing").
	 */
	public static String versionName() {
		String v = version();
		int test = v.indexOf("-test");
		if (test < 0) return "v" + v;
		return (v.startsWith("0.") ? "PRE-ALPHA" : "v" + v.substring(0, test)) + " - TEST " + v.substring(test + 5);
	}

	/** A number from the match sheet. */
	public static float setting(String key) {
		return Sheets.MATCH_BY_ID.get(key).value();
	}

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModItems.init();
		IslandPregen.init(); // its chunk ticket type (registries close after start-up)
		BotEntity.init();
		BattleBusEntity.init();
		Net.init();
		Combat.init();
		Loot.initStructureLoot();
		Loot.initContainers();
		LootChests.init(); // village and other structure chests become loot chests
		Harvest.init();
		// Knocked fighters can't open chests or use blocks.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> Knocked.is(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		// A world from an older build (Block Royale): its files take the new names before anything reads them.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> Legacy.migrateWorld(server.getWorldPath(LevelResource.ROOT)));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			HubBuilder.ensureBuilt(server);
			MatchManager.get().onServerStarted(server);
			IslandPregen.start(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			stoppingAt = System.currentTimeMillis();
			IslandPregen.stop(server); // first: let go of the island chunks being made, so saving doesn't wait for them
			Builds.clearAll(server.overworld());
			IslandRestore.restoreAll(server.overworld());
			MatchManager.get().onServerStopping(server);
		});
		// How long closing the world took (leaving it with /rc waits for this).
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOG.info("BR: world saved and closed in {} ms", System.currentTimeMillis() - stoppingAt));
		// No vanilla item ever drops (blocks, mobs, players, chests): any vanilla item entity is removed as it appears.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && Loadout.isVanilla(item.getItem())) entity.discard();
			if (HubBuilder.isStaleText(entity)) entity.discard(); // hub text from an older hub build
			// No mobs at all (creator): animals, bees, wolves, villagers, golems, monsters... are removed as they load
			// (world generation still makes some). Only Mineblox Royale's own bots stay.
			if (entity instanceof Mob && !(entity instanceof BotEntity)) entity.discard();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MatchManager.get().tick(server);
			// Always midday (creator): if anything moves the clock, it's put back.
			if (server.getTickCount() % 600 == 0)
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
			Combat.tick(server);
			Obby.tick(server);
			HubFun.tick(server);
			LootChests.tick(server);
			if (server.getTickCount() % 10 == 0) for (var p : server.getPlayerList().getPlayers()) Loadout.stripVanilla(p);
			// Difficulty is always Normal and locked (the Options button is greyed out); snaps back if forced by
			// command.
			if (server.getWorldData().getDifficulty() != Difficulty.NORMAL || !server.getWorldData().isDifficultyLocked()) {
				server.setDifficulty(Difficulty.NORMAL, true);
				server.setDifficultyLocked(true);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> MatchManager.get().onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MatchManager.get().onLeave(handler.player));
		// Testing helpers for the development account and the server console (/br start, give, tp, storm, hub, status).
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DevCommands.register(dispatcher));
		LOG.info("Mineblox Royale ready: {} weapons, {} consumables, {} storm phases, {} bots",
			Sheets.WEAPONS.size(), Sheets.CONSUMABLES.size(), Sheets.STORM.size(), Sheets.BOTS.size());
	}
}
