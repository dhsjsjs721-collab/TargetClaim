package pvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
final class PracticeCombat {
static boolean golemTest;
private static LocalPlayer lastPlayer;
private static float lastHealth;
private static int responseWindow;
private static int readyTicks;
private static double angularError=Double.NaN;
private static CombatEngine.Decision decision=CombatEngine.Decision.WAIT;
static double angularError() { return angularError; }
static int readyTicks() { return readyTicks; }
static CombatEngine.Decision decision() { return decision; }
private static boolean valid(LocalPlayer self,LivingEntity target) {
return target!=self && (target instanceof Player || (golemTest && target.getType()==EntityType.IRON_GOLEM)) && target.isAlive() && !target.isSpectator() && !target.isInvisible() && self.hasLineOfSight(target) && self.distanceToSqr(target)<=9;
}
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
if(settings.aimEnabled()) {
var eye=self.getEyePosition();
var current=new AimModel.Angles(self.getYRot(),self.getXRot());
AimModel.Angles best=null;
double bestError=settings.fov()/2;
for(var target:mc.level.getEntitiesOfClass(LivingEntity.class,self.getBoundingBox().inflate(3),target -> valid(self,target))) {
var bb=target.getBoundingBox();
var box=new AimModel.Box(new AimModel.Vec(bb.minX,bb.minY,bb.minZ),new AimModel.Vec(bb.maxX,bb.maxY,bb.maxZ));
var desired=AimModel.direction(new AimModel.Vec(eye.x,eye.y,eye.z),AimModel.point(box,.67));
double error=Math.hypot(AimModel.wrap(desired.yaw()-current.yaw()),desired.pitch()-current.pitch());
if(error<bestError) { bestError=error; best=desired; }
}
if(best!=null) {
angularError=bestError;
var next=AimModel.step(current,best,settings);
self.setYRot((float)next.yaw());
self.setXRot((float)next.pitch());
}
}
if(!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target) || !valid(self,target)) return;
if(settings.triggerEnabled()) {
var candidate=new TargetFilter.Candidate(true,false,true,true,false,false);
var frame=new CombatEngine.Frame(self.getAttackStrengthScale(0),self.onGround(),self.fallDistance,self.isSprinting(),self.isUsingItem() || self.isInWater() || self.isPassenger() || self.onClimbable(),self.distanceToSqr(target)<=9,true,false,responseWindow>0,candidate);
decision=CombatEngine.evaluate(mode,frame);
if(decision!=CombatEngine.Decision.WAIT) {
mc.gameMode.attack(self,target);
self.swingAndResetAttackStrength(InteractionHand.MAIN_HAND,self.getMainHandItem().getAttackAnimation(),false);
readyTicks++;
responseWindow=0;
}
}
}
private PracticeCombat() {}
}
