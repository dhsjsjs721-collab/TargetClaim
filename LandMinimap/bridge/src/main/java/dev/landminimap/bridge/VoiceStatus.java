package dev.landminimap.bridge;
import org.bukkit.Bukkit;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
final class VoiceStatus {
    private final Map<UUID,Long> speaking=new ConcurrentHashMap<>();
    void register(){
        var plugin=Bukkit.getPluginManager().getPlugin("voicechat");if(plugin==null)return;
        try{
            ClassLoader cl=plugin.getClass().getClassLoader();
            Class<?> service=cl.loadClass("de.maxhenkel.voicechat.api.BukkitVoicechatService");
            Class<?> iface=cl.loadClass("de.maxhenkel.voicechat.api.VoicechatPlugin");
            Class<?> event=cl.loadClass("de.maxhenkel.voicechat.api.events.MicrophonePacketEvent");
            Class<?> registration=cl.loadClass("de.maxhenkel.voicechat.api.events.EventRegistration");
            Method sender=cl.loadClass("de.maxhenkel.voicechat.api.events.PacketEvent").getMethod("getSenderConnection");
            Method player=cl.loadClass("de.maxhenkel.voicechat.api.VoicechatConnection").getMethod("getPlayer");
            Method uuid=cl.loadClass("de.maxhenkel.voicechat.api.Entity").getMethod("getUuid");
            Object svc=Bukkit.getServicesManager().load(service);if(svc==null)return;
            Object proxy=Proxy.newProxyInstance(cl,new Class<?>[]{iface},(o,m,a)->{
                switch(m.getName()){
                    case "getPluginId":return "landminimap";
                    case "hashCode":return System.identityHashCode(o);
                    case "equals":return o==a[0];
                    case "toString":return "LandMinimap voice activity";
                    case "registerEvents":
                        Consumer<Object> cb=packet->{try{Object c=sender.invoke(packet);if(c!=null){UUID id=(UUID)uuid.invoke(player.invoke(c));if(speaking.size()<2048||speaking.containsKey(id))speaking.put(id,System.nanoTime());}}catch(ReflectiveOperationException|RuntimeException ignored){}};
                        registration.getMethod("registerEvent",Class.class,Consumer.class).invoke(a[0],event,cb);return null;
                    default:return null;
                }
            });
            service.getMethod("registerPlugin",iface).invoke(svc,proxy);
        }catch(ReflectiveOperationException|RuntimeException e){plugin.getLogger().warning("LandMinimap voice activity unavailable: "+e.getClass().getSimpleName());}
    }
    boolean speaking(UUID id){return System.nanoTime()-speaking.getOrDefault(id,Long.MIN_VALUE/2)<1_500_000_000L;}
    void forget(UUID id){speaking.remove(id);}
}
