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
private AimModel.Vec position,previousObservation,velocity=new AimModel.Vec(0,0,0);
private double noise=.05;
public void reset() { position=null; previousObservation=null; velocity=new AimModel.Vec(0,0,0); noise=.05; }
public AimModel.Vec update(AimModel.Vec observed) {
if(!Double.isFinite(norm(observed))) { reset(); return velocity; }
if(position==null) { position=observed; previousObservation=observed; return velocity; }
var residual=sub(observed,add(position,velocity));
double error=norm(residual);
if(error>2.0) { reset(); position=observed; previousObservation=observed; return velocity; }
var measured=limit(sub(observed,previousObservation),.85);
previousObservation=observed;
double speed=norm(velocity),sampleSpeed=norm(measured);
double dot=velocity.x()*measured.x()+velocity.y()*measured.y()+velocity.z()*measured.z();
if(speed>.09 && sampleSpeed>.09 && dot<-.25*speed*sampleSpeed) {
position=observed; velocity=scale(measured,.70); noise=Math.min(.35,noise+.08); return velocity;
}
if(sampleSpeed<.025 && speed>.08) {
velocity=scale(velocity,.55);
residual=sub(observed,add(position,velocity));
error=norm(residual);
}
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
/** A short receding-horizon search over two sequential input choices. */
public static MovementChoice choosePath(double dx,double dz,double targetYaw,double viewYaw,double ownX,double ownZ,double targetX,double targetZ,double ideal,AimModel.Movement last,BiPredicate<Double,Double> safe) {
return choosePath(dx,dz,targetYaw,viewYaw,ownX,ownZ,targetX,targetZ,ideal,3,0,last,safe);
}
public static MovementChoice choosePath(double dx,double dz,double targetYaw,double viewYaw,double ownX,double ownZ,double targetX,double targetZ,double ideal,double reach,double uncertainty,AimModel.Movement last,BiPredicate<Double,Double> safe) {
double y=Math.toRadians(viewYaw),o=Math.toRadians(targetYaw);
double fx=-Math.sin(y),fz=Math.cos(y),rx=-Math.cos(y),rz=-Math.sin(y);
double rearX=Math.sin(o),rearZ=-Math.cos(o);
double initial=Math.hypot(dx,dz);
ideal=clamp(ideal,1.6,2.8);
reach=clamp(reach,1.5,6);
uncertainty=clamp(uncertainty,0,.6);
var moves=new AimModel.Movement[9];
var xs=new double[9]; var zs=new double[9];
int index=0;
for(int f=-1;f<=1;f++) for(int right=-1;right<=1;right++) {
double mag=Math.max(1,Math.hypot(f,right));
moves[index]=new AimModel.Movement(f>0,f<0,right<0,right>0);
xs[index]=(fx*f+rx*right)/mag*.22;
zs[index]=(fz*f+rz*right)/mag*.22;
index++;
}
double best=Double.POSITIVE_INFINITY,shortBest=Double.POSITIVE_INFINITY;
var chosen=moves[4]; var shortChoice=moves[4];
for(int first=0;first<9;first++) {
double px=0,pz=0,vx=clamp(ownX,-.5,.5),vz=clamp(ownZ,-.5,.5),cost=0;
boolean ok=true;
for(int tick=1;tick<=2;tick++) {
vx=.45*vx+.55*xs[first]; vz=.45*vz+.55*zs[first]; px+=vx; pz+=vz;
if(!safe.test(px,pz)) { ok=false; break; }
cost+=pathCost(dx,dz,targetX,targetZ,tick,px,pz,ideal,initial,rearX,rearZ,reach,uncertainty);
if(tick==1 && cost<shortBest) { shortBest=cost; shortChoice=moves[first]; }
}
if(!ok) continue;
if(last!=null && !moves[first].equals(last)) cost+=.22;
for(int second=0;second<9;second++) {
double x=px,z=pz,u=vx,v=vz,s=cost+(second==first?0:.16);
boolean valid=true;
for(int tick=3;tick<=4;tick++) {
u=.45*u+.55*xs[second]; v=.45*v+.55*zs[second]; x+=u; z+=v;
if(!safe.test(x,z)) { valid=false; break; }
s+=pathCost(dx,dz,targetX,targetZ,tick,x,z,ideal,initial,rearX,rearZ,reach,uncertainty)*(tick==4?1.2:1);
if(s>=best) { valid=false; break; }
}
if(valid && s<best) { best=s; chosen=moves[first]; }
}
}
if(!Double.isFinite(best) && Double.isFinite(shortBest)) return new MovementChoice(shortChoice,shortBest);
if(!Double.isFinite(best)) return new MovementChoice(moves[4],Double.POSITIVE_INFINITY);
return new MovementChoice(chosen,best);
}
/** Short-horizon pursuit with an explicit rear-position objective. */
public static MovementChoice chooseFollow(double dx,double dz,double targetYaw,double viewYaw,double ownX,double ownZ,double targetX,double targetZ,double reach,AimModel.Movement last,BiPredicate<Double,Double> safe) {
return chooseFollow(dx,dz,targetYaw,viewYaw,ownX,ownZ,targetX,targetZ,reach,last,0,safe);
}
public static MovementChoice chooseFollow(double dx,double dz,double targetYaw,double viewYaw,double ownX,double ownZ,double targetX,double targetZ,double reach,AimModel.Movement last,int orbitSide,BiPredicate<Double,Double> safe) {
double d0=Math.hypot(dx,dz);
var hold=new AimModel.Movement(false,false,false,false);
if(!Double.isFinite(d0) || !Double.isFinite(reach) || reach<=0) return new MovementChoice(hold,Double.POSITIVE_INFINITY);
double ideal=clamp(reach*.64,1.55,2.55);
double y=Math.toRadians(viewYaw),o=Math.toRadians(targetYaw);
double fx=-Math.sin(y),fz=Math.cos(y),rx=-Math.cos(y),rz=-Math.sin(y);
double rearX=Math.sin(o),rearZ=-Math.cos(o);
double best=Double.POSITIVE_INFINITY;
var chosen=hold;
for(int f=-1;f<=1;f++) for(int right=-1;right<=1;right++) {
double mag=Math.max(1,Math.hypot(f,right));
double ix=(fx*f+rx*right)/mag*.22,iz=(fz*f+rz*right)/mag*.22;
double x=0,z=0,vx=clamp(ownX,-.5,.5),vz=clamp(ownZ,-.5,.5),cost=0;
boolean ok=true;
for(int t=1;t<=5;t++) {
vx=.45*vx+.55*ix; vz=.45*vz+.55*iz; x+=vx; z+=vz;
if(!safe.test(x,z)) { ok=false; break; }
double a=dx+clamp(targetX,-.4,.4)*t-x,b=dz+clamp(targetZ,-.4,.4)*t-z;
double d=Math.hypot(a,b),err=d-ideal;
double angle=d>.01?Math.acos(clamp((-a*rearX-b*rearZ)/d,-1,1)):Math.PI;
double rearWeight=2.0*clamp((reach+1.3-d)/1.4,0,1);
cost+=err*err*(err>0?1.7:2.6)+Math.max(0,d-reach)*2.8+rearWeight*angle*angle;
cost+=8*Math.pow(Math.max(0,1.05-d),2);
if(orbitSide!=0 && angle>Math.PI*.65 && d>.01) {
double cross=(-a*rearZ+b*rearX)/d;
cost+=.9*Math.max(0,-orbitSide*cross);
}
}
if(!ok) continue;
var move=new AimModel.Movement(f>0,f<0,right<0,right>0);
if(last!=null && !move.equals(last)) cost+=.18;
if(cost<best) { best=cost; chosen=move; }
}
return new MovementChoice(chosen,best);
}
/** Local dummy practice: prioritize stable spacing rather than circling a stationary target.
 * Conservative four-tick model; this is not a full Minecraft physics simulation. */
public static MovementChoice choosePracticeWalk(double dx,double dz,double viewYaw,double ownX,double ownZ,double targetX,double targetZ,double reach,AimModel.Movement last,BiPredicate<Double,Double> safe) {
var hold=new AimModel.Movement(false,false,false,false);
if(!Double.isFinite(dx) || !Double.isFinite(dz) || !Double.isFinite(viewYaw) || !Double.isFinite(ownX) || !Double.isFinite(ownZ) || !Double.isFinite(targetX) || !Double.isFinite(targetZ) || !Double.isFinite(reach) || reach<=0) return new MovementChoice(hold,Double.POSITIVE_INFINITY);
double ideal=clamp(reach*.73,1.45,2.5),low=ideal-.18,high=ideal+.18;
double yaw=Math.toRadians(viewYaw),fx=-Math.sin(yaw),fz=Math.cos(yaw),rx=-Math.cos(yaw),rz=-Math.sin(yaw);
double best=Double.POSITIVE_INFINITY; var chosen=hold;
for(int f=-1;f<=1;f++) for(int rr=-1;rr<=1;rr++) {
double mag=Math.max(1,Math.hypot(f,rr)),ix=(fx*f+rx*rr)/mag*.22,iz=(fz*f+rz*rr)/mag*.22;
double x=0,z=0,vx=clamp(ownX,-.5,.5),vz=clamp(ownZ,-.5,.5),score=0;
boolean ok=true;
for(int k=1;k<=4;k++) {
vx=.45*vx+.55*ix; vz=.45*vz+.55*iz;
if(!safe.test(x+vx*.5,z+vz*.5) || !safe.test(x+vx,z+vz)) { ok=false; break; }
x+=vx; z+=vz;
double d=Math.hypot(dx+clamp(targetX,-.35,.35)*k-x,dz+clamp(targetZ,-.35,.35)*k-z);
double excess=Math.max(0,d-high),deficit=Math.max(0,low-d);
score+=3*excess*excess+4*deficit*deficit+6*Math.pow(Math.max(0,d-reach),2)+12*Math.pow(Math.max(0,1.05-d),2);
}
if(!ok) continue;
var movement=new AimModel.Movement(f>0,f<0,rr<0,rr>0);
score+=.10*(Math.abs(f)+Math.abs(rr));
if(last!=null && !movement.equals(last)) score+=.12;
if(score<best) { best=score; chosen=movement; }
}
return new MovementChoice(chosen,best);
}
/** Select safe steps away from an opponent, while preserving the current facing. */
public static MovementChoice chooseRetreat(double dx,double dz,double viewYaw,double ownX,double ownZ,AimModel.Movement last,BiPredicate<Double,Double> safe) {
var hold=new AimModel.Movement(false,false,false,false);
double dist=Math.hypot(dx,dz);
if(!Double.isFinite(dist) || dist<.01) return new MovementChoice(hold,Double.POSITIVE_INFINITY);
double y=Math.toRadians(viewYaw),fx=-Math.sin(y),fz=Math.cos(y),rx=-Math.cos(y),rz=-Math.sin(y);
double best=Double.POSITIVE_INFINITY;
var chosen=hold;
for(int f=-1;f<=1;f++) for(int right=-1;right<=1;right++) {
double mag=Math.max(1,Math.hypot(f,right)),ix=(fx*f+rx*right)/mag*.22,iz=(fz*f+rz*right)/mag*.22;
double x=0,z=0,vx=clamp(ownX,-.5,.5),vz=clamp(ownZ,-.5,.5),cost=0;
boolean ok=true;
for(int t=1;t<=3;t++) {
vx=.45*vx+.55*ix; vz=.45*vz+.55*iz; x+=vx; z+=vz;
if(!safe.test(x,z)) { ok=false; break; }
cost+= (dx*x+dz*z)/dist;
}
if(!ok || cost>0) continue;
var move=new AimModel.Movement(f>0,f<0,right<0,right>0);
if(last!=null && !move.equals(last)) cost+=.06;
if(cost<best) { best=cost; chosen=move; }
}
return new MovementChoice(chosen,best);
}
public static int orbitSide(double dx,double dz,double targetYaw,int previous) {
double y=Math.toRadians(targetYaw),rx=Math.sin(y),rz=-Math.cos(y);
double length=Math.hypot(dx,dz);
if(length<.01) return previous==0?1:previous;
double cross=(-dx*rz+dz*rx)/length;
if(Math.abs(cross)<.23) return previous==0?1:previous;
return cross>0?1:-1;
}
/** Bounded flat-ground detour search, in view-relative coordinates.
 * Each edge and its midpoint must be safe. The first step is rechecked
 * against current velocity. It never plans block breaking or vertical jumps. */
public static MovementChoice chooseDetour(double dx,double dz,double viewYaw,double ownX,double ownZ,double ideal,BiPredicate<Double,Double> safe) {
var hold=new AimModel.Movement(false,false,false,false);
if(!Double.isFinite(dx) || !Double.isFinite(dz) || !Double.isFinite(viewYaw) || !Double.isFinite(ideal) || !Double.isFinite(ownX) || !Double.isFinite(ownZ)) return new MovementChoice(hold,Double.POSITIVE_INFINITY);
final int n=15,center=n/2,origin=center*n+center;
final double step=.35;
double yaw=Math.toRadians(viewYaw),fx=-Math.sin(yaw),fz=Math.cos(yaw),rx=-Math.cos(yaw),rz=-Math.sin(yaw);
double[] cost=new double[n*n]; java.util.Arrays.fill(cost,Double.POSITIVE_INFINITY);
int[] firstF=new int[n*n],firstR=new int[n*n];
record Node(int index,double cost) {}
var queue=new java.util.PriorityQueue<Node>(java.util.Comparator.comparingDouble(Node::cost).thenComparingInt(Node::index));
cost[origin]=0; queue.add(new Node(origin,0));
double initial=Math.max(0,Math.hypot(dx,dz)-ideal),best=initial*initial;
int bestNode=-1,expanded=0;
while(!queue.isEmpty() && expanded<180) {
var node=queue.remove(); int idx=node.index();
if(node.cost()!=cost[idx]) continue;
expanded++;
int f=idx/n-center,rr=idx%n-center;
double x=(fx*f+rx*rr)*step,z=(fz*f+rz*rr)*step;
double distance=Math.hypot(dx-x,dz-z),error=Math.max(0,distance-ideal);
double score=error*error+.20*node.cost()+3*Math.pow(Math.max(0,1.05-distance),2);
if(idx!=origin && score<best-.000001) { best=score; bestNode=idx; }
for(int df=-1;df<=1;df++) for(int dr=-1;dr<=1;dr++) {
if(df==0 && dr==0) continue;
int nf=f+df,nr=rr+dr;
if(Math.abs(nf)>center || Math.abs(nr)>center) continue;
int next=(nf+center)*n+nr+center;
double nx=(fx*nf+rx*nr)*step,nz=(fz*nf+rz*nr)*step;
double nc=node.cost()+Math.hypot(df,dr)*step;
if(nc>=cost[next] || !safe.test(nx,nz) || !safe.test((x+nx)*.5,(z+nz)*.5)) continue;
if(df!=0 && dr!=0 && (!safe.test(x+fx*df*step,z+fz*df*step) || !safe.test(x+rx*dr*step,z+rz*dr*step))) continue;
if(idx==origin) {
double mag=Math.hypot(df,dr);
double vx=.45*clamp(ownX,-.5,.5)+.55*(fx*df+rx*dr)/mag*.22;
double vz=.45*clamp(ownZ,-.5,.5)+.55*(fz*df+rz*dr)/mag*.22;
if(!safe.test(vx,vz) || !safe.test(vx*.5,vz*.5)) continue;
}
cost[next]=nc; firstF[next]=idx==origin?df:firstF[idx]; firstR[next]=idx==origin?dr:firstR[idx];
queue.add(new Node(next,nc));
}
}
if(bestNode<0) return new MovementChoice(hold,Double.POSITIVE_INFINITY);
int f=firstF[bestNode],rr=firstR[bestNode];
return new MovementChoice(new AimModel.Movement(f>0,f<0,rr<0,rr>0),best);
}
public static final class Progress {
private double x,z;
private boolean initialized;
private int stalled;
public void reset() { initialized=false; stalled=0; }
public int observe(double px,double pz,boolean previouslyRequested) {
if(!Double.isFinite(px) || !Double.isFinite(pz)) { reset(); return 0; }
if(initialized && previouslyRequested && Math.hypot(px-x,pz-z)<.025) stalled=Math.min(100,stalled+1); else stalled=0;
x=px; z=pz; initialized=true; return stalled;
}
}
private static double pathCost(double dx,double dz,double tx,double tz,int tick,double x,double z,double ideal,double initial,double rearX,double rearZ,double reach,double uncertainty) {
double a=dx+clamp(tx,-.35,.35)*tick-x,b=dz+clamp(tz,-.35,.35)*tick-z;
double dist=Math.hypot(a,b),delta=dist-ideal;
double spread=Math.min(.75,uncertainty*tick*.5);
double cost=delta*delta*(delta>0?1.6:2)+Math.max(0,dist+spread-reach)*2.2;
cost+=3*Math.pow(Math.max(0,1.05-dist+spread*.3),2);
if(initial<3.4 && dist<3.1 && dist>.01) cost+=.38*(1-(-a*rearX-b*rearZ)/dist);
return cost;
}
/** Learns the observed delay from a requested jump to its first descending tick. */
public static final class JumpTiming {
private double estimate=6;
private int samples;
public void reset() { estimate=6; samples=0; }
public void observe(int ticks) {
if(ticks<2 || ticks>20) return;
estimate=samples==0?ticks:.75*estimate+.25*ticks;
samples++;
}
public int horizon() { return (int)Math.round(clamp(estimate,3,14)); }
public int samples() { return samples; }
}
private CombatAI() {}
}
