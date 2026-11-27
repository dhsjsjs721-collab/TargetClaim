package pvp;
import java.nio.file.Path;
import java.util.concurrent.*;
/** No Minecraft objects on worker; bounded queue, serialized IO. */
final class CalibrationHistory {
private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),r->{
var thread=new Thread(r,"land-of-losers-calibration"); thread.setDaemon(true); return thread;
});
private static volatile String report="Local calibration history: loading";
static String report() { return report; }
static void load(Path file) { submit(file,null); }
static void save(Path file,ContextMemory.Session session) { if(!session.empty()) submit(file,session); }
private static void submit(Path file,ContextMemory.Session session) {
try {
worker.execute(()->{
try {
var history=ContextMemory.read(file);
if(session!=null) { history.add(session); history.write(file); }
report=history.report();
} catch(java.io.IOException | RuntimeException ex) { report="Local calibration history unavailable: "+ex.getClass().getSimpleName(); System.err.println("Land of Losers calibration: "+ex.getMessage()); }
});
} catch(RejectedExecutionException ex) { System.err.println("Land of Losers calibration queue full; sample not saved"); }
}
private CalibrationHistory() {}
}
