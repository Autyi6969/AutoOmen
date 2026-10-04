package io.github.autyism.autoomen.logic;

import io.github.autyism.autoomen.AutoOmen;
import io.github.autyism.autoomen.config.AutoOmenConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Decides every tick whether to drink an Ominous Bottle and carries the drinking out:
 * select the bottle (moving it to the hotbar first if needed), use it until it is drunk,
 * then select the original hotbar slot again.
 */
public final class AutoDrinker {
	/** After joining a world: wait for boss bars and effects to arrive from the server. */
	private static final int JOIN_GRACE_TICKS = 100;
	/** Bad Omen / Raid Omen must have been gone this long (effect and boss bar packets are not in step). */
	private static final int OMEN_GRACE_TICKS = 40;
	/** Safe mode: no raid boss bar for this long. */
	private static final int RAID_BAR_GRACE_TICKS = 60;
	/** Using a bottle takes 32 ticks; give up if it has not finished by then. */
	private static final int DRINK_TIMEOUT_TICKS = 100;
	/** After drinking: how long Bad Omen may take to show up. */
	private static final int OMEN_APPEAR_TICKS = 100;
	/** After the omen ends: how long the new raid's boss bar may take to show up. */
	private static final int RAID_APPEAR_TICKS = 200;
	/** This many bottles in a row without a new raid pause the mod (e.g. raids are turned off). */
	private static final int MAX_FAILED_BOTTLES = 2;

	public enum State { IDLE, SWAPPED, DRINKING }

	private final RaidWatcher watcher = new RaidWatcher();
	private State state = State.IDLE;
	private InteractionHand hand = InteractionHand.MAIN_HAND;
	private int originalSlot = -1;
	private int drinkSlot = -1;
	private int drinkLevel;
	private int stateTicks;
	private int bottlesBefore;

	/** Waiting for the raid the last bottle should start. */
	private boolean awaitingRaid;
	private boolean omenSeenSinceDrink;
	private int awaitTicks;
	private int failedBottles;
	private boolean paused;

	private int bottlesDrunk;

	public RaidWatcher watcher() {
		return watcher;
	}

	public State state() {
		return state;
	}

	public boolean paused() {
		return paused;
	}

	public int bottlesDrunk() {
		return bottlesDrunk;
	}

	/** True while the use key must count as held so the game does not stop the drinking. */
	public boolean holdUse(LocalPlayer player) {
		return state == State.DRINKING && player != null && player.isUsingItem();
	}

