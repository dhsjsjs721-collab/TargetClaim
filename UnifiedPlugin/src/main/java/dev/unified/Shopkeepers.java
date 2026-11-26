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
private static final int OFFERS=9, BUFFER=27, CAPACITY=5, MAX_PRICE=320;
private static final class Shop {
UUID id,owner; Location home; String name="&6Торговец",skin="",model="classic",style="normal";
ItemStack[] stock=new ItemStack[OFFERS],money=new ItemStack[BUFFER],product=new ItemStack[OFFERS];
ItemStack[][] currency=new ItemStack[2][OFFERS];
int[][] price=new int[2][OFFERS];int[] quantity=new int[OFFERS];
Shop(){Arrays.fill(quantity,1);for(int i=0;i<OFFERS;i++){currency[0][i]=new ItemStack(Material.DIAMOND);currency[1][i]=new ItemStack(Material.EMERALD);price[0][i]=0;price[1][i]=0;}}
Mannequin npc; Villager walker; Location chest; boolean returning; long deadline,nextTrip;
}
private static final class Catalog implements InventoryHolder {
final UUID shop,owner;final int offer,kind,page;final String query,filter;final List<ItemStack> items;Inventory inv;
Catalog(UUID shop,UUID owner,int offer,int kind,int page,String query,String filter,List<ItemStack> items){this.shop=shop;this.owner=owner;this.offer=offer;this.kind=kind;this.page=page;this.query=query;this.filter=filter;this.items=items;}public Inventory getInventory(){return inv;}
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
for(int i=0;i<OFFERS;i++){s.stock[i]=y.getItemStack(p+"stock."+i);s.product[i]=y.getItemStack(p+"product."+i);if(empty(s.product[i])&&!empty(s.stock[i]))s.product[i]=s.stock[i].asOne();s.quantity[i]=Math.max(1,Math.min(64,y.getInt(p+"quantity."+i,1)));for(int c=0;c<2;c++){ItemStack chosen=y.getItemStack(p+"currency."+c+"."+i);if(!empty(chosen))s.currency[c][i]=chosen.asOne();s.price[c][i]=Math.max(0,Math.min(1728,y.getInt(p+"price."+c+"."+i,y.getInt(p+(c==0?"diamonds.":"emeralds.")+i,empty(s.product[i])?0:c==0?2:5))));}}
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
for(int i=0;i<OFFERS;i++){y.set(p+"stock."+i,s.stock[i]);y.set(p+"product."+i,s.product[i]);y.set(p+"quantity."+i,s.quantity[i]);for(int c=0;c<2;c++){y.set(p+"currency."+c+"."+i,s.currency[c][i]);y.set(p+"price."+c+"."+i,s.price[c][i]);}}
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
private void open(Player p,Shop s,boolean owner,int edit){if(edit>=0)edit=-1;Menu menu=new Menu(s.id,owner,edit,-1);int size=edit==-2?54:36;menu.inv=Bukkit.createInventory(menu,size,!owner?"Лавка · купить":edit==-2?"Выручка":edit==-3?"Старые товары":"Моя лавка · 5 слотов");fill(menu,s,p);p.openInventory(menu.inv);}
private String label(ItemStack i){return empty(i)?"—":i.getItemMeta().hasDisplayName()?i.getItemMeta().getDisplayName():i.getType().name().toLowerCase(Locale.ROOT);}
private ItemStack cost(Shop s,int c,int i){ItemStack item=s.currency[c][i];int price=s.price[c][i];if(price==0)return icon(Material.GRAY_DYE,"§7Цена не задана","§7Нажми для выбора валюты");ItemStack result=item.clone();result.setAmount(Math.min(price,result.getMaxStackSize()));return preview(result,"§6Цена: "+price+" × "+label(item),"§7За "+s.quantity[i]+" шт.");}
private void fill(Menu m,Shop s,Player p){
Inventory inv=m.inv;inv.clear();
if(m.edit==-2){for(int i=0;i<BUFFER;i++)if(!empty(s.money[i]))inv.setItem(i,preview(s.money[i],"§7Нажми, чтобы забрать"));inv.setItem(49,icon(Material.ARROW,"§7Назад"));return;}
if(m.edit==-3){for(int i=CAPACITY;i<OFFERS;i++)if(!empty(s.stock[i]))inv.setItem(i-CAPACITY+2,preview(s.stock[i],"§7Старый товар · нажми, чтобы забрать"));inv.setItem(31,icon(Material.ARROW,"§7Назад"));return;}
for(int i=0;i<CAPACITY;i++){
int column=i+2;ItemStack product=s.stock[i];
if(m.owner){
ItemStack shown=empty(product)?s.product[i]:product;
inv.setItem(column,empty(shown)?icon(Material.LIME_STAINED_GLASS_PANE,"§a+ Положить товар","§7Shift+ЛКМ по предмету в инвентаре","§7Или возьми предмет курсором и нажми сюда","§7ПКМ без предмета — каталог"):preview(shown,"§7Остаток: "+(empty(product)?0:product.getAmount()),"§7Нажми с предметом — пополнить","§7ЛКМ пустой рукой — забрать остаток","§7ПКМ пустой рукой — выбрать товар"));
inv.setItem(9+column,preview(cost(s,0,i),"§eЛКМ — выбрать валюту","§eПКМ — изменить число","§7Можно нажать с валютой на курсоре","§7Shift+ПКМ — отключить цену"));
inv.setItem(18+column,s.price[1][i]>0?preview(cost(s,1,i),"§eДоп. цена · ЛКМ валюта, ПКМ число","§7Shift+ПКМ — отключить"):icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE,"§7+ Вторая цена (необязательно)","§7ЛКМ — выбрать валюту"));
}else if(!empty(product)){
inv.setItem(column,preview(product,"§7В наличии: "+product.getAmount(),"§7За покупку: "+s.quantity[i]+" шт.",s.price[0][i]>0&&s.price[1][i]>0?"§eВыбери одну из цен ниже":"§eНажми — купить"));
for(int c=0;c<2;c++)if(s.price[c][i]>0)inv.setItem(9+c*9+column,cost(s,c,i));
}}
if(m.owner){
inv.setItem(27,icon(Material.NAME_TAG,"§eИмя"));inv.setItem(28,icon(Material.PLAYER_HEAD,"§eСкин"));inv.setItem(29,icon(Material.WRITABLE_BOOK,"§eСтиль: "+s.style));
inv.setItem(31,icon(Material.CHEST,"§6Забрать выручку"));if(Arrays.stream(s.stock,CAPACITY,OFFERS).anyMatch(i->!empty(i)))inv.setItem(32,icon(Material.BARREL,"§eСтарые товары","§7Забрать из прежних слотов"));
inv.setItem(35,icon(Material.EMERALD,"§aПосмотреть витрину"));
}else if(s.owner.equals(p.getUniqueId()))inv.setItem(35,icon(Material.COMPARATOR,"§eНастройки"));
}
private boolean canEdit(Player p,Shop s){return p.isOnline()&&!host.seasonLocked(p)&&s!=null&&s.owner.equals(p.getUniqueId())&&near(p,s)&&s.walker==null&&owns(s.owner,s.home);}
private void later(Player p,Runnable task){Bukkit.getScheduler().runTask(host,()->{if(p.isOnline())task.run();});}
private void dialog(Player p,Shop s,int index,String field){
if(!canEdit(p,s))return;
boolean pricing=field.startsWith("price");int c=field.equals("price1")?1:0;
List<DialogInput> inputs;
if(pricing)inputs=List.of(DialogInput.text("qty",Component.text("Количество товара за покупку")).initial(""+s.quantity[index]).maxLength(2).width(200).build(),DialogInput.text("price",Component.text("Цена · "+label(s.currency[c][index])+" (0 = выкл.)")).initial(""+Math.max(1,s.price[c][index])).maxLength(4).width(200).build());
else inputs=List.of(DialogInput.text("value",Component.text(field.equals("name")?"Название (&-цвета)":"Ник, ссылка, self или reset")).initial(field.equals("name")?s.name:"self").maxLength(field.equals("name")?64:1800).width(240).build());
p.closeInventory();
p.showDialog(Dialog.create(builder->builder.empty().base(DialogBase.builder(Component.text(pricing?"Цена и количество":"Настройки торговца")).inputs(inputs).build()).type(DialogType.confirmation(
ActionButton.create(Component.text("Сохранить"),null,100,DialogAction.customClick((view,audience)->{if(!(audience instanceof Player actor)||!actor.getUniqueId().equals(p.getUniqueId()))return;
String qty=view.getText("qty"),price=view.getText("price"),value=view.getText("value");
later(actor,()->{if(!canEdit(actor,s))return;if(pricing){try{int q=Integer.parseInt(qty),a=Integer.parseInt(price);if(q<1||q>64||a<0||a>Math.min(MAX_PRICE,5*s.currency[c][index].getMaxStackSize())||!empty(s.product[index])&&q>s.product[index].getMaxStackSize())throw new IllegalArgumentException();int oldQ=s.quantity[index],old=s.price[c][index];s.quantity[index]=q;s.price[c][index]=a;if(!save()){s.quantity[index]=oldQ;s.price[c][index]=old;say(actor,"§cОшибка сохранения.");}}catch(Exception ex){say(actor,"§cНедопустимое количество или цена (не больше 5 стаков).");open(actor,s,true,-1);return;}}
else if(value!=null){selected.put(actor.getUniqueId(),s.id);command(actor,new String[]{field,value});}open(actor,s,true,-1);refresh(s);});
},ClickCallback.Options.builder().uses(1).build())),
ActionButton.create(Component.text("Отмена"),null,100,DialogAction.customClick((view,audience)->{if(audience instanceof Player actor&&actor.getUniqueId().equals(p.getUniqueId()))later(actor,()->{if(canEdit(actor,s))open(actor,s,true,-1);});},ClickCallback.Options.builder().uses(1).build()))))));
}
private void refresh(Shop s){for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu m&&m.id.equals(s.id))fill(m,s,p);}
@EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu||e.getView().getTopInventory().getHolder() instanceof Catalog)e.setCancelled(true);}
private void choose(Player p,Shop s,int c,int i,ItemStack item){if(empty(item))return;ItemStack old=s.currency[c][i];int oldPrice=s.price[c][i];s.currency[c][i]=item.asOne();if(oldPrice==0||!old.isSimilar(item))s.price[c][i]=0;if(!save()){s.currency[c][i]=old;s.price[c][i]=oldPrice;say(p,"§cОшибка сохранения.");}}
private int put(Shop s,int i,ItemStack item,int requested){
if(i<0||i>=CAPACITY||empty(item))return 0;
ItemStack old=s.stock[i],template=s.product[i];if(!empty(old)&&!old.isSimilar(item))return 0;
int count=Math.min(requested,Math.min(item.getAmount(),item.getMaxStackSize()-(empty(old)?0:old.getAmount())));if(count<=0)return 0;
int oldQty=s.quantity[i],oldA=s.price[0][i],oldB=s.price[1][i];
if(empty(template)||!template.isSimilar(item)){s.product[i]=item.asOne();s.price[0][i]=0;s.price[1][i]=0;s.quantity[i]=1;}
ItemStack next=item.clone();next.setAmount(count+(empty(old)?0:old.getAmount()));s.stock[i]=next;s.quantity[i]=Math.min(s.quantity[i],item.getMaxStackSize());
if(!save()){s.stock[i]=old;s.product[i]=template;s.quantity[i]=oldQty;s.price[0][i]=oldA;s.price[1][i]=oldB;return 0;}return count;
}
private int quickSlot(Shop s,ItemStack item){for(int i=0;i<CAPACITY;i++)if(!empty(s.stock[i])&&s.stock[i].isSimilar(item)&&s.stock[i].getAmount()<item.getMaxStackSize())return i;for(int i=0;i<CAPACITY;i++)if(empty(s.stock[i])&&!empty(s.product[i])&&s.product[i].isSimilar(item))return i;for(int i=0;i<CAPACITY;i++)if(empty(s.stock[i])&&empty(s.product[i]))return i;return -1;}
private ItemStack remainder(ItemStack item,int amount){if(amount>=item.getAmount())return new ItemStack(Material.AIR);ItemStack rest=item.clone();rest.setAmount(item.getAmount()-amount);return rest;}
@EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){
if(!(e.getView().getTopInventory().getHolder() instanceof Menu m)||!(e.getWhoClicked() instanceof Player p))return;
Shop s=shops.get(m.id);int slot=e.getRawSlot(),size=m.inv.getSize();boolean regular=e.getClick()==ClickType.LEFT||e.getClick()==ClickType.RIGHT,shift=e.getClick()==ClickType.SHIFT_LEFT||e.getClick()==ClickType.SHIFT_RIGHT;
if(host.seasonLocked(p)||s==null||!near(p,s)||s.walker!=null||!owns(s.owner,s.home)){e.setCancelled(true);later(p,p::closeInventory);return;}
if(slot>=size&&e.getClickedInventory()==p.getInventory()&&regular)return; // vanilla cursor movement in own inventory only
e.setCancelled(true);if(slot<0||!regular&&!shift)return;
if(slot>=size){if(!m.owner||m.edit!=-1||!canEdit(p,s)||!shift||e.getClickedInventory()!=p.getInventory()||e.getSlot()<0||e.getSlot()>=36)return;
ItemStack item=e.getCurrentItem();if(empty(item))return;int i=quickSlot(s,item),count=put(s,i,item,e.isRightClick()?1:item.getAmount());if(count>0){p.getInventory().setItem(e.getSlot(),remainder(item,count));refresh(s);}else say(p,"§cНет свободного слота. Забери старый товар или пополни совпадающий.");return;}
int row=slot/9,column=slot%9,i=column-2;
if(!m.owner){if(slot==35&&s.owner.equals(p.getUniqueId())){later(p,()->open(p,s,true,-1));return;}if(i<0||i>=CAPACITY||row>2)return;if(row==0){if(s.price[0][i]>0&&s.price[1][i]>0){say(p,"§eВыбери одну из цен ниже.");return;}int c=s.price[0][i]>0?0:1;buy(p,s,i,s.currency[c][i],s.price[c][i]);}else buy(p,s,i,s.currency[row-1][i],s.price[row-1][i]);refresh(s);return;}
if(!canEdit(p,s))return;
if(m.edit==-2){if(slot==49)later(p,()->open(p,s,true,-1));else if(slot<BUFFER){withdraw(p,s,s.money,slot);refresh(s);}return;}
if(m.edit==-3){if(slot==31)later(p,()->open(p,s,true,-1));else if(slot>=2&&slot<2+OFFERS-CAPACITY){withdraw(p,s,s.stock,slot-2+CAPACITY);refresh(s);}return;}
if(row==3){switch(slot){case 27,28->later(p,()->dialog(p,s,-1,slot==27?"name":"skin"));case 29->{String old=s.style;s.style=switch(old){case "normal"->"bold";case "bold"->"italic";default->"normal";};if(save())applyAppearance(s);else s.style=old;refresh(s);}case 31->later(p,()->open(p,s,true,-2));case 32->later(p,()->open(p,s,true,-3));case 35->later(p,()->open(p,s,false,-1));default->{}}return;}
if(i<0||i>=CAPACITY)return;
ItemStack cursor=e.getCursor();
if(row==0){if(!empty(cursor)){int count=put(s,i,cursor,e.isRightClick()?1:cursor.getAmount());if(count>0){p.setItemOnCursor(remainder(cursor,count));refresh(s);}else say(p,"§cСлот заполнен или занят другим товаром.");return;}
if(e.isRightClick()){if(!empty(s.stock[i])){say(p,"§7Сначала забери остаток ЛКМ.");return;}later(p,()->catalog(p,s,i,-1,0,"","Все"));}else{if(!empty(s.stock[i])){withdraw(p,s,s.stock,i);refresh(s);}else later(p,()->catalog(p,s,i,-1,0,"","Все"));}return;}
int c=row-1;
if(e.getClick()==ClickType.SHIFT_RIGHT){int old=s.price[c][i];s.price[c][i]=0;if(!save())s.price[c][i]=old;refresh(s);return;}
if(!empty(cursor)){choose(p,s,c,i,cursor);later(p,()->dialog(p,s,i,"price"+c));return;}
if(e.isRightClick()&&s.price[c][i]>0){later(p,()->dialog(p,s,i,"price"+c));return;}
later(p,()->catalog(p,s,i,c,0,"","Все"));
}
private static final List<String> FILTERS=List.of("Все","Блоки","Еда","Оружие и инструменты","Броня","Материалы","Кастомные","Инвентарь");
private String normalized(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replace('_',' ').replace('ё','е').strip();}
private String russian(Material m){String n=m.name();if(n.equals("DIAMOND"))return "алмаз";if(n.equals("EMERALD"))return "изумруд";if(n.equals("GOLD_INGOT"))return "золото золотой слиток";if(n.equals("IRON_INGOT"))return "железо железный слиток";if(n.equals("NETHERITE_INGOT"))return "незерит";if(n.equals("COAL"))return "уголь";if(n.equals("REDSTONE"))return "редстоун красная пыль";if(n.contains("SWORD"))return "меч";if(n.contains("PICKAXE"))return "кирка";if(n.contains("AXE"))return "топор";if(n.contains("SHOVEL"))return "лопата";if(n.contains("HELMET"))return "шлем";if(n.contains("CHESTPLATE"))return "нагрудник броня";if(n.contains("LEGGINGS"))return "поножи броня";if(n.contains("BOOTS"))return "ботинки броня";if(n.contains("APPLE"))return "яблоко";if(n.contains("BREAD"))return "хлеб";if(n.contains("OBSIDIAN"))return "обсидиан";if(n.contains("STONE"))return "камень";if(n.contains("PLANKS"))return "доски дерево";return "";}
private boolean matches(ItemStack item,String query){if(query.isBlank())return true;String hay=normalized(item.getType().name()+" "+label(item)+" "+russian(item.getType()));return Arrays.stream(normalized(query).split("\\s+")).allMatch(hay::contains);}
private boolean category(ItemStack item,String filter,boolean custom){Material m=item.getType();String n=m.name();return switch(filter){case "Все","Инвентарь"->true;case "Кастомные"->custom;case "Блоки"->m.isBlock();case "Еда"->m.isEdible();case "Оружие и инструменты"->n.endsWith("_SWORD")||n.endsWith("_AXE")||n.endsWith("_PICKAXE")||n.endsWith("_SHOVEL")||n.endsWith("_HOE")||Set.of("BOW","CROSSBOW","TRIDENT","MACE","SHIELD","SHEARS","FISHING_ROD","FLINT_AND_STEEL").contains(n);case "Броня"->n.endsWith("_HELMET")||n.endsWith("_CHESTPLATE")||n.endsWith("_LEGGINGS")||n.endsWith("_BOOTS")||n.equals("ELYTRA");default->!m.isBlock()&&!m.isEdible();};}
private void append(List<ItemStack> items,ItemStack item,String query,String filter,boolean custom){if(empty(item)||!matches(item,query)||!category(item,filter,custom)||items.stream().anyMatch(i->i.isSimilar(item)))return;items.add(item.asOne());}
private List<ItemStack> catalogItems(Player p,String query,String filter){List<ItemStack> items=new ArrayList<>();
for(ItemStack item:p.getInventory().getStorageContents())append(items,item,query,filter,item!=null&&item.hasItemMeta());
if(!filter.equals("Инвентарь")){
Iterator<Recipe> recipes=Bukkit.recipeIterator();while(recipes.hasNext()){Recipe r=recipes.next();if(r instanceof Keyed k&&Set.of("minecraftunified","targetclaim","talismanforge","enchantmentforge","swampsky").contains(k.getKey().getNamespace()))append(items,r.getResult(),query,filter,true);}
if(!filter.equals("Кастомные"))for(Material mat:Material.values())if(mat.isItem()&&!mat.isAir()&&!mat.isLegacy())append(items,new ItemStack(mat),query,filter,false);
}
return items;}
private void catalog(Player p,Shop s,int offer,int kind,int page,String query,String filter){if(!canEdit(p,s)||offer<0||offer>=CAPACITY)return;
List<ItemStack> items=catalogItems(p,query,filter);int max=Math.max(0,(items.size()-1)/45);page=Math.max(0,Math.min(page,max));Catalog c=new Catalog(s.id,p.getUniqueId(),offer,kind,page,query,filter,items);c.inv=Bukkit.createInventory(c,54,kind<0?"1. Выбери товар":"2. Выбери валюту");for(int i=0;i<45&&page*45+i<items.size();i++)c.inv.setItem(i,preview(items.get(page*45+i),"§7Нажми для выбора (не выдаёт предмет)"));
c.inv.setItem(45,icon(Material.ARROW,"§eПредыдущая"));c.inv.setItem(46,icon(Material.COMPASS,"§bПоиск: "+(query.isBlank()?"все":query),"§7Название предмета / ID"));c.inv.setItem(47,icon(Material.HOPPER,"§eФильтр: "+filter,"§7ЛКМ — следующий · ПКМ — предыдущий"));c.inv.setItem(48,icon(Material.CHEST,"§eТолько мой инвентарь"));c.inv.setItem(49,icon(Material.BARRIER,"§7Назад"));c.inv.setItem(50,icon(Material.PAPER,"§7Страница "+(page+1)+"/"+(max+1)));c.inv.setItem(51,icon(Material.MILK_BUCKET,"§7Сброс поиска"));c.inv.setItem(53,icon(Material.ARROW,"§eСледующая"));p.openInventory(c.inv);}
private void search(Player p,Shop s,Catalog c){if(!canEdit(p,s))return;p.closeInventory();p.showDialog(Dialog.create(builder->builder.empty().base(DialogBase.builder(Component.text("Поиск предметов")).inputs(List.of(DialogInput.text("q",Component.text("Название или ID")).initial(c.query).maxLength(80).width(240).build())).build()).type(DialogType.confirmation(ActionButton.create(Component.text("Найти"),null,100,DialogAction.customClick((view,audience)->{if(audience instanceof Player actor&&actor.getUniqueId().equals(c.owner)){String q=view.getText("q");later(actor,()->catalog(actor,s,c.offer,c.kind,0,q==null?"":q,c.filter));}},ClickCallback.Options.builder().uses(1).build())),ActionButton.create(Component.text("Отмена"),null,100,DialogAction.customClick((view,audience)->{if(audience instanceof Player actor&&actor.getUniqueId().equals(c.owner))later(actor,()->catalog(actor,s,c.offer,c.kind,c.page,c.query,c.filter));},ClickCallback.Options.builder().uses(1).build()))))));}
private void selectCatalog(Player p,Shop s,int i,int kind,ItemStack item){if(!canEdit(p,s)||empty(item)||i>=CAPACITY)return;if(kind<0){if(!empty(s.stock[i])&&!s.stock[i].isSimilar(item)){say(p,"§cСначала забери остаток прежнего товара.");return;}ItemStack previous=s.product[i];int oldQ=s.quantity[i],oldA=s.price[0][i],oldB=s.price[1][i];if(empty(previous)||!previous.isSimilar(item)){s.price[0][i]=0;s.price[1][i]=0;}s.product[i]=item.asOne();s.quantity[i]=Math.min(oldQ,item.getMaxStackSize());if(!save()){s.product[i]=previous;s.quantity[i]=oldQ;s.price[0][i]=oldA;s.price[1][i]=oldB;return;}later(p,()->open(p,s,true,-1));}else{choose(p,s,kind,i,item);later(p,()->dialog(p,s,i,"price"+kind));}}
@EventHandler(priority=EventPriority.HIGHEST) public void catalogClick(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Catalog c))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(c.owner)||e.getRawSlot()<0||e.getClick()!=ClickType.LEFT&&e.getClick()!=ClickType.RIGHT)return;Shop s=shops.get(c.shop);if(!canEdit(p,s))return;int slot=e.getRawSlot();if(slot>=54){if(e.getClickedInventory()==p.getInventory()&&!empty(e.getCurrentItem()))selectCatalog(p,s,c.offer,c.kind,e.getCurrentItem());return;}if(slot<45){int i=c.page*45+slot;if(i<c.items.size())selectCatalog(p,s,c.offer,c.kind,c.items.get(i));return;}
later(p,()->{switch(slot){case 45->catalog(p,s,c.offer,c.kind,c.page-1,c.query,c.filter);case 46->search(p,s,c);case 47->catalog(p,s,c.offer,c.kind,0,c.query,FILTERS.get(Math.floorMod(FILTERS.indexOf(c.filter)+(e.isRightClick()?-1:1),FILTERS.size())));case 48->catalog(p,s,c.offer,c.kind,0,c.query,"Инвентарь");case 49->open(p,s,true,-1);case 51->catalog(p,s,c.offer,c.kind,0,"","Все");case 53->catalog(p,s,c.offer,c.kind,c.page+1,c.query,c.filter);default->{}}});}

