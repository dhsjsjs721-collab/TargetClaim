package pvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
final class PracticeCombat {
private static LocalPlayer lastPlayer;
private static float lastHealth;
private static int responseWindow;
private static int readyTicks;
private static double angularError=Double.NaN;
private static CombatEngine.Decision decision=CombatEngine.Decision.WAIT;
static double angularError() { return angularError; }
static int readyTicks() { return readyTicks; }
static CombatEngine.Decision decision() { return decision; }
static void tick(Minecraft mc, CombatSettings settings, CombatEngine.Mode mode) {
if(!mc.hasSingleplayerServer() || mc.player==null || mc.level==null || mc.gameMode==null) {
lastPlayer=null;
angularError=Double.NaN;
decision=CombatEngine.Decision.WAIT;
readyTicks=0;
responseWindow=0;
return;
}
var self=mc.player;
if(lastPlayer!=self) { lastPlayer=self; lastHealth=self.getHealth(); responseWindow=0; readyTicks=0; }
if(self.getHealth()<lastHealth) responseWindow=12;
else if(responseWindow>0) responseWindow--;
lastHealth=self.getHealth();
if(mc.gui.screen()!=null) return;
angularError=Double.NaN;
decision=CombatEngine.Decision.WAIT;
if(!self.isAlive() || self.isSpectator() || self.isCreative() || self.isUsingItem()) return;
if(!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player target) || target==self || !target.isAlive() || target.isSpectator() || target.isInvisible() || !self.hasLineOfSight(target)) return;
if(settings.aimEnabled()) {
var eye=self.getEyePosition();
var bb=target.getBoundingBox();
var box=new AimModel.Box(new AimModel.Vec(bb.minX,bb.minY,bb.minZ),new AimModel.Vec(bb.maxX,bb.maxY,bb.maxZ));
var desired=AimModel.direction(new AimModel.Vec(eye.x,eye.y,eye.z),AimModel.point(box,.67));
angularError=Math.hypot(AimModel.wrap(desired.yaw()-self.getYRot()),desired.pitch()-self.getXRot());
}
if(settings.triggerEnabled()) {
var candidate=new TargetFilter.Candidate(true,false,true,true,false,false);
var frame=new CombatEngine.Frame(self.getAttackStrengthScale(0),self.onGround(),self.fallDistance,self.isSprinting(),self.isUsingItem() || self.isInWater() || self.isPassenger() || self.onClimbable(),self.distanceToSqr(target)<=9,true,false,responseWindow>0,candidate);
decision=CombatEngine.evaluate(mode,frame);
if(decision!=CombatEngine.Decision.WAIT) {
mc.gameMode.attack(self,target);
self.swing(InteractionHand.MAIN_HAND);
readyTicks++;
responseWindow=0;
}
}
}
private PracticeCombat() {}
}
