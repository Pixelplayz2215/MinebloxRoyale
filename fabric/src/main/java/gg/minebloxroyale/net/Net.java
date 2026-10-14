package gg.minebloxroyale.net;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.hub.Locker;
import gg.minebloxroyale.hub.Obby;
import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.match.Builds;
import gg.minebloxroyale.match.Interact;
import gg.minebloxroyale.match.Knocked;
import gg.minebloxroyale.match.MatchManager;
import gg.minebloxroyale.match.Parties;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server <-> client messages. */
public final class Net {
	private Net() {
	}

	/** Server -> client, twice a second: what the HUD shows. */
	public record Hud(String phase, int alive, float shield, float stormX, float stormZ, float stormR, float nextX, float nextZ, float nextR,
		int secondsLeft, String label, boolean inMatch, int kills,
		int drop, int onPortal, int hubCount, int stormPhase, boolean shrinking, int teamSize, String pads) implements CustomPacketPayload {
		public static final Type<Hud> TYPE = new Type<>(MinebloxRoyale.id("hud"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hud> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeUtf(p.phase); buf.writeVarInt(p.alive); buf.writeFloat(p.shield);
			buf.writeFloat(p.stormX); buf.writeFloat(p.stormZ); buf.writeFloat(p.stormR);
			buf.writeFloat(p.nextX); buf.writeFloat(p.nextZ); buf.writeFloat(p.nextR);
			buf.writeVarInt(p.secondsLeft); buf.writeUtf(p.label); buf.writeBoolean(p.inMatch); buf.writeVarInt(p.kills);
			buf.writeVarInt(p.drop); buf.writeVarInt(p.onPortal); buf.writeVarInt(p.hubCount); buf.writeVarInt(p.stormPhase); buf.writeBoolean(p.shrinking);
			buf.writeVarInt(p.teamSize); buf.writeUtf(p.pads);
		}, buf -> new Hud(buf.readUtf(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
			buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readVarInt(),
			buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readUtf()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * One teammate on a player's HUD and map. key: account id, or "bot:" + name. status: 0 standing, 1 knocked
	 * (seconds = until they bleed out), 2 out with a reboot card still running (seconds left), 3 out. entityId -1 when
	 * they aren't loaded near you. Their shared waypoint, if they placed one, and their eliminations this match.
	 */
	public record Mate(String key, String name, int slot, int entityId, float x, float y, float z, float health, float shield, int status, float seconds,
		boolean waypoint, float wx, float wz, int kills) {
		static void write(RegistryFriendlyByteBuf buf, Mate m) {
			buf.writeUtf(m.key); buf.writeUtf(m.name); buf.writeVarInt(m.slot); buf.writeVarInt(m.entityId);
			buf.writeFloat(m.x); buf.writeFloat(m.y); buf.writeFloat(m.z); buf.writeFloat(m.health); buf.writeFloat(m.shield);
			buf.writeVarInt(m.status); buf.writeFloat(m.seconds); buf.writeBoolean(m.waypoint); buf.writeFloat(m.wx); buf.writeFloat(m.wz);
			buf.writeVarInt(m.kills);
		}

		static Mate read(RegistryFriendlyByteBuf buf) {
			return new Mate(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
				buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
		}
	}

	/** A reboot van: where, which way it faces, and seconds until it can be used again (0 = ready). */
	public record VanMark(float x, float y, float z, float yaw, int readyIn) {
	}

	/** A teammate's reboot card lying on the ground, with its seconds left. */
	public record CardMark(float x, float z, float seconds) {
	}

	/**
	 * Server -> each player in a Duos / Trios / Squads match, twice a second: their teammates, the reboot vans, their
	 * team's cards on the ground, what they are doing with F (action + progress), who is reviving them, and (knocked)
	 * the seconds until they bleed out.
	 */
	public record Squad(int teamSize, int mySlot, List<Mate> mates, List<VanMark> vans, List<CardMark> cards, String action, float actionProgress,
		String revivedBy, float revivedProgress, float bleedLeft) implements CustomPacketPayload {
		public static final Type<Squad> TYPE = new Type<>(MinebloxRoyale.id("squad"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Squad> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.teamSize); buf.writeVarInt(p.mySlot);
			buf.writeVarInt(p.mates.size());
			for (Mate m : p.mates) Mate.write(buf, m);
			buf.writeVarInt(p.vans.size());
			for (VanMark v : p.vans) { buf.writeFloat(v.x()); buf.writeFloat(v.y()); buf.writeFloat(v.z()); buf.writeFloat(v.yaw()); buf.writeVarInt(v.readyIn()); }
			buf.writeVarInt(p.cards.size());
			for (CardMark c : p.cards) { buf.writeFloat(c.x()); buf.writeFloat(c.z()); buf.writeFloat(c.seconds()); }
			buf.writeUtf(p.action); buf.writeFloat(p.actionProgress); buf.writeUtf(p.revivedBy); buf.writeFloat(p.revivedProgress); buf.writeFloat(p.bleedLeft);
		}, buf -> {
			int size = buf.readVarInt(), slot = buf.readVarInt();
			List<Mate> mates = new ArrayList<>();
			for (int i = buf.readVarInt(); i > 0; i--) mates.add(Mate.read(buf));
			List<VanMark> vans = new ArrayList<>();
			for (int i = buf.readVarInt(); i > 0; i--) vans.add(new VanMark(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));
			List<CardMark> cards = new ArrayList<>();
			for (int i = buf.readVarInt(); i > 0; i--) cards.add(new CardMark(buf.readFloat(), buf.readFloat(), buf.readFloat()));
			return new Squad(size, slot, mates, vans, cards, buf.readUtf(), buf.readFloat(), buf.readUtf(), buf.readFloat(), buf.readFloat());
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> everyone: entity ids of every knocked fighter (crawling pose, nothing in hand, HUD). */
	public record Knocks(int[] ids) implements CustomPacketPayload {
		public static final Type<Knocks> TYPE = new Type<>(MinebloxRoyale.id("knocks"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Knocks> CODEC = StreamCodec.of((buf, p) -> buf.writeVarIntArray(p.ids), buf -> new Knocks(buf.readVarIntArray()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: your party (account ids and names, in the order they joined; empty = no party). */
	public record PartyInfo(List<String> ids, List<String> names) implements CustomPacketPayload {
		public static final Type<PartyInfo> TYPE = new Type<>(MinebloxRoyale.id("party"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PartyInfo> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.ids.size());
			for (int i = 0; i < p.ids.size(); i++) { buf.writeUtf(p.ids.get(i)); buf.writeUtf(p.names.get(i)); }
		}, buf -> {
			List<String> ids = new ArrayList<>(), names = new ArrayList<>();
			for (int i = buf.readVarInt(); i > 0; i--) { ids.add(buf.readUtf()); names.add(buf.readUtf()); }
			return new PartyInfo(ids, names);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: play a sounds-sheet sound at a position. */
	public record Sfx(String soundId, double x, double y, double z) implements CustomPacketPayload {
		public static final Type<Sfx> TYPE = new Type<>(MinebloxRoyale.id("sfx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sfx> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeUtf(p.soundId); buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z);
		}, buf -> new Sfx(buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Both ways. Client -> server: "I want to do this emote" (entityId ignored). Server -> clients: entity emotes (""
	 * stops).
	 */
	public record Emote(int entityId, String emoteId) implements CustomPacketPayload {
		public static final Type<Emote> TYPE = new Type<>(MinebloxRoyale.id("emote"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Emote> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.entityId); buf.writeUtf(p.emoteId);
		}, buf -> new Emote(buf.readVarInt(), buf.readUtf()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: this world's unlock stats, "kills;wins;obby;all;worldId;rangeBest;bullseyes"
	 * (Obby.sendUnlocks).
	 */
	public record Unlocks(String stats) implements CustomPacketPayload {
		public static final Type<Unlocks> TYPE = new Type<>(MinebloxRoyale.id("unlocks"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Unlocks> CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.stats), buf -> new Unlocks(buf.readUtf()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client, on joining (and if it changes): the 8-digit code of the room (world) you're in (/rc shows it).
	 */
	public record Room(String code) implements CustomPacketPayload {
		public static final Type<Room> TYPE = new Type<>(MinebloxRoyale.id("room"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Room> CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.code), buf -> new Room(buf.readUtf()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: a simple action (reload, hub). */
	public record Action(String action) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(MinebloxRoyale.id("action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.action), buf -> new Action(buf.readUtf()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: one player's Locker outfit (Locker.encode), for drawing their cosmetics. */
	public record LockerOutfit(String uuid, String outfit) implements CustomPacketPayload {
		public static final Type<LockerOutfit> TYPE = new Type<>(MinebloxRoyale.id("locker"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LockerOutfit> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeUtf(p.uuid); buf.writeUtf(p.outfit);
		}, buf -> new LockerOutfit(buf.readUtf(), buf.readUtf()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void lockerTo(ServerPlayer to, UUID who, String outfit) {
		ServerPlayNetworking.send(to, new LockerOutfit(who.toString(), outfit));
	}

	/** Server -> the attacker only: a damage number over what they hit (kind 0 health, 1 shield, 2 headshot). */
	public record DamageNumber(float x, float y, float z, float amount, int kind) implements CustomPacketPayload {
		public static final Type<DamageNumber> TYPE = new Type<>(MinebloxRoyale.id("damage_number"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DamageNumber> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeFloat(p.x); buf.writeFloat(p.y); buf.writeFloat(p.z); buf.writeFloat(p.amount); buf.writeVarInt(p.kind);
		}, buf -> new DamageNumber(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void damageNumberTo(ServerPlayer attacker, Entity target, float amount, int kind) {
		ServerPlayNetworking.send(attacker, new DamageNumber((float) target.getX(), (float) (target.getY() + target.getBbHeight() + 0.3), (float) target.getZ(), amount, kind));
	}

	/** Server -> client: the whole island as a picture (IslandMap) for the full map; RGB bytes, res x res. */
	public record IslandMapImage(int res, float originX, float originZ, float span, byte[] rgb) implements CustomPacketPayload {
		public static final Type<IslandMapImage> TYPE = new Type<>(MinebloxRoyale.id("island_map"));
		public static final StreamCodec<RegistryFriendlyByteBuf, IslandMapImage> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.res); buf.writeFloat(p.originX); buf.writeFloat(p.originZ); buf.writeFloat(p.span); buf.writeByteArray(p.rgb);
		}, buf -> new IslandMapImage(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByteArray()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void mapTo(ServerPlayer player, IslandMapImage map) {
		ServerPlayNetworking.send(player, map);
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Hud.TYPE, Hud.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Sfx.TYPE, Sfx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Emote.TYPE, Emote.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Unlocks.TYPE, Unlocks.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Room.TYPE, Room.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(IslandMapImage.TYPE, IslandMapImage.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DamageNumber.TYPE, DamageNumber.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LockerOutfit.TYPE, LockerOutfit.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Squad.TYPE, Squad.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Knocks.TYPE, Knocks.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PartyInfo.TYPE, PartyInfo.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Emote.TYPE, Emote.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			String act = payload.action(), head = act.contains(":") ? act.substring(0, act.indexOf(':')) : act;
			String arg = act.substring(head.length() + (act.contains(":") ? 1 : 0));
			switch (head) {
				case "reload" -> {
					ItemStack stack = player.getMainHandItem();
					if (stack.getItem() instanceof WeaponItem w && !Knocked.is(player)) Guns.startReload(player, stack, w.def);
				}
				// party:<invite:name | accept[:name] | leave | list> - the /party commands, typed in the player's own
				// game
				case "party" -> Parties.handle(player, arg);
				// interact:1 / interact:0 - F held down or let go (revive, reboot)
				case "interact" -> Interact.set(player, arg.equals("1"));
				// waypoint:<x>:<z> or waypoint:clear - shared with teammates in team modes (Solo: nobody else sees it)
				case "waypoint" -> MatchManager.get().setWaypoint(player, arg);
				case "hub" -> MatchManager.get().leaveToHub(player);
				case "bus_jump" -> MatchManager.get().jumpFromBus(player);
				case "locker" -> {
					// locker:<slot>:<cosmetic id or none> - outside matches only
					String[] a = arg.split(":");
					if (a.length == 2 && !MatchManager.get().isAlive(player)) Locker.equip(player, a[0], a[1]);
				}
				// career:<worldId=kills,wins,obby,rangeBest,bullseyes;...> - this player's stats from every world, as
				// their own game remembers them
				case "career" -> Obby.setCareer(player, arg);
				case "lockerall" -> {
					// lockerall:<slot:id,slot:id...> - the outfit this player's own game remembers, sent on join
					if (!MatchManager.get().isAlive(player)) Locker.restore(player, arg);
				}
				case "build" -> {
					// build:<piece>:<material>:<tx>:<ty>:<tz>:<dir> (a malformed one is ignored, never an error)
					String[] a = arg.split(":");
					Direction dir = a.length == 6 ? Direction.byName(a[5]) : null;
					if (dir == null || !MatchManager.get().isAlive(player) || Knocked.is(player)) return;
					try {
						Builds.place(player.level(), player, a[0], a[1], new Builds.Placement(Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]), dir));
					} catch (NumberFormatException ignored) {
					}
				}
				case "fire" -> {
					ItemStack stack = player.getMainHandItem();
					if (stack.getItem() instanceof WeaponItem w && !player.isSpectator() && !Knocked.is(player)) {
						// fire:x:y:z = where the player's crosshair points (used if it's believable: in front, in
						// range).
						Vec3 aim = null;
						String[] xyz = arg.split(":");
						if (xyz.length == 3) {
							try {
								aim = new Vec3(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2]));
								Vec3 to = aim.subtract(player.getEyePosition());
								if (to.length() > w.def.rangeBlocks() + 8 || to.length() < 0.5 || to.normalize().dot(player.getLookAngle()) < 0.7) aim = null;
							} catch (NumberFormatException e) {
								aim = null;
							}
						}
						Guns.tryFire(player, stack, w.def, player.isUsingItem() ? w.def.adsSpreadMult() : 1F, aim);
					}
				}
				default -> {
				}
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Emote.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			EmoteDef def = Sheets.EMOTES_BY_ID.get(payload.emoteId());
			if (def != null && !Obby.hasEmote(player, def)) return;
			emoteAll(player.level().getServer(), player, def == null ? "" : def.id());
		});
	}

	public static void sfxTo(ServerPlayer player, String soundId, Vec3 at) {
		ServerPlayNetworking.send(player, new Sfx(soundId, at.x, at.y, at.z));
	}

	public static void sfxAll(MinecraftServer server, String soundId, Vec3 at) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) sfxTo(p, soundId, at);
	}

	public static void emoteAll(MinecraftServer server, Entity who, String emoteId) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(p, new Emote(who.getId(), emoteId));
	}
}
