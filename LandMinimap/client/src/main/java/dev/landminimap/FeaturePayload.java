package dev.landminimap;
import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.nio.charset.StandardCharsets;
import java.util.*;
public record FeaturePayload(UUID viewer,UUID world,boolean sharing,int actualLayer,int layer,int cx,int cz,int radius,boolean truncated,Map<Long,Integer> colors,List<Status> team,List<Ping> pings) implements CustomPacketPayload {
    public record Status(UUID id,String name,float health,float max,int armor,int food,boolean speaking,boolean dead,boolean sameWorld,String race){}
    public record Ping(int x,int y,int z,String label){}
    public static final Type<FeaturePayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("landminimap","features"));
    static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    public static final StreamCodec<RegistryFriendlyByteBuf,FeaturePayload> CODEC=new StreamCodec<>(){
        public FeaturePayload decode(RegistryFriendlyByteBuf b){
            if(b.readInt()!=1)throw new IllegalArgumentException("version");UUID viewer=id(b),world=id(b);boolean sharing=b.readBoolean();
            int actual=b.readInt(),layer=b.readInt(),cx=b.readInt(),cz=b.readInt(),radius=b.readInt();boolean truncated=b.readBoolean();
            if(radius<32||radius>256||!validLayer(actual)||!validLayer(layer)||Math.abs((long)cx)>7500000||Math.abs((long)cz)>7500000)throw new IllegalArgumentException("view");
            int n=b.readUnsignedShort();if(n>2000)throw new IllegalArgumentException("cells");Map<Long,Integer> colors=new HashMap<>();
            for(int i=0;i<n;i++){int x=b.readInt(),z=b.readInt(),c=b.readInt();if(Math.abs((long)x-cx)>radius||Math.abs((long)z-cz)>radius)throw new IllegalArgumentException("cell");colors.put(key(x,z),c);}
            int m=b.readUnsignedByte();if(m>16)throw new IllegalArgumentException("team");List<Status> team=new ArrayList<>();
            for(int i=0;i<m;i++){UUID id=id(b);String name=str(b);float h=b.readFloat(),max=b.readFloat();int armor=b.readUnsignedByte(),food=b.readUnsignedByte();boolean speaking=b.readBoolean(),dead=b.readBoolean(),same=b.readBoolean();String race=str(b);if(!Float.isFinite(h)||!Float.isFinite(max)||max<=0)throw new IllegalArgumentException("health");team.add(new Status(id,name,h,max,armor,food,speaking,dead,same,race));}
            int k=b.readUnsignedByte();if(k>16)throw new IllegalArgumentException("pings");List<Ping> pings=new ArrayList<>();
            for(int i=0;i<k;i++){int x=b.readInt(),y=b.readInt(),z=b.readInt();if(Math.abs((long)x)>30000000||Math.abs((long)z)>30000000||Math.abs((long)y)>4096)throw new IllegalArgumentException("ping");pings.add(new Ping(x,y,z,str(b)));}
            if(b.isReadable())throw new IllegalArgumentException("trailing");
            return new FeaturePayload(viewer,world,sharing,actual,layer,cx,cz,radius,truncated,Map.copyOf(colors),List.copyOf(team),List.copyOf(pings));
        }
        public void encode(RegistryFriendlyByteBuf b,FeaturePayload f){
            b.writeInt(1);id(b,f.viewer);id(b,f.world);b.writeBoolean(f.sharing);b.writeInt(f.actualLayer);b.writeInt(f.layer);b.writeInt(f.cx);b.writeInt(f.cz);b.writeInt(f.radius);b.writeBoolean(f.truncated);
            b.writeShort(f.colors.size());for(var c:f.colors.entrySet()){b.writeInt((int)(c.getKey()>>32));b.writeInt((int)(long)c.getKey());b.writeInt(c.getValue());}
            b.writeByte(f.team.size());for(Status t:f.team){id(b,t.id);str(b,t.name);b.writeFloat(t.health);b.writeFloat(t.max);b.writeByte(t.armor);b.writeByte(t.food);b.writeBoolean(t.speaking);b.writeBoolean(t.dead);b.writeBoolean(t.sameWorld);str(b,t.race);}
            b.writeByte(f.pings.size());for(Ping p:f.pings){b.writeInt(p.x);b.writeInt(p.y);b.writeInt(p.z);str(b,p.label);}
        }
    };
    private static boolean validLayer(int n){return n==10000||n>=-256&&n<=256;}
    private static UUID id(RegistryFriendlyByteBuf b){return new UUID(b.readLong(),b.readLong());}
    private static void id(RegistryFriendlyByteBuf b,UUID id){b.writeLong(id.getMostSignificantBits());b.writeLong(id.getLeastSignificantBits());}
    private static String str(RegistryFriendlyByteBuf b){int n=b.readUnsignedByte();if(n>96)throw new IllegalArgumentException("text");byte[] data=new byte[n];b.readBytes(data);return new String(data,StandardCharsets.UTF_8);}
    private static void str(RegistryFriendlyByteBuf b,String s){byte[] data=s.getBytes(StandardCharsets.UTF_8);b.writeByte(data.length);b.writeBytes(data);}
    @Override public Type<FeaturePayload> type(){return TYPE;}
}
