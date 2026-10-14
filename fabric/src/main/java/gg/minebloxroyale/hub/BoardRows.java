package gg.minebloxroyale.hub;

import gg.minebloxroyale.sheets.ConsumableDef;
import gg.minebloxroyale.sheets.HubPieceDef;
import gg.minebloxroyale.sheets.RarityDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hub boards filled from the design sheets, one row per entry with the item's picture beside its name (creator):
 * rarity_board: each gun rarity (rarities sheet) with an assault rifle in that rarity's colour, how much more damage it
 * does and how much faster it reloads; heals_board: each heal and shield (consumables sheet) with the item itself,
 * what it gives and how long it takes. Label: title | line above the rows | notes under the rows...
 */
final class BoardRows {
	/** Advance in pixels of each printable ASCII character (space to ~) in Minecraft's default font; bold adds 1. */
	private static final String WIDTHS = "42466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247";
	private static final String GUN_PICTURE = "minebloxroyale:gun_assault_rifle";

	/**
	 * One row: the item shown beside it (with a rarity colour, or -1), the name in its colour, then the rest in white.
	 */
	private record Row(String item, int tint, int nameColor, String name, String rest) {
	}

	private BoardRows() {
	}

	static void build(ServerLevel level, HubPieceDef p, int hubY, BlockState block) {
		HubBuilder.boardFrame(level, p, hubY, block);
		MinecraftServer server = level.getServer();
		String[] lines = p.label().split(Pattern.quote(" | "));
		double[] t = HubBuilder.boardText(p);
		HubBuilder.label(server, t[0], t[1], t[2], lines[0], "gold", (float) t[3] * 1.7F, "fixed", p.facing(), false);
		double icon = 1.3, margin = 0.7, usable = p.width() - 2 * margin - icon;
		// The line above the rows and the notes under them: all the same size, as big as fits (at most 0.8 of a line).
		int widestNote = 1;
		for (int i = 1; i < lines.length; i++) widestNote = Math.max(widestNote, width(lines[i]));
		float note = (float) Math.min(t[3] * 0.8, (p.width() - 2 * margin) * 40 / widestNote);
		List<Row> rows = p.kind().equals("rarity_board") ? rarityRows() : healRows();
		// Lines spread down the whole board (at most 1.3 blocks apart).
		int h = Math.round(p.height() / 2F) + 1;
		double top = HubBuilder.boardLineY(p, 0), bottom = hubY + p.y() - h + 1.6;
		double step = Math.min(1.3, (top - bottom) / Math.max(1, lines.length - 1 + rows.size()));
		int line = 1;
		if (lines.length > 1) label(server, top - step * line++, p, t, List.<String[]>of(new String[] { lines[1], "#B8C4D6" }), note);
		// The rows' text as big as fits beside the pictures (never bigger than the board's normal lines).
		int widest = 1;
		for (Row r : rows) widest = Math.max(widest, width(r.name() + "  " + r.rest()));
		float scale = (float) Math.min(t[3], usable * 40 / widest);
		double left = -p.width() / 2.0 + margin;
		for (Row r : rows) {
			double y = top - step * line++;
			double[] at = along(p, t, left + icon / 2, 0.08);
			item(server, at[0], y + 4.5 * scale / 40, at[1], r, p.facing(), (float) Math.min(Math.min(0.9, step * 0.8), scale * 0.5));
			double w = width(r.name() + "  " + r.rest()) * scale / 40;
			List<String[]> parts = List.<String[]>of(new String[] { r.name(), String.format(Locale.ROOT, "#%06X", r.nameColor() & 0xFFFFFF) }, new String[] { "  " + r.rest(), "white" });
			double[] tx = along(p, t, left + icon + w / 2, 0);
			rich(server, tx[0], y, tx[1], parts, scale, p.facing());
		}
		for (int i = 2; i < lines.length; i++) label(server, top - step * line++, p, t, List.<String[]>of(new String[] { lines[i], "#B8C4D6" }), note);
	}

	/** Gun rarities, from the rarities sheet. */
	private static List<Row> rarityRows() {
		List<Row> out = new ArrayList<>();
		for (RarityDef r : Sheets.RARITIES) {
			int dmg = Math.round((r.damageMult() - 1) * 100), reload = Math.round((1 - r.reloadMult()) * 100);
			String rest = dmg == 0 && reload == 0 ? "base damage and reload" : "+" + dmg + "% damage, " + reload + "% faster reload";
			out.add(new Row(GUN_PICTURE, r.color(), r.color(), r.name(), rest));
		}
		return out;
	}

