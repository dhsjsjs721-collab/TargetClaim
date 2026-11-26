package pvp.mixin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pvp.PracticeFreeLook;
@Mixin(Entity.class)
public abstract class PracticeOrbitInputMixin {
@Inject(method="turn(DD)V",at=@At("HEAD"),cancellable=true,require=0)
private void pvp$orbitInput(double dx,double dy,CallbackInfo ci) {
if(PracticeFreeLook.mouseTurn(this,dx,dy)) ci.cancel();
}
}
