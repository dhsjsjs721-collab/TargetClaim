package pvp;
import java.util.function.BiPredicate;
/** Deterministic adaptive state estimation and finite-horizon movement search. */
public final class CombatAI {
private static double clamp(double v,double lo,double hi) { return Math.max(lo,Math.min(hi,v)); }
private static AimModel.Vec add(AimModel.Vec a,AimModel.Vec b) { return new AimModel.Vec(a.x()+b.x(),a.y()+b.y(),a.z()+b.z()); }
private static AimModel.Vec sub(AimModel.Vec a,AimModel.Vec b) { return new AimModel.Vec(a.x()-b.x(),a.y()-b.y(),a.z()-b.z()); }
private static AimModel.Vec scale(AimModel.Vec a,double s) { return new AimModel.Vec(a.x()*s,a.y()*s,a.z()*s); }
private static double norm(AimModel.Vec v) { return Math.sqrt(v.x()*v.x()+v.y()*v.y()+v.z()*v.z()); }
private static AimModel.Vec limit(AimModel.Vec v,double max) { double n=norm(v); return n>max?scale(v,max/n):v; }
public static final class Tracker {
private AimModel.Vec position,velocity=new AimModel.Vec(0,0,0);
private double noise=.05;
public void reset() { position=null; velocity=new AimModel.Vec(0,0,0); noise=.05; }
public AimModel.Vec update(AimModel.Vec observed) {
if(!Double.isFinite(norm(observed))) { reset(); return velocity; }
if(position==null) { position=observed; return velocity; }
var residual=sub(observed,add(position,velocity));
double error=norm(residual);
if(error>2.0) { reset(); position=observed; return velocity; }
noise=.8*noise+.2*error;
double gain=clamp(.45+noise*.9,.45,.85);
position=add(add(position,velocity),scale(residual,gain));
velocity=limit(add(scale(velocity,.96),scale(residual,.12+gain*.24)),.85);
return velocity;
}
public AimModel.Vec velocity() { return velocity; }
public AimModel.Vec forecast(int ticks) { return position==null?new AimModel.Vec(0,0,0):add(position,scale(velocity,clamp(ticks,0,6))); }
public double uncertainty() { return noise; }
}
public record MovementChoice(AimModel.Movement movement,double cost) {}
public static MovementChoice chooseMovement(double dx,double dz,double targetYaw,double viewYaw,double ownX,double ownZ,double targetX,double targetZ,double ideal,AimModel.Movement last,BiPredicate<Double,Double> safe) {
double yaw=Math.toRadians(viewYaw),opponentYaw=Math.toRadians(targetYaw);
double fx=-Math.sin(yaw),fz=Math.cos(yaw),rx=-Math.cos(yaw),rz=-Math.sin(yaw);
double rearX=Math.sin(opponentYaw),rearZ=-Math.cos(opponentYaw);
double initial=Math.hypot(dx,dz);
double best=Double.POSITIVE_INFINITY;
var chosen=new AimModel.Movement(false,false,false,false);
ideal=clamp(ideal,1.6,2.8);
for(int forward=-1;forward<=1;forward++) for(int right=-1;right<=1;right++) {
double mag=Math.max(1,Math.hypot(forward,right));
double inputX=(fx*forward+rx*right)/mag*.22,inputZ=(fz*forward+rz*right)/mag*.22;
double px=0,pz=0,vx=clamp(ownX,-.5,.5),vz=clamp(ownZ,-.5,.5);
double score=0;
boolean ok=true;
for(int tick=1;tick<=4;tick++) {
vx=.45*vx+.55*inputX; vz=.45*vz+.55*inputZ;
px+=vx; pz+=vz;
if(!safe.test(px,pz)) { ok=false; break; }
double tx=dx+clamp(targetX,-.35,.35)*tick-px,tz=dz+clamp(targetZ,-.35,.35)*tick-pz;
double dist=Math.hypot(tx,tz),delta=dist-ideal;
double rear=dist>.01?(-tx*rearX-tz*rearZ)/dist:0;
score+=delta*delta*(delta>0?1.5:2.0)+Math.max(0,dist-3)*2.2;
if(initial<3.4 && dist<3.1) score+=.48*(1-rear);
}
if(!ok) continue;
var move=new AimModel.Movement(forward>0,forward<0,right<0,right>0);
if(last!=null && !move.equals(last)) score+=.14;
if(forward==0 && right==0 && initial>ideal+.35) score+=.25;
if(score<best) { best=score; chosen=move; }
}
return new MovementChoice(chosen,best);
}
private CombatAI() {}
}
