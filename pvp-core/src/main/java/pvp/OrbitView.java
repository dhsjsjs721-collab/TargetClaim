package pvp;
/** Camera-only orientation; independent of the player's gameplay rotation. */
public final class OrbitView {
private double yaw,pitch;
public void reset(double yaw,double pitch) {
this.yaw=Double.isFinite(yaw)?AimModel.wrap(yaw):0;
this.pitch=Double.isFinite(pitch)?Math.max(-90,Math.min(90,pitch)):0;
}
public void turn(double dx,double dy) {
if(!Double.isFinite(dx) || !Double.isFinite(dy)) return;
// Entity.turn receives mouse input already processed by the mouse handler.
yaw=AimModel.wrap(yaw+dx*.15);
pitch=Math.max(-90,Math.min(90,pitch+dy*.15));
}
public float yaw() { return (float)yaw; }
public float pitch() { return (float)pitch; }
}
