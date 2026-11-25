package pvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
final class PracticeCombat {
static boolean golemTest;
static boolean networkTest;
private static LocalPlayer lastPlayer;
private static LivingEntity aimTarget;
private static float lastHealth;
private static int responseWindow;
private static int readyTicks;
private static double angularError=Double.NaN;
private static CombatEngine.Decision decision=CombatEngine.Decision.WAIT;
static double angularError() { return angularError; }
static int readyTicks() { return readyTicks; }
static CombatEngine.Decision decision() { return decision; }
private static boolean valid(LocalPlayer self,LivingEntity target) {
return target!=self && (target instanceof Player || (golemTest && "entity.minecraft.iron_golem".equals(target.getType().getDescriptionId()))) && target.isAlive() && !target.isSpectator() && !target.isInvisible() && self.hasLineOfSight(target) && self.distanceToSqr(target)<=9;
}
static void tick(Minecraft mc, CombatSettings settings, CombatEngine.Mode mode) {
if(mc.player==null || mc.level==null || mc.gameMode==null || (!mc.hasSingleplayerServer() && !networkTest)) {
lastPlayer=null;
aimTarget=null;
angularError=Double.NaN;
decision=CombatEngine.Decision.WAIT;
readyTicks=0;
responseWindow=0;
return;
}
var self=mc.player;
if(lastPlayer!=self) { lastPlayer=self; aimTarget=null; lastHealth=self.getHealth(); responseWindow=0; readyTicks=0; }
if(self.getHealth()<lastHealth) responseWindow=12;
else if(responseWindow>0) responseWindow--;
lastHealth=self.getHealth();
if(mc.gui.screen()!=null) { aimTarget=null; return; }
angularError=Double.NaN;
decision=CombatEngine.Decision.WAIT;
if(!self.isAlive() || self.isSpectator() || self.isCreative() || self.isUsingItem()) return;
if(settings.triggerEnabled() && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target && valid(self,target)) {
var candidate=new TargetFilter.Candidate(true,false,true,true,false,false);
var frame=new CombatEngine.Frame(self.getAttackStrengthScale(0),self.onGround(),self.getDeltaMovement().y<0 ? self.fallDistance : 0,self.isSprinting(),self.isUsingItem() || self.isInWater() || self.isPassenger() || self.onClimbable(),self.distanceToSqr(target)<=9,true,!target.onGround() && target.getDeltaMovement().y<0,responseWindow>0,candidate);
decision=CombatEngine.evaluate(mode,frame);
if(decision!=CombatEngine.Decision.WAIT) {
mc.gameMode.attack(self,target);
self.swingAndResetAttackStrength(InteractionHand.MAIN_HAND,self.getMainHandItem().getAttackAnimation(),false);
readyTicks++;
responseWindow=0;
}
}
if(!settings.aimEnabled()) aimTarget=null;
if(settings.aimEnabled()) {
var eye=self.getEyePosition();
var current=new AimModel.Angles(self.getYRot(),self.getXRot());
AimModel.Angles best=null;
double bestError=settings.fov()/2;
LivingEntity selected=null;
for(var target:mc.level.getEntitiesOfClass(LivingEntity.class,self.getBoundingBox().inflate(3),target -> valid(self,target))) {
var bb=target.getBoundingBox();
var box=new AimModel.Box(new AimModel.Vec(bb.minX,bb.minY,bb.minZ),new AimModel.Vec(bb.maxX,bb.maxY,bb.maxZ));
var desired=AimModel.direction(new AimModel.Vec(eye.x,eye.y,eye.z),AimModel.nearestAimPoint(new AimModel.Vec(eye.x,eye.y,eye.z),current,box));
double error=Math.hypot(AimModel.wrap(desired.yaw()-current.yaw()),desired.pitch()-current.pitch());
if(error>=settings.fov()/2) continue;
if(target==aimTarget || error<bestError) { bestError=error; best=desired; selected=target; }
if(target==aimTarget) break;
}
aimTarget=selected;
if(best!=null) {
angularError=bestError;
if(!(mc.hitResult instanceof EntityHitResult aimed && aimed.getEntity()==selected)) {
var next=AimModel.step(current,best,settings);
self.setYRot((float)next.yaw());
self.setXRot((float)next.pitch());
}
}
}

}
private PracticeCombat() {}
}