	/** Heals and shields, from the consumables sheet: what each gives and how long it takes to use. */
	private static List<Row> healRows() {
		List<Row> out = new ArrayList<>();
		for (ConsumableDef c : Sheets.CONSUMABLES) {
			int amount = Math.round(c.amount()), cap = Math.round(c.cap());
			String what = switch (c.kind()) {
				case "heal" -> "+" + amount + " health (max " + cap + ")";
				case "shield" -> "+" + amount + " shield (max " + cap + ")";
				default -> c.overTimeTicks() > 0 ? "+" + amount + " health & shield, slowly" : "fills health, then shield";
			};
			out.add(new Row("minebloxroyale:" + c.id(), -1, c.color(), c.name(), what + ", " + seconds(c.useTicks()) + " s"));
		}
		return out;
	}

	/** Ticks as seconds, to the nearest half second ("3.5", "10"). */
	private static String seconds(int ticks) {
		double s = Math.round(ticks / 10.0) / 2.0;
		return s == Math.floor(s) ? Integer.toString((int) s) : String.format(Locale.ROOT, "%.1f", s);
	}

	/** Width in font pixels of bold text (as the boards draw it). */
	static int width(String s) {
		int w = 0;
		for (char c : s.toCharArray()) w += (c >= 32 && c < 32 + WIDTHS.length() ? WIDTHS.charAt(c - 32) - '0' : 6) + 1;
		return w;
	}

	/**
	 * A point on the board's face: `side` blocks to the reader's right of its middle, `out` blocks in front of the
	 * text. Returns {x, z}.
	 */
	private static double[] along(HubPieceDef p, double[] t, double side, double out) {
		return switch (p.facing()) {
			case "north" -> new double[] { t[0] - side, t[2] - out };
			case "west" -> new double[] { t[0] - out, t[2] + side };
			case "east" -> new double[] { t[0] + out, t[2] - side };
			default -> new double[] { t[0] + side, t[2] + out };
		};
	}

	/** One line of text centred on the board, at height y. */
	private static void label(MinecraftServer server, double y, HubPieceDef p, double[] t, List<String[]> parts, float scale) {
		rich(server, t[0], y, t[2], parts, scale, p.facing());
	}

	/** A line of text made of parts, each in its own colour ({text, colour}), on the board's face. */
	private static void rich(MinecraftServer server, double x, double y, double z, List<String[]> parts, float scale, String facing) {
		server.overworld().getChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
		StringBuilder extra = new StringBuilder();
		for (String[] part : parts)
			extra.append(extra.length() == 0 ? "" : ",").append("{text:\"").append(HubBuilder.json(part[0])).append("\",color:\"").append(part[1]).append("\"}");
		HubBuilder.command(server, String.format(Locale.ROOT,
			"summon minecraft:text_display %.3f %.3f %.3f {Tags:[\"br_hub_text\",\"" + HubBuilder.VERSION_TAG + "\"],billboard:\"fixed\",Rotation:[%.1ff,0f],line_width:600,"
				+ "text:{text:\"\",bold:true,extra:[%s]},background:0,shadow:1b,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
				+ "translation:[0f,0f,0f],scale:[%.2ff,%.2ff,%.2ff]}}",
			x, y, z, HubBuilder.yawFor(facing), extra, scale, scale, scale));
	}

	/**
	 * The row's item as its inventory picture (an item display in "gui" view, full bright), facing the reader. A gun
	 * gets the rarity colour on its stripe (custom model data, as Guns.make does).
	 */
	private static void item(MinecraftServer server, double x, double y, double z, Row r, String facing, float size) {
		server.overworld().getChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
		String components = r.tint() < 0 ? "" : ",components:{\"minecraft:custom_model_data\":{colors:[" + (r.tint() & 0xFFFFFF) + "]}}";
		// Item displays turn their item round (it would face away): half a turn more than the text faces.
		HubBuilder.command(server, String.format(Locale.ROOT,
			"summon minecraft:item_display %.3f %.3f %.3f {Tags:[\"br_hub_text\",\"" + HubBuilder.VERSION_TAG + "\"],item:{id:\"%s\",count:1%s},item_display:\"gui\","
				+ "billboard:\"fixed\",Rotation:[%.1ff,0f],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
				+ "translation:[0f,0f,0f],scale:[%.2ff,%.2ff,%.2ff]}}",
			x, y, z, r.item(), components, HubBuilder.yawFor(facing) + 180F, size, size, size));
	}
}
