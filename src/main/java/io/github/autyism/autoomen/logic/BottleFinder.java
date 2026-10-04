package io.github.autyism.autoomen.logic;

import io.github.autyism.autoomen.config.AutoOmenConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.OminousBottleAmplifier;

/** Finds the Ominous Bottle to drink, following the level priority setting. */
public final class BottleFinder {
	/** Inventory index of the bottle (0-8 hotbar, 9-35 main inventory, {@link Inventory#SLOT_OFFHAND}) and its level I..V. */
	public record Found(int slot, int level) {
		public boolean offhand() {
			return slot == Inventory.SLOT_OFFHAND;
		}

		public boolean hotbar() {
			return slot >= 0 && slot < Inventory.getSelectionSize();
		}
	}

	private BottleFinder() {
	}

	/** Level I..V of an Ominous Bottle stack, or 0 if the stack is something else. */
	public static int levelOf(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.OMINOUS_BOTTLE)) {
			return 0;
		}
		OminousBottleAmplifier amplifier = stack.get(DataComponents.OMINOUS_BOTTLE_AMPLIFIER);
		return (amplifier == null ? 0 : amplifier.value()) + 1;
	}

	public static Found find(Player player, AutoOmenConfig config) {
		Inventory inventory = player.getInventory();
		Found best = consider(null, Inventory.SLOT_OFFHAND, levelOf(player.getOffhandItem()), config);
		int selected = inventory.getSelectedSlot();
		// The held slot first, then the rest of the hotbar, then the main inventory: on equal level
		// the first one found wins, which means the fewest slot changes.
		best = consider(best, selected, levelOf(inventory.getItem(selected)), config);
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			if (slot != selected) {
				best = consider(best, slot, levelOf(inventory.getItem(slot)), config);
			}
		}
		return best;
	}

	private static Found consider(Found best, int slot, int level, AutoOmenConfig config) {
		if (level <= 0) {
			return best;
		}
		switch (config.priority) {
			case SPECIFIC -> {
				return best == null && level == config.specificLevel ? new Found(slot, level) : best;
			}
			case HIGHEST -> {
				return best == null || level > best.level() ? new Found(slot, level) : best;
			}
			default -> {
				return best == null || level < best.level() ? new Found(slot, level) : best;
			}
		}
	}

	/** Number of Ominous Bottles of every level in the inventory (offhand included). */
	public static int count(Player player) {
		int total = levelOf(player.getOffhandItem()) > 0 ? player.getOffhandItem().getCount() : 0;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (levelOf(stack) > 0) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
