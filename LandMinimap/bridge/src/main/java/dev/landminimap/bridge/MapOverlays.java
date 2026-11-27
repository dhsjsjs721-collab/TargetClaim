package dev.landminimap.bridge;

import org.bukkit.*;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

final class MapOverlays {
    static final String CHANNEL="landminimap:overlays";
    private record Point(int kind,int x,int y,int z,String label){}
    private record Zone(int kind,int x1,int z1,int x2,int z2,int y1,int y2,String label){}
    private final JavaPlugin host,bridge;
    private final Method claimModule;
    private final Function<UUID,UUID> clan;
    MapOverlays(JavaPlugin host,JavaPlugin bridge,Method claimModule,Function<UUID,UUID> clan){this.host=host;this.bridge=bridge;this.claimModule=claimModule;this.clan=clan;}
    private static void id(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static void label(DataOutputStream out,String s)throws IOException{
        s=s.replaceAll("[\\p{Cntrl}§]","").strip();
        while(s.getBytes(StandardCharsets.UTF_8).length>96)s=s.substring(0,s.offsetByCodePoints(s.length(),-1));
        if(s.isEmpty())s="?";
        byte[] b=s.getBytes(StandardCharsets.UTF_8);out.writeByte(b.length);out.write(b);
    }
    private static void point(List<Point> out,World world,Location l,int kind,String text){
        if(l!=null&&l.isWorldLoaded()&&world.equals(l.getWorld())&&Math.abs((long)l.getBlockX())<=30000000&&Math.abs((long)l.getBlockZ())<=30000000)
            out.add(new Point(kind,l.getBlockX(),l.getBlockY(),l.getBlockZ(),text));
    }
    private static int edge(double n){return (int)Math.max(-30000000,Math.min(30000000,n));}
    private void storedPoints(Player p,List<Point> out){
        World world=p.getWorld();
        for(String type:List.of("bed","anchor"))for(int i=0;i<6;i++){
            String raw=p.getPersistentDataContainer().get(new NamespacedKey("minecraftunified","respawn_"+type+"_"+i),PersistentDataType.STRING);
            if(raw==null)continue;
            try{
                String[] v=raw.split(",");if(v.length!=4||!world.getUID().equals(UUID.fromString(v[0])))continue;
                Location l=new Location(world,Integer.parseInt(v[1]),Integer.parseInt(v[2]),Integer.parseInt(v[3]));
                if(Math.abs((long)l.getBlockX())>30000000||Math.abs((long)l.getBlockZ())>30000000||l.getBlockY()<world.getMinHeight()||l.getBlockY()>=world.getMaxHeight())continue;
                String name=(type.equals("bed")?"Кровать ":"Якорь ")+(i+1);
                if(world.isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4)){
                    var data=l.getBlock().getBlockData();
                    if(type.equals("bed")&&!(data instanceof Bed))continue;
                    if(type.equals("anchor")){if(!(data instanceof RespawnAnchor a))continue;if(a.getCharges()==0)name+=" (пуст)";}
                }else name+=" (?)";
                point(out,world,l,type.equals("bed")?3:4,name);
            }catch(RuntimeException ignored){}
        }
    }
    private static Object field(Object o,String name)throws ReflectiveOperationException{
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);
    }
    private static int bound(Object o,String name)throws ReflectiveOperationException{
        Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return ((Number)m.invoke(o)).intValue();
    }
    private void claims(Player p,UUID group,List<Zone> out){
        try{
            Object module=claimModule.invoke(host);if(module==null)return;
            Object data=field(module,"claims");if(!(data instanceof Map<?,?> all))return;
            for(Object c:all.values()){
                if(out.size()>=64)break;
                if(!p.getWorld().getUID().equals(field(c,"world")))continue;
                UUID owner=(UUID)field(c,"owner");
                if(!p.getUniqueId().equals(owner)&&!(group!=null&&Boolean.TRUE.equals(field(c,"teamBuild"))&&group.equals(clan.apply(owner))))continue;
                String name=(String)field(c,"ownerName");
                int level=((Number)field(c,"level")).intValue();boolean independent=Boolean.TRUE.equals(field(c,"independent"));
                String price=independent?"фикс.":switch(level){case 0->"23 амет.";case 1->"64 амет.";case 2->"3 незер.";case 3->"яйцо";default->"макс.";};
                try{Method max=module.getClass().getDeclaredMethod("maxLevel",c.getClass());max.setAccessible(true);if(level>=((Number)max.invoke(module,c)).intValue())price="макс.";}catch(ReflectiveOperationException ignored){}
                name=name+" L"+level+" · "+price;
                out.add(new Zone(3,bound(c,"minX"),bound(c,"minZ"),bound(c,"maxX"),bound(c,"maxZ"),bound(c,"minY"),bound(c,"maxY"),"Приват "+name));
            }
        }catch(ReflectiveOperationException|RuntimeException ignored){/* Unknown claim schema fails closed. */}
    }
    void send(Player p){
        if(!p.isOnline()||!p.getListeningPluginChannels().contains(CHANNEL))return;
        World world=p.getWorld();UUID group=clan.apply(p.getUniqueId());
        List<Point> points=new ArrayList<>();List<Zone> zones=new ArrayList<>();
        World overworld=Bukkit.getWorlds().getFirst();
        if(world.equals(overworld))point(points,world,overworld.getSpawnLocation(),1,"Мировой спавн");
        if(group!=null)try{point(points,world,host.getConfig().getLocation("season.spawns."+group),2,"Спавн клана");}catch(RuntimeException ignored){}
        storedPoints(p,points);
        try{point(points,world,p.getRespawnLocation(false),5,"Текущий спавн");}catch(RuntimeException ignored){}
        try{point(points,world,p.getLastDeathLocation(),6,"Последняя смерть");}catch(RuntimeException ignored){}
        String history=p.getPersistentDataContainer().getOrDefault(new NamespacedKey("landminimapbridge","death_history"),PersistentDataType.STRING,"");
        int death=0;for(String row:history.split("\\n"))try{
            String[] v=row.split(",");if(v.length!=5||!world.getUID().equals(UUID.fromString(v[0])))continue;
            point(points,world,new Location(world,Integer.parseInt(v[1]),Integer.parseInt(v[2]),Integer.parseInt(v[3])),6,"Смерть "+(++death));
            if(death>=5)break;
        }catch(RuntimeException ignored){}
        if(world.getEnvironment()==World.Environment.NORMAL&&world.getName().equals(host.getConfig().getString("peace.world","world"))){
            Location s=world.getSpawnLocation();int r=Math.max(16,Math.min(1000,host.getConfig().getInt("peace.radius",250)));
            zones.add(new Zone(1,s.getBlockX()-r,s.getBlockZ()-r,s.getBlockX()+r-1,s.getBlockZ()+r-1,world.getMinHeight(),world.getMaxHeight()-1,"Мирная зона"));
        }
        var border=world.getWorldBorder();var center=border.getCenter();double radius=border.getSize()/2;
        zones.add(new Zone(2,edge(Math.floor(center.getX()-radius)),edge(Math.floor(center.getZ()-radius)),edge(Math.ceil(center.getX()+radius)-1),edge(Math.ceil(center.getZ()+radius)-1),world.getMinHeight(),world.getMaxHeight()-1,"Граница мира"));
        claims(p,group,zones);
        try{
            ByteArrayOutputStream b=new ByteArrayOutputStream();
            try(DataOutputStream out=new DataOutputStream(b)){
                out.writeInt(1);id(out,p.getUniqueId());id(out,world.getUID());out.writeByte(points.size());
                for(Point x:points){out.writeByte(x.kind);out.writeInt(x.x);out.writeInt(x.y);out.writeInt(x.z);label(out,x.label);}
                out.writeByte(zones.size());
                for(Zone z:zones){out.writeByte(z.kind);out.writeInt(z.x1);out.writeInt(z.z1);out.writeInt(z.x2);out.writeInt(z.z2);out.writeInt(z.y1);out.writeInt(z.y2);label(out,z.label);}
            }
            p.sendPluginMessage(bridge,CHANNEL,b.toByteArray());
        }catch(IOException|RuntimeException ignored){}
    }
}
