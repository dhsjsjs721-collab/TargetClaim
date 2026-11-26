package pvp;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
/** Bounded local diagnostics. No remote collection or persistent identifiers. */
public final class CombatTrace {
private final ArrayDeque<String> events=new ArrayDeque<>();
private final Map<String,Long> counts=new LinkedHashMap<>();
private String last="";
private int attempts,confirmed,expired,comboAttempts,criticalAttempts;
private long comboTicks,criticalTicks,recoveryTicks;
private double latency;
private double lastCharge=Double.NaN,lastDistance=Double.NaN,lastReach=Double.NaN,lastAngle=Double.NaN;
private long samples,onTarget,aimSamples,aimCorrections;
private double maxYaw,maxPitch;
private final Map<String,Long> movement=new LinkedHashMap<>();
public void clear() { events.clear(); counts.clear(); last=""; attempts=confirmed=expired=comboAttempts=criticalAttempts=0; comboTicks=criticalTicks=recoveryTicks=0; latency=0; samples=onTarget=aimSamples=aimCorrections=0; maxYaw=maxPitch=0; movement.clear(); lastCharge=lastDistance=lastReach=lastAngle=Double.NaN; }
public void state(long tick,String value) {
counts.merge(value,1L,Long::sum);
if(!value.equals(last)) { event(tick,value); last=value; }
}
public void event(long tick,String value) {
if(events.size()>=48) events.removeFirst();
events.addLast(tick+" "+value);
}
public void attempt(long tick,boolean critical) { attempt(tick,critical,false); }
public void attempt(long tick,boolean critical,boolean combo) { attempts++; if(critical) criticalAttempts++; else if(combo) comboAttempts++; event(tick,critical?"CRITICAL_ATTEMPT":combo?"SPRINT_HIT_ATTEMPT":"NORMAL_ATTEMPT"); }
public void recovery(boolean recovering) { if(recovering) recoveryTicks++; }
public void style(boolean combo) { if(combo) comboTicks++; else criticalTicks++; }
public void confirm(long tick,long sent) { confirmed++; double elapsed=Math.max(0,tick-sent); latency=confirmed==1?elapsed:latency*.8+elapsed*.2; event(tick,"DAMAGE_CONFIRMED"); }
public void expire(long tick) { expired++; event(tick,"NO_DAMAGE_EVENT"); }
public static boolean manualAction(double before,double after,boolean sameItem,double beforeDelay,double afterDelay) {
return !sameItem || (Double.isFinite(beforeDelay) && Double.isFinite(afterDelay) && Math.abs(beforeDelay-afterDelay)>.001) || (Double.isFinite(before) && Double.isFinite(after) && after<before-.05);
}
public void sample(double charge,double distance,double reach,double angle,boolean aligned) {
lastCharge=charge; lastDistance=distance; lastReach=reach; lastAngle=angle; samples++; if(aligned) onTarget++;
}
public void aimCorrection(double yaw,double pitch) {
if(!Double.isFinite(yaw) || !Double.isFinite(pitch)) return;
aimSamples++;
if(yaw!=0 || pitch!=0) aimCorrections++;
maxYaw=Math.max(maxYaw,Math.abs(yaw)); maxPitch=Math.max(maxPitch,Math.abs(pitch));
}
public void movement(String value) { movement.merge(value,1L,Long::sum); }
private static String fmt(double value) { return Double.isFinite(value)?String.format(java.util.Locale.ROOT,"%.2f",value):"n/a"; }
public String summary() { return confirmed+"/"+attempts+" hits | "+expired+" timeout"; }
public String report() { return "Land of Losers diagnostics\n"+summary()+" | observed feedback delay: "+String.format(java.util.Locale.ROOT,"%.1f",latency)+" ticks\nStyles: combo="+comboTicks+" critical="+criticalTicks+" | attempts: sprint="+comboAttempts+" critical="+criticalAttempts+"\nRecovery ticks: "+recoveryTicks+"\nSamples aligned: "+onTarget+"/"+samples+"\nLast sample: charge="+fmt(lastCharge)+" distance="+fmt(lastDistance)+" reach="+fmt(lastReach)+" angle="+fmt(lastAngle)+"\nAim corrections: "+aimCorrections+"/"+aimSamples+" | max yaw="+fmt(maxYaw)+" pitch="+fmt(maxPitch)+"\nMovement: "+movement+"\nStates: "+counts+"\nRecent transitions:\n"+String.join("\n",events); }
}
