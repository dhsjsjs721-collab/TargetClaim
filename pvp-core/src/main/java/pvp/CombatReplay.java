package pvp;
import java.util.ArrayDeque;
import java.util.Locale;
/** Bounded local observations, without player names, world coordinates or packet contents. */
public final class CombatReplay {
public static final int CAPACITY=2400;
public record Frame(long tick,double hp,double targetHp,double dx,double dy,double dz,double charge,double angle,boolean grounded,boolean sprinting,String gate,String plan,String result,String movement,int attempts,int confirmed) {}
private final ArrayDeque<Frame> frames=new ArrayDeque<>();
public void clear() { frames.clear(); }
public int size() { return frames.size(); }
public void add(Frame frame) {
if(!frames.isEmpty() && frame.tick()<=frames.peekLast().tick()) return;
if(frames.size()==CAPACITY) frames.removeFirst();
frames.addLast(frame);
}
private static String number(double value) { return Double.isFinite(value)?String.format(Locale.ROOT,"%.4f",value):""; }
private static String quote(String value) { return "\""+value.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\""; }
public String summary() {
int ready=0,noRay=0,streak=0,longest=0;
for(var f:frames) {
if("READY".equals(f.gate())) ready++;
if("READY_NO_RAY".equals(f.gate())) { noRay++; longest=Math.max(longest,++streak); } else streak=0;
}
return "Replay frames: "+frames.size()+" | ready="+ready+" | charged without ray="+noRay+" | longest consecutive charged/no-ray="+longest;
}
public String csv() {
var out=new StringBuilder("tick,hp,target_hp,relative_x,relative_y,relative_z,predecision_charge,aim_error,grounded,sprinting,gate,plan,result,movement,attempts,confirmed\n");
for(var f:frames) {
out.append(f.tick()).append(',').append(number(f.hp())).append(',').append(number(f.targetHp())).append(',').append(number(f.dx())).append(',').append(number(f.dy())).append(',').append(number(f.dz())).append(',').append(number(f.charge())).append(',').append(number(f.angle())).append(',').append(f.grounded()).append(',').append(f.sprinting()).append(',').append(quote(f.gate())).append(',').append(quote(f.plan())).append(',').append(quote(f.result())).append(',').append(quote(f.movement())).append(',').append(f.attempts()).append(',').append(f.confirmed()).append('\n');
}
return out.toString();
}
}
