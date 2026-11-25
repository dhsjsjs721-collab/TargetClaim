package dev.unified;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

public final class WorldTabStyle {
private final JavaPlugin plugin;
private Constructor<?> packet,entry;
private Method asVanilla,send,handle;
private Field connection;
private java.lang.reflect.RecordComponent[] fields;
private Enum<?> update;
private boolean failed;
public WorldTabStyle(JavaPlugin plugin){this.plugin=plugin;}
@SuppressWarnings({"unchecked","rawtypes"})
private void init() throws ReflectiveOperationException {
Class<?> packetType=Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
Class<?> entryType=Class.forName(packetType.getName()+"$Entry");
Class<?> action=Class.forName(packetType.getName()+"$Action");
update=Enum.valueOf((Class)action,"UPDATE_DISPLAY_NAME");
fields=entryType.getRecordComponents();
if(fields==null)throw new ReflectiveOperationException("PlayerInfo entry is not a record");
entry=entryType.getDeclaredConstructor(Arrays.stream(fields).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new));entry.setAccessible(true);
packet=packetType.getConstructor(EnumSet.class,List.class);
Class<?> adventure=Class.forName("io.papermc.paper.adventure.PaperAdventure");
asVanilla=adventure.getMethod("asVanilla",Component.class);
Class<?> craft=Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
handle=craft.getMethod("getHandle");
connection=handle.getReturnType().getField("connection");
for(Method method:connection.getType().getMethods())if(method.getName().equals("send")&&method.getParameterCount()==1&&method.getParameterTypes()[0].isAssignableFrom(packetType)){send=method;break;}
if(send==null)throw new NoSuchMethodException("send(Packet)");
}
public void enable(){try{init();plugin.getLogger().info("Per-viewer world tab styling available.");}catch(Exception e){fail(e);}}
private void fail(Exception e){if(!failed)plugin.getLogger().warning("Per-viewer tab styling unavailable; using global clan colors: "+e);failed=true;}
@SuppressWarnings({"rawtypes","unchecked"})
public boolean send(Player viewer,Map<UUID,Component> names){
if(failed||packet==null||names.isEmpty())return !failed;
try{
List<Object> entries=new ArrayList<>();
for(var name:names.entrySet()){
Object[] args=new Object[fields.length];
for(int i=0;i<fields.length;i++){
Class<?> type=fields[i].getType();String key=fields[i].getName();
if(type==UUID.class)args[i]=name.getKey();
else if(key.equals("displayName"))args[i]=asVanilla.invoke(null,name.getValue());
else if(type==boolean.class)args[i]=true;
else if(type==int.class)args[i]=0;
else if(type==long.class)args[i]=0L;
}
entries.add(entry.newInstance(args));
}
Object message=packet.newInstance(EnumSet.of((Enum)update),entries);send.invoke(connection.get(handle.invoke(viewer)),message);return true;
}catch(Exception e){fail(e);return false;}
}
}
