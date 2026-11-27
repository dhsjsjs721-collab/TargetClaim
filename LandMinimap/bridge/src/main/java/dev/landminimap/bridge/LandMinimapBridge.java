package dev.landminimap.bridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class LandMinimapBridge extends JavaPlugin implements Listener {
    public static final String CHANNEL="landminimap:team", ACTION="landminimap:action";
    private Plugin clans;
    private Method areClanmates,claimModule,clanId;
    private MapOverlays overlays;
    private ClanFeatures features;
    private int overlayTick;
    private record Marker(UUID id,UUID owner,UUID clan,UUID world,int x,int y,int z,String label){}
    private record Access(UUID owner,UUID viewer){}
    private record Permit(UUID clan,long expires){}
    private final Map<UUID,Marker> markers=new LinkedHashMap<>();
    private final Map<Access,Permit> permissions=new HashMap<>(),requests=new HashMap<>();
    private final Map<UUID,Long> rate=new HashMap<>();
    private final Map<UUID,Snapshot> snapshots=new HashMap<>();
    private static final class Snapshot implements InventoryHolder {
        final UUID viewer,owner;
        Inventory inventory;
        Snapshot(UUID viewer,UUID owner){this.viewer=viewer;this.owner=owner;}
        @Override public Inventory getInventory(){return inventory;}
    }
    @Override public void onEnable(){
        clans=Bukkit.getPluginManager().getPlugin("MinecraftUnified");
        try{
            if(clans==null||!clans.isEnabled())throw new IllegalStateException("MinecraftUnified missing");
            areClanmates=clans.getClass().getMethod("areClanmates",UUID.class,UUID.class);
            claimModule=clans.getClass().getMethod("claimModule");
            Object claims=claimModule.invoke(clans);
            if(claims==null)throw new IllegalStateException("TargetClaim module unavailable");
            clanId=claims.getClass().getMethod("clanId",UUID.class);
        }catch(ReflectiveOperationException|RuntimeException error){
            getLogger().severe("Cannot verify clan membership; no positions will be sent: "+error);
            Bukkit.getPluginManager().disablePlugin(this);return;
        }
        loadMarkers();
        Bukkit.getPluginManager().registerEvents(this,this);
        getServer().getMessenger().registerOutgoingPluginChannel(this,CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this,MapOverlays.CHANNEL);
        overlays=new MapOverlays((JavaPlugin)clans,this,claimModule,this::clan);
        features=new ClanFeatures(this,this::clan);
        getServer().getMessenger().registerIncomingPluginChannel(this,ACTION,(channel,player,bytes)->{
            if(bytes.length>128||bytes.length<26)return;
            byte[] copy=bytes.clone();
            if(Bukkit.isPrimaryThread())handle(player,copy);
            else Bukkit.getScheduler().runTask(this,()->{if(player.isOnline())handle(player,copy);});
        });
        Bukkit.getScheduler().runTaskTimer(this,this::broadcast,20L,10L);
    }
    private UUID clan(UUID id){
        try{
            if(clans==null||!clans.isEnabled())return null;
            return (UUID)clanId.invoke(claimModule.invoke(clans),id);
        }catch(ReflectiveOperationException|RuntimeException e){return null;}
    }
    private boolean teammate(Player a,Player b){
        if(a==null||b==null||a==b||!a.isOnline()||!b.isOnline()||a.isDead()||b.isDead()||!clans.isEnabled())return false;
        try{return Boolean.TRUE.equals(areClanmates.invoke(clans,a.getUniqueId(),b.getUniqueId()));}
        catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void label(DataOutputStream out,String s)throws IOException{byte[] bytes=s.getBytes(StandardCharsets.UTF_8);out.writeByte(bytes.length);out.write(bytes);}
    private static String clean(String s){
        s=s.strip();
        if(s.isEmpty()||s.length()>24||s.getBytes(StandardCharsets.UTF_8).length>96||s.codePoints().anyMatch(c->Character.isISOControl(c)||c==167))return null;
        return s;
    }
    private void msg(Player p,String s){p.sendMessage("§bLandMap §7» §f"+s);}
    private void handle(Player p,byte[] data){
        if(!p.isOnline()||p.isDead()||!clans.isEnabled()||!p.getListeningPluginChannels().contains(CHANNEL))return;
        long now=System.currentTimeMillis();Long last=rate.put(p.getUniqueId(),now);
        if(last!=null&&now-last<250)return;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(data))){
            int op=in.readUnsignedByte();UUID id=uuid(in);int x=in.readInt(),z=in.readInt(),n=in.readUnsignedByte();
            if(n>96||in.available()!=n)return;
            String name=new String(in.readNBytes(n),StandardCharsets.UTF_8);
            if(features.action(p,op,x,z,name))return;
            UUID ownClan=clan(p.getUniqueId());if(ownClan==null){msg(p,"Сначала вступи в клан.");return;}
            if(op==1||op==10){
                int y=op==10?(int)id.getLeastSignificantBits():p.getLocation().getBlockY();
                if(y<p.getWorld().getMinHeight()||y>=p.getWorld().getMaxHeight()){msg(p,"Недопустимая высота.");return;}
                name=clean(name);
                if(name==null||Math.abs((long)x)>30000000||Math.abs((long)z)>30000000||!p.getWorld().getWorldBorder().isInside(new Location(p.getWorld(),x+.5,p.getY(),z+.5))){msg(p,"Недопустимая метка.");return;}
                if(markers.size()>=4096||markers.values().stream().filter(m->m.clan.equals(ownClan)).count()>=32){msg(p,"Лимит командных меток: 32.");return;}
                Marker m=new Marker(UUID.randomUUID(),p.getUniqueId(),ownClan,p.getWorld().getUID(),x,y,z,name);
                markers.put(m.id,m);if(!saveMarkers()){markers.remove(m.id);msg(p,"Не удалось сохранить метку.");return;}
                msg(p,"Командная метка создана.");return;
            }
            if(op==2){
                Marker m=markers.get(id);
                if(m!=null&&m.owner.equals(p.getUniqueId())&&m.clan.equals(ownClan)&&m.world.equals(p.getWorld().getUID())){
                    markers.remove(id);if(!saveMarkers()){markers.put(id,m);msg(p,"Не удалось удалить метку.");return;}msg(p,"Метка удалена.");
                }return;
            }
            Player target=Bukkit.getPlayer(id);
            Access granted=new Access(p.getUniqueId(),id);
            if(op==5||op==6){
                permissions.remove(granted);requests.remove(granted);closeAccess(granted);msg(p,"Доступ к инвентарю отозван.");
                if(target!=null)msg(target,"Доступ к инвентарю "+p.getName()+" отозван.");return;
            }
            if(!teammate(p,target)||!ownClan.equals(clan(id))){msg(p,"Союзник недоступен.");return;}
            if(op==4){
                Permit request=requests.remove(granted);
                if(request==null||request.expires<now||!ownClan.equals(request.clan)){msg(p,"Нет активного запроса от "+target.getName()+".");return;}
                permissions.put(granted,new Permit(ownClan,now+600000));
                msg(p,"Разрешён просмотр на 10 минут: "+target.getName());
                msg(target,p.getName()+" разрешил просмотр инвентаря. Открой карту и нажми «Инвентарь».");return;
            }
            if(op==3){
                Access access=new Access(id,p.getUniqueId());
                if(allowed(access,now)){openSnapshot(p,target);return;}
                Permit pending=requests.get(access);
                if(pending!=null&&pending.expires>now){msg(p,"Запрос уже отправлен.");return;}
                requests.put(access,new Permit(ownClan,now+60000));
                msg(p,"Запрос отправлен на 60 секунд.");
                msg(target,p.getName()+" хочет посмотреть твой инвентарь. Открой карту (M), выбери его и нажми «Разрешить» или «Отозвать».");
            }
        }catch(IOException|RuntimeException e){getLogger().warning("Invalid map action: "+e.getClass().getSimpleName());}
    }
    private boolean allowed(Access a,long now){
        Permit permit=permissions.get(a);Player owner=Bukkit.getPlayer(a.owner),viewer=Bukkit.getPlayer(a.viewer);
        return permit!=null&&permit.expires>now&&teammate(viewer,owner)&&permit.clan.equals(clan(a.owner))&&permit.clan.equals(clan(a.viewer));
    }
    private void closeAccess(Access a){
        Snapshot s=snapshots.get(a.viewer);
        if(s!=null&&s.owner.equals(a.owner)){Player p=Bukkit.getPlayer(a.viewer);snapshots.remove(a.viewer);if(p!=null&&p.getOpenInventory().getTopInventory().getHolder()==s)p.closeInventory();}
    }
    private void openSnapshot(Player viewer,Player owner){
        Snapshot s=new Snapshot(viewer.getUniqueId(),owner.getUniqueId());
        s.inventory=Bukkit.createInventory(s,54,"Инвентарь: "+owner.getName()+" (просмотр)");
        fill(s,owner);viewer.openInventory(s.inventory);
        if(viewer.getOpenInventory().getTopInventory().getHolder()==s)snapshots.put(viewer.getUniqueId(),s);
    }
    private void fill(Snapshot s,Player owner){
        ItemStack[] storage=owner.getInventory().getStorageContents();
        for(int i=0;i<36;i++)s.inventory.setItem(i,i<storage.length&&storage[i]!=null?storage[i].clone():null);
        ItemStack[] armor=owner.getInventory().getArmorContents();
        for(int i=0;i<4;i++)s.inventory.setItem(36+i,i<armor.length&&armor[i]!=null?armor[i].clone():null);
        ItemStack off=owner.getInventory().getItemInOffHand();s.inventory.setItem(40,off.clone());
        ItemStack note=new ItemStack(Material.PAPER);ItemMeta meta=note.getItemMeta();meta.setDisplayName("§eТолько просмотр · 36–39 броня · 40 вторая рука");note.setItemMeta(meta);s.inventory.setItem(49,note);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){if(e.getView().getTopInventory().getHolder() instanceof Snapshot)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Snapshot)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR) public void close(InventoryCloseEvent e){if(e.getInventory().getHolder() instanceof Snapshot s)snapshots.remove(s.viewer,s);}
    @EventHandler public void quit(PlayerQuitEvent e){
        UUID id=e.getPlayer().getUniqueId();rate.remove(id);
        for(Access a:new ArrayList<>(permissions.keySet()))if(a.owner.equals(id)||a.viewer.equals(id)){permissions.remove(a);closeAccess(a);}
        requests.keySet().removeIf(a->a.owner.equals(id)||a.viewer.equals(id));snapshots.remove(id);
    }
    private void broadcast(){
        boolean sendOverlays=++overlayTick%4==0;
        if(features!=null)features.tick();
        long now=System.currentTimeMillis();
        requests.entrySet().removeIf(e->e.getValue().expires<now||!e.getValue().clan.equals(clan(e.getKey().owner))||!e.getValue().clan.equals(clan(e.getKey().viewer)));
        for(Access a:new ArrayList<>(permissions.keySet()))if(!allowed(a,now)){permissions.remove(a);closeAccess(a);}
        for(Snapshot s:new ArrayList<>(snapshots.values())){
            Player viewer=Bukkit.getPlayer(s.viewer),owner=Bukkit.getPlayer(s.owner);
            if(viewer==null||viewer.getOpenInventory().getTopInventory().getHolder()!=s){snapshots.remove(s.viewer);continue;}
            if(!allowed(new Access(s.owner,s.viewer),now)){closeAccess(new Access(s.owner,s.viewer));continue;}
            fill(s,owner);
        }
        if(!clans.isEnabled())return;
        for(Player viewer:Bukkit.getOnlinePlayers()){
            if(!viewer.getListeningPluginChannels().contains(CHANNEL))continue;
            UUID group=clan(viewer.getUniqueId()),world=viewer.getWorld().getUID();
            List<Player> team=viewer.getWorld().getPlayers().stream().filter(t->teammate(viewer,t)).limit(16).toList();
            List<Marker> visible=group==null?List.of():markers.values().stream().filter(m->m.clan.equals(group)&&m.world.equals(world)).limit(32).toList();
            try{
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(DataOutputStream out=new DataOutputStream(bytes)){
                    out.writeInt(viewer.getListeningPluginChannels().contains(ClanFeatures.CHANNEL)?3:2);uuid(out,viewer.getUniqueId());uuid(out,world);out.writeByte(team.size());
                    for(Player target:team){uuid(out,target.getUniqueId());label(out,target.getName());Location pos=target.getLocation();out.writeDouble(pos.getX());out.writeDouble(pos.getY());out.writeDouble(pos.getZ());}
                    out.writeByte(visible.size());
                    for(Marker m:visible){uuid(out,m.id);uuid(out,m.owner);out.writeInt(m.x);out.writeInt(m.z);if(viewer.getListeningPluginChannels().contains(ClanFeatures.CHANNEL))out.writeInt(m.y);label(out,m.label);}
                }
                viewer.sendPluginMessage(this,CHANNEL,bytes.toByteArray());
                if(sendOverlays)overlays.send(viewer);
            }catch(IOException|RuntimeException e){getLogger().warning("Team update failed: "+e.getClass().getSimpleName());}
        }
    }
    private void loadMarkers(){
        var y=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"markers.yml"));
        var root=y.getConfigurationSection("markers");if(root==null)return;
        for(String k:root.getKeys(false)){
            if(markers.size()>=4096)break;
            try{
                var q=root.getConfigurationSection(k);if(q==null)continue;
                String name=clean(q.getString("label",""));if(name==null)continue;
                Marker m=new Marker(UUID.fromString(k),UUID.fromString(q.getString("owner")),UUID.fromString(q.getString("clan")),UUID.fromString(q.getString("world")),q.getInt("x"),q.getInt("y",64),q.getInt("z"),name);
                if(Math.abs((long)m.x)<=30000000&&Math.abs((long)m.z)<=30000000)markers.put(m.id,m);
            }catch(RuntimeException ignored){}
        }
    }
    private boolean saveMarkers(){
        try{
            Files.createDirectories(getDataFolder().toPath());var y=new YamlConfiguration();
            for(Marker m:markers.values()){String k="markers."+m.id+".";y.set(k+"owner",m.owner.toString());y.set(k+"clan",m.clan.toString());y.set(k+"world",m.world.toString());y.set(k+"x",m.x);y.set(k+"y",m.y);y.set(k+"z",m.z);y.set(k+"label",m.label);}
            Path file=getDataFolder().toPath().resolve("markers.yml"),tmp=getDataFolder().toPath().resolve("markers.tmp");
            y.save(tmp.toFile());try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}return true;
        }catch(IOException e){getLogger().warning("Could not save markers");return false;}
    }
    @Override public void onDisable(){
        for(Snapshot s:new ArrayList<>(snapshots.values()))closeAccess(new Access(s.owner,s.viewer));
        if(features!=null)features.save();permissions.clear();requests.clear();getServer().getMessenger().unregisterIncomingPluginChannel(this);getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }
}
