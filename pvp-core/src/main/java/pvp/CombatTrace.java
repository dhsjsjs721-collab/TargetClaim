package pvp;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
/** Bounded local diagnostics. No remote collection or persistent identifiers. */
public final class CombatTrace {
private final ArrayDeque<String> events=new ArrayDeque<>();
private final Map<String,Long> counts=new LinkedHashMap<>();
private String last="";
private int attempts,confirmed,expired;
private double latency;
public void clear() { events.clear(); counts.clear(); last=""; attempts=confirmed=expired=0; latency=0; }
public void state(long tick,String value) {
counts.merge(value,1L,Long::sum);
if(!value.equals(last)) { event(tick,value); last=value; }
}
public void event(long tick,String value) {
if(events.size()>=48) events.removeFirst();
events.addLast(tick+" "+value);
}
public void attempt(long tick,boolean critical) { attempts++; event(tick,critical?"CRITICAL_ATTEMPT":"NORMAL_ATTEMPT"); }
public void confirm(long tick,long sent) { confirmed++; double elapsed=Math.max(0,tick-sent); latency=confirmed==1?elapsed:latency*.8+elapsed*.2; event(tick,"DAMAGE_CONFIRMED"); }
public void expire(long tick) { expired++; event(tick,"NO_DAMAGE_EVENT"); }
public String summary() { return confirmed+"/"+attempts+" hits | "+expired+" timeout"; }
public String report() { return "PvP Practice diagnostics\n"+summary()+" | observed feedback delay: "+String.format(java.util.Locale.ROOT,"%.1f",latency)+" ticks\nStates: "+counts+"\nRecent transitions:\n"+String.join("\n",events); }
}
