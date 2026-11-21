package dev.unified;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Survival resource craft agreed with players:
 *
 * B K B
 * K O K  => 8 Deepslate
 * B K B
 *
 * B = Basalt, K = Blackstone, O = Obsidian.
 *
 * The recipe is public to all races and uses only ordinary vanilla items.
 */
public final class DeepslateCrafts {
private final NamespacedKey recipeKey;
private boolean enabled;

public DeepslateCrafts(UnifiedPlugin plugin){
recipeKey=new NamespacedKey(plugin,"nether_deepslate");
}

public void enable(){
if(enabled)return;
Bukkit.removeRecipe(recipeKey);
ShapedRecipe recipe=new ShapedRecipe(recipeKey,new ItemStack(Material.DEEPSLATE,8));
recipe.shape("BKB","KOK","BKB");
recipe.setIngredient('B',Material.BASALT);
recipe.setIngredient('K',Material.BLACKSTONE);
recipe.setIngredient('O',Material.OBSIDIAN);
if(!Bukkit.addRecipe(recipe))throw new IllegalStateException("Could not register Nether Deepslate recipe");
enabled=true;
}

public void disable(){
if(!enabled)return;
Bukkit.removeRecipe(recipeKey);
enabled=false;
}
}
