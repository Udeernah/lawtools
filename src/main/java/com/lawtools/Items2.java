package com.lawtools;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Fragment and fruit are normal items (amethyst shard / apple) marked with custom data,
 * so vanilla clients can use them without any client mod.
 */
final class Items2 {
	static final String FRAGMENT = "lawtools_fragment";
	static final String FRUIT = "lawtools_fruit";

	static ItemStack fragment(String id) {
		ItemStack s = new ItemStack(Items.AMETHYST_SHARD);
		s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(Morphs.title(id) + " Fragment")
				.styled(st -> st.withItalic(false).withColor(Formatting.LIGHT_PURPLE)));
		mark(s, FRAGMENT, id);
		return s;
	}

	static ItemStack fruit(String id) {
		ItemStack s = new ItemStack(Items.APPLE);
		s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(Morphs.title(id) + " Morph Ability")
				.styled(st -> st.withItalic(false).withColor(Formatting.LIGHT_PURPLE)));
		mark(s, FRUIT, id);
		return s;
	}

	private static void mark(ItemStack s, String key, String id) {
		NbtCompound n = new NbtCompound();
		n.putString(key, "minecraft:" + id);
		s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
		s.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
	}

	/** mob id (without namespace) stored under key, or null */
	static String read(ItemStack s, String key) {
		if (s.isEmpty()) return null;
		NbtComponent c = s.get(DataComponentTypes.CUSTOM_DATA);
		if (c == null) return null;
		NbtCompound n = c.copyNbt();
		if (!n.contains(key)) return null;
		String v = n.getString(key, "");
		return v.startsWith("minecraft:") ? v.substring(10) : v;
	}
}
