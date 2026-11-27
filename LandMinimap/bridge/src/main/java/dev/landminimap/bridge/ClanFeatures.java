package dev.landminimap.bridge;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.nio.charset.StandardCharsets;

final class ClanFeatures implements Listener {
    static final String CHANNEL="landminimap:features";
    static final int SURFACE=10000, MAX_CELLS=20000, MAX_TOTAL=200000;
    record Cell(UUID world,int layer,int x,int z){}
    record View(UUID world,int layer,int x,int z,int radius){}
    record Contribution(UUID clan,LinkedHashMap<Cell,Integer> cells){}
    record Ping(UUID clan,UUID world,int x,int y,int z,String label,long expires){}
    private final JavaPlugin plugin;
    private final Function<UUID,UUID> clan;
    private final Map<UUID,Contribution> data=new HashMap<>();
    private final Map<UUID,View> views=new HashMap<>();
    private final List<Ping> pings=new ArrayList<>();
    private final Map<UUID,Long> lastPing=new HashMap<>(),lastDamage=new HashMap<>();
    private final VoiceStatus voice=new VoiceStatus();
    private boolean dirty;
    private int ticks,total;
    ClanFeatures(JavaPlugin plugin,Function<UUID,UUID> clan){
        this.plugin=plugin;this.clan=clan;load();
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,CHANNEL);
        voice.register();
    }
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void str(DataOutputStream out,String s)throws IOException{byte[] b=s.getBytes(StandardCharsets.UTF_8);if(b.length>96)b=new byte[0];out.writeByte(b.length);out.write(b);}
    static int layer(Player p){return p.getWorld().getEnvironment()==World.Environment.NORMAL&&p.getLocation().getBlock().getLightFromSky()==15?SURFACE:Math.floorDiv(p.getLocation().getBlockY(),16);}
    private static int color(Material m){
        String s=m.name();
        if(s.contains("WATER"))return 0xff365fbd;
        if(s.contains("LAVA"))return 0xffdf6a30;
        if(s.contains("GRASS")||s.contains("MOSS")||s.contains("LEAVES"))return 0xff65994e;
        if(s.contains("SAND")||s.contains("END_STONE"))return 0xffc6b87e;
        if(s.contains("SNOW")||s.contains("ICE"))return 0xffd4e8ed;
        if(s.contains("DIRT")||s.contains("MUD")||s.contains("WOOD")||s.contains("LOG")||s.contains("PLANK"))return 0xff8d7150;
        if(s.contains("NETHERRACK")||s.contains("NYLIUM"))return 0xff793b37;
        return 0xff747780; // No ore type or hidden blocks disclosed.
    }
    private void remove(UUID id){Contribution old=data.remove(id);if(old!=null){total-=old.cells.size();dirty=true;}}
    boolean action(Player p,int op,int x,int z,String label){
        UUID id=p.getUniqueId(),group=clan.apply(id);
        if(op==7){
            if(label.equals("off")){remove(id);save();p.sendMessage("§bLandMap: общая разведка выключена; вклад удалён.");return true;}
            if(!label.equals("on")||group==null)return true;
            Contribution c=data.get(id);
            if(c==null||!c.clan.equals(group)){remove(id);if(data.size()>=128){p.sendMessage("§cЛимит хранилища разведки.");return true;}data.put(id,new Contribution(group,new LinkedHashMap<>()));dirty=true;save();}
            p.sendMessage("§bLandMap: общая разведка включена. Передаются только посещённые тобой клетки.");return true;
        }
        if(op==8){
            try{String[] v=label.split(":");int layer=Integer.parseInt(v[0]),radius=Integer.parseInt(v[1]);
                if((layer==SURFACE||layer>=-256&&layer<=256)&&radius>=32&&radius<=256&&Math.abs((long)x)<=7500000&&Math.abs((long)z)<=7500000)views.put(id,new View(p.getWorld().getUID(),layer,x,z,radius));
            }catch(RuntimeException ignored){}return true;
        }
        if(op==9){
            if(group==null||!Set.of("Помощь","Опасность","Сюда").contains(label))return true;
            long now=System.currentTimeMillis();if(now-lastPing.getOrDefault(id,0L)<3000)return true;
            if(pings.size()>=128)return true;lastPing.put(id,now);
            Location l=p.getLocation();pings.add(new Ping(group,p.getWorld().getUID(),l.getBlockX(),l.getBlockY(),l.getBlockZ(),label+" · "+p.getName(),now+30000));
            announce(p,"§e"+p.getName()+": "+label+" · "+l.getBlockX()+", "+l.getBlockY()+", "+l.getBlockZ());return true;
        }
        return false;
    }
    private void announce(Player source,String message){
        UUID g=clan.apply(source.getUniqueId());if(g==null)return;
        for(Player target:Bukkit.getOnlinePlayers())if(!target.equals(source)&&g.equals(clan.apply(target.getUniqueId())))target.sendMessage(message);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player p)||e.getFinalDamage()<=0||p.getHealth()-e.getFinalDamage()>6||p.getHealth()-e.getFinalDamage()<=0)return;
        long now=System.currentTimeMillis();if(now-lastDamage.getOrDefault(p.getUniqueId(),0L)<15000)return;
        lastDamage.put(p.getUniqueId(),now);announce(p,"§c"+p.getName()+" получил урон: мало здоровья!");
    }
    @EventHandler public void death(PlayerDeathEvent e){
        Player p=e.getEntity();Location l=p.getLocation();
        NamespacedKey key=new NamespacedKey("landminimapbridge","death_history");
        String old=p.getPersistentDataContainer().getOrDefault(key,PersistentDataType.STRING,"");
        String row=l.getWorld().getUID()+","+l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ()+","+System.currentTimeMillis();
        List<String> lines=new ArrayList<>();lines.add(row);for(String s:old.split("\\n"))if(!s.isBlank()&&lines.size()<5)lines.add(s);
        p.getPersistentDataContainer().set(key,PersistentDataType.STRING,String.join("\n",lines));
        announce(p,"§c"+p.getName()+" погиб.");
    }
    @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();views.remove(id);lastPing.remove(id);lastDamage.remove(id);voice.forget(id);}
    void tick(){
        ticks++;long now=System.currentTimeMillis();
        pings.removeIf(p->p.expires<now);
        for(UUID id:new ArrayList<>(data.keySet()))if(!data.get(id).clan.equals(clan.apply(id)))remove(id);
        for(Player p:Bukkit.getOnlinePlayers()){
            Contribution c=data.get(p.getUniqueId());
            if(c==null||p.isDead()||p.getGameMode()==GameMode.SPECTATOR)continue;
            Location l=p.getLocation();var floor=l.clone().subtract(0,.15,0).getBlock();
            if(floor.getType().isAir())continue;
            Cell key=new Cell(p.getWorld().getUID(),layer(p),Math.floorDiv(l.getBlockX(),4),Math.floorDiv(l.getBlockZ(),4));
            int col=color(floor.getType());Integer old=c.cells.get(key);
            if(old==null){
                if(c.cells.size()>=MAX_CELLS||total>=MAX_TOTAL)continue;
                total++;
            }
            if(!Objects.equals(old,col)){c.cells.put(key,col);dirty=true;}
        }
        if(ticks%4==0)for(Player p:Bukkit.getOnlinePlayers())if(p.getListeningPluginChannels().contains(CHANNEL))send(p,now);
        if(ticks%60==0&&dirty)save();
    }
    private void send(Player p,long now){
        UUID id=p.getUniqueId(),g=clan.apply(id),world=p.getWorld().getUID();
        View v=views.get(id);if(v==null||!v.world.equals(world))v=new View(world,layer(p),Math.floorDiv(p.getLocation().getBlockX(),4),Math.floorDiv(p.getLocation().getBlockZ(),4),40);
        final View area=v;
        boolean enabled=g!=null&&data.containsKey(id)&&data.get(id).clan.equals(g);
        Map<Cell,Integer> cells=new HashMap<>();
        if(enabled)for(var entry:data.entrySet())if(!entry.getKey().equals(id)&&entry.getValue().clan.equals(g)&&g.equals(clan.apply(entry.getKey())))
            for(var cell:entry.getValue().cells.entrySet()){
                Cell k=cell.getKey();
                if(k.world.equals(world)&&k.layer==area.layer&&Math.abs(k.x-area.x)<=area.radius&&Math.abs(k.z-area.z)<=area.radius)cells.putIfAbsent(k,cell.getValue());
            }
        List<Map.Entry<Cell,Integer>> visible=cells.entrySet().stream().sorted(Comparator.comparingLong(e->{long dx=e.getKey().x-area.x,dz=e.getKey().z-area.z;return dx*dx+dz*dz;})).limit(2000).toList();
        List<Player> team=g==null?List.of():Bukkit.getOnlinePlayers().stream().filter(t->!t.equals(p)&&g.equals(clan.apply(t.getUniqueId()))).limit(16).toList();
        List<Ping> markers=g==null?List.of():pings.stream().filter(x->x.clan.equals(g)&&x.world.equals(world)).limit(16).toList();
        try{
            ByteArrayOutputStream b=new ByteArrayOutputStream();
            try(DataOutputStream out=new DataOutputStream(b)){
                out.writeInt(1);uuid(out,id);uuid(out,world);out.writeBoolean(enabled);out.writeInt(layer(p));out.writeInt(area.layer);out.writeInt(area.x);out.writeInt(area.z);out.writeInt(area.radius);out.writeBoolean(cells.size()>visible.size());
                out.writeShort(visible.size());for(var e:visible){out.writeInt(e.getKey().x);out.writeInt(e.getKey().z);out.writeInt(e.getValue());}
                out.writeByte(team.size());
                for(Player t:team){
                    uuid(out,t.getUniqueId());str(out,t.getName());out.writeFloat((float)t.getHealth());
                    var max=t.getAttribute(Attribute.MAX_HEALTH);out.writeFloat(max==null?20:(float)max.getValue());
                    var armor=t.getAttribute(Attribute.ARMOR);out.writeByte(armor==null?0:Math.min(127,(int)armor.getValue()));out.writeByte(t.getFoodLevel());
                    out.writeBoolean(voice.speaking(t.getUniqueId()));out.writeBoolean(t.isDead());out.writeBoolean(t.getWorld().equals(p.getWorld()));
                    String race=t.getPersistentDataContainer().getOrDefault(new NamespacedKey("landminimapbridge","race"),PersistentDataType.STRING,"");str(out,race);
                }
                out.writeByte(markers.size());for(Ping m:markers){out.writeInt(m.x);out.writeInt(m.y);out.writeInt(m.z);str(out,m.label);}
            }
            p.sendPluginMessage(plugin,CHANNEL,b.toByteArray());
        }catch(IOException|RuntimeException e){plugin.getLogger().warning("Clan features snapshot failed: "+e.getClass().getSimpleName());}
    }
    private Path file(){return plugin.getDataFolder().toPath().resolve("exploration.bin");}
    private void load(){
        if(!Files.isRegularFile(file()))return;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(Files.newInputStream(file())))){
            if(in.readInt()!=0x4c4d0401)throw new IOException("format");int n=in.readInt();if(n<0||n>128)throw new IOException("players");
            for(int i=0;i<n;i++){
                UUID id=uuid(in),g=uuid(in);int count=in.readInt();if(count<0||count>MAX_CELLS||total+count>MAX_TOTAL)throw new IOException("cells");
                LinkedHashMap<Cell,Integer> cells=new LinkedHashMap<>();
                for(int j=0;j<count;j++){Cell k=new Cell(uuid(in),in.readInt(),in.readInt(),in.readInt());cells.put(k,in.readInt());}
                data.put(id,new Contribution(g,cells));total+=cells.size();
            }
        }catch(IOException|RuntimeException e){data.clear();total=0;plugin.getLogger().warning("Exploration data invalid; sharing disabled until opt-in.");}
    }
    void save(){
        if(!dirty)return;
        try{
            Files.createDirectories(file().getParent());Path tmp=file().resolveSibling("exploration.tmp");
            try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))){
                out.writeInt(0x4c4d0401);out.writeInt(data.size());
                for(var e:data.entrySet()){uuid(out,e.getKey());uuid(out,e.getValue().clan);out.writeInt(e.getValue().cells.size());for(var c:e.getValue().cells.entrySet()){uuid(out,c.getKey().world);out.writeInt(c.getKey().layer);out.writeInt(c.getKey().x);out.writeInt(c.getKey().z);out.writeInt(c.getValue());}}
            }
            try{Files.move(tmp,file(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file(),StandardCopyOption.REPLACE_EXISTING);}
            dirty=false;
        }catch(IOException e){plugin.getLogger().warning("Could not persist shared exploration");}
    }
}
