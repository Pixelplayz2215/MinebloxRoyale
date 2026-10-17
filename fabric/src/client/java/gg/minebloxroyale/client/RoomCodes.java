package gg.minebloxroyale.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.net.RoomCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * Room codes (creator): everyone starts in their own world, their own private room. /rc (or /roomcode) shows the
 * 8-digit code of the room you're in; /rc <code> leaves it and joins a friend's room; /leave goes back to your own.
 *
 * Finding a room by its code (creator's choice): the host's game posts its join address (from e4mc) to ntfy.sh, a free
 * public message service with no account, under a topic made from the code and scrambled with the code (AES-GCM), so
 * only someone who has the code can find or read it. ntfy keeps a post for about 12 hours, so it's posted again every 6
 * hours while hosting, and a "closed" note goes up when the host leaves the world. If the service can't be reached,
 * the Melty join link still works.
 */
public final class RoomCodes {
	private static final String SERVICE = "https://ntfy.sh/";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final long REPOST_MS = 6 * 3600_000L, RETRY_MS = 60_000L;

	/** The code of the room this game is in (its host sends it when we join). */
	private static volatile @Nullable String current;
	/**
	 * Hosting: the address friends join at (once e4mc has given one), the code it's posted under, and how that went.
	 */
	private static @Nullable String hostAddress;
	private static volatile RoomCode.@Nullable Code hosted;
	private static volatile long postedAt;
	private static volatile boolean online, posting;
	private static volatile String problem = "";

	/** One post on a room's topic: open (with the host's name and address) or closed. */
	private record Post(String owner, boolean open, String host, String address) {
	}

	private RoomCodes() {
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
			for (String name : new String[] { "rc", "roomcode" })
				dispatcher.register(ClientCommands.literal(name).executes(c -> show(c.getSource()))
					.then(ClientCommands.argument("code", StringArgumentType.greedyString()).executes(c -> join(c.getSource(), StringArgumentType.getString(c, "code")))));
			dispatcher.register(ClientCommands.literal("leave").executes(c -> leave(c.getSource())));
			dispatcher.register(ClientCommands.literal("rejoin").executes(c -> rejoin(c.getSource())));
		});
		ClientPlayNetworking.registerGlobalReceiver(Net.Room.TYPE, (p, ctx) -> current = p.code());
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> closeNow());
	}

	/** The code of the room this game is in, or null until its host has said (shown on the hub banner). */
	static @Nullable String current() {
		return current;
	}

	/** Left a world: no room code until the next one says what it is. */
	static void onDisconnect() {
		current = null;
	}

	// ---- the command ----

	private static int show(FabricClientCommandSource src) {
		String code = current;
		if (code == null) {
			src.sendError(Component.literal("No room code yet - try again in a moment."));
			return 0;
		}
		boolean host = src.getClient().getSingleplayerServer() != null;
		Component shown = Component.literal(code).withStyle(s -> s.withColor(ChatFormatting.GOLD).withBold(true)
			.withClickEvent(new ClickEvent.CopyToClipboard(code)).withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy"))));
		src.sendFeedback(Component.literal(host ? "Your room code: " : "This room's code: ").append(shown));
		src.sendFeedback(Component.literal("Friends type /rc " + code + " to join.").withStyle(ChatFormatting.GRAY));
		if (host && !online)
			src.sendFeedback(Component.literal(problem.isEmpty() ? "Your room is still going online - give it a moment."
				: "Friends can't find it by code right now (" + problem + "). The Melty join link still works.").withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	private static int join(FabricClientCommandSource src, String typed) {
		String code = typed.replaceAll("[^0-9]", "");
		if (code.length() != 8) {
			src.sendError(Component.literal("A room code is 8 digits, like /rc 12345678."));
			return 0;
		}
		if (code.equals(current)) {
			src.sendFeedback(Component.literal("You're already in room " + code + "."));
			return 1;
		}
		src.sendFeedback(Component.literal("Looking for room " + code + "...").withStyle(ChatFormatting.GRAY));
		Minecraft mc = src.getClient();
		latest(code).whenComplete((post, error) -> mc.execute(() -> {
			if (error != null) say(mc, "Couldn't reach the room code service. Try again in a moment.", ChatFormatting.RED);
			else if (post == null || !post.open()) say(mc, "No open room with code " + code + ". Check the code, or ask your friend to type /rc.", ChatFormatting.RED);
			else {
				say(mc, "Joining " + post.host() + "'s room...", ChatFormatting.GREEN);
				MinebloxRoyale.LOG.info("BR: joining room {}", code);
				AutoWorld.joinRoom(post.address(), code);
			}
		}));
		return 1;
	}

	/** /leave (creator): out of a friend's room, back to your own world. */
	private static int leave(FabricClientCommandSource src) {
		Minecraft mc = src.getClient();
		if (mc.getSingleplayerServer() != null) {
			src.sendFeedback(Component.literal("You're already in your own world."));
			return 1;
		}
		src.sendFeedback(Component.literal("Going back to your own world...").withStyle(ChatFormatting.GRAY));
		mc.execute(AutoWorld::leaveRoom); // after the command has finished
		return 1;
	}

	/** (1.0.1.3) /rejoin: try the friend Melty sent us to again, when their world wasn't open yet. */
	private static int rejoin(FabricClientCommandSource src) {
		if (src.getClient().getSingleplayerServer() == null) {
			src.sendFeedback(Component.literal("You're in a friend's world already. /leave goes back to your own."));
			return 1;
		}
		Minecraft mc = src.getClient();
		src.sendFeedback(Component.literal("Trying your friend's game again...").withStyle(ChatFormatting.GRAY));
		mc.execute(() -> { // after the command has finished
			if (!AutoWorld.rejoinFriend()) say(mc, "There's no friend's game to rejoin. Press Join in Melty, or type /rc and their room code.", ChatFormatting.RED);
		});
		return 1;
	}

	private static void say(Minecraft mc, String text, ChatFormatting color) {
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text).withStyle(color));
	}

	// ---- hosting ----

	/** e4mc gave this world its public address: post it under the world's room code. */
	static void hosting(String address) {
		MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) return;
		hostAddress = address;
		publish(server, true);
	}

	/** Every client tick: post again before ntfy forgets it, retry a failed post, and say "closed" once we've left. */
	static void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		RoomCode.Code c = hosted;
		if (hostAddress != null && server == null) { // left our own world: friends can't come in any more
			if (c != null) post(c, false, "", "");
			hostAddress = null;
			hosted = null;
			online = false;
			return;
		}
		if (hostAddress == null || server == null || posting) return;
		long since = System.currentTimeMillis() - postedAt;
		if (online ? since > REPOST_MS : since > RETRY_MS) publish(server, false);
	}

	private static void publish(MinecraftServer server, boolean checkClash) {
		RoomCode.Code c = RoomCode.of(server);
		String name = Minecraft.getInstance().getUser().getName(), address = hostAddress;
		posting = true;
		online = false;
		problem = "";
		// Another world that drew the same code and is open right now: this world takes a new code.
		CompletableFuture<Boolean> clash = checkClash ? latest(c.code()).thenApply(p -> p != null && p.open() && !p.owner().equals(c.owner()))
			: CompletableFuture.completedFuture(false);
		clash.exceptionally(e -> false).thenCompose(taken -> {
			RoomCode.Code use = taken ? RoomCode.renew(server) : c;
			hosted = use;
			return post(use, true, name, address);
		}).whenComplete((ok, error) -> {
			online = error == null && Boolean.TRUE.equals(ok);
			problem = online ? "" : error != null ? "no connection to the code service" : "the code service said no";
			postedAt = System.currentTimeMillis();
			posting = false;
			RoomCode.Code h = hosted;
			MinebloxRoyale.LOG.info("BR: room {} {}", h == null ? "?" : h.code(), online ? "is online" : "couldn't go online (" + problem + ")");
		});
	}

	/** The game is closing while hosting: say "closed" (waits up to 2 seconds). */
	private static void closeNow() {
		RoomCode.Code c = hosted;
		if (hostAddress == null || c == null) return;
		try {
			post(c, false, "", "").get(2, TimeUnit.SECONDS);
		} catch (Exception ignored) {
		}
	}

	// ---- the code service ----

	private static CompletableFuture<Boolean> post(RoomCode.Code c, boolean open, String host, String address) {
		String plain = open ? String.join("|", "v1", c.owner(), "open", host, address) : String.join("|", "v1", c.owner(), "closed");
		HttpRequest req = HttpRequest.newBuilder(URI.create(SERVICE + topic(c.code()))).timeout(Duration.ofSeconds(8))
			.header("Firebase", "no").POST(HttpRequest.BodyPublishers.ofString(seal(c.code(), plain))).build();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).thenApply(r -> r.statusCode() / 100 == 2);
	}

	/** The newest post for this code in the last 12 hours (null = none). */
	private static CompletableFuture<@Nullable Post> latest(String code) {
		HttpRequest req = HttpRequest.newBuilder(URI.create(SERVICE + topic(code) + "/json?poll=1&since=12h")).timeout(Duration.ofSeconds(8)).GET().build();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
			if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
			Post last = null;
			for (String line : r.body().split("\n")) {
				if (line.isBlank()) continue;
				JsonObject o = JsonParser.parseString(line).getAsJsonObject();
				if (!o.has("message") || !o.has("event") || !"message".equals(o.get("event").getAsString())) continue;
				String[] p = unseal(code, o.get("message").getAsString()).split("\\|", -1);
				if (p.length == 5 && p[0].equals("v1") && p[2].equals("open")) last = new Post(p[1], true, p[3], p[4]);
				else if (p.length == 3 && p[0].equals("v1") && p[2].equals("closed")) last = new Post(p[1], false, "", "");
			}
			return last;
		});
	}

	/** The topic a code's posts go to: made from the code, so it can't be read back to the code. */
	private static String topic(String code) {
		return "minebloxroyale-" + HexFormat.of().formatHex(sha256("minebloxroyale room topic " + code)).substring(0, 32);
	}

	private static SecretKeySpec key(String code) {
		return new SecretKeySpec(Arrays.copyOf(sha256("minebloxroyale room key " + code), 16), "AES");
	}

	private static String seal(String code, String plain) {
		try {
			byte[] iv = new byte[12];
			RANDOM.nextBytes(iv);
			Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
			c.init(Cipher.ENCRYPT_MODE, key(code), new GCMParameterSpec(128, iv));
			byte[] body = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + body.length).put(iv).put(body).array());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The post's text, or "" when it isn't one of ours (or was made with another code). */
	private static String unseal(String code, String sealed) {
		try {
			byte[] all = Base64.getDecoder().decode(sealed.trim());
			Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
			c.init(Cipher.DECRYPT_MODE, key(code), new GCMParameterSpec(128, all, 0, 12));
			return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
		} catch (Exception e) {
			return "";
		}
	}

	private static byte[] sha256(String s) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
