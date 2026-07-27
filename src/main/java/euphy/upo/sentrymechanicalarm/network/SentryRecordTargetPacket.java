package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record SentryRecordTargetPacket(int entityId) implements CustomPacketPayload {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_record_target");
   public static final Type<SentryRecordTargetPacket> TYPE = new Type(ID);
   public static final StreamCodec<FriendlyByteBuf, SentryRecordTargetPacket> CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, SentryRecordTargetPacket::entityId, SentryRecordTargetPacket::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(SentryRecordTargetPacket packet, ServerPlayer player) {
      if (player.getMainHandItem().getItem() == Items.SPYGLASS && player.getOffhandItem().getItem() instanceof FireControlClipboardItem) {
         ItemStack offHand = player.getOffhandItem();
         Entity target = player.level().getEntity(packet.entityId());
         if (target != null && target.distanceToSqr(player) < 65536.0) {
            String name = target.getName().getString();
            addNameToListLogic(offHand, name, player);
         }
      }
   }

   private static void addNameToListLogic(ItemStack stack, String name, ServerPlayer player) {
      CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
      ListTag listTag;
      if (tag.contains("TargetList", 9)) {
         listTag = tag.getList("TargetList", 8);
      } else {
         listTag = new ListTag();
         tag.put("TargetList", listTag);
      }

      boolean alreadyExists = false;

      for (Tag t : listTag) {
         if (t.getAsString().equals(name)) {
            alreadyExists = true;
            break;
         }
      }

      if (!alreadyExists) {
         listTag.add(StringTag.valueOf(name));
         ItemNBTHelper.setTag(stack, tag);
         player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.added_target", new Object[]{name}), true);
      } else {
         player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.already_on_list", new Object[]{name}), true);
      }
   }
}
