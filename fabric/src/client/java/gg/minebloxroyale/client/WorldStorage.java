package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.client.mixin.MinecraftLevelSourceAccessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.Nullable;

/**
 * (2.0.0, creator: "an option in the menu ... to change the path drive of cache/island building that way it leaves
 * everyone's C: drive alone if changed ... for storage issues", moved "Right away") Where this game folder's worlds are
 * kept: the game folder's own saves folder (Minecraft's), or a folder on another drive, X:\Mineblox Royale\<game
 * folder>\saves. The Mineblox Royale world is where every island is built (100 to 300 MB, more while the next one is
 * made), so a nearly full C: can be left alone. Picked in Options > World storage (StorageScreen): the world saves and
 * closes, is copied there and checked, is removed from the old place, and opens again from the new one.
 *
 * Kept per game folder in %APPDATA%\MinebloxRoyale\storage.txt ("<game folder>\t<folder>"), outside every launcher's
 * folders, so an update of the game never forgets it. Melty's own copy of Minecraft and Java stays where Melty keeps it.
 */
public final class WorldStorage {
	/** Room a drive needs beyond the worlds themselves: the next island is built there. */
	static final long HEADROOM = 2L << 30;
	/** The folder in use (its worlds in saves/ inside it); the game folder itself unless another one was picked. */
	private static volatile @Nullable Path current;
	/** The folder picked can't be found (a drive that isn't plugged in): the world waits for it (StorageScreen). */
	private static volatile @Nullable Path missing;
	private static boolean shownMissing;
	/** Where the world is being moved (null: no move), and how far it's got. */
	private static volatile @Nullable Path moveTo;
	private static volatile boolean moveStarted, moveDone;
	static volatile long movedBytes, totalBytes;
	static volatile @Nullable String moveError;
	/** Moved and remembered, but Minecraft couldn't be pointed there: the world waits for a restart (opens from there). */
	private static volatile boolean restartNeeded;
	/** Once back in the world after a move: a line in the chat saying where it's kept now. */
	private static volatile @Nullable String tell;

	private WorldStorage() {
	}

	/** One place the worlds can be kept: its folder, its name ("D:"), the space free there, and why not, if it can't be. */
	record Place(Path folder, String name, long free, boolean inUse, @Nullable String why) {
	}

