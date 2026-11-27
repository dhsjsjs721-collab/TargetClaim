package dev.landminimap;
import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.nio.charset.StandardCharsets;
import java.util.*;
public record OverlayPayload(UUID viewer,UUID world,List<Point> points,List<Zone> zones) implements CustomPacketPayload {
    public record Point(int kind,int x,int y,int z,String label){}
    public record Zone(int kind,int x1,int z1,int x2,int z2,int y1,int y2,String label){}
    public static final Type<OverlayPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("landminimap","overlays"));
    public static final StreamCodec<RegistryFriendlyByteBuf,OverlayPayload> CODEC=new StreamCodec<>(){
        public OverlayPayload decode(RegistryFriendlyByteBuf b){
            if(b.readInt()!=1)throw new IllegalArgumentException("overlay version");
            UUID viewer=id(b),world=id(b);
            int n=b.readUnsignedByte();if(n>24)throw new IllegalArgumentException("points");
            List<Point> points=new ArrayList<>();
            for(int i=0;i<n;i++){
                int k=b.readUnsignedByte(),x=b.readInt(),y=b.readInt(),z=b.readInt();String s=label(b);
                if(k<1||k>6||!coord(x)||!coord(z)||Math.abs((long)y)>4096)throw new IllegalArgumentException("point");
                points.add(new Point(k,x,y,z,s));
            }
            int m=b.readUnsignedByte();if(m>64)throw new IllegalArgumentException("zones");
            List<Zone> zones=new ArrayList<>();
            for(int i=0;i<m;i++){
                int k=b.readUnsignedByte(),x1=b.readInt(),z1=b.readInt(),x2=b.readInt(),z2=b.readInt(),y1=b.readInt(),y2=b.readInt();String s=label(b);
                if(k<1||k>3||!coord(x1)||!coord(z1)||!coord(x2)||!coord(z2)||x1>x2||z1>z2||y1>y2)throw new IllegalArgumentException("zone");
                zones.add(new Zone(k,x1,z1,x2,z2,y1,y2,s));
            }
            if(b.isReadable())throw new IllegalArgumentException("trailing bytes");
            return new OverlayPayload(viewer,world,List.copyOf(points),List.copyOf(zones));
        }
        public void encode(RegistryFriendlyByteBuf b,OverlayPayload p){
            b.writeInt(1);id(b,p.viewer);id(b,p.world);b.writeByte(p.points.size());
            for(Point v:p.points){b.writeByte(v.kind);b.writeInt(v.x);b.writeInt(v.y);b.writeInt(v.z);label(b,v.label);}
            b.writeByte(p.zones.size());
            for(Zone v:p.zones){b.writeByte(v.kind);b.writeInt(v.x1);b.writeInt(v.z1);b.writeInt(v.x2);b.writeInt(v.z2);b.writeInt(v.y1);b.writeInt(v.y2);label(b,v.label);}
        }
    };
    private static boolean coord(int x){return Math.abs((long)x)<=30000000;}
    private static UUID id(RegistryFriendlyByteBuf b){return new UUID(b.readLong(),b.readLong());}
    private static void id(RegistryFriendlyByteBuf b,UUID id){b.writeLong(id.getMostSignificantBits());b.writeLong(id.getLeastSignificantBits());}
    private static String label(RegistryFriendlyByteBuf b){int n=b.readUnsignedByte();if(n<1||n>96)throw new IllegalArgumentException("label");byte[] data=new byte[n];b.readBytes(data);return new String(data,StandardCharsets.UTF_8);}
    private static void label(RegistryFriendlyByteBuf b,String s){byte[] data=s.getBytes(StandardCharsets.UTF_8);b.writeByte(data.length);b.writeBytes(data);}
    public static int pointColor(int kind){return switch(kind){case 1->0xffffffff;case 2->0xffc39bff;case 3->0xffff8fae;case 4->0xffbb86fc;case 5->0xfff9f285;case 6->0xffff6262;default->0xffffffff;};}
    public static int zoneColor(int kind){return switch(kind){case 1->0xff69e4a3;case 2->0xffff6969;case 3->0xffb19bff;default->0xffffffff;};}
    @Override public Type<OverlayPayload> type(){return TYPE;}
}
