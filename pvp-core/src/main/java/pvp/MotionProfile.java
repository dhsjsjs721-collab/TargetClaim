package pvp;
import java.util.Locale;
/** Local training observations. Competing one-step forecasts are evaluated
 * before the new observation is consumed. No claim of neural-network training. */
public final class MotionProfile {
public enum Trend { UNKNOWN, STILL, MOVING, REVERSING }
private AimModel.Vec position,velocity;
private long tick=-1,samples;
private int reversals;
private double stillError,velocityError,learnedError;
private AimModel.Vec smoothed=new AimModel.Vec(0,0,0);
private Trend trend=Trend.UNKNOWN;
public void reset() { position=velocity=null; tick=-1; samples=0; reversals=0; stillError=velocityError=learnedError=0; smoothed=new AimModel.Vec(0,0,0); trend=Trend.UNKNOWN; }
private static AimModel.Vec minus(AimModel.Vec a,AimModel.Vec b) { return new AimModel.Vec(a.x()-b.x(),a.y()-b.y(),a.z()-b.z()); }
private static double norm(AimModel.Vec a) { return Math.hypot(Math.hypot(a.x(),a.z()),a.y()); }
private static AimModel.Vec blend(AimModel.Vec a,AimModel.Vec b,double f) { return new AimModel.Vec(a.x()*(1-f)+b.x()*f,a.y()*(1-f)+b.y()*f,a.z()*(1-f)+b.z()*f); }
public void observe(long now,AimModel.Vec observed) {
if(!Double.isFinite(norm(observed))) { reset(); return; }
if(now==tick) return;
if(position==null || now!=tick+1) { position=observed; velocity=null; smoothed=new AimModel.Vec(0,0,0); tick=now; trend=Trend.UNKNOWN; return; }
var measured=minus(observed,position);
if(norm(measured)>1.5) { position=observed; velocity=null; smoothed=new AimModel.Vec(0,0,0); tick=now; trend=Trend.UNKNOWN; return; }
if(velocity!=null) {
double a=norm(measured),b=norm(minus(measured,velocity)),c=norm(minus(measured,smoothed));
stillError+=a*a; velocityError+=b*b; learnedError+=c*c; samples++;
double dot=measured.x()*velocity.x()+measured.z()*velocity.z();
boolean reversal=dot<-.25*Math.hypot(measured.x(),measured.z())*Math.hypot(velocity.x(),velocity.z()) && Math.hypot(measured.x(),measured.z())>.04 && Math.hypot(velocity.x(),velocity.z())>.04;
trend=reversal?Trend.REVERSING:norm(measured)<.035?Trend.STILL:Trend.MOVING;
if(reversal) reversals++;
smoothed=blend(smoothed,measured,reversal?.85:.45);
} else { smoothed=measured; trend=norm(measured)<.035?Trend.STILL:Trend.MOVING; }
velocity=measured; position=observed; tick=now;
}
public Trend trend() { return trend; }
public long samples() { return samples; }
public String report() {
if(samples==0) return "Ghost motion: insufficient consecutive observations";
return String.format(Locale.ROOT,"Ghost motion: %s | samples=%d | reversals=%d | one-step RMS still=%.4f velocity=%.4f adaptive=%.4f blocks",trend,samples,reversals,Math.sqrt(stillError/samples),Math.sqrt(velocityError/samples),Math.sqrt(learnedError/samples));
}
}
