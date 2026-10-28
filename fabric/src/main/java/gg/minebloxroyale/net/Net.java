package gg.minebloxroyale.net;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.hub.Locker;
import gg.minebloxroyale.hub.Obby;
import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.match.Builds;
import gg.minebloxroyale.match.GrenadeEntity;
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
import net.minecraft.server.level.ServerLevel;
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
		int drop, int onPortal, int hubCount, int stormPhase, boolean shrinking, int teamSize, String pads, String mode) implements CustomPacketPayload {
		public static final Type<Hud> TYPE = new Type<>(MinebloxRoyale.id("hud"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hud> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeUtf(p.phase); buf.writeVarInt(p.alive); buf.writeFloat(p.shield);
			buf.writeFloat(p.stormX); buf.writeFloat(p.stormZ); buf.writeFloat(p.stormR);
			buf.writeFloat(p.nextX); buf.writeFloat(p.nextZ); buf.writeFloat(p.nextR);
			buf.writeVarInt(p.secondsLeft); buf.writeUtf(p.label); buf.writeBoolean(p.inMatch); buf.writeVarInt(p.kills);
			buf.writeVarInt(p.drop); buf.writeVarInt(p.onPortal); buf.writeVarInt(p.hubCount); buf.writeVarInt(p.stormPhase); buf.writeBoolean(p.shrinking);
			buf.writeVarInt(p.teamSize); buf.writeUtf(p.pads); buf.writeUtf(p.mode);
		}, buf -> new Hud(buf.readUtf(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
			buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readVarInt(),
			buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(), buf.readUtf()));

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

	/**
	 * Server -> everyone: entity ids of every knocked fighter (crawling pose, nothing in hand, HUD), and of everyone
	 * reviving, rebooting or searching a chest right now (their avatars kneel, type or rummage).
	 */
	public record Knocks(int[] ids, int[] reviving, int[] rebooting, int[] searching) implements CustomPacketPayload {
		public static final Type<Knocks> TYPE = new Type<>(MinebloxRoyale.id("knocks"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Knocks> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarIntArray(p.ids);
			buf.writeVarIntArray(p.reviving);
			buf.writeVarIntArray(p.rebooting);
			buf.writeVarIntArray(p.searching);
		}, buf -> new Knocks(buf.readVarIntArray(), buf.readVarIntArray(), buf.readVarIntArray(), buf.readVarIntArray()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> client: everyone's launcher in this world, by account id (the badges by their names; Launchers,
	 * 2.0.0-test6.2), and everyone's country (the flags after their names, 2.0.0).
	 */
	public record Launchers(java.util.Map<String, String> byPlayer, java.util.Map<String, String> flags) implements CustomPacketPayload {
		public static final Type<Launchers> TYPE = new Type<>(MinebloxRoyale.id("launchers"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Launchers> CODEC = StreamCodec.of((buf, p) -> {
			for (var map : java.util.List.of(p.byPlayer, p.flags)) {
				buf.writeVarInt(map.size());
				for (var e : map.entrySet()) { buf.writeUtf(e.getKey()); buf.writeUtf(e.getValue()); }
			}
		}, buf -> {
			java.util.Map<String, String> m = new java.util.HashMap<>(), f = new java.util.HashMap<>();
			for (int i = buf.readVarInt(); i > 0; i--) m.put(buf.readUtf(), buf.readUtf());
			for (int i = buf.readVarInt(); i > 0; i--) f.put(buf.readUtf(), buf.readUtf());
			return new Launchers(m, f);
		});

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

	/** Server -> client, each second of a match (v2.0.0): where the bosses still standing are (x, z each), for the map icons. */
	public record BossMarks(float[] xz) implements CustomPacketPayload {
		public static final Type<BossMarks> TYPE = new Type<>(MinebloxRoyale.id("boss_marks"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BossMarks> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.xz.length);
			for (float f : p.xz) buf.writeFloat(f);
		}, buf -> {
			float[] xz = new float[Math.min(buf.readVarInt(), 32)];
			for (int i = 0; i < xz.length; i++) xz[i] = buf.readFloat();
			return new BossMarks(xz);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> clients near it (v2.0.0): a gun fired. Who, which gun (weapons sheet), from where (the shooter's eye) and
	 * where each bullet or pellet ended (x, y, z each); hits: which of them hit someone (a bit each, the first 32). Each
	 * client draws the tracers, the muzzle flash, the impacts, and the shooter's recoil.
	 */
	public record Shot(int shooter, String weapon, float x, float y, float z, float[] ends, int hits) implements CustomPacketPayload {
		public static final Type<Shot> TYPE = new Type<>(MinebloxRoyale.id("shot"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Shot> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.shooter); buf.writeUtf(p.weapon); buf.writeFloat(p.x); buf.writeFloat(p.y); buf.writeFloat(p.z);
			buf.writeVarInt(p.ends.length);
			for (float f : p.ends) buf.writeFloat(f);
			buf.writeInt(p.hits);
		}, buf -> {
			int shooter = buf.readVarInt();
			String weapon = buf.readUtf();
			float x = buf.readFloat(), y = buf.readFloat(), z = buf.readFloat();
			float[] ends = new float[Math.min(buf.readVarInt(), 3 * 64)];
			for (int i = 0; i < ends.length; i++) ends[i] = buf.readFloat();
			return new Shot(shooter, weapon, x, y, z, ends, buf.readInt());
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Both ways. Client -> server: "I want to do this emote" (entityId ignored). Server -> clients: entity emotes (""
	 * stops).
	 */
	/**
	 * The match is over (2.0.0-test5): this player's results card, as JSON (MatchStats.results): won, the mode, their
	 * placing and stats, their gold line by line, their team's rows, the winners, the stage the winning team stands on.
	 */
	public record Victory(String json) implements CustomPacketPayload {
		public static final Type<Victory> TYPE = new Type<>(MinebloxRoyale.id("victory"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Victory> CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.json, 65535), buf -> new Victory(buf.readUtf(65535)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

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

	/**
	 * (2.0.0) Server -> client: the obby timer. state 0: none (off the course); 1: running from now (best: this world's
	 * best, ticks, 0 = none yet); 2: finished in ticks, with the line under it (New best! or the best, and any preset it
	 * unlocked), shown for 4 seconds.
	 */
	public record ObbyTimer(int state, int ticks, int best, String line) implements CustomPacketPayload {
		public static final Type<ObbyTimer> TYPE = new Type<>(MinebloxRoyale.id("obby_timer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ObbyTimer> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.state); buf.writeVarInt(p.ticks); buf.writeVarInt(p.best); buf.writeUtf(p.line);
		}, buf -> new ObbyTimer(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()));

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
		PayloadTypeRegistry.clientboundPlay().register(Shot.TYPE, Shot.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BossMarks.TYPE, BossMarks.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Emote.TYPE, Emote.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Victory.TYPE, Victory.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Unlocks.TYPE, Unlocks.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Room.TYPE, Room.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(IslandMapImage.TYPE, IslandMapImage.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DamageNumber.TYPE, DamageNumber.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ObbyTimer.TYPE, ObbyTimer.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LockerOutfit.TYPE, LockerOutfit.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Squad.TYPE, Squad.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Knocks.TYPE, Knocks.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PartyInfo.TYPE, PartyInfo.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Launchers.TYPE, Launchers.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Emote.TYPE, Emote.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			String act = payload.action(), head = act.contains(":") ? act.substring(0, act.indexOf(':')) : act;
			String arg = act.substring(head.length() + (act.contains(":") ? 1 : 0));
			switch (head) {
				case "reload" -> {
					if (MatchManager.get().frozen(player)) return; // (a winner, the match over: 2.0.0-test5)
					ItemStack stack = player.getMainHandItem();
					if (stack.getItem() instanceof WeaponItem w && !Knocked.is(player)) Guns.startReload(player, stack, w.def);
				}
				// party:<invite:name | accept[:name] | leave | list> - the /party commands, typed in the player's own
				// game
				case "party" -> Parties.handle(player, arg);
				// launcher:<id> - which launcher this player's game was started from (the badge by their name, 2.0.0-test6.2)
				case "launcher" -> gg.minebloxroyale.net.Launchers.set(player, arg);
				// flag:<CC> - this player's country, the flag after their name ("flag:" while they hide it; 2.0.0)
				case "flag" -> gg.minebloxroyale.net.Launchers.setFlag(player, arg);
				// interact:1 / interact:0 - F held down or let go (revive, reboot)
				case "interact" -> Interact.set(player, arg.equals("1"));
				// waypoint:<x>:<z> or waypoint:clear - shared with teammates in team modes (Solo: nobody else sees it)
				case "waypoint" -> MatchManager.get().setWaypoint(player, arg);
				case "hub" -> MatchManager.get().leaveToHub(player);
				case "bus_jump" -> MatchManager.get().jumpFromBus(player);
				case "locker" -> {
					// locker:<slot>:<cosmetic id or none> - outside matches only; locker:buy:<item id> - the shop (v2.0.0, gold)
					String[] a = arg.split(":");
					if (a.length == 2 && a[0].equals("buy")) gg.minebloxroyale.hub.Gold.buy(player, a[1]);
					else if (a.length == 2 && !MatchManager.get().isAlive(player)) Locker.equip(player, a[0], a[1]);
				}
				// career:<worldId=kills,wins,obby,rangeBest,bullseyes;...> - this player's stats from every world, as
				// their own game remembers them
				case "career" -> Obby.setCareer(player, arg);
				case "lockerall" -> {
					// lockerall:<slot:id,slot:id...> - the outfit this player's own game remembers, sent on join
					if (!MatchManager.get().isAlive(player)) Locker.restore(player, arg);
				}
				case "build" -> {
					if (MatchManager.get().frozen(player)) return; // (a winner, the match over: 2.0.0-test5)
					// build:<piece>:<material>:<tx>:<ty>:<tz>:<dir> (a malformed one is ignored, never an error)
					String[] a = arg.split(":");
					Direction dir = a.length == 6 ? Direction.byName(a[5]) : null;
					if (dir == null || !MatchManager.get().isAlive(player) || Knocked.is(player)) return;
					try {
						Builds.place(player.level(), player, a[0], a[1], new Builds.Placement(Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]), dir));
					} catch (NumberFormatException ignored) {
					}
				}
				case "throw" -> {
					if (MatchManager.get().frozen(player)) return; // (a winner, the match over: 2.0.0-test5)
					// throw:x:y:z = a grenade, towards where the crosshair points (v2.0.0)
					String[] xyz = arg.split(":");
					Vec3 aim = null;
					try {
						if (xyz.length == 3) aim = new Vec3(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2]));
					} catch (NumberFormatException e) {
						aim = null;
					}
					if (aim != null && (aim.distanceTo(player.getEyePosition()) > 120 || aim.subtract(player.getEyePosition()).normalize().dot(player.getLookAngle()) < 0.7)) aim = null;
					GrenadeEntity.tryThrow(player, aim);
				}
				case "fire" -> {
					if (MatchManager.get().frozen(player)) return; // (a winner, the match over: 2.0.0-test5)
					ItemStack stack = player.getMainHandItem();
					if (stack.getItem() instanceof WeaponItem w && !player.isSpectator() && !Knocked.is(player)) {
						// fire:x:y:z = where the player's crosshair points (used if it's believable: in front, in
						// range); 2.0.0: then :x:y:z of their camera, so the shot follows the crosshair's own line
						// (Hitboxes; used if it's near their head).
						Vec3 aim = null, cam = null;
						String[] xyz = arg.split(":");
						if (xyz.length == 3 || xyz.length == 6) {
							try {
								aim = new Vec3(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2]));
								Vec3 to = aim.subtract(player.getEyePosition());
								if (to.length() > w.def.rangeBlocks() + 8 || to.length() < 0.5 || to.normalize().dot(player.getLookAngle()) < 0.7) aim = null;
								if (aim != null && xyz.length == 6) {
									cam = new Vec3(Double.parseDouble(xyz[3]), Double.parseDouble(xyz[4]), Double.parseDouble(xyz[5]));
									Vec3 line = aim.subtract(cam);
									if (cam.distanceTo(player.getEyePosition()) > 4 || line.length() < 0.5 || line.normalize().dot(player.getLookAngle()) < 0.7) cam = null;
								}
							} catch (NumberFormatException e) {
								aim = null;
								cam = null;
							}
						}
						Guns.tryFire(player, stack, w.def, player.isUsingItem() ? w.def.adsSpreadMult() : 1F, aim, cam);
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

	public static void bossMarks(ServerPlayer p, float[] xz) {
		ServerPlayNetworking.send(p, new BossMarks(xz));
	}

	/** A shot, to every player within 256 blocks of it in its world (they see its tracers). */
	public static void shot(ServerLevel level, Shot shot) {
		for (ServerPlayer p : level.players())
			if (p.distanceToSqr(shot.x(), shot.y(), shot.z()) < 256 * 256) ServerPlayNetworking.send(p, shot);
	}

	public static void emoteAll(MinecraftServer server, Entity who, String emoteId) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(p, new Emote(who.getId(), emoteId));
	}
}
