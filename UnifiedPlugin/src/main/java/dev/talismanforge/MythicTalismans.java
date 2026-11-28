package dev.talismanforge;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Persistent independently registered mythic artifacts. All world access is on the server thread. */
final class MythicTalismans implements Listener {
    enum Kind {
        THOR("Тор · Громовержец","Гнев Асгарда",Material.NETHER_STAR,0x63C8FF,3),
        ANUBIS("Анубис · Судья мёртвых","Владыка Дуата",Material.ECHO_SHARD,0xE9B650,500),
        KOSCHEI("Кощей · Смерть вне тела","Вечная жизнь",Material.SNIFFER_EGG,0x8AD46B,3),
        KITSUNE("Кицунэ · Лисица иллюзий","Девять хвостов",Material.RABBIT_FOOT,0xFF9460,3),
        MEDUSA("Медуза · Каменный взгляд","Проклятие Горгоны",Material.PRISMARINE_SHARD,0xB8A4F0,3);
        final String title,awakening; final Material icon; final int color,initial;
        Kind(String title,String awakening,Material icon,int color,int initial){this.title=title;this.awakening=awakening;this.icon=icon;this.color=color;this.initial=initial;}
        String key(){return name().toLowerCase(Locale.ROOT);}
        static Kind parse(String s){try{return valueOf(s.toUpperCase(Locale.ROOT));}catch(Exception ex){return null;}}
    }
    private static final class State {
        final Kind kind; State(Kind kind){this.kind=kind;} String serial; UUID display; int charges,progress,eggHp=3; boolean awakened,broken,linkIssued,retired;
        UUID owner,eggWorld; Location egg,store; UUID enderOwner,ritualResult; String ritualOldSerial; long storedAt,lastEggHit,gazeReady;
    }
    private record Mark(Location loc,long time,String name){}
    private record Minion(UUID owner,State state,long until){}
    private record Gaze(UUID victim,long since){}
    private record Credit(UUID owner,State state,long until){}
    private record Clone(UUID owner,State state,long until,Vector direction){}
    private final TalismanForgePlugin plugin;
    private final File file;
    private final NamespacedKey typeKey,serialKey,healthKey,rainKey,naturalKey,frozenKey,aiKey,despawnKey,gravityKey,summonKey;
    private final Map<String,State> states=new LinkedHashMap<>();
    private final Map<UUID,State> boundEggs=new HashMap<>();
    private final Map<UUID,Long> combat=new HashMap<>(),sneak=new HashMap<>(),revealUntil=new HashMap<>(),stonePlayers=new HashMap<>(),stoneImmune=new HashMap<>(),storm=new HashMap<>();
    private final Map<UUID,Gaze> gazes=new HashMap<>();
    private final Map<UUID,Credit> credits=new HashMap<>();
    private final Map<UUID,Minion> minions=new HashMap<>();
    private final Map<UUID,Long> frozenMobs=new HashMap<>();
    private final NamespacedKey frozenUntil=key("mythic_stone_until");
    private final Map<UUID,Clone> clones=new HashMap<>();
    private final Map<UUID,UUID> markDisplays=new HashMap<>();
    private final Map<UUID,Mark> marks=new LinkedHashMap<>();
    private final Set<UUID> hidden=new HashSet<>(),flightGranted=new HashSet<>(),internalDamage=new HashSet<>();
    private final Map<UUID,State> kitsuneEquipped=new HashMap<>(),gazeInstance=new HashMap<>(),stormInstance=new HashMap<>();
    private final Random random=new Random();
    private record Drop(UUID owner,long until){}
    private record Ritual(State state,UUID owner,UUID relic,UUID egg,Location center,long start){Kind kind(){return state.kind;}}
    private final Map<UUID,Drop> ritualDrops=new HashMap<>();
    private final Map<State,Ritual> rituals=new HashMap<>();
    private final Map<State,List<UUID>> ritualDisplays=new HashMap<>();
    private final Set<UUID> spentEggs=new HashSet<>();
    private final Map<UUID,Credit> soulHits=new HashMap<>();
    private final Map<UUID,Long> soulFeedback=new HashMap<>(),soulVfx=new HashMap<>();
    private final Map<String,Long> generatedChunks=new HashMap<>();
    private final NamespacedKey ritualLock=key("ritual_lock"),fallGrace=key("thor_fall_grace"),flightFlag=key("thor_flight_granted");
    private static final long RITUAL_MS=6000L;
    private static final Set<String> NATURAL_REASONS=Set.of("NATURAL","CHUNK_GEN","PATROL","RAID","JOCKEY","MOUNT","TRAP");
    private long ticks;
    
