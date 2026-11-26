package dev.unified;
import java.lang.reflect.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
final class ClanGlow {
private final UnifiedPlugin host;
private record Pair(UUID viewer,UUID target){}
private final Set<Pair> previous=new HashSet<>();
private Method handle,getEntityData,getValue,create,send;
private Field connection;
private Object accessor;
private Constructor<?> packet;
private boolean ready;
ClanGlow(UnifiedPlugin host){this.host=host;}
void enable(){try{
Class<?> entity=Class.forName("net.minecraft.world.entity.Entity"),ac=Class.forName("net.minecraft.network.syncher.EntityDataAccessor"),data=Class.forName("net.minecraft.network.syncher.SynchedEntityData"),value=Class.forName(data.getName()+"$DataValue"),pkt=Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
Field f=entity.getDeclaredField("DATA_SHARED_FLAGS_ID");f.setAccessible(true);accessor=f.get(null);
getEntityData=entity.getMethod("getEntityData");getValue=data.getMethod("get",ac);create=value.getMethod("create",ac,Object.class);
packet=pkt.getConstructor(int.class,List.class);
handle=Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle");connection=handle.getReturnType().getField("connection");
for(Method method:connection.getType().getMethods())if(method.getName().equals("send")&&method.getParameterCount()==1&&method.getParameterTypes()[0].isAssignableFrom(pkt)){send=method;break;}
if(send==null)throw new NoSuchMethodException("send");
ready=true;host.getLogger().info("SELFTEST clan viewer metadata OK");Bukkit.getScheduler().runTaskTimer(host,this::tick,5L,5L);
}catch(Exception e){host.getLogger().warning("Clan glow disabled: "+e);}}
private boolean send(Player viewer,Player target,boolean glow){try{
Object data=getEntityData.invoke(handle.invoke(target));byte flags=((Number)getValue.invoke(data,accessor)).byteValue();Object metadata=create.invoke(null,accessor,(byte)(flags|(glow?0x40:0)));
send.invoke(connection.get(handle.invoke(viewer)),packet.newInstance(target.getEntityId(),List.of(metadata)));return true;
}catch(Exception e){if(ready)host.getLogger().warning("Clan glow packet failed: "+e);ready=false;return false;}}
private void tick(){if(!ready)return;Set<Pair> current=new HashSet<>();for(Player target:Bukkit.getOnlinePlayers())if(!target.isDead())for(Player viewer:target.getTrackedBy())if(viewer!=target&&viewer.canSee(target)&&host.areClanmates(viewer.getUniqueId(),target.getUniqueId())){if(send(viewer,target,true))current.add(new Pair(viewer.getUniqueId(),target.getUniqueId()));}
for(Pair pair:previous)if(!current.contains(pair)){Player viewer=Bukkit.getPlayer(pair.viewer),target=Bukkit.getPlayer(pair.target);if(viewer!=null&&target!=null&&target.getTrackedBy().contains(viewer)&&viewer.canSee(target))send(viewer,target,false);}previous.clear();previous.addAll(current);}
void disable(){for(Pair pair:previous){Player viewer=Bukkit.getPlayer(pair.viewer),target=Bukkit.getPlayer(pair.target);if(viewer!=null&&target!=null&&target.getTrackedBy().contains(viewer))send(viewer,target,false);}previous.clear();}
}
