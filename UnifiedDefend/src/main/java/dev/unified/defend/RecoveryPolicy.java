package dev.unified.defend;

import org.bukkit.Material;

final class RecoveryPolicy {
private RecoveryPolicy(){}
static boolean protectedDungeonBlock(Material material){
return material==Material.SPAWNER || material==Material.TRIAL_SPAWNER
|| material==Material.VAULT || material==Material.CHEST
|| material==Material.TRAPPED_CHEST || material==Material.BARREL;
}
static boolean dangerousPlacement(Material material){
String n=material.name();
return protectedDungeonBlock(material)
|| n.contains("SHULKER_BOX") || n.endsWith("_BED")
|| material==Material.HOPPER || material==Material.DISPENSER
|| material==Material.DROPPER || material==Material.FURNACE
|| material==Material.BLAST_FURNACE || material==Material.SMOKER
|| material==Material.BREWING_STAND || material==Material.CRAFTER;
}
static boolean fluid(Material material){
return material==Material.WATER || material==Material.LAVA
|| material==Material.BUBBLE_COLUMN;
}
static boolean valuable(Material m){
String n=m.name();
return n.contains("COPPER") || n.contains("GOLD") || n.contains("DIAMOND")
|| n.contains("EMERALD") || n.contains("NETHERITE")
|| n.contains("IRON") || n.contains("REDSTONE")
|| n.contains("LAPIS") || n.contains("ANCIENT_DEBRIS")
|| n.contains("AMETHYST") || n.contains("QUARTZ")
|| n.startsWith("RAW_") || n.contains("ORE")
|| n.equals("GILDED_BLACKSTONE");
}
/** Null means never regenerate. Copper in trial chambers becomes deepslate. */
static Material repairedAs(StructureIndex.Kind kind,Material original){
if(kind==null || original.isAir() || fluid(original) || protectedDungeonBlock(original))return null;
if(kind==StructureIndex.Kind.TRIAL_CHAMBERS && original.name().contains("COPPER"))
return Material.DEEPSLATE;
if(valuable(original))return null;
return original;
}
}
