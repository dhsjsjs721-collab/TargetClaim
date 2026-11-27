package dev.landminimap;
import java.io.*;
import java.nio.file.*;
import java.util.*;
final class LocalMarks {
    private final Path path;
    private final List<TeamPayload.Mark> marks=new ArrayList<>();
    LocalMarks(Path path,UUID owner){
        this.path=path;
        if(!Files.isRegularFile(path))return;
        try(DataInputStream in=new DataInputStream(Files.newInputStream(path))){
            int version=in.readInt();if(version!=0x4C4D0201&&version!=0x4C4D0401)return;
            int n=in.readUnsignedByte();if(n>64)return;
            for(int i=0;i<n;i++){UUID id=new UUID(in.readLong(),in.readLong());int x=in.readInt(),z=in.readInt(),y=version==0x4C4D0401?in.readInt():64;String label=in.readUTF();if(label.length()<=24&&Math.abs((long)x)<=30000000&&Math.abs((long)z)<=30000000)marks.add(new TeamPayload.Mark(id,owner,x,y,z,label));}
        }catch(IOException e){marks.clear();}
    }
    List<TeamPayload.Mark> all(){return List.copyOf(marks);}
    boolean add(TeamPayload.Mark m){if(marks.size()>=64)return false;marks.add(m);if(save())return true;marks.remove(m);return false;}
    boolean remove(UUID id){var old=new ArrayList<>(marks);if(!marks.removeIf(m->m.id().equals(id)))return false;if(save())return true;marks.clear();marks.addAll(old);return false;}
    private boolean save(){
        try{
            Files.createDirectories(path.getParent());Path tmp=path.resolveSibling("personal.tmp");
            try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(tmp))){out.writeInt(0x4C4D0401);out.writeByte(marks.size());for(var m:marks){out.writeLong(m.id().getMostSignificantBits());out.writeLong(m.id().getLeastSignificantBits());out.writeInt(m.x());out.writeInt(m.z());out.writeInt(m.y());out.writeUTF(m.label());}}
            try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}return true;
        }catch(IOException e){return false;}
    }
}
