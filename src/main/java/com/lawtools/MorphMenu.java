package com.lawtools;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** A plain 6-row chest screen: mob pictures (spawn eggs), pages, and an "un-morph" button. */
final class MorphMenu extends GenericContainerScreenHandler {
	private static final int PER_PAGE = 45;

	private final ServerPlayerEntity viewer;
	private final boolean op;
	private final SimpleInventory inv;
	private int page;

	MorphMenu(int syncId, PlayerInventory pinv, SimpleInventory inv, ServerPlayerEntity viewer, boolean op) {
		super(ScreenHandlerType.GENERIC_9X6, syncId, pinv, inv, 6);
		this.inv = inv;
		this.viewer = viewer;
		this.op = op;
		render();
	}

	private boolean unlocked(String id) {
		return op || Store.has(viewer.getUuid(), id);
	}

	private void render() {
		inv.clear();
		List<String> ids = Morphs.available();
		for (int i = 0; i < PER_PAGE; i++) {
			int idx = page * PER_PAGE + i;
			if (idx >= ids.size()) break;
			String id = ids.get(idx);
			inv.setStack(i, Morphs.icon(id, unlocked(id)));
		}
		int pages = Math.max(1, (ids.size() + PER_PAGE - 1) / PER_PAGE);
		if (page > 0) inv.setStack(45, named(Items.ARROW, "Previous page", Formatting.YELLOW));
		inv.setStack(49, named(Items.BARRIER, "Back to normal (un-morph)", Formatting.RED));
		inv.setStack(47, named(Items.BOOK, "Page " + (page + 1) + "/" + pages
				+ (op ? " - operator: everything unlocked" : " - unlocked: " + Store.count(viewer.getUuid())), Formatting.AQUA));
		if (page < pages - 1) inv.setStack(53, named(Items.ARROW, "Next page", Formatting.YELLOW));
	}

	private static ItemStack named(net.minecraft.item.Item item, String name, Formatting color) {
		ItemStack st = new ItemStack(item);
		st.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).styled(s -> s.withItalic(false).withColor(color)));
		return st;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (slotIndex >= 0 && slotIndex < 54) click(slotIndex);
		syncState();                                    // undo whatever the client predicted
	}

	private void click(int slot) {
		List<String> ids = Morphs.available();
		if (slot < PER_PAGE) {
			int idx = page * PER_PAGE + slot;
			if (idx >= ids.size()) return;
			String id = ids.get(idx);
			if (!unlocked(id)) {
				viewer.sendMessage(Text.literal("You haven't unlocked " + Morphs.title(id) + " yet."), true);
				return;
			}
			MorphEngine.morph(LawTools.SERVER, viewer, id);
			viewer.closeHandledScreen();
		} else if (slot == 45 && page > 0) {
			page--;
			render();
		} else if (slot == 53) {
			int pages = Math.max(1, (ids.size() + PER_PAGE - 1) / PER_PAGE);
			if (page < pages - 1) {
				page++;
				render();
			}
		} else if (slot == 49) {
			MorphEngine.unmorph(LawTools.SERVER, viewer);
			viewer.closeHandledScreen();
		}
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}
