package gg.minebloxroyale.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;
import java.util.stream.Stream;

/**
 * (2.0.0) The files side of moving the worlds to another folder (WorldStorage), with no game classes in it: copy every
 * world from one saves folder to another and check each file, then remove the old copies. Nothing is removed until the
 * copy is complete and checked; a copy that fails is taken back out again.
 */
final class WorldMove {
	private WorldMove() {
	}

	/** Every file under a folder, and how much they take. */
	record Files_(List<Path> files, long bytes) {
	}

	static Files_ list(Path saves) throws IOException {
		List<Path> files;
		try (Stream<Path> s = Files.walk(saves)) {
			files = s.filter(Files::isRegularFile).toList();
		}
		long total = 0;
		for (Path f : files) total += Files.size(f);
		return new Files_(files, total);
	}

	/**
	 * Copies the worlds in src (a saves folder) into dst, telling progress the bytes copied so far. A world already in
	 * dst with the same name is kept under another name. Throws (with the new copies removed) if dst hasn't room for
	 * the worlds and headroom more, or anything fails.
	 */
	static void copy(Path src, Path dst, long headroom, String dstName, LongConsumer progress) throws IOException {
		Files_ all = list(src);
		Files.createDirectories(dst);
		long free = Files.getFileStore(dst).getUsableSpace();
		if (free < all.bytes() + headroom)
			throw new IOException(dstName + " has " + gb(free) + " free; the world needs " + gb(all.bytes() + headroom) + " (with room to build the next island)");
		try (Stream<Path> worlds = Files.list(src)) {
			for (Path w : worlds.toList()) {
				Path there = dst.resolve(w.getFileName().toString());
				if (!Files.exists(there)) continue;
				String base = w.getFileName() + " (older copy " + LocalDate.now();
				Path aside = dst.resolve(base + ")");
				for (int i = 2; Files.exists(aside); i++) aside = dst.resolve(base + " " + i + ")");
				Files.move(there, aside);
			}
		}
		List<Path> made = new ArrayList<>();
		try {
			long done = 0;
			for (Path f : all.files()) {
				Path d = dst.resolve(src.relativize(f).toString());
				Files.createDirectories(d.getParent());
				Files.copy(f, d, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
				made.add(d);
				done += Files.size(f);
				progress.accept(done);
			}
			for (Path f : all.files()) {
				Path d = dst.resolve(src.relativize(f).toString());
				if (!Files.isRegularFile(d) || Files.size(d) != Files.size(f)) throw new IOException("a copied file didn't match: " + src.relativize(f));
			}
		} catch (IOException | RuntimeException e) {
			for (int i = made.size() - 1; i >= 0; i--) {
				try {
					Files.deleteIfExists(made.get(i));
				} catch (IOException ignored) {
				}
			}
			throw e instanceof IOException io ? io : new IOException(e.toString(), e);
		}
	}

	/** Removes from dst the copies of src's worlds (and nothing else there). */
	static void removeCopies(Path src, Path dst) throws IOException {
		try (Stream<Path> worlds = Files.list(src)) {
			for (Path w : worlds.toList()) {
				Path d = dst.resolve(w.getFileName().toString());
				if (Files.exists(d)) deleteTree(d);
			}
		}
	}

	/** Removes every world in src (a saves folder), and src itself too when keepFolder is false. */
	static void removeOld(Path src, boolean keepFolder) throws IOException {
		try (Stream<Path> worlds = Files.list(src)) {
			for (Path w : worlds.toList()) deleteTree(w);
		}
		if (!keepFolder) Files.deleteIfExists(src);
	}

	/** A folder and what's in it (links are removed, never followed). */
	static void deleteTree(Path p) throws IOException {
		try (Stream<Path> s = Files.walk(p)) {
			for (Path q : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(q);
		}
	}

	static void deleteIfEmpty(Path dir) {
		if (dir == null) return;
		try (Stream<Path> s = Files.list(dir)) {
			if (s.findAny().isEmpty()) Files.deleteIfExists(dir);
		} catch (IOException | RuntimeException ignored) {
		}
	}

	static String gb(long bytes) {
		if (bytes < 1L << 30) return Math.max(1, Math.round(bytes / 1048576.0)) + " MB";
		return String.format(Locale.ROOT, bytes < 10L << 30 ? "%.1f GB" : "%.0f GB", bytes / 1073741824.0);
	}
}
