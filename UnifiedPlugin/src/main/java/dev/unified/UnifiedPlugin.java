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
