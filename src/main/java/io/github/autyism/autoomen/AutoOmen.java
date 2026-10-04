package io.github.autyism.autoomen;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.autoomen.config.AutoOmenConfig;
import io.github.autyism.autoomen.logic.AutoDrinker;
import io.github.autyism.autoomen.selftest.SelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoOmen implements ClientModInitializer {
	public static final String MOD_ID = "autoomen";
	public static final Logger LOGGER = LoggerFactory.getLogger("AutoOmen");

	private static final AutoDrinker DRINKER = new AutoDrinker();
	private static KeyMapping toggleKey;

	public static AutoDrinker drinker() {
		return DRINKER;
	}

	@Override
	public void onInitializeClient() {
		AutoOmenConfig.get();
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.autoomen.toggle", InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				toggle(client);
			}
			DRINKER.tick(client);
		});

		SelfTest.registerIfEnabled();
		LOGGER.info("Auto Omen loaded");
	}

	/** Switches the mod on / off (the toggle key) and says so in the action bar. */
	public static void toggle(Minecraft client) {
		AutoOmenConfig config = AutoOmenConfig.get();
		config.enabled = !config.enabled;
		config.save();
		DRINKER.reset();
		if (client.player != null) {
			client.player.sendOverlayMessage(Component.translatable(config.enabled ? "autoomen.toggle.on" : "autoomen.toggle.off")
					.withStyle(config.enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
		}
	}
}
