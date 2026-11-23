package dev.unified.defend;

import org.bukkit.Material;

/** Pure policy is testable outside a running Minecraft server. */
final class RecoveryPolicy {
private RecoveryPolicy(){}
static boolean protectedDungeonBlock(Material material){
return protectedName(material.name());
}
private static boolean protectedName(String name){
return name.equals("SPAWNER")||name.equals("TRIAL_SPAWNER")
||name.equals("VAULT")||name.equals("CHEST")
||name.equals("TRAPPED_CHEST")||name.equals("BARREL");
}
static boolean dangerousPlacement(Material material){
String n=material.name();
return protectedName(n)
|| n.contains("SHULKER_BOX") || n.endsWith("_BED")
|| n.equals("HOPPER") || n.equals("DISPENSER")
|| n.equals("DROPPER") || n.equals("FURNACE")
|| n.equals("BLAST_FURNACE") || n.equals("SMOKER")
|| n.equals("BREWING_STAND") || n.equals("CRAFTER");
}
static boolean fluid(Material material){
return fluidName(material.name());
}
private static boolean fluidName(String n){
return n.equals("WATER")||n.equals("LAVA")||n.equals("BUBBLE_COLUMN");
}
private static boolean valuableName(String n){
return n.contains("COPPER") || n.contains("GOLD") || n.contains("DIAMOND")
|| n.contains("EMERALD") || n.contains("NETHERITE")
|| n.contains("IRON") || n.contains("REDSTONE")
|| n.contains("LAPIS") || n.contains("ANCIENT_DEBRIS")
|| n.contains("AMETHYST") || n.contains("QUARTZ")
|| n.startsWith("RAW_") || n.contains("ORE")
|| n.equals("GILDED_BLACKSTONE");
}
/** Null means never regenerate. Trial Chambers copper becomes deepslate. */
static String repairName(boolean inTrialChamber,String original){
if(original==null || original.equals("AIR") || original.endsWith("_AIR")
|| fluidName(original) || protectedName(original))return null;
if(inTrialChamber && original.contains("COPPER"))return "DEEPSLATE";
if(valuableName(original))return null;
return original;
}
static Material repairedAs(StructureIndex.Kind kind,Material original){
if(kind==null)return null;
String target=repairName(kind==StructureIndex.Kind.TRIAL_CHAMBERS,original.name());
return target==null?null:Material.matchMaterial(target);
}
}
