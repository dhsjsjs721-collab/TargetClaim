package dev.unified.world;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

/** Order chunks from center outward; no duplicate chunks. Pure function. */
final class PregenPlan {
record Offset(int x,int z){}
private PregenPlan(){}
static List<Offset> spiral(int radius){
if(radius<0||radius>128)throw new IllegalArgumentException("radius must be 0..128");
List<Offset> result=new ArrayList<>((2*radius+1)*(2*radius+1));
result.add(new Offset(0,0));
for(int r=1;r<=radius;r++){
for(int x=-r;x<=r;x++){
result.add(new Offset(x,-r));
result.add(new Offset(x,r));
}
for(int z=-r+1;z<r;z++){
result.add(new Offset(-r,z));
result.add(new Offset(r,z));
}
}
return result;
}
}
