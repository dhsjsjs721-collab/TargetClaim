package dev.unified.machines;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MachineRulesTest {
@Test void swordsAxesAndSpearsWork(){
for(String name:new String[]{"IRON_SWORD","NETHERITE_AXE","COPPER_SPEAR","DIAMOND_SPEAR","MACE","TRIDENT"})
assertTrue(MachineRules.weaponName(name),name);
}
@Test void neverAcceptOtherItems(){
for(String name:new String[]{"DIAMOND_PICKAXE","DIAMOND_SHOVEL","BOW","TOTEM_OF_UNDYING","TNT","IRON_INGOT"})
assertFalse(MachineRules.weaponName(name),name);
}
@Test void damageIsBoundedAndPositive(){
assertTrue(MachineRules.damage("NETHERITE_SWORD")>MachineRules.damage("WOODEN_SWORD"));
assertTrue(MachineRules.damage("IRON_SPEAR")>0);
assertEquals(0.0,MachineRules.damage("BOW"));
}
@Test void onlyOneLoaderPerOwner(){
assertTrue(MachineRules.canPlaceLoader(0));
assertFalse(MachineRules.canPlaceLoader(1));
assertFalse(MachineRules.canPlaceLoader(2));
}
@Test void offlineLoadersDoNotKeepChunksLoaded(){
assertFalse(MachineRules.activeOnlyWhileOnline(false));
assertTrue(MachineRules.activeOnlyWhileOnline(true));
}
}
