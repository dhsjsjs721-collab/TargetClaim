package dev.landminimap;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.nio.charset.StandardCharsets;
import java.util.*;

public record TeamPayload(UUID viewer, UUID world, List<Member> members) implements CustomPacketPayload {
    public record Member(UUID id, String name, double x, double y, double z) {}
    public static final Type<TeamPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("landminimap", "team"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamPayload> CODEC = new StreamCodec<>() {
        @Override public TeamPayload decode(RegistryFriendlyByteBuf in) {
            if (in.readInt() != 1) throw new IllegalArgumentException("Unsupported minimap protocol");
            UUID viewer = readId(in), world = readId(in);
            int count = in.readUnsignedByte();
            if (count > 16) throw new IllegalArgumentException("Too many teammates");
            List<Member> members = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                UUID id = readId(in);
                int length = in.readUnsignedByte();
                if (length < 1 || length > 16) throw new IllegalArgumentException("Invalid name length");
                byte[] bytes = new byte[length]; in.readBytes(bytes);
                String name = new String(bytes, StandardCharsets.UTF_8);
                double x=in.readDouble(), y=in.readDouble(), z=in.readDouble();
                if (!name.matches("[A-Za-z0-9_]{1,16}") || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                        || Math.abs(x)>30000000 || Math.abs(z)>30000000 || Math.abs(y)>4096)
                    throw new IllegalArgumentException("Invalid teammate");
                if (!id.equals(viewer)) members.add(new Member(id,name,x,y,z));
            }
            if (in.isReadable()) throw new IllegalArgumentException("Unexpected payload bytes");
            return new TeamPayload(viewer,world,List.copyOf(members));
        }
        @Override public void encode(RegistryFriendlyByteBuf out, TeamPayload packet) {
            out.writeInt(1); writeId(out,packet.viewer); writeId(out,packet.world); out.writeByte(packet.members.size());
            for (Member m:packet.members) {
                writeId(out,m.id); byte[] name=m.name.getBytes(StandardCharsets.UTF_8);
                out.writeByte(name.length); out.writeBytes(name);
                out.writeDouble(m.x); out.writeDouble(m.y); out.writeDouble(m.z);
            }
        }
    };
    private static UUID readId(RegistryFriendlyByteBuf in) { return new UUID(in.readLong(),in.readLong()); }
    private static void writeId(RegistryFriendlyByteBuf out,UUID id) { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    @Override public Type<TeamPayload> type() { return TYPE; }
}
