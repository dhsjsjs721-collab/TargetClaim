package dev.unified.machines;

/** Pure rules used to validate all weapon types including 26.3 spears. */
final class MachineRules {
private MachineRules(){}
static boolean weaponName(String name){
return name!=null && (name.endsWith("_SWORD") || name.endsWith("_AXE")
|| name.endsWith("_SPEAR") || name.equals("TRIDENT") || name.equals("MACE"));
}
static double damage(String name){
if(!weaponName(name))return 0;
if(name.equals("MACE"))return 7.0;
if(name.equals("TRIDENT"))return 6.0;
double tier=name.startsWith("NETHERITE_")?9:
name.startsWith("DIAMOND_")?8:
name.startsWith("IRON_")?7:
name.startsWith("COPPER_")?6:
name.startsWith("STONE_")?5:
name.startsWith("GOLDEN_")?4:4;
if(name.endsWith("_SPEAR"))return Math.max(2,tier-2);
if(name.endsWith("_AXE"))return Math.min(10,tier+1);
return tier;
}
static boolean canPlaceLoader(int alreadyPlaced){
return alreadyPlaced==0;
}
static boolean activeOnlyWhileOnline(boolean online){return online;}
}
