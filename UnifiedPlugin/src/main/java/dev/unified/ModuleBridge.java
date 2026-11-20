package dev.unified;

import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * Delegates Paper plugin runtime services while isolating each legacy module's
 * data folder and configuration. Modules are ordinary Java objects; the only
 * JavaPlugin registered with Paper is MinecraftUnified.
 */
public abstract class ModuleBridge {
private final JavaPlugin host;
private final String name;
private FileConfiguration config;

protected ModuleBridge(JavaPlugin host,String name){
this.host=host;
this.name=name;
}
public final JavaPlugin host(){return host;}
public final String getName(){return name;}
public final Server getServer(){return host.getServer();}
public final PluginCommand getCommand(String command){return host.getCommand(command);}
public final Logger getLogger(){return host.getLogger();}
public final boolean isEnabled(){return host.isEnabled();}
public final File getDataFolder(){return new File(host.getDataFolder().getParentFile(),name);}
public final FileConfiguration getConfig(){
if(config==null)config=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"config.yml"));
return config;
}
public final void saveDefaultConfig(){
File target=new File(getDataFolder(),"config.yml");
if(target.exists())return;
target.getParentFile().mkdirs();
try(InputStream in=host.getResource("module-defaults/"+name+".yml")){
if(in!=null)Files.copy(in,target.toPath());
else Files.writeString(target.toPath(),"# "+name+" module configuration\\n");
}catch(IOException ex){throw new IllegalStateException("Could not create "+name+" config.yml",ex);}
}
public final void reloadConfig(){config=null;}
public final void saveConfig(){
getDataFolder().mkdirs();
try{getConfig().save(new File(getDataFolder(),"config.yml"));}
catch(IOException e){throw new IllegalStateException("Could not save module config "+name,e);}
}
public void onEnable(){}
public void onDisable(){}
}
