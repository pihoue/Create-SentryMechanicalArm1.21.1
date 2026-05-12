package euphy.upo.sentrymechanicalarm.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

public class ModCodecs {

    public static final StreamCodec<ByteBuf, Vec3> VEC3_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public Vec3 decode(ByteBuf buf) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            return new Vec3(x, y, z);
        }

        @Override
        public void encode(ByteBuf buf, Vec3 vec) {
            buf.writeDouble(vec.x);
            buf.writeDouble(vec.y);
            buf.writeDouble(vec.z);
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, Vec3> VEC3_STREAM_CODEC_REGISTRY = new StreamCodec<>() {
        @Override
        public Vec3 decode(RegistryFriendlyByteBuf buf) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            return new Vec3(x, y, z);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, Vec3 vec) {
            buf.writeDouble(vec.x);
            buf.writeDouble(vec.y);
            buf.writeDouble(vec.z);
        }
    };

    public static final StreamCodec<FriendlyByteBuf, CompoundTag> COMPOUND_TAG_CODEC = new StreamCodec<>() {
        @Override
        public CompoundTag decode(FriendlyByteBuf buf) {
            return buf.readNbt();
        }

        @Override
        public void encode(FriendlyByteBuf buf, CompoundTag tag) {
            buf.writeNbt(tag);
        }
    };
}