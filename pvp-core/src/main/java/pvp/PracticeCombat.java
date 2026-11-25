package pvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import pvp.mixin.MinecraftAttackInvoker;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.ClipContext;
final class PracticeCombat {
static boolean golemTest;
static boolean networkTest;
static boolean autoMove=true;
private static final double ATTACK_RANGE=3.2;
private static int jumpDelay;
private static boolean jumpRequested,forcedJump,forcedForward,forcedSprint,forcedBack,forcedLeft,forcedRight;
private static int descendingTicks;
private static boolean hitThisFall;
private static long trackingTick;
private static int attackDelay;
private static LocalPlayer lastPlayer;
private static Object lastLevel;
private static LivingEntity jumpTarget;
private static Object jumpItem;
private static int jumpAge;
private static LivingEntity aimTarget;
private static LivingEntity sampledTarget;
private static AimModel.Vec previousRelative;
private static AimModel.Vec relativeMotion=new AimModel.Vec(0,0,0);
private static double motionAngle;
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
private static boolean valid(LocalPlayer self,LivingEntity target) { return aimValid(self,target) && inRange(self,target); }
private static boolean aimValid(LocalPlayer self,LivingEntity target) {
return target!=self && (target instanceof Player || (golemTest && "entity.minecraft.iron_golem".equals(target.getType().getDescriptionId()))) && target.isAlive() && !target.isSpectator() && !target.isInvisible() && self.hasLineOfSight(target) && (!(target instanceof Player other) || self.canHarmPlayer(other)) && self.distanceToSqr(target)<=36;
}
private static boolean inRange(LocalPlayer self, LivingEntity target) {
return self.getAttackRangeWith(self.getMainHandItem()).isInRange(self,target.getBoundingBox(),ATTACK_RANGE);
}
static void tick(Minecraft mc, CombatSettings settings) {
if(mc.player==null || mc.level==null || mc.gameMode==null || (!mc.hasSingleplayerServer() && !networkTest)) {
status="Not in enabled world";
lastPlayer=null;
lastLevel=null;
clearJump();
aimTarget=null; resetTracking();
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
if(lastPlayer!=self || lastLevel!=mc.level) { lastPlayer=self; lastLevel=mc.level; clearJump(); aimTarget=null; resetTracking(); lastHealth=self.getHealth(); responseWindow=0; readyTicks=0; jumpDelay=0; attackDelay=0; descendingTicks=0; hitThisFall=false; trackingTick=0; }
if(self.getHealth()<lastHealth) responseWindow=12;
else if(responseWindow>0) responseWindow--;
lastHealth=self.getHealth();
if(jumpDelay>0) jumpDelay--;
if(attackDelay>0) attackDelay--;
trackingTick++;
if(mc.gui.screen()!=null || mc.isPaused()) { aimTarget=null; resetTracking(); clearJump(); descendingTicks=0; return; }
double previousAimError=angularError;
angularError=Double.NaN;
decision=CombatEngine.Decision.WAIT;
if(!self.isAlive() || self.isSpectator() || self.isCreative() || self.isUsingItem() || self.isHandsBusy()) { aimTarget=null; resetTracking(); clearJump(); descendingTicks=0; status="Player busy"; return; }
boolean falling=!self.onGround() && self.getDeltaMovement().y<0 && self.fallDistance>0;
if(self.onGround()) { hitThisFall=false; if(jumpAge>2) clearJump(); }
if(!settings.triggerEnabled() || (jumpTarget!=null && (++jumpAge>24 || !aimValid(self,jumpTarget) || jumpItem!=self.getMainHandItem().getItem()))) clearJump();
descendingTicks=falling?Math.min(descendingTicks+1,20):0;
status=settings.triggerEnabled()?"No valid target":"AutoCritical off";
EntityHitResult freshHit=pickTarget(mc,self);
LivingEntity crosshair=freshHit!=null && freshHit.getEntity() instanceof LivingEntity living && valid(self,living)?living:null;
LivingEntity candidateTarget=crosshair!=null?crosshair:settings.aimEnabled() && aimTarget!=null && valid(self,aimTarget) && Double.isFinite(previousAimError) && previousAimError<12?aimTarget:null;
if(settings.triggerEnabled() && candidateTarget!=null) {
var target=candidateTarget;
var candidate=new TargetFilter.Candidate(true,false,true,true,false,false);
boolean restricted=self.isInWater() || self.isInLava() || self.isPassenger() || self.onClimbable() || self.isMobilityRestricted() || self.hasEffect(MobEffects.BLINDNESS) || self.isFallFlying();
boolean counter=responseWindow>0 || (!target.onGround() && target.getDeltaMovement().y<0);
var frame=new CombatEngine.Frame(self.getAttackStrengthScale(0),self.onGround(),falling?self.fallDistance:0,self.isSprinting(),restricted,inRange(self,target),crosshair==target,!target.onGround() && target.getDeltaMovement().y<0,responseWindow>0,candidate);
if(!restricted && self.onGround() && attackDelay==0 && jumpDelay==0 && self.getAttackStrengthScale(0)>=.85f && mc.level.noCollision(self,self.getBoundingBox().move(0,.35,0))) {
self.setSprinting(false);
jumpRequested=true;
jumpDelay=5;
jumpTarget=target;
jumpItem=self.getMainHandItem().getItem();
jumpAge=0;
status="Auto jump";
}
else if(jumpTarget!=null && jumpTarget!=target) { status="Waiting for jump target"; }
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
if(attack(mc,self,freshHit)) {
hitThisFall=true;
attackDelay=3;
clearJump();
readyTicks++;
status=counter?"Counter attack":"Critical attack";
responseWindow=0;
} else { status="Attack not consumed"; attackDelay=1; }
}
}
}
if(!settings.aimEnabled()) { aimTarget=null; resetTracking(); }
if(settings.aimEnabled()) {
var eye=self.getEyePosition();
var current=new AimModel.Angles(self.getYRot(),self.getXRot());
AimModel.Angles best=null;
double bestError=Double.POSITIVE_INFINITY,bestScore=Double.POSITIVE_INFINITY,retainedScore=Double.POSITIVE_INFINITY,retainedError=0;
AimModel.Angles retained=null;
LivingEntity selected=null;
for(var target:mc.level.getEntitiesOfClass(LivingEntity.class,self.getBoundingBox().inflate(6),target -> aimValid(self,target))) {
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
if(best!=null && selected!=null) {
var bb=selected.getBoundingBox();
var box=new AimModel.Box(new AimModel.Vec(bb.minX,bb.minY,bb.minZ),new AimModel.Vec(bb.maxX,bb.maxY,bb.maxZ));
var origin=new AimModel.Vec(eye.x,eye.y,eye.z);
var center=AimModel.point(box,.65);
var relative=new AimModel.Vec(center.x()-eye.x,center.y()-eye.y,center.z()-eye.z);
if(sampledTarget==selected && previousRelative!=null) {
var delta=new AimModel.Vec(relative.x()-previousRelative.x(),relative.y()-previousRelative.y(),relative.z()-previousRelative.z());
relativeMotion=new AimModel.Vec(relativeMotion.x()*.35+delta.x()*.65,relativeMotion.y()*.35+delta.y()*.65,relativeMotion.z()*.35+delta.z()*.65);
var zero=new AimModel.Vec(0,0,0);
motionAngle=AimModel.error(AimModel.direction(zero,previousRelative),AimModel.direction(zero,relative));
} else resetTracking();
sampledTarget=selected;
previousRelative=relative;
best=AimModel.direction(origin,AimModel.movingPoint(AimModel.trackingPoint(origin,current,box,trackingTick),box,relativeMotion));
bestError=AimModel.error(current,best);
angularError=bestError;
if(bestError>.10) {
var next=AimModel.movingStep(current,best,settings,motionAngle);
self.setYRot(self.getYRot()+(float)AimModel.wrap(next.yaw()-current.yaw()));
self.setXRot((float)next.pitch());
}
} else resetTracking();
}

}
private static void resetTracking() { sampledTarget=null; previousRelative=null; relativeMotion=new AimModel.Vec(0,0,0); motionAngle=0; }
static void startTick(Minecraft mc,CombatSettings settings) {
if(mc.player==null || mc.level==null || mc.gui.screen()!=null || mc.isPaused() || (!mc.hasSingleplayerServer() && !networkTest) || !settings.triggerEnabled()) { jumpRequested=false; return; }
var self=mc.player;
if(!self.isAlive() || self.isSpectator() || self.isCreative() || self.isUsingItem() || self.isShiftKeyDown()) { jumpRequested=false; return; }
if(jumpTarget!=null && aimValid(self,jumpTarget)) {
if(mc.options.keySprint.isDown()) { mc.options.keySprint.setDown(false); forcedSprint=true; }
self.setSprinting(false);
}
if(jumpRequested && jumpTarget!=null && self.onGround() && valid(self,jumpTarget)) {
if(!mc.options.keyJump.isDown()) { mc.options.keyJump.setDown(true); forcedJump=true; }
}
jumpRequested=false;
if(autoMove && settings.aimEnabled() && aimTarget!=null && aimValid(self,aimTarget) && !mc.options.keyDown.isDown() && !mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown() && !mc.options.keyUp.isDown()) {
double dx=aimTarget.getX()-self.getX(),dz=aimTarget.getZ()-self.getZ();
if(Math.abs(aimTarget.getY()-self.getY())<1.5 && !self.horizontalCollision && !self.isInWater() && !self.isPassenger()) {
var move=AimModel.flank(dx,dz,aimTarget.getYRot(),self.getYRot());
if(move.forward()) { mc.options.keyUp.setDown(true); forcedForward=true; }
if(move.back()) { mc.options.keyDown.setDown(true); forcedBack=true; }
if(move.left()) { mc.options.keyLeft.setDown(true); forcedLeft=true; }
if(move.right()) { mc.options.keyRight.setDown(true); forcedRight=true; }
}
}
}
static void releaseInput(Minecraft mc) {
if(forcedJump || forcedForward || forcedSprint || forcedBack || forcedLeft || forcedRight) { KeyMapping.setAll(); forcedJump=false; forcedForward=false; forcedSprint=false; forcedBack=false; forcedLeft=false; forcedRight=false; }
}
private static EntityHitResult pickTarget(Minecraft mc,LocalPlayer self) {
var eye=self.getEyePosition();
var end=eye.add(self.getViewVector(1.0f).scale(ATTACK_RANGE));
var block=mc.level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,self));
double nearest=eye.distanceToSqr(block.getLocation());
EntityHitResult result=null;
for(var e:mc.level.getEntitiesOfClass(LivingEntity.class,self.getBoundingBox().inflate(ATTACK_RANGE+1),e->e!=self && e.isAlive() && !e.isSpectator())) {
var box=e.getBoundingBox();
var point=box.contains(eye)?java.util.Optional.of(eye):box.clip(eye,end);
if(point.isEmpty()) continue;
double dist=eye.distanceToSqr(point.get());
if(dist<=nearest) { nearest=dist; result=new EntityHitResult(e,point.get()); }
}
return result;
}
private static boolean attack(Minecraft mc,LocalPlayer self,EntityHitResult hit) {
if(hit==null) return false;
var previous=mc.hitResult;
float before=self.getAttackStrengthScale(0);
try { mc.hitResult=hit; ((MinecraftAttackInvoker)mc).pvp$startAttack(); }
finally { mc.hitResult=previous; }
return self.getAttackStrengthScale(0)<before;
}
private static void clearJump() { jumpTarget=null; jumpItem=null; jumpAge=0; jumpRequested=false; }
private PracticeCombat() {}
}
