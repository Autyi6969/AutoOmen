package io.github.autyism.autoomen.gui;

import io.github.autyism.autoomen.config.AutoOmenConfig;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** The settings screen (opened from Mod Menu). Every change is saved to config/autoomen.json at once. */
public class AutoOmenConfigScreen extends OptionsSubScreen {
	private @Nullable LevelSlider levelSlider;

	public AutoOmenConfigScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("autoomen.config.title"));
	}

	@Override
	protected void addOptions() {
		AutoOmenConfig config = AutoOmenConfig.get();

		list.addBig(CycleButton.onOffBuilder(config.enabled)
				.withTooltip(value -> Tooltip.create(Component.translatable("autoomen.config.enabled.tooltip")))
				.create(Component.translatable("autoomen.config.enabled"), (button, value) -> {
					config.enabled = value;
					config.save();
				}));

		list.addBig(CycleButton.builder(AutoOmenConfigScreen::timingName, config.timing)
				.withValues(List.of(AutoOmenConfig.Timing.values()))
				.withTooltip(value -> Tooltip.create(Component.translatable("autoomen.config.timing." + key(value) + ".tooltip")))
				.create(Component.translatable("autoomen.config.timing"), (button, value) -> {
					config.timing = value;
					config.save();
				}));

		levelSlider = new LevelSlider(config);
		CycleButton<AutoOmenConfig.Priority> priority = CycleButton.builder(AutoOmenConfigScreen::priorityName, config.priority)
				.withValues(List.of(AutoOmenConfig.Priority.values()))
				.withTooltip(value -> Tooltip.create(Component.translatable("autoomen.config.priority." + key(value) + ".tooltip")))
				.create(Component.translatable("autoomen.config.priority"), (button, value) -> {
					config.priority = value;
					config.save();
					updateLevelSlider();
				});
		list.addSmall(priority, levelSlider);
		updateLevelSlider();

		list.addBig(new HealthSlider(config));

		list.addBig(CycleButton.onOffBuilder(config.chatNotice)
				.withTooltip(value -> Tooltip.create(Component.translatable("autoomen.config.chat_notice.tooltip")))
				.create(Component.translatable("autoomen.config.chat_notice"), (button, value) -> {
					config.chatNotice = value;
					config.save();
				}));
	}

	private void updateLevelSlider() {
		if (levelSlider != null) {
			levelSlider.active = AutoOmenConfig.get().priority == AutoOmenConfig.Priority.SPECIFIC;
		}
	}

	@Override
	public void removed() {
		super.removed();
		AutoOmenConfig.get().save();
	}

	private static String key(Enum<?> value) {
		return value.name().toLowerCase(java.util.Locale.ROOT);
	}

	private static Component timingName(AutoOmenConfig.Timing timing) {
		return Component.translatable("autoomen.config.timing." + key(timing));
	}

	private static Component priorityName(AutoOmenConfig.Priority priority) {
		return Component.translatable("autoomen.config.priority." + key(priority));
	}

	/** Bottle level I..V for the "specific level" priority. */
	private static final class LevelSlider extends AbstractSliderButton {
		private final AutoOmenConfig config;

		LevelSlider(AutoOmenConfig config) {
			super(0, 0, 150, 20, Component.empty(), toValue(config.specificLevel));
			this.config = config;
			setTooltip(Tooltip.create(Component.translatable("autoomen.config.level.tooltip")));
			updateMessage();
		}

		private static double toValue(int level) {
			return (level - AutoOmenConfig.MIN_LEVEL) / (double) (AutoOmenConfig.MAX_LEVEL - AutoOmenConfig.MIN_LEVEL);
		}

		private int level() {
			return AutoOmenConfig.MIN_LEVEL + (int) Math.round(value * (AutoOmenConfig.MAX_LEVEL - AutoOmenConfig.MIN_LEVEL));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("autoomen.config.level", Component.translatable("autoomen.level." + level())));
		}

		@Override
		protected void applyValue() {
			config.specificLevel = level();
			config.save();
		}
	}

	/** Health limit in half hearts, 0 = off. */
	private static final class HealthSlider extends AbstractSliderButton {
		private final AutoOmenConfig config;

		HealthSlider(AutoOmenConfig config) {
			super(0, 0, 310, 20, Component.empty(), config.minHealth / AutoOmenConfig.MAX_HEALTH_LIMIT);
			this.config = config;
			setTooltip(Tooltip.create(Component.translatable("autoomen.config.min_health.tooltip")));
			updateMessage();
		}

		private int health() {
			return Mth.floor(value * AutoOmenConfig.MAX_HEALTH_LIMIT + 0.5);
		}

		@Override
		protected void updateMessage() {
			int health = health();
			Component shown = health <= 0
					? Component.translatable("autoomen.config.min_health.off")
					: Component.translatable("autoomen.config.min_health.hearts", health / 2.0F == Math.floor(health / 2.0F)
							? String.valueOf(health / 2) : String.valueOf(health / 2.0F));
			setMessage(Component.translatable("autoomen.config.min_health", shown));
		}

		@Override
		protected void applyValue() {
			config.minHealth = health();
			config.save();
		}
	}
}
