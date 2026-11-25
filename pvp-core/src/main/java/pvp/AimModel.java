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
public static Vec trackingPoint(Vec eye,Angles view,Box b,double phase) {
Vec near=nearestAimPoint(eye,view,b);
double fx=.5+.10*Math.sin(phase*.11);
double fy=.63+.09*Math.sin(phase*.073);
double fz=.5+.10*Math.cos(phase*.097);
Vec anchor=new Vec(b.min().x()+(b.max().x()-b.min().x())*fx,b.min().y()+(b.max().y()-b.min().y())*fy,b.min().z()+(b.max().z()-b.min().z())*fz);
return new Vec(near.x()*.45+anchor.x()*.55,near.y()*.45+anchor.y()*.55,near.z()*.45+anchor.z()*.55);
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
public static boolean shouldApproach(double horizontalDistanceSquared,double yawError) {
return horizontalDistanceSquared>6.25 && horizontalDistanceSquared<=36 && Math.abs(yawError)<25;
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