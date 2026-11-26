package dev.unified;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import java.util.*;

final class RecipeGuide implements Listener {
private final UnifiedPlugin host;
private final Set<String> namespaces=Set.of("minecraftunified","targetclaim","talismanforge","enchantmentforge","swampsky");
RecipeGuide(UnifiedPlugin h){host=h;}
private static final class Menu implements InventoryHolder {
final List<Recipe> recipes;final int page;final Recipe selected;Inventory inventory;
Menu(List<Recipe> recipes,int page,Recipe selected){this.recipes=recipes;this.page=page;this.selected=selected;}
public Inventory getInventory(){return inventory;}
}
void enable(){Bukkit.getPluginManager().registerEvents(this,host);for(Player p:Bukkit.getOnlinePlayers())discover(p);}
private List<Recipe> all(){List<Recipe> out=new ArrayList<>();Iterator<Recipe> i=Bukkit.recipeIterator();while(i.hasNext()){Recipe r=i.next();if(r instanceof Keyed k&&namespaces.contains(k.getKey().getNamespace())&&(r instanceof ShapedRecipe||r instanceof ShapelessRecipe))out.add(r);}out.sort(Comparator.comparing(r->((Keyed)r).getKey().toString()));return List.copyOf(out);}
private void discover(Player p){p.discoverRecipes(all().stream().map(r->((Keyed)r).getKey()).toList());}
@EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(host,()->{if(e.getPlayer().isOnline())discover(e.getPlayer());},20L);}
private ItemStack button(Material m,String name,String... lore){ItemStack item=new ItemStack(m);var meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(Arrays.asList(lore));item.setItemMeta(meta);return item;}
void open(Player p){discover(p);show(p,all(),0);}
private void show(Player p,List<Recipe> list,int page){Menu m=new Menu(list,page,null);m.inventory=Bukkit.createInventory(m,54,"Книга рецептов · "+(page+1));for(int i=0;i<45&&page*45+i<list.size();i++)m.inventory.setItem(i,list.get(page*45+i).getResult().clone());if(page>0)m.inventory.setItem(45,button(Material.ARROW,"§eНазад"));if((page+1)*45<list.size())m.inventory.setItem(53,button(Material.ARROW,"§eДалее"));m.inventory.setItem(49,button(Material.BOOK,"§6Особые предметы","§7Личный торговец: 32 алмаза","§7Верити: редкий обмен","§7Талисманы: реликварий","§7Независимый приват: 20 алмазов"));p.openInventory(m.inventory);}
private ItemStack ingredient(RecipeChoice choice){if(choice==null)return null;ItemStack item=choice instanceof RecipeChoice.ExactChoice e?e.getChoices().getFirst().clone():choice instanceof RecipeChoice.MaterialChoice m?new ItemStack(m.getChoices().getFirst()):choice.getItemStack().clone();if(choice instanceof RecipeChoice.MaterialChoice m&&m.getChoices().size()>1){var meta=item.getItemMeta();if(meta!=null){meta.setLore(m.getChoices().stream().map(x->"§7"+x.name()).limit(12).toList());item.setItemMeta(meta);}}return item;}
private void detail(Player p,Menu source,Recipe r){Menu m=new Menu(source.recipes,source.page,r);m.inventory=Bukkit.createInventory(m,45,"Рецепт · "+((Keyed)r).getKey().getKey());if(r instanceof ShapedRecipe shaped){String[] shape=shaped.getShape();var map=shaped.getChoiceMap();for(int y=0;y<shape.length;y++)for(int x=0;x<shape[y].length();x++)if(shape[y].charAt(x)!=' ')m.inventory.setItem(10+y*9+x,ingredient(map.get(shape[y].charAt(x))));}else if(r instanceof ShapelessRecipe shapeless){int i=0;for(RecipeChoice choice:shapeless.getChoiceList()){if(i>=9)break;m.inventory.setItem(10+(i/3)*9+i%3,ingredient(choice));i++;}m.inventory.setItem(4,button(Material.PAPER,"§7Бесформенный рецепт"));}m.inventory.setItem(24,r.getResult().clone());m.inventory.setItem(36,button(Material.ARROW,"§eК списку"));p.openInventory(m.inventory);}
@EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Menu m))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||e.getRawSlot()<0||e.getRawSlot()>=m.inventory.getSize()||!e.isLeftClick())return;int slot=e.getRawSlot();Bukkit.getScheduler().runTask(host,()->{if(!p.isOnline()||p.getOpenInventory().getTopInventory().getHolder()!=m)return;if(m.selected!=null){if(slot==36)show(p,m.recipes,m.page);return;}if(slot==45&&m.page>0)show(p,m.recipes,m.page-1);else if(slot==53&&(m.page+1)*45<m.recipes.size())show(p,m.recipes,m.page+1);else if(slot<45&&m.page*45+slot<m.recipes.size())detail(p,m,m.recipes.get(m.page*45+slot));});}
@EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
}
