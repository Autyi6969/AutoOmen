package io.github.autyism.autoomen.selftest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.autyism.autoomen.AutoOmen;
import io.github.autyism.autoomen.compat.ModMenuEntry;
import io.github.autyism.autoomen.config.AutoOmenConfig;
import io.github.autyism.autoomen.gui.AutoOmenConfigScreen;
import io.github.autyism.autoomen.logic.AutoDrinker;
import io.github.autyism.autoomen.logic.BottleFinder;
import io.github.autyism.autoomen.logic.RaidWatcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Automated in-game check, only active with -Dao.selftest=true in a development environment.
 * Started by tools/selftest.ps1, which also chooses the mode (run/selftest_mode.txt):
 * <ul>
 *   <li>menu: title screen, the settings screen in English and Chinese, lang file check.</li>
 *   <li>safe / early: a flat world with a tiny village; the mod has to start four raids in a row
 *   on its own with the given timing while the test kills every raider by command.</li>
 * </ul>
 * Every result is a log line starting with [SelfTest]; the run ends with "[SelfTest] DONE".
 */
public final class SelfTest {
	private static final String WORLD_NAME = "selftest_raid";
	/** Bottle levels the mod should drink, in this order (priority "lowest first"). */
	private static final List<Integer> EXPECTED_LEVELS = List.of(1, 3, 5, 5);
	private static final int RAID_PHASE_TIMEOUT_TICKS = 20 * 60 * 16;

	private final Minecraft client = Minecraft.getInstance();
	private final String mode;
	private final Deque<Step> steps = new ArrayDeque<>();
	private Step current;
	private int stepTicks;
	private int passed;
	private int failed;
	private int screenshotIndex;
	private boolean done;

	// settings before the test, restored at the end
	private AutoOmenConfig savedConfig;

	// world state
	private BlockPos origin;
	private BlockPos bedPos;
	private final RaidLog raidLog = new RaidLog();
	private AutoDrinker.State lastDrinkerState = AutoDrinker.State.IDLE;
	private int drinksSeen;
	private final List<Integer> drunkLevels = new ArrayList<>();
	private int raidPhaseTicks;
	private int lastKillTick;

	private SelfTest(String mode) {
		this.mode = mode;
	}

	public static void registerIfEnabled() {
		if (!Boolean.getBoolean("ao.selftest") || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
			return;
		}
		String mode = "safe";
		Path modeFile = FabricLoader.getInstance().getGameDir().resolve("selftest_mode.txt");
		try {
			if (Files.isRegularFile(modeFile)) {
				mode = Files.readString(modeFile).trim().toLowerCase(java.util.Locale.ROOT);
			}
		} catch (IOException e) {
			AutoOmen.LOGGER.warn("[SelfTest] could not read {}", modeFile, e);
		}
		SelfTest test = new SelfTest(mode);
		test.plan();
		ClientTickEvents.END_CLIENT_TICK.register(client -> test.tick());
		AutoOmen.LOGGER.info("[SelfTest] enabled, mode {}", mode);
	}

	// ------------------------------------------------------------------ step engine

	private record Step(String name, Runnable start, BooleanSupplier finished, int timeoutTicks) {
	}

	private void step(String name, int timeoutTicks, Runnable start, BooleanSupplier finished) {
		steps.add(new Step(name, start, finished, timeoutTicks));
	}

	private void action(String name, Runnable action) {
		step(name, 1, action, () -> true);
	}

	private void wait(String name, int ticks) {
		step(name, ticks + 1, () -> {
		}, () -> stepTicks >= ticks);
	}

	private void tick() {
		if (done) {
			return;
		}
		totalTicks++;
		takePendingShots();
		if (current == null) {
			current = steps.poll();
			if (current == null) {
				return;
			}
			stepTicks = 0;
			log("step: " + current.name());
			try {
				current.start().run();
			} catch (RuntimeException e) {
				fail(current.name() + " threw " + e);
				AutoOmen.LOGGER.error("[SelfTest] step failed", e);
				abort();
				return;
			}
		}
		stepTicks++;
		boolean finished;
		try {
			finished = current.finished().getAsBoolean();
		} catch (RuntimeException e) {
			fail(current.name() + " threw " + e);
			AutoOmen.LOGGER.error("[SelfTest] step failed", e);
			abort();
			return;
		}
		if (finished) {
			current = null;
		} else if (stepTicks > current.timeoutTicks()) {
			fail("timeout in step '" + current.name() + "' after " + stepTicks + " ticks");
			abort();
		}
	}

