package io.github.autyism.autoomen.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.autyism.autoomen.gui.AutoOmenConfigScreen;

/** Mod Menu entry point; the only class that references Mod Menu. */
public class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return AutoOmenConfigScreen::new;
	}
}
