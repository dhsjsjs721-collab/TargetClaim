package pvp.mixin;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pvp.PracticeCombat;
@Mixin(ClientPacketListener.class)
public abstract class DamageFeedbackMixin {
@Inject(method="handleDamageEvent",at=@At("TAIL"))
private void pvp$observeDamage(ClientboundDamageEventPacket packet,CallbackInfo ci) {
PracticeCombat.recordDamage(packet);
}
}
