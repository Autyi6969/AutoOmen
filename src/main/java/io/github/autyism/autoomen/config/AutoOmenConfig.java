package io.github.autyism.autoomen.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.autyism.autoomen.AutoOmen;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Settings stored in config/autoomen.json. */
public final class AutoOmenConfig {
	/** When the next bottle is drunk. */
	public enum Timing {
		/** After the raid boss bar is gone (the old raid has been removed). */
		SAFE,
		/** As soon as the boss bar shows "Victory" / "Defeat" (see docs/MECHANICS.md). */
		EARLY
	}

	/** Which bottle to pick when several levels are in the inventory. */
	public enum Priority {
		LOWEST,
		HIGHEST,
		/** Only bottles of {@link #specificLevel}; nothing else is drunk. */
		SPECIFIC
	}

	public static final int MIN_LEVEL = 1;
	public static final int MAX_LEVEL = 5;
	public static final float MAX_HEALTH_LIMIT = 20.0F;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static AutoOmenConfig instance;

	public boolean enabled = true;
	public Timing timing = Timing.SAFE;
	public Priority priority = Priority.LOWEST;
	/** Bottle level I..V used with {@link Priority#SPECIFIC}. */
	public int specificLevel = 1;
	/** Health points (2 = one heart). Below this nothing is drunk. 0 = no limit. */
	public float minHealth = 6.0F;
	public boolean chatNotice = true;

	public static AutoOmenConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(AutoOmen.MOD_ID + ".json");
	}

	private static AutoOmenConfig load() {
		Path path = path();
		AutoOmenConfig config = null;
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, AutoOmenConfig.class);
			} catch (IOException | JsonParseException e) {
				AutoOmen.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		if (config == null) {
			config = new AutoOmenConfig();
		}
		config.sanitize();
		config.save();
		return config;
	}

	private void sanitize() {
		if (timing == null) timing = Timing.SAFE;
		if (priority == null) priority = Priority.LOWEST;
		specificLevel = Math.clamp(specificLevel, MIN_LEVEL, MAX_LEVEL);
		if (Float.isNaN(minHealth)) minHealth = 0.0F;
		minHealth = Math.clamp(minHealth, 0.0F, MAX_HEALTH_LIMIT);
	}

	public void save() {
		sanitize();
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			AutoOmen.LOGGER.warn("Could not write {}", path, e);
		}
	}
}
