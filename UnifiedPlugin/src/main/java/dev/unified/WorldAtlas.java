package dev.unified;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** One permanent, pre-rendered surface atlas. Bounded asynchronous chunk loading. */
final class WorldAtlas {
private record Pixel(int x,int z,int wx,int wz){}
private record Batch(int x,int z,List<Pixel> pixels){}
private final UnifiedPlugin host;
private final World world;
private final int size,centerX,centerZ;
private final Path cache;
private final BufferedImage picture;
private final ArrayDeque<Batch> queue=new ArrayDeque<>();
private final int[] height=new int[128*128];
private BukkitTask task;
private int done,active,lastSaved,revision,failed;
private boolean stopped;
WorldAtlas(UnifiedPlugin host,World world){
this.host=host;this.world=world;
WorldBorder border=world.getWorldBorder();double width=border.getSize();
int fallback=Math.max(128,Math.min(16384,host.getConfig().getInt("scout-maps.width",3000)));
boolean finite=width>=128&&width<=16384;
size=finite?(int)Math.ceil(width):fallback;
Location center=finite?border.getCenter():world.getSpawnLocation();centerX=center.getBlockX();centerZ=center.getBlockZ();
cache=host.getDataFolder().toPath().resolve("atlas-v2-"+world.getUID()+"-"+world.getSeed()+"-"+centerX+"-"+centerZ+"-"+size+".png");
BufferedImage loaded=null;try{if(Files.isRegularFile(cache))loaded=ImageIO.read(cache.toFile());}catch(Exception ignored){}
picture=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);
if(loaded!=null&&loaded.getWidth()==128&&loaded.getHeight()==128)for(int x=0;x<128;x++)for(int z=0;z<128;z++)picture.setRGB(x,z,loaded.getRGB(x,z));
Map<Long,List<Pixel>> groups=new LinkedHashMap<>();
for(int z=0;z<128;z++)for(int x=0;x<128;x++){
if((picture.getRGB(x,z)>>>24)!=0){done++;continue;}
int wx=worldX((x+.5)/128.0),wz=worldZ((z+.5)/128.0);long key=((long)(wx>>4)<<32)|((wz>>4)&0xffffffffL);
groups.computeIfAbsent(key,k->new ArrayList<>()).add(new Pixel(x,z,wx,wz));
}
for(var e:groups.entrySet())queue.add(new Batch((int)(e.getKey()>>32),(int)(long)e.getKey(),e.getValue()));
lastSaved=done;
}
int width(){return size;}int centerX(){return centerX;}int centerZ(){return centerZ;}
int worldX(double fraction){return (int)Math.floor(centerX-size/2.0+fraction*size);}
int worldZ(double fraction){return (int)Math.floor(centerZ-size/2.0+fraction*size);}
int cursorX(double x){return (int)Math.round((x-centerX)*256.0/size);}
int cursorZ(double z){return (int)Math.round((z-centerZ)*256.0/size);}
int progress(){return done*100/16384;}boolean ready(){return done==16384;}int revision(){return revision;}BufferedImage image(){return picture;}
void start(){if(ready()){host.getLogger().info("SELFTEST world atlas READY: "+size+"x"+size+" (cached)");return;}host.getLogger().info("World atlas pre-render: "+size+"x"+size+", "+queue.size()+" chunk batches");task=Bukkit.getScheduler().runTaskTimer(host,this::pump,1L,1L);}
private void pump(){
if(stopped)return;
if(ready()){save();if(task!=null)task.cancel();host.getLogger().info("SELFTEST world atlas READY: "+size+"x"+size);return;}
if(done-lastSaved>=512){save();host.getLogger().info("World atlas: "+progress()+"%");}
int budget=Math.max(1,Math.min(12,host.getConfig().getInt("scout-maps.chunk-loads-per-tick",6)));
for(int i=0;i<budget&&active<budget&&!queue.isEmpty();i++){
Batch b=queue.removeFirst();active++;
try{world.getChunkAtAsync(b.x,b.z,true).whenComplete((chunk,error)->{
if(stopped)return;
Runnable result=()->{active--;if(stopped)return;if(error!=null||chunk==null||!chunk.isLoaded()){retry(b,error);return;}
try{for(Pixel p:b.pixels){if((picture.getRGB(p.x,p.z)>>>24)!=0)continue;int y=world.getHighestBlockYAt(p.wx,p.wz,HeightMap.WORLD_SURFACE);Block block=world.getBlockAt(p.wx,y,p.wz);if(block.getType().isAir()&&y>world.getMinHeight())block=world.getBlockAt(p.wx,--y,p.wz);
int rgb=block.getType().isAir()?0x26323A:block.getBlockData().getMapColor().asRGB();
if(block.getType()==Material.WATER||block.getType()==Material.KELP||block.getType()==Material.SEAGRASS||block.getType()==Material.TALL_SEAGRASS)rgb=0x3C69AD;
picture.setRGB(p.x,p.z,0xff000000|rgb);done++;}revision++;}
catch(Exception ex){retry(b,ex);}
};if(Bukkit.isPrimaryThread())result.run();else if(host.isEnabled())Bukkit.getScheduler().runTask(host,result);
});}catch(Exception ex){active--;retry(b,ex);}
}
}
private void retry(Batch b,Throwable ex){if(++failed%100==1)host.getLogger().warning("Atlas chunk failed, retry scheduled: "+b.x+","+b.z+" "+ex);Bukkit.getScheduler().runTaskLater(host,()->{if(!stopped)queue.addLast(b);},100L);}
private void save(){try{Files.createDirectories(cache.getParent());Path temp=cache.resolveSibling(cache.getFileName()+".tmp");if(!ImageIO.write(picture,"PNG",temp.toFile()))throw new IllegalStateException("PNG writer unavailable");try{Files.move(temp,cache,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,cache,StandardCopyOption.REPLACE_EXISTING);}lastSaved=done;}catch(Exception ex){host.getLogger().warning("Atlas cache save failed: "+ex.getMessage());}}
void stop(){stopped=true;if(task!=null)task.cancel();if(done!=lastSaved)save();}
}
