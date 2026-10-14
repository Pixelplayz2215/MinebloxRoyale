import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Design sheets -> preflight + Java records.
 *   preflight <designDir>          lists every unfilled cell and unresolved cross-sheet reference
 *   gen <designDir> <outDir>       writes gg/minebloxroyale/sheets/*.java (runs preflight first)
 * Column types: string, int, float, bool, color (#RRGGBB), enum:a|b, ref:<sheet> (ref:<sheet>? allows "-").
 */
public class SheetTool {
	record Sheet(String name, String cls, Map<String, String> columns, List<Map<String, Object>> rows) {}

	public static void main(String[] args) throws IOException {
		Map<String, Sheet> sheets = load(Path.of(args[1]));
		List<String> problems = preflight(sheets);
		int cells = sheets.values().stream().mapToInt(s -> s.rows.size() * s.columns.size()).sum();
		if (!problems.isEmpty()) {
			problems.forEach(p -> System.out.println("PREFLIGHT: " + p));
			System.out.println("PREFLIGHT FAILED: " + problems.size() + " problem(s) in " + cells + " cells");
			System.exit(1);
		}
		System.out.println("PREFLIGHT OK: " + sheets.size() + " sheets, " + cells + " cells checked");
		if (args[0].equals("gen")) gen(sheets, Path.of(args[2]));
	}

