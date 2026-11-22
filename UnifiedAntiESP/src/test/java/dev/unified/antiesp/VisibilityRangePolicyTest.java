package dev.unified.antiesp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class VisibilityRangePolicyTest {
@Test void oneBlockBehindWallIsAlwaysEligible(){
assertTrue(VisibilityRangePolicy.within(1,224*224));
}
@Test void touchingRangeIsEligible(){
assertTrue(VisibilityRangePolicy.within(0,224*224));
}
@Test void farawayMobIsCovered(){
assertTrue(VisibilityRangePolicy.within(125*125,224*224));
assertTrue(VisibilityRangePolicy.within(220*220,224*224));
}
@Test void outsideConfiguredRangeIsExcluded(){
assertFalse(VisibilityRangePolicy.within(225*225,224*224));
}
@Test void invalidDistanceCannotForceExpensiveRaytrace(){
assertFalse(VisibilityRangePolicy.within(Double.NaN,224*224));
assertFalse(VisibilityRangePolicy.within(Double.POSITIVE_INFINITY,224*224));
assertFalse(VisibilityRangePolicy.within(-1,224*224));
}
}