private void withdraw(Player p,Shop s,ItemStack[] source,int i){if(empty(source[i]))return;ItemStack old=source[i];ItemStack[] next=insert(p.getInventory().getStorageContents(),old);if(next==null){say(p,"§cНет места в инвентаре.");return;}source[i]=null;if(!save()){source[i]=old;return;}p.getInventory().setStorageContents(next);}
private void buy(Player p,Shop s,int i,ItemStack currency,int price){
if(s.owner.equals(p.getUniqueId())){say(p,"§7Это твоя витрина.");return;}
if(p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE){say(p,"§cПокупка доступна только в выживании.");return;}
if(price<=0||empty(s.stock[i])||s.stock[i].getAmount()<s.quantity[i]){say(p,"§cНет товара или цена отключена.");return;}
ItemStack payment=currency.clone();payment.setAmount(price);ItemStack[] five=insert(Arrays.copyOf(s.money,CAPACITY),payment);ItemStack[] nextMoney=five==null?null:copy(s.money);if(five!=null)System.arraycopy(five,0,nextMoney,0,CAPACITY);if(nextMoney==null){say(p,"§cВыручка заполнена, ожидайте разгрузки.");s.nextTrip=0;return;}
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
Shop s=shops.get(selected.get(p.getUniqueId()));if(s==null||!s.owner.equals(p.getUniqueId())||!near(p,s)){say(p,"§eПКМ по своему торговцу — открыть настройки.");return true;}
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
