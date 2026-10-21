package gg.minebloxroyale.client;

import com.mojang.blaze3d.platform.InputConstants;
import gg.minebloxroyale.Legacy;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModBlocks;
import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.client.avatar.Cosmetics;
import gg.minebloxroyale.client.media.FortniteMedia;
import gg.minebloxroyale.client.media.SfxBank;
import gg.minebloxroyale.client.mesh.MeshItemRenderer;
import gg.minebloxroyale.client.mixin.SpecialModelRenderersAccessor;
import gg.minebloxroyale.match.BattleBusEntity;
import gg.minebloxroyale.match.Knocked;
import gg.minebloxroyale.net.Net;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.sdl.SDLVideo;

/** Client side: HUD, Roblox avatars and sounds, hub music and art, keys, auto world start and multiplayer. */
public final class MinebloxRoyaleClient implements ClientModInitializer {
	private static KeyMapping reload, emote, hub, build, locker, interact;
	private static boolean tabWasDown, interactWasDown;
	/** A second copy for multiplayer tests can start in a maximised window instead (MINEBLOXROYALE_WINDOW=windowed). */
	private static final boolean WINDOWED = "windowed".equalsIgnoreCase(System.getenv("MINEBLOXROYALE_WINDOW"));
	private static int maximizeIn = WINDOWED ? 10 : 0;
	/** Ticks until the next note of what the game is doing (LastRun). */
	private static int noteIn;

	/** The revive / reboot key (F unless changed), for the HUD's prompts. */
	static KeyMapping interactKey() {
		return interact;
	}

