package dev.landminimap;
import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
public record ActionPayload(int op, UUID id, int x, int z, String label) implements CustomPacketPayload {
    public static final int ADD=1, REMOVE=2, VIEW=3, ALLOW=4, REVOKE=5, DENY=6;
    public static final UUID NONE=new UUID(0,0);
    public static final Type<ActionPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("landminimap","action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ActionPayload> CODEC=new StreamCodec<>() {
        public ActionPayload decode(RegistryFriendlyByteBuf b) {
            int op=b.readUnsignedByte();UUID id=new UUID(b.readLong(),b.readLong());
            int x=b.readInt(),z=b.readInt(),n=b.readUnsignedByte();
            if(n>96)throw new IllegalArgumentException("label");
            byte[] data=new byte[n];b.readBytes(data);
            return new ActionPayload(op,id,x,z,new String(data,StandardCharsets.UTF_8));
        }
        public void encode(RegistryFriendlyByteBuf b,ActionPayload a) {
            byte[] data=a.label.getBytes(StandardCharsets.UTF_8);
            if(data.length>96)throw new IllegalArgumentException("label");
            b.writeByte(a.op);b.writeLong(a.id.getMostSignificantBits());b.writeLong(a.id.getLeastSignificantBits());
            b.writeInt(a.x);b.writeInt(a.z);b.writeByte(data.length);b.writeBytes(data);
        }
    };
    @Override public Type<ActionPayload> type(){return TYPE;}
}
