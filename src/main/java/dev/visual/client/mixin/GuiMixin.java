package dev.visual.client.mixin;

import dev.visual.client.VisualScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void visualClient$hideHudUnderMenu(GuiGraphics graphics, DeltaTracker delta, CallbackInfo callback) {
        if (Minecraft.getInstance().screen instanceof VisualScreen) {
            callback.cancel();
        }
    }
}