	public static void init() {
		ClientLifecycleEvents.CLIENT_STARTED.register(WorldStorage::atStart);
		ClientTickEvents.END_CLIENT_TICK.register(WorldStorage::tick);
		// Options > World storage (a button in the corner of Minecraft's Options screen)
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof OptionsScreen)) return;
			Screens.getWidgets(screen).add(Button.builder(Component.literal("World storage: " + drive(folder())), b -> mc.gui.setScreen(new StorageScreen(screen)))
				.bounds(6, 6, 130, 20).build());
		});
	}

	static Path gameDir() {
		return Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
	}

	/** The folder the worlds are kept in now (its saves/ inside). */
	static Path folder() {
		Path c = current;
		return c != null ? c : gameDir();
	}

	static boolean isGameFolder(Path folder) {
		return folder.toAbsolutePath().normalize().equals(gameDir());
	}

	static Path saves(Path folder) {
		return folder.resolve("saves");
	}

	/** "D:" for a drive's folder, "the game folder (C:)" for the game folder's own. */
	static String name(Path folder) {
		String drive = drive(folder);
		return isGameFolder(folder) ? "the game folder (" + drive + ")" : drive;
	}

	/** "in the game folder (C:)" or "on D:", for a sentence. */
	static String where(Path folder) {
		return (isGameFolder(folder) ? "in " : "on ") + name(folder);
	}

	static String drive(Path folder) {
		Path root = folder.toAbsolutePath().getRoot();
		return root == null ? folder.toString() : root.toString().replaceAll("[\\\\/]+$", "");
	}

	/** The folder on a drive for this game folder: X:\Mineblox Royale\<its name and a short code>. */
	static Path folderOn(Path root) {
		Path g = gameDir();
		String name = "game";
		for (Path p = g; p != null && p.getFileName() != null; p = p.getParent()) {
			String n = p.getFileName().toString();
			if (!n.equalsIgnoreCase(".minecraft") && !n.equalsIgnoreCase("minecraft")) {
				name = n;
				break;
			}
		}
		name = name.replaceAll("[^A-Za-z0-9 ._-]", "_");
		String code = Integer.toHexString(g.toString().toLowerCase(Locale.ROOT).hashCode() & 0xFFFFFF);
		return root.resolve("Mineblox Royale").resolve(name + " " + code);
	}

	// ---- the setting ----

	private static Path file() {
		return PlayerFiles.dir().resolve("storage.txt");
	}

	/** The folder picked for this game folder, or null for the game folder's own. */
	private static @Nullable Path picked() {
		try {
			String me = gameDir().toString();
			for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
				String[] a = line.split("\t");
				if (a.length == 2 && a[0].equalsIgnoreCase(me)) return Path.of(a[1]).toAbsolutePath().normalize();
			}
		} catch (IOException | RuntimeException ignored) {
		}
		return null;
	}

	/** Remember the folder for this game folder (the game folder itself: forget it). */
	private static void remember(Path folder) throws IOException {
		String me = gameDir().toString();
		List<String> lines = new ArrayList<>();
		try {
			for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8))
				if (!line.split("\t")[0].equalsIgnoreCase(me)) lines.add(line); // (other game folders on this PC)
		} catch (IOException ignored) {
		}
		if (!isGameFolder(folder)) lines.add(me + "\t" + folder.toAbsolutePath().normalize());
		Files.createDirectories(file().getParent());
		Files.write(file(), lines, StandardCharsets.UTF_8);
	}

	/** Minecraft's worlds from this folder on (no world may be open). */
	private static void use(Minecraft mc, Path folder) throws IOException {
		Files.createDirectories(saves(folder));
		((MinecraftLevelSourceAccessor) mc).minebloxroyale$setLevelSource(LevelStorageSource.createDefault(saves(folder)));
		current = isGameFolder(folder) ? null : folder;
		MinebloxRoyale.LOG.info("BR: worlds kept in {}", saves(folder));
	}

	/** At start, before the world opens: the folder picked, if it can be found. */
	private static void atStart(Minecraft mc) {
		Path f = picked();
		if (f == null) return;
		Path root = f.getRoot();
		if (root == null || !Files.isDirectory(root)) {
			missing = f;
			AutoWorld.hold(); // (the world waits: a fresh one here would be a second world)
			MinebloxRoyale.LOG.info("BR: the worlds are kept in {}, but {} can't be found", f, drive(f));
			return;
		}
		try {
			if (!Files.isDirectory(f)) {
				Files.createDirectories(f);
				readme(f);
			}
			use(mc, f);
		} catch (IOException | RuntimeException e) {
			missing = f;
			AutoWorld.hold();
			MinebloxRoyale.LOG.info("BR: couldn't use {} for the worlds ({})", f, e.toString());
		}
	}

	static @Nullable Path missing() {
		return missing;
	}

	/** The missing drive is back (Try again): use it and open the world. False if it still can't be found. */
	static boolean retryMissing(Minecraft mc) {
		Path f = missing;
		if (f == null) return true;
		try {
			if (f.getRoot() == null || !Files.isDirectory(f.getRoot())) return false;
			Files.createDirectories(f);
			use(mc, f);
		} catch (IOException | RuntimeException e) {
			return false;
		}
		missing = null;
		AutoWorld.reopen(mc);
		return true;
	}

	/** Use this PC's game folder from now on (the drive that can't be found keeps its copy). */
	static void forgetMissing(Minecraft mc) {
		try {
			remember(gameDir());
		} catch (IOException e) {
			MinebloxRoyale.LOG.info("BR: couldn't save the world storage setting ({})", e.toString());
		}
		missing = null;
		current = null;
		AutoWorld.reopen(mc);
	}

	// ---- the places ----

	/** How much the worlds kept now take. */
	static long size(Path folder) {
		try (Stream<Path> s = Files.walk(saves(folder))) {
			return s.filter(Files::isRegularFile).mapToLong(p -> {
				try {
					return Files.size(p);
				} catch (IOException e) {
					return 0;
				}
			}).sum();
		} catch (IOException | RuntimeException e) {
			return 0;
		}
	}

	/** The game folder's own, then every other drive that can be written to, with the space free on each. */
	static List<Place> places(long need) {
		List<Place> out = new ArrayList<>();
		Path now = folder().toAbsolutePath().normalize(), g = gameDir();
		Path gameRoot = g.getRoot();
		out.add(place(g, gameRoot, now, need));
		for (Path root : FileSystems.getDefault().getRootDirectories()) {
			if (gameRoot != null && root.toString().equalsIgnoreCase(gameRoot.toString())) continue;
			Place p = place(folderOn(root), root, now, need);
			if (p != null) out.add(p);
		}
		out.removeIf(p -> p == null);
		return out;
	}

	private static @Nullable Place place(Path folder, @Nullable Path root, Path now, long need) {
		long free;
		try {
			if (root == null) return null;
			FileStore store = Files.getFileStore(root);
			if (store.isReadOnly() || store.getTotalSpace() <= 0) return null;
			free = store.getUsableSpace();
		} catch (IOException | RuntimeException e) {
			return null; // (a drive with no disc in it, a card reader with no card)
		}
		boolean inUse = folder.toAbsolutePath().normalize().equals(now);
		String why = inUse ? null : free < need + HEADROOM ? "not enough space" : null;
		return new Place(folder, name(folder), free, inUse, why);
	}

	/** Why the world can't move now (null: it can). */
	static @Nullable String cantMove(Minecraft mc) {
		if (moveTo != null) return "A move is already happening.";
		if (mc.level != null && mc.getSingleplayerServer() == null) return "You're in a friend's room: /leave first, then move your world.";
		if (mc.level != null && ClientState.inMatch()) return "Finish the match first: your world can move from the hub.";
		return null;
	}

	// ---- the move ----

	/** Move the worlds to this folder now: the world saves and closes, then the copy starts (tick). */
	static void move(Minecraft mc, Path to) {
		moveTo = to;
		moveStarted = moveDone = false;
		movedBytes = totalBytes = 0;
		moveError = null;
		AutoWorld.hold();
		MinebloxRoyale.LOG.info("BR: moving the worlds from {} to {}", saves(folder()), saves(to));
		if (mc.level != null) mc.disconnectFromWorld(Component.literal("Moving your world to " + name(to)));
		mc.gui.setScreen(StorageScreen.moving());
	}

	static boolean moving() {
		return moveTo != null;
	}

	static boolean restartNeeded() {
		return restartNeeded;
	}

	private static void tick(Minecraft mc) {
		Path to = moveTo;
		if (to != null) {
			if (!(mc.gui.screen() instanceof StorageScreen) && mc.level == null) mc.gui.setScreen(StorageScreen.moving());
			if (!moveStarted && mc.level == null && mc.getSingleplayerServer() == null) { // (the world is saved and closed)
				moveStarted = true;
				Path from = folder();
				Thread t = new Thread(() -> copy(from, to), "Mineblox Royale world move");
				t.setDaemon(false); // (a move is never cut off by the game closing)
				t.start();
			}
			if (moveDone) {
				moveTo = null;
				if (moveError == null) {
					try {
						use(mc, to);
					} catch (IOException e) {
						restartNeeded = true; // (the old copy is gone: opening from there would make a fresh world)
						moveError = "Your world moved to " + name(to) + ". Restart the game to open it from there.";
					}
				}
				if (moveError == null) {
					tell = "Your world is kept " + where(to) + " now (Options > World storage).";
					AutoWorld.reopen(mc);
				}
				// (failed: StorageScreen says why, and OK opens the world from where it was)
			}
		}
		if (missing != null && !shownMissing && mc.level == null && mc.gui.overlay() == null
			&& (mc.gui.screen() == null || mc.gui.screen() instanceof TitleScreen)) {
			shownMissing = true;
			mc.gui.setScreen(StorageScreen.missingDrive());
		}
		String t = tell;
		if (t != null && mc.player != null) {
			tell = null;
			mc.player.sendSystemMessage(Component.literal(t).withStyle(ChatFormatting.YELLOW));
		}
	}

	/**
	 * The move (its own thread): every world in from's saves folder copied to to's and checked file by file
	 * (WorldMove), the new place remembered, then the old copies removed. On any problem the new copies are removed and
	 * the old ones stay as they were.
	 */
	private static void copy(Path from, Path to) {
		Path src = saves(from), dst = saves(to);
		try {
			totalBytes = WorldMove.list(src).bytes();
			WorldMove.copy(src, dst, HEADROOM, name(to), done -> movedBytes = done);
			try {
				if (!isGameFolder(to)) readme(to);
				remember(to);
			} catch (IOException | RuntimeException e) {
				WorldMove.removeCopies(src, dst); // (not remembered: the copies come out again)
				throw e;
			}
		} catch (IOException | RuntimeException e) {
			MinebloxRoyale.LOG.warn("BR: the world move failed; the world stays in {}", src, e);
			moveError = e.getMessage() == null ? e.toString() : e.getMessage();
			moveDone = true;
			return;
		}
		// Copied, checked and remembered: the old copies go (only the worlds that were copied).
		try {
			WorldMove.removeOld(src, isGameFolder(from));
			if (!isGameFolder(from)) {
				Files.deleteIfExists(from.resolve("Where these worlds are from.txt"));
				WorldMove.deleteIfEmpty(from);
				WorldMove.deleteIfEmpty(from.getParent());
			}
		} catch (IOException | RuntimeException e) {
			MinebloxRoyale.LOG.info("BR: the world moved, but some of the old copy in {} couldn't be removed ({})", src, e.toString());
		}
		MinebloxRoyale.LOG.info("BR: moved {} of worlds from {} to {}", gb(totalBytes), src, dst);
		moveDone = true;
	}

	/** A note in the folder on the other drive saying whose worlds these are. */
	private static void readme(Path folder) {
		try {
			Files.writeString(folder.resolve("Where these worlds are from.txt"), "Mineblox Royale keeps the worlds of this game folder here (picked in Options > World storage):\r\n"
				+ gameDir() + "\r\nTo move them back, pick the game folder there.\r\n", StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException ignored) {
		}
	}

	/** The move failed and its message has been read: the world opens again from where it was. */
	static void afterFailedMove(Minecraft mc) {
		moveError = null;
		if (restartNeeded) mc.gui.setScreen(new TitleScreen()); // (held until the game is started again)
		else AutoWorld.reopen(mc);
	}

	static String gb(long bytes) {
		return WorldMove.gb(bytes);
	}
}
