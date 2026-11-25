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
public static Angles direction(Vec eye,Vec p) {
double x=p.x()-eye.x(),y=p.y()-eye.y(),z=p.z()-eye.z();
return new Angles(Math.toDegrees(Math.atan2(z,x))-90,-Math.toDegrees(Math.atan2(y,Math.hypot(x,z))));
}
public static Angles step(Angles current,Angles target,CombatSettings settings) {
if (!settings.aimEnabled()) return current;
double dy=wrap(target.yaw()-current.yaw()),dp=target.pitch()-current.pitch();
if (Math.hypot(dy,dp)>settings.fov()/2) return current;
return new Angles(wrap(current.yaw()+dy*settings.horizontalSpeed()),clamp(current.pitch()+dp*settings.verticalSpeed(),-90,90));
}
private AimModel() {}
}