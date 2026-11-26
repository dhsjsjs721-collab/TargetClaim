package pvp;
import java.util.Locale;
import java.util.TreeMap;
/** Descriptive analysis of recorded observations, not an attack controller. */
public final class ReplayAnalysis {
public static String analyze(Iterable<CombatReplay.Frame> frames) {
int size=0,ready=0,noRay=0,streak=0,longest=0,readyWait=0,readyAttack=0,readyUnclassified=0,gaps=0;
int attempts=0,confirmed=0;
long first=-1,last=-1;
double hpLost=0,hpGained=0;
var deferred=new TreeMap<String,Integer>(); var attemptResults=new TreeMap<String,Integer>();
int invalidCounters=0,attackPlanWithoutAttempt=0,unattributedAttempts=0;
var gates=new TreeMap<String,Integer>(); var plans=new TreeMap<String,Integer>(); var movement=new TreeMap<String,Integer>();
CombatReplay.Frame prev=null;
for(var f:frames) {
size++;
if(first<0) first=f.tick();
last=f.tick();
boolean adjacent=prev!=null && f.tick()==prev.tick()+1;
if(prev!=null && !adjacent) gaps++;
if(!adjacent) streak=0;
gates.merge(f.gate(),1,Integer::sum); plans.merge(f.plan(),1,Integer::sum); movement.merge(f.movement(),1,Integer::sum);
if("READY_NO_RAY".equals(f.gate())) { noRay++; longest=Math.max(longest,++streak); } else streak=0;
if("READY".equals(f.gate())) {
ready++;
if(!adjacent || f.attempts()<prev.attempts()) readyUnclassified++;
else if(f.attempts()>prev.attempts()) readyAttack++;
else { readyWait++; deferred.merge(f.plan(),1,Integer::sum); }
}
if(adjacent) {
boolean countersValid=f.attempts()>=prev.attempts() && f.confirmed()>=prev.confirmed();
if(!countersValid) invalidCounters++;
else {
int delta=f.attempts()-prev.attempts();
attempts+=delta; confirmed+=f.confirmed()-prev.confirmed();
boolean attackPlan=f.plan().equals("NORMAL") || f.plan().equals("CRITICAL") || f.plan().equals("COMBO");
if(attackPlan && delta==0) { attackPlanWithoutAttempt++; attemptResults.merge(f.result(),1,Integer::sum); }
if(delta>0 && !attackPlan) unattributedAttempts+=delta;
}
if(Double.isFinite(f.hp()) && Double.isFinite(prev.hp())) {
double change=f.hp()-prev.hp();
hpLost+=Math.max(0,-change); hpGained+=Math.max(0,change);
}
}
prev=f;
}
return "Replay frames: "+size+" | ready="+ready+" | charged without ray="+noRay+" | longest consecutive charged/no-ray="+longest
+"\nRecorded ticks: "+first+".."+last+" | gaps="+gaps
+"\nReady ticks: attack observed="+readyAttack+" | no attempt="+readyWait+" | unclassified="+readyUnclassified
+"\nReady deferrals by recorded plan: "+deferred
+"\nAttack plan without counter increase: "+attackPlanWithoutAttempt+" | results="+attemptResults
+"\nUnattributed attempt increments="+unattributedAttempts+" | counter discontinuities="+invalidCounters
+"\nAdjacent-frame counter increases: attempts="+attempts+" confirmed="+confirmed
+"\nObserved health changes: lost="+String.format(Locale.ROOT,"%.2f",hpLost)+" gained="+String.format(Locale.ROOT,"%.2f",hpGained)
+"\nReplay gates: "+gates+"\nReplay plans: "+plans+"\nReplay movement: "+movement
+"\nNotes: READY is a recorded gate, not proof an attack was possible; delayed confirmations may belong to earlier attempts. Health changes are not total damage (absorption is not recorded).";
}
public static void main(String[] args) throws java.io.IOException {
if(args.length!=1) { System.err.println("Usage: java -cp pvp-core-0.2.jar pvp.ReplayAnalysis <replay.csv>"); return; }
try(var reader=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(args[0]))) { System.out.println(CombatReplay.readCsv(reader).summary()); }
}
private ReplayAnalysis() {}
}
