package dev.unified.defend;

import java.util.*;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.util.BoundingBox;

/** Only examines the current already-loaded chunk. Never finds/loads structures remotely. */
final class StructureIndex implements Listener {
enum Kind {
FORTRESS("Незерская крепость","chests/nether_bridge"),
BASTION("Бастион","chests/bastion_treasure"),
STRONGHOLD("Крепость Энда","chests/stronghold_corridor"),
END_CITY("Город Энда","chests/end_city_treasure"),
ANCIENT_CITY("Древний город","chests/ancient_city"),
TRIAL_CHAMBERS("Испытательные палаты","trial_chambers/chests/entrance");
final String title;
final String fallbackLoot;
Kind(String title,String fallbackLoot){this.title=title;this.fallbackLoot=fallbackLoot;}
static Kind of(String id){
return switch(id){
case "fortress","nether_fortress" -> FORTRESS;
case "bastion_remnant" -> BASTION;
case "stronghold" -> STRONGHOLD;
case "end_city" -> END_CITY;
case "ancient_city" -> ANCIENT_CITY;
case "trial_chambers" -> TRIAL_CHAMBERS;
default -> null;
};
}
}
private record Region(BoundingBox box,Kind kind){}
private record Cached(List<Region> regions,long checkedAt){}
private final LinkedHashMap<String,Cached> cache=new LinkedHashMap<>(256,0.75f,true);
private long lookupErrors;
private long lastErrorLog;

private final Set<Kind> allowed;
private final int maxEntries;
StructureIndex(Set<Kind> allowed,int maxEntries){
this.allowed=allowed;
this.maxEntries=maxEntries;
}
private static String chunkId(Chunk c){return c.getWorld().getUID()+":"+c.getX()+":"+c.getZ();}
Kind at(Block block){
Chunk chunk=block.getChunk();
String key=chunkId(chunk);
long now=System.currentTimeMillis();
Cached cached=cache.get(key);
if(cached==null || cached.regions.isEmpty() && now-cached.checkedAt>10000){
List<Region> regions=new ArrayList<>();
try{
for(GeneratedStructure structure:chunk.getStructures()){
String id=structure.getStructure().getKey().getKey();
Kind kind=Kind.of(id);
if(kind!=null && allowed.contains(kind))regions.add(new Region(structure.getBoundingBox(),kind));
}
}catch(RuntimeException error){
// Retry false negatives after ten seconds instead of caching them forever.
lookupErrors++;
if(now-lastErrorLog>60000){
lastErrorLog=now;
org.bukkit.Bukkit.getLogger().warning("[UnifiedDefend] Failed inspecting structure metadata for "+key+
" ("+error.getClass().getSimpleName()+"). Retry in 10 seconds.");
}
}
if(cache.size()>=maxEntries){Iterator<String> it=cache.keySet().iterator();if(it.hasNext()){it.next();it.remove();}}
cached=new Cached(regions,now);
cache.put(key,cached);
}
List<Region> regions=cached.regions;
double x=block.getX()+0.5,y=block.getY()+0.5,z=block.getZ()+0.5;
for(Region region:regions)if(region.box.contains(x,y,z))return region.kind;
return null;
}
@EventHandler public void unload(ChunkUnloadEvent e){cache.remove(chunkId(e.getChunk()));}
void clear(){cache.clear();}
int size(){return cache.size();}
long lookupErrors(){return lookupErrors;}
}
