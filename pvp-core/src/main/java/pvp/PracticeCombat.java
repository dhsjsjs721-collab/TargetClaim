package pvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import pvp.mixin.MinecraftAttackInvoker;
import net.minecraft.world.phys.EntityHitResult;
final class PracticeCombat {
static boolean golemTest;
static boolean networkTest;
private static int jumpDelay;
private static int descendingTicks;
private static boolean hitThisFall;
private static long trackingTick;
private static int attackDelay;
private static LocalPlayer lastPlayer;
private static LivingEntity aimTarget;
private static float lastHealth;
private static int responseWindow;
private static int readyTicks;
private static String status="Disabled";
static String status() { return status; }
private static double angularError=Double.NaN;
private static CombatEngine.Decision decision=CombatEngine.Decision.WAIT;
static double angularError() { return angularError; }
static int readyTicks() { return readyTicks; }
static CombatEngine.Decision decision() { return decision; }
private static boolean valid(LocalPlayer self,LivingEntity target) {
return target!=self && (target instanceof Player || (golemTest && "entity.minecraft.iron_golem".equals(target.getType().getDescriptionId()))) && target.isAlive() && !target.isSpectator() && !target.isInvisible() && self.hasLineOfSight(target) && (!(target instanceof Player other) || self.canHarmPlayer(other)) && inRange(self,target);
}
private static boolean inRange(LocalPlayer self, LivingEntity target) {
return self.getAttackRangeWith(self.getMainHandItem()).isInRange(self,target.getBoundingBox(),3.0);
}
static void tick(Minecraft mc, CombatSettings settings) {
if(mc.player==null || mc.level==null || mc.gameMode==null || (!mc.hasSingleplayerServer() && !networkTest)) {
status="Not in enabled world";
lastPlayer=null;
aimTarget=null;
angularError=Double.NaN;
decision=CombatEngine.Decision.WAIT;
readyTicks=0;
responseWindow=0;
jumpDelay=0;
attackDelay=0;
descendingTicks=0;
hitThisFall=false;
trackingTick=0;
return;
}
var self=mc.player;
if(lastPlayer!=self) { lastPlayer=self; aimTarget=null; lastHealth=self.getHealth(); responseWindow=0; readyTicks=0; jumpDelay=0; attackDelay=0; descendingTicks=0; hitThisFall=false; trackingTick=0; }
if(self.getHealth()<lastHealth) responseWindow=12;
else if(responseWindow>0) responseWindow--;
lastHealth=self.getHealth();
if(jumpDelay>0) jumpDelay--;
if(attackDelay>0) attackDelay--;
trackingTick++;
if(mc.gui.screen()!=null || mc.isPaused()) { aimTarget=null; return; }
angularError=Double.NaN;
decision=CombatEngine.Decision.WAIT;
if(!self.isAlive() || self.isSpectator() || self.isCreative() || self.isUsingItem() || self.isHandsBusy()) { aimTarget=null; status="Player busy"; return; }
boolean falling=!self.onGround() && self.getDeltaMovement().y<0 && self.fallDistance>0;
if(self.onGround()) hitThisFall=false;
descendingTicks=falling?Math.min(descendingTicks+1,20):0;
status=settings.triggerEnabled()?"No valid target":"AutoCritical off";
if(settings.triggerEnabled() && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target && valid(self,target)) {
var candidate=new TargetFilter.Candidate(true,false,true,true,false,false);
boolean restricted=self.isInWater() || self.isInLava() || self.isPassenger() || self.onClimbable() || self.isMobilityRestricted() || self.hasEffect(MobEffects.BLINDNESS) || self.isFallFlying();
boolean counter=responseWindow>0 || (!target.onGround() && target.getDeltaMovement().y<0);
var frame=new CombatEngine.Frame(self.getAttackStrengthScale(0),self.onGround(),falling?self.fallDistance:0,self.isSprinting(),restricted,inRange(self,target),true,!target.onGround() && target.getDeltaMovement().y<0,responseWindow>0,candidate);
if(!restricted && self.onGround() && attackDelay==0 && jumpDelay==0 && self.getAttackStrengthScale(0)>=.95f && mc.level.noCollision(self,self.getBoundingBox().move(0,1.1,0))) {
self.setSprinting(false);
self.jumpFromGround();
jumpDelay=5;
status="Auto jump";
}
else if(!restricted && !self.onGround() && !hitThisFall && self.isSprinting()) {
self.setSprinting(false);
status="Stopping sprint";
}
else {
decision=attackDelay>0?CombatEngine.Decision.WAIT:CombatEngine.autoCritical(frame,descendingTicks,hitThisFall);
status=CombatEngine.reason(CombatEngine.Mode.CRITICAL,frame);
if(hitThisFall) status="Waiting for landing";
else if(falling && decision==CombatEngine.Decision.WAIT && status.equals("Attack ready")) status="Waiting for descent tick";
if(decision!=CombatEngine.Decision.WAIT) {
((MinecraftAttackInvoker)mc).pvp$startAttack();
hitThisFall=true;
attackDelay=3;
readyTicks++;
status=counter?"Counter attempt":"Critical attempt";
responseWindow=0;
}
}
}
if(!settings.aimEnabled()) aimTarget=null;
if(settings.aimEnabled()) {
var eye=self.getEyePosition();
var current=new AimModel.Angles(self.getYRot(),self.getXRot());
AimModel.Angles best=null;
double bestError=Double.POSITIVE_INFINITY,bestScore=Double.POSITIVE_INFINITY,retainedScore=Double.POSITIVE_INFINITY,retainedError=0;
AimModel.Angles retained=null;
LivingEntity selected=null;
for(var target:mc.level.getEntitiesOfClass(LivingEntity.class,self.getBoundingBox().inflate(3),target -> valid(self,target))) {
var bb=target.getBoundingBox();
var box=new AimModel.Box(new AimModel.Vec(bb.minX,bb.minY,bb.minZ),new AimModel.Vec(bb.maxX,bb.maxY,bb.maxZ));
var desired=AimModel.direction(new AimModel.Vec(eye.x,eye.y,eye.z),AimModel.trackingPoint(new AimModel.Vec(eye.x,eye.y,eye.z),current,box,trackingTick));
double error=AimModel.error(current,desired);
if(error>settings.fov()/2) continue;
double score=AimModel.targetScore(error,settings.fov(),self.distanceToSqr(target));
if(target==aimTarget) { retainedScore=score; retainedError=error; retained=desired; }
if(score<bestScore) { bestScore=score; bestError=error; best=desired; selected=target; }
}
if(retained!=null && AimModel.retainTarget(bestScore,retainedScore)) { best=retained; bestError=retainedError; selected=aimTarget; }
aimTarget=selected;
if(best!=null) {
angularError=bestError;
if(bestError>.10) {
var next=AimModel.step(current,best,settings);
self.setYRot(self.getYRot()+(float)AimModel.wrap(next.yaw()-current.yaw()));
self.setXRot((float)next.pitch());
}
}
}

}
private PracticeCombat() {}
}
