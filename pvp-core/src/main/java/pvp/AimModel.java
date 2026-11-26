package pvp;
/** Geometry for offline target practice simulation. */
public final class AimModel {
public record Vec(double x,double y,double z) {}
public record Angles(double yaw,double pitch) {}
public record Box(Vec min,Vec max) {
public Box { if(min.x()>max.x() || min.y()>max.y() || min.z()>max.z()) throw new IllegalArgumentException("Invalid box"); }
}
private static double clamp(double v,double lo,double hi) { return Math.max(lo,Math.min(hi,v)); }
public static double wrap(double a) { return ((a+180)%360+360)%360-180; }
public static Vec point(Box b,double height) {
return new Vec((b.min().x()+b.max().x())/2,b.min().y()+(b.max().y()-b.min().y())*clamp(height,0,1),(b.min().z()+b.max().z())/2);
}
public static Vec nearestAimPoint(Vec eye,Angles view,Box b) {
Vec center=point(b,.65);
double yaw=Math.toRadians(view.yaw()),pitch=Math.toRadians(view.pitch());
double dx=-Math.sin(yaw)*Math.cos(pitch),dy=-Math.sin(pitch),dz=Math.cos(yaw)*Math.cos(pitch);
double t=Math.max(0,(center.x()-eye.x())*dx+(center.y()-eye.y())*dy+(center.z()-eye.z())*dz);
double mx=(b.max().x()-b.min().x())*.16,my=(b.max().y()-b.min().y())*.18,mz=(b.max().z()-b.min().z())*.16;
return new Vec(clamp(eye.x()+t*dx,b.min().x()+mx,b.max().x()-mx),clamp(eye.y()+t*dy,b.min().y()+my,b.max().y()-my),clamp(eye.z()+t*dz,b.min().z()+mz,b.max().z()-mz));
}
/** Ray / box geometry for local practice. A ray already intersecting the box is never recentered. */
private static Vec rayDirection(Angles view) {
double y=Math.toRadians(view.yaw()),p=Math.toRadians(view.pitch());
return new Vec(-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p));
}
public static boolean intersects(Vec eye,Angles view,Box box) {
Vec d=rayDirection(view);
double lo=0,hi=Double.POSITIVE_INFINITY;
double[] o={eye.x(),eye.y(),eye.z()},v={d.x(),d.y(),d.z()},mn={box.min().x(),box.min().y(),box.min().z()},mx={box.max().x(),box.max().y(),box.max().z()};
for(int i=0;i<3;i++) {
if(Math.abs(v[i])<1e-10) { if(o[i]<mn[i] || o[i]>mx[i]) return false; continue; }
double t1=(mn[i]-o[i])/v[i],t2=(mx[i]-o[i])/v[i];
lo=Math.max(lo,Math.min(t1,t2)); hi=Math.min(hi,Math.max(t1,t2));
if(hi<lo) return false;
}
return true;
}
public static Angles bodyDirection(Vec eye,Angles view,Box box) { return bodyDirection(eye,view,box,.035); }
public static Angles lockDirection(Vec eye,Angles view,Box box) { return bodyDirection(eye,view,box,.22); }
private static Angles bodyDirection(Vec eye,Angles view,Box box,double margin) {
if(intersects(eye,view,box)) return view;
Vec d=rayDirection(view);
double x=(box.max().x()-box.min().x())*margin,y=(box.max().y()-box.min().y())*margin,z=(box.max().z()-box.min().z())*margin;
Box inner=new Box(new Vec(box.min().x()+x,box.min().y()+y,box.min().z()+z),new Vec(box.max().x()-x,box.max().y()-y,box.max().z()-z));
double high=0;
for(double xx:new double[]{inner.min().x(),inner.max().x()})
for(double yy:new double[]{inner.min().y(),inner.max().y()})
for(double zz:new double[]{inner.min().z(),inner.max().z()})
high=Math.max(high,(xx-eye.x())*d.x()+(yy-eye.y())*d.y()+(zz-eye.z())*d.z());
double lo=0,hi=high;
for(int i=0;i<24;i++) {
double l=lo+(hi-lo)/3,r=hi-(hi-lo)/3;
if(rayBoxDistance(eye,d,inner,l)<=rayBoxDistance(eye,d,inner,r)) hi=r; else lo=l;
}
double t=(lo+hi)*.5;
return direction(eye,new Vec(clamp(eye.x()+d.x()*t,inner.min().x(),inner.max().x()),clamp(eye.y()+d.y()*t,inner.min().y(),inner.max().y()),clamp(eye.z()+d.z()*t,inner.min().z(),inner.max().z())));
}
private static double rayBoxDistance(Vec eye,Vec d,Box box,double t) {
return boxDistance(new Vec(eye.x()+d.x()*t,eye.y()+d.y()*t,eye.z()+d.z()*t),box);
}
public static Vec trackingPoint(Vec eye,Angles view,Box b,double phase) {
Vec near=nearestAimPoint(eye,view,b);
double fx=.5+.045*Math.sin(phase*.055);
double fy=.63+.045*Math.sin(phase*.043);
double fz=.5+.045*Math.cos(phase*.049);
Vec anchor=new Vec(b.min().x()+(b.max().x()-b.min().x())*fx,b.min().y()+(b.max().y()-b.min().y())*fy,b.min().z()+(b.max().z()-b.min().z())*fz);
return new Vec(near.x()*.75+anchor.x()*.25,near.y()*.75+anchor.y()*.25,near.z()*.75+anchor.z()*.25);
}
public static double error(Angles a,Angles b) { double ap=Math.toRadians(a.pitch()),bp=Math.toRadians(b.pitch()),dy=Math.toRadians(wrap(b.yaw()-a.yaw()));
return Math.toDegrees(Math.acos(clamp(Math.sin(ap)*Math.sin(bp)+Math.cos(ap)*Math.cos(bp)*Math.cos(dy),-1,1))); }
public static Angles direction(Vec eye,Vec p) {
double x=p.x()-eye.x(),y=p.y()-eye.y(),z=p.z()-eye.z();
return new Angles(Math.toDegrees(Math.atan2(z,x))-90,-Math.toDegrees(Math.atan2(y,Math.hypot(x,z))));
}
public static Vec movingPoint(Vec base,Box box,Vec relativeMotion) {
double marginX=(box.max().x()-box.min().x())*.12,marginY=(box.max().y()-box.min().y())*.12,marginZ=(box.max().z()-box.min().z())*.12;
return new Vec(clamp(base.x()+relativeMotion.x()*.5,box.min().x()+marginX,box.max().x()-marginX),clamp(base.y()+relativeMotion.y()*.5,box.min().y()+marginY,box.max().y()-marginY),clamp(base.z()+relativeMotion.z()*.5,box.min().z()+marginZ,box.max().z()-marginZ));
}
public static Angles movingStep(Angles current,Angles target,CombatSettings settings,double motion) {
if(!settings.aimEnabled() || error(current,target)>settings.fov()/2) return current;
double boost=clamp(motion/5,0,1);
double dy=wrap(target.yaw()-current.yaw()),dp=target.pitch()-current.pitch();
return new Angles(wrap(current.yaw()+clamp(dy*(settings.horizontalSpeed()+(1-settings.horizontalSpeed())*.55*boost),-12-6*boost,12+6*boost)),clamp(current.pitch()+clamp(dp*(settings.verticalSpeed()+(1-settings.verticalSpeed())*.55*boost),-8-4*boost,8+4*boost),-90,90));
}
public record Turn(double yaw,double pitch) {}
public static Turn smoothTurn(Angles current,Angles target,Turn previous,CombatSettings settings,double motion) {
if(!settings.aimEnabled() || error(current,target)>settings.fov()/2) return new Turn(0,0);
double dy=wrap(target.yaw()-current.yaw()),dp=target.pitch()-current.pitch();
double boost=clamp(motion/8,0,1);
double capY=7+2*boost,capP=4+1.5*boost;
double wantY=clamp(dy*(.18+.29*settings.horizontalSpeed()+.12*boost),-capY,capY);
double wantP=clamp(dp*(.15+.30*settings.verticalSpeed()+.12*boost),-capP,capP);
return new Turn(slew(dy,wantY,previous.yaw(),2.0+boost),slew(dp,wantP,previous.pitch(),1.2+.6*boost));
}
/** Faster acquisition outside the box, still bounded and without overshoot. */
public static Turn lockTurn(Vec eye,Angles current,Box box,Turn previous,CombatSettings settings,double motion) {
if(!settings.aimEnabled() || intersects(eye,current,box)) return new Turn(0,0);
var target=lockDirection(eye,current,box);
double error=error(current,target);
if(error>settings.fov()/2) return new Turn(0,0);
double dy=wrap(target.yaw()-current.yaw()),dp=target.pitch()-current.pitch();
double moving=clamp(motion/8,0,1);
double yawLimit=32+12*moving,pitchLimit=20+8*moving;
// Acquire the interior of the box directly when reachable within the bounded turn.
return new Turn(clamp(dy,-yawLimit,yawLimit),clamp(dp,-pitchLimit,pitchLimit));
}
private static double slew(double error,double desired,double previous,double acceleration) {
if(Math.abs(error)<.12) return 0;
if(Math.signum(previous)!=Math.signum(error)) previous=0;
double next=previous+clamp(desired-previous,-acceleration,acceleration);
// Limit residual inertia near the target; never exceed the remaining error.
double brakingLimit=Math.sqrt(2*acceleration*Math.abs(error));
return Math.copySign(Math.min(Math.abs(error),Math.min(brakingLimit,Math.abs(next))),error);
}
public static boolean shouldApproach(double horizontalDistanceSquared,double yawError) {
return horizontalDistanceSquared>6.25 && horizontalDistanceSquared<=36 && Math.abs(yawError)<25;
}
public static double boxDistance(Vec eye,Box box) {
double dx=eye.x()-clamp(eye.x(),box.min().x(),box.max().x());
double dy=eye.y()-clamp(eye.y(),box.min().y(),box.max().y());
double dz=eye.z()-clamp(eye.z(),box.min().z(),box.max().z());
return Math.sqrt(dx*dx+dy*dy+dz*dz);
}
public static double futureBoxDistance(Vec eye,Box box,Vec relativeMotion,int ticks) {
double x=clamp(relativeMotion.x(),-.45,.45)*ticks,y=clamp(relativeMotion.y(),-.45,.45)*ticks,z=clamp(relativeMotion.z(),-.45,.45)*ticks;
return boxDistance(new Vec(eye.x()-x,eye.y()-y,eye.z()-z),box);
}
public record Movement(boolean forward,boolean back,boolean left,boolean right) {}
public static Movement flank(double dx,double dz,double targetYaw,double viewYaw) {
double d=Math.hypot(dx,dz);
if(d<.01 || d>6) return new Movement(false,false,false,false);
double rx=-dx/d,rz=-dz/d;
double yaw=Math.toRadians(targetYaw);
double bx=Math.sin(yaw),bz=-Math.cos(yaw);
double cross=rx*bz-rz*bx,dot=rx*bx+rz*bz;
double radial=clamp((d-2.25)*.8,-1,1);
double orbit=dot>.92?0:Math.copySign(.85,Math.abs(cross)<.001?1:cross)*clamp((4.0-d)/1.4,0,1);
double wx=-rx*radial-rz*orbit,wz=-rz*radial+rx*orbit;
double view=Math.toRadians(viewYaw);
double forward=wx*-Math.sin(view)+wz*Math.cos(view);
double right=wx*-Math.cos(view)+wz*-Math.sin(view);
return new Movement(forward>.23,forward<-.35,right<-.23,right>.23);
}
public static double targetScore(double angle,double fov,double distanceSquared) {
return .7*angle/(fov*.5)+.3*Math.min(1,Math.sqrt(Math.max(0,distanceSquared))/4);
}
public static boolean retainTarget(double bestScore,double retainedScore) {
return Double.isFinite(retainedScore) && bestScore>=retainedScore*.75;
}
public static Angles step(Angles current,Angles target,CombatSettings settings) {
if (!settings.aimEnabled()) return current;
double dy=wrap(target.yaw()-current.yaw()),dp=target.pitch()-current.pitch();
if (error(current,target)>settings.fov()/2) return current;
return new Angles(wrap(current.yaw()+clamp(dy*settings.horizontalSpeed(),-12,12)),clamp(current.pitch()+clamp(dp*settings.verticalSpeed(),-8,8),-90,90));
}
private AimModel() {}
}