	/** Called when the world is left or the mod is switched on / off. */
	public void reset() {
		state = State.IDLE;
		originalSlot = -1;
		awaitingRaid = false;
		omenSeenSinceDrink = false;
		failedBottles = 0;
		paused = false;
		watcher.reset();
	}

	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gameMode == null) {
			if (state != State.IDLE || watcher.ticksInWorld() > 0) {
				reset();
			}
			return;
		}
		watcher.tick(client, player);
		AutoOmenConfig config = AutoOmenConfig.get();

		switch (state) {
			case SWAPPED -> startUsing(client, player);
			case DRINKING -> tickDrinking(client, player, config);
			case IDLE -> {
				trackLastBottle(client, player);
				if (config.enabled && !paused && shouldDrink(client, player, config)) {
					begin(client, player, config);
				}
			}
		}
	}

	// ------------------------------------------------------------------ when to drink

	private boolean shouldDrink(Minecraft client, LocalPlayer player, AutoOmenConfig config) {
		if (awaitingRaid || player.isSpectator() || !player.isAlive() || player.isUsingItem()) {
			return false;
		}
		if (watcher.ticksInWorld() < JOIN_GRACE_TICKS || watcher.badOmen() || watcher.raidOmen()
				|| watcher.ticksSinceOmen() < OMEN_GRACE_TICKS) {
			return false;
		}
		if (config.minHealth > 0 && player.getHealth() < config.minHealth) {
			return false;
		}
		if (!timingAllows(config)) {
			return false;
		}
		// Clicks in the inventory are only safe while no chest or similar is open.
		if (player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty()) {
			return false;
		}
		return BottleFinder.find(player, config) != null;
	}

	public boolean timingAllows(AutoOmenConfig config) {
		boolean raidGone = !watcher.raidBar() && watcher.ticksSinceRaidBar() >= RAID_BAR_GRACE_TICKS;
		if (config.timing == AutoOmenConfig.Timing.EARLY) {
			// The raid has been won or lost: its removal is at most 30 s away, and a Raid Omen
			// started now ends after that (docs/MECHANICS.md, question 5).
			return raidGone || (watcher.overRaidBar() && !watcher.ongoingRaidBar());
		}
		return raidGone;
	}

	// ------------------------------------------------------------------ drinking

	private void begin(Minecraft client, LocalPlayer player, AutoOmenConfig config) {
		BottleFinder.Found found = BottleFinder.find(player, config);
		if (found == null) {
			return;
		}
		Inventory inventory = player.getInventory();
		originalSlot = inventory.getSelectedSlot();
		drinkLevel = found.level();
		stateTicks = 0;

		if (found.offhand()) {
			hand = InteractionHand.OFF_HAND;
			drinkSlot = -1;
			startUsing(client, player);
			return;
		}
		hand = InteractionHand.MAIN_HAND;
		if (found.hotbar()) {
			drinkSlot = found.slot();
			inventory.setSelectedSlot(drinkSlot);
			startUsing(client, player);
			return;
		}
		// In the main inventory: swap it onto a hotbar slot like pressing a number key over it.
		drinkSlot = hotbarTarget(inventory);
		client.gameMode.handleContainerInput(InventoryMenu.CONTAINER_ID, found.slot(), drinkSlot, ContainerInput.SWAP, player);
		AutoOmen.LOGGER.info("Moved Ominous Bottle from inventory slot {} to hotbar slot {}", found.slot(), drinkSlot);
		inventory.setSelectedSlot(drinkSlot);
		state = State.SWAPPED;
	}

	/** An empty hotbar slot other than the held one, else the last hotbar slot that is not held. */
	private static int hotbarTarget(Inventory inventory) {
		int selected = inventory.getSelectedSlot();
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (slot != selected && inventory.getItem(slot).isEmpty()) {
				return slot;
			}
		}
		int last = Inventory.getSelectionSize() - 1;
		return selected == last ? last - 1 : last;
	}

	private void startUsing(Minecraft client, LocalPlayer player) {
		int level = hand == InteractionHand.OFF_HAND ? BottleFinder.levelOf(player.getOffhandItem()) : BottleFinder.levelOf(player.getMainHandItem());
		if (level <= 0) {
			AutoOmen.LOGGER.warn("Ominous Bottle is not in hand any more, giving up this attempt");
			finish(player, false);
			return;
		}
		drinkLevel = level;
		InteractionResult result = client.gameMode.useItem(player, hand);
		if (!(result instanceof InteractionResult.Success)) {
			AutoOmen.LOGGER.warn("Using the Ominous Bottle failed: {}", result);
			finish(player, false);
			return;
		}
		state = State.DRINKING;
		stateTicks = 0;
		bottlesBefore = BottleFinder.count(player);
		AutoOmen.LOGGER.info("Drinking Ominous Bottle level {} ({} hand)", drinkLevel, hand == InteractionHand.OFF_HAND ? "off" : "main");
	}

	private void tickDrinking(Minecraft client, LocalPlayer player, AutoOmenConfig config) {
		stateTicks++;
		if (player.isUsingItem()) {
			if (stateTicks > DRINK_TIMEOUT_TICKS) {
				AutoOmen.LOGGER.warn("Drinking took too long, stopping");
				client.gameMode.releaseUsingItem(player);
				finish(player, false);
			}
			return;
		}
		// Not using any more: either drunk (one bottle less), or interrupted (e.g. the player switched slots).
		boolean drank = BottleFinder.count(player) < bottlesBefore;
		finish(player, drank);
		if (!drank) {
			AutoOmen.LOGGER.info("Drinking was interrupted");
		} else if (config.chatNotice) {
			client.gui.hud.getChat().addClientSystemMessage(Component.translatable("autoomen.chat.drank",
					Component.translatable("autoomen.level." + drinkLevel), BottleFinder.count(player)).withStyle(ChatFormatting.GRAY));
		}
	}

	private void finish(LocalPlayer player, boolean drank) {
		if (hand == InteractionHand.MAIN_HAND && originalSlot >= 0 && player.getInventory().getSelectedSlot() == drinkSlot) {
			player.getInventory().setSelectedSlot(originalSlot);
		}
		state = State.IDLE;
		originalSlot = -1;
		if (drank) {
			bottlesDrunk++;
			awaitingRaid = true;
			omenSeenSinceDrink = false;
			awaitTicks = 0;
			AutoOmen.LOGGER.info("Finished drinking Ominous Bottle level {}", drinkLevel);
		}
	}

	// ------------------------------------------------------------------ did the bottle start a raid?

	private void trackLastBottle(Minecraft client, LocalPlayer player) {
		if (!awaitingRaid) {
			return;
		}
		awaitTicks++;
		if (watcher.badOmen() || watcher.raidOmen()) {
			omenSeenSinceDrink = true;
			awaitTicks = 0;
			return;
		}
		if (omenSeenSinceDrink && watcher.ongoingRaidBar()) {
			awaitingRaid = false;
			failedBottles = 0;
			return;
		}
		int limit = omenSeenSinceDrink ? RAID_APPEAR_TICKS : OMEN_APPEAR_TICKS;
		if (awaitTicks < limit) {
			return;
		}
		awaitingRaid = false;
		failedBottles++;
		AutoOmen.LOGGER.warn("The last Ominous Bottle did not start a raid ({} in a row)", failedBottles);
		if (failedBottles >= MAX_FAILED_BOTTLES) {
			paused = true;
			client.gui.hud.getChat().addClientSystemMessage(Component.translatable("autoomen.chat.paused", failedBottles).withStyle(ChatFormatting.RED));
		}
	}
}
