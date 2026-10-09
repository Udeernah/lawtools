package com.lawtools;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Morphs {
	private static final List<String> AVAILABLE = new ArrayList<>();
	private static final Set<String> SET = new HashSet<>();

	private static final Map<String, Item> ICON_FALLBACK = Map.of(
			"iron_golem", Items.IRON_BLOCK,
			"snow_golem", Items.CARVED_PUMPKIN,
			"copper_golem", Items.COPPER_BLOCK);

	/** keep only mobs that exist in this game version */
	static void init() {
		AVAILABLE.clear();
		SET.clear();
		for (String id : MobIds.ALL) {
			if (Registries.ENTITY_TYPE.containsId(Identifier.of("minecraft", id))) {
				AVAILABLE.add(id);
				SET.add(id);
			}
		}
		LawTools.LOG.info("[LawTools] {} morphable mobs", AVAILABLE.size());
	}

	static List<String> available() {
		return AVAILABLE;
	}

	static boolean exists(String id) {
		return SET.contains(id);
	}

	static EntityType<?> type(String id) {
		return Registries.ENTITY_TYPE.get(Identifier.of("minecraft", id));
	}

	static String title(String id) {
		StringBuilder sb = new StringBuilder();
		for (String w : id.split("_")) {
			if (sb.length() > 0) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}

	static Item eggFor(String id) {
		Identifier egg = Identifier.of("minecraft", id + "_spawn_egg");
		if (Registries.ITEM.containsId(egg)) return Registries.ITEM.get(egg);
		return ICON_FALLBACK.getOrDefault(id, Items.NAME_TAG);
	}

	/** menu icon: the spawn egg (a picture of the mob), green when morphable, red when locked */
	static ItemStack icon(String id, boolean unlocked) {
		ItemStack st = new ItemStack(eggFor(id));
		st.set(DataComponentTypes.CUSTOM_NAME, Text.literal(title(id))
				.styled(s -> s.withItalic(false).withColor(unlocked ? Formatting.GREEN : Formatting.RED)));
		List<Text> lore = new ArrayList<>();
		if (unlocked) {
			lore.add(Text.literal("Click to morph").styled(s -> s.withItalic(false).withColor(Formatting.GRAY)));
		} else {
			lore.add(Text.literal("Locked").styled(s -> s.withItalic(false).withColor(Formatting.DARK_RED)));
			lore.add(Text.literal("Kill it for fragments (9 = a Morph Ability)")
					.styled(s -> s.withItalic(false).withColor(Formatting.GRAY)));
		}
		st.set(DataComponentTypes.LORE, new LoreComponent(lore));
		if (unlocked) st.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		return st;
	}
}
