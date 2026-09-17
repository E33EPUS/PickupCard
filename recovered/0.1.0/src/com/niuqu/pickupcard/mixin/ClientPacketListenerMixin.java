package com.niuqu.pickupcard.mixin;

import com.niuqu.pickupcard.pickup.PickupDispatcher;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
  @Shadow
  private ClientLevel f_104889_;

  public ClientPacketListenerMixin() {
  }

  @Inject(
    method = "handleTakeItemEntity",
    at = @At(
      value = "INVOKE",
      target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
      shift = Shift.AFTER
    )
  )
  private void pickupcard$onTakeItemEntity(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
    PickupDispatcher.handle(this.f_104889_, packet);
  }
}
