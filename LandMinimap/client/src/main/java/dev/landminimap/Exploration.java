package dev.landminimap;

import java.io.*;
import java.nio.file.*;
import java.util.*;

final class Exploration {
    private static final int SIZE=64, MAGIC=0x4C4D0100;
    private final Path directory;
    private record RegionKey(int x,int z) {}
    private static final class Region {
        final int[] colors=new int[SIZE*SIZE];
        boolean dirty;
    }
    private final LinkedHashMap<RegionKey,Region> regions=new LinkedHashMap<>(32,0.75f,true);
    Exploration(Path directory) { this.directory=directory; }
    private static RegionKey region(int x,int z) { return new RegionKey(Math.floorDiv(x,SIZE),Math.floorDiv(z,SIZE)); }
    private static int index(int x,int z) { return Math.floorMod(z,SIZE)*SIZE+Math.floorMod(x,SIZE); }
    private Path file(RegionKey key) { return directory.resolve(key.x+"_"+key.z+".bin"); }
    private Region load(RegionKey key) {
        Region r=regions.get(key);
        if(r!=null)return r;
        r=new Region();
        Path file=file(key);
        if(Files.isRegularFile(file)) {
            try(DataInputStream in=new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                if(in.readInt()!=MAGIC)throw new IOException("Map format");
                for(int i=0;i<r.colors.length;i++)r.colors[i]=in.readInt();
                if(in.read()!=-1)throw new IOException("Map length");
            } catch(IOException e) { Arrays.fill(r.colors,0); }
        }
        regions.put(key,r);
        if(regions.size()>100) {
            var entry=regions.entrySet().iterator().next();
            if(save(entry.getKey(),entry.getValue()))regions.remove(entry.getKey());
        }
        return r;
    }
    void prepare(int x,int z,int radius) {
        for(int rz=Math.floorDiv(z-radius,SIZE);rz<=Math.floorDiv(z+radius,SIZE);rz++)
            for(int rx=Math.floorDiv(x-radius,SIZE);rx<=Math.floorDiv(x+radius,SIZE);rx++)load(new RegionKey(rx,rz));
    }
    void visit(int x,int z,int color) {
        Region r=load(region(x,z)); int i=index(x,z);
        if(color!=0 && r.colors[i]!=color){ r.colors[i]=color; r.dirty=true; }
    }
    int color(int x,int z) {
        Region r=regions.get(region(x,z));
        return r==null?0:r.colors[index(x,z)];
    }
    private boolean save(RegionKey key,Region r) {
        if(!r.dirty)return true;
        Path tmp=null;
        try {
            Files.createDirectories(directory);
            tmp=Files.createTempFile(directory,"region-",".tmp");
            try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(MAGIC);for(int color:r.colors)out.writeInt(color);
            }
            try { Files.move(tmp,file(key),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e){ Files.move(tmp,file(key),StandardCopyOption.REPLACE_EXISTING); }
            r.dirty=false;return true;
        } catch(IOException e){ return false; }
        finally { if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(IOException ignored){} }
    }
    void flush(){ for(var e:regions.entrySet())save(e.getKey(),e.getValue()); }
}
