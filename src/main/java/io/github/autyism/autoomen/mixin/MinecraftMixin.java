package io.github.autyism.autoomen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.autyism.autoomen.AutoOmen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While the mod drinks a bottle, the game must not stop the drinking because the use key is not
 * physically held. Only the "is the use key held?" question inside handleKeybinds is answered,
 * and only while the player is in the middle of drinking for the mod.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	public @Nullable LocalPlayer player;

	@WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
	private boolean autoomen$holdUseWhileDrinking(KeyMapping key, Operation<Boolean> original) {
		Minecraft self = (Minecraft) (Object) this;
		if (key == self.options.keyUse && AutoOmen.drinker().holdUse(this.player)) {
			return true;
		}
		return original.call(key);
	}
}
