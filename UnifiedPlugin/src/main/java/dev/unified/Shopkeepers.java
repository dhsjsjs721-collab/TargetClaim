package dev.unified;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.command.CommandSender;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;

public final class Shopkeepers implements Listener {
private final UnifiedPlugin host;
private final NamespacedKey token=new NamespacedKey("minecraftunified","shop_token");
private final NamespacedKey marker=new NamespacedKey("minecraftunified","shop_entity");
private final Map<UUID,Shop> shops=new LinkedHashMap<>();
private final Map<UUID,UUID> selected=new HashMap<>();
private final Map<UUID,Long> skinRequests=new HashMap<>();
private final File file;
private long tick;
private static final int OFFERS=9, BUFFER=27, MAX_PRICE=1728;
private static final class Shop {
UUID id,owner; Location home; String name="&6Торговец",skin="",model="classic",style="normal";
ItemStack[] stock=new ItemStack[OFFERS],money=new ItemStack[BUFFER];
ItemStack[][] currency=new ItemStack[2][OFFERS];
int[][] price=new int[2][OFFERS];int[] quantity=new int[OFFERS];
Shop(){Arrays.fill(quantity,1);for(int i=0;i<OFFERS;i++){currency[0][i]=new ItemStack(Material.DIAMOND);currency[1][i]=new ItemStack(Material.EMERALD);price[0][i]=2;price[1][i]=5;}}
Mannequin npc; Villager walker; Location chest; boolean returning; long deadline,nextTrip;
}
private static final class Menu implements InventoryHolder {
final UUID id; final int edit; final boolean owner; final int choose; Inventory inv;
Menu(UUID id,boolean owner,int edit,int choose){this.id=id;this.owner=owner;this.edit=edit;this.choose=choose;}
public Inventory getInventory(){return inv;}
}
Shopkeepers(UnifiedPlugin host){this.host=host;file=new File(host.getDataFolder(),"shops.yml");}
static ItemStack token(){ItemStack i=new ItemStack(Material.NAME_TAG);var m=i.getItemMeta();m.setDisplayName("§6§lЛичный торговец");m.setLore(List.of("§7ПКМ по блоку в своём привате","§7Личная лавка с автоматическим сбором выручки"));m.getPersistentDataContainer().set(new NamespacedKey("minecraftunified","shop_token"),PersistentDataType.BYTE,(byte)1);m.setItemModel(new NamespacedKey("minecraftunified","shop_token"));i.setItemMeta(m);return i;}
public static MerchantRecipe offer(){MerchantRecipe r=new MerchantRecipe(token(),1);r.setPriceMultiplier(0);r.setDemand(0);r.setSpecialPrice(0);r.addIngredient(new ItemStack(Material.DIAMOND,32));r.setExperienceReward(false);return r;}
void enable(){
YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
var section=y.getConfigurationSection("shops");
if(section!=null)for(String id:section.getKeys(false))try{
String p="shops."+id+".";Shop s=new Shop();s.id=UUID.fromString(id);s.owner=UUID.fromString(y.getString(p+"owner"));s.home=y.getLocation(p+"home");if(s.home==null)continue;
s.name=y.getString(p+"name","&6Торговец");s.skin=y.getString(p+"skin","");s.model=y.getString(p+"model","classic");s.style=y.getString(p+"style","normal");
for(int i=0;i<OFFERS;i++){s.stock[i]=y.getItemStack(p+"stock."+i);s.quantity[i]=Math.max(1,Math.min(64,y.getInt(p+"quantity."+i,1)));for(int c=0;c<2;c++){ItemStack chosen=y.getItemStack(p+"currency."+c+"."+i);if(!empty(chosen))s.currency[c][i]=chosen.asOne();s.price[c][i]=Math.max(0,Math.min(MAX_PRICE,y.getInt(p+"price."+c+"."+i,y.getInt(p+(c==0?"diamonds.":"emeralds.")+i,c==0?2:5))));}}
for(int i=0;i<BUFFER;i++)s.money[i]=y.getItemStack(p+"money."+i);
shops.put(s.id,s);
}catch(Exception ex){host.getLogger().warning("Shop record invalid: "+id+" "+ex.getMessage());}
Bukkit.getPluginManager().registerEvents(this,host);
Bukkit.getScheduler().runTaskTimer(host,this::update,20L,5L);
host.getLogger().info("Personal shops enabled: "+shops.size());
}
void disable(){for(Shop s:shops.values())removeEntities(s);}
private boolean save(){
try{YamlConfiguration y=new YamlConfiguration();for(Shop s:shops.values()){
String p="shops."+s.id+".";y.set(p+"owner",s.owner.toString());y.set(p+"home",s.home);y.set(p+"name",s.name);y.set(p+"skin",s.skin);y.set(p+"model",s.model);y.set(p+"style",s.style);
for(int i=0;i<OFFERS;i++){y.set(p+"stock."+i,s.stock[i]);y.set(p+"quantity."+i,s.quantity[i]);for(int c=0;c<2;c++){y.set(p+"currency."+c+"."+i,s.currency[c][i]);y.set(p+"price."+c+"."+i,s.price[c][i]);}}
for(int i=0;i<BUFFER;i++)y.set(p+"money."+i,s.money[i]);
}Path target=file.toPath(),temp=target.resolveSibling(file.getName()+".tmp");y.save(temp.toFile());try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}return true;
}catch(Exception ex){host.getLogger().severe("Shop save failed: "+ex);return false;}
}
private boolean owns(UUID owner,Location l){if(l!=null&&l.isWorldLoaded()&&l.getWorld().getName().equals(TestWorld.WORLD)){Player p=Bukkit.getPlayer(owner);return p!=null&&p.isOp();}var claim=host.claimModule();return claim!=null&&claim.ownsShopLocation(owner,l);}
private boolean loaded(Location l){return l!=null&&l.getWorld()!=null&&l.getWorld().isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4);}
private boolean near(Player p,Shop s){return s.npc!=null&&s.npc.isValid()&&p.getWorld().equals(s.npc.getWorld())&&p.getLocation().distanceSquared(s.npc.getLocation())<=36;}
private boolean empty(ItemStack i){return i==null||i.getType().isAir()||i.getAmount()<=0;}
private ItemStack[] copy(ItemStack[] a){ItemStack[] b=new ItemStack[a.length];for(int i=0;i<a.length;i++)if(!empty(a[i]))b[i]=a[i].clone();return b;}
private ItemStack[] insert(ItemStack[] input,ItemStack item){ItemStack[] a=copy(input);int remaining=item.getAmount();for(int i=0;i<a.length;i++)if(!empty(a[i])&&a[i].isSimilar(item)){int x=Math.min(remaining,Math.max(0,a[i].getMaxStackSize()-a[i].getAmount()));a[i].setAmount(a[i].getAmount()+x);remaining-=x;}
for(int i=0;i<a.length&&remaining>0;i++)if(empty(a[i])){int x=Math.min(remaining,item.getMaxStackSize());a[i]=item.clone();a[i].setAmount(x);remaining-=x;}return remaining==0?a:null;}
private ItemStack[] pay(ItemStack[] input,ItemStack currency,int price){ItemStack[] a=copy(input);ItemStack plain=currency.asOne();for(int i=0;i<a.length&&price>0;i++)if(!empty(a[i])&&a[i].isSimilar(plain)){int x=Math.min(price,a[i].getAmount());a[i].setAmount(a[i].getAmount()-x);price-=x;if(empty(a[i]))a[i]=null;}return price==0?a:null;}
private void say(Player p,String msg){p.sendMessage("§6Лавка §8» §r"+msg);}
private UUID entityId(Entity e){String s=e.getPersistentDataContainer().get(marker,PersistentDataType.STRING);if(s==null)return null;try{return UUID.fromString(s);}catch(Exception ignored){return null;}}
private void tag(LivingEntity e,Shop s){e.getPersistentDataContainer().set(marker,PersistentDataType.STRING,s.id.toString());e.setPersistent(false);e.setInvulnerable(true);e.setSilent(true);e.setCollidable(false);e.setRemoveWhenFarAway(false);e.setCanPickupItems(false);}
private Component title(Shop s){Component c=LegacyComponentSerializer.legacyAmpersand().deserialize(s.name);return c.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD,s.style.equals("bold")).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,s.style.equals("italic"));}
private void applyAppearance(Shop s){
if(s.npc==null||!s.npc.isValid())return;
s.npc.customName(title(s));s.npc.setCustomNameVisible(true);s.npc.setDescription(Component.text("Личная лавка"));
if(s.skin.isBlank()){s.npc.setProfile(Mannequin.defaultProfile());return;}
try{PlayerProfile profile=Bukkit.createProfile(s.id,"Merchant");var textures=profile.getTextures();textures.setSkin(URI.create(s.skin).toURL(),s.model.equals("slim")?PlayerTextures.SkinModel.SLIM:PlayerTextures.SkinModel.CLASSIC);profile.setTextures(textures);s.npc.setProfile(ResolvableProfile.resolvableProfile(profile));}catch(Exception ex){host.getLogger().warning("Shop skin invalid: "+s.id);}
}
private void spawn(Shop s){
if(!loaded(s.home)||s.npc!=null&&s.npc.isValid())return;
s.npc=s.home.getWorld().spawn(s.home,Mannequin.class,e->{tag(e,s);e.setImmovable(true);e.setGravity(false);});
applyAppearance(s);
}
private void removeEntities(Shop s){if(s.walker!=null)s.walker.remove();if(s.npc!=null)s.npc.remove();s.walker=null;s.npc=null;s.chest=null;s.returning=false;}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(PlayerInteractEvent e){
if(e.getHand()!=EquipmentSlot.HAND||!e.getAction().isRightClick()||e.getClickedBlock()==null)return;
ItemStack hand=e.getItem();if(empty(hand)||!hand.getItemMeta().getPersistentDataContainer().has(token,PersistentDataType.BYTE))return;
e.setCancelled(true);Player p=e.getPlayer();Location l=e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(.5,0,.5);l.setYaw(p.getLocation().getYaw()+180);
if(!owns(p.getUniqueId(),l)||!l.getBlock().isPassable()||!l.clone().add(0,1,0).getBlock().isPassable()||!l.clone().add(0,-1,0).getBlock().getType().isSolid()){say(p,"§cНужен свободный участок в своём привате.");return;}
if(shops.values().stream().filter(s->s.owner.equals(p.getUniqueId())&&s.home.getWorld().equals(l.getWorld())).count()>=3){say(p,"§cЛимит: три торговца.");return;}
for(Shop s:shops.values())if(s.home.getWorld().equals(l.getWorld())&&s.home.distanceSquared(l)<4){say(p,"§cЗдесь уже есть торговец.");return;}
Shop s=new Shop();s.id=UUID.randomUUID();s.owner=p.getUniqueId();s.home=l;shops.put(s.id,s);if(!save()){shops.remove(s.id);say(p,"§cОшибка сохранения.");return;}
hand.setAmount(hand.getAmount()-1);spawn(s);say(p,"§aЛавка создана. ПКМ — настройки, Shift+ПКМ — витрина.");
}
@EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEntityEvent e){
if(host.seasonLocked(e.getPlayer())){e.setCancelled(true);return;}
UUID id=entityId(e.getRightClicked());if(id==null)return;e.setCancelled(true);if(e.getHand()!=EquipmentSlot.HAND)return;
Shop s=shops.get(id);if(s==null||!near(e.getPlayer(),s))return;
if(s.walker!=null){say(e.getPlayer(),"§eТорговец переносит выручку.");return;}
if(!owns(s.owner,s.home)){say(e.getPlayer(),"§cЛавка приостановлена: приват утрачен.");return;}
Player p=e.getPlayer();boolean owner=s.owner.equals(p.getUniqueId());if(owner)selected.put(p.getUniqueId(),id);open(p,s,owner&&!p.isSneaking(),-1);
}
@EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageEvent e){if(entityId(e.getEntity())!=null)e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST) public void target(EntityTargetLivingEntityEvent e){if(e.getTarget()!=null&&entityId(e.getTarget())!=null)e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST) public void leash(PlayerLeashEntityEvent e){if(entityId(e.getEntity())!=null)e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST) public void portal(EntityPortalEvent e){if(entityId(e.getEntity())!=null)e.setCancelled(true);}
@EventHandler public void quit(PlayerQuitEvent e){selected.remove(e.getPlayer().getUniqueId());skinRequests.remove(e.getPlayer().getUniqueId());}
private ItemStack icon(Material m,String name,String... lore){ItemStack i=new ItemStack(m);var im=i.getItemMeta();im.setDisplayName(name);im.setLore(Arrays.asList(lore));i.setItemMeta(im);return i;}
private ItemStack preview(ItemStack stack,String... lore){if(empty(stack))return icon(Material.GRAY_STAINED_GLASS_PANE,"§7Пусто",lore);ItemStack i=stack.clone();var m=i.getItemMeta();List<String> list=m.hasLore()?new ArrayList<>(m.getLore()):new ArrayList<>();list.addAll(Arrays.asList(lore));m.setLore(list);i.setItemMeta(m);return i;}
private void open(Player p,Shop s,boolean owner,int edit){open(p,s,owner,edit,-1);}
private void open(Player p,Shop s,boolean owner,int edit,int choose){Menu menu=new Menu(s.id,owner,edit,choose);menu.inv=Bukkit.createInventory(menu,54,choose>=0?"Выбор предмета оплаты":!owner?"Товары":edit==-2?"Выручка":edit>=0?"Товар "+(edit+1):"Настройки торговца");fill(menu,s,p);p.openInventory(menu.inv);}
private String label(ItemStack i){return empty(i)?"—":i.getItemMeta().hasDisplayName()?i.getItemMeta().getDisplayName():i.getType().name().toLowerCase(Locale.ROOT);}
private ItemStack cost(Shop s,int c,int i){ItemStack item=s.currency[c][i];int price=s.price[c][i];return price==0?icon(Material.GRAY_DYE,"§7Оплата отключена"):preview(item.asOne(),"§6Цена: "+price+" × "+label(item),"§7За "+s.quantity[i]+" шт.");}
private void fill(Menu m,Shop s,Player p){Inventory inv=m.inv;inv.clear();
if(!m.owner){for(int i=0;i<OFFERS;i++){if(empty(s.stock[i]))continue;inv.setItem(i,preview(s.stock[i],"§7В наличии: "+s.stock[i].getAmount()+" шт.","§7За покупку: "+s.quantity[i]+" шт."));for(int c=0;c<2;c++)if(s.price[c][i]>0)inv.setItem(9+c*9+i,cost(s,c,i));}if(s.owner.equals(p.getUniqueId()))inv.setItem(49,icon(Material.COMPARATOR,"§eНастройки"));return;}
if(m.edit==-2){for(int i=0;i<BUFFER;i++)if(!empty(s.money[i]))inv.setItem(i,preview(s.money[i],"§7Нажми, чтобы забрать"));inv.setItem(49,icon(Material.ARROW,"§7Назад"));return;}
if(m.choose>=0){inv.setItem(11,icon(Material.DIAMOND,"§bАлмазы"));inv.setItem(13,icon(Material.EMERALD,"§aИзумруды"));inv.setItem(15,icon(Material.GOLD_INGOT,"§6Золото"));inv.setItem(31,icon(Material.BOOK,"§fЛюбой предмет","§7Нажми на предмет в своём инвентаре.","§7Он не будет изъят; сравнивается точная мета."));inv.setItem(49,icon(Material.ARROW,"§7Назад"));return;}
if(m.edit<0){for(int i=0;i<OFFERS;i++)inv.setItem(i,empty(s.stock[i])?icon(Material.LIME_STAINED_GLASS_PANE,"§a+ Добавить товар "+(i+1),"§7Нажми, затем выбери предмет в инвентаре"):preview(s.stock[i],"§7Настроить товар "+(i+1),"§7В наличии: "+s.stock[i].getAmount(),"§7Оплата: "+s.price[0][i]+" / "+s.price[1][i]));inv.setItem(19,icon(Material.NAME_TAG,"§eИмя и цвет","§7Открыть поле ввода"));inv.setItem(21,icon(Material.PLAYER_HEAD,"§eСкин","§7Ник, ссылка, self, reset"));inv.setItem(23,icon(Material.WRITABLE_BOOK,"§eСтиль: "+s.style,"§7Нажми, чтобы переключить"));inv.setItem(25,icon(Material.CHEST,"§eВыручка","§7Забрать оплату"));inv.setItem(49,icon(Material.EMERALD,"§aВитрина","§7Посмотреть как покупатель"));return;}
int i=m.edit;inv.setItem(4,empty(s.stock[i])?icon(Material.LIME_STAINED_GLASS_PANE,"§aДобавить товар","§7Нажми на предмет в своём инвентаре"):preview(s.stock[i],"§7Нажми здесь, чтобы забрать весь остаток","§7Нажми на предмет в инвентаре, чтобы пополнить"));
inv.setItem(20,cost(s,0,i));inv.setItem(24,cost(s,1,i));inv.setItem(29,icon(Material.HOPPER,"§eВыбрать валюту 1","§7Любой предмет · ПКМ выключает"));inv.setItem(33,icon(Material.HOPPER,"§eВыбрать валюту 2","§7Любой предмет · ПКМ выключает"));inv.setItem(31,icon(Material.PAPER,"§eКоличество и цена","§7За покупку: "+s.quantity[i],"§7Введи точные числа в окне"));inv.setItem(49,icon(Material.ARROW,"§7Назад"));
}
private boolean canEdit(Player p,Shop s){return p.isOnline()&&!host.seasonLocked(p)&&s!=null&&s.owner.equals(p.getUniqueId())&&near(p,s)&&s.walker==null&&owns(s.owner,s.home);}
private void later(Player p,Runnable task){Bukkit.getScheduler().runTask(host,()->{if(p.isOnline())task.run();});}
private void dialog(Player p,Shop s,int index,String field){
if(!canEdit(p,s))return;
List<DialogInput> inputs;
if(field.equals("prices"))inputs=List.of(DialogInput.text("qty",Component.text("Товар за покупку (1–64)")).initial(""+s.quantity[index]).maxLength(2).width(200).build(),DialogInput.text("p0",Component.text("Цена 1 · "+label(s.currency[0][index])+" (0 = выкл.)")).initial(""+s.price[0][index]).maxLength(4).width(200).build(),DialogInput.text("p1",Component.text("Цена 2 · "+label(s.currency[1][index])+" (0 = выкл.)")).initial(""+s.price[1][index]).maxLength(4).width(200).build());
else inputs=List.of(DialogInput.text("value",Component.text(field.equals("name")?"Название (&-цвета)":"Ник, URL, self или reset")).initial(field.equals("name")?s.name:"self").maxLength(field.equals("name")?64:1800).width(240).build());
p.closeInventory();
p.showDialog(Dialog.create(builder->builder.empty().base(DialogBase.builder(Component.text(field.equals("prices")?"Количество и стоимость":"Настройки торговца")).inputs(inputs).build()).type(DialogType.confirmation(
ActionButton.create(Component.text("Сохранить"),null,100,DialogAction.customClick((view,audience)->{if(!(audience instanceof Player actor)||!actor.getUniqueId().equals(p.getUniqueId()))return;
String qty=view.getText("qty"),price0=view.getText("p0"),price1=view.getText("p1"),value=view.getText("value");
later(actor,()->{if(!canEdit(actor,s))return;if(field.equals("prices")){try{int q=Integer.parseInt(qty),a=Integer.parseInt(price0),b=Integer.parseInt(price1);if(q<1||q>64||a<0||b<0||a>MAX_PRICE||b>MAX_PRICE||!empty(s.stock[index])&&q>s.stock[index].getMaxStackSize())throw new IllegalArgumentException();int oldQ=s.quantity[index],oldA=s.price[0][index],oldB=s.price[1][index];s.quantity[index]=q;s.price[0][index]=a;s.price[1][index]=b;if(!save()){s.quantity[index]=oldQ;s.price[0][index]=oldA;s.price[1][index]=oldB;say(actor,"§cОшибка сохранения.");}}catch(Exception ex){say(actor,"§cКоличество 1–64, цена 0–"+MAX_PRICE+".");}}
else if(value!=null){selected.put(actor.getUniqueId(),s.id);command(actor,new String[]{field,value});}
open(actor,s,true,index);refresh(s);});
},ClickCallback.Options.builder().uses(1).build())),
ActionButton.create(Component.text("Отмена"),null,100,DialogAction.customClick((view,audience)->{if(audience instanceof Player actor)later(actor,()->{if(canEdit(actor,s))open(actor,s,true,index);});},ClickCallback.Options.builder().uses(1).build()))))));
}
private void refresh(Shop s){for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu m&&m.id.equals(s.id))fill(m,s,p);}
@EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
private void choose(Player p,Shop s,int c,int i,ItemStack item){if(empty(item))return;ItemStack old=s.currency[c][i];int oldPrice=s.price[c][i];s.currency[c][i]=item.asOne();if(oldPrice==0)s.price[c][i]=1;if(!save()){s.currency[c][i]=old;s.price[c][i]=oldPrice;say(p,"§cОшибка сохранения.");}}
private void deposit(Player p,Shop s,int i,int slot,boolean one){ItemStack item=p.getInventory().getItem(slot);if(empty(item))return;ItemStack old=s.stock[i];if(!empty(old)&&!old.isSimilar(item)){say(p,"§cСначала забери другой товар.");return;}int count=Math.min(one?1:item.getAmount(),item.getMaxStackSize()-(empty(old)?0:old.getAmount()));if(count<=0){say(p,"§cЭтот слот заполнен.");return;}ItemStack next=item.clone();next.setAmount(count+(empty(old)?0:old.getAmount()));int oldQty=s.quantity[i];s.quantity[i]=Math.min(oldQty,item.getMaxStackSize());s.stock[i]=next;if(save()){ItemStack rest=item.clone();rest.setAmount(item.getAmount()-count);p.getInventory().setItem(slot,empty(rest)?null:rest);}else{s.stock[i]=old;s.quantity[i]=oldQty;say(p,"§cОшибка сохранения.");}}
@EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){
if(!(e.getView().getTopInventory().getHolder() instanceof Menu m)||!(e.getWhoClicked() instanceof Player p))return;
e.setCancelled(true);if(host.seasonLocked(p)||e.getRawSlot()<0||!(e.getClick()==ClickType.LEFT||e.getClick()==ClickType.RIGHT||e.getClick()==ClickType.SHIFT_LEFT||e.getClick()==ClickType.SHIFT_RIGHT))return;
Shop s=shops.get(m.id);if(s==null||!near(p,s)||s.walker!=null||!owns(s.owner,s.home)){later(p,p::closeInventory);return;}
int slot=e.getRawSlot();
if(slot>=54){if(!m.owner||m.edit<0||!canEdit(p,s)||e.getClickedInventory()!=p.getInventory())return;int index=e.getSlot();if(index<0||index>=36)return;ItemStack item=e.getCurrentItem();if(m.choose>=0){if(!empty(item)){choose(p,s,m.choose,m.edit,item);later(p,()->open(p,s,true,m.edit));}}else{deposit(p,s,m.edit,index,e.isRightClick());refresh(s);}return;}
if(!m.owner){if(slot==49&&s.owner.equals(p.getUniqueId())){later(p,()->open(p,s,true,-1));return;}if(slot>=9&&slot<27){int c=(slot-9)/9,i=(slot-9)%9;buy(p,s,i,s.currency[c][i],s.price[c][i]);refresh(s);}return;}
if(!canEdit(p,s))return;
if(slot==49){later(p,()->{if(m.choose>=0)open(p,s,true,m.edit);else if(m.edit==-1)open(p,s,false,-1);else open(p,s,true,-1);});return;}
if(m.edit==-2){if(slot<BUFFER){withdraw(p,s,s.money,slot);refresh(s);}return;}
if(m.choose>=0){Material type=slot==11?Material.DIAMOND:slot==13?Material.EMERALD:slot==15?Material.GOLD_INGOT:null;if(type!=null){choose(p,s,m.choose,m.edit,new ItemStack(type));later(p,()->open(p,s,true,m.edit));}return;}
if(m.edit<0){if(slot<OFFERS){later(p,()->open(p,s,true,slot));return;}if(slot==19||slot==21){later(p,()->dialog(p,s,-1,slot==19?"name":"skin"));return;}if(slot==23){String old=s.style;s.style=switch(old){case "normal"->"bold";case "bold"->"italic";default->"normal";};if(save())applyAppearance(s);else s.style=old;refresh(s);}if(slot==25)later(p,()->open(p,s,true,-2));return;}
int i=m.edit;
if(slot==4){withdraw(p,s,s.stock,i);refresh(s);return;}
if(slot==29||slot==33){int c=slot==29?0:1;if(e.isRightClick()){int old=s.price[c][i];s.price[c][i]=0;if(!save())s.price[c][i]=old;refresh(s);}else later(p,()->open(p,s,true,i,c));return;}
if(slot==20||slot==24||slot==31)later(p,()->dialog(p,s,i,"prices"));
}
private void withdraw(Player p,Shop s,ItemStack[] source,int i){if(empty(source[i]))return;ItemStack old=source[i];ItemStack[] next=insert(p.getInventory().getStorageContents(),old);if(next==null){say(p,"§cНет места в инвентаре.");return;}source[i]=null;if(!save()){source[i]=old;return;}p.getInventory().setStorageContents(next);}
private void buy(Player p,Shop s,int i,ItemStack currency,int price){
if(s.owner.equals(p.getUniqueId())){say(p,"§7Это твоя витрина.");return;}
if(p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE){say(p,"§cПокупка доступна только в выживании.");return;}
if(price<=0||empty(s.stock[i])||s.stock[i].getAmount()<s.quantity[i]){say(p,"§cНет товара или цена отключена.");return;}
ItemStack payment=currency.clone();payment.setAmount(price);ItemStack[] nextMoney=insert(s.money,payment);if(nextMoney==null){say(p,"§cВыручка заполнена, ожидайте разгрузки.");s.nextTrip=0;return;}
ItemStack[] next=pay(p.getInventory().getStorageContents(),currency,price);if(next==null){say(p,"§cНе хватает оплаты.");return;}
ItemStack product=s.stock[i].clone();product.setAmount(s.quantity[i]);next=insert(next,product);if(next==null){say(p,"§cНет места в инвентаре.");return;}
ItemStack old=s.stock[i];ItemStack[] oldMoney=s.money;s.stock[i]=old.clone();s.stock[i].setAmount(old.getAmount()-product.getAmount());if(empty(s.stock[i]))s.stock[i]=null;s.money=nextMoney;
if(!save()){s.stock[i]=old;s.money=oldMoney;say(p,"§cОшибка сохранения.");return;}
p.getInventory().setStorageContents(next);say(p,"§aПокупка завершена.");
}
private Chest nearest(Shop s){World w=s.home.getWorld();Chest best=null;double distance=35*35;
int x=s.home.getBlockX(),z=s.home.getBlockZ();for(int cx=(x-35)>>4;cx<=(x+35)>>4;cx++)for(int cz=(z-35)>>4;cz<=(z+35)>>4;cz++){
if(!w.isChunkLoaded(cx,cz))continue;for(var state:w.getChunkAt(cx,cz).getTileEntities())if(state instanceof Chest c&&owns(s.owner,c.getLocation())){
double d=s.home.distanceSquared(c.getLocation().add(.5,.5,.5));if(d>distance)continue;for(ItemStack i:s.money)if(!empty(i)&&insert(c.getBlockInventory().getStorageContents(),i)!=null){distance=d;best=c;break;}
}}
return best;}
private Location approach(Chest c,Location from){Location best=null;double d=Double.MAX_VALUE;for(var face:List.of(org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.SOUTH,org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.WEST)){Block b=c.getBlock().getRelative(face);Location l=b.getLocation().add(.5,0,.5);if(!loaded(l)||!b.isPassable()||!b.getRelative(org.bukkit.block.BlockFace.UP).isPassable()||!b.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid())continue;double x=l.distanceSquared(from);if(x<d){d=x;best=l;}}return best;}
private boolean atChest(Chest c,Location from){Location a=approach(c,from);return a!=null&&a.distanceSquared(from)<1.0;}
private void unload(Shop s,Chest c){if(!owns(s.owner,c.getLocation()))return;Inventory inv=c.getBlockInventory();ItemStack[] next=inv.getStorageContents(),old=copy(s.money);boolean changed=false;for(int i=0;i<BUFFER;i++)if(!empty(s.money[i])){ItemStack[] result=insert(next,s.money[i]);if(result!=null){next=result;s.money[i]=null;changed=true;}}
if(changed){if(save())inv.setStorageContents(next);else s.money=old;}}
private void finishTrip(Shop s){if(s.walker!=null)s.walker.remove();s.walker=null;s.chest=null;s.returning=false;if(s.npc!=null&&s.npc.isValid()&&loaded(s.home))s.npc.teleport(s.home);s.nextTrip=tick+200;}
private void update(){tick+=5;for(Shop s:shops.values()){
if(!loaded(s.home)){removeEntities(s);continue;}
spawn(s);if(s.npc==null||!s.npc.isValid())continue;
if(!owns(s.owner,s.home)){finishTrip(s);continue;}
if(s.walker!=null){
if(!s.walker.isValid()||tick>s.deadline){finishTrip(s);continue;}
Location current=s.walker.getLocation();if(!loaded(current)||!s.npc.teleport(current)){finishTrip(s);continue;}
if(s.returning){if(current.distanceSquared(s.home)<2.25){finishTrip(s);continue;}if(tick%20==0)s.walker.getPathfinder().moveTo(s.home,1.0);continue;}
if(s.chest==null||!loaded(s.chest)||!(s.chest.getBlock().getState() instanceof Chest c)||!owns(s.owner,c.getLocation())){finishTrip(s);continue;}
if(current.distanceSquared(c.getLocation().add(.5,.5,.5))<6.25&&atChest(c,current)){unload(s,c);s.returning=true;s.deadline=tick+300;s.walker.getPathfinder().moveTo(s.home,1.0);continue;}
if(tick%20==0){Location dest=approach(c,current);if(dest==null){finishTrip(s);continue;}s.walker.getPathfinder().moveTo(dest,1.0);}continue;}
if(tick<s.nextTrip||Arrays.stream(s.money).allMatch(this::empty))continue;s.nextTrip=tick+200;
Chest c=nearest(s);if(c==null)continue;Location dest=approach(c,s.home);if(dest==null)continue;
if(atChest(c,s.home)){unload(s,c);continue;}
s.walker=s.home.getWorld().spawn(s.home,Villager.class,e->{tag(e,s);e.setInvisible(true);e.setGravity(true);e.setAI(true);e.setAdult();});s.chest=c.getLocation();s.returning=false;s.deadline=tick+300;
if(!s.walker.getPathfinder().moveTo(dest,1.0))finishTrip(s);
}}
@EventHandler public void unloadChunk(ChunkUnloadEvent e){for(Entity entity:e.getChunk().getEntities()){UUID id=entityId(entity);if(id==null)continue;Shop s=shops.get(id);if(s!=null)removeEntities(s);else entity.remove();}}
private String texture(String input){try{URI u=URI.create(input);if("https".equalsIgnoreCase(u.getScheme())&&"textures.minecraft.net".equalsIgnoreCase(u.getHost())&&u.getUserInfo()==null&&u.getPort()==-1&&u.getPath().matches("/texture/[a-fA-F0-9]{32,128}"))return "https://textures.minecraft.net"+u.getPath();}catch(Exception ignored){}return null;}
private void setSkin(Player p,Shop s,String url,String model){String old=s.skin,oldModel=s.model;s.skin=url;s.model=model;if(save()){applyAppearance(s);say(p,"§aСкин сохранён.");}else{s.skin=old;s.model=oldModel;say(p,"§cОшибка сохранения.");}}
boolean command(CommandSender sender,String[] a){
if(!(sender instanceof Player p)){sender.sendMessage("Только в игре.");return true;}
if(a.length>0&&a[0].equalsIgnoreCase("give")&&p.hasPermission("targetclaim.admin")){for(ItemStack rest:p.getInventory().addItem(token()).values())p.getWorld().dropItemNaturally(p.getLocation(),rest);return true;}
Shop s=shops.get(selected.get(p.getUniqueId()));if(s==null||!s.owner.equals(p.getUniqueId())||!near(p,s)){say(p,"§eShift+ПКМ по своему торговцу, затем /u shop name|skin|style.");return true;}
if(a.length==0){open(p,s,true,-1);return true;}
switch(a[0].toLowerCase(Locale.ROOT)){
case "name" -> {String name=String.join(" ",Arrays.copyOfRange(a,1,a.length)).replaceAll("[\\p{Cntrl}§]","").strip();if(name.isBlank()||name.length()>64){say(p,"§cИмя: 1–64 символа, &-цвета.");return true;}String old=s.name;s.name=name;if(save())applyAppearance(s);else s.name=old;}
case "style" -> {if(a.length!=2||!Set.of("normal","bold","italic").contains(a[1])){say(p,"§enormal | bold | italic");return true;}String old=s.style;s.style=a[1];if(save())applyAppearance(s);else s.style=old;}
case "skin" -> {if(a.length<2){say(p,"§e/u shop skin self|reset|ник|ссылка [slim|classic]");return true;}String src=a[1],model=a.length>2&&a[2].equalsIgnoreCase("slim")?"slim":"classic";if(src.equalsIgnoreCase("reset")){setSkin(p,s,"",model);return true;}if(src.equalsIgnoreCase("self")){var t=p.getPlayerProfile().getTextures();if(t.getSkin()!=null)setSkin(p,s,t.getSkin().toString().replace("http://","https://"),t.getSkinModel()==PlayerTextures.SkinModel.SLIM?"slim":"classic");else say(p,"§cНет доступной текстуры.");return true;}
String url=texture(src);if(url!=null){setSkin(p,s,url,model);return true;}if(!src.matches("[A-Za-z0-9_]{1,16}")){say(p,"§cНужен ник Minecraft или ссылка textures.minecraft.net.");return true;}
long now=System.currentTimeMillis();if(now-skinRequests.getOrDefault(p.getUniqueId(),0L)<15000){say(p,"§cПодожди 15 секунд.");return true;}skinRequests.put(p.getUniqueId(),now);
Bukkit.createProfile(src).update().orTimeout(15,java.util.concurrent.TimeUnit.SECONDS).whenComplete((profile,error)->{if(!host.isEnabled())return;Bukkit.getScheduler().runTask(host,()->{if(!p.isOnline()||!Objects.equals(skinRequests.get(p.getUniqueId()),now)||!shops.containsKey(s.id))return;if(error!=null||profile.getTextures().getSkin()==null){say(p,"§cСкин не найден.");return;}var t=profile.getTextures();setSkin(p,s,t.getSkin().toString().replace("http://","https://"),t.getSkinModel()==PlayerTextures.SkinModel.SLIM?"slim":"classic");});});}
default -> say(p,"§e/u shop name | skin | style");
}return true;
}
}
