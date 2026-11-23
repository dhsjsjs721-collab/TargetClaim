package dev.unified.machines;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.World;

/** Durable sparse index; never scans world chunks or forces them to load. */
final class MachineRegistry {
enum Kind { DAMAGE, LOADER }
record Position(UUID world,int x,int y,int z){
String id(){return world+":"+x+":"+y+":"+z;}
int cx(){return x>>4;}
int cz(){return z>>4;}
}
record Machine(Position position,UUID owner,Kind kind){}
private final Path path;
final LinkedHashMap<String,Machine> machines=new LinkedHashMap<>();
final Map<UUID,Machine> owners=new HashMap<>();
MachineRegistry(Path path){this.path=path;}
void load() throws IOException {
machines.clear();owners.clear();
if(!Files.exists(path))return;
Properties p=new Properties();
try(InputStream in=Files.newInputStream(path)){p.load(in);}
if(p.size()>20000)throw new IOException("Machine registry unexpectedly large.");
for(String id:p.stringPropertyNames()){
try{
String[] v=p.getProperty(id).split(";",-1);
if(v.length!=6)continue;
Kind kind=Kind.valueOf(v[0]);
UUID owner=UUID.fromString(v[1]);
Position pos=new Position(UUID.fromString(v[2]),Integer.parseInt(v[3]),Integer.parseInt(v[4]),Integer.parseInt(v[5]));
if(!pos.id().equals(id))continue;
Machine machine=new Machine(pos,owner,kind);
if(kind==Kind.LOADER && owners.containsKey(owner))continue;
if(machines.size()<10000){
machines.put(id,machine);
if(kind==Kind.LOADER)owners.put(owner,machine);
}
}catch(RuntimeException ignored){}
}
}
boolean add(Machine machine){
if(machines.containsKey(machine.position().id()))return false;
if(machine.kind()==Kind.LOADER && owners.containsKey(machine.owner()))return false;
machines.put(machine.position().id(),machine);
if(machine.kind()==Kind.LOADER)owners.put(machine.owner(),machine);
return true;
}
void remove(Machine machine){
machines.remove(machine.position().id());
if(machine.kind()==Kind.LOADER)owners.remove(machine.owner(),machine);
}
Machine at(Position p){return machines.get(p.id());}
int count(Kind kind){
int count=0;
for(Machine m:machines.values())if(m.kind()==kind)count++;
return count;
}
void save() throws IOException {
Files.createDirectories(path.getParent());
Properties p=new Properties();
for(Machine m:machines.values()){
Position pos=m.position();
p.setProperty(pos.id(),m.kind()+";"+m.owner()+";"+pos.world()+";"+pos.x()+";"+pos.y()+";"+pos.z());
}
Path tmp=Files.createTempFile(path.getParent(),"machines-", ".tmp");
try{
try(OutputStream out=Files.newOutputStream(tmp)){p.store(out,"UnifiedMachines sparse block registry. Backup together with worlds.");}
try{Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
catch(AtomicMoveNotSupportedException noAtomic){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
}finally{Files.deleteIfExists(tmp);}
}
}
