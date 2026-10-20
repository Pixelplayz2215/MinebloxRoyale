package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.mixin.IOWorkerAccessor;
import gg.minebloxroyale.mixin.SimpleRegionStorageAccessor;
import gg.minebloxroyale.sheets.Sheets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The match island is generated in the background, a few chunks at a time on Minecraft's world-generation threads, so
 * the Battle Bus flies over terrain that already exists instead of making the server generate it mid-flight. Every
 * match gets a new island (creator: different biomes and terrain each round) somewhere fresh. The next one is made
 * while the current match is played (more slowly, so the match doesn't lag), so it's usually ready by the time
 * everyone is back in the hub (creator: waiting for the island between rounds was a pain).
 */
public final class IslandPregen {
	private static double cx, cz;
	private static volatile int total = 1;
	/**
	 * Chunks finished (counted from several threads, so atomic: a plain counter lost counts and never reached 100%).
	 */
	private static final AtomicInteger doneCount = new AtomicInteger();
	private static volatile boolean running, finished;
	/** A match is being played: the island being made is the next one, generated more slowly. */
	private static volatile boolean inMatch;
	/**
	 * Until when the next island waits (2.0.0-test3, creator: "we need to reduce the lag"; in 1.2.0 too: "lag is bad in game for v1.2.0"): the match's first minutes,
	 * the Battle Bus and everyone dropping in, are when every game is busiest (all the land below loading, bots landing
	 * round you), and making the next island used the processor right then (match pregenQuiet).
	 */
	private static volatile long quietUntil;
	/** Since when the generator has been waiting for the disk to catch up (0 = it isn't). */
	private static volatile long diskWaitSince;
	private static volatile Thread worker;
	/** Until when the island generator is on its gentle start (one chunk at a time). */
	private static volatile long gentleUntil;
	/**
	 * Holds an island chunk loaded while it's generated, then for 15 s more (the countdown starts once the chunk is
	 * done): the next chunks of the spiral need its half-built neighbours, and letting them go at once made Minecraft
	 * write them to disk and read them back again and again (an island took 20+ minutes). It only loads: unlike the
	 * portal ticket used before, it never ticks the chunk and isn't saved with the world.
	 */
	private static TicketType ticket;
	private static volatile boolean started, stopping;
	/** When this session's island finished generating (0 = it was already done when the world opened). */
	private static volatile long finishedAt;
	private static final long STILL_SAVING_MS = 120_000;
	private static final long GENTLE_START_MS = 60_000;
	/** Island chunks being generated right now (each holds a ticket). */
	private static final Set<ChunkPos> HELD = ConcurrentHashMap.newKeySet();
	private static final int MAX_QUEUED_WRITES = 200;
	/**
	 * The chunk area (x0, z0, x1, z1) of the island being generated, with a margin for its half-made border; null when
	 * none.
	 */
	private static volatile int @Nullable [] building, abandoned;
	/** Recent progress (time, chunks done), for the countdown. Server thread only. */
	private static final ArrayDeque<long[]> PROGRESS = new ArrayDeque<>();
	private static long finishGuess, guessedAt;
	/**
	 * How fast the gentle start goes next to full speed (measured 0.41 to 0.49 with two games on one PC). Rounded up on
	 * purpose: guessing too slow a full speed only makes the countdown run a little fast, never jump up.
	 */
	private static final double GENTLE_SPEED = 0.5;
	/**
	 * (1.0.1.1) Disk space. An island takes about 100-150 MB of the world folder, and a disk that ran full while one was
	 * made crashed the game (Melty, 2026-10-16). A new island is only started with NEW_ISLAND_MIN free; otherwise the
	 * next match is played on the island just played (put back as it was, as every island is after its match). While
	 * one is made, under DISK_FLOOR free it stops: the next island is dropped for the one just played, and a world's
	 * first island (no other to play on) waits until there's room again, the hub saying so.
	 */
	private static final long NEW_ISLAND_MIN = 1L << 30, DISK_FLOOR = 300L << 20;
	/** Low on disk space: the next match is on the island just played. */
	private static volatile boolean sameIsland;
	/** Since when the first island has been waiting for disk space (0 = it isn't). */
	private static volatile long diskFullSince;
	/** The island being played while the next one is made, to go back to if the disk runs low. */
	private static double lastCx, lastCz;
	private static volatile boolean haveLast;
	/** The chunk area of a next island dropped for lack of disk space: its half-made chunks are never written. */
	private static volatile int @Nullable [] lowDiskDrop;

	private IslandPregen() {
	}

	/** Low on disk space, so the next match is on the island just played (the hub says so). */
	public static boolean sameIsland() {
		return sameIsland;
	}

	private static long freeSpace(MinecraftServer server) {
		long free = MinebloxRoyale.freeSpace(server.getWorldPath(LevelResource.ROOT));
		return free < 0 ? Long.MAX_VALUE : free;
	}

	/** At start-up, while registries are open: the island's chunk ticket. */
	public static void init() {
		ticket = Registry.register(BuiltInRegistries.TICKET_TYPE, MinebloxRoyale.id("island_pregen"), new TicketType(300L, TicketType.FLAG_LOADING));
	}

	public static double centerX() {
		return cx;
	}

	public static double centerZ() {
		return cz;
	}

	/** 0..100 */
	public static int percent() {
		return Math.min(100, doneCount.get() * 100 / Math.max(1, total));
	}

	/**
	 * Seconds until the island is done (creator: a countdown in the hub), from how fast it has gone over the last 30 s.
	 * A second of the gentle start counts as GENTLE_SPEED of a full-speed one, so the countdown doesn't jump up when
	 * the gentle start ends (it did, at about 15%). Smoothed over about 10 s so it counts down steadily. -1 while it
	 * can't tell yet (still finding a spot, or under 10 s in). Called from the server thread (HUD).
	 */
	public static int secondsLeft() {
		long now = System.currentTimeMillis();
		int done = doneCount.get(), left = total - done;
		if (ready() || worker == null || !worker.isAlive() || total <= 1 || waitingForDisk() || diskFullSince != 0) {
			PROGRESS.clear();
			finishGuess = 0;
			return ready() ? 0 : -1;
		}
		if (PROGRESS.isEmpty() || now - PROGRESS.peekLast()[0] >= 1000) PROGRESS.addLast(new long[] { now, done });
		while (PROGRESS.size() > 2 && now - PROGRESS.peekFirst()[0] > 30_000) PROGRESS.removeFirst();
		long[] first = PROGRESS.peekFirst();
		long gentleEnd = gentleUntil;
		double gentleSecs = Math.max(0, Math.min(now, gentleEnd) - first[0]) / 1000.0;
		double fullSecs = (now - first[0]) / 1000.0 - gentleSecs + gentleSecs * GENTLE_SPEED;
		double rate = now - first[0] < 10_000 ? 0 : (done - first[1]) / fullSecs; // chunks a second at full speed
		if (rate <= 0) return finishGuess == 0 ? -1 : (int) Math.max(1, (finishGuess - now) / 1000);
		double gentleLeft = Math.max(0, (gentleEnd - now) / 1000.0), slow = rate * GENTLE_SPEED;
		double est = left <= slow * gentleLeft ? left / slow : gentleLeft + (left - slow * gentleLeft) / rate;
		long guess = now + (long) (est * 1000);
		if (finishGuess == 0) finishGuess = guess;
		else finishGuess += (long) ((guess - finishGuess) * (1 - Math.exp(-(now - guessedAt) / 10_000.0)));
		guessedAt = now;
		return (int) Math.max(1, (finishGuess - now) / 1000);
	}

	/** The countdown as the hub shows it: "1:20 left", "saving to disk", "disk full", or "" while it can't tell yet. */
	public static String timeLeft() {
		int s = secondsLeft();
		if (diskFullSince != 0) return "disk full: free up space";
		if (waitingForDisk()) return "saving to disk";
		return s < 0 ? "" : String.format(Locale.ROOT, "%d:%02d left", s / 60, s % 60);
	}

	public static void start(MinecraftServer server) {
		ServerLevel level = server.overworld();
		float radius = MinebloxRoyale.setting("startRadius");
		Path file = server.getWorldPath(LevelResource.ROOT).resolve("minebloxroyale_island.txt");
		started = false;
		stopping = false;
		inMatch = false;
		diskWaitSince = 0;
		diskFullSince = 0;
		sameIsland = false;
		haveLast = false;
		lowDiskDrop = null;
		IslandMap.reset();
		HELD.clear();
		building = null;
		abandoned = null;
		finishedAt = 0;
		int resumeFrom;
		try {
			String[] s = Files.readString(file).trim().split("\\s+");
			cx = Double.parseDouble(s[0]);
			cz = Double.parseDouble(s[1]);
			resumeFrom = s.length > 2 ? Integer.parseInt(s[2]) : 0;
			// A changed terrain sheet (e.g. snow biomes now excluded) means the saved island may break the rules: pick
			// again.
			if (s.length < 4 || Integer.parseInt(s[3]) != terrainHash()) throw new IllegalStateException("terrain rules changed");
		} catch (IOException | RuntimeException e) {
			// No island yet (a new world): search in the background, so the world opens straight away (the search
			// took about 12 s on the server thread, holding up "Loading world").
			pick(server, file, radius, () -> Island.findCenter(level, MinebloxRoyale.setting("firstCenterDistance"), 0, radius));
			return;
		}
		generate(server, file, radius, resumeFrom);
	}

	/**
	 * A match has taken the island: the next one is started now (found at once, made from pregenQuiet seconds in) and
	 * made while the match is played, more slowly so the match doesn't lag. Its map stays hidden until the match is over.
	 */
	public static void matchStarted(MinecraftServer server) {
		inMatch = true;
		quietUntil = System.currentTimeMillis() + (long) (MinebloxRoyale.setting("pregenQuiet") * 1000);
		IslandMap.hold();
		long free = freeSpace(server);
		if (free < NEW_ISLAND_MIN) {
			sameIsland = true;
			MinebloxRoyale.LOG.info("BR: {} MB free on the disk: the next match is on this same island (a new one needs {} MB free)", free >> 20, NEW_ISLAND_MIN >> 20);
			return;
		}
		sameIsland = false;
		next(server);
	}

	/** Back to the hub: the next island's map is shown, and whatever is left of it is made at full speed. */
	public static void matchOver(MinecraftServer server) {
		inMatch = false;
		PROGRESS.clear(); // a fresh countdown at the new speed
		finishGuess = 0;
		IslandMap.release(server);
		if (sameIsland)
			server.getPlayerList().broadcastSystemMessage(Component.literal("The disk is nearly full (" + String.format(Locale.ROOT, "%.1f", freeSpace(server) / 1e9)
				+ " GB free), so the next match is on the same island. Free up some space and every match gets a new island again.").withStyle(ChatFormatting.YELLOW), false);
	}

	/**
	 * A brand-new island in a random direction, far from the last one, on land never generated before (searched and
	 * generated on the background thread; the hub shows the progress, the next match waits for it).
	 */
	private static void next(MinecraftServer server) {
		if (running) return;
		ServerLevel level = server.overworld();
		float radius = MinebloxRoyale.setting("startRadius");
		Path file = server.getWorldPath(LevelResource.ROOT).resolve("minebloxroyale_island.txt");
		double a = level.getRandom().nextDouble() * Math.PI * 2, far = 2 * (radius + 64) + 600 + level.getRandom().nextInt(2000);
		double baseX = cx + Math.cos(a) * far, baseZ = cz + Math.sin(a) * far;
		lastCx = cx;
		lastCz = cz;
		haveLast = true;
		pick(server, file, radius, () -> Island.findCenter(level, baseX, baseZ, Math.cos(a), Math.sin(a), radius));
		MinebloxRoyale.LOG.info("BR: picking a new island for the next match");
	}

	/** Finds an island centre on a background thread, then generates the island from the server thread. */
	private static void pick(MinecraftServer server, Path file, float radius, Supplier<double[]> search) {
		building = null; // (the last island is finished and played on: never dropped)
		lowDiskDrop = null; // (an island dropped for disk space was let go of long ago)
		doneCount.set(0);
		total = 1;
		finished = false;
		running = true;
		Thread finder = new Thread(() -> {
			double[] c = search.get();
			server.execute(() -> {
				if (stopping) return; // the world closed while we were looking
				cx = c[0];
				cz = c[1];
				running = false;
				generate(server, file, radius, 0);
			});
		}, "MinebloxRoyale island search");
		finder.setDaemon(true);
		finder.start();
	}

	/** The island for the next match is completely generated (and its map made). */
	public static boolean ready() {
		return finished && !running;
	}

	private static void generate(MinecraftServer server, Path file, float radius, int resumeFrom) {
		ServerLevel level = server.overworld();
		started = true;
		// Chunks in a spiral from the centre outwards, covering the island plus a margin for view distance.
		int r = (int) Math.ceil((radius + 64) / 16.0), ccx = (int) Math.floor(cx / 16), ccz = (int) Math.floor(cz / 16);
		List<int[]> order = new ArrayList<>();
		for (int ring = 0; ring <= r; ring++)
			for (int dx = -ring; dx <= ring; dx++)
				for (int dz = -ring; dz <= ring; dz++)
					if (Math.max(Math.abs(dx), Math.abs(dz)) == ring && dx * dx + dz * dz <= r * r) order.add(new int[] { ccx + dx, ccz + dz });
		total = order.size();
		// Its area plus the ring of half-made chunks world generation leaves around it (up to about 11 chunks).
		building = new int[] { ccx - r - 12, ccz - r - 12, ccx + r + 12, ccz + r + 12 };
		doneCount.set(Math.min(resumeFrom, total));
		finished = false;
		// The full map (Tab) is drawn from these chunks; if it isn't saved yet, every chunk is visited again (cheap
		// once generated).
		boolean mapSaved = IslandMap.prepare(server, cx, cz, radius);
		if (!mapSaved) doneCount.set(0);
		save(file);
		if (doneCount.get() >= total) {
			MinebloxRoyale.LOG.info("BR: island at {} {} already generated", (int) cx, (int) cz);
			finished = true;
			return;
		}
		running = true;
		final int startAt = doneCount.get();
		final boolean canDrop = haveLast; // a next island, with the one being played to go back to
		worker = new Thread(() -> {
			AtomicInteger inFlight = new AtomicInteger();
			long[] nextDiskCheck = { 0 };
			boolean outOfDisk = false;
			// A gentle start: one chunk at a time for the first minute, then six (three while a match is played).
			// Someone who leaves straight away (/rc to a friend, the usual thing right after opening the game) then has
			// little half-made work for the world to finish and save before it can close (it took 25 s at full speed).
			gentleUntil = System.currentTimeMillis() + GENTLE_START_MS;
			// Each chunk is held loaded by a ticket (from the server thread) until it is completely generated; the old
			// short-lived request often ran out on a busy server, leaving gaps in the map. Failures are asked for
			// again.
			Queue<int[]> failed = new ConcurrentLinkedQueue<>();
			List<int[]> pass = new ArrayList<>(order.subList(startAt, order.size()));
			chunks: for (int attempt = 0; attempt < 6 && !pass.isEmpty() && running; attempt++) {
				if (attempt > 0) MinebloxRoyale.LOG.info("BR: asking again for {} island chunks that didn't load", pass.size());
				for (int i = 0; i < pass.size() && running; i++) {
					while (inMatch && System.currentTimeMillis() < quietUntil && running) sleep(100);
					while (inFlight.get() >= (System.currentTimeMillis() < gentleUntil ? 1 : inMatch ? 3 : 6) && running) sleep(5);
					// Never get far ahead of the disk: chunks can be made faster than Minecraft writes them, and
					// closing the world waits for every write still queued (measured: 23-86 s when closing mid-island).
					// Pause while more than MAX_QUEUED_WRITES are waiting, so closing has only a moment's worth left.
					// (Waiting for the whole queue every 16 chunks instead made an island take over 20 minutes.)
					while (queuedWrites(level) > MAX_QUEUED_WRITES && running) {
						if (diskWaitSince == 0) diskWaitSince = System.currentTimeMillis();
						sleep(20);
					}
					diskWaitSince = 0;
					if (outOfDisk(server, canDrop, nextDiskCheck)) {
						outOfDisk = true;
						break chunks;
					}
					int[] c = pass.get(i);
					inFlight.incrementAndGet();
					final boolean first = attempt == 0;
					Runnable finish = () -> {
						inFlight.decrementAndGet();
						if (first) doneCount.incrementAndGet();
					};
					server.execute(() -> {
						if (!running) { // the world is closing: don't start another chunk
							inFlight.decrementAndGet();
							return;
						}
						var pos = new ChunkPos(c[0], c[1]);
						HELD.add(pos);
						try {
							level.getChunkSource().addTicketAndLoadWithRadius(ticket, pos, 0).whenComplete((v, t) -> server.execute(() -> {
								var chunk = t == null ? level.getChunkSource().getChunk(c[0], c[1], false) : null;
								if (chunk != null) IslandMap.sample(chunk);
								else failed.add(c);
								HELD.remove(pos); // done: its ticket runs out by itself 15 s from now
								finish.run();
							}));
						} catch (RuntimeException e) {
							release(level, pos);
							failed.add(c);
							finish.run();
						}
					});
					if (i % 200 == 0) save(file);
					if (i % 250 == 0) MinebloxRoyale.LOG.info("BR: island {} of {} chunks ({} writes waiting for the disk)", doneCount.get(), total, queuedWrites(level));
				}
				while (inFlight.get() > 0 && running) sleep(20);
				pass = new ArrayList<>(failed);
				failed.clear();
				if (!pass.isEmpty()) sleep(500);
			}
			if (outOfDisk) {
				dropForDisk(server, file);
				return;
			}
			if (!pass.isEmpty()) MinebloxRoyale.LOG.warn("BR: {} island chunks never loaded; the map has gaps there", pass.size());
			save(file);
			if (running) MinebloxRoyale.LOG.info("BR: island fully generated ({} chunks)", total);
			if (running) IslandMap.finish(server);
			if (running) {
				finishedAt = System.currentTimeMillis();
				finished = true;
			}
			running = false;
		}, "MinebloxRoyale island pregen");
		worker.setDaemon(true);
		worker.start();
		MinebloxRoyale.LOG.info("BR: generating the island at {} {} in the background ({} of {} chunks done)", (int) cx, (int) cz, doneCount.get(), total);
	}

	/**
	 * Under DISK_FLOOR free (checked every 2 s on the island thread): true for a next island, which is then dropped for
	 * the one being played; a world's first island waits here until there's room again (the hub says "disk full").
	 */
	private static boolean outOfDisk(MinecraftServer server, boolean canDrop, long[] nextCheck) {
		long now = System.currentTimeMillis();
		if (now < nextCheck[0]) return false;
		nextCheck[0] = now + 2000;
		if (freeSpace(server) >= DISK_FLOOR) return false;
		if (canDrop) return true;
		MinebloxRoyale.LOG.info("BR: under {} MB free on the disk: the island waits until there's room", DISK_FLOOR >> 20);
		diskFullSince = now;
		while (running && freeSpace(server) < DISK_FLOOR + (64L << 20)) sleep(2000);
		diskFullSince = 0;
		if (running) MinebloxRoyale.LOG.info("BR: room on the disk again: the island carries on");
		return false;
	}

	/**
	 * The next island is dropped for lack of disk space (server thread): its chunks are let go and never written, and
	 * the next match is on the island just played.
	 */
	private static void dropForDisk(MinecraftServer server, Path file) {
		server.execute(() -> {
			if (stopping) return; // the world is closing: stop() forgets the half-made island
			lowDiskDrop = building;
			for (ChunkPos pos : List.copyOf(HELD)) release(server.overworld(), pos);
			building = null;
			cx = lastCx;
			cz = lastCz;
			doneCount.set(total);
			finished = true;
			sameIsland = true;
			running = false;
			save(file);
			MinebloxRoyale.LOG.info("BR: {} MB free on the disk: the next island is dropped, and the next match is on this same island", freeSpace(server) >> 20);
		});
	}

	/** The generator has been waiting over 3 s for the disk to catch up with the chunks it made (the hub says so). */
	public static boolean waitingForDisk() {
		long since = diskWaitSince;
		return since != 0 && System.currentTimeMillis() - since > 3000;
	}

	/** Chunk writes waiting for the disk (read from another thread: only a rough count is needed). */
	private static int queuedWrites(ServerLevel level) {
		IOWorker worker = ((SimpleRegionStorageAccessor) level.getChunkSource().chunkMap).getWorker();
		return ((IOWorkerAccessor) worker).getPendingWrites().size();
	}

	private static void release(ServerLevel level, ChunkPos pos) {
		if (HELD.remove(pos)) level.getChunkSource().removeTicketWithRadius(ticket, pos, 0);
	}

	/**
	 * The world is closing (server thread): stop, and let go of the chunks still being generated, so closing doesn't
	 * first finish them and the wide ring of half-made chunks around them. They're simply generated again next time.
	 */
	public static void stop(MinecraftServer server) {
		running = false;
		stopping = true;
		int dropped = HELD.size();
		for (ChunkPos pos : List.copyOf(HELD)) release(server.overworld(), pos);
		Path file = server.getWorldPath(LevelResource.ROOT).resolve("minebloxroyale_island.txt");
		// Half made, or finished so recently that its last chunks are still being written (that drains in the
		// background within about a minute): not worth the wait to save (up to a minute and a half). Its chunks are
		// pure world generation, so they're dropped (ChunkMapSaveMixin) and a new island is picked next time; the
		// island search never reuses land that was ever generated. An island finished longer ago is saved by then and
		// kept.
		boolean stillSaving = finished && System.currentTimeMillis() - finishedAt < STILL_SAVING_MS;
		if (started && (!finished || stillSaving) && building != null) {
			abandoned = building;
			try {
				Files.deleteIfExists(file);
			} catch (IOException e) {
				MinebloxRoyale.couldntSave("that the island wasn't finished", file, e);
			}
			MinebloxRoyale.LOG.info("BR: the island {} ({} of {} chunks): not saved, a new one is picked next time", finished ? "was still being saved" : "wasn't finished", doneCount.get(), total);
		} else if (started) {
			// Only once an island was picked (a crash during start-up used to save 0, 0: an island under the hub).
			save(file);
		}
		if (dropped > 0) MinebloxRoyale.LOG.info("BR: island generation paused ({} chunks in progress let go)", dropped);
	}

	/**
	 * While the world closes, or after a next island was dropped for disk space: is this chunk part of an unfinished
	 * island being dropped (not saved)? Never near the hub
	 * (islands are always far from it; this is only a safety net).
	 */
	public static boolean abandonsChunk(int x, int z) {
		return inArea(abandoned, x, z) || inArea(lowDiskDrop, x, z);
	}

	private static boolean inArea(int @Nullable [] a, int x, int z) {
		return a != null && x >= a[0] && z >= a[1] && x <= a[2] && z <= a[3] && Math.max(Math.abs(x), Math.abs(z)) > 64;
	}

	private static int terrainHash() {
		return Sheets.TERRAIN.hashCode();
	}

	private static void save(Path file) {
		if (abandoned != null) return; // a dropped island is forgotten, not saved (the worker's last save comes after stop)
		try {
			Files.writeString(file, cx + " " + cz + " " + doneCount.get() + " " + terrainHash());
		} catch (IOException e) {
			MinebloxRoyale.couldntSave("island progress", file, e);
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
