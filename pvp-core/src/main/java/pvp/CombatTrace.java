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
private double maxYaw,maxPitch,previousYaw,previousPitch,peakDelta,sumDeltaSquared;
private long deltaSamples,geometrySamples,geometryAligned;
private long ghostReady,ghostRayMismatch,ghostDeferred,ghostAttackPlanned;
private long plannerSamples,plannerNanos,plannerPeak,plannerProbes,plannerLimited;
private final long[][] callbackStats=new long[2][4];
private final Map<String,Long> criticalGates=new LinkedHashMap<>();
private boolean previousCorrection;
private final Map<String,Long> movement=new LinkedHashMap<>(),attackGates=new LinkedHashMap<>(),supportActions=new LinkedHashMap<>();
public void clear() { events.clear(); counts.clear(); last=""; attempts=confirmed=expired=comboAttempts=criticalAttempts=0; comboTicks=criticalTicks=recoveryTicks=0; latency=0; samples=onTarget=aimSamples=aimCorrections=0; maxYaw=maxPitch=previousYaw=previousPitch=peakDelta=sumDeltaSquared=0; deltaSamples=geometrySamples=geometryAligned=0; previousCorrection=false; ghostReady=ghostRayMismatch=ghostDeferred=ghostAttackPlanned=0; plannerSamples=plannerNanos=plannerPeak=plannerProbes=plannerLimited=0; criticalGates.clear(); for(var row:callbackStats) java.util.Arrays.fill(row,0); movement.clear(); attackGates.clear(); supportActions.clear(); lastCharge=lastDistance=lastReach=lastAngle=Double.NaN; }
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
if(previousCorrection) { double delta=Math.hypot(yaw-previousYaw,pitch-previousPitch); peakDelta=Math.max(peakDelta,delta); sumDeltaSquared+=delta*delta; deltaSamples++; }
previousYaw=yaw; previousPitch=pitch; previousCorrection=true;
}
public void breakAimSequence() { previousCorrection=false; }
public void aimObservation(boolean aligned) { geometrySamples++; if(aligned) geometryAligned++; }
public void attackGate(boolean aligned,boolean inReach,boolean itemReady,double charge,boolean retreating) {
String gate=!inReach?"OUT_OF_RANGE":!aligned?"NO_RAY":!itemReady?"ITEM_BUSY":!Double.isFinite(charge) || charge<.95?"COOLDOWN":retreating?"RETREAT_READY":"READY";
attackGates.merge(gate,1L,Long::sum);
}
public void ghostWindow(long tick,boolean geometry,boolean ray,boolean range,double charge,CombatEngine.Action action) {
if(geometry && !ray && range) { ghostRayMismatch++; event(tick,"GHOST_GEOMETRY_RAY_MISMATCH"); }
if(range && ray && Double.isFinite(charge) && charge>=.95) {
ghostReady++;
if(CombatEngine.attackAction(action)) ghostAttackPlanned++; else ghostDeferred++;
}
}
public void callbackTime(boolean start,long nanos) {
var stats=callbackStats[start?0:1]; stats[0]++; stats[1]+=nanos; stats[2]=Math.max(stats[2],nanos); if(nanos>=5_000_000L) stats[3]++;
}
private String callbackReport(int phase) {
var s=callbackStats[phase];
return "samples="+s[0]+" avg_ms="+fmt(s[0]==0?Double.NaN:s[1]/(1e6*s[0]))+" max_ms="+fmt(s[2]/1e6)+" >=5ms="+s[3];
}
public void planner(int probes,long nanos,boolean limited) {
plannerSamples++; plannerProbes+=probes; plannerNanos+=nanos; plannerPeak=Math.max(plannerPeak,nanos); if(limited) plannerLimited++;
}
public void criticalWindow(CombatEngine.Frame f,int descendingTicks,boolean hitThisFall,boolean itemReady) {
String gate=!f.inRange()?"RANGE":!f.crosshairOnTarget()?"RAY":!itemReady?"ITEM_OR_DELAY":!Double.isFinite(f.cooldown()) || f.cooldown()<.95?"CHARGE":f.restricted()?"RESTRICTED":f.grounded()?"GROUND":hitThisFall?"ALREADY_ATTEMPTED":f.sprinting()?"SPRINT":f.fallDistance()<=0 || descendingTicks<1?"NOT_DESCENDING":"DESCENT_READY";
criticalGates.merge(gate,1L,Long::sum);
}
public void support(String value) { supportActions.merge(value,1L,Long::sum); }
public void movement(String value) { movement.merge(value,1L,Long::sum); }
private static String fmt(double value) { return Double.isFinite(value)?String.format(java.util.Locale.ROOT,"%.2f",value):"n/a"; }
public String summary() { return confirmed+"/"+attempts+" hits | "+expired+" timeout"; }
public String report() { return "Land of Losers diagnostics\n"+summary()+" | observed feedback delay: "+String.format(java.util.Locale.ROOT,"%.1f",latency)+" ticks\nStyles: combo="+comboTicks+" critical="+criticalTicks+" | attempts: sprint="+comboAttempts+" critical="+criticalAttempts+"\nRecovery ticks: "+recoveryTicks+"\nSamples aligned: "+onTarget+"/"+samples+"\nLast sample: charge="+fmt(lastCharge)+" distance="+fmt(lastDistance)+" reach="+fmt(lastReach)+" angle="+fmt(lastAngle)+"\nAim corrections: "+aimCorrections+"/"+aimSamples+" | max yaw="+fmt(maxYaw)+" pitch="+fmt(maxPitch)+"\nGeometry aligned: "+geometryAligned+"/"+geometrySamples+" | correction delta RMS="+fmt(deltaSamples==0?Double.NaN:Math.sqrt(sumDeltaSquared/deltaSamples))+" peak="+fmt(peakDelta)+" deg/tick^2 | pairs="+deltaSamples+"\nGhost windows: ready="+ghostReady+" planned="+ghostAttackPlanned+" deferred="+ghostDeferred+" geometry/ray mismatch="+ghostRayMismatch+"\nPlanner: samples="+plannerSamples+" probes="+plannerProbes+" limited="+plannerLimited+" avg_ms="+fmt(plannerSamples==0?Double.NaN:plannerNanos/(1e6*plannerSamples))+" max_ms="+fmt(plannerPeak/1e6)+"\nSTART callback: "+callbackReport(0)+"\nEND callback: "+callbackReport(1)+"\nLocal critical gates: "+criticalGates+"\nMovement: "+movement+"\nSupport: "+supportActions+"\nAttack gates: "+attackGates+"\nStates: "+counts+"\nRecent transitions:\n"+String.join("\n",events); }
}
