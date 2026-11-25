package pvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import pvp.mixin.MinecraftAttackInvoker;
import net.minecraft.world.phys.EntityHitResult;
final class PracticeCombat {
static boolean golemTest;
static boolean networkTest;
static boolean autoJump=true;
private static int jumpDelay;
private static long trackingTick;
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
static void tick(Minecraft mc, CombatSettings settings, CombatEngine.Mode mode) {
if(mc.player==null || mc.level==null || mc.gameMode==null || (!mc.hasSingleplayerServer() && !networkTest)) {
status="Not in enabled world";
lastPlayer=null;
aimTarget=null;
angularError=Double.NaN;
decision=CombatEngine.Decision.WAIT;
readyTicks=0;
responseWindow=0;
jumpDelay=0;
trackingTick=0;
return;
}
var self=mc.player;
if(lastPlayer!=self) { lastPlayer=self; aimTarget=null; lastHealth=self.getHealth(); responseWindow=0; readyTicks=0; jumpDelay=0; trackingTick=0; }
if(self.getHealth()<lastHealth) responseWindow=12;
else if(responseWindow>0) responseWindow--;
lastHealth=self.getHealth();
if(jumpDelay>0) jumpDelay--;
trackingTick++;
if(mc.gui.screen()!=null || mc.isPaused()) { aimTarget=null; return; }
angularError=Double.NaN;
decision=CombatEngine.Decision.WAIT;
if(!self.isAlive() || self.isSpectator() || self.isCreative() || self.isUsingItem() || self.isHandsBusy()) { aimTarget=null; status="Player busy"; return; }
status=settings.triggerEnabled()?"No valid target":"Trigger off";
if(settings.triggerEnabled() && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target && valid(self,target)) {
var candidate=new TargetFilter.Candidate(true,false,true,true,false,false);
boolean criticalMode=mode==CombatEngine.Mode.CRITICAL || mode==CombatEngine.Mode.COUNTER_CRITICAL;
boolean falling=!self.onGround() && self.getDeltaMovement().y<0 && self.fallDistance>0;
boolean restricted=self.isInWater() || self.isPassenger() || self.onClimbable() || self.isMobilityRestricted();
boolean counterReady=mode!=CombatEngine.Mode.COUNTER_CRITICAL || responseWindow>0 || (!target.onGround() && target.getDeltaMovement().y<0);
if(autoJump && criticalMode && counterReady && !restricted && self.onGround() && jumpDelay==0 && self.getAttackStrengthScale(0)>=.95f && mc.level.noCollision(self,self.getBoundingBox().move(0,1.1,0))) {
self.setSprinting(false);
self.jumpFromGround();
jumpDelay=5;
status="Auto jump";
}
else if(!restricted && criticalMode && falling && self.isSprinting() && self.getAttackStrengthScale(0)>=.95f) { self.setSprinting(false); status="Stopping sprint"; }
else {
var frame=new CombatEngine.Frame(self.getAttackStrengthScale(0),self.onGround(),self.getDeltaMovement().y<0 ? self.fallDistance : 0,self.isSprinting(),restricted,inRange(self,target),true,!target.onGround() && target.getDeltaMovement().y<0,responseWindow>0,candidate);
decision=CombatEngine.evaluate(mode,frame);
status=CombatEngine.reason(mode,frame);
if(decision!=CombatEngine.Decision.WAIT) {
((MinecraftAttackInvoker)mc).pvp$startAttack();
readyTicks++;
responseWindow=0;
}
}
}
if(!settings.aimEnabled()) aimTarget=null;
if(settings.aimEnabled()) {
var eye=self.getEyePosition();
var current=new AimModel.Angles(self.getYRot(),self.getXRot());
AimModel.Angles best=null;
double bestError=Double.POSITIVE_INFINITY;
LivingEntity selected=null;
for(var target:mc.level.getEntitiesOfClass(LivingEntity.class,self.getBoundingBox().inflate(3),target -> valid(self,target))) {
var bb=target.getBoundingBox();
var box=new AimModel.Box(new AimModel.Vec(bb.minX,bb.minY,bb.minZ),new AimModel.Vec(bb.maxX,bb.maxY,bb.maxZ));
var desired=AimModel.direction(new AimModel.Vec(eye.x,eye.y,eye.z),AimModel.trackingPoint(new AimModel.Vec(eye.x,eye.y,eye.z),current,box,trackingTick));
double error=AimModel.error(current,desired);
if(error>settings.fov()/2) continue;
if(target==aimTarget || error<bestError) { bestError=error; best=desired; selected=target; }
if(target==aimTarget) break;
}
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
