package pvp;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Properties;
/** Persists non-activation preferences only. Automation and network permission never persist. */
public record ClientPreferences(double fov,boolean golemTest) {
public ClientPreferences {
if(!Double.isFinite(fov) || fov<35 || fov>360) fov=150;
}
public static ClientPreferences defaults() { return new ClientPreferences(150,false); }
public static ClientPreferences read(Path path) throws IOException {
if(!Files.exists(path)) return defaults();
var p=new Properties();
try(var reader=Files.newBufferedReader(path)) { p.load(reader); }
double fov=150;
try { fov=Double.parseDouble(p.getProperty("fov","150")); } catch(NumberFormatException ignored) {}
return new ClientPreferences(fov,"true".equalsIgnoreCase(p.getProperty("golemTest","false")));
}
public void write(Path path) throws IOException {
Path absolute=path.toAbsolutePath();
Path parent=absolute.getParent();
Files.createDirectories(parent);
Path tmp=Files.createTempFile(parent,"lol-config-",".tmp");
try {
var p=new Properties();
p.setProperty("fov",Double.toString(fov));
p.setProperty("golemTest",Boolean.toString(golemTest));
try(var writer=Files.newBufferedWriter(tmp)) { p.store(writer,"Land of Losers | non-activation preferences"); }
try { Files.move(tmp,absolute,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
catch(AtomicMoveNotSupportedException ignored) { Files.move(tmp,absolute,StandardCopyOption.REPLACE_EXISTING); }
} finally { Files.deleteIfExists(tmp); }
}
}
