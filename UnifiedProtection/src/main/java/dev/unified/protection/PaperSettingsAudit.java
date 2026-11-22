package dev.unified.protection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class PaperSettingsAudit {
private static final String[] UNSAFE_KEYS={
"allow-piston-duplication",
"skip-tripwire-hook-placement-validation",
"allow-headless-pistons",
"allow-permanent-block-break-exploits",
"allow-unsafe-end-portal-teleportation"
};
private PaperSettingsAudit(){}
static List<String> inspect(JavaPlugin plugin) {
List<String> report=new ArrayList<>();
File serverRoot=plugin.getDataFolder().getParentFile().getParentFile();
File file=new File(serverRoot,"config/paper-global.yml");
if(!file.isFile()){
report.add("Paper audit: cannot read config/paper-global.yml; vanilla-dupe protection NOT verified.");
return report;
}
YamlConfiguration config=YamlConfiguration.loadConfiguration(file);
int danger=0,unknown=0;
for(String key:UNSAFE_KEYS) {
String path="unsupported-settings."+key;
if(!config.contains(path)) {
report.add("Paper audit UNKNOWN (not explicitly set): "+path+"; confirm Paper default for this build.");
unknown++;continue;
}
if(config.getBoolean(path,false)){
report.add("Paper audit UNSAFE: "+path+"=true (set false and restart server).");
danger++;
}else{
report.add("Paper audit OK: "+path+"=false.");
}
}
if(danger==0 && unknown==0)report.add("Paper audit PASS: all 5 checked exploit flags are explicitly disabled.");
else report.add("Paper audit INCOMPLETE: "+danger+" unsafe and "+unknown+" unknown flags.");
return report;
}
}
