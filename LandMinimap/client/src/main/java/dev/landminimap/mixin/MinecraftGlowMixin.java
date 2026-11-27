package dev.landminimap.mixin;

import dev.landminimap.LandMinimap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
    @Inject(method="shouldEntityAppearGlowing",at=@At("HEAD"),cancellable=true)
    private void landminimap$clanGlow(Entity entity,CallbackInfoReturnable<Boolean> cir) {
        if(LandMinimap.glow(entity))cir.setReturnValue(true);
    }
}