	@Override
	public void onInitializeClient() {
		// Items drawn from Blender meshes (items/*.json "minebloxroyale:mesh"), registered before the item models load.
		SpecialModelRenderersAccessor.minebloxroyale$types().put(MinebloxRoyale.id("mesh"), MeshItemRenderer.Unbaked.MAP_CODEC);
		// Renamed from Block Royale: the game folder's files, the world and the account record come across first.
		Path game = FabricLoader.getInstance().getGameDir();
		Legacy.migrateFolder(game.resolve(Legacy.OLD_FOLDER), game.resolve(Legacy.NEW_FOLDER), false);
		boolean movedWorld = !Files.exists(game.resolve("saves").resolve(Legacy.NEW_WORLD)) && Files.isDirectory(game.resolve("saves").resolve(Legacy.OLD_WORLD));
		Legacy.migrateFolder(game.resolve("saves").resolve(Legacy.OLD_WORLD), game.resolve("saves").resolve(Legacy.NEW_WORLD), false);
		// Fabric's list of the mod content each world used, with our old names in it (else "Missing content
		// detected!").
		try (var worlds = Files.list(game.resolve("saves"))) {
			worlds.forEach(Legacy::migrateContentList);
		} catch (Exception ignored) { // (no saves folder yet)
		}
		PlayerFiles.migrate();
		if (movedWorld) ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
			// Its name inside the save, too (the world list shows it).
			try (var access = mc.getLevelSource().createAccess(Legacy.NEW_WORLD)) {
				access.renameLevel(Legacy.NEW_WORLD);
			} catch (Exception e) {
				MinebloxRoyale.LOG.warn("BR: couldn't rename the world inside its save", e);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Net.Hud.TYPE, (p, ctx) -> {
			ClientState.stormFrom = ClientState.storm(); // where the storm is drawn right now: glide on from there
			ClientState.hud = p;
			ClientState.hudAt = System.currentTimeMillis();
		});
		ClientPlayNetworking.registerGlobalReceiver(Net.Sfx.TYPE, (p, ctx) -> SfxBank.play(p.soundId(), new Vec3(p.x(), p.y(), p.z())));
		ClientPlayNetworking.registerGlobalReceiver(Net.Emote.TYPE, (p, ctx) -> {
			if (p.emoteId().isEmpty()) ClientState.EMOTES.remove(p.entityId());
			else ClientState.EMOTES.put(p.entityId(), Map.entry(p.emoteId(), System.currentTimeMillis()));
		});
		ClientPlayNetworking.registerGlobalReceiver(Net.LockerOutfit.TYPE, (p, ctx) -> {
			Cosmetics.receive(p);
			var me = ctx.client().player;
			if (me != null && me.getUUID().toString().equals(p.uuid())) MyLocker.onOwnOutfit(p.outfit());
		});
		ClientPlayNetworking.registerGlobalReceiver(Net.DamageNumber.TYPE, (p, ctx) -> {
			DamageNumbers.add(p);
			Aim.hit(p.kind() == 2); // the scope's hit marker (kind 2 = headshot)
		});
		ClientPlayNetworking.registerGlobalReceiver(Net.Squad.TYPE, (p, ctx) -> ClientState.squad = p);
		ClientPlayNetworking.registerGlobalReceiver(Net.Knocks.TYPE, (p, ctx) -> {
			Knocked.CLIENT.clear();
			for (int id : p.ids()) Knocked.CLIENT.add(id);
			ClientState.ACTIONS.clear();
			for (int id : p.reviving()) ClientState.ACTIONS.put(id, 1);
			for (int id : p.rebooting()) ClientState.ACTIONS.put(id, 2);
			for (int id : p.searching()) ClientState.ACTIONS.put(id, 3);
		});
		ClientPlayNetworking.registerGlobalReceiver(Net.PartyInfo.TYPE, (p, ctx) -> {
			ClientState.partyIds = List.copyOf(p.ids());
			ClientState.partyNames = List.copyOf(p.names());
		});
		RoomCodes.init(); // /rc and /roomcode
		PartyCommands.init(); // /party
		ClientPlayNetworking.registerGlobalReceiver(Net.IslandMapImage.TYPE, (p, ctx) -> MapScreen.receive(p));
		ClientPlayNetworking.registerGlobalReceiver(Net.Unlocks.TYPE, (p, ctx) -> {
			// "kills;wins;obby;all;worldId;rangeBest;bullseyes": my stats in this world (unlocks add my other worlds,
			// from MyUnlocks).
			String[] st = p.stats().split(";");
			try {
				ClientState.kills = Integer.parseInt(st[0]);
				ClientState.wins = Integer.parseInt(st[1]);
				ClientState.obby = st[2].equals("1");
				ClientState.all = st.length > 3 && st[3].equals("1");
				ClientState.rangeBest = st.length > 6 ? Integer.parseInt(st[5]) : 0;
				ClientState.bullseyes = st.length > 6 ? Integer.parseInt(st[6]) : 0;
				if (st.length > 4) MyUnlocks.onWorldStats(st[4], ClientState.here());
			} catch (RuntimeException ignored) {
			}
		});

		HudElementRegistry.removeElement(VanillaHudElements.HEALTH_BAR);
		HudElementRegistry.removeElement(VanillaHudElements.FOOD_BAR);
		HudElementRegistry.removeElement(VanillaHudElements.ARMOR_BAR);
		SettingsLock.init();
		HudElementRegistry.removeElement(VanillaHudElements.INFO_BAR); // XP bar: Fortnite has none
		HudElementRegistry.removeElement(VanillaHudElements.EXPERIENCE_LEVEL);
		HudElementRegistry.removeElement(VanillaHudElements.PLAYER_LIST); // Tab is the full map now
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, delta) -> BRHud.crosshair(g));
		// On the Battle Bus our own "PRESS SPACE TO JUMP" replaces Minecraft's "Press Left Shift to dismount".
		HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE, vanilla -> (g, delta) -> {
			var p = Minecraft.getInstance().player;
			if (p == null || !(p.getVehicle() instanceof BattleBusEntity)) vanilla.extractRenderState(g, delta);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, vanilla -> (g, delta) -> BRHud.hotbar(g, () -> vanilla.extractRenderState(g, delta)));
		HudElementRegistry.addLast(MinebloxRoyale.id("hud"), (g, delta) -> BRHud.draw(g));
		LevelRenderEvents.COLLECT_SUBMITS.register(WorldVisuals::submit);
		EntityRenderers.register(BotEntity.TYPE, BotRenderer::new);
		EntityRenderers.register(BattleBusEntity.TYPE, BattleBusRenderer::new);
		BlockEntityRenderers.register(ModBlocks.CAR_ENTITY, context -> new CarRenderer()); // parked cars (smooth models)
		BlockEntityRenderers.register(ModBlocks.LOOT_CHEST_ENTITY, context -> new ChestRenderer()); // loot chests (Blender models, lid opening)

		KeyMapping.Category cat = KeyMapping.Category.register(MinebloxRoyale.id("main"));
		reload = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minebloxroyale.reload", InputConstants.KEY_R, cat));
		emote = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minebloxroyale.emote", InputConstants.KEY_B, cat));
		hub = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minebloxroyale.hub", InputConstants.KEY_H, cat));
		build = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minebloxroyale.build", InputConstants.KEY_G, cat));
		locker = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minebloxroyale.locker", InputConstants.KEY_K, cat));
		// F (held): revive a knocked teammate, or reboot teammates at a van. F no longer swaps hands (nothing to swap
		// in Mineblox Royale; the offhand only holds reboot cards).
		interact = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minebloxroyale.interact", InputConstants.KEY_F, cat));
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			while (mc.options.keySwapOffhand.consumeClick()) {
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			AutoWorld.tick(mc);
			RoomCodes.tick(mc);
			SettingsLock.tick(mc);
			RobloxSfx.tick(mc);
			LockerAudit.tick(mc);
			WhatToTest.tick(mc);
			if (maximizeIn > 0 && --maximizeIn == 0) SDLVideo.SDL_MaximizeWindow(mc.getWindow().handle()); // once it's left fullscreen
			if (++noteIn >= 100) { // every 5 s: what the game is doing, told after a crash (LastRun, 1.2.0)
				noteIn = 0;
				LastRun.note(doing(mc));
			}
			if (mc.player == null) return;
			// Fortnite camera: always third person from behind; F5 can't change it.
			while (build.consumeClick()) BuildMode.toggle(mc);
			BuildMode.tick(mc); // before Aim: in build mode the mouse buttons build
			Aim.tick(mc);
			// (A scoped sniper looks through the scope, as in Fortnite.)
			var want = Aim.scoped() ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK;
			if (mc.options.getCameraType() != want) mc.options.setCameraType(want);
			DropControl.tick(mc);
			// Tab toggles the full map (in a match); the map screen closes itself on Tab. Waypoints only live during a
			// match.
			boolean tab = mc.options.keyPlayerList.isDown();
			if (tab && !tabWasDown && mc.gui.screen() == null && ClientState.inMatch()) mc.gui.setScreen(new MapScreen());
			tabWasDown = tab;
			if (!ClientState.inMatch()) Waypoint.clear();
			boolean inHub = !ClientState.inMatch();
			FortniteMedia.tick(inHub);
			if (inHub) mc.getMusicManager().stopPlaying();
			while (reload.consumeClick()) ClientPlayNetworking.send(new Net.Action("reload"));
			boolean holding = interact.isDown() && mc.gui.screen() == null && ClientState.inMatch();
			if (holding != interactWasDown) ClientPlayNetworking.send(new Net.Action("interact:" + (holding ? 1 : 0)));
			interactWasDown = holding;
			if (!ClientState.inMatch()) ClientState.squad = null;
			while (hub.consumeClick()) ClientPlayNetworking.send(new Net.Action("hub"));
			while (emote.consumeClick()) if (mc.gui.screen() == null) mc.gui.setScreen(new EmoteScreen());
			while (locker.consumeClick()) if (mc.gui.screen() == null && !ClientState.inMatch()) mc.gui.setScreen(new LockerScreen());
			CosmeticParticles.tick(mc);
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			SfxBank.preload();
			AutoWorld.onJoinWorld(mc);
			ClientState.kills = ClientState.wins = ClientState.rangeBest = ClientState.bullseyes = 0; // this world's stats arrive next (Unlocks); the last world's are in MyUnlocks
			ClientState.obby = false;
			MyUnlocks.onJoin(); // before the outfit: it's checked against unlocks from every world
			MyLocker.onJoin();
			WhatToTest.onJoin();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			FortniteMedia.stopAll();
			ClientState.EMOTES.clear();
			DamageNumbers.clear(); // (numbers still floating from the last world)
			ClientState.squad = null;
			ClientState.partyIds = List.of();
			ClientState.partyNames = List.of();
			Knocked.CLIENT.clear();
			ClientState.ACTIONS.clear();
			interactWasDown = false;
			RoomCodes.onDisconnect();
			AutoWorld.onDisconnect();
		});
		// e4mc's relay sessions leave a thread running after a game that hosted or joined over the relay closes, so the
		// Java process never ended (Melty would think the game was still open). Once Minecraft's own shutdown has
		// completely finished (the world is saved by then), end the process.
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			Thread main = Thread.currentThread();
			Thread exit = new Thread(() -> {
				try {
					main.join();
					Thread.sleep(2000);
				} catch (InterruptedException ignored) {
				}
				System.exit(0);
			}, "MinebloxRoyale exit");
			exit.setDaemon(true);
			exit.start();
		});
		// Always start borderless fullscreen (fullscreen on, exclusive off), or a maximised window for a test copy.
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
			mc.options.exclusiveFullscreen().set(false);
			if (mc.options.fullscreen().get() == WINDOWED) mc.options.fullscreen().set(!WINDOWED);
			mc.options.save();
		});
	}

	/**
	 * What the game is doing, in a few words, for LastRun: told after a crash, so players' reports say what was on
	 * screen when a graphics driver gave up (1.2.0, players' Melty: nvoglv64.dll about 3 minutes in).
	 */
	private static String doing(Minecraft mc) {
		var device = com.mojang.blaze3d.systems.RenderSystem.tryGetDevice();
		var screen = mc.gui.screen();
		String title = screen == null ? "" : screen.getTitle().getString();
		return (mc.level == null ? "in the menus" : ClientState.inMatch() ? "in a match" : "in the hub")
			+ (screen == null ? "" : ", " + (title.isBlank() ? screen.getClass().getSimpleName() : title) + " open")
			+ ", " + mc.getFps() + " fps" + (mc.level == null ? "" : ", " + mc.level.getEntityCount() + " entities")
			+ ", render distance " + mc.options.renderDistance().get() + ", window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
			+ (device == null ? "" : ", " + device.getDeviceInfo().backendName());
	}
}
