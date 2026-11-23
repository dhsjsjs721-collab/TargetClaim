package dev.unified.enchantments;

final class EnchantRules {
private EnchantRules(){}
static boolean weapon(String material){
return material!=null && (material.endsWith("_SWORD")||material.endsWith("_AXE")||
material.endsWith("_SPEAR")||material.equals("TRIDENT")||material.equals("MACE"));
}
static double chance(double base,int level){
if(!Double.isFinite(base))return 0;
return Math.min(1.0,Math.max(0.0,base)*Math.max(0,Math.min(level,3)));
}
static int level(int requested){return Math.max(1,Math.min(3,requested));}
static boolean validId(String value){return value!=null && value.matches("[0-9a-fA-F-]{36}");}
}
