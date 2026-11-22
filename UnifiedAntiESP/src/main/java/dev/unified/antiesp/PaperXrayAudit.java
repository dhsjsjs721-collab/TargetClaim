package dev.unified.antiesp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

/** Read-only inspection. This class never changes server Paper configuration. */
final class PaperXrayAudit {
private PaperXrayAudit(){}
static List<String> inspect(){
List<String> report=new ArrayList<>();
File defaults=new File("config/paper-world-defaults.yml");
if(!defaults.isFile()){
report.add("Anti-Xray audit: config/paper-world-defaults.yml not found; Paper obfuscation status UNKNOWN.");
return report;
}
YamlConfiguration config=YamlConfiguration.loadConfiguration(defaults);
String path="anticheat.anti-xray.";
boolean enabled=config.getBoolean(path+"enabled",false);
int mode=config.getInt(path+"engine-mode",1);
int maxY=config.getInt(path+"max-block-height",64);
boolean air=config.getStringList(path+"hidden-blocks").stream().anyMatch(x->x.equalsIgnoreCase("air"));
boolean bypass=config.getBoolean(path+"use-permission",false);
if(!enabled)report.add("Anti-Xray audit WARN: Paper anti-xray is disabled in world defaults.");
else report.add("Anti-Xray audit: enabled in world defaults.");
if(mode<2)report.add("Anti-Xray audit WARN: engine-mode="+mode+"; 2 or 3 gives stronger obfuscation.");
else report.add("Anti-Xray audit: engine-mode="+mode);
if(!air)report.add("Anti-Xray audit WARN: air missing from hidden-blocks; cave-finder resistance is weaker.");
else report.add("Anti-Xray audit: air included; cave-finder obfuscation enabled (not full cave concealment).");
if(maxY<128)report.add("Anti-Xray audit WARN: max-block-height="+maxY+"; upper caves/ores may leak.");
else report.add("Anti-Xray audit: max-block-height="+maxY);
if(bypass)report.add("Anti-Xray audit: permission bypass enabled; check paper.antixray.bypass.");
for(World world:Bukkit.getWorlds()){
File override=new File(world.getWorldFolder(),"paper-world.yml");
if(!override.isFile())continue;
YamlConfiguration c=YamlConfiguration.loadConfiguration(override);
if(c.contains(path+"enabled") && !c.getBoolean(path+"enabled"))
report.add("Anti-Xray audit WARN: "+world.getName()+" overrides enabled=false.");
if(c.contains(path+"engine-mode") || c.contains(path+"hidden-blocks") || c.contains(path+"max-block-height"))
report.add("Anti-Xray audit: "+world.getName()+" has world override; inspect final effective values manually.");
}
report.add("Paper changes require full server restart; /uesp reload does not reload Paper chunks.");
return report;
}
}
