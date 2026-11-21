package com.sefixz.scorpysafio.mixin;

import com.sefixz.scorpysafio.ScorpyTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    /** Cada vez que el juego quiera abrir el menú principal vanilla, abrimos el nuestro. */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen scorpysafio$replaceTitleScreen(Screen screen) {
        if (screen != null && screen.getClass() == TitleScreen.class) {
            return new ScorpyTitleScreen();
        }
        return screen;
    }
}
