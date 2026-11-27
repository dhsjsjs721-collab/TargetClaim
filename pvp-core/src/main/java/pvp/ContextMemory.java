package pvp;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Rolling anonymous summary of completed local calibration sessions. */
public final class ContextMemory {
public static final int CAPACITY=64;
private static final String HEADER="LOL-CALIBRATION-1";
public record Row(long samples,double stillSquared,double linearSquared,double trackerSquared) {
public Row {
if(samples<0 || samples>1_000_000_000L || !valid(stillSquared) || !valid(linearSquared) || !valid(trackerSquared) || samples==0 && (stillSquared!=0 || linearSquared!=0 || trackerSquared!=0)) throw new IllegalArgumentException("Invalid calibration row");
}
private static boolean valid(double n) { return Double.isFinite(n) && n>=0 && n<=1e15; }
}
public record Session(Row one,Row three,Row six) {
public Session { Objects.requireNonNull(one); Objects.requireNonNull(three); Objects.requireNonNull(six); }
public Row row(int i) { return switch(i) { case 0 -> one; case 1 -> three; case 2 -> six; default -> throw new IndexOutOfBoundsException(i); }; }
public boolean empty() { return one.samples()==0 && three.samples()==0 && six.samples()==0; }
}
private final ArrayDeque<Session> sessions=new ArrayDeque<>();
public int size() { return sessions.size(); }
public void add(Session session) { if(session.empty()) return; if(sessions.size()==CAPACITY) sessions.removeFirst(); sessions.addLast(session); }
public String report() {
var out=new StringBuilder("Local calibration history: ").append(size()).append("/").append(CAPACITY).append(" sessions");
for(int i=0;i<3;i++) {
long count=0; double a=0,b=0,c=0;
for(var session:sessions) { var row=session.row(i); count+=row.samples(); a+=row.stillSquared(); b+=row.linearSquared(); c+=row.trackerSquared(); }
out.append("\n  +").append(new int[]{1,3,6}[i]).append(" ticks: samples=").append(count);
if(count>0) out.append(String.format(Locale.ROOT," RMS stationary=%.4f linear=%.4f tracker=%.4f",Math.sqrt(a/count),Math.sqrt(b/count),Math.sqrt(c/count)));
}
return out.toString();
}
public static ContextMemory read(Path path) throws IOException {
var result=new ContextMemory();
if(!Files.exists(path)) return result;
if(Files.size(path)>65536) throw new IOException("Calibration file too large");
try(var reader=Files.newBufferedReader(path)) {
if(!HEADER.equals(reader.readLine())) throw new IOException("Unsupported calibration format");
String line; int lines=0;
while((line=reader.readLine())!=null) {
if(++lines>CAPACITY || line.length()>1024) throw new IOException("Calibration limit exceeded");
String[] parts=line.split(",",-1);
if(parts.length!=12) throw new IOException("Invalid calibration columns");
try { var rows=new Row[3]; for(int i=0;i<3;i++) { int k=i*4; rows[i]=new Row(Long.parseLong(parts[k]),Double.parseDouble(parts[k+1]),Double.parseDouble(parts[k+2]),Double.parseDouble(parts[k+3])); }
result.add(new Session(rows[0],rows[1],rows[2]));
} catch(IllegalArgumentException ex) { throw new IOException("Invalid calibration values",ex); }
}
}
return result;
}
public void write(Path path) throws IOException {
Path absolute=path.toAbsolutePath(),parent=absolute.getParent();
Files.createDirectories(parent);
Path temp=Files.createTempFile(parent,"lol-calibration-",".tmp");
try {
try(var writer=Files.newBufferedWriter(temp)) {
writer.write(HEADER); writer.newLine();
for(var session:sessions) {
for(int i=0;i<3;i++) {
if(i>0) writer.write(",");
var row=session.row(i);
writer.write(row.samples()+","+row.stillSquared()+","+row.linearSquared()+","+row.trackerSquared());
}
writer.newLine();
}
}
try { Files.move(temp,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
catch(AtomicMoveNotSupportedException ex) { Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING); }
} finally { Files.deleteIfExists(temp); }
}
}
