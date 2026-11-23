package dev.unified.enchantments;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class EnchantRulesTest {
@Test void supportsAllServerWeapons(){
for(String m:new String[]{"IRON_SWORD","NETHERITE_AXE","COPPER_SPEAR","TRIDENT","MACE"})assertTrue(EnchantRules.weapon(m));
assertFalse(EnchantRules.weapon("DIAMOND_PICKAXE"));
}
@Test void chanceClamped(){
assertEquals(0.25,EnchantRules.chance(0.125,2),0.00001);
assertEquals(1.0,EnchantRules.chance(0.5,3),0.00001);
assertEquals(0.0,EnchantRules.chance(Double.NaN,2),0.00001);
}
@Test void levelsAreBounded(){
assertEquals(1,EnchantRules.level(-50));
assertEquals(3,EnchantRules.level(99));
}
}
