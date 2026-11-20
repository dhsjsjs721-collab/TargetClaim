package dev.unified;

import dev.targetclaim.TargetClaimPlugin;
import dev.talismanforge.TalismanForgePlugin;
import dev.enchantmentforge.EnchantmentForgePlugin;
import dev.swampsky.SwampSkyPlugin;
import dev.imagemaps.ImageMapsPlugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;

public final class UnifiedPlugin extends JavaPlugin {
private final List<ModuleBridge> enabledModules=new ArrayList<>();
@Override public void onEnable(){
getDataFolder().mkdirs();
List<ModuleBridge> modules=List.of(
new TargetClaimPlugin(this),
new TalismanForgePlugin(this),
new EnchantmentForgePlugin(this),
new SwampSkyPlugin(this),
new ImageMapsPlugin(this)
);
for(ModuleBridge module:modules){
try{
module.onEnable();
enabledModules.add(module);
getLogger().info("Enabled module: "+module.getName());
}catch(Throwable failure){
getLogger().severe("Module "+module.getName()+" failed: "+failure);
failure.printStackTrace();
getServer().getPluginManager().disablePlugin(this);
return;
}
}
getLogger().info("All five modules loaded in one JAR. Legacy data folders and item namespaces preserved.");
org.bukkit.command.PluginCommand diagnostics=getCommand("unified");
if(diagnostics!=null)diagnostics.setExecutor((sender,command,label,args)->{
sender.sendMessage("§aMinecraftUnified 1.0.2 §7| модулей: §f"+enabledModules.size()+"/5");
for(ModuleBridge module:enabledModules){
sender.sendMessage("§7• §a"+module.getName()+" §8(запущен)");
}
sender.sendMessage("§7Проверка существ: §f/ss status §7| §f/ss repair §7| §f/ss spawn flying_pig");
sender.sendMessage("§7Торговец: §f/ce trader");
return true;
});
}
@Override public void onDisable(){
for(int i=enabledModules.size()-1;i>=0;i--){
ModuleBridge module=enabledModules.get(i);
try{module.onDisable();}
catch(Throwable failure){getLogger().severe("Shutdown failed for "+module.getName()+": "+failure);}
}
enabledModules.clear();
}
}
