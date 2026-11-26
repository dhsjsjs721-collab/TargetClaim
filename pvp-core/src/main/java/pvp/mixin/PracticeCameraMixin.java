package pvp.mixin;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pvp.PracticeFreeLook;
/** Apply visual orbit orientation before vanilla third-person collision/zoom. */
@Mixin(Camera.class)
public abstract class PracticeCameraMixin {
@Shadow private Entity entity;
@Shadow protected abstract void setRotation(float yaw,float pitch);
@Inject(method="alignWithEntity",at=@At(value="INVOKE",target="Lnet/minecraft/client/Camera;getMaxZoom(F)F"),require=0)
private void pvp$orbit(float partialTick,CallbackInfo ci) {
if(PracticeFreeLook.applyCamera(entity)) setRotation(PracticeFreeLook.yaw(),PracticeFreeLook.pitch());
}
}
