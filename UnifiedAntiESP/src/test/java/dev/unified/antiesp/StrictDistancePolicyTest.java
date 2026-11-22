package dev.unified.antiesp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class StrictDistancePolicyTest {
@Test void distantCaveMobIsHidden(){
assertTrue(StrictDistancePolicy.hide(true,true,true,70*70,40*40));
assertTrue(StrictDistancePolicy.hide(true,true,true,125*125,40*40));
}
@Test void closeCaveMobIsVisible(){
assertFalse(StrictDistancePolicy.hide(true,true,true,25*25,40*40));
}
@Test void distantSurfaceMobIsNotHiddenByDistanceAlone(){
assertFalse(StrictDistancePolicy.hide(true,true,false,70*70,40*40));
}
@Test void modeCanBeDisabled(){
assertFalse(StrictDistancePolicy.hide(false,true,true,70*70,40*40));
}
@Test void nonLivingObjectsAreNotStrictlyHidden(){
assertFalse(StrictDistancePolicy.hide(true,false,true,70*70,40*40));
}
@Test void invalidDistanceDoesNotTriggerStrictHiding(){
assertFalse(StrictDistancePolicy.hide(true,true,true,Double.NaN,40*40));
assertFalse(StrictDistancePolicy.hide(true,true,true,Double.POSITIVE_INFINITY,40*40));
}
}
