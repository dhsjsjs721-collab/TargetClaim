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
public String summary() { return ReplayAnalysis.analyze(frames); }
public static CombatReplay readCsv(java.io.Reader source) throws java.io.IOException {
var reader=new java.io.BufferedReader(source);
String header=reader.readLine();
if(header==null || !header.equals("tick,hp,target_hp,relative_x,relative_y,relative_z,predecision_charge,aim_error,grounded,sprinting,gate,plan,result,movement,attempts,confirmed")) throw new java.io.IOException("Unexpected replay header");
var replay=new CombatReplay();
String line; int row=1;
while((line=reader.readLine())!=null) {
row++;
if(row>100001 || line.length()>8192) throw new java.io.IOException("Replay input limit exceeded");
if(line.isBlank()) continue;
try {
var c=columns(line);
if(c.size()!=16) throw new IllegalArgumentException("Expected 16 columns");
long tick=Long.parseLong(c.get(0));
if(tick<0 || !replay.frames.isEmpty() && tick<=replay.frames.peekLast().tick()) throw new IllegalArgumentException("Non-increasing tick");
replay.add(new Frame(tick,decimal(c.get(1)),decimal(c.get(2)),decimal(c.get(3)),decimal(c.get(4)),decimal(c.get(5)),decimal(c.get(6)),decimal(c.get(7)),bool(c.get(8)),bool(c.get(9)),c.get(10),c.get(11),c.get(12),c.get(13),Integer.parseInt(c.get(14)),Integer.parseInt(c.get(15))));
} catch(IllegalArgumentException ex) { throw new java.io.IOException("Invalid replay row "+row,ex); }
}
return replay;
}
private static boolean bool(String value) {
if(!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid boolean");
return Boolean.parseBoolean(value);
}
private static double decimal(String value) {
if(value.isEmpty()) return Double.NaN;
double d=Double.parseDouble(value);
if(!Double.isFinite(d)) throw new IllegalArgumentException("Non-finite number");
return d;
}
private static java.util.List<String> columns(String line) {
var result=new java.util.ArrayList<String>();
var field=new StringBuilder(); boolean quoted=false,closed=false;
for(int i=0;i<line.length();i++) {
char c=line.charAt(i);
if(quoted) {
if(c=='"') { if(i+1<line.length() && line.charAt(i+1)=='"') { field.append('"'); i++; } else { quoted=false; closed=true; } }
else field.append(c);
} else if(c==',') { result.add(field.toString()); field.setLength(0); closed=false; }
else if(c=='"' && field.length()==0 && !closed) quoted=true;
else { if(closed || c=='"') throw new IllegalArgumentException("Invalid quoting"); field.append(c); }
}
if(quoted) throw new IllegalArgumentException("Unclosed quote");
result.add(field.toString()); return result;
}
public String csv() {
var out=new StringBuilder("tick,hp,target_hp,relative_x,relative_y,relative_z,predecision_charge,aim_error,grounded,sprinting,gate,plan,result,movement,attempts,confirmed\n");
for(var f:frames) {
out.append(f.tick()).append(',').append(number(f.hp())).append(',').append(number(f.targetHp())).append(',').append(number(f.dx())).append(',').append(number(f.dy())).append(',').append(number(f.dz())).append(',').append(number(f.charge())).append(',').append(number(f.angle())).append(',').append(f.grounded()).append(',').append(f.sprinting()).append(',').append(quote(f.gate())).append(',').append(quote(f.plan())).append(',').append(quote(f.result())).append(',').append(quote(f.movement())).append(',').append(f.attempts()).append(',').append(f.confirmed()).append('\n');
}
return out.toString();
}
}
