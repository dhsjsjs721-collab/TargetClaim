package dev.unified.defend;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RecoveryPolicyTest {
@Test void valuableBlocksNeverRegrow(){
for(Material m:new Material[]{Material.DIAMOND_ORE,Material.DEEPSLATE_DIAMOND_ORE,
Material.GOLD_BLOCK,Material.IRON_BLOCK,Material.EMERALD_ORE,Material.ANCIENT_DEBRIS,
Material.REDSTONE_ORE,Material.NETHER_QUARTZ_ORE}){
assertNull(RecoveryPolicy.repairedAs(StructureIndex.Kind.STRONGHOLD,m));
}
}
@Test void copperInChamberBecomesDeepslate(){
assertEquals(Material.DEEPSLATE,RecoveryPolicy.repairedAs(StructureIndex.Kind.TRIAL_CHAMBERS,Material.COPPER_BLOCK));
assertEquals(Material.DEEPSLATE,RecoveryPolicy.repairedAs(StructureIndex.Kind.TRIAL_CHAMBERS,Material.OXIDIZED_COPPER));
}
@Test void copperOutsideChambersNeverRegrows(){
assertNull(RecoveryPolicy.repairedAs(StructureIndex.Kind.END_CITY,Material.COPPER_BLOCK));
}
@Test void waterLavaAndSpawnersAreNeverAltered(){
for(Material m:new Material[]{Material.WATER,Material.LAVA,Material.SPAWNER,Material.TRIAL_SPAWNER,Material.CHEST}){
assertNull(RecoveryPolicy.repairedAs(StructureIndex.Kind.TRIAL_CHAMBERS,m));
}
}
@Test void plainDecorationCanRegrow(){
assertEquals(Material.DEEPSLATE_BRICKS,RecoveryPolicy.repairedAs(StructureIndex.Kind.ANCIENT_CITY,Material.DEEPSLATE_BRICKS));
assertEquals(Material.NETHER_BRICKS,RecoveryPolicy.repairedAs(StructureIndex.Kind.FORTRESS,Material.NETHER_BRICKS));
}
}