	/** Skips the remaining steps and goes straight to cleanup (or ends the run if cleanup itself failed). */
	private void abort() {
		steps.clear();
		current = null;
		if (cleanupPlanned) {
			finish();
		} else {
			planCleanup();
		}
	}

	// ------------------------------------------------------------------ the plan

	private void plan() {
		step("wait for the title screen", 20 * 120, () -> {
		}, () -> client.gui.screen() instanceof TitleScreen && client.gui.overlay() == null);
		wait("let the title screen settle", 40);
		action("title screen reached", () -> {
			pass("main menu reached");
			screenshot("title");
		});
		wait("screenshot", 10);
		configScreenTour("en");
		if (mode.equals("menu")) {
			action("lang files", this::checkLangFiles);
			switchLanguage("zh_cn");
			configScreenTour("zh");
			switchLanguage("en_us");
		} else {
			planRaids();
		}
		planCleanup();
	}

	private void configScreenTour(String tag) {
		action("open settings via Mod Menu factory (" + tag + ")", () -> {
			Screen screen = new ModMenuEntry().getModConfigScreenFactory().create(client.gui.screen());
			client.gui.setScreen(screen);
		});
		wait("settings screen settles", 20);
		action("settings screen (" + tag + ")", () -> {
			check(client.gui.screen() instanceof AutoOmenConfigScreen, "settings screen is open (" + tag + ")");
			screenshot("settings_" + tag);
		});
		wait("screenshot", 10);
		action("close settings", () -> client.gui.screen().onClose());
		wait("back to title", 10);
		action("back on title", () -> check(client.gui.screen() instanceof TitleScreen, "Done goes back to the title screen (" + tag + ")"));
	}

	private void switchLanguage(String code) {
		final boolean[] reloaded = {false};
		step("switch language to " + code, 20 * 60, () -> {
			client.getLanguageManager().setSelected(code);
			client.options.languageCode = code;
			client.options.save();
			client.reloadResourcePacks().thenRun(() -> reloaded[0] = true);
		}, () -> reloaded[0] && client.gui.overlay() == null);
		wait("language settles", 20);
	}

	private void checkLangFiles() {
		Set<String> en = langKeys("en_us");
		Set<String> zh = langKeys("zh_cn");
		Set<String> onlyEn = new TreeSet<>(en);
		onlyEn.removeAll(zh);
		Set<String> onlyZh = new TreeSet<>(zh);
		onlyZh.removeAll(en);
		check(!en.isEmpty() && onlyEn.isEmpty() && onlyZh.isEmpty(),
				"en_us and zh_cn have the same " + en.size() + " keys (only en: " + onlyEn + ", only zh: " + onlyZh + ")");
	}