    private static final long RECHARGE=30*60*1000L;
    private static final long MARK_LIFE=72*60*60*1000L;
    MythicTalismans(TalismanForgePlugin plugin){
        this.plugin=plugin;file=new File(plugin.getDataFolder(),"mythic-artifacts.yml");
        typeKey=key("mythic_kind");serialKey=key("mythic_serial");healthKey=key("koschei_health");rainKey=key("anubis_rain");
        naturalKey=key("mythic_natural");frozenKey=key("mythic_stone");aiKey=key("mythic_original_ai");despawnKey=key("mythic_original_despawn");gravityKey=key("mythic_original_gravity");summonKey=key("mythic_summon");
        
        load(); Bukkit.getPluginManager().registerEvents(this,plugin.host());for(Kind k:Kind.values())Bukkit.removeRecipe(new NamespacedKey("talismanforge","craft_"+k.key()));
        for(World w:Bukkit.getWorlds()){for(Item i:w.getEntitiesByClass(Item.class))recoverItem(i);for(LivingEntity l:w.getLivingEntities())if(!(l instanceof Player)&&isStone(l)){long until=l.getPersistentDataContainer().getOrDefault(frozenUntil,PersistentDataType.LONG,0L);if(until<=now())thaw(l);else frozenMobs.put(l.getUniqueId(),until);}}
        Bukkit.getScheduler().runTaskTimer(plugin.host(),this::tick,5,5);
    }
    private NamespacedKey key(String name){return new NamespacedKey("talismanforge",name);}
    private static long now(){return System.currentTimeMillis();}
    private static UUID id(String value){try{return UUID.fromString(value);}catch(Exception ex){return null;}}
    private void load(){
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
        var registry=y.getConfigurationSection("instances");
        if(registry==null&&file.isFile()&&y.isConfigurationSection("artifacts")){try{Path backup=file.toPath().resolveSibling("mythic-artifacts-pre-instances.yml");if(!Files.exists(backup))Files.copy(file.toPath(),backup);}catch(IOException ex){throw new IllegalStateException("Cannot back up old relic registry",ex);}}
        if(registry!=null){for(String serial:registry.getKeys(false)){String p="instances."+serial+".";Kind k=Kind.parse(y.getString(p+"kind",""));if(k!=null&&id(serial)!=null)loadState(y,p,k,serial);}}
        else for(Kind k:Kind.values()){String p="artifacts."+k.key()+".";String serial=y.getString(p+"serial");if(id(serial)!=null)loadState(y,p,k,serial);}
        for(String v:y.getStringList("spent-dragon-eggs")){UUID u=id(v);if(u!=null)spentEggs.add(u);}
        var section=y.getConfigurationSection("deaths");if(section!=null)for(String k:section.getKeys(false)){
            UUID uid=id(k);Location loc=y.getLocation("deaths."+k+".location");long time=y.getLong("deaths."+k+".time");
            if(uid!=null&&loc!=null&&now()-time<MARK_LIFE)marks.put(uid,new Mark(loc,time,y.getString("deaths."+k+".name","?")));
        }
    }
    private void loadState(YamlConfiguration y,String p,Kind k,String serial){
        State s=new State(k);
            s.serial=serial; s.charges=Math.max(0,y.getInt(p+"charges",k==Kind.ANUBIS?0:k.initial));
            s.awakened=y.getBoolean(p+"awakened");s.charges=Math.min(s.charges,cap(k,s));s.progress=y.getInt(p+"progress");
            s.owner=id(y.getString(p+"owner"));s.eggWorld=id(y.getString(p+"egg-world"));try{s.egg=y.getLocation(p+"egg");}catch(IllegalArgumentException ex){s.egg=null;}s.eggHp=y.getInt(p+"egg-health",3);s.broken=y.getBoolean(p+"broken");s.retired=y.getBoolean(p+"retired");s.linkIssued=y.getBoolean(p+"link-issued",false);s.lastEggHit=y.getLong(p+"egg-hit");
            if(k==Kind.KOSCHEI&&y.contains(p+"egg")){if(s.egg==null||!s.egg.isWorldLoaded()||s.eggWorld!=null&&!s.eggWorld.equals(s.egg.getWorld().getUID())){s.egg=null;s.broken=true;s.linkIssued=false;}else s.eggWorld=s.egg.getWorld().getUID();}
            s.ritualResult=id(y.getString(p+"ritual-result"));s.ritualOldSerial=y.getString(p+"ritual-old-serial");
            s.gazeReady=y.getLong(p+"gaze-ready");s.store=y.getLocation(p+"storage");s.enderOwner=id(y.getString(p+"ender-owner"));s.storedAt=y.getLong(p+"stored-at");
        states.put(serial,s);if(k==Kind.KOSCHEI&&s.owner!=null&&!s.retired)boundEggs.putIfAbsent(s.owner,s);
    }
    private boolean save(){
        YamlConfiguration y=new YamlConfiguration();
        for(State s:states.values()){
            String p="instances."+s.serial+".";y.set(p+"kind",s.kind.key());
            y.set(p+"serial",s.serial);y.set(p+"charges",s.charges);y.set(p+"awakened",s.awakened);y.set(p+"progress",s.progress);
            y.set(p+"owner",s.owner==null?null:s.owner.toString());y.set(p+"egg",s.egg);y.set(p+"egg-world",s.eggWorld==null?null:s.eggWorld.toString());y.set(p+"egg-health",s.eggHp);y.set(p+"broken",s.broken);y.set(p+"retired",s.retired);y.set(p+"link-issued",s.linkIssued);y.set(p+"egg-hit",s.lastEggHit);
            y.set(p+"ritual-result",s.ritualResult==null?null:s.ritualResult.toString());y.set(p+"ritual-old-serial",s.ritualOldSerial);
            y.set(p+"gaze-ready",s.gazeReady);y.set(p+"storage",s.store);y.set(p+"ender-owner",s.enderOwner==null?null:s.enderOwner.toString());y.set(p+"stored-at",s.storedAt);
        }
        y.set("spent-dragon-eggs",spentEggs.stream().map(UUID::toString).sorted().toList());
        for(var e:marks.entrySet()){String p="deaths."+e.getKey()+".";Mark m=e.getValue();y.set(p+"location",m.loc());y.set(p+"time",m.time());y.set(p+"name",m.name());}
        try{
            Path tmp=file.toPath().resolveSibling(file.getName()+".tmp");y.save(tmp.toFile());
            try{Files.move(tmp,file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(IOException ex){plugin.getLogger().severe("Cannot save mythic artifacts: "+ex);return false;}
    }
    private int cap(Kind k,State s){return k==Kind.KITSUNE&&s.awakened?9:k.initial;}
    private String chargeText(Kind k,State s){return k==Kind.KOSCHEI&&s.awakened?"∞":s.charges+"/"+cap(k,s)+(k==Kind.ANUBIS?" заряда":"");}
    private Component title(Kind k,String value){return Component.text(value,TextColor.color(k.color));}
    private void message(Player p,Kind k,String text){p.sendMessage(title(k,"✦ "+text));}
    private ItemStack item(State s){
        Kind k=s.kind;boolean link=k==Kind.KOSCHEI&&s.egg!=null;ItemStack i=new ItemStack(link?Material.ECHO_SHARD:k.icon);ItemMeta m=i.getItemMeta();
        m.displayName(title(k,k==Kind.KOSCHEI?(link?"Душа Кощея":s.awakened?"Пробуждённое яйцо Кощея":"Яйцо Кощея"):s.awakened?k.awakening:k.title));
        m.setMaxStackSize(1);m.setEnchantmentGlintOverride(s.awakened);
        m.getPersistentDataContainer().set(typeKey,PersistentDataType.STRING,k.key());
        m.getPersistentDataContainer().set(serialKey,PersistentDataType.STRING,s.serial);
        if(plugin.getConfig().getBoolean("mythic-custom-models",true))m.setItemModel(new NamespacedKey("talismanforge","mythic_"+k.key()+(link?"_soul":"")+(s.awakened?"_awakened":"")));
        List<String> lore=new ArrayList<>(List.of("§8МИФИЧЕСКАЯ РЕЛИКВИЯ", "§7"+(s.awakened?"Пробуждена":"Способна пробудиться"),"§7Заряды: §f"+chargeText(k,s)));
        switch(k){
            case THOR -> {lore.add("§bМолния · +13% некритического урона");lore.add("§7Естественная молния восстанавливает заряд");lore.add("§cВ Незере: Слабость I");}
            case ANUBIS -> {lore.add("§6Полученный опыт ×10 · 200 заряда за спасение");lore.add("§7Видит места гибели");lore.add("§cПод дождём: −15% скорости");}
            case KOSCHEI -> {lore.add(link?"§aСвязана с установленным яйцом. Держи в руке.":"§aУстанови вне любого привата, чтобы получить Душу Кощея");lore.add("§7Мембрана фантома восстанавливает заряд");lore.add("§c−1 сердце, −25% питания");}
            case KITSUNE -> {lore.add("§6Невидимость · 180с / Shift 30с");lore.add("§730 минут в хранилище за заряд");lore.add("§cВне маскировки: +10% входящего урона");}
            case MEDUSA -> {lore.add("§dВзгляд 3 сек. (1 сек. пробуждённая) · стан 15 сек.");lore.add("§720 убитых статуй за заряд · щит отражает взгляд");}
        }
        if(k!=Kind.KOSCHEI||link)lore.add("§7Активна только в руках (вторая в приоритете)");
        if(!s.awakened)lore.add(link?"§8Для пробуждения брось яйцо дракона рядом с установленным яйцом":"§8Брось реликвию и яйцо дракона рядом");
        m.setLore(lore);i.setItemMeta(m);return i;
    }
    private State state(ItemStack i){
        if(i==null||!i.hasItemMeta()||i.getAmount()!=1)return null;
        var p=i.getItemMeta().getPersistentDataContainer();String serial=p.get(serialKey,PersistentDataType.STRING),v=p.get(typeKey,PersistentDataType.STRING);
        State s=serial==null?null:states.get(serial);
        if(s==null||s.retired||!s.kind.key().equals(v))return null;
        if(s.kind==Kind.KOSCHEI){if(s.broken||s.retired)return null;if(s.egg!=null){if(i.getType()!=Material.ECHO_SHARD)return null;}else if(i.getType()!=s.kind.icon&&i.getType()!=Material.DRAGON_EGG)return null;}
        else if(i.getType()!=s.kind.icon)return null;
        return s;
    }
    private Kind kind(ItemStack i){State s=state(i);return s==null?null:s.kind;}
    private boolean tagged(ItemStack i){return i!=null&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(typeKey,PersistentDataType.STRING);}
    private boolean usable(Player p){return !((dev.unified.UnifiedPlugin)plugin.host()).seasonLocked(p)&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);}
    private boolean canRitual(Player p){return usable(p)||p.getGameMode()==GameMode.CREATIVE&&p.hasPermission("talismanforge.admin");}
    private boolean peace(Location l){return ((dev.unified.UnifiedPlugin)plugin.host()).isPeace(l);}
    private State heldState(Player p){return selectHeld(p.getUniqueId(),p.getInventory().getItemInOffHand(),p.getInventory().getItemInMainHand());}
    private State selectHeld(UUID owner,ItemStack off,ItemStack main){
        State s=state(off);if(validHeld(owner,s))return s;
        s=state(main);return validHeld(owner,s)?s:null;
    }
    private boolean validHeld(UUID owner,State s){return s!=null&&(s.kind!=Kind.KOSCHEI||!s.broken&&s.egg!=null&&owner.equals(s.owner)&&boundEggs.get(owner)==s);}
    private State activeState(Player p){
        if(!usable(p))return null;
        State s=heldState(p);
        if(s!=null){if(s.kind==Kind.KOSCHEI&&!physicalEgg(s))return null;for(Player other:Bukkit.getOnlinePlayers())if(other!=p&&usable(other)&&!other.isDead()&&other.getUniqueId().compareTo(p.getUniqueId())<0&&heldState(other)==s)return null;return s;}
        return null;
    }
    private Kind active(Player p){State s=activeState(p);return s==null?null:s.kind;}
    boolean hasActive(Player p){return active(p)!=null;}
    private void refreshItem(Player p,Kind k){
        State s=activeState(p);if(s==null||s.kind!=k)return;
        var inv=p.getInventory();ItemStack replacement=item(s);
        if(state(inv.getItemInOffHand())==s){if(!replacement.equals(inv.getItemInOffHand()))inv.setItemInOffHand(replacement);}
        else if(state(inv.getItemInMainHand())==s&&!replacement.equals(inv.getItemInMainHand()))inv.setItemInMainHand(replacement);
    }
    private void give(Player p,ItemStack i){for(ItemStack rest:p.getInventory().addItem(i).values())protect(p.getWorld().dropItemNaturally(p.getLocation(),rest));}
    String issueRandom(Player p){Kind[] kinds=Kind.values();Kind k=kinds[random.nextInt(kinds.length)];return issue(p,k)?k.title:null;}
    List<ItemStack> testRelics(){List<ItemStack> result=new ArrayList<>();for(Kind k:Kind.values())for(boolean awakened:new boolean[]{false,true}){State s=new State(k);s.serial=UUID.randomUUID().toString();s.awakened=awakened;s.charges=k==Kind.ANUBIS?500:cap(k,s);states.put(s.serial,s);if(save())result.add(item(s));else states.remove(s.serial);}return result;}
    boolean issue(Player p,Kind k){
        State s=new State(k);s.serial=UUID.randomUUID().toString();s.charges=k==Kind.ANUBIS?0:3;
        states.put(s.serial,s);if(!save()){states.remove(s.serial);return false;}
        give(p,item(s));burst(p.getLocation(),k,32);message(p,k,"Получена реликвия: "+k.title);return true;
    }
    private void changeSerial(State s,String serial){states.remove(s.serial);s.serial=serial;states.put(serial,s);}
    List<String> names(){return Arrays.stream(Kind.values()).map(Kind::key).toList();}
    boolean command(CommandSender sender,String[] args){
        if(args.length==0)return false;
        if(args[0].equalsIgnoreCase("selftest")){
            if(!sender.hasPermission("talismanforge.admin")){sender.sendMessage("§cНет прав.");return true;}
            registrySelfTest(sender);return true;
        }
        if(args[0].equalsIgnoreCase("list")){sender.sendMessage("§6Пять мифических реликвий — количество экземпляров не ограничено.");for(Kind kind:Kind.values())sender.sendMessage(title(kind,kind.key()+" · "+kind.title+" → "+kind.awakening));sender.sendMessage("§7Реликвии активны только в руках. Кощей — Душа Кощея в руке + установленное яйцо. На спавне нет боевых эффектов.");return true;}
        if(args[0].equalsIgnoreCase("vfx")){if(!(sender instanceof Player p)||!p.hasPermission("talismanforge.admin")){sender.sendMessage("§cТолько администратор в игре.");return true;}Kind kind=args.length>=2?Kind.parse(args[1]):null;if(kind==null){sender.sendMessage("§e/u vfx thor|anubis|koschei|kitsune|medusa");return true;}burst(p.getLocation(),kind,24);rescueVfx(p.getLocation().clone(),kind);return true;}
        if(args[0].equalsIgnoreCase("give")&&args.length>=3){
            Kind k=Kind.parse(args[2]);if(k==null)return false;
            if(!sender.hasPermission("talismanforge.admin")){sender.sendMessage("§cНет прав.");return true;}
            Player p=Bukkit.getPlayerExact(args[1]);if(p==null){sender.sendMessage("§cИгрок не найден.");return true;}
            sender.sendMessage(issue(p,k)?"§aРеликвия выдана.":"§cНе удалось сохранить реликвию.");return true;
        }
        if(!Set.of("awaken","restore","status","link").contains(args[0].toLowerCase(Locale.ROOT)))return false;
        if(!(sender instanceof Player p)||!usable(p)){sender.sendMessage("§cТолько игрок в режиме выживания.");return true;}
        Kind k=active(p);
        if(args[0].equalsIgnoreCase("status")){
            if(k==null)p.sendMessage("§7Нет активной мифической реликвии.");
            else message(p,k,k.title+" · "+chargeText(k,activeState(p)));
            return true;
        }
        if(args[0].equalsIgnoreCase("link")){
            State s=boundEggs.get(p.getUniqueId());if(s==null||s.egg==null||s.broken){message(p,Kind.KOSCHEI,"Нет действующего установленного яйца.");return true;}
            if(rituals.containsKey(s)){message(p,Kind.KOSCHEI,"Дождись завершения ритуала.");return true;}
            if(s.egg.getWorld()==null||!s.egg.getWorld().isChunkLoaded(s.egg.getBlockX()>>4,s.egg.getBlockZ()>>4)||!eggBlock(s.egg.getBlock())){message(p,Kind.KOSCHEI,"Подойди к своему установленному яйцу.");return true;}
            if(!closeTo(p.getLocation(),s.egg,6)){message(p,Kind.KOSCHEI,"Подойди к своему яйцу на 6 блоков.");return true;}
            issueLink(p,s,true);return true;
        }
        if(args[0].equalsIgnoreCase("restore")){
            State s=boundEggs.get(p.getUniqueId());
            if(s==null||!s.broken){p.sendMessage("§cНет разрушенного привязанного яйца.");return true;}
            if(!take(p,Material.PHANTOM_MEMBRANE,16)){p.sendMessage("§cТребуется 16 мембран фантома.");return true;}
            String previous=s.serial;int oldCharges=s.charges,oldHp=s.eggHp;changeSerial(s,UUID.randomUUID().toString());s.charges=3;s.eggHp=3;s.broken=false;boolean oldLink=s.linkIssued;s.linkIssued=false;
            if(!save()){changeSerial(s,previous);s.charges=oldCharges;s.eggHp=oldHp;s.broken=true;s.linkIssued=oldLink;give(p,new ItemStack(Material.PHANTOM_MEMBRANE,16));return true;}
            give(p,item(s));message(p,Kind.KOSCHEI,"Душа возвращена в яйцо.");return true;
        }
        p.sendMessage("§6✦ Пробуждение: выбрось одну реликвию и одно обычное яйцо дракона рядом на твёрдый блок. Стой рядом 6 секунд.");
        p.sendMessage("§7Для установленного яйца Кощея достаточно бросить яйцо дракона рядом, если ты владелец.");
        return true;
    }
    // Test item metadata and instance lookup on the actual running Paper API.
    // Temporary entries exist only during this synchronous command and are never saved.
    private void registrySelfTest(CommandSender sender){
        int passed=0;
        for(Kind k:Kind.values()){
            State a=new State(k),b=new State(k);a.serial=UUID.randomUUID().toString();b.serial=UUID.randomUUID().toString();
            a.charges=k==Kind.ANUBIS?75:1;b.charges=k==Kind.ANUBIS?125:3;
            states.put(a.serial,a);states.put(b.serial,b);
            try{
                ItemStack old=item(a),other=item(b);
                if(state(old)!=a||state(other)!=b||a==b||a.charges==b.charges)throw new IllegalStateException("independent serials");
                String prior=a.serial;changeSerial(a,UUID.randomUUID().toString());a.awakened=true;
                ItemStack awakened=item(a);
                if(state(old)!=null||state(awakened)!=a||state(other)!=b||b.awakened||a.charges==(b.charges)||prior.equals(a.serial))throw new IllegalStateException("awakening isolation");
                if(cap(k,a)!=(k==Kind.KITSUNE?9:k.initial)||cap(k,b)!=k.initial)throw new IllegalStateException("charge capacity");
                passed++;sender.sendMessage("SELFTEST "+k.key()+" OK");
            }catch(Exception e){sender.sendMessage("SELFTEST "+k.key()+" FAILED: "+e.getMessage());plugin.getLogger().warning("Relic registry self-test failed: "+k+" "+e);}
            finally{states.remove(a.serial);states.remove(b.serial);}
        }
        sender.sendMessage("SELFTEST relic registry "+passed+"/5");
        heldLinkSelfTest(sender);
        policySelfTest(sender);
    }
    private static int rescueCost(Kind k,boolean awakened){return k==Kind.ANUBIS?200:k==Kind.KOSCHEI&&awakened?0:1;}
    private static int armySize(int souls){return Math.min(10,Math.max(4,souls/50));}
    private static int storageCharges(int current,int max,long elapsed){return Math.min(max,current+(int)Math.min(9,Math.max(0,elapsed)/RECHARGE));}
    private static boolean naturalReason(String reason,boolean marked){return reason==null||reason.equals("DEFAULT")?marked:NATURAL_REASONS.contains(reason);}
    private void policySelfTest(CommandSender sender){
        try{
            for(Kind k:Kind.values())for(boolean awakened:new boolean[]{false,true}){
                int expected=k==Kind.ANUBIS?200:k==Kind.KOSCHEI&&awakened?0:1;
                if(rescueCost(k,awakened)!=expected)throw new IllegalStateException("rescue cost "+k);
            }
            if(armySize(200)!=4||armySize(300)!=6||armySize(400)!=8||armySize(500)!=10||armySize(10000)!=10)throw new IllegalStateException("army scaling");
            for(String reason:List.of("SPAWNER","SPAWNER_EGG","DISPENSE_EGG","BREEDING","CUSTOM","COMMAND"))if(naturalReason(reason,true))throw new IllegalStateException("forbidden soul origin "+reason);
            if(!naturalReason("NATURAL",false)||!naturalReason("CHUNK_GEN",false)||naturalReason("DEFAULT",false)||!naturalReason("DEFAULT",true))throw new IllegalStateException("natural spawn classification");
            if(storageCharges(0,3,RECHARGE-1)!=0||storageCharges(0,3,RECHARGE)!=1||storageCharges(2,3,RECHARGE*9)!=3||storageCharges(0,9,RECHARGE*10)!=9||storageCharges(1,3,-1)!=1)throw new IllegalStateException("storage timing");
            if(anubisXp(0,5)!=50||anubisXp(490,30)!=500||anubisXp(50,-5)!=50||anubisXp(499,Integer.MAX_VALUE)!=500)throw new IllegalStateException("Anubis XP policy");
            if(medusaGazeMillis(false)!=3000||medusaGazeMillis(true)!=1000||medusaStunMillis()!=15000)throw new IllegalStateException("Medusa stun policy");
            sender.sendMessage("SELFTEST relic policies OK");
        }catch(Exception e){sender.sendMessage("SELFTEST relic policies FAILED: "+e.getMessage());}
    }
    private void heldLinkSelfTest(CommandSender sender){
        UUID owner=UUID.randomUUID();State s=new State(Kind.KOSCHEI);s.serial=UUID.randomUUID().toString();s.charges=3;s.owner=owner;states.put(s.serial,s);
        try{
            ItemStack egg=item(s);if(selectHeld(owner,egg,null)!=null)throw new IllegalStateException("unplaced egg active");
            s.egg=Bukkit.getWorlds().getFirst().getSpawnLocation();boundEggs.put(owner,s);ItemStack link=item(s);
            if(selectHeld(owner,null,null)!=null||selectHeld(owner,egg,null)!=null||selectHeld(owner,link,null)!=s||selectHeld(owner,null,link)!=s||selectHeld(UUID.randomUUID(),link,null)!=null)throw new IllegalStateException("wrong held link behavior");
            changeSerial(s,UUID.randomUUID().toString());ItemStack replacement=item(s);if(selectHeld(owner,link,null)!=null||selectHeld(owner,replacement,null)!=s)throw new IllegalStateException("old token valid");
            s.broken=true;s.egg=null;if(selectHeld(owner,replacement,null)!=null)throw new IllegalStateException("destroyed egg active");
            sender.sendMessage("SELFTEST koschei held-link OK");
        }catch(Exception e){sender.sendMessage("SELFTEST koschei held-link FAILED: "+e.getMessage());}
        finally{states.remove(s.serial);boundEggs.remove(owner);}
    }
    private boolean take(Player p,Material type,int count){
        int available=0;for(ItemStack i:p.getInventory().getStorageContents())if(i!=null&&i.getType()==type&&!tagged(i))available+=i.getAmount();
        if(available<count)return false;
        for(int slot=0;slot<36&&count>0;slot++){ItemStack i=p.getInventory().getItem(slot);if(i==null||i.getType()!=type||tagged(i))continue;int n=Math.min(count,i.getAmount());count-=n;i.setAmount(i.getAmount()-n);p.getInventory().setItem(slot,i.getAmount()==0?null:i);}
        return true;
    }
    private void burst(Location l,Kind k,int amount){
        World w=l.getWorld();if(w==null)return;Location c=l.clone().add(0,1,0);
        w.spawnParticle(Particle.DUST,c,amount,.7,.9,.7,0,new Particle.DustOptions(Color.fromRGB(k.color),1.4f));
        Particle effect=switch(k){case THOR->Particle.ELECTRIC_SPARK;case ANUBIS->Particle.SOUL;case KOSCHEI->Particle.SPORE_BLOSSOM_AIR;case KITSUNE->Particle.CLOUD;case MEDUSA->Particle.ENCHANT;};
        w.spawnParticle(effect,c,amount/2,.55,.65,.55,.04);
    }
    private void ring(Location l,Kind k,double radius){
        World w=l.getWorld();if(w==null)return;
        Particle.DustOptions dust=new Particle.DustOptions(Color.fromRGB(k.color),1.1f);
        for(int i=0;i<18;i++){double a=i*Math.PI/9+ticks*.08;w.spawnParticle(Particle.DUST,l.clone().add(Math.cos(a)*radius,.15,Math.sin(a)*radius),1,0,0,0,0,dust);}
    }
    private boolean locked(Item i){return rituals.values().stream().anyMatch(r->i.getUniqueId().equals(r.egg())||i.getUniqueId().equals(r.relic()));}
    private Item findItem(UUID uid){return uid!=null&&Bukkit.getEntity(uid) instanceof Item i&&i.isValid()&&!i.isDead()?i:null;}
    private boolean dragonEgg(Item i){return i!=null&&i.getItemStack().getType()==Material.DRAGON_EGG&&i.getItemStack().getAmount()==1&&!tagged(i.getItemStack());}
    private void lockItem(Item i){
        i.getPersistentDataContainer().set(ritualLock,PersistentDataType.BYTE,(byte)1);
        i.setPickupDelay(32767);i.setCanPlayerPickup(false);i.setCanMobPickup(false);i.setGravity(false);i.setVelocity(new Vector());i.setInvulnerable(true);i.setUnlimitedLifetime(true);
    }
    private void unlockItem(Item i){
        if(i==null)return;i.getPersistentDataContainer().remove(ritualLock);
        i.setPickupDelay(20);i.setCanPlayerPickup(true);i.setCanMobPickup(true);i.setGravity(true);
        boolean relic=kind(i.getItemStack())!=null;i.setInvulnerable(relic);i.setUnlimitedLifetime(relic);
    }
    private boolean repairResult(Item i){
        for(State st:states.values()){
            if(!i.getUniqueId().equals(st.ritualResult)||st.ritualOldSerial==null||!tagged(i.getItemStack()))continue;
            String serial=i.getItemStack().getItemMeta().getPersistentDataContainer().get(serialKey,PersistentDataType.STRING);
            if(st.ritualOldSerial.equals(serial)||Objects.equals(st.serial,serial)){i.setItemStack(item(st));return true;}
        }
        return false;
    }
    private void recoverItem(Item i){
        if(spentEggs.contains(i.getUniqueId())){i.remove();return;}
        repairResult(i);
        if(i.getPersistentDataContainer().has(ritualLock,PersistentDataType.BYTE)&&!locked(i))unlockItem(i);
        protect(i);
    }
    private void protect(Item i){if(kind(i.getItemStack())!=null){i.setInvulnerable(true);i.setUnlimitedLifetime(true);}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void droppedForRitual(PlayerDropItemEvent e){
        if(!canRitual(e.getPlayer()))return;Item i=e.getItemDrop();Kind k=kind(i.getItemStack());i.setThrower(e.getPlayer().getUniqueId());
        if(dragonEgg(i)||k!=null&&i.getItemStack().getAmount()==1&&!state(i.getItemStack()).awakened)ritualDrops.put(i.getUniqueId(),new Drop(e.getPlayer().getUniqueId(),now()+30000));
    }
    private boolean closeTo(Location a,Location b,double radius){return a!=null&&b!=null&&a.getWorld()!=null&&a.getWorld().equals(b.getWorld())&&a.distanceSquared(b)<=radius*radius;}
    private boolean safeRitual(Location l){
        if(l==null||l.getWorld()==null||l.getY()<l.getWorld().getMinHeight()+.2||!l.getWorld().isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4))return false;
        Block b=l.getBlock();if(b.isLiquid()||b.getType()==Material.FIRE||b.getType()==Material.SOUL_FIRE)return false;
        var hit=l.getWorld().rayTraceBlocks(l.clone().add(0,.35,0),new Vector(0,-1,0),.95,FluidCollisionMode.NEVER,true);
        return hit!=null&&hit.getHitBlock()!=null&&!hit.getHitBlock().isLiquid()&&!hit.getHitBlock().isPassable();
    }
    private boolean settled(Item i){return (i.isOnGround()||Math.abs(i.getVelocity().getY())<.12)&&safeRitual(i.getLocation());}
    private void detectRituals(){
        long time=now();
        for(Player p:Bukkit.getOnlinePlayers())if(canRitual(p))for(Entity e:p.getNearbyEntities(8,5,8))if(e instanceof Item i&&p.getUniqueId().equals(i.getThrower())&&!locked(i)&&!spentEggs.contains(i.getUniqueId())){State st=state(i.getItemStack());if(dragonEgg(i)||st!=null&&!st.awakened)ritualDrops.putIfAbsent(i.getUniqueId(),new Drop(p.getUniqueId(),time+120000));}
        ritualDrops.entrySet().removeIf(e->e.getValue().until()<time||findItem(e.getKey())==null);
        for(var entry:new ArrayList<>(ritualDrops.entrySet())){
            Item egg=findItem(entry.getKey());if(!dragonEgg(egg)||locked(egg)||spentEggs.contains(egg.getUniqueId())||!settled(egg))continue;
            UUID owner=entry.getValue().owner();Player p=Bukkit.getPlayer(owner);if(p==null||!canRitual(p)||!closeTo(p.getLocation(),egg.getLocation(),8))continue;
            boolean found=false;
            for(var candidate:new ArrayList<>(ritualDrops.entrySet())){
                if(!candidate.getValue().owner().equals(owner)||candidate.getKey().equals(egg.getUniqueId()))continue;
                Item relic=findItem(candidate.getKey());if(relic==null||locked(relic)||!settled(relic)||!closeTo(relic.getLocation(),egg.getLocation(),2.5))continue;
                State st=state(relic.getItemStack());if(st==null||st.awakened||rituals.containsKey(st))continue;Kind k=st.kind;if(k==Kind.KOSCHEI&&(st.egg!=null||st.broken||st.owner!=null&&!owner.equals(st.owner)))continue;
                startRitual(st,p,relic,egg,egg.getLocation().clone().add(relic.getLocation()).multiply(.5));found=true;break;
            }
            if(found)continue;
            State st=boundEggs.get(owner);
            if(st!=null&&!st.awakened&&!st.broken&&!rituals.containsKey(st)&&owner.equals(st.owner)&&st.egg!=null&&closeTo(st.egg,egg.getLocation(),2.5)&&eggBlock(st.egg.getBlock()))
                startRitual(st,p,null,egg,st.egg.clone().add(.5,.2,.5));
        }
    }
    private ItemDisplay ritualModel(Location l,ItemStack stack){
        ItemStack visual=stack.clone();visual.setAmount(1);
        // Display copies are non-pickup entities, not item drops; strip all relic identity tags.
        ItemMeta meta=visual.getItemMeta();if(meta!=null){meta.getPersistentDataContainer().remove(serialKey);meta.getPersistentDataContainer().remove(typeKey);visual.setItemMeta(meta);}
        return l.getWorld().spawn(l,ItemDisplay.class,d->{d.setItemStack(visual);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setBillboard(Display.Billboard.FIXED);d.setPersistent(false);d.setInvulnerable(true);d.setGravity(false);d.setTeleportDuration(5);d.getPersistentDataContainer().set(key("ritual_visual"),PersistentDataType.BYTE,(byte)1);});
    }
    private void clearRitualDisplays(State st){List<UUID> ids=ritualDisplays.remove(st);if(ids!=null)for(UUID id:ids){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}}
    private void startRitual(State st,Player p,Item relic,Item egg,Location center){
        Kind k=st.kind;Ritual r=new Ritual(st,p.getUniqueId(),relic==null?null:relic.getUniqueId(),egg.getUniqueId(),center.clone(),now());
        rituals.put(st,r);lockItem(egg);if(relic!=null)lockItem(relic);
        List<UUID> displays=new ArrayList<>();ritualDisplays.put(st,displays);
        displays.add(ritualModel(center.clone().add(0,.7,0),egg.getItemStack()).getUniqueId());
        if(relic!=null)displays.add(ritualModel(center.clone().add(0,.9,0),relic.getItemStack()).getUniqueId());
        ritualDrops.remove(egg.getUniqueId());if(relic!=null)ritualDrops.remove(relic.getUniqueId());
        p.showTitle(net.kyori.adventure.title.Title.title(title(k,"ПРОБУЖДЕНИЕ"),Component.text(k.title+" · 6 секунд")));
        p.getWorld().playSound(center,Sound.BLOCK_BEACON_ACTIVATE,1,.65f);
    }
    private void abortRitual(Ritual r,String why){
        rituals.remove(r.state());clearRitualDisplays(r.state());unlockItem(findItem(r.relic()));unlockItem(findItem(r.egg()));
        Player p=Bukkit.getPlayer(r.owner());if(p!=null)message(p,r.kind(),"Ритуал прерван: "+why+". Предметы возвращены на землю.");
    }
    private void finishRitual(Ritual r,Item relic,Item egg,Player p){
        State st=r.state();String old=st.serial;UUID previousResult=st.ritualResult;String previousOld=st.ritualOldSerial;
        st.awakened=true;if(relic!=null)changeSerial(st,UUID.randomUUID().toString());st.ritualResult=r.relic();st.ritualOldSerial=old;
        spentEggs.add(r.egg());
        // The same atomic registry write commits the awakening, new serial and spent egg UUID.
        // On a restart, the exact old item entity is repaired and a still-saved egg is removed.
        if(!save()){st.awakened=false;changeSerial(st,old);st.ritualResult=previousResult;st.ritualOldSerial=previousOld;spentEggs.remove(r.egg());abortRitual(r,"ошибка сохранения");return;}
        rituals.remove(r.state());clearRitualDisplays(r.state());egg.remove();
        if(relic!=null){relic.setItemStack(item(st));unlockItem(relic);}
        else if(st.egg!=null){renderEgg(st);refreshItem(p,Kind.KOSCHEI);}
        p.showTitle(net.kyori.adventure.title.Title.title(title(r.kind(),r.kind().awakening),Component.text("Реликвия пробуждена")));
        p.playSound(r.center(),Sound.BLOCK_END_PORTAL_SPAWN,1,1.1f);p.playSound(r.center(),Sound.ENTITY_PLAYER_LEVELUP,.8f,.8f);
        burst(r.center(),r.kind(),64);rescueVfx(r.center(),r.kind());
    }
    private void tickRituals(){
        for(Ritual r:new ArrayList<>(rituals.values())){
            Item egg=findItem(r.egg()),relic=findItem(r.relic());Player p=Bukkit.getPlayer(r.owner());State st=r.state();
            boolean valid=p!=null&&canRitual(p)&&closeTo(p.getLocation(),r.center(),8)&&!st.awakened&&dragonEgg(egg)&&closeTo(egg.getLocation(),r.center(),3);
            if(r.relic()!=null)valid=valid&&relic!=null&&state(relic.getItemStack())==st&&relic.getItemStack().getAmount()==1&&closeTo(relic.getLocation(),r.center(),3);
            else valid=valid&&st.egg!=null&&r.owner().equals(st.owner)&&!st.broken&&closeTo(st.egg,r.center(),2)&&eggBlock(st.egg.getBlock());
            if(!valid||!safeRitual(egg.getLocation())||relic!=null&&!safeRitual(relic.getLocation())){abortRitual(r,"условия больше не выполнены");continue;}
            long elapsed=now()-r.start();if(elapsed>=RITUAL_MS){finishRitual(r,relic,egg,p);continue;}
            double t=elapsed/(double)RITUAL_MS;World w=r.center().getWorld();Location c=r.center().clone();
            List<UUID> displays=ritualDisplays.getOrDefault(r.state(),List.of());for(int i=0;i<displays.size();i++){Entity display=Bukkit.getEntity(displays.get(i));if(display==null)continue;double a=t*6*Math.PI+i*Math.PI,orbit=.8*(1-t)+.15;Location q=c.clone().add(Math.cos(a)*orbit,.7+1.45*Math.sin(Math.PI*t),Math.sin(a)*orbit);q.setYaw((float)(t*1080+i*180));display.teleport(q);}
            double radius=1.6-.65*t;Particle.DustOptions dust=new Particle.DustOptions(Color.fromRGB(r.kind().color),1.15f);
            for(int j=0;j<24;j++){double a=j*Math.PI/12+t*8*Math.PI;w.spawnParticle(Particle.DUST,c.clone().add(Math.cos(a)*radius,.08,Math.sin(a)*radius),1,0,0,0,0,dust);}
            for(int j=0;j<12;j++){double a=j*Math.PI/6+t*12*Math.PI;w.spawnParticle(Particle.END_ROD,c.clone().add(Math.cos(a)*radius,.35+1.8*j/12.0,Math.sin(a)*radius),1,0,0,0,0);}
            w.spawnParticle(Particle.ENCHANT,c.clone().add(0,.8,0),12,.45,.7,.45,.04);
            if(ticks%20==0)w.playSound(c,Sound.BLOCK_AMETHYST_BLOCK_CHIME,.8f,(float)(.65+t*1.25));
            p.sendActionBar(title(r.kind(),"ПРОБУЖДЕНИЕ  "+(int)(t*100)+"%  ·  Не отходи"));
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void ritualPickup(EntityPickupItemEvent e){if(locked(e.getItem())||spentEggs.contains(e.getItem().getUniqueId()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void ritualHopper(InventoryPickupItemEvent e){if(locked(e.getItem())||spentEggs.contains(e.getItem().getUniqueId()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void ritualMerge(ItemMergeEvent e){if(locked(e.getEntity())||locked(e.getTarget())||tagged(e.getEntity().getItemStack())||tagged(e.getTarget().getItemStack())||ritualDrops.containsKey(e.getEntity().getUniqueId())||ritualDrops.containsKey(e.getTarget().getUniqueId()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void ritualTeleport(EntityTeleportEvent e){if(e.getEntity() instanceof Item i&&locked(i))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void ritualDamage(EntityDamageEvent e){if(e.getEntity() instanceof Item i&&locked(i))e.setCancelled(true);}
    private void dust(World w,Location l,int color,float size){w.spawnParticle(Particle.DUST,l,1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(color),size));}
    private void rescueVfx(Location l,Kind k){
        for(int step=0;step<6;step++){final int wave=step;Bukkit.getScheduler().runTaskLater(plugin.host(),()->{
            World w=l.getWorld();if(w==null||!w.isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4))return;
            double radius=.55+wave*.48;
            for(int j=0;j<24;j++){
                double a=j*Math.PI/12+wave*.25;double x=Math.cos(a),z=Math.sin(a);
                switch(k){
                    case THOR->{double y=.3+((j+wave)%4)*.3;dust(w,l.clone().add(x*radius,y,z*radius),j%3==0?0xF0FAFF:0x56BFFF,1.1f);if(j%4==0)w.spawnParticle(Particle.ELECTRIC_SPARK,l.clone().add(x*radius,y,z*radius),2,.08,.15,.08,.04);}
                    case ANUBIS->{double rr=radius*(j%2==0?1:.72);dust(w,l.clone().add(x*rr,.12,z*rr),j%2==0?0xF6C461:0x2EC9BF,1.15f);if(j%6==0)w.spawnParticle(Particle.SOUL,l.clone().add(x*radius,.3+wave*.2,z*radius),2,.08,.1,.08,.02);}
                    case KOSCHEI->{double rr=radius*(.72+.28*Math.cos(3*a));dust(w,l.clone().add(x*rr,.18+wave*.28,z*rr),j%2==0?0x81D856:0x345A25,1.1f);if(j%6==0)w.spawnParticle(Particle.SPORE_BLOSSOM_AIR,l.clone().add(x*rr,.5,z*rr),2,.06,.1,.06,0);}
                    case KITSUNE->{double rr=radius*(.64+.36*Math.cos(3*a));dust(w,l.clone().add(x*rr,.2+wave*.18,z*rr),j%2==0?0xFFB480:0xFBE0ED,.95f);if(j%6==0)w.spawnParticle(Particle.CLOUD,l.clone().add(x*rr,.4,z*rr),1,.06,.05,.06,.01);}
                    case MEDUSA->{double rr=radius*(j%2==0?1:.85);dust(w,l.clone().add(x*rr,.1+wave*.12,z*rr),j%2==0?0xBDA5F6:0x80947B,1.1f);if(j%6==0)w.spawnParticle(Particle.BLOCK,l.clone().add(x*rr,.4,z*rr),3,.1,.12,.1,0,Material.STONE.createBlockData());}
                }
            }
            if(wave==0)w.playSound(l,switch(k){case THOR->Sound.ENTITY_LIGHTNING_BOLT_THUNDER;case ANUBIS->Sound.BLOCK_SOUL_SAND_PLACE;case KOSCHEI->Sound.BLOCK_AMETHYST_BLOCK_CHIME;case KITSUNE->Sound.ENTITY_FOX_AMBIENT;case MEDUSA->Sound.BLOCK_STONE_BREAK;},.6f,1.05f);
        },step*3L);}
    }
    @EventHandler public void spawnItem(ItemSpawnEvent e){recoverItem(e.getEntity());}
    @EventHandler(priority=EventPriority.HIGHEST) public void despawn(ItemDespawnEvent e){if(kind(e.getEntity().getItemStack())!=null||locked(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void inventoryCraft(PrepareItemCraftEvent e){
        for(ItemStack i:e.getInventory().getMatrix())if(tagged(i)){e.getInventory().setResult(null);return;}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void spawnNatural(CreatureSpawnEvent e){
        if(NATURAL_REASONS.contains(e.getSpawnReason().name()))e.getEntity().getPersistentDataContainer().set(naturalKey,PersistentDataType.BYTE,(byte)1);
    }
    private String chunkKey(Chunk c){return c.getWorld().getUID()+":"+c.getX()+":"+c.getZ();}
    @EventHandler public void generatedChunk(org.bukkit.event.world.ChunkLoadEvent e){
        if(!e.isNewChunk())return;generatedChunks.put(chunkKey(e.getChunk()),now()+30000);
        for(Entity entity:e.getChunk().getEntities())markGenerated(entity);
    }
    private void markGenerated(Entity entity){
        if(!(entity instanceof Mob)||entity.getPersistentDataContainer().has(summonKey,PersistentDataType.STRING))return;
        var reason=entity.getEntitySpawnReason();
        if(reason==null||reason.name().equals("DEFAULT")||NATURAL_REASONS.contains(reason.name()))entity.getPersistentDataContainer().set(naturalKey,PersistentDataType.BYTE,(byte)1);
    }
    private boolean natural(LivingEntity e){
        if(e instanceof ArmorStand||e instanceof Mannequin||e.getPersistentDataContainer().has(summonKey,PersistentDataType.STRING))return false;
        var reason=e.getEntitySpawnReason();
        return naturalReason(reason==null?null:reason.name(),e.getPersistentDataContainer().has(naturalKey,PersistentDataType.BYTE));
    }
    private int soulValue(LivingEntity e){
        return switch(e.getType().name()){
            case "ENDER_DRAGON","WITHER" -> 150;
            case "WARDEN","ELDER_GUARDIAN","RAVAGER" -> 75;
            default -> e.getHeight()<1?10:e.getHeight()>2.3?40:25;
        };
    }
    private void soulTrail(Player p,Location from){
        long time=now();if(time-soulVfx.getOrDefault(p.getUniqueId(),0L)<300)return;soulVfx.put(p.getUniqueId(),time);
        UUID owner=p.getUniqueId();Location start=from.clone().add(0,.7,0);
        for(int step=1;step<=8;step++){final int n=step;Bukkit.getScheduler().runTaskLater(plugin.host(),()->{
            Player player=Bukkit.getPlayer(owner);if(player==null||!closeTo(start,player.getLocation(),40))return;
            Location target=player.getEyeLocation();Vector delta=target.toVector().subtract(start.toVector());
            Location point=start.clone().add(delta.multiply(n/8.0)).add(0,Math.sin(Math.PI*n/8.0)*.7,0);
            point.getWorld().spawnParticle(Particle.SOUL,point,3,.09,.09,.09,.01);
            point.getWorld().spawnParticle(Particle.DUST,point,2,.06,.06,.06,0,new Particle.DustOptions(Color.fromRGB(0xE9B650),1.1f));
        },step*2L);}
        p.playSound(p.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,.45f,1.35f);
    }
    private boolean eligible(LivingEntity e){return !(e instanceof ArmorStand)&&(!(e instanceof Mannequin)||e.getWorld().getName().equals("unified_test")&&e.getPersistentDataContainer().has(new NamespacedKey("minecraftunified","test_target"),PersistentDataType.STRING))&&!e.getPersistentDataContainer().has(new NamespacedKey("minecraftunified","shop_entity"))&&!Set.of("ENDER_DRAGON","WITHER","WARDEN","ELDER_GUARDIAN").contains(e.getType().name());}
    private Player attacker(Entity e){
        if(e instanceof Player p)return p;
        if(e instanceof Projectile q&&q.getShooter() instanceof Player p)return p;
        return null;
    }
    private boolean enemy(Player p,LivingEntity target){
        if(target==p||!eligible(target)||peace(p.getLocation())||peace(target.getLocation()))return false;
        if(target instanceof Player other){
            if(!usable(other))return false;
            var claim=((dev.unified.UnifiedPlugin)plugin.host()).claimModule();
            return claim!=null&&claim.allowsTalismanDamage(p,other);
        }
        Minion m=minions.get(target.getUniqueId());
        return m==null?!target.getPersistentDataContainer().has(summonKey,PersistentDataType.STRING):!m.owner().equals(p.getUniqueId());
    }
    private void charge(State s,int n,Player p){
        if(s==null)return;Kind k=s.kind;int old=s.charges;s.charges=Math.min(cap(k,s),s.charges+n);
        if(old==s.charges)return;
        if(!save()){s.charges=old;return;}
        if(p!=null){refreshItem(p,k);message(p,k,"Заряд восстановлен · "+chargeText(k,s));burst(p.getLocation(),k,10);}
    }
    private boolean mirrorReflection(EntityDamageEvent e){return ((dev.unified.UnifiedPlugin)plugin.host()).isMirrorReflection(e);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void attacks(EntityDamageByEntityEvent e){
        if(e.getDamager() instanceof LivingEntity l&&isStone(l)||e.getDamager() instanceof Projectile q&&q.getShooter() instanceof LivingEntity shooter&&isStone(shooter)){e.setCancelled(true);return;}
        if(mirrorReflection(e))return;
        if(clones.containsKey(e.getDamager().getUniqueId())){e.setCancelled(true);return;}
        Minion m=minions.get(e.getDamager().getUniqueId());
        if(m==null&&e.getDamager() instanceof Projectile q&&q.getShooter() instanceof LivingEntity shooter)m=minions.get(shooter.getUniqueId());
        if(m!=null&&e.getEntity() instanceof LivingEntity target){Player owner=Bukkit.getPlayer(m.owner());if(owner==null||activeState(owner)!=m.state()||!enemy(owner,target)){e.setCancelled(true);return;}if(e.getDamager() instanceof Mob mob&&maxHealth(mob)<=1)e.setDamage(1); }
        Player p=attacker(e.getDamager());if(p==null||!(e.getEntity() instanceof LivingEntity target))return;
        Kind k=active(p);
        if(k==Kind.THOR&&!peace(p.getLocation())&&!peace(target.getLocation())&&e.getDamager()==p&&!internalDamage.contains(p.getUniqueId())){
            if(!e.isCritical())e.setDamage(e.getDamage()*1.13);
        }
        if(k==Kind.KITSUNE&&!peace(p.getLocation())&&!peace(target.getLocation())&&hidden.contains(p.getUniqueId())&&activeState(p).awakened)e.setDamage(e.getDamage()*1.20);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void incomingPenalty(EntityDamageEvent e){
        if(!mirrorReflection(e)&&e.getEntity() instanceof Player p&&active(p)==Kind.KITSUNE&&!hidden.contains(p.getUniqueId())&&e.getFinalDamage()>0)e.setDamage(e.getDamage()*1.10);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent e){
        if(e.getEntity() instanceof Item i&&kind(i.getItemStack())!=null){e.setCancelled(true);return;}
        if(clones.containsKey(e.getEntity().getUniqueId())){e.setCancelled(true);return;}
        if(!(e.getEntity() instanceof LivingEntity target)||e.getFinalDamage()<=0)return;
        if(isStone(target)){
            if(e instanceof EntityDamageByEntityEvent by){Player p=attacker(by.getDamager());if(p!=null&&active(p)==Kind.MEDUSA&&!(target instanceof Player))credits.put(target.getUniqueId(),new Credit(p.getUniqueId(),activeState(p),now()+30000));}
        }
        if(!(target instanceof Player p))return;
        Kind k=active(p);
        if(e.getCause()==EntityDamageEvent.DamageCause.FALL&&(p.getPersistentDataContainer().has(fallGrace,PersistentDataType.BYTE)||k==Kind.THOR&&activeState(p).awakened&&raining(p))){
            e.setCancelled(true);p.setFallDistance(0);p.getPersistentDataContainer().remove(fallGrace);return;
        }
        if(k==Kind.THOR&&e.getCause()==EntityDamageEvent.DamageCause.LIGHTNING){
            e.setCancelled(true);if(activeState(p).awakened)heal(p,4);return;
        }
        if(k==Kind.KITSUNE){
            reveal(p);
        }else combat.put(p.getUniqueId(),now());
        if(k==null||e.getFinalDamage()+1e-6<p.getHealth())return;
        State s=activeState(p);int cost=rescueCost(k,s.awakened);
        if(s.charges<cost||k==Kind.KOSCHEI&&s.broken)return;
        Location destination=null;
        if(k==Kind.KOSCHEI){if(s.egg==null||claimed(s.egg)||!eggBlock(s.egg.getBlock()))return;destination=safeEgg(s.egg);if(destination==null)return;}
        if(e.getCause()==EntityDamageEvent.DamageCause.VOID&&destination==null)destination=p.getWorld().getSpawnLocation();
        if(e.getCause().name().equals("KILL"))return;
        int before=s.charges;s.charges-=cost;
        if(!save()){s.charges=before;return;}
        if(destination!=null&&!p.teleport(destination)){s.charges=before;save();return;}
        e.setCancelled(true);completeRescue(p,k,s,before);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void rescueDeath(PlayerDeathEvent e){
        Player p=e.getEntity();State s=activeState(p);if(s==null)return;Kind k=s.kind;
        var last=p.getLastDamageCause();
        if(last!=null&&last.getCause().name().equals("KILL"))return;
        int cost=rescueCost(k,s.awakened);
        if(s.charges<cost||k==Kind.KOSCHEI&&s.broken)return;
        Location destination=null;
        if(k==Kind.KOSCHEI){if(s.egg==null||claimed(s.egg)||!eggBlock(s.egg.getBlock()))return;destination=safeEgg(s.egg);if(destination==null)return;}
        else if(last!=null&&last.getCause()==EntityDamageEvent.DamageCause.VOID)destination=p.getWorld().getSpawnLocation();
        int before=s.charges;s.charges-=cost;if(!save()){s.charges=before;return;}
        e.setReviveHealth(Math.min(maxHealth(p),8));e.setCancelled(true);
        Location dest=destination;
        Bukkit.getScheduler().runTask(plugin.host(),()->{
            if(!p.isOnline()||p.isDead())return;
            if(dest!=null&&!p.teleport(dest)){p.teleport(p.getWorld().getSpawnLocation());}
            completeRescue(p,k,s,before);
        });
    }
    private void completeRescue(Player p,Kind k,State s,int before){
        p.setFireTicks(0);p.setFallDistance(0);p.setHealth(Math.min(maxHealth(p),8));p.setNoDamageTicks(40);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,100,1));
        p.showTitle(net.kyori.adventure.title.Title.title(title(k,k.title),Component.text("Возвращение к жизни")));
        p.playSound(p.getLocation(),Sound.ITEM_TOTEM_USE,0.85f,0.9f);
        burst(p.getLocation(),k,70);ring(p.getLocation(),k,2);rescueVfx(p.getLocation().clone(),k);
        switch(k){
            case THOR->{if(!peace(p.getLocation()))resurrectThor(p,s);}
            case ANUBIS->{if(!peace(p.getLocation()))summonArmy(p,before,s);}
            case KOSCHEI->{if(!s.awakened&&s.charges==0)destroyEgg(s);else ring(p.getLocation(),k,2);}
            case KITSUNE->{if(!peace(p.getLocation())){conceal(p);makeClones(p,s.awakened?9:3);}}
            case MEDUSA->{double radius=s.awakened?20:8;for(Entity entity:p.getNearbyEntities(radius,radius,radius))if(entity instanceof LivingEntity l&&enemy(p,l)&&l.getLocation().distanceSquared(p.getLocation())<=radius*radius)freeze(l);}
        }
        refreshItem(p,k);message(p,k,"Спасение · "+chargeText(k,s));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void successfulHit(EntityDamageByEntityEvent e){
        if(e.getFinalDamage()<=0)return;Player p=attacker(e.getDamager());if(p==null)return;
        combat.put(p.getUniqueId(),now());
        Kind k=active(p);
        if(k==Kind.ANUBIS&&e.getEntity() instanceof LivingEntity l&&!(l instanceof Player))soulHits.put(l.getUniqueId(),new Credit(p.getUniqueId(),activeState(p),now()+15000));
        else soulHits.remove(e.getEntity().getUniqueId());
        if(k==Kind.KITSUNE)reveal(p);
        if(k==Kind.THOR&&!mirrorReflection(e)&&!peace(p.getLocation())&&!peace(e.getEntity().getLocation())&&e.getDamager()==p&&!internalDamage.contains(p.getUniqueId())){e.getEntity().getWorld().strikeLightningEffect(e.getEntity().getLocation());burst(e.getEntity().getLocation(),k,6);}
    }
    private double maxHealth(LivingEntity l){var a=l.getAttribute(Attribute.MAX_HEALTH);return a==null?20:a.getValue();}
    private void heal(Player p,double amount){p.setHealth(Math.min(maxHealth(p),p.getHealth()+amount));}
    private void resurrectThor(Player p,State s){
        for(int i=0;i<9;i++){double a=i*Math.PI*2/9;p.getWorld().strikeLightningEffect(p.getLocation().clone().add(Math.cos(a)*4,0,Math.sin(a)*4));}
        if(s.awakened){World w=p.getWorld();if(w.getEnvironment()==World.Environment.NORMAL){w.setStorm(true);w.setThundering(true);w.setWeatherDuration(Math.max(w.getWeatherDuration(),600));w.setThunderDuration(Math.max(w.getThunderDuration(),600));}storm.put(p.getUniqueId(),now()+30000);stormInstance.put(p.getUniqueId(),s);}
    }
    private void stormPulse(Player p){
        World w=p.getWorld();for(int i=0;i<3;i++){
            double a=random.nextDouble()*Math.PI*2,r=random.nextDouble()*20;
            Location strike=p.getLocation().clone().add(Math.cos(a)*r,0,Math.sin(a)*r);
            w.strikeLightningEffect(strike);burst(strike,Kind.THOR,6);
            if(strike.distanceSquared(p.getLocation())<=12.25)heal(p,4);
            for(Entity e:w.getNearbyEntities(strike,3,4,3))if(e instanceof LivingEntity l&&enemy(p,l)){
                internalDamage.add(p.getUniqueId());try{l.damage(5,p);}finally{internalDamage.remove(p.getUniqueId());}
                if(!l.isDead()&&l.getHealth()>0){l.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,100,1));l.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,100,1));}
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void naturalLightning(LightningStrikeEvent e){
        if(e.getCause()!=LightningStrikeEvent.Cause.WEATHER)return;
        Location original=e.getLightning().getLocation(),best=null;Player holder=null;State chosen=null;double distance=64*64;
        for(Entity entity:e.getWorld().getNearbyEntities(original,64,96,64)){
            Location l=entity.getLocation();State candidate=entity instanceof Item i?state(i.getItemStack()):null;
            if(entity instanceof Player p&&usable(p)){candidate=state(p.getInventory().getItemInOffHand());if(candidate==null||candidate.kind!=Kind.THOR)candidate=state(p.getInventory().getItemInMainHand());}
            if(candidate==null||candidate.kind!=Kind.THOR||l.getBlockY()<e.getWorld().getHighestBlockYAt(l)-1)continue;
            double d=l.distanceSquared(original);if(d<distance){distance=d;best=l;chosen=candidate;holder=entity instanceof Player p?p:null;}
        }
        if(best==null)return;e.setCancelled(true);e.getWorld().strikeLightningEffect(best);burst(best,Kind.THOR,24);charge(chosen,1,holder);
        if(holder!=null&&active(holder)==Kind.THOR&&activeState(holder).awakened)heal(holder,4);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void targeting(EntityTargetLivingEntityEvent e){
        if(e.getTarget()==null)return;
        if(e.getTarget() instanceof Player p&&active(p)==Kind.ANUBIS&&e.getEntityType()==EntityType.HUSK){e.setCancelled(true);return;}
        Minion m=minions.get(e.getEntity().getUniqueId());if(m==null)return;Player p=Bukkit.getPlayer(m.owner());
        if(p==null||!enemy(p,e.getTarget())||(!(e.getTarget() instanceof Player)&&!(e.getTarget() instanceof Monster)&&!(e.getTarget() instanceof Slime)))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void died(EntityDeathEvent e){
        LivingEntity entity=e.getEntity();
        if(entity.getPersistentDataContainer().has(summonKey,PersistentDataType.STRING)||clones.containsKey(entity.getUniqueId())){e.getDrops().clear();e.setDroppedExp(0);minions.remove(entity.getUniqueId());clones.remove(entity.getUniqueId());return;}
        if(entity instanceof Player p){
            if(marks.size()>=64){UUID first=marks.keySet().iterator().next();removeMark(first);}
            marks.put(UUID.randomUUID(),new Mark(p.getLocation().clone(),now(),p.getName()));save();return;
        }
        Credit soul=soulHits.remove(entity.getUniqueId());
        Player p=entity.getKiller();
        if(p==null&&e.getDamageSource().getCausingEntity() instanceof Player source)p=source;
        if(p==null&&soul!=null&&soul.until()>=now())p=Bukkit.getPlayer(soul.owner());
        if(p==null)return;
        Kind k=active(p);
        Credit credit=credits.remove(entity.getUniqueId());
        if(k==Kind.MEDUSA&&credit!=null&&credit.owner().equals(p.getUniqueId())&&credit.state()==activeState(p)&&credit.until()>=now()){
            State s=activeState(p);if(s.charges<cap(k,s)){if(++s.progress>=20){s.progress=0;charge(s,1,p);}save();}
        }
    }
    private static int anubisXp(int current,int received){return (int)Math.min(500L,Math.max(0L,current)+10L*Math.max(0,received));}
    @EventHandler(priority=EventPriority.MONITOR) public void receivedXp(PlayerExpChangeEvent e){
        Player p=e.getPlayer();if(e.getAmount()<=0||p.getGameMode()==GameMode.CREATIVE||p.getGameMode()==GameMode.SPECTATOR)return;
        State st=activeState(p);if(st==null||st.kind!=Kind.ANUBIS)return;
        int old=st.charges;st.charges=anubisXp(old,e.getAmount());if(st.charges==old)return;
        if(!save()){st.charges=old;return;}
        p.sendActionBar(title(Kind.ANUBIS,"+"+(st.charges-old)+" заряда · "+st.charges+"/500"));refreshItem(p,Kind.ANUBIS);
    }
    private void summonArmy(Player p,int souls,State source){
        if(activeState(p)!=source)return;boolean awakened=source.awakened;
        int count=armySize(souls);
        for(int i=0;i<count+(awakened?3:0);i++){
            boolean elite=i>=count;double a=i*Math.PI*2/(count+(awakened?3:0));
            Location l=p.getLocation().clone().add(Math.cos(a)*2,0,Math.sin(a)*2);if(!l.getBlock().isPassable())l=p.getLocation();
            Mob m=elite||i%2==0?l.getWorld().spawn(l,Skeleton.class):l.getWorld().spawn(l,Husk.class);
            m.getPersistentDataContainer().set(summonKey,PersistentDataType.STRING,p.getUniqueId().toString());
            m.setPersistent(false);m.setRemoveWhenFarAway(false);m.setCanPickupItems(false);
            var attr=m.getAttribute(Attribute.MAX_HEALTH);if(attr!=null)attr.setBaseValue(elite?40:1);m.setHealth(elite?40:1);
            EntityEquipment eq=m.getEquipment();
            if(eq!=null){eq.setItemInMainHand(new ItemStack(elite?Material.NETHERITE_AXE:Material.STONE_SWORD));if(elite){eq.setItemInOffHand(new ItemStack(Material.SHIELD));eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));}}
            m.customName(title(Kind.ANUBIS,elite?"Страж Дуата":"Душа Анубиса"));m.setCustomNameVisible(true);
            minions.put(m.getUniqueId(),new Minion(p.getUniqueId(),source,now()+30000));burst(l,Kind.ANUBIS,12);
        }
    }
    private boolean claimed(Location loc){
        var claim=((dev.unified.UnifiedPlugin)plugin.host()).claimModule();
        return claim==null||claim.isClaimed(loc);
    }
    List<Location> eggLocations(){return boundEggs.values().stream().filter(s->s.egg!=null).map(s->s.egg.clone()).toList();}
    private boolean physicalEgg(State s){if(s.egg==null||!s.egg.isWorldLoaded())return false;World w=s.egg.getWorld();if(s.eggWorld!=null&&!s.eggWorld.equals(w.getUID()))return false;return !w.isChunkLoaded(s.egg.getBlockX()>>4,s.egg.getBlockZ()>>4)||eggBlock(s.egg.getBlock());}
    private boolean eggBlock(Block b){return b.getType()==Material.SNIFFER_EGG||b.getType()==Material.DRAGON_EGG;}
    private State eggAt(Block b){for(State s:boundEggs.values())if(same(s.egg,b.getLocation()))return s;return null;}
    private boolean isEgg(Block b){return eggAt(b)!=null;}
    private boolean same(Location a,Location b){return a!=null&&b!=null&&Objects.equals(a.getWorld(),b.getWorld())&&a.getBlockX()==b.getBlockX()&&a.getBlockY()==b.getBlockY()&&a.getBlockZ()==b.getBlockZ();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void eggPlace(BlockPlaceEvent e){
        State s=state(e.getItemInHand());if(s==null||s.kind!=Kind.KOSCHEI)return;Player p=e.getPlayer();
        String reason=null;
        if(!canRitual(p))reason="Нужен режим выживания.";
        else if(rituals.containsKey(s))reason="Идёт пробуждение этого яйца.";
        else if(s.egg!=null||s.broken||s.retired||s.owner!=null&&!s.owner.equals(p.getUniqueId()))reason="Это яйцо уже привязано или разрушено.";
        else if(claimed(e.getBlockPlaced().getLocation()))reason="Эта точка внутри привата либо модуль приватов недоступен.";
        else if(!e.getBlockPlaced().getRelative(BlockFace.DOWN).getType().isSolid())reason="Нужен твёрдый блок под яйцом.";
        if(reason!=null){e.setCancelled(true);message(p,Kind.KOSCHEI,reason);}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void eggPlaced(BlockPlaceEvent e){
        State s=state(e.getItemInHand());if(s==null||s.kind!=Kind.KOSCHEI)return;
        Player p=e.getPlayer();State old=boundEggs.get(p.getUniqueId());
        if(old!=null&&old!=s){if(rituals.containsKey(old))abortRitual(rituals.get(old),"замена яйца");old.retired=true;}
        s.egg=e.getBlockPlaced().getLocation();s.eggWorld=s.egg.getWorld().getUID();s.owner=p.getUniqueId();boundEggs.put(s.owner,s);s.eggHp=3;s.lastEggHit=now();s.linkIssued=false;
        if(!save()){s.egg=null;s.owner=null;if(old!=null&&old!=s){old.retired=false;boundEggs.put(p.getUniqueId(),old);}else if(old==null)boundEggs.remove(p.getUniqueId());e.setCancelled(true);message(p,Kind.KOSCHEI,"Не удалось сохранить яйцо.");return;}
        if(old!=null&&old!=s){if(old.egg!=null&&old.egg.isWorldLoaded()&&(old.eggWorld==null||old.eggWorld.equals(old.egg.getWorld().getUID()))){World w=old.egg.getWorld();int x=old.egg.getBlockX()>>4,z=old.egg.getBlockZ()>>4;if(!w.isChunkLoaded(x,z)&&w.isChunkGenerated(x,z))w.getChunkAt(x,z);}cleanupRetiredEgg(old);message(p,Kind.KOSCHEI,"Старое яйцо и его душа уничтожены.");}
        burst(s.egg,Kind.KOSCHEI,36);issueLink(p,s,true);
        Bukkit.getScheduler().runTask(plugin.host(),()->{if(s.egg!=null&&eggBlock(s.egg.getBlock()))renderEgg(s);});
    }
    private void issueLink(Player p,State s,boolean rotate){
        String old=s.serial;boolean issued=s.linkIssued;
        if(rotate)changeSerial(s,UUID.randomUUID().toString());
        s.linkIssued=true;
        if(!save()){changeSerial(s,old);s.linkIssued=issued;message(p,Kind.KOSCHEI,"Ошибка сохранения. /u talisman link возле яйца.");return;}
        give(p,item(s));message(p,Kind.KOSCHEI,"Душа Кощея выдана. Спасение только пока она в руке.");
    }
    private void hitEgg(Player p,State s,Block b){
        if(!canRitual(p)||isStone(p)||claimed(b.getLocation()))return;
        if(rituals.containsKey(s))abortRitual(rituals.get(s),"яйцо атаковано");
        long time=now();if(time-s.lastEggHit<350)return;
        int previous=s.eggHp;long last=s.lastEggHit;s.eggHp--;s.lastEggHit=time;
        burst(b.getLocation(),Kind.KOSCHEI,12);
        b.getWorld().playSound(b.getLocation(),Sound.BLOCK_GLASS_HIT,1,.8f);
        if(s.eggHp<=0){destroyEgg(s);message(p,Kind.KOSCHEI,"Яйцо разрушено.");}
        else if(!save()){s.eggHp=previous;s.lastEggHit=last;}
        else {message(p,Kind.KOSCHEI,"Прочность яйца: "+s.eggHp+"/3");}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void eggDamage(BlockDamageEvent e){
        State s=eggAt(e.getBlock());if(s==null)return;e.setCancelled(true);hitEgg(e.getPlayer(),s,e.getBlock());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void eggBreak(BlockBreakEvent e){
        State s=eggAt(e.getBlock());if(s==null)return;e.setCancelled(true);hitEgg(e.getPlayer(),s,e.getBlock());
    }
    private void cleanupRetiredEgg(State s){
        if(!s.retired||s.egg==null)return;removeEggDisplay(s);Location l=s.egg;
        if(!l.isWorldLoaded()||s.eggWorld!=null&&!s.eggWorld.equals(l.getWorld().getUID())){s.egg=null;s.broken=true;s.linkIssued=false;save();return;}
        if(l.getWorld().isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4)){
            if(eggAt(l.getBlock())==null&&l.getBlock().getType()==Material.SNIFFER_EGG)l.getBlock().setType(Material.AIR,false);
            s.egg=null;s.broken=true;s.eggHp=0;s.linkIssued=false;save();
        }
    }
    private void destroyEgg(State s){
        removeEggDisplay(s);
        Location l=s.egg;if(l!=null&&eggBlock(l.getBlock())){l.getBlock().setType(Material.AIR,false);burst(l,Kind.KOSCHEI,50);l.getWorld().playSound(l,Sound.BLOCK_GLASS_BREAK,1,.7f);}
        s.egg=null;s.broken=true;s.eggHp=0;s.linkIssued=false;save();Player p=s.owner==null?null:Bukkit.getPlayer(s.owner);
        if(p!=null)message(p,Kind.KOSCHEI,"Яйцо уничтожено. /tf restore — 16 мембран.");
    }
    private void removeEggDisplay(State s){
        if(s.display!=null){Entity entity=Bukkit.getEntity(s.display);if(entity!=null)entity.remove();s.display=null;}
    }
    private void renderEgg(State s){
        Block block=s.egg.getBlock();
        // Migrate only the registered old Koschei block; never change ordinary dragon eggs.
        if(block.getType()==Material.DRAGON_EGG)block.setType(Material.SNIFFER_EGG,false);
        if(!plugin.getConfig().getBoolean("mythic-custom-models",true)){removeEggDisplay(s);return;}
        Location center=s.egg.clone().add(.5,.5,.5);
        ItemStack model=new ItemStack(Material.SNIFFER_EGG);
        ItemMeta meta=model.getItemMeta();
        meta.setItemModel(new NamespacedKey("talismanforge","mythic_koschei_block"+(s.awakened?"_awakened":"")));
        model.setItemMeta(meta);
        Entity existing=s.display==null?null:Bukkit.getEntity(s.display);
        ItemDisplay display;
        if(existing instanceof ItemDisplay item&&item.isValid()&&item.getWorld()==center.getWorld()&&item.getLocation().distanceSquared(center)<.01)display=item;
        else{
            removeEggDisplay(s);
            display=center.getWorld().spawn(center,ItemDisplay.class,d->{
                d.setPersistent(false);d.setInvulnerable(true);d.setGravity(false);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setDisplayWidth(1.1f);d.setDisplayHeight(1.1f);
                // A slightly larger exact vanilla egg cuboid hides the underlying native egg
                // without replacing vanilla assets or changing collision.
                var transform=d.getTransformation();transform.getScale().set(1.025f);d.setTransformation(transform);
                d.getPersistentDataContainer().set(key("koschei_display"),PersistentDataType.BYTE,(byte)1);
                d.setItemStack(model);
            });s.display=display.getUniqueId();
        }
        if(!model.equals(display.getItemStack()))display.setItemStack(model);
    }
    private Location safeEgg(Location l){
        for(int y=0;y<=2;y++)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
            if(x==0&&z==0)continue;Location t=l.clone().add(x,y,z);
            if(t.getWorld()==null||t.getBlockY()<t.getWorld().getMinHeight()+1||t.getBlockY()>=t.getWorld().getMaxHeight()-2||!t.getWorld().isChunkLoaded(t.getBlockX()>>4,t.getBlockZ()>>4)||claimed(t)||peace(t))continue;Block feet=t.getBlock(),head=feet.getRelative(BlockFace.UP),ground=feet.getRelative(BlockFace.DOWN);if(feet.isPassable()&&head.isPassable()&&!feet.isLiquid()&&!head.isLiquid()&&ground.getType().isSolid()&&!Set.of(Material.MAGMA_BLOCK,Material.CACTUS,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW).contains(ground.getType())&&!Set.of(Material.FIRE,Material.SOUL_FIRE,Material.POWDER_SNOW).contains(feet.getType())){t.add(.5,0,.5);t.setDirection(l.clone().add(.5,.5,.5).toVector().subtract(t.toVector()));return t;}
        }return null;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void useEgg(PlayerInteractEvent e){
        if(e.getClickedBlock()==null||!isEgg(e.getClickedBlock()))return;
        if(e.getAction()==Action.LEFT_CLICK_BLOCK){if(e.getHand()==EquipmentSlot.HAND&&e.useInteractedBlock()!=Event.Result.DENY){State hit=eggAt(e.getClickedBlock());if(hit!=null){e.setCancelled(true);hitEgg(e.getPlayer(),hit,e.getClickedBlock());}}return;}
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;e.setCancelled(true);if(e.getHand()!=EquipmentSlot.HAND)return;
        State s=eggAt(e.getClickedBlock());Player p=e.getPlayer();if(s==null)return;
        if(!p.getUniqueId().equals(s.owner)||!usable(p))return;
        if(!s.awakened&&s.charges<3&&p.getInventory().getItemInMainHand().getType()==Material.PHANTOM_MEMBRANE&&take(p,Material.PHANTOM_MEMBRANE,1)){int before=s.charges;charge(s,1,p);if(s.charges==before)give(p,new ItemStack(Material.PHANTOM_MEMBRANE));}
        else message(p,Kind.KOSCHEI,"Жизни: "+chargeText(Kind.KOSCHEI,s)+" · Прочность: "+s.eggHp+"/3");
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void eggMove(BlockFromToEvent e){if(isEgg(e.getToBlock())||isEgg(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void eggGrow(BlockGrowEvent e){if(isEgg(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void eggHatch(BlockFadeEvent e){if(isEgg(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void eggBurn(BlockBurnEvent e){if(isEgg(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void eggFall(BlockPhysicsEvent e){if(isEgg(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void eggEntity(EntityChangeBlockEvent e){if(isEgg(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void eggExplode(EntityExplodeEvent e){e.blockList().removeIf(this::isEgg);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void eggExplode(BlockExplodeEvent e){e.blockList().removeIf(this::isEgg);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void eggPiston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(this::isEgg))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void eggPiston(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(this::isEgg))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void food(FoodLevelChangeEvent e){
        if(e.getEntity() instanceof Player p&&active(p)==Kind.KOSCHEI&&e.getFoodLevel()>p.getFoodLevel()){
            int old=p.getFoodLevel();float saturation=p.getSaturation();int increase=e.getFoodLevel()-old;e.setFoodLevel(old+(int)Math.floor(increase*.75));
            Bukkit.getScheduler().runTask(plugin.host(),()->{if(p.isOnline()&&active(p)==Kind.KOSCHEI&&p.getSaturation()>saturation)p.setSaturation(saturation+(p.getSaturation()-saturation)*.75f);});
        }
    }
    private void conceal(Player p){
        if(!hidden.add(p.getUniqueId()))return;
        for(Player viewer:Bukkit.getOnlinePlayers())if(viewer!=p&&!((dev.unified.UnifiedPlugin)plugin.host()).areClanmates(viewer.getUniqueId(),p.getUniqueId()))viewer.hidePlayer(plugin.host(),p);
        burst(p.getLocation(),Kind.KITSUNE,20);
    }
    private void reveal(Player p){
        combat.put(p.getUniqueId(),now());sneak.remove(p.getUniqueId());
        if(!hidden.remove(p.getUniqueId()))return;
        for(Player viewer:Bukkit.getOnlinePlayers())if(viewer!=p)viewer.showPlayer(plugin.host(),p);
        revealUntil.put(p.getUniqueId(),now()+60000);burst(p.getLocation(),Kind.KITSUNE,12);
    }
    private void show(Player p){if(hidden.remove(p.getUniqueId()))for(Player v:Bukkit.getOnlinePlayers())v.showPlayer(plugin.host(),p);}
    private void makeClones(Player p,int count){
        State source=activeState(p);if(source==null||peace(p.getLocation()))return;
        for(var e:new ArrayList<>(clones.entrySet()))if(e.getValue().owner().equals(p.getUniqueId())){Entity old=Bukkit.getEntity(e.getKey());if(old!=null)old.remove();clones.remove(e.getKey());}
        for(int i=0;i<count;i++){
            double a=2*Math.PI*i/count;Vector direction=new Vector(Math.cos(a),0,Math.sin(a));Location loc=p.getLocation().clone().add(direction.clone().multiply(.4));if(!walkable(loc,p)){loc=safeCloneSpawn(p,direction);if(loc==null)continue;}loc.setDirection(direction);
            Mannequin clone=loc.getWorld().spawn(loc,Mannequin.class);
            clone.setProfile(ResolvableProfile.resolvableProfile(p.getPlayerProfile()));clone.setDescription(null);clone.customName(Component.text(p.getName()));clone.setCustomNameVisible(true);
            clone.setInvulnerable(true);clone.setCanPickupItems(false);clone.setPersistent(false);clone.setCollidable(false);clone.setAI(false);clone.setGravity(true);clone.setImmovable(false);
            clone.getPersistentDataContainer().set(summonKey,PersistentDataType.STRING,p.getUniqueId().toString());
            clone.getEquipment().setArmorContents(Arrays.stream(p.getInventory().getArmorContents()).map(this::visualCopy).toArray(ItemStack[]::new));clone.getEquipment().setItemInMainHand(visualCopy(p.getInventory().getItemInMainHand()));clone.getEquipment().setItemInOffHand(visualCopy(p.getInventory().getItemInOffHand()));
            clones.put(clone.getUniqueId(),new Clone(p.getUniqueId(),source,now()+5000,direction));
        }
    }
    private ItemStack visualCopy(ItemStack item){
        if(item==null||item.getType().isAir())return new ItemStack(Material.AIR);
        ItemStack copy=new ItemStack(item.getType());ItemMeta meta=copy.getItemMeta(),original=item.getItemMeta();
        if(original!=null&&meta!=null){if(original.hasItemModel())meta.setItemModel(original.getItemModel());if(original.hasEquippable())meta.setEquippable(original.getEquippable());copy.setItemMeta(meta);}
        return copy;
    }
    private Location safeCloneSpawn(Player p,Vector direction){for(int y=0;y>=-3;y--)for(double r:new double[]{.5,1,1.5,0}){Location l=p.getLocation().clone().add(direction.clone().multiply(r)).add(0,y,0);if(walkable(l,p))return l;}return null;}
    private boolean walkable(Location l,Player owner){
        World w=l.getWorld();if(w==null||!w.isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4)||peace(l)||l.getY()<w.getMinHeight()+1||l.getY()>=w.getMaxHeight()-2)return false;
        var claim=((dev.unified.UnifiedPlugin)plugin.host()).claimModule();
        if(claim==null||!claim.allowsTemporaryStructure(owner,l))return false;
        Block feet=l.getBlock(),head=feet.getRelative(BlockFace.UP),ground=feet.getRelative(BlockFace.DOWN);
        return feet.isPassable()&&head.isPassable()&&!feet.isLiquid()&&!head.isLiquid()&&ground.getType().isSolid()&&!Set.of(Material.MAGMA_BLOCK,Material.CACTUS,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW).contains(ground.getType())&&feet.getType()!=Material.FIRE&&feet.getType()!=Material.SOUL_FIRE;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void swapClone(PlayerInteractEntityEvent e){
        Clone c=clones.get(e.getRightClicked().getUniqueId());if(c==null)return;e.setCancelled(true);Player p=e.getPlayer();
        if(e.getHand()==EquipmentSlot.HAND&&c.owner().equals(p.getUniqueId())&&activeState(p)==c.state()&&c.state().awakened&&p.isSneaking()){
            Location old=p.getLocation(),next=e.getRightClicked().getLocation();if(!closeTo(old,next,12)||!walkable(next,p)||!walkable(old,p))return;if(p.teleport(next)){e.getRightClicked().teleport(old);burst(old,Kind.KITSUNE,12);burst(next,Kind.KITSUNE,12);p.playSound(next,Sound.ENTITY_ENDERMAN_TELEPORT,.7f,1.5f);}
        }
    }
    private Set<State> kitsunes(Inventory inv){Set<State> found=new HashSet<>();for(ItemStack i:inv.getContents()){State s=state(i);if(s!=null&&s.kind==Kind.KITSUNE)found.add(s);}return found;}
    private Location storageLocation(Inventory inventory){
        if(inventory.getHolder() instanceof DoubleChest chest&&chest.getLeftSide() instanceof BlockState b)return b.getLocation();
        if(inventory.getHolder() instanceof Container c)return c.getLocation();return null;
    }
    private void checkStorage(Inventory inv,Player owner){
        boolean ender=inv.getType()==InventoryType.ENDER_CHEST&&owner!=null;Location l=storageLocation(inv);if(!ender&&l==null)return;
        Set<State> present=kitsunes(inv),relevant=new HashSet<>(present);
        for(State s:states.values())if(s.kind==Kind.KITSUNE&&(ender?owner.getUniqueId().equals(s.enderOwner):same(s.store,l)))relevant.add(s);
        for(State s:relevant){boolean match=ender?owner.getUniqueId().equals(s.enderOwner):same(s.store,l);
            if(present.contains(s)){if(!match){s.store=ender?null:l;s.enderOwner=ender?owner.getUniqueId():null;s.storedAt=now();save();}else applyStorage(s);}
            else if(match){applyStorage(s);s.store=null;s.enderOwner=null;s.storedAt=0;save();}
        }
    }
    private void applyStorage(State s){
        if(s.storedAt==0)return;int max=cap(Kind.KITSUNE,s);
        long n=(now()-s.storedAt)/RECHARGE;if(n<=0)return;
        s.charges=storageCharges(s.charges,max,now()-s.storedAt);s.storedAt=s.charges==max?now():s.storedAt+n*RECHARGE;save();
    }
    private void validateStorage(){
        Set<UUID> checked=new HashSet<>();Set<Location> checkedChests=new HashSet<>();
        for(State s:new ArrayList<>(states.values()))if(s.kind==Kind.KITSUNE){
            if(s.enderOwner!=null){Player p=Bukkit.getPlayer(s.enderOwner);if(p!=null&&checked.add(s.enderOwner))checkStorage(p.getEnderChest(),p);}
            else if(s.store!=null){Location l=s.store;World w=l.getWorld();if(w!=null&&w.isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4)){
                if(l.getBlock().getState() instanceof Container c){if(checkedChests.add(l))checkStorage(c.getInventory(),null);}
                else{s.store=null;s.storedAt=0;save();}
            }}
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void inventoryClick(InventoryClickEvent e){Player p=e.getWhoClicked() instanceof Player pl?pl:null;Inventory top=e.getView().getTopInventory();Bukkit.getScheduler().runTask(plugin.host(),()->checkStorage(top,p));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void inventoryDrag(InventoryDragEvent e){Player p=e.getWhoClicked() instanceof Player pl?pl:null;Inventory top=e.getView().getTopInventory();Bukkit.getScheduler().runTask(plugin.host(),()->checkStorage(top,p));}
    @EventHandler public void inventoryClose(InventoryCloseEvent e){checkStorage(e.getInventory(),e.getPlayer() instanceof Player p?p:null);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void inventoryMove(InventoryMoveItemEvent e){Bukkit.getScheduler().runTask(plugin.host(),()->{checkStorage(e.getSource(),null);checkStorage(e.getDestination(),null);});}
    private static long medusaGazeMillis(boolean awakened){return awakened?1000L:3000L;}
    private static long medusaStunMillis(){return 15000L;}
    private boolean isStone(LivingEntity l){return l instanceof Player p?stonePlayers.getOrDefault(p.getUniqueId(),0L)>now():l.getPersistentDataContainer().has(frozenKey,PersistentDataType.BYTE);}
    private void freeze(LivingEntity l){
        if(!eligible(l)||isStone(l)||stoneImmune.getOrDefault(l.getUniqueId(),0L)>now())return;
        if(l instanceof Player p){stonePlayers.put(p.getUniqueId(),now()+medusaStunMillis());p.leaveVehicle();p.setGliding(false);p.setSprinting(false);p.sendActionBar(title(Kind.MEDUSA,"Окаменение · 15 сек."));}
        else {var data=l.getPersistentDataContainer();long until=now()+medusaStunMillis();frozenMobs.put(l.getUniqueId(),until);data.set(frozenUntil,PersistentDataType.LONG,until);data.set(frozenKey,PersistentDataType.BYTE,(byte)1);data.set(aiKey,PersistentDataType.BYTE,(byte)(l.hasAI()?1:0));data.set(despawnKey,PersistentDataType.BYTE,(byte)(l.getRemoveWhenFarAway()?1:0));data.set(gravityKey,PersistentDataType.BYTE,(byte)(l.hasGravity()?1:0));l.setAI(false);l.setGravity(false);l.setRemoveWhenFarAway(false);if(l instanceof Mob mob){mob.setTarget(null);mob.getPathfinder().stopPathfinding();}}
        l.setVelocity(new Vector());burst(l.getLocation(),Kind.MEDUSA,24);l.getWorld().playSound(l.getLocation(),Sound.BLOCK_STONE_PLACE,1,.6f);
    }
    private void thaw(LivingEntity l){
        if(l instanceof Player p)stonePlayers.remove(p.getUniqueId());
        else {var data=l.getPersistentDataContainer();if(!data.has(frozenKey,PersistentDataType.BYTE))return;l.setAI(data.getOrDefault(aiKey,PersistentDataType.BYTE,(byte)1)==1);l.setRemoveWhenFarAway(data.getOrDefault(despawnKey,PersistentDataType.BYTE,(byte)1)==1);l.setGravity(data.getOrDefault(gravityKey,PersistentDataType.BYTE,(byte)1)==1);data.remove(frozenKey);data.remove(frozenUntil);frozenMobs.remove(l.getUniqueId());data.remove(aiKey);data.remove(despawnKey);data.remove(gravityKey);}
        stoneImmune.put(l.getUniqueId(),now()+30000);l.getWorld().playSound(l.getLocation(),Sound.BLOCK_STONE_BREAK,1,1.2f);
        l.getWorld().spawnParticle(Particle.BLOCK,l.getLocation().add(0,.7,0),18,.4,.5,.4,0,Material.STONE.createBlockData());
    }
    private boolean looking(LivingEntity source,LivingEntity target,double dot){
        Vector d=target.getEyeLocation().toVector().subtract(source.getEyeLocation().toVector());
        return d.lengthSquared()>0&&source.getEyeLocation().getDirection().dot(d.normalize())>=dot&&source.hasLineOfSight(target);
    }
    private void gaze(Player p){
        State st=activeState(p);if(st==null||st.gazeReady>now()||isStone(p)){gazes.remove(p.getUniqueId());return;}
        LivingEntity best=null;double distance=12*12;
        for(Entity e:p.getNearbyEntities(12,12,12))if(e instanceof LivingEntity l&&enemy(p,l)&&!isStone(l)&&stoneImmune.getOrDefault(l.getUniqueId(),0L)<=now()){
            double d=l.getLocation().distanceSquared(p.getLocation());if(d>distance||!looking(p,l,.96))continue;
            best=l;distance=d;
        }
        if(best==null){gazes.remove(p.getUniqueId());return;}
        Gaze g=gazes.get(p.getUniqueId());if(g==null||!g.victim().equals(best.getUniqueId())){gazes.put(p.getUniqueId(),new Gaze(best.getUniqueId(),now()));return;}
        long required=medusaGazeMillis(st.awakened);
        if(now()-g.since()<required){if(ticks%10==0)best.getWorld().spawnParticle(Particle.DUST,best.getEyeLocation(),3,.2,.2,.2,0,new Particle.DustOptions(Color.GRAY,1));return;}
        gazes.remove(p.getUniqueId());st.gazeReady=now()+30000;if(!save()){st.gazeReady=0;return;}
        if(best instanceof Player other&&other.isBlocking()&&looking(other,p,.3)){stonePlayers.put(p.getUniqueId(),now()+3000);p.setVelocity(new Vector());message(p,Kind.MEDUSA,"Взгляд отражён щитом · 3 сек.");burst(p.getLocation(),Kind.MEDUSA,16);return;}
        freeze(best);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenMove(PlayerMoveEvent e){
        if(isStone(e.getPlayer())&&e.getTo()!=null&&(!Objects.equals(e.getFrom().getWorld(),e.getTo().getWorld())||e.getFrom().distanceSquared(e.getTo())>0))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenTarget(EntityTargetLivingEntityEvent e){if(e.getEntity() instanceof LivingEntity l&&isStone(l)&&e.getTarget()!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenMount(org.bukkit.event.entity.EntityMountEvent e){if(e.getEntity() instanceof LivingEntity l&&isStone(l))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenVelocity(PlayerVelocityEvent e){if(isStone(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenInteract(PlayerInteractEvent e){if(isStone(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenInteract(PlayerInteractEntityEvent e){if(isStone(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenShoot(EntityShootBowEvent e){if(isStone(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenConsume(PlayerItemConsumeEvent e){if(isStone(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenBreak(BlockBreakEvent e){if(isStone(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenPlace(BlockPlaceEvent e){if(isStone(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenInventory(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p&&isStone(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenDrag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p&&isStone(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenSwap(PlayerSwapHandItemsEvent e){if(isStone(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frozenDrop(PlayerDropItemEvent e){if(isStone(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void entitiesLoaded(EntitiesLoadEvent e){for(Entity entity:e.getEntities()){
        if(entity instanceof Item i)recoverItem(i);
        else if(entity instanceof ItemDisplay&&entity.getPersistentDataContainer().has(key("ritual_visual"),PersistentDataType.BYTE)&&ritualDisplays.values().stream().noneMatch(ids->ids.contains(entity.getUniqueId()))){entity.remove();continue;}
        else if(generatedChunks.getOrDefault(chunkKey(entity.getLocation().getChunk()),0L)>now())markGenerated(entity);
        if(entity instanceof ItemDisplay&&entity.getPersistentDataContainer().has(key("koschei_display"),PersistentDataType.BYTE)&&boundEggs.values().stream().noneMatch(s->entity.getUniqueId().equals(s.display))){entity.remove();continue;}
        if(entity.getPersistentDataContainer().has(summonKey,PersistentDataType.STRING)&&!minions.containsKey(entity.getUniqueId())&&!clones.containsKey(entity.getUniqueId()))entity.remove();
        else if(entity instanceof LivingEntity l&&isStone(l)&&!(l instanceof Player)){long until=l.getPersistentDataContainer().getOrDefault(frozenUntil,PersistentDataType.LONG,0L);if(until<=now())thaw(l);else{frozenMobs.put(l.getUniqueId(),until);l.setAI(false);l.setGravity(false);l.setRemoveWhenFarAway(false);}}
    }}
    private void modifier(Player p,Attribute attribute,NamespacedKey key,double amount,AttributeModifier.Operation op,boolean apply){
        var a=p.getAttribute(attribute);if(a==null)return;var old=a.getModifier(key);
        if(apply&&old==null){a.addTransientModifier(new AttributeModifier(key,amount,op));if(attribute==Attribute.MAX_HEALTH&&p.getHealth()>a.getValue())p.setHealth(a.getValue());}
        else if(!apply&&old!=null)a.removeModifier(old);
    }
    private boolean raining(Player p){return p.getWorld().getEnvironment()==World.Environment.NORMAL&&p.getWorld().hasStorm();}
    private void effects(Player p,Kind k){
        boolean raining=raining(p);
        modifier(p,Attribute.MAX_HEALTH,healthKey,-2,AttributeModifier.Operation.ADD_NUMBER,k==Kind.KOSCHEI);
        modifier(p,Attribute.MOVEMENT_SPEED,rainKey,-.15,AttributeModifier.Operation.ADD_SCALAR,k==Kind.ANUBIS&&raining);
        if(k==Kind.THOR&&p.getWorld().getEnvironment()==World.Environment.NETHER)p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,10,0,true,false,true));
        boolean fly=k==Kind.THOR&&activeState(p).awakened&&raining;
        var data=p.getPersistentDataContainer();
        if(fly){
            if(!p.getAllowFlight()){p.setAllowFlight(true);flightGranted.add(p.getUniqueId());data.set(flightFlag,PersistentDataType.BYTE,(byte)1);}
            if(p.isFlying()||!p.isOnGround())data.set(fallGrace,PersistentDataType.BYTE,(byte)1);
        }else if(flightGranted.remove(p.getUniqueId())||data.has(flightFlag,PersistentDataType.BYTE)){
            if(usable(p)){if(p.isFlying()||!p.isOnGround())data.set(fallGrace,PersistentDataType.BYTE,(byte)1);p.setFlying(false);p.setAllowFlight(false);p.setFallDistance(0);}
            data.remove(flightFlag);
        }
        if(!fly&&p.isOnGround()&&!p.isFlying()&&p.getFallDistance()==0)data.remove(fallGrace);
        if(k!=null&&ticks%20==0){refreshItem(p,k);p.sendActionBar(title(k,"✦ "+k.title+"  ·  "+chargeText(k,activeState(p))+(k==Kind.ANUBIS&&raining?" · −15% скорости":k==Kind.KOSCHEI?" · −1 сердце":fly?" · Полёт":"")));}
    }
    private void removeMark(UUID id){marks.remove(id);UUID entity=markDisplays.remove(id);if(entity!=null){Entity e=Bukkit.getEntity(entity);if(e!=null)e.remove();}}
    private void renderMarks(){
        for(var entry:new ArrayList<>(marks.entrySet())){
            UUID id=entry.getKey();Mark mark=entry.getValue();if(now()-mark.time()>MARK_LIFE){removeMark(id);continue;}
            Location l=mark.loc();World w=l.getWorld();if(w==null||!w.isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4))continue;
            List<Player> viewers=w.getPlayers().stream().filter(p->active(p)==Kind.ANUBIS&&p.getLocation().distanceSquared(l)<=128*128).toList();
            Entity entity=markDisplays.containsKey(id)?Bukkit.getEntity(markDisplays.get(id)):null;
            if(viewers.isEmpty()){if(entity!=null)entity.remove();markDisplays.remove(id);continue;}
            TextDisplay d;
            if(entity instanceof TextDisplay existing)d=existing;
            else {d=w.spawn(l.clone().add(0,1.1,0),TextDisplay.class,t->{t.setPersistent(false);t.setVisibleByDefault(false);t.setSeeThrough(true);t.setBillboard(Display.Billboard.CENTER);t.setViewRange(4);t.text(title(Kind.ANUBIS,"✦ "+mark.name()+"\nМесто гибели"));});markDisplays.put(id,d.getUniqueId());}
            for(Player p:w.getPlayers())if(viewers.contains(p))p.showEntity(plugin.host(),d);else p.hideEntity(plugin.host(),d);
        }
    }
    private void tick(){
        ticks+=5;long time=now();
        if(ticks%20==0)for(UUID uid:hidden){Player target=Bukkit.getPlayer(uid);if(target==null)continue;for(Player viewer:Bukkit.getOnlinePlayers())if(viewer!=target){if(((dev.unified.UnifiedPlugin)plugin.host()).areClanmates(viewer.getUniqueId(),uid))viewer.showPlayer(plugin.host(),target);else viewer.hidePlayer(plugin.host(),target);}}
        tickRituals();if(ticks%10==0)detectRituals();
        for(Player p:Bukkit.getOnlinePlayers()){
            UUID id=p.getUniqueId();State bound=boundEggs.get(id);if(ticks%100==0&&bound!=null&&bound.egg!=null&&!bound.broken&&!bound.linkIssued){issueLink(p,bound,true);}
            Kind k=active(p);
            if(stonePlayers.containsKey(id)&&(stonePlayers.get(id)<=time||peace(p.getLocation())))thaw(p);
            if(k==Kind.KITSUNE&&!peace(p.getLocation())){
                State storage=activeState(p);
                if(storage.store!=null||storage.enderOwner!=null){storage.store=null;storage.enderOwner=null;storage.storedAt=0;save();}
                if(kitsuneEquipped.put(id,storage)!=storage){show(p);combat.put(id,time);sneak.remove(id);}
                if(p.isSneaking())sneak.putIfAbsent(id,time);else sneak.remove(id);
                if(!hidden.contains(id)&&time>=revealUntil.getOrDefault(id,0L)){
                    long since=combat.getOrDefault(id,time);
                    if(time-since>=180000||p.isSneaking()&&time-Math.max(since,sneak.getOrDefault(id,time))>=30000)conceal(p);
                }
            }else {kitsuneEquipped.remove(id);sneak.remove(id);show(p);}
            if(k==Kind.MEDUSA&&!peace(p.getLocation())){if(gazeInstance.put(id,activeState(p))!=activeState(p))gazes.remove(id);gaze(p);}else {gazes.remove(id);gazeInstance.remove(id);}
            effects(p,k);if(ticks%20==0){if(storm.getOrDefault(id,0L)>time&&k==Kind.THOR&&!peace(p.getLocation())&&stormInstance.get(id)==activeState(p))stormPulse(p);else {storm.remove(id);stormInstance.remove(id);}if(isStone(p)){p.setVelocity(new Vector());ring(p.getLocation(),Kind.MEDUSA,.65);p.sendActionBar(title(Kind.MEDUSA,"Окаменение · "+Math.max(1,(stonePlayers.getOrDefault(id,time)-time+999)/1000)+" сек."));}}
        }
        for(var entry:new ArrayList<>(clones.entrySet())){
            Entity entity=Bukkit.getEntity(entry.getKey());Clone c=entry.getValue();
            Player owner=Bukkit.getPlayer(c.owner());if(entity==null||!entity.isValid()||c.until()<=time||owner==null||owner.isDead()||activeState(owner)!=c.state()||owner.getWorld()!=entity.getWorld()){if(entity!=null){burst(entity.getLocation(),Kind.KITSUNE,12);entity.remove();}clones.remove(entry.getKey());continue;}
            Location l=entity.getLocation();Vector forward=c.direction().clone();if(random.nextInt(5)==0)forward.rotateAroundY((random.nextDouble()-.5)*.8);
            boolean moved=false;
            for(double angle:new double[]{0,.65,-.65,1.3,-1.3,Math.PI}){
                Vector dir=forward.clone().rotateAroundY(angle).normalize();
                for(double dy:new double[]{0,1,-1}){
                    Location next=l.clone().add(dir.clone().multiply(.85)).add(0,dy,0);next.setY(Math.floor(next.getY())+.02);
                    if(!walkable(next,owner)||!walkable(l.clone().add(dir.clone().multiply(.4)).add(0,Math.max(0,dy),0),owner))continue;
                    next.setDirection(dir);if(entity.teleport(next)){clones.put(entry.getKey(),new Clone(c.owner(),c.state(),c.until(),dir));moved=true;}break;
                }
                if(moved)break;
            }
            if(ticks%10==0&&entity instanceof LivingEntity living)living.swingMainHand();
        }
        if(ticks%20!=0)return;
        validateStorage();
        for(var entry:new ArrayList<>(frozenMobs.entrySet())){Entity entity=Bukkit.getEntity(entry.getKey());if(!(entity instanceof LivingEntity l)||l.isDead()){frozenMobs.remove(entry.getKey());continue;}if(entry.getValue()<=time)thaw(l);else{l.setVelocity(new Vector());if(l instanceof Mob mob){if(mob.getTarget()!=null)mob.setTarget(null);mob.getPathfinder().stopPathfinding();}}}
        for(State old:states.values())if(old.retired&&old.egg!=null)cleanupRetiredEgg(old);
        for(State egg:new ArrayList<>(boundEggs.values()))if(egg.egg!=null&&egg.egg.isWorldLoaded()&&egg.egg.getWorld().isChunkLoaded(egg.egg.getBlockX()>>4,egg.egg.getBlockZ()>>4)){
            if(!eggBlock(egg.egg.getBlock()))destroyEgg(egg);
            else {renderEgg(egg);if(egg.eggHp<3&&time-egg.lastEggHit>=30000){egg.eggHp++;egg.lastEggHit=time;save();}}
        }
        for(var entry:new ArrayList<>(minions.entrySet())){
            Entity entity=Bukkit.getEntity(entry.getKey());Minion m=entry.getValue();Player owner=Bukkit.getPlayer(m.owner());
            if(!(entity instanceof Mob mob)||m.until()<=time||owner==null||owner.isDead()||activeState(owner)!=m.state()||peace(owner.getLocation())||peace(entity.getLocation())||owner.getWorld()!=entity.getWorld()){if(entity!=null)entity.remove();minions.remove(entry.getKey());continue;}
            LivingEntity nearest=null;double distance=16*16;
            for(Entity e:mob.getNearbyEntities(16,8,16))if(e instanceof LivingEntity l&&enemy(owner,l)&&(l instanceof Player||l instanceof Monster||l instanceof Slime)){
                double d=l.getLocation().distanceSquared(mob.getLocation());if(d<distance){nearest=l;distance=d;}
            }
            mob.setTarget(nearest);
        }
        if(ticks%40==0)renderMarks();
        if(ticks%200==0){soulHits.entrySet().removeIf(e->e.getValue().until()<time);generatedChunks.entrySet().removeIf(e->e.getValue()<time);soulVfx.entrySet().removeIf(e->time-e.getValue()>60000);soulFeedback.entrySet().removeIf(e->time-e.getValue()>60000);stoneImmune.entrySet().removeIf(e->e.getValue()<time);stonePlayers.entrySet().removeIf(e->e.getValue()<time);credits.entrySet().removeIf(e->e.getValue().until()<time);}
    }
    @EventHandler public void join(PlayerJoinEvent e){
        Player p=e.getPlayer();combat.put(p.getUniqueId(),now());
        for(UUID uid:hidden){Player other=Bukkit.getPlayer(uid);if(other!=null&&other!=p&&!((dev.unified.UnifiedPlugin)plugin.host()).areClanmates(p.getUniqueId(),uid))p.hidePlayer(plugin.host(),other);}
        checkStorage(p.getEnderChest(),p);
    }
    @EventHandler public void quit(PlayerQuitEvent e){
        Player p=e.getPlayer();show(p);effects(p,null);UUID id=p.getUniqueId();combat.remove(id);sneak.remove(id);kitsuneEquipped.remove(id);gazes.remove(id);storm.remove(id);stormInstance.remove(id);gazeInstance.remove(id);
    }
    void close(){
        for(Ritual r:new ArrayList<>(rituals.values()))abortRitual(r,"сервер выключается");
        for(State s:boundEggs.values())removeEggDisplay(s);
        for(Player p:Bukkit.getOnlinePlayers()){show(p);effects(p,null);}
        for(UUID uid:new ArrayList<>(frozenMobs.keySet())){Entity entity=Bukkit.getEntity(uid);if(entity instanceof LivingEntity l)thaw(l);}
        for(UUID uid:minions.keySet()){Entity e=Bukkit.getEntity(uid);if(e!=null)e.remove();}
        for(UUID uid:clones.keySet()){Entity e=Bukkit.getEntity(uid);if(e!=null)e.remove();}
        for(UUID uid:markDisplays.values()){Entity e=Bukkit.getEntity(uid);if(e!=null)e.remove();}
        save();
    }
}