	static Map<String, Sheet> load(Path dir) throws IOException {
		Map<String, Sheet> out = new LinkedHashMap<>();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
				@SuppressWarnings("unchecked")
				Map<String, Object> root = (Map<String, Object>) new Json(Files.readString(f, StandardCharsets.UTF_8)).value();
				@SuppressWarnings("unchecked")
				Map<String, String> cols = (Map<String, String>) (Map<?, ?>) root.get("columns");
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> rows = (List<Map<String, Object>>) (List<?>) root.get("rows");
				Sheet s = new Sheet((String) root.get("sheet"), (String) root.get("class"), cols, rows);
				out.put(s.name, s);
			}
		}
		return out;
	}

	static List<String> preflight(Map<String, Sheet> sheets) {
		List<String> p = new ArrayList<>();
		for (Sheet s : sheets.values()) {
			if (s.cls == null || s.columns == null || s.rows == null) { p.add(s.name + ": missing class/columns/rows"); continue; }
			if (!s.columns.containsKey("id")) p.add(s.name + ": no id column");
			java.util.Set<Object> ids = new java.util.HashSet<>();
			for (Map<String, Object> row : s.rows) {
				Object id = row.get("id");
				String where = s.name + "[" + id + "]";
				if (!ids.add(id)) p.add(where + ": duplicate id");
				for (String extra : row.keySet()) if (!s.columns.containsKey(extra)) p.add(where + "." + extra + ": not a column");
				for (var col : s.columns.entrySet()) {
					Object v = row.get(col.getKey());
					String type = col.getValue();
					String cell = where + "." + col.getKey();
					if (v == null || (v instanceof String str && str.isBlank())) { p.add(cell + ": unfilled"); continue; }
					switch (type.replaceAll("[:?].*", "")) {
						case "string" -> { if (!(v instanceof String)) p.add(cell + ": not text"); }
						case "int" -> { if (!(v instanceof Double d) || d != Math.rint(d)) p.add(cell + ": not a whole number"); }
						case "float" -> { if (!(v instanceof Double)) p.add(cell + ": not a number"); }
						case "bool" -> { if (!(v instanceof Boolean)) p.add(cell + ": not true/false"); }
						case "color" -> { if (!(v instanceof String c) || !c.matches("#[0-9A-Fa-f]{6}")) p.add(cell + ": not #RRGGBB"); }
						case "enum" -> {
							List<String> allowed = List.of(type.substring(5).split("\\|"));
							if (!allowed.contains(v)) p.add(cell + ": '" + v + "' not one of " + allowed);
						}
						case "ref" -> {
							boolean optional = type.endsWith("?");
							String target = type.substring(4).replace("?", "");
							Sheet t = sheets.get(target);
							if (t == null) p.add(cell + ": refers to missing sheet " + target);
							else if (!(optional && "-".equals(v)) && t.rows.stream().noneMatch(r -> v.equals(r.get("id"))))
								p.add(cell + ": '" + v + "' not found in " + target);
						}
						default -> p.add(cell + ": unknown column type " + type);
					}
				}
			}
		}
		// Cross-sheet rules that a single column can't express.
		Sheet loot = sheets.get("loot");
		if (loot != null) for (var r : loot.rows) {
			String kind = (String) r.get("kind");
			if ("weapon".equals(kind) && "-".equals(r.get("weapon"))) p.add("loot[" + r.get("id") + "]: weapon row without weapon");
			if ("consumable".equals(kind) && "-".equals(r.get("consumable"))) p.add("loot[" + r.get("id") + "]: consumable row without consumable");
			if ("ammo".equals(kind) && "-".equals(r.get("ammo"))) p.add("loot[" + r.get("id") + "]: ammo row without ammo");
		}
		Sheet obby = sheets.get("obby");
		if (obby != null) {
			double prevSize = -1, prevSizeZ = 1;
			for (var r : obby.rows) {
				double dx = (Double) r.get("dx"), dy = (Double) r.get("dy"), dz = (Double) r.get("dz");
				if (prevSize >= 0) {
					double gap = -dx - prevSize;
					double side = Math.max(0, Math.abs(dz) - Math.floor(prevSizeZ / 2));
					double reach = Math.sqrt(gap * gap + side * side);
					if (gap > 3 || reach > 3.7 || (dy > 0 && reach > 3.0))
						p.add("obby[" + r.get("id") + "]: jump of " + String.format("%.1f", reach) + " blocks" + (dy > 0 ? " while climbing" : "") + " is too far");
				}
				prevSize = (Double) r.get("sizeX");
				prevSizeZ = (Double) r.get("sizeZ");
			}
		}
		Sheet media = sheets.get("media");
		if (media != null) for (String use : List.of("billboard") /* storm_music: none (creator: no storm music); hub_music: the mod's own hub_loop.ogg now */)
			if (media.rows.stream().noneMatch(r -> use.equals(r.get("use")))) p.add("media: nothing for use=" + use);
		Sheet emotes = sheets.get("emotes"), keys = sheets.get("emote_keys");
		if (emotes != null && keys != null) for (var e : emotes.rows) {
			long n = keys.rows.stream().filter(k -> e.get("id").equals(k.get("emote"))).count();
			if (n < 2) p.add("emotes[" + e.get("id") + "]: needs at least 2 keyframes in emote_keys, has " + n);
		}
		return p;
	}

	static void gen(Map<String, Sheet> sheets, Path out) throws IOException {
		Path pkg = out.resolve("gg/minebloxroyale/sheets");
		Files.createDirectories(pkg);
		StringBuilder all = new StringBuilder("package gg.minebloxroyale.sheets;\n\nimport java.util.List;\nimport java.util.Map;\nimport java.util.function.Function;\nimport java.util.stream.Collectors;\n\n/** Generated from design/*.json by tools/SheetTool. Do not edit. */\npublic final class Sheets {\n\tprivate Sheets() {}\n");
		for (Sheet s : sheets.values()) {
			StringBuilder rec = new StringBuilder("package gg.minebloxroyale.sheets;\n\n/** Generated from design/" + s.name + ".json. Do not edit. */\npublic record " + s.cls + "(");
			List<String> fields = new ArrayList<>();
			for (var c : s.columns.entrySet()) fields.add(javaType(c.getValue()) + " " + c.getKey());
			rec.append(String.join(", ", fields)).append(") {}\n");
			Files.writeString(pkg.resolve(s.cls + ".java"), rec.toString());
			String constant = s.name.toUpperCase();
			// Each sheet's rows are made by its own class, a few hundred rows a method: Java caps a method's code (and a
			// class's constants), and one class making every sheet's rows ran out (the Blender emote curves).
			String holder = s.cls + "Rows";
			StringBuilder h = new StringBuilder("package gg.minebloxroyale.sheets;\n\nimport java.util.ArrayList;\nimport java.util.List;\n\n/** Generated from design/" + s.name
				+ ".json: its rows. Do not edit. */\nfinal class " + holder + " {\n\tprivate " + holder + "() {}\n");
			int per = 250, parts = Math.max(1, (s.rows.size() + per - 1) / per);
			h.append("\n\tstatic List<").append(s.cls).append("> all() {\n\t\tList<").append(s.cls).append("> out = new ArrayList<>(").append(s.rows.size()).append(");\n");
			for (int i = 0; i < parts; i++) h.append("\t\tpart").append(i).append("(out);\n");
			h.append("\t\treturn List.copyOf(out);\n\t}\n");
			for (int i = 0; i < parts; i++) {
				h.append("\n\tprivate static void part").append(i).append("(List<").append(s.cls).append("> out) {\n");
				for (var r : s.rows.subList(i * per, Math.min(s.rows.size(), (i + 1) * per))) {
					List<String> vals = new ArrayList<>();
					for (var c : s.columns.entrySet()) vals.add(literal(c.getValue(), r.get(c.getKey())));
					h.append("\t\tout.add(new ").append(s.cls).append("(").append(String.join(", ", vals)).append("));\n");
				}
				h.append("\t}\n");
			}
			h.append("}\n");
			Files.writeString(pkg.resolve(holder + ".java"), h.toString());
			all.append("\n\tpublic static final List<").append(s.cls).append("> ").append(constant).append(" = ").append(holder).append(".all();\n");
			all.append("\tpublic static final Map<String, ").append(s.cls).append("> ").append(constant).append("_BY_ID = ")
				.append(constant).append(".stream().collect(Collectors.toUnmodifiableMap(").append(s.cls).append("::id, Function.identity()));\n");
		}
		all.append("}\n");
		Files.writeString(pkg.resolve("Sheets.java"), all.toString());
		System.out.println("GEN: " + sheets.size() + " sheets -> " + pkg);
	}

	static String javaType(String t) {
		return switch (t.replaceAll("[:?].*", "")) {
			case "int", "color" -> "int";
			case "float" -> "float";
			case "bool" -> "boolean";
			default -> "String";
		};
	}

	static String literal(String t, Object v) {
		return switch (t.replaceAll("[:?].*", "")) {
			case "int" -> String.valueOf(((Double) v).intValue());
			case "float" -> ((Double) v).floatValue() + "F";
			case "bool" -> v.toString();
			case "color" -> "0x" + ((String) v).substring(1).toUpperCase();
			default -> "\"" + ((String) v).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		};
	}

	/** Small JSON reader: objects keep key order; numbers are Double. */
	static final class Json {
		private final String s;
		private int i;
		Json(String s) { this.s = s; }
		Object value() {
			ws();
			char c = s.charAt(i);
			if (c == '{') { i++; Map<String, Object> m = new LinkedHashMap<>(); ws(); if (s.charAt(i) == '}') { i++; return m; }
				while (true) { ws(); String k = str(); ws(); i++; m.put(k, value()); ws(); if (s.charAt(i++) == '}') return m; } }
			if (c == '[') { i++; List<Object> l = new ArrayList<>(); ws(); if (s.charAt(i) == ']') { i++; return l; }
				while (true) { l.add(value()); ws(); if (s.charAt(i++) == ']') return l; } }
			if (c == '"') return str();
			if (s.startsWith("true", i)) { i += 4; return true; }
			if (s.startsWith("false", i)) { i += 5; return false; }
			if (s.startsWith("null", i)) { i += 4; return null; }
			int st = i; while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
			return Double.parseDouble(s.substring(st, i));
		}
		String str() {
			StringBuilder b = new StringBuilder(); i++;
			while (true) { char c = s.charAt(i++); if (c == '"') return b.toString();
				if (c == '\\') { char e = s.charAt(i++); switch (e) { case 'n' -> b.append('\n'); case 't' -> b.append('\t');
					case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; } default -> b.append(e); } }
				else b.append(c); }
		}
		void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
	}
}