	private static Set<String> langKeys(String code) {
		String path = "/assets/autoomen/lang/" + code + ".json";
		try (InputStream in = SelfTest.class.getResourceAsStream(path)) {
			if (in == null) {
				return Set.of();
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			return new TreeSet<>(json.keySet());
		} catch (IOException e) {
			return Set.of();
		}
	}

	// ------------------------------------------------------------------ raid phase

	private void planRaids() {
		AutoOmenConfig.Timing timing = mode.equals("early") ? AutoOmenConfig.Timing.EARLY : AutoOmenConfig.Timing.SAFE;
		action("settings for the test", () -> {
			AutoOmenConfig config = AutoOmenConfig.get();
			savedConfig = copy(config);
			config.enabled = false; // switched on once the village is ready
			config.timing = timing;
			config.priority = AutoOmenConfig.Priority.LOWEST;
			config.minHealth = 0;
			config.chatNotice = true;
			config.save();
			client.options.pauseOnLostFocus = false;
			// no tutorial toasts over the screenshots (options.txt is restored by the harness)
			client.getTutorial().setStep(TutorialSteps.NONE);
			log("timing " + timing + ", expected bottle levels " + EXPECTED_LEVELS);
		});
		action("create world " + WORLD_NAME, this::createWorld);
		step("wait for the world", 20 * 120, () -> {
		}, () -> client.player != null && client.level != null && client.gui.screen() == null && client.getSingleplayerServer() != null);
		wait("world settles", 60);
		action("note the player position", () -> {
			origin = client.player.blockPosition();
			bedPos = origin.offset(6, 0, -1);
			client.player.getInventory().setSelectedSlot(0);
			log("player at " + origin.toShortString());
		});
		for (String command : setupCommands()) {
			action("command: " + command, () -> command(command));
			wait("command runs", 2);
		}
		step("village is recognised by the server", 20 * 60, () -> {
		}, () -> {
			ServerLevel level = serverLevel();
			return level != null && callOnServer(() -> level.isVillage(origin) && level.isVillage(bedPos), false);
		});
		action("village ready", () -> {
			pass("the test village counts as a village (a villager claimed the bed / workstation)");
			check(BottleFinder.count(client.player) == 4, "4 Ominous Bottles in the inventory (I in the main inventory, III in the hotbar, 2x V in the offhand)");
			screenshot("village");
			AutoOmenConfig.get().enabled = true;
			AutoOmenConfig.get().save();
			AutoOmen.drinker().reset();
		});
		step("four raids started by the mod", RAID_PHASE_TIMEOUT_TICKS, () -> raidPhaseTicks = 0, this::raidPhaseTick);
		action("raid phase results", this::raidResults);
		if (timing == AutoOmenConfig.Timing.SAFE) {
			planPauseCheck();
		}
	}

	/**
	 * With raids switched off no bottle can start a raid: the mod must stop after two bottles
	 * instead of drinking every bottle, and the toggle key (pressed twice) must resume it.
	 */
	private void planPauseCheck() {
		action("switch raids off, give 3 bottles II", () -> {
			command("gamerule raids false");
			command("item replace entity @s hotbar.2 with minecraft:ominous_bottle[minecraft:ominous_bottle_amplifier=1] 3");
		});
		wait("commands run", 10);
		step("mod pauses itself after 2 bottles without a raid", 20 * 180, () -> {
		}, () -> AutoOmen.drinker().paused());
		wait("chat line arrives", 10);
		action("pause results", () -> {
			check(BottleFinder.count(client.player) == 1, "paused with 1 of the 3 bottles left (got " + BottleFinder.count(client.player) + ")");
			screenshot("paused");
		});
		wait("no more drinking while paused", 20 * 15);
		action("toggle twice", () -> {
			check(BottleFinder.count(client.player) == 1, "nothing drunk while paused");
			AutoOmen.toggle(client);
			check(!AutoOmenConfig.get().enabled, "toggle switches the mod off");
			AutoOmen.toggle(client);
			check(AutoOmenConfig.get().enabled && !AutoOmen.drinker().paused(), "toggling off and on again clears the pause");
			command("gamerule raids true");
		});
	}

	private List<String> setupCommands() {
		return List.of(
				"gamerule send_command_feedback false",
				"gamerule spawn_mobs false",
				"gamerule spawn_patrols false",
				"gamerule spawn_wandering_traders false",
				"gamerule advance_time false",
				"gamerule advance_weather false",
				"time set 6000",
				"difficulty easy",
				"gamemode survival",
				"effect give @s minecraft:resistance infinite 4 true",
				"effect give @s minecraft:saturation infinite 0 true",
				// a glass hut with a bed, a composter and a villager: the smallest possible village
				"fill ~4 ~-1 ~-2 ~8 ~2 ~2 minecraft:glass hollow",
				"setblock ~6 ~ ~-1 minecraft:white_bed[facing=north,part=head]",
				"setblock ~6 ~ ~ minecraft:white_bed[facing=north,part=foot]",
				"setblock ~5 ~ ~1 minecraft:composter",
				"summon minecraft:villager ~7 ~ ~1",
				"clear @s",
				"item replace entity @s hotbar.0 with minecraft:iron_sword",
				"item replace entity @s inventory.10 with minecraft:ominous_bottle[minecraft:ominous_bottle_amplifier=0]",
				"item replace entity @s hotbar.4 with minecraft:ominous_bottle[minecraft:ominous_bottle_amplifier=2]",
				"item replace entity @s weapon.offhand with minecraft:ominous_bottle[minecraft:ominous_bottle_amplifier=4] 2");
	}

	private void createWorld() {
		Path worldDir = client.getLevelSource().getBaseDir().resolve(WORLD_NAME);
		try {
			deleteRecursively(worldDir);
		} catch (IOException e) {
			fail("could not remove the old " + WORLD_NAME + " folder: " + e);
		}
		LevelSettings settings = new LevelSettings(WORLD_NAME, GameType.SURVIVAL,
				new LevelSettings.DifficultySettings(Difficulty.EASY, false, false), true, WorldDataConfiguration.DEFAULT);
		// No structures: a generated village nearby would move the raid centre.
		client.createWorldOpenFlows().createFreshLevel(WORLD_NAME, settings, new WorldOptions(20261004L, false, false),
				WorldPresets::createTestWorldDimensions, client.gui.screen());
	}

	/** One tick of the raid phase; returns true once the fourth raid is won. */
	private boolean raidPhaseTick() {
		raidPhaseTicks++;
		LocalPlayer player = client.player;
		if (player == null) {
			fail("player left the world during the raid phase");
			return true;
		}
		AutoDrinker drinker = AutoOmen.drinker();
		RaidWatcher watcher = drinker.watcher();

		// the mod starts drinking
		if (drinker.state() == AutoDrinker.State.DRINKING && lastDrinkerState != AutoDrinker.State.DRINKING) {
			drinksSeen++;
			int level = player.isUsingItem() ? BottleFinder.levelOf(player.getUseItem()) : 0;
			drunkLevels.add(level);
			log("mod drinks bottle #" + drinksSeen + " (level " + level + ") at game time " + gameTime()
					+ ", raid bar: " + (watcher.raidBar() ? (watcher.overRaidBar() ? "victory/defeat" : "ongoing") : "none")
					+ ", held slot " + player.getInventory().getSelectedSlot());
			screenshot("drink_" + drinksSeen);
		}
		if (drinker.state() == AutoDrinker.State.IDLE && lastDrinkerState == AutoDrinker.State.DRINKING) {
			log("mod finished drinking, held slot back to " + player.getInventory().getSelectedSlot()
					+ ", Bad Omen " + watcher.badOmen() + ", bottles left " + BottleFinder.count(player));
			check(player.getInventory().getSelectedSlot() == 0, "held slot is the sword slot again after bottle #" + drinksSeen);
			screenshotLater("drank_" + drinksSeen, 5);
		}
		lastDrinkerState = drinker.state();
		check(!drinker.paused(), "mod did not pause itself", true);

		// server side view of the raid at the village
		if (raidPhaseTicks % 5 == 0) {
			RaidSnapshot snapshot = snapshot();
			raidLog.update(snapshot, this);
			if (snapshot != null && snapshot.raidersAlive() > 0 && raidPhaseTicks - lastKillTick >= 20) {
				lastKillTick = raidPhaseTicks;
				command("kill @e[type=#minecraft:raiders]");
			}
		}
		if (raidLog.wonRaids() >= EXPECTED_LEVELS.size()) {
			return true;
		}
		if (failed > 0 && raidLog.lastFailureFatal) {
			return true;
		}
		return false;
	}

	private void raidResults() {
		log("raids seen: " + raidLog.summary());
		check(raidLog.wonRaids() >= EXPECTED_LEVELS.size(), "the mod started " + EXPECTED_LEVELS.size() + " raids in a row and all were won");
		check(drunkLevels.equals(EXPECTED_LEVELS), "bottles drunk in the order " + EXPECTED_LEVELS + " (got " + drunkLevels + ")");
		check(drinksSeen == EXPECTED_LEVELS.size(), "exactly " + EXPECTED_LEVELS.size() + " bottles drunk (got " + drinksSeen + ")");
		check(BottleFinder.count(client.player) == 0, "no Ominous Bottle left");
		List<Integer> levels = raidLog.raidLevels();
		check(levels.equals(EXPECTED_LEVELS), "every bottle started its own new raid at its own level " + EXPECTED_LEVELS + " (raid levels " + levels + ")");
		check(!raidLog.levelChanged, "no raid had its level raised by a later bottle (nothing merged into an old raid)");
		log("gaps between a raid being removed and the next one being created (ticks): " + raidLog.gaps);
		screenshot("end");
	}

	// ------------------------------------------------------------------ server helpers

	private record RaidSnapshot(int id, int level, int groupsSpawned, boolean started, boolean victory, boolean loss, int raidersAlive, long gameTime) {
	}

	private RaidSnapshot snapshot() {
		ServerLevel level = serverLevel();
		if (level == null) {
			return null;
		}
		return callOnServer(() -> {
			Raid raid = level.getRaidAt(bedPos);
			if (raid == null) {
				return new RaidSnapshot(-1, 0, 0, false, false, false, 0, level.getGameTime());
			}
			OptionalInt id = level.getRaids().getId(raid);
			return new RaidSnapshot(id.orElse(-1), raid.getRaidOmenLevel(), raid.getGroupsSpawned(), raid.isStarted(), raid.isVictory(), raid.isLoss(),
					raid.getTotalRaidersAlive(), level.getGameTime());
		}, null);
	}

	/** Tracks every raid the server has at the village. */
	private static final class RaidLog {
		private final Map<Integer, RaidSnapshot> firstSeen = new LinkedHashMap<>();
		private final Map<Integer, RaidSnapshot> last = new LinkedHashMap<>();
		private final Set<Integer> won = new TreeSet<>();
		private final List<Long> gaps = new ArrayList<>();
		private int currentId = -1;
		private long removedAt = -1;
		private boolean levelChanged;
		private boolean lastFailureFatal;

		void update(RaidSnapshot s, SelfTest test) {
			if (s == null) {
				return;
			}
			if (s.id() != currentId) {
				if (currentId != -1 && s.id() == -1) {
					removedAt = s.gameTime();
					test.log("raid #" + currentId + " removed at game time " + removedAt);
					test.screenshot("raid_" + currentId + "_removed");
				}
				if (s.id() != -1) {
					if (firstSeen.containsKey(s.id())) {
						test.fail("raid #" + s.id() + " came back");
					} else {
						firstSeen.put(s.id(), s);
						test.log("raid #" + s.id() + " created at game time " + s.gameTime() + ", omen level " + s.level());
						if (removedAt >= 0 && currentId == -1) {
							gaps.add(s.gameTime() - removedAt);
						}
						if (currentId != -1) {
							test.log("raid #" + s.id() + " appeared while raid #" + currentId + " was still there");
						}
					}
				}
				currentId = s.id();
			}
			if (s.id() == -1) {
				return;
			}
			RaidSnapshot before = last.put(s.id(), s);
			if (before != null && before.level() != s.level()) {
				levelChanged = true;
				test.fail("raid #" + s.id() + " level changed from " + before.level() + " to " + s.level() + " (a bottle was merged into it)");
			}
			if (before != null && before.groupsSpawned() != s.groupsSpawned()) {
				test.log("raid #" + s.id() + " wave " + s.groupsSpawned() + " spawned");
				if (s.groupsSpawned() == 1) {
					test.screenshot("raid_" + s.id() + "_wave1");
				}
			}
			if (s.victory() && won.add(s.id())) {
				test.pass("raid #" + s.id() + " (level " + s.level() + ") won at game time " + s.gameTime());
				test.screenshotLater("raid_" + s.id() + "_victory", 30);
			}
			if (s.loss()) {
				test.fail("raid #" + s.id() + " was lost");
				lastFailureFatal = true;
			}
		}

		int wonRaids() {
			return won.size();
		}

		List<Integer> raidLevels() {
			return firstSeen.values().stream().map(RaidSnapshot::level).toList();
		}

		String summary() {
			StringBuilder sb = new StringBuilder();
			for (RaidSnapshot s : firstSeen.values()) {
				RaidSnapshot l = last.get(s.id());
				sb.append("#").append(s.id()).append(" level ").append(l.level()).append(" waves ").append(l.groupsSpawned())
						.append(won.contains(s.id()) ? " won" : " not won").append("; ");
			}
			return sb.toString();
		}
	}

	private ServerLevel serverLevel() {
		IntegratedServer server = client.getSingleplayerServer();
		return server == null ? null : server.overworld();
	}

	private long gameTime() {
		return client.level == null ? -1 : client.level.getGameTime();
	}

	/** Runs on the server thread and waits for the answer (the integrated server ticks on its own thread). */
	private <T> T callOnServer(java.util.function.Supplier<T> supplier, T fallback) {
		IntegratedServer server = client.getSingleplayerServer();
		if (server == null) {
			return fallback;
		}
		try {
			return server.submit(supplier).get(5, java.util.concurrent.TimeUnit.SECONDS);
		} catch (Exception e) {
			AutoOmen.LOGGER.warn("[SelfTest] server call failed", e);
			return fallback;
		}
	}

	private void command(String command) {
		if (client.player != null) {
			client.player.connection.sendCommand(command);
		}
	}

	// ------------------------------------------------------------------ cleanup

	private boolean cleanupPlanned;

	private void planCleanup() {
		cleanupPlanned = true;
		action("restore settings", () -> {
			if (savedConfig != null) {
				AutoOmenConfig config = AutoOmenConfig.get();
				config.enabled = savedConfig.enabled;
				config.timing = savedConfig.timing;
				config.priority = savedConfig.priority;
				config.specificLevel = savedConfig.specificLevel;
				config.minHealth = savedConfig.minHealth;
				config.chatNotice = savedConfig.chatNotice;
				config.save();
			}
		});
		step("leave the world", 20 * 60, () -> {
			if (client.level != null) {
				// ends on the title screen, like the "Save and Quit to Title" button
				client.disconnectFromWorld(Component.literal("self-test finished"));
			}
		}, () -> client.level == null && client.gui.screen() instanceof TitleScreen);
		wait("title screen settles", 20);
		action("finish", this::finish);
	}

	private void finish() {
		log("passed=" + passed + " failed=" + failed + " screenshots=" + screenshotIndex);
		log("DONE");
		done = true;
		client.stop();
	}

	// ------------------------------------------------------------------ bookkeeping

	private static AutoOmenConfig copy(AutoOmenConfig config) {
		AutoOmenConfig copy = new AutoOmenConfig();
		copy.enabled = config.enabled;
		copy.timing = config.timing;
		copy.priority = config.priority;
		copy.specificLevel = config.specificLevel;
		copy.minHealth = config.minHealth;
		copy.chatNotice = config.chatNotice;
		return copy;
	}

	private final Set<String> reportedOnce = new TreeSet<>();

	private void check(boolean ok, String what) {
		check(ok, what, false);
	}

	/** With onlyFailures, a passing check is not logged (used for checks made every tick). */
	private void check(boolean ok, String what, boolean onlyFailures) {
		if (ok) {
			if (!onlyFailures) {
				pass(what);
			}
		} else if (!onlyFailures || reportedOnce.add(what)) {
			fail(what);
		}
	}

	private void pass(String what) {
		passed++;
		AutoOmen.LOGGER.info("[SelfTest] PASS {}", what);
	}

	private void fail(String what) {
		failed++;
		AutoOmen.LOGGER.error("[SelfTest] FAIL {}", what);
	}

	private void log(String message) {
		AutoOmen.LOGGER.info("[SelfTest] {}", message);
	}

	private record PendingShot(String name, int atTick) {
	}

	private final List<PendingShot> pendingShots = new ArrayList<>();
	private int totalTicks;

	/** Takes a screenshot a few ticks later (e.g. once a boss bar text has been updated). */
	private void screenshotLater(String name, int ticks) {
		pendingShots.add(new PendingShot(name, totalTicks + ticks));
	}

	private void takePendingShots() {
		for (PendingShot shot : List.copyOf(pendingShots)) {
			if (totalTicks >= shot.atTick()) {
				pendingShots.remove(shot);
				screenshot(shot.name());
			}
		}
	}

	private void screenshot(String name) {
		String fileName = String.format("selftest_%02d_%s.png", ++screenshotIndex, name);
		Screenshot.grab(client.gameDirectory, fileName, client.gameRenderer.mainRenderTarget(), 1, message -> {
		});
		log("screenshot " + fileName);
	}

	private static void deleteRecursively(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(dir)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}
}
