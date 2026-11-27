package dev.unified.auction;

import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class UnifiedAuction extends JavaPlugin implements Listener,CommandExecutor,TabCompleter {
private static final int MAX_BID=256, MAX_QUEUE=12, MAX_MAIL=128;
private record Lot(String name,boolean secret,int start,ItemStack chest){}
private record Menu(UUID owner) implements InventoryHolder{
public Inventory getInventory(){throw new UnsupportedOperationException();}
}
private final List<Lot> queue=new ArrayList<>();
private final Map<UUID,List<ItemStack>> mail=new HashMap<>();
private UUID leader;
private String leaderName="";
private int price=0,seconds=60;
private long end=0,next=0;
private boolean running=false,frozen=false;
private Path file,journal;
private static final String P="§6[Аукцион] §f";
@Override public void onEnable(){
getDataFolder().mkdirs();file=getDataFolder().toPath().resolve("auction.yml");journal=getDataFolder().toPath().resolve("pending.yml");
try{load();if(Files.exists(journal)){frozen=true;getLogger().severe("Unresolved inventory transaction: inspect pending.yml and player data before restarting. Auction frozen.");}}
catch(Exception e){freeze(e);}
Objects.requireNonNull(getCommand("auction")).setExecutor(this);getCommand("auction").setTabCompleter(this);
Bukkit.getPluginManager().registerEvents(this,this);
Bukkit.getScheduler().runTaskTimer(this,this::tick,20,20);
}
private void freeze(Exception e){frozen=true;getLogger().severe("Auction frozen to prevent loss/duplication: "+e);}
private void atomic(Path target,String body)throws IOException{
Path tmp=target.resolveSibling(target.getFileName()+".tmp");
Files.writeString(tmp,body,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);
try(FileChannel ch=FileChannel.open(tmp,StandardOpenOption.WRITE)){ch.force(true);}
Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
}
private void load()throws Exception{
if(!Files.exists(file))return;
YamlConfiguration y=new YamlConfiguration();y.load(file.toFile());
running=y.getBoolean("running");end=y.getLong("end");next=y.getLong("next");seconds=y.getInt("seconds",60);
price=y.getInt("price");String id=y.getString("leader");if(id!=null)leader=UUID.fromString(id);
leaderName=y.getString("leader-name","");
int count=y.getInt("lots");if(count<0||count>MAX_QUEUE)throw new IOException("Bad queue size");
for(int i=0;i<count;i++){
String p="queue."+i+".";
ItemStack chest=y.getItemStack(p+"chest");
int start=y.getInt(p+"start");
if(chest==null||chest.getType()!=Material.CHEST||start<1||start>MAX_BID)throw new IOException("Bad lot");
queue.add(new Lot(y.getString(p+"name","Сундук"),y.getBoolean(p+"secret"),start,chest));
}
ConfigurationSection m=y.getConfigurationSection("mail");
if(m!=null)for(String key:m.getKeys(false)){
List<?> stacks=m.getList(key,List.of());List<ItemStack> items=new ArrayList<>();
for(Object o:stacks){if(!(o instanceof ItemStack item))throw new IOException("Bad mailbox item");items.add(item.clone());}
mail.put(UUID.fromString(key),items);
}
if(price<0||price>MAX_BID||(leader==null)!=(price==0)||(leader!=null&&(!running||queue.isEmpty()||end==0))||(running&&queue.isEmpty()))throw new IOException("Bad escrow state");
}
private void persist()throws IOException{
YamlConfiguration y=new YamlConfiguration();y.set("running",running);y.set("end",end);y.set("next",next);y.set("seconds",seconds);
y.set("leader",leader==null?null:leader.toString());y.set("leader-name",leaderName);y.set("price",price);y.set("lots",queue.size());
for(int i=0;i<queue.size();i++){Lot l=queue.get(i);String p="queue."+i+".";y.set(p+"name",l.name);y.set(p+"secret",l.secret);y.set(p+"start",l.start);y.set(p+"chest",l.chest);}
for(var e:mail.entrySet())if(!e.getValue().isEmpty())y.set("mail."+e.getKey(),e.getValue());
atomic(file,y.saveToString());
}
private boolean save(){try{persist();return true;}catch(Exception e){freeze(e);return false;}}
private boolean ready(Player p){return !frozen&&p.isOnline()&&!p.isDead()&&p.getGameMode()==GameMode.SURVIVAL;}
private List<ItemStack> mailbox(UUID id){return mail.computeIfAbsent(id,k->new ArrayList<>());}
private boolean room(UUID id,int slots){return mail.getOrDefault(id,List.of()).size()+slots<=MAX_MAIL;}
private void credit(UUID id,int amount){
if(amount<=0)return;
List<ItemStack> m=mailbox(id);
for(ItemStack s:m)if(s.getType()==Material.DIAMOND&&!s.hasItemMeta()&&s.getAmount()<64){int add=Math.min(64-s.getAmount(),amount);s.setAmount(s.getAmount()+add);amount-=add;if(amount==0)return;}
while(amount>0){int n=Math.min(64,amount);m.add(new ItemStack(Material.DIAMOND,n));amount-=n;}
}
private int diamonds(Player p){int n=0;for(ItemStack s:p.getInventory().getStorageContents())if(plainDiamond(s))n+=s.getAmount();return n;}
private boolean plainDiamond(ItemStack s){return s!=null&&s.getType()==Material.DIAMOND&&!s.hasItemMeta();}
private void debit(Player p,int n){
ItemStack[] a=p.getInventory().getStorageContents();
for(int i=0;i<a.length&&n>0;i++){ItemStack s=a[i];if(!plainDiamond(s))continue;int take=Math.min(n,s.getAmount());n-=take;if(take==s.getAmount())a[i]=null;else{a[i]=s.clone();a[i].setAmount(s.getAmount()-take);}}
if(n!=0)throw new IllegalStateException("Insufficient diamonds");p.getInventory().setStorageContents(a);
}
private void transaction(Player p,String action,Runnable mutate,Runnable state){
try{
if(Files.exists(journal))throw new IOException("Unresolved journal");
YamlConfiguration j=new YamlConfiguration();j.set("player",p.getUniqueId().toString());j.set("action",action);j.set("old-storage",Arrays.asList(p.getInventory().getStorageContents()));j.set("old-auction-state",Files.exists(file)?Files.readString(file):"");atomic(journal,j.saveToString());
mutate.run();p.saveData();state.run();persist();Files.delete(journal);
}catch(Exception e){freeze(e);p.sendMessage(P+"§cОперация остановлена. Администратор проверит журнал; повторно ничего не списывается.");}
}
private boolean active(){return running&&!queue.isEmpty()&&end>System.currentTimeMillis();}
private int minimum(){return leader==null?queue.getFirst().start:price+1;}
private void bid(Player p,int amount){
if(!ready(p)||!active()){p.sendMessage(P+"Сейчас ставки не принимаются.");return;}
if(amount<minimum()||amount>MAX_BID){p.sendMessage(P+"Минимум "+minimum()+", максимум "+MAX_BID+" алмазов.");return;}
UUID id=p.getUniqueId(),old=leader;int oldPrice=price;boolean same=id.equals(old);int cost=same?amount-price:amount;
if(diamonds(p)<cost){p.sendMessage(P+"Нужно ещё "+cost+" обычных алмазов в инвентаре.");return;}
if(!room(id,5)||(old!=null&&!same&&!room(old,4))){p.sendMessage(P+"Сначала заберите предметы из почты /auction claim.");return;}
transaction(p,"BID "+amount+" debit="+cost,()->debit(p,cost),()->{
if(old!=null&&!same)credit(old,oldPrice);
leader=id;leaderName=p.getName();price=amount;
end=Math.max(end,System.currentTimeMillis()+15000);
});
if(!frozen)Bukkit.broadcastMessage(P+p.getName()+" — "+price+" алм. §7(/auction)");
refreshMenus();
}
private void claim(Player p){
if(!ready(p)){p.sendMessage(P+"Почта пока недоступна.");return;}
List<ItemStack> m=mail.get(p.getUniqueId());
if(m==null||m.isEmpty()){p.sendMessage(P+"Почта пуста.");return;}
ItemStack reward=m.getFirst().clone();
boolean space=false;for(ItemStack s:p.getInventory().getStorageContents())if(s==null||s.getType().isAir()||(s.isSimilar(reward)&&s.getAmount()+reward.getAmount()<=s.getMaxStackSize())){space=true;break;}
if(!space){p.sendMessage(P+"Освободите слот инвентаря.");return;}
transaction(p,"CLAIM "+reward.getType()+" x"+reward.getAmount(),()->{if(!p.getInventory().addItem(reward.clone()).isEmpty())throw new IllegalStateException("Inventory unexpectedly full");},()->{m.removeFirst();if(m.isEmpty())mail.remove(p.getUniqueId());});
if(!frozen)p.sendMessage(P+"Получено. Осталось в почте: "+mail.getOrDefault(p.getUniqueId(),List.of()).size());
refreshMenus();
}
private void startNext(){
if(queue.isEmpty()){running=false;end=next=0;save();return;}
end=System.currentTimeMillis()+seconds*1000L;next=0;leader=null;leaderName="";price=0;
if(!save())return;
Lot l=queue.getFirst();Bukkit.broadcastMessage(P+"Новый лот: "+l.name+" · старт "+l.start+" алм. · /auction");
if(!l.secret)Bukkit.broadcastMessage(P+describe(l.chest));else Bukkit.broadcastMessage(P+"Содержимое скрыто до покупки.");
refreshMenus();
}
private void finish(){
Lot lot=queue.removeFirst();
if(leader!=null){mailbox(leader).add(lot.chest.clone());}
String result=leader==null?"Без ставок: "+lot.name:leaderName+" выиграл "+lot.name+" за "+price+" алм. Заберите через /auction claim.";
leader=null;leaderName="";price=0;end=0;running=!queue.isEmpty();next=running?System.currentTimeMillis()+15000:0;
if(save()){Bukkit.broadcastMessage(P+result);if(running)Bukkit.broadcastMessage(P+"Следующий лот через 15 секунд.");else Bukkit.broadcastMessage(P+"Аукцион завершён.");}
refreshMenus();
}
private void tick(){
if(frozen)return;long now=System.currentTimeMillis();
if(running){if(end>0&&now>=end)finish();else if(end==0&&now>=next)startNext();else refreshMenus();}
}
private void cancel(CommandSender sender){
if(leader!=null)credit(leader,price);
queue.clear();running=false;leader=null;leaderName="";price=0;end=next=0;
if(save())Bukkit.broadcastMessage(P+"Аукцион отменён. Ставки возвращены в почту /auction claim.");
refreshMenus();
}
private ItemStack named(Material mat,String name,String... lore){
ItemStack s=new ItemStack(mat);ItemMeta m=s.getItemMeta();m.displayName(Component.text(name));m.lore(Arrays.stream(lore).map(Component::text).toList());s.setItemMeta(m);return s;
}
private ItemStack chest(String name,List<ItemStack> items){
ItemStack s=new ItemStack(Material.CHEST);BlockStateMeta m=(BlockStateMeta)s.getItemMeta();Container c=(Container)m.getBlockState();
c.getSnapshotInventory().clear();for(int i=0;i<items.size();i++)c.getSnapshotInventory().setItem(i,items.get(i).clone());
m.setBlockState(c);m.displayName(Component.text(name));m.setMaxStackSize(1);s.setItemMeta(m);return s;
}
private List<ItemStack> contents(ItemStack chest){
if(chest.getItemMeta() instanceof BlockStateMeta m&&m.getBlockState() instanceof Container c)return Arrays.stream(c.getSnapshotInventory().getContents()).filter(Objects::nonNull).filter(i->!i.getType().isAir()).map(ItemStack::clone).toList();
return List.of();
}
private String itemName(ItemStack i){
return switch(i.getType()){
case DIAMOND_PICKAXE -> "Алмазная кирка";case DIAMOND_AXE -> "Алмазный топор";case DIAMOND_CHESTPLATE -> "Алмазный нагрудник";case DIAMOND_BOOTS -> "Алмазные ботинки";case ENCHANTED_BOOK -> "Зачарованная книга";case DIAMOND -> "Алмаз";case GOLDEN_APPLE -> "Золотое яблоко";case TOTEM_OF_UNDYING -> "Тотем бессмертия";case AMETHYST_SHARD -> "Аметист";case IRON_INGOT -> "Железо";case OBSIDIAN -> "Обсидиан";case LAPIS_LAZULI -> "Лазурит";case BOOKSHELF -> "Книжная полка";case ENDER_PEARL -> "Жемчуг Эндера";case GOLDEN_CARROT -> "Золотая морковь";case COOKED_BEEF -> "Стейк";case EMERALD -> "Изумруд";case GOLD_INGOT -> "Золото";default -> i.getType().name();};
}
private String enchantName(Enchantment e){return switch(e.getKey().getKey()){case "efficiency"->"Эффективность";case "unbreaking"->"Прочность";case "protection"->"Защита";case "feather_falling"->"Невесомость";case "mending"->"Починка";case "fortune"->"Удача";default->e.getKey().getKey();};}
private String line(ItemStack i){Map<Enchantment,Integer> ench=i.getItemMeta() instanceof EnchantmentStorageMeta m?m.getStoredEnchants():i.getEnchantments();String extra=ench.isEmpty()?"":" ("+String.join(", ",ench.entrySet().stream().map(e->enchantName(e.getKey())+" "+e.getValue()).toList())+")";return i.getAmount()+"× "+itemName(i)+extra;}
private String describe(ItemStack chest){return String.join("; ",contents(chest).stream().map(this::line).toList());}
private ItemStack enchanted(Material type,Enchantment e,int lvl,Enchantment e2,int lvl2){ItemStack s=new ItemStack(type);s.addEnchantment(e,lvl);if(e2!=null)s.addEnchantment(e2,lvl2);return s;}
private ItemStack book(Enchantment e,int lvl){ItemStack s=new ItemStack(Material.ENCHANTED_BOOK);EnchantmentStorageMeta m=(EnchantmentStorageMeta)s.getItemMeta();m.addStoredEnchant(e,lvl,true);s.setItemMeta(m);return s;}
private ItemStack item(Material m,int n){return new ItemStack(m,n);}
private Lot lot(String name,boolean hidden,int start,ItemStack... items){return new Lot(name,hidden,start,chest(name,List.of(items)));}
private void presets(){
List<Lot> pool=new ArrayList<>(List.of(
lot("Сундук шахтёра",false,3,enchanted(Material.DIAMOND_PICKAXE,Enchantment.EFFICIENCY,4,Enchantment.UNBREAKING,3),item(Material.IRON_INGOT,32),item(Material.OBSIDIAN,16),item(Material.AMETHYST_SHARD,24)),
lot("Сундук воина",false,3,enchanted(Material.DIAMOND_CHESTPLATE,Enchantment.PROTECTION,3,Enchantment.UNBREAKING,2),item(Material.TOTEM_OF_UNDYING,1),item(Material.GOLDEN_APPLE,3),item(Material.COOKED_BEEF,32)),
lot("Сундук зачарователя",false,3,book(Enchantment.MENDING,1),book(Enchantment.FORTUNE,3),item(Material.LAPIS_LAZULI,32),item(Material.BOOKSHELF,16)),
lot("Сундук путешественника",false,2,enchanted(Material.DIAMOND_BOOTS,Enchantment.FEATHER_FALLING,4,Enchantment.UNBREAKING,3),item(Material.ENDER_PEARL,16),item(Material.GOLDEN_CARROT,32),item(Material.GOLDEN_APPLE,2)),
lot("Сундук кузнеца",false,2,enchanted(Material.DIAMOND_AXE,Enchantment.EFFICIENCY,4,Enchantment.UNBREAKING,3),item(Material.IRON_INGOT,32),item(Material.GOLD_INGOT,12),item(Material.EMERALD,12))));
Collections.shuffle(pool,new Random());queue.add(pool.get(0));queue.add(pool.get(1));
Lot third=pool.get(2);queue.add(new Lot("Тайный сундук",true,2,chest("Тайный сундук",contents(third.chest))));
}
private void render(Inventory inv,Player p){
inv.clear();
if(running&&!queue.isEmpty()){
Lot l=queue.getFirst();List<String> lore=new ArrayList<>();
lore.add(l.secret?"Содержимое скрыто": "Содержимое:");
if(!l.secret)lore.addAll(contents(l.chest).stream().map(this::line).toList());
lore.add("Старт: "+l.start+" алм.");lore.add("Лидер: "+(leader==null?"нет":leaderName+" · "+price+" алм."));
lore.add(end>0?"Осталось: "+Math.max(0,(end-System.currentTimeMillis()+999)/1000)+" сек.":"Следующий лот скоро");
inv.setItem(13,named(Material.CHEST,l.name,lore.toArray(String[]::new)));
if(active()&&minimum()<=MAX_BID){inv.setItem(20,named(Material.EMERALD,"Ставка "+minimum()+" алм.","ЛКМ — минимальная ставка"));if(minimum()+4<=MAX_BID)inv.setItem(24,named(Material.DIAMOND,"Ставка "+(minimum()+4)+" алм.","ЛКМ — повысить на 5"));}
}else inv.setItem(13,named(Material.CHEST,"Нет активного аукциона"));
inv.setItem(4,named(Material.PAPER,"Ваши алмазы: "+diamonds(p),frozen?"Торги заморожены":"Ставка сразу резервируется","Перебитая ставка возвращается в почту"));
inv.setItem(22,named(Material.ENDER_CHEST,"Почта: "+mail.getOrDefault(p.getUniqueId(),List.of()).size(),"ЛКМ — забрать первый предмет / возврат"));
}
private void menu(Player p){Inventory inv=Bukkit.createInventory(new Menu(p.getUniqueId()),27,Component.text("Аукцион • Land of Losers"));render(inv,p);p.openInventory(inv);}
private void refreshMenus(){for(Player p:Bukkit.getOnlinePlayers()){Inventory inv=p.getOpenInventory().getTopInventory();if(inv.getHolder() instanceof Menu m&&m.owner.equals(p.getUniqueId()))render(inv,p);}}
@EventHandler(priority=EventPriority.HIGHEST)public void click(InventoryClickEvent e){
if(!(e.getView().getTopInventory().getHolder() instanceof Menu m))return;
e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!m.owner.equals(p.getUniqueId())||e.getRawSlot()<0||e.getRawSlot()>=27||e.getClick()!=ClickType.LEFT)return;
if(e.getRawSlot()==22){claim(p);return;}if(!active()||frozen)return;
int min=minimum();if(e.getRawSlot()==20)bid(p,min);else if(e.getRawSlot()==24)bid(p,min+4);
}
@EventHandler(priority=EventPriority.HIGHEST)public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
@Override public void onDisable(){for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu)p.closeInventory();}
@Override public boolean onCommand(CommandSender s,Command cmd,String label,String[] args){
String a=args.length==0?"":args[0].toLowerCase(Locale.ROOT);boolean admin=s.hasPermission("unifiedauction.admin");
if(a.equals("status")){s.sendMessage(P+"running="+running+" queue="+queue.size()+" bid="+price+" frozen="+frozen+" mail="+mail.size());return true;}
if(a.equals("help")){s.sendMessage(P+"/auction · /auction bid <алмазы> · /auction claim");if(admin)s.sendMessage(P+"/auction random [секунд] · /auction add <цена> <public|secret> (сундук в руке) · /auction start [секунд] · /auction cancel");return true;}
if(s instanceof Player p){
if(a.isEmpty()){menu(p);return true;}
if(a.equals("claim")){claim(p);return true;}
if(a.equals("bid")&&args.length==2){try{bid(p,Integer.parseInt(args[1]));}catch(NumberFormatException e){p.sendMessage(P+"Введите целое число.");}return true;}
}
if(!admin){s.sendMessage(P+"Нет прав. /auction help");return true;}
if(frozen){s.sendMessage(P+"Заморожено: проверьте pending.yml и данные игрока. Не удаляйте журнал без сверки.");return true;}
if(a.equals("cancel")){cancel(s);return true;}
if(running){s.sendMessage(P+"Аукцион уже идёт. /auction cancel");return true;}
if(a.equals("random")||a.equals("start")){
try{seconds=args.length>1?Integer.parseInt(args[1]):60;if(seconds<30||seconds>300)throw new NumberFormatException();}catch(NumberFormatException e){s.sendMessage(P+"Длительность 30–300 секунд.");return true;}
if(a.equals("random")){if(!queue.isEmpty()){s.sendMessage(P+"Очередь не пуста. /auction cancel");return true;}presets();}
if(queue.isEmpty()){s.sendMessage(P+"Нет лотов.");return true;}running=true;startNext();return true;
}
if(a.equals("add")&&s instanceof Player p&&args.length==3){
if(queue.size()>=MAX_QUEUE){s.sendMessage(P+"Очередь заполнена.");return true;}
int start;try{start=Integer.parseInt(args[1]);}catch(NumberFormatException e){return true;}
if(start<1||start>MAX_BID||!Set.of("public","secret").contains(args[2])){s.sendMessage(P+"Цена 1–256, public или secret.");return true;}
ItemStack held=p.getInventory().getItemInMainHand();if(held.getType()!=Material.CHEST||contents(held).isEmpty()){s.sendMessage(P+"Держите заполненный сундук.");return true;}
boolean secret=args[2].equals("secret");String title=secret?"Тайный сундук":"Сундук №"+(queue.size()+1);
queue.add(new Lot(title,secret,start,chest(title,contents(held))));if(save())s.sendMessage(P+"Добавлен шаблон лота (административная копия сундука).");return true;
}
s.sendMessage(P+"/auction help");return true;
}
@Override public List<String> onTabComplete(CommandSender s,Command cmd,String alias,String[] args){
if(args.length==1)return (s.hasPermission("unifiedauction.admin")?List.of("bid","claim","status","help","random","start","add","cancel"):List.of("bid","claim","status","help")).stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
if(args.length==3&&args[0].equals("add")&&s.hasPermission("unifiedauction.admin"))return List.of("public","secret");return List.of();
}
}
