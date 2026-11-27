package pvp;
import java.util.Locale;
/** Compare predictions of relative horizontal spacing against later observations.
 * Diagnostic only; never extrapolates through missing ticks or teleports. */
public final class ApproachCalibration {
private static final int[] H={1,3,6};
private record Forecast(long tick,double stationary,double linear,double adaptive) {}
private final Forecast[][] pending=new Forecast[3][7];
private final long[] count=new long[3];
private final double[][] squared=new double[3][3];
private long last=-1;
private double x,z,vx,vz;
private boolean knownVelocity;
public void reset() {
gap();
java.util.Arrays.fill(count,0);
for(var row:squared) java.util.Arrays.fill(row,0);
}
public void gap() {
for(var row:pending) java.util.Arrays.fill(row,null);
last=-1; vx=vz=0; knownVelocity=false;
}
public void observe(long tick,double dx,double dz,double estimatedX,double estimatedZ) {
if(tick<0 || !Double.isFinite(dx) || !Double.isFinite(dz) || !Double.isFinite(estimatedX) || !Double.isFinite(estimatedZ)) { gap(); return; }
if(tick==last) return;
if(last>=0 && (tick!=last+1 || Math.hypot(dx-x,dz-z)>1.5)) gap();
double actual=Math.hypot(dx,dz);
for(int i=0;i<H.length;i++) {
int slot=(int)(tick%7); var f=pending[i][slot];
if(f!=null && f.tick()==tick) {
count[i]++;
double a=actual-f.stationary(),b=actual-f.linear(),c=actual-f.adaptive();
squared[i][0]+=a*a; squared[i][1]+=b*b; squared[i][2]+=c*c;
}
pending[i][slot]=null;
}
if(last>=0) { vx=dx-x; vz=dz-z; knownVelocity=true; }
x=dx; z=dz; last=tick;
if(!knownVelocity) return;
for(int i=0;i<H.length;i++) {
int h=H[i]; long due=tick+h;
pending[i][(int)(due%7)]=new Forecast(due,actual,Math.hypot(dx+vx*h,dz+vz*h),Math.hypot(dx+estimatedX*h,dz+estimatedZ*h));
}
}
public ContextMemory.Session snapshot() {
return new ContextMemory.Session(row(0),row(1),row(2));
}
private ContextMemory.Row row(int i) { return new ContextMemory.Row(count[i],squared[i][0],squared[i][1],squared[i][2]); }
public long samples(int index) { return count[index]; }
public double rms(int horizonIndex,int model) { return count[horizonIndex]==0?Double.NaN:Math.sqrt(squared[horizonIndex][model]/count[horizonIndex]); }
public String report() {
var out=new StringBuilder("Local approach calibration (horizontal distance, blocks):");
for(int i=0;i<H.length;i++) {
out.append("\n  +").append(H[i]).append(" ticks: samples=").append(count[i]);
if(count[i]>0) out.append(String.format(Locale.ROOT," RMS stationary=%.4f linear=%.4f tracker=%.4f",rms(i,0),rms(i,1),rms(i,2)));
}
return out.toString();
}
}
