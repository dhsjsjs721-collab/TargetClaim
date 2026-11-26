package pvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
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
static boolean autoMove=false;
private record ServerTarget(Object world,LocalPlayer player,java.util.UUID id,String name,LivingEntity entity) {}
private static ServerTarget serverTarget;
private static boolean localPractice(Minecraft mc) {
return mc.hasSingleplayerServer() && mc.getSingleplayerServer()!=null && !mc.getSingleplayerServer().isPublished();
}
private static ServerTarget currentServerTarget(Minecraft mc) {
if(serverTarget!=null && (serverTarget.world()!=mc.level || serverTarget.player()!=mc.player || !serverTarget.entity().isAlive())) serverTarget=null;
return serverTarget;
}
static boolean hasLockedTarget(Minecraft mc) { return currentServerTarget(mc)!=null; }
static void clearServerTarget() { serverTarget=null; clearJump(); aimTarget=null; resetTracking(); resetStyle(); }
static String selectNearestPracticeTarget(Minecraft mc) {
if(mc.player==null || mc.level==null || mc.gameMode==null || !available(mc,mc.player)) return null;
var self=mc.player;
boolean local=localPractice(mc);
LivingEntity nearest=null;
double best=AIM_RANGE*AIM_RANGE;
for(var target:mc.level.getEntitiesOfClass(LivingEntity.class,self.getBoundingBox().inflate(AIM_RANGE),entity -> entity!=self && (local?isGolem(entity):entity instanceof Player))) {
if(!target.isAlive() || target.isInvisible() || target.isSpectator() || !self.hasLineOfSight(target) || (target instanceof Player other && !self.canHarmPlayer(other))) continue;
double distance=self.distanceToSqr(target);
if(!TargetFilter.nearer(distance,best,nearest==null)) continue;
nearest=target; best=distance;
}
if(nearest==null) return null;
clearServerTarget();
serverTarget=new ServerTarget(mc.level,self,nearest.getUUID(),nearest.getName().getString(),nearest);
return nearest.getName().getString();
}
static String serverTargetLabel(Minecraft mc) {
if(localPractice(mc)) return "Local practice only";
var target=currentServerTarget(mc);
return target==null?"Select server practice target":"Clear target: "+target.name();
}
static String toggleServerTarget(Minecraft mc) {
if(localPractice(mc) || mc.player==null || mc.level==null || mc.gameMode==null) return "Join a practice server first";
if(currentServerTarget(mc)!=null) { clearServerTarget(); return serverTargetLabel(mc); }
var self=mc.player;
var hit=pickTarget(mc,self);
if(hit==null || !(hit.getEntity() instanceof LivingEntity target) || target==self || !target.isAlive() || target.isInvisible() || target.isSpectator() || !self.hasLineOfSight(target) || (!(target instanceof Player) && !isGolem(target)) || (target instanceof Player other && !self.canHarmPlayer(other))) return "Aim at your practice bot first";
serverTarget=new ServerTarget(mc.level,self,target.getUUID(),target.getName().getString(),target);
clearJump(); aimTarget=null; resetTracking();
return serverTargetLabel(mc);
}
private static boolean isGolem(LivingEntity target) { return "entity.minecraft.iron_golem".equals(target.getType().getDescriptionId()); }
private static final double AIM_RANGE=6.0,FOLLOW_RANGE=12.0;
private record Pending(int entityId,long tick) {}
private static final ArrayDeque<Pending> pending=new ArrayDeque<>();
private static final CombatTrace trace=new CombatTrace();
private static String previousReport="";
private static boolean sessionActive;
private static float startStrength=Float.NaN;
private static Object startItem;
private static double startWeaponDelay;
private static long manualTick=-1;
private static Object lastWeapon;
private static double lastWeaponDelay=Double.NaN;
static String diagnostics() { return "Current status: "+status+"\n"+(sessionActive?trace.report():previousReport.isEmpty()?trace.report():previousReport); }
private static void setStatus(String value) { status=value; trace.state(trackingTick,value); }
private static void expirePending() { while(!pending.isEmpty() && trackingTick-pending.peekFirst().tick()>40) { pending.removeFirst(); expiredHits++; trace.expire(trackingTick); } }
private static int confirmedHits,expiredHits,jumpCount;
private static int jumpPressTicks;
private static int blockedJumpTicks;
private static int readyWait;
private static LivingEntity waitingTarget;
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
private static AimModel.Vec previousRelative,previousEye;
private static AimModel.Vec targetMotion=new AimModel.Vec(0,0,0);
private static AimModel.Vec relativeMotion=new AimModel.Vec(0,0,0);
private static double motionAngle;
private static final CombatAI.Tracker tracker=new CombatAI.Tracker();
private static final CombatAI.JumpTiming jumpTiming=new CombatAI.JumpTiming();
private static long requestedJumpTick=-1;
private static boolean observedAscent;
private static int consecutiveNoRay;
private static AimModel.Movement lastMovement=new AimModel.Movement(false,false,false,false);
private static AimModel.Turn turn=new AimModel.Turn(0,0);
private static float lastHealth;
private static int responseWindow;
private static final CombatEngine.Recovery recovery=new CombatEngine.Recovery();
private static void incomingDamage() { if(recovery.hit(trackingTick)) trace.event(trackingTick,"INCOMING_DAMAGE"); responseWindow=12; }
private static boolean comboMode;
private static LivingEntity styleTarget;
private static void resetStyle() { comboMode=false; styleTarget=null; }
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
var mc=Minecraft.getInstance();
var selected=currentServerTarget(mc);
boolean chosen=selected!=null && selected.id().equals(target.getUUID());
return target!=self && TargetFilter.lockedPracticeTarget(localPractice(mc),golemTest,isGolem(target),selected!=null,chosen) && target.isAlive() && !target.isSpectator() && !target.isInvisible() && self.hasLineOfSight(target) && (!(target instanceof Player other) || self.canHarmPlayer(other)) && self.distanceToSqr(target)<=(chosen && autoMove?FOLLOW_RANGE*FOLLOW_RANGE:AIM_RANGE*AIM_RANGE);
}
private static boolean inRange(LocalPlayer self, LivingEntity target) {
return self.getAttackRangeWith(self.getMainHandItem()).isInRange(self,target.getBoundingBox(),0.0);
}
private static boolean enabled(Minecraft mc) {
var selected=currentServerTarget(mc);
return mc.player!=null && mc.level!=null && mc.gameMode!=null && (localPractice(mc) || selected!=null);
}
private static boolean available(Minecraft mc,LocalPlayer self) {
return mc.gui.screen()==null && !mc.isPaused() && mc.getCameraEntity()==self && self.isAlive() && !self.isSpectator() && !self.isCreative() && !self.isUsingItem() && !self.isHandsBusy();
}
private static String unavailableReason(Minecraft mc,LocalPlayer self) {
if(mc.isPaused()) return "Paused";
if(mc.getCameraEntity()!=self) return "Different camera entity";
if(!self.isAlive()) return "Player dead";
if(self.isSpectator()) return "Spectator mode";
if(self.isCreative()) return "Creative mode";
if(self.isUsingItem() || self.isHandsBusy()) return "Hands busy";
return "Unavailable";
}
private static boolean restricted(LocalPlayer self) {
return self.isInWater() || self.isInLava() || self.isPassenger() || self.onClimbable() || self.isMobilityRestricted() || self.hasEffect(MobEffects.BLINDNESS) || self.isFallFlying() || self.getAbilities().flying;
}
private static void endSession() {
if(sessionActive) { previousReport=trace.report(); sessionActive=false; }
}
private static void resetWorld(Minecraft mc) {
endSession(); resetStyle(); recovery.reset();
lastPlayer=mc.player; lastLevel=mc.level; clearJump(); aimTarget=null; resetTracking();
lastHealth=mc.player==null?0:mc.player.getHealth();
responseWindow=readyTicks=attackDelay=jumpDelay=descendingTicks=confirmedHits=expiredHits=jumpCount=blockedJumpTicks=readyWait=0;
waitingTarget=null;
jumpTiming.reset(); consecutiveNoRay=0;
trackingTick=0; hitThisFall=false; angularError=Double.NaN; decision=CombatEngine.Decision.WAIT; pending.clear(); trace.clear();
startStrength=Float.NaN; startItem=null; startWeaponDelay=0; manualTick=-1; lastWeapon=null; lastWeaponDelay=Double.NaN;
}
static void tick(Minecraft mc,CombatSettings settings) {
if(!enabled(mc)) { if(sessionActive) resetWorld(mc); status=mc.player!=null && !localPractice(mc)?"Select server practice target":"Not in enabled world"; return; }
var self=mc.player;
if(lastPlayer!=self || lastLevel!=mc.level) resetWorld(mc);
sessionActive=true;
expirePending();
if(!available(mc,self)) { clearJump(); descendingTicks=0; readyWait=0; waitingTarget=null; startStrength=Float.NaN; lastHealth=self.getHealth(); if(mc.gui.screen()==null) setStatus(unavailableReason(mc,self)); return; }
if(self.getHealth()<lastHealth) incomingDamage();
else if(responseWindow>0) responseWindow--;
lastHealth=self.getHealth();
if(jumpDelay>0) jumpDelay--;
if(attackDelay>0) attackDelay--;
if(blockedJumpTicks>0) blockedJumpTicks--;
boolean falling=!self.onGround() && self.getDeltaMovement().y<0 && self.fallDistance>0;
descendingTicks=falling?Math.min(descendingTicks+1,20):0;
if(self.onGround()) {
hitThisFall=false;
if(observedAscent && !jumpRequested) { observedAscent=false; requestedJumpTick=-1; }
if(jumpTarget!=null && !jumpRequested && jumpAge>2) clearJump();
} else if(jumpRequested) {
jumpRequested=false; jumpPressTicks=0;
if(self.getDeltaMovement().y>0) { jumpCount++; observedAscent=true; }
else { requestedJumpTick=-1; observedAscent=false; }
}
if(falling && observedAscent && requestedJumpTick>=0) {
if(!recovery.pressure(trackingTick)) jumpTiming.observe((int)(trackingTick-requestedJumpTick)); observedAscent=false; requestedJumpTick=-1;
}
if(jumpTarget!=null && (++jumpAge>26 || !aimValid(self,jumpTarget) || jumpItem!=self.getMainHandItem().getItem())) clearJump();
if(!settings.triggerEnabled()) { clearJump(); readyWait=0; waitingTarget=null; setStatus(settings.aimEnabled()?(aimTarget!=null?"Aim only / target acquired":"Aim only / no target"):"Modules off"); return; }
if(mc.options.keyAttack.isDown() || CombatTrace.manualAction(startStrength,self.getAttackStrengthScale(.5f),startItem==self.getMainHandItem().getItem(),startWeaponDelay,self.getCurrentItemAttackStrengthDelay())) {
manualTick=trackingTick; clearJump(); readyWait=0; setStatus("Manual attack / item change"); return;
}
decision=CombatEngine.Decision.WAIT;
EntityHitResult hit=pickTarget(mc,self);
LivingEntity crosshair=hit!=null && hit.getEntity() instanceof LivingEntity living && aimValid(self,living)?living:null;
LivingEntity target=crosshair!=null?crosshair:settings.aimEnabled() && aimTarget!=null && valid(self,aimTarget) && Double.isFinite(angularError) && angularError<9?aimTarget:null;
if(target==null) { if(++consecutiveNoRay>=3) { readyWait=0; waitingTarget=null; } setStatus(localPractice(mc) && !golemTest && currentServerTarget(mc)==null?"Iron Golem Test off":aimTarget!=null?"Approach / align":"No valid target"); return; }
consecutiveNoRay=0;
boolean restricted=restricted(self);
boolean canJump=!restricted && !self.isShiftKeyDown() && mc.level.noCollision(self,self.getBoundingBox().move(0,.35,0)) && blockedJumpTicks==0;
boolean counter=responseWindow>0 || (!target.onGround() && target.getDeltaMovement().y<0);
float cooldown=self.getAttackStrengthScale(.5f);
boolean itemReady=!self.cannotAttackWithItem(self.getMainHandItem(),0);
if(waitingTarget!=target) { waitingTarget=target; readyWait=0; }
if(crosshair==target && cooldown>.90 && itemReady) readyWait=Math.min(20,readyWait+1);
else if(cooldown<=.90 || !itemReady) readyWait=0;
else readyWait=Math.max(0,readyWait-1);
var eye=self.getEyePosition();
var bounds=target.getBoundingBox();
var box=new AimModel.Box(new AimModel.Vec(bounds.minX,bounds.minY,bounds.minZ),new AimModel.Vec(bounds.maxX,bounds.maxY,bounds.maxZ));
var origin=new AimModel.Vec(eye.x,eye.y,eye.z);
var motion=target==sampledTarget?relativeMotion:new AimModel.Vec(0,0,0);
double reach=self.getAttackRangeWith(self.getMainHandItem()).effectiveMaxRange(self);
double now=AimModel.boxDistance(origin,box),future=AimModel.futureBoxDistance(origin,box,motion,4);
if(target==sampledTarget && tracker.uncertainty()>.35) future=Math.max(now,future);
boolean urgent=crosshair==target && (future>reach-.18 && future>now+.04 || now>reach-.20 || blockedJumpTicks>0);
var frame=new CombatEngine.Frame(cooldown,self.onGround(),falling?self.fallDistance:0,self.isSprinting(),restricted,crosshair==target || inRange(self,target),crosshair==target,!target.onGround() && target.getDeltaMovement().y<0,responseWindow>0,new TargetFilter.Candidate(true,false,true,true,false,false));
boolean recovering=updateRecovery(self,target);
boolean combo=updateStyle(self,target,now,future,reach) || recovering && self.onGround();
if((combo || recovering) && jumpRequested && self.onGround()) clearJump();
boolean canSprint=!restricted && !self.isShiftKeyDown() && (mc.options.keyUp.isDown() || autoMove) && !mc.options.keyDown.isDown();
var action=CombatEngine.hybridPlan(frame,descendingTicks,hitThisFall,canJump,jumpRequested,attackDelay==0 && itemReady,jumpDelay==0,urgent,self.getCurrentItemAttackStrengthDelay(),readyWait,jumpTiming.horizon(),combo,canSprint);
trace.style(combo);
trace.recovery(recovering);
trace.sample(cooldown,now,reach,angularError,crosshair==target);
setStatus(action.name());
switch(action) {
case JUMP -> { requestedJumpTick=trackingTick; observedAscent=false; jumpRequested=true; jumpPressTicks=3; jumpDelay=5; jumpTarget=target; jumpItem=self.getMainHandItem().getItem(); jumpAge=0; }
case STOP_SPRINT -> self.setSprinting(false);
case CRITICAL, COMBO, NORMAL -> {
if(crosshair==target && attack(mc,self,hit)) {
if(!self.onGround()) hitThisFall=true;
attackDelay=2;
clearJump();
readyTicks++;
pending.addLast(new Pending(target.getId(),trackingTick));
trace.attempt(trackingTick,action==CombatEngine.Action.CRITICAL,action==CombatEngine.Action.COMBO);
while(pending.size()>32) { pending.removeFirst(); expiredHits++; trace.expire(trackingTick); }
status=action==CombatEngine.Action.CRITICAL?(counter?"Counter attempt":"Critical attempt"):action==CombatEngine.Action.COMBO?"Sprint hit attempt":"Normal attack";
readyWait=0;
decision=action==CombatEngine.Action.CRITICAL?CombatEngine.Decision.CRITICAL_WINDOW:action==CombatEngine.Action.COMBO?CombatEngine.Decision.COMBO_WINDOW:CombatEngine.Decision.NORMAL_WINDOW;
// Incoming pressure is not cleared by our own attack.
} else { trace.event(trackingTick,crosshair!=target?"ATTACK_TARGET_MISMATCH":"ATTACK_NOT_CONSUMED"); setStatus("Attack not consumed"); attackDelay=1; }
}
default -> {}
}
}
private static void updateAim(Minecraft mc,LocalPlayer self,CombatSettings settings) {
if(PracticeClient.hotkeyActive() && currentServerTarget(mc)!=null) settings=new CombatSettings(settings.aimEnabled(),settings.triggerEnabled(),settings.horizontalSpeed(),settings.verticalSpeed(),360);
if(!settings.aimEnabled()) { aimTarget=null; resetTracking(); }
if(settings.aimEnabled()) {
var eye=self.getEyePosition();
var current=new AimModel.Angles(self.getYRot(),self.getXRot());
AimModel.Angles best=null;
double bestError=Double.POSITIVE_INFINITY,bestScore=Double.POSITIVE_INFINITY,retainedScore=Double.POSITIVE_INFINITY,retainedError=0;
AimModel.Angles retained=null;
LivingEntity selected=null;
for(var target:mc.level.getEntitiesOfClass(LivingEntity.class,self.getBoundingBox().inflate(autoMove && currentServerTarget(mc)!=null?FOLLOW_RANGE:AIM_RANGE),target -> aimValid(self,target))) {
var bb=target.getBoundingBox();
var box=new AimModel.Box(new AimModel.Vec(bb.minX,bb.minY,bb.minZ),new AimModel.Vec(bb.maxX,bb.maxY,bb.maxZ));
var desired=AimModel.bodyDirection(new AimModel.Vec(eye.x,eye.y,eye.z),current,box);
double error=AimModel.error(current,desired);
if(error>settings.fov()/2) continue;
double score=AimModel.targetScore(error,settings.fov(),self.distanceToSqr(target));
if(inRange(self,target)) score*=.82;
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
targetMotion=tracker.update(center);
if(previousEye!=null) relativeMotion=new AimModel.Vec(targetMotion.x()-(eye.x-previousEye.x()),targetMotion.y()-(eye.y-previousEye.y()),targetMotion.z()-(eye.z-previousEye.z()));
var zero=new AimModel.Vec(0,0,0);
motionAngle=AimModel.error(AimModel.direction(zero,previousRelative),AimModel.direction(zero,relative));
} else { resetTracking(); tracker.update(center); }
sampledTarget=selected;
previousRelative=relative;
previousEye=origin;
boolean locked=currentServerTarget(mc)!=null;
best=locked?AimModel.lockDirection(origin,current,box):AimModel.bodyDirection(origin,current,box);
bestError=AimModel.error(current,best);
angularError=bestError;
boolean onTarget=AimModel.intersects(origin,current,box);
if(bestError>.12 && !onTarget) {
turn=locked?AimModel.lockTurn(origin,current,box,turn,settings,motionAngle):AimModel.smoothTurn(current,best,turn,settings,motionAngle);
trace.aimCorrection(turn.yaw(),turn.pitch());
self.setYRot(self.getYRot()+(float)turn.yaw());
self.setXRot((float)Math.max(-90,Math.min(90,self.getXRot()+turn.pitch())));
var applied=new AimModel.Angles(self.getYRot(),self.getXRot());
angularError=AimModel.intersects(origin,applied,box)?0:AimModel.error(applied,best);
} else { turn=new AimModel.Turn(0,0); trace.aimCorrection(0,0); }
} else resetTracking();
}
}
private static boolean updateRecovery(LocalPlayer self,LivingEntity target) {
var v=self.getDeltaMovement();
double away=target==null?0:CombatEngine.awaySpeed(target.getX()-self.getX(),target.getZ()-self.getZ(),v.x,v.z);
return recovery.update(trackingTick,self.onGround(),away);
}
private static boolean updateStyle(LocalPlayer self,LivingEntity target,double now,double future,double reach) {
if(styleTarget!=target) { comboMode=false; styleTarget=target; }
comboMode=CombatEngine.preferCombo(comboMode,now,future,reach,responseWindow>0 || self.getHealth()<lastHealth);
return comboMode;
}
private static boolean updateStyle(LocalPlayer self,LivingEntity target) {
if(target==null || !aimValid(self,target)) { resetStyle(); return false; }
var eye=self.getEyePosition(); var b=target.getBoundingBox();
var origin=new AimModel.Vec(eye.x,eye.y,eye.z);
var box=new AimModel.Box(new AimModel.Vec(b.minX,b.minY,b.minZ),new AimModel.Vec(b.maxX,b.maxY,b.maxZ));
double now=AimModel.boxDistance(origin,box);
double future=AimModel.futureBoxDistance(origin,box,target==sampledTarget?relativeMotion:new AimModel.Vec(0,0,0),4);
return updateStyle(self,target,now,future,self.getAttackRangeWith(self.getMainHandItem()).effectiveMaxRange(self));
}
private static void resetTracking() { tracker.reset(); lastMovement=new AimModel.Movement(false,false,false,false); sampledTarget=null; previousRelative=null; previousEye=null; targetMotion=new AimModel.Vec(0,0,0); relativeMotion=new AimModel.Vec(0,0,0); motionAngle=0; turn=new AimModel.Turn(0,0); angularError=Double.NaN; }
static void startTick(Minecraft mc,CombatSettings settings) {
if(!enabled(mc)) { if(sessionActive) resetWorld(mc); return; }
var self=mc.player;
if(lastPlayer!=self || lastLevel!=mc.level) resetWorld(mc);
sessionActive=true;
trackingTick++;
startStrength=self.getAttackStrengthScale(.5f);
startItem=self.getMainHandItem().getItem();
startWeaponDelay=self.getCurrentItemAttackStrengthDelay();
if(lastWeapon!=null && (lastWeapon!=startItem || Math.abs(lastWeaponDelay-startWeaponDelay)>.001)) {
clearJump(); readyWait=0; waitingTarget=null; jumpDelay=0;
trace.event(trackingTick,"WEAPON_CHANGED");
}
lastWeapon=startItem; lastWeaponDelay=startWeaponDelay;
if(!available(mc,self)) { clearJump(); aimTarget=null; resetTracking(); return; }
updateAim(mc,self,settings);
if(!settings.triggerEnabled() || mc.options.keyAttack.isDown()) { clearJump(); resetStyle(); return; }
LivingEntity combatTarget=aimTarget;
if(combatTarget==null) { var hit=pickTarget(mc,self); if(hit!=null && hit.getEntity() instanceof LivingEntity living && aimValid(self,living)) combatTarget=living; }
if(self.getHealth()<lastHealth) incomingDamage();
boolean recovering=updateRecovery(self,combatTarget);
boolean combo=updateStyle(self,combatTarget) || recovering && self.onGround();
if((combo || recovering) && self.onGround() && jumpRequested) clearJump();
if(jumpTarget!=null && jumpItem!=self.getMainHandItem().getItem()) { clearJump(); trace.event(trackingTick,"JUMP_CANCEL_ITEM_CHANGE"); }
boolean critActive=jumpTarget!=null && aimValid(self,jumpTarget) && !restricted(self);
if(critActive || (!combo && combatTarget!=null && valid(self,combatTarget) && !restricted(self)) || (!self.onGround() && !hitThisFall && self.getDeltaMovement().y<0 && combatTarget!=null && valid(self,combatTarget) && !restricted(self))) {
if(mc.options.keySprint.isDown()) { mc.options.keySprint.setDown(false); forcedSprint=true; }
self.setSprinting(false);
}
if(jumpRequested && critActive && self.onGround() && (valid(self,jumpTarget) || rayOn(mc,self,jumpTarget)) && !self.isShiftKeyDown()) {
if(jumpPressTicks>0) {
if(!mc.options.keyJump.isDown()) { mc.options.keyJump.setDown(true); forcedJump=true; }
jumpPressTicks--;
} else { blockedJumpTicks=12; clearJump(); setStatus("Jump blocked"); }
} else if(jumpRequested && self.onGround() && (!critActive || (!valid(self,jumpTarget) && !rayOn(mc,self,jumpTarget)))) clearJump();
if(autoMove && (!mc.options.keyJump.isDown() || forcedJump) && (!recovering || self.onGround()) && settings.aimEnabled() && aimTarget!=null && aimValid(self,aimTarget) && !self.isShiftKeyDown() && !mc.options.keyDown.isDown() && !mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown() && !mc.options.keyUp.isDown()) {
double dx=aimTarget.getX()-self.getX(),dz=aimTarget.getZ()-self.getZ();
if(Math.abs(aimTarget.getY()-self.getY())<2.0 && !self.isInWater() && !self.isPassenger()) {
var velocity=self.getDeltaMovement();
var other=aimTarget.getDeltaMovement();
double targetX=aimTarget==sampledTarget?targetMotion.x():other.x;
double targetZ=aimTarget==sampledTarget?targetMotion.z():other.z;
double reach=self.getAttackRangeWith(self.getMainHandItem()).effectiveMaxRange(self);
var choice=CombatAI.chooseFollow(dx,dz,aimTarget.getYRot(),self.getYRot(),velocity.x,velocity.z,targetX,targetZ,reach,lastMovement,(mx,mz)->{
var next=self.getBoundingBox().move(mx,0,mz);
return mc.level.noCollision(self,next) && !mc.level.noCollision(self,next.move(0,self.onGround()?-.7:-1.7,0));
});
var move=choice.movement();
lastMovement=move;
if(Double.isFinite(choice.cost())) {
trace.movement(Math.hypot(dx,dz)>reach?"FOLLOW":move.left() || move.right()?"FLANK":move.forward()?"APPROACH":move.back()?"RETREAT":"HOLD");
if(move.forward()) { mc.options.keyUp.setDown(true); forcedForward=true; }
if(move.back()) { mc.options.keyDown.setDown(true); forcedBack=true; }
if(move.left()) { mc.options.keyLeft.setDown(true); forcedLeft=true; }
if(move.right()) { mc.options.keyRight.setDown(true); forcedRight=true; }
} else trace.movement("BLOCKED");
}
}
if(combo && combatTarget!=null && self.onGround() && !restricted(self) && !self.isShiftKeyDown() && mc.options.keyUp.isDown() && !mc.options.keyDown.isDown() && !mc.options.keySprint.isDown()) {
mc.options.keySprint.setDown(true); forcedSprint=true;
}
}
static void releaseInput(Minecraft mc) {
if(forcedJump || forcedForward || forcedBack || forcedLeft || forcedRight || forcedSprint) {
if(mc.gui.screen()==null) KeyMapping.setAll();
else {
if(forcedJump) mc.options.keyJump.setDown(false);
if(forcedForward) mc.options.keyUp.setDown(false);
if(forcedBack) mc.options.keyDown.setDown(false);
if(forcedLeft) mc.options.keyLeft.setDown(false);
if(forcedRight) mc.options.keyRight.setDown(false);
if(forcedSprint) mc.options.keySprint.setDown(false);
}
}
forcedJump=forcedForward=forcedSprint=forcedBack=forcedLeft=forcedRight=false;
}
private static boolean rayOn(Minecraft mc,LocalPlayer self,LivingEntity target) { var hit=pickTarget(mc,self); return hit!=null && hit.getEntity()==target; }
private static EntityHitResult pickTarget(Minecraft mc,LocalPlayer self) {
var result=self.raycastHitResult(1.0f,self);
return result instanceof EntityHitResult hit?hit:null;
}
public static void recordDamage(ClientboundDamageEventPacket packet) {
var mc=Minecraft.getInstance();
if(mc.player==null || mc.level==null || lastPlayer!=mc.player || lastLevel!=mc.level || !sessionActive) return;
if(packet.entityId()==mc.player.getId()) { incomingDamage(); return; }
if(packet.sourceCauseId()!=mc.player.getId() || packet.sourceDirectId()!=mc.player.getId()) return;
if(manualTick>=0 && trackingTick-manualTick<=2) { trace.event(trackingTick,"MANUAL_DAMAGE_UNATTRIBUTED"); return; }
for(var it=pending.iterator();it.hasNext();) {
var attempt=it.next();
if(attempt.entityId()==packet.entityId() && trackingTick-attempt.tick()<=40) { it.remove(); confirmedHits++; trace.confirm(trackingTick,attempt.tick()); break; }
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
private static void clearJump() { jumpTarget=null; jumpItem=null; jumpAge=0; jumpRequested=false; jumpPressTicks=0; observedAscent=false; requestedJumpTick=-1; }
private PracticeCombat() {}
}
