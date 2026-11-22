package dev.unified.antiesp;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

public final class OreShufflePopulator extends BlockPopulator {
private final UnifiedAntiESP plugin;
private final LongAdder chunks=new LongAdder(),moved=new LongAdder();
OreShufflePopulator(UnifiedAntiESP plugin){this.plugin=plugin;}
long chunks(){return chunks.sum();}
long moved(){return moved.sum();}
private static long mix(long z){z=(z^(z>>>30))*0xbf58476d1ce4e5b9L;z=(z^(z>>>27))*0x94d049bb133111ebL;return z^(z>>>31);}
private static Material base(Material ore){
return switch(ore) {
case DIAMOND_ORE,EMERALD_ORE,GOLD_ORE,IRON_ORE,REDSTONE_ORE,LAPIS_ORE,COPPER_ORE,COAL_ORE -> Material.STONE;
case DEEPSLATE_DIAMOND_ORE,DEEPSLATE_EMERALD_ORE,DEEPSLATE_GOLD_ORE,DEEPSLATE_IRON_ORE,DEEPSLATE_REDSTONE_ORE,DEEPSLATE_LAPIS_ORE,DEEPSLATE_COPPER_ORE,DEEPSLATE_COAL_ORE -> Material.DEEPSLATE;
case NETHER_GOLD_ORE,NETHER_QUARTZ_ORE,ANCIENT_DEBRIS -> Material.NETHERRACK;
default -> null;};
}
@Override public void populate(WorldInfo info,Random random,int cx,int cz,LimitedRegion region){
chunks.increment();
boolean nether=info.getEnvironment()==World.Environment.NETHER;
int low=Math.max(info.getMinHeight(),nether?8:-60);
int high=Math.min(info.getMaxHeight()-1,nether?118:84);
if(low>high)return;
SplittableRandom r=new SplittableRandom(mix(plugin.oreSalt() ^ ((long)cx*0x9e3779b97f4a7c15L)^((long)cz*0xbf58476d1ce4e5b9L)^info.getName().hashCode()));
int changed=0,limit=plugin.oreMoves(),range=plugin.oreOffset();
HashSet<Long> modified=new HashSet<>();
for(int i=0;i<plugin.oreReads() && changed<limit;i++){
int x=(cx<<4)+r.nextInt(16), z=(cz<<4)+r.nextInt(16),y=low+r.nextInt(high-low+1);
long id=key(x,y,z);
if(modified.contains(id))continue;
Material ore=region.getType(x,y,z),stone=base(ore);
if(stone==null)continue;
for(int t=0;t<4;t++){
int dx=r.nextInt(-range,range+1),dy=r.nextInt(-range,range+1),dz=r.nextInt(-range,range+1);
if((dx|dy|dz)==0)continue;
int xx=x+dx,yy=y+dy,zz=z+dz;
if(yy<low||yy>high||(xx>>4)!=cx||(zz>>4)!=cz)continue;
long target=key(xx,yy,zz);
if(modified.contains(target)||region.getType(xx,yy,zz)!=stone)continue;
region.setType(xx,yy,zz,ore);
region.setType(x,y,z,stone);
modified.add(id);modified.add(target);changed++;break;
}
}
moved.add(changed);
}
private static long key(int x,int y,int z){return (((long)x&0x3ffffffL)<<38)|(((long)z&0x3ffffffL)<<12)|((long)y&0xfff);}
}
