package dev.landminimap;
import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.nio.charset.StandardCharsets;
import java.util.*;
public record TeamPayload(UUID viewer, UUID world, List<Member> members,List<Mark> marks) implements CustomPacketPayload {
    public record Member(UUID id,String name,double x,double y,double z){}
    public record Mark(UUID id,UUID owner,int x,int y,int z,String label){}
    public static final Type<TeamPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("landminimap","team"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TeamPayload> CODEC=new StreamCodec<>(){
        public TeamPayload decode(RegistryFriendlyByteBuf in){
            int version=in.readInt();if(version!=1&&version!=2&&version!=3)throw new IllegalArgumentException("protocol");
            UUID viewer=readId(in),world=readId(in);int n=in.readUnsignedByte();if(n>16)throw new IllegalArgumentException("members");
            List<Member> members=new ArrayList<>();
            for(int i=0;i<n;i++){
                UUID id=readId(in);String name=readLabel(in,16);
                double x=in.readDouble(),y=in.readDouble(),z=in.readDouble();
                if(!name.matches("[A-Za-z0-9_]{1,16}")||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30000000||Math.abs(z)>30000000||Math.abs(y)>4096)throw new IllegalArgumentException("member");
                if(!id.equals(viewer))members.add(new Member(id,name,x,y,z));
            }
            List<Mark> marks=new ArrayList<>();
            if(version>=2){
                int count=in.readUnsignedByte();if(count>32)throw new IllegalArgumentException("marks");
                for(int i=0;i<count;i++){
                    UUID id=readId(in),owner=readId(in);int x=in.readInt(),z=in.readInt(),y=version>=3?in.readInt():64;String label=readLabel(in,96);
                    if(Math.abs((long)x)>30000000||Math.abs((long)z)>30000000||label.length()>24||label.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("mark");
                    marks.add(new Mark(id,owner,x,y,z,label));
                }
            }
            if(in.isReadable())throw new IllegalArgumentException("trailing bytes");
            return new TeamPayload(viewer,world,List.copyOf(members),List.copyOf(marks));
        }
        public void encode(RegistryFriendlyByteBuf out,TeamPayload p){
            out.writeInt(3);writeId(out,p.viewer);writeId(out,p.world);out.writeByte(p.members.size());
            for(Member m:p.members){writeId(out,m.id);writeLabel(out,m.name);out.writeDouble(m.x);out.writeDouble(m.y);out.writeDouble(m.z);}
            out.writeByte(p.marks.size());for(Mark m:p.marks){writeId(out,m.id);writeId(out,m.owner);out.writeInt(m.x);out.writeInt(m.z);out.writeInt(m.y);writeLabel(out,m.label);}
        }
    };
    private static String readLabel(RegistryFriendlyByteBuf in,int max){int n=in.readUnsignedByte();if(n<1||n>max)throw new IllegalArgumentException("label");byte[] b=new byte[n];in.readBytes(b);return new String(b,StandardCharsets.UTF_8);}
    private static void writeLabel(RegistryFriendlyByteBuf out,String s){byte[] b=s.getBytes(StandardCharsets.UTF_8);out.writeByte(b.length);out.writeBytes(b);}
    private static UUID readId(RegistryFriendlyByteBuf in){return new UUID(in.readLong(),in.readLong());}
    private static void writeId(RegistryFriendlyByteBuf out,UUID id){out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    @Override public Type<TeamPayload> type(){return TYPE;}
}
