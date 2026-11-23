package dev.unified.world;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
final class PregenPlanTest {
@Test void beginsAtCenter(){assertEquals(new PregenPlan.Offset(0,0),PregenPlan.spiral(3).getFirst());}
@Test void coversAllSquarePositions(){
for(int r:new int[]{0,1,2,4,16}){
var offsets=PregenPlan.spiral(r);
assertEquals((2*r+1)*(2*r+1),offsets.size());
assertEquals(offsets.size(),new HashSet<>(offsets).size());
for(var off:offsets)assertTrue(Math.abs(off.x())<=r&&Math.abs(off.z())<=r);
}
}
@Test void invalidRadiusDenied(){
assertThrows(IllegalArgumentException.class,()->PregenPlan.spiral(-1));
assertThrows(IllegalArgumentException.class,()->PregenPlan.spiral(129));
}
}
