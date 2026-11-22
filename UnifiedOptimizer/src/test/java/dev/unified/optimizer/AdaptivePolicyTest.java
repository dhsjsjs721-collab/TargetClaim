package dev.unified.optimizer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AdaptivePolicyTest {
private AdaptivePolicy policy(){
return new AdaptivePolicy(46,37,19.2,19.8,3,4,0.35);
}
@Test void sustainedPressureTriggersReduction(){
AdaptivePolicy p=policy();
assertEquals(0,p.observe(56,19.0));
assertEquals(0,p.observe(56,19.0));
assertEquals(-1,p.observe(56,19.0));
}
@Test void singleLagSpikeMustNotTriggerReduction(){
AdaptivePolicy p=policy();
for(int i=0;i<4;i++)p.observe(20,20);
assertEquals(0,p.observe(92,20));
for(int i=0;i<4;i++)assertNotEquals(-1,p.observe(20,20));
}
@Test void recoveryRequiresSeveralHealthySamples(){
AdaptivePolicy p=policy();
for(int i=0;i<8;i++)p.observe(60,18.5);
for(int i=0;i<18;i++)p.observe(20,20);
assertEquals(1,p.observe(20,20));
}
@Test void invalidMeasurementsIgnored(){
AdaptivePolicy p=policy();
assertEquals(0,p.observe(Double.NaN,20));
assertEquals(0,p.observe(-2,20));
assertEquals(0,p.observe(20,Double.POSITIVE_INFINITY));
assertEquals(0,p.samples());
assertTrue(Double.isNaN(p.average()));
}
@Test void resetClearsHysteresisButKeepsWindow(){
AdaptivePolicy p=policy();
p.observe(60,18);p.observe(60,18);
p.resetStreaks();
assertEquals(0,p.badStreak());
assertEquals(0,p.observe(60,18));
assertEquals(3,p.samples());
}
@Test void fixedSizeHistoryIsBounded(){
AdaptivePolicy p=policy();
for(int i=0;i<100;i++)p.observe(20+i/100.0,20);
assertEquals(12,p.samples());
assertTrue(p.average()>20);
}
}
