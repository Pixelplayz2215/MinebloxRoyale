package gg.minebloxroyale.bot;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.item.ConsumableItem;
import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.match.BotRoster;
import gg.minebloxroyale.match.Combat;
import gg.minebloxroyale.match.Reboot;
import gg.minebloxroyale.sheets.ConsumableDef;
import gg.minebloxroyale.sheets.MaterialDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What a bot carries: only what it looted or harvested (bots start with nothing). Guns, heals and shields as real item
 * stacks, so a real bot drinks a real Shield Potion (same rules as players) and drops its whole bag when eliminated; wood,
 * brick and metal counted apart, each up to the materials sheet's maxCarry like a player's.
 */
public final class BotBag {
	private final List<ItemStack> items = new ArrayList<>();
	private final Map<String, Integer> materials = new LinkedHashMap<>();

	public void addAll(Collection<ItemStack> stacks) {
		for (ItemStack s : stacks) add(s);
	}

	public void add(ItemStack s) {
		if (s.isEmpty() || ModItems.AMMO.containsValue(s.getItem())) return; // bots never run dry
		if (Reboot.isCard(s)) return; // reboot cards are carried apart (BotEntity.cards), never looted
		for (var m : ModItems.MATERIALS.entrySet())
			if (s.is(m.getValue())) {
				addMaterial(m.getKey(), s.getCount());
				return;
			}
		for (ItemStack have : items)
			if (ItemStack.isSameItemSameComponents(have, s) && have.getCount() < have.getMaxStackSize()) {
				int move = Math.min(s.getCount(), have.getMaxStackSize() - have.getCount());
				have.grow(move);
				s.shrink(move);
				if (s.isEmpty()) return;
			}
		items.add(s.copy());
	}

	/** Rebooted: back with nothing but the pickaxe. */
	public void clear() {
		items.clear();
		materials.clear();
	}

	// ---- materials ----

	public void addMaterial(String id, int amount) {
		MaterialDef m = Sheets.MATERIALS_BY_ID.get(id);
		if (m != null && amount > 0) materials.put(id, Math.min(m.maxCarry(), material(id) + amount));
	}

	public int material(String id) {
		return materials.getOrDefault(id, 0);
	}

	public int totalMaterials() {
		int n = 0;
		for (int v : materials.values()) n += v;
		return n;
	}

	/** Uses up materials for a build piece; false (nothing used) if there isn't enough. */
	public boolean spend(String id, int amount) {
		if (material(id) < amount) return false;
		materials.put(id, material(id) - amount);
		return true;
	}

	/**
	 * The material to build a piece costing `cost` with, or null if none has enough: the one it has most of, or (strong:
	 * cover that must last, like a box while healing) the toughest it can afford, metal before brick before wood.
	 */
	public String buildMaterial(int cost, boolean strong) {
		String best = null;
		for (MaterialDef m : Sheets.MATERIALS) {
			if (material(m.id()) < cost) continue;
			if (best == null || (strong ? m.pieceHealth() > Sheets.MATERIALS_BY_ID.get(best).pieceHealth() : material(m.id()) > material(best))) best = m.id();
		}
		return best;
	}

	/** The material it has least of (what it farms for next). */
	public String scarcest() {
		String best = null;
		for (MaterialDef m : Sheets.MATERIALS) if (best == null || material(m.id()) < material(best)) best = m.id();
		return best;
	}

	/** Its materials as item stacks (dropped when it's eliminated). */
	public List<ItemStack> materialStacks() {
		List<ItemStack> out = new ArrayList<>();
		materials.forEach((id, n) -> {
			if (n > 0) out.add(new ItemStack(ModItems.MATERIALS.get(id), n));
		});
		return out;
	}

	public int size() {
		items.removeIf(ItemStack::isEmpty);
		return items.size();
	}

	public List<ItemStack> items() {
		items.removeIf(ItemStack::isEmpty);
		return items;
	}

	/** The best gun carried: highest rarity first. */
	public ItemStack bestGun() {
		ItemStack best = null;
		for (ItemStack s : items())
			if (s.getItem() instanceof WeaponItem && (best == null || Guns.rarity(s).tier() > Guns.rarity(best).tier())) best = s;
		return best;
	}

	/**
	 * The heal or shield a bot should use now, following the player rules (Combat.whyNot): shields when the shield is
	 * low, heals when health is low; nothing if it carries nothing that would help.
	 */
	public ItemStack careItem(LivingEntity e) {
		float hp = e.getHealth(), max = e.getMaxHealth(), sh = Combat.shield(e), maxSh = MinebloxRoyale.setting("maxShield");
		ItemStack pick = null;
		int score = 0;
		for (ItemStack s : items()) {
			if (!(s.getItem() instanceof ConsumableItem c) || Combat.whyNot(e, c.def) != null) continue;
			int v = switch (c.def.kind()) {
				case "heal" -> hp < max * 0.75F ? 3 : hp < max ? 1 : 0;
				case "shield" -> sh < maxSh * 0.5F ? 3 : sh < maxSh ? 2 : 0;
				default -> hp < max * 0.5F || sh < maxSh * 0.3F ? 4 : 1;
			};
			if (v > score) {
				score = v;
				pick = s;
			}
		}
		return pick;
	}

	/** Simulated bots (far from everyone) heal and shield with the numbers alone, one item a second. */
	public void selfCare(BotRoster.Sim s, RandomSource rnd) {
		float hp = BotRoster.health(s), sh = BotRoster.shield(s), max = MinebloxRoyale.setting("maxHealth"), maxSh = MinebloxRoyale.setting("maxShield");
		for (ItemStack st : items()) {
			if (!(st.getItem() instanceof ConsumableItem c)) continue;
			ConsumableDef d = c.def;
			float addHp = 0, addSh = 0;
			switch (d.kind()) {
				case "heal" -> addHp = hp < d.cap() ? Math.min(d.amount(), d.cap() - hp) : 0;
				case "shield" -> addSh = sh < d.cap() ? Math.min(d.amount(), d.cap() - sh) : 0;
				default -> {
					addHp = Math.min(d.amount(), max - hp);
					addSh = Math.min(d.amount() - addHp, maxSh - sh);
				}
			}
			if (addHp <= 0 && addSh <= 0) continue;
			BotRoster.heal(s, addHp, addSh);
			st.shrink(1);
			return;
		}
	}
}
