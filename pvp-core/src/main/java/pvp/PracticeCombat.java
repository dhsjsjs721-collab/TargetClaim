package pvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import pvp.mixin.MinecraftAttackInvoker;
import net.minecraft.world.phys.EntityHitResult;
import java.util.ArrayDeque;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
public final class PracticeCombat {
static boolean golemTest;
static boolean networkTest;
static boolean autoMove=true;
private static final double AIM_RANGE=6.0;
private record Pending(int entityId,long tick) {}
private static final ArrayDeque<Pending> pending=new ArrayDeque<>();
private static int confirmedHits,expiredHits,jumpCount;
private static int jumpPressTicks;
private static int blockedJumpTicks;
static int confirmedHits() { return confirmedHits; }
static int expiredHits() { return expiredHits; }
static int jumpCount() { return jumpCount; }
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
return target!=self && (target instanceof Player || (golemTest && "entity.minecraft.iron_golem".equals(target.getType().getDescriptionId()))) && target.isAlive() && !target.isSpectator() && !target.isInvisible() && self.hasLineOfSight(target) && (!(target instanceof Player other) || self.canHarmPlayer(other)) && self.distanceToSqr(target)<=AIM_RANGE*AIM_RANGE;
}
private static boolean inRange(LocalPlayer self, LivingEntity target) {
return self.getAttackRangeWith(self.getMainHandItem()).isInRange(self,target.getBoundingBox(),0.0);
}
private static boolean enabled(Minecraft mc) {
return mc.player!=null && mc.level!=null && mc.gameMode!=null && (mc.hasSingleplayerServer() || networkTest);
}
private static boolean available(Minecraft mc,LocalPlayer self) {
return mc.gui.screen()==null && !mc.isPaused() && mc.getCameraEntity()==self && self.isAlive() && !self.isSpectator() && !self.isCreative() && !self.isUsingItem() && !self.isHandsBusy();
}
private static boolean restricted(LocalPlayer self) {
return self.isInWater() || self.isInLava() || self.isPassenger() || self.onClimbable() || self.isMobilityRestricted() || self.hasEffect(MobEffects.BLINDNESS) || self.isFallFlying() || self.getAbilities().flying;
}
private static void resetWorld(Minecraft mc) {
lastPlayer=mc.player; lastLevel=mc.level; clearJump(); aimTarget=null; resetTracking();
lastHealth=mc.player==null?0:mc.player.getHealth();
responseWindow=readyTicks=attackDelay=jumpDelay=descendingTicks=confirmedHits=expiredHits=jumpCount=blockedJumpTicks=0;
trackingTick=0; hitThisFall=false; angularError=Double.NaN; decision=CombatEngine.Decision.WAIT; pending.clear();
}
static void tick(Minecraft mc,CombatSettings settings) {
if(!enabled(mc)) { status="Not in enabled world"; resetWorld(mc); return; }
var self=mc.player;
if(lastPlayer!=self || lastLevel!=mc.level) resetWorld(mc);
if(!available(mc,self)) { clearJump(); descendingTicks=0; return; }
if(self.getHealth()<lastHealth) responseWindow=12;
else if(responseWindow>0) responseWindow--;
lastHealth=self.getHealth();
if(jumpDelay>0) jumpDelay--;
if(attackDelay>0) attackDelay--;
if(blockedJumpTicks>0) blockedJumpTicks--;
while(!pending.isEmpty() && trackingTick-pending.peekFirst().tick()>40) { pending.removeFirst(); expiredHits++; }
boolean falling=!self.onGround() && self.getDeltaMovement().y<0 && self.fallDistance>0;
descendingTicks=falling?Math.min(descendingTicks+1,20):0;
if(self.onGround()) {
hitThisFall=false;
if(jumpTarget!=null && !jumpRequested && jumpAge>2) clearJump();
} else if(jumpRequested) { jumpRequested=false; jumpPressTicks=0; jumpCount++; }
if(jumpTarget!=null && (++jumpAge>26 || !aimValid(self,jumpTarget) || jumpItem!=self.getMainHandItem().getItem())) clearJump();
if(!settings.triggerEnabled()) { clearJump(); status="AutoCritical off"; return; }
decision=CombatEngine.Decision.WAIT;
EntityHitResult hit=pickTarget(mc,self);
LivingEntity crosshair=hit!=null && hit.getEntity() instanceof LivingEntity living && valid(self,living)?living:null;
LivingEntity target=crosshair!=null?crosshair:settings.aimEnabled() && aimTarget!=null && valid(self,aimTarget) && Double.isFinite(angularError) && angularError<9?aimTarget:null;
if(target==null) { status=aimTarget!=null?"Approach / align":"No valid target"; return; }
boolean restricted=restricted(self);
boolean canJump=!restricted && !self.isShiftKeyDown() && mc.level.noCollision(self,self.getBoundingBox().move(0,.35,0)) && blockedJumpTicks==0;
boolean counter=responseWindow>0 || (!target.onGround() && target.getDeltaMovement().y<0);
float cooldown=self.getAttackStrengthScale(.5f);
boolean itemReady=!self.cannotAttackWithItem(self.getMainHandItem(),0);
var frame=new CombatEngine.Frame(cooldown,self.onGround(),falling?self.fallDistance:0,self.isSprinting(),restricted,inRange(self,target),crosshair==target,!target.onGround() && target.getDeltaMovement().y<0,responseWindow>0,new TargetFilter.Candidate(true,false,true,true,false,false));
var action=CombatEngine.plan(frame,descendingTicks,hitThisFall,canJump,jumpRequested,attackDelay==0 && itemReady,jumpDelay==0);
status=action.name();
switch(action) {
case JUMP -> { jumpRequested=true; jumpPressTicks=3; jumpDelay=5; jumpTarget=target; jumpItem=self.getMainHandItem().getItem(); jumpAge=0; }
case STOP_SPRINT -> self.setSprinting(false);
case CRITICAL, NORMAL -> {
if(attack(mc,self,hit)) {
if(!self.onGround()) hitThisFall=true;
attackDelay=2;
clearJump();
readyTicks++;
pending.addLast(new Pending(target.getId(),trackingTick));
while(pending.size()>32) { pending.removeFirst(); expiredHits++; }
status=action==CombatEngine.Action.CRITICAL?(counter?"Counter attempt":"Critical attempt"):"Ground attack";
decision=action==CombatEngine.Action.CRITICAL?CombatEngine.Decision.CRITICAL_WINDOW:CombatEngine.Decision.NORMAL_WINDOW;
responseWindow=0;
} else { status="Attack not consumed"; attackDelay=1; }
}
default -> {}
}
}
private static void updateAim(Minecraft mc,LocalPlayer self,CombatSettings settings) {
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
if(target==jumpTarget && settings.triggerEnabled()) score*=.35;
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
angularError=AimModel.error(new AimModel.Angles(self.getYRot(),self.getXRot()),best);
}
} else resetTracking();
}
}
private static void resetTracking() { sampledTarget=null; previousRelative=null; relativeMotion=new AimModel.Vec(0,0,0); motionAngle=0; }
static void startTick(Minecraft mc,CombatSettings settings) {
if(!enabled(mc)) { resetWorld(mc); return; }
var self=mc.player;
if(lastPlayer!=self || lastLevel!=mc.level) resetWorld(mc);
if(!available(mc,self)) { clearJump(); aimTarget=null; resetTracking(); return; }
trackingTick++;
updateAim(mc,self,settings);
if(!settings.triggerEnabled()) { clearJump(); return; }
boolean critActive=jumpTarget!=null && aimValid(self,jumpTarget) && !restricted(self);
if(critActive || (!self.onGround() && !hitThisFall && aimTarget!=null && valid(self,aimTarget) && !restricted(self))) {
if(mc.options.keySprint.isDown()) { mc.options.keySprint.setDown(false); forcedSprint=true; }
self.setSprinting(false);
}
if(jumpRequested && critActive && self.onGround() && valid(self,jumpTarget) && !self.isShiftKeyDown()) {
if(jumpPressTicks>0) {
if(!mc.options.keyJump.isDown()) { mc.options.keyJump.setDown(true); forcedJump=true; }
jumpPressTicks--;
} else { blockedJumpTicks=12; clearJump(); status="Jump blocked"; }
} else if(jumpRequested && self.onGround() && (!critActive || !valid(self,jumpTarget))) clearJump();
if(autoMove && settings.aimEnabled() && aimTarget!=null && aimValid(self,aimTarget) && !self.isShiftKeyDown() && !mc.options.keyDown.isDown() && !mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown() && !mc.options.keyUp.isDown()) {
double dx=aimTarget.getX()-self.getX(),dz=aimTarget.getZ()-self.getZ();
if(Math.abs(aimTarget.getY()-self.getY())<1.5 && !self.isInWater() && !self.isPassenger()) {
var move=AimModel.flank(dx,dz,aimTarget.getYRot(),self.getYRot());
double y=Math.toRadians(self.getYRot());
double forward=(move.forward()?1:0)-(move.back()?1:0), right=(move.right()?1:0)-(move.left()?1:0);
double mx=(-Math.sin(y)*forward-Math.cos(y)*right)*.28,mz=(Math.cos(y)*forward-Math.sin(y)*right)*.28;
var next=self.getBoundingBox().move(mx,0,mz);
if(mc.level.noCollision(self,next) && (!self.onGround() || !mc.level.noCollision(self,next.move(0,-.7,0)))) {
if(move.forward()) { mc.options.keyUp.setDown(true); forcedForward=true; }
if(move.back()) { mc.options.keyDown.setDown(true); forcedBack=true; }
if(move.left()) { mc.options.keyLeft.setDown(true); forcedLeft=true; }
if(move.right()) { mc.options.keyRight.setDown(true); forcedRight=true; }
}
}
}
}
static void releaseInput(Minecraft mc) {
if(forcedJump) mc.options.keyJump.setDown(false);
if(forcedForward) mc.options.keyUp.setDown(false);
if(forcedBack) mc.options.keyDown.setDown(false);
if(forcedLeft) mc.options.keyLeft.setDown(false);
if(forcedRight) mc.options.keyRight.setDown(false);
if(forcedSprint) mc.options.keySprint.setDown(true);
forcedJump=forcedForward=forcedSprint=forcedBack=forcedLeft=forcedRight=false;
}
private static EntityHitResult pickTarget(Minecraft mc,LocalPlayer self) {
var result=self.raycastHitResult(1.0f,self);
return result instanceof EntityHitResult hit?hit:null;
}
public static void recordDamage(ClientboundDamageEventPacket packet) {
var mc=Minecraft.getInstance();
if(mc.player==null || mc.level==null || lastPlayer!=mc.player || lastLevel!=mc.level) return;
if(packet.sourceCauseId()!=mc.player.getId() || packet.sourceDirectId()!=mc.player.getId()) return;
for(var it=pending.iterator();it.hasNext();) {
var attempt=it.next();
if(attempt.entityId()==packet.entityId() && trackingTick-attempt.tick()<=40) { it.remove(); confirmedHits++; break; }
}
}
private static boolean attack(Minecraft mc,LocalPlayer self,EntityHitResult hit) {
if(hit==null) return false;
var previous=mc.hitResult;
float before=self.getAttackStrengthScale(.5f);
try { mc.hitResult=hit; ((MinecraftAttackInvoker)mc).pvp$startAttack(); }
finally { mc.hitResult=previous; }
return self.getAttackStrengthScale(.5f)<before;
}
private static void clearJump() { jumpTarget=null; jumpItem=null; jumpAge=0; jumpRequested=false; jumpPressTicks=0; }
private PracticeCombat() {}
}
