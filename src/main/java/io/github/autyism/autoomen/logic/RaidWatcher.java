package io.github.autyism.autoomen.logic;

import io.github.autyism.autoomen.mixin.BossHealthOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.effect.MobEffects;

/**
 * Everything the client can know about raids, worked out once per tick.
 *
 * <p>The server shows a raid to nearby players as a boss bar whose name is the translation
 * "event.minecraft.raid" (ongoing, sometimes with "Raiders Remaining: N" appended), or
 * "event.minecraft.raid.victory.full" / "event.minecraft.raid.defeat.full" during the 30 s
 * celebration after the raid. The bar is removed when the raid is stopped and deleted.
 * See docs/MECHANICS.md.</p>
 */
public final class RaidWatcher {
	public static final String KEY_RAID = "event.minecraft.raid";
	public static final String KEY_VICTORY = "event.minecraft.raid.victory.full";
	public static final String KEY_DEFEAT = "event.minecraft.raid.defeat.full";

	/** Large value meaning "not seen for a long time". */
	private static final int LONG_AGO = 1_000_000;

	private int ticksInWorld;
	private int ticksSinceRaidBar = LONG_AGO;
	private int ticksSinceOmen = LONG_AGO;
	private boolean raidBar;
	private boolean ongoingRaidBar;
	private boolean overRaidBar;
	private boolean badOmen;
	private boolean raidOmen;

	public void reset() {
		ticksInWorld = 0;
		ticksSinceRaidBar = LONG_AGO;
		ticksSinceOmen = LONG_AGO;
		raidBar = ongoingRaidBar = overRaidBar = badOmen = raidOmen = false;
	}

	public void tick(Minecraft client, LocalPlayer player) {
		ticksInWorld = Math.min(ticksInWorld + 1, LONG_AGO);

		raidBar = ongoingRaidBar = overRaidBar = false;
		var events = ((BossHealthOverlayAccessor) client.gui.hud.getBossOverlay()).autoomen$getEvents();
		for (LerpingBossEvent event : events.values()) {
			String key = translationKey(event.getName());
			if (key == null || !key.startsWith(KEY_RAID)) {
				continue;
			}
			if (key.equals(KEY_VICTORY) || key.equals(KEY_DEFEAT)) {
				raidBar = true;
				overRaidBar = true;
			} else if (key.equals(KEY_RAID)) {
				raidBar = true;
				ongoingRaidBar = true;
			}
		}
		ticksSinceRaidBar = raidBar ? 0 : Math.min(ticksSinceRaidBar + 1, LONG_AGO);

		badOmen = player.hasEffect(MobEffects.BAD_OMEN);
		raidOmen = player.hasEffect(MobEffects.RAID_OMEN);
		ticksSinceOmen = (badOmen || raidOmen) ? 0 : Math.min(ticksSinceOmen + 1, LONG_AGO);
	}

	private static String translationKey(Component name) {
		return name != null && name.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
	}

	public int ticksInWorld() {
		return ticksInWorld;
	}

	public int ticksSinceRaidBar() {
		return ticksSinceRaidBar;
	}

	public int ticksSinceOmen() {
		return ticksSinceOmen;
	}

	/** Any raid boss bar is shown (ongoing or celebrating). */
	public boolean raidBar() {
		return raidBar;
	}

	/** A raid boss bar of a raid that is still being fought is shown. */
	public boolean ongoingRaidBar() {
		return ongoingRaidBar;
	}

	/** A "Raid - Victory" or "Raid - Defeat" bar is shown: the raid is over and celebrating. */
	public boolean overRaidBar() {
		return overRaidBar;
	}

	public boolean badOmen() {
		return badOmen;
	}

	public boolean raidOmen() {
		return raidOmen;
	}
}
