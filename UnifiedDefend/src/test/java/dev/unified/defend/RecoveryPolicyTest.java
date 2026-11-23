package dev.unified.defend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RecoveryPolicyTest {
@Test void valuablesNeverRegrow(){
for(String m:new String[]{"DIAMOND_ORE","DEEPSLATE_DIAMOND_ORE","GOLD_BLOCK",
"IRON_BLOCK","EMERALD_ORE","ANCIENT_DEBRIS","REDSTONE_ORE","NETHER_QUARTZ_ORE",
"AMETHYST_BLOCK","RAW_COPPER_BLOCK","COPPER_BLOCK"}){
assertNull(RecoveryPolicy.repairName(false,m),m);
}
}
@Test void copperInTrialChamberBecomesDeepslate(){
assertEquals("DEEPSLATE",RecoveryPolicy.repairName(true,"COPPER_BLOCK"));
assertEquals("DEEPSLATE",RecoveryPolicy.repairName(true,"OXIDIZED_COPPER"));
assertEquals("DEEPSLATE",RecoveryPolicy.repairName(true,"WAXED_COPPER_GRATE"));
}
@Test void outsideChambersCopperNeverRegrows(){
assertNull(RecoveryPolicy.repairName(false,"COPPER_BLOCK"));
}
@Test void fluidsSpawnersAndStorageArePreserved(){
for(String m:new String[]{"WATER","LAVA","BUBBLE_COLUMN","SPAWNER","TRIAL_SPAWNER","VAULT","CHEST","BARREL","AIR","CAVE_AIR"}){
assertNull(RecoveryPolicy.repairName(true,m),m);
}
}
@Test void normalDungeonDecorationsRegrow(){
assertEquals("DEEPSLATE_BRICKS",RecoveryPolicy.repairName(false,"DEEPSLATE_BRICKS"));
assertEquals("NETHER_BRICKS",RecoveryPolicy.repairName(false,"NETHER_BRICKS"));
assertEquals("TUFF_BRICKS",RecoveryPolicy.repairName(true,"TUFF_BRICKS"));
}
}
