package com.niuqu.pickupcard.pickup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 把"某个实体被捡起了"翻译成"我捡到了什么"，交给账本。
 * <p>
 * 【谁捡的必须查——这是 v0.1.0 的真 bug】这个包广播视野内<b>所有人</b>的拾取，
 * {@code getPlayerId()} 是拾取者的实体 id。v0.1.0 从来没查过它：其他玩家捡了东西
 * 会给你弹卡，僵尸捡起装备也会给你弹卡。现在非玩家拾取与他人的拾取都在源头丢弃，
 * 只留"我"的。物品实体与经验球都走这个包（经验球经 {@code LivingEntity.take} 广播，
 * 字节码核对过），所以 XP 卡是同一个入口。
 * <p>
 * 【世界里实体还在才认识它】改名过的、带附魔/NBT 的物品只有实体身上那份才是全的。
 * 实体已经被移除（网络竞态）就直接放弃——旧版曾退回"按数字 id 造一个栈"，但那个
 * 数字是<b>网络实体 id</b>，当注册表 id 用造出来的要么是空气要么是错物品，宁可不弹。
 * <p>
 * 【线程】Mixin 注入在 {@code ensureRunningOnSameThread} 之后，已经在主线程，
 * 可以直接操作账本。改注入点时记得一起看这里——前提一旦破坏，症状是偶发崩溃。
 */
public final class PickupRelay {

    private PickupRelay() {
    }

    public static void onTakeItem(@Nullable ClientLevel level, ClientboundTakeItemEntityPacket packet) {
        if (level == null) return;
        if (!(level.getEntity(packet.getPlayerId()) instanceof AbstractClientPlayer collector)) return;
        if (!isSelf(collector)) return;

        Entity carried = level.getEntity(packet.getItemId());
        if (carried instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) return;
            // 必须复制：实体下一 tick 就可能被移除，而我们的卡要活好几秒
            Inbox.INSTANCE.offer(new CardContent.Item(stack.copy()), packet.getAmount());
        } else if (carried instanceof ExperienceOrb) {
            // 数量就是包里的经验值（take 广播的是实际吸收的量）
            Inbox.INSTANCE.offer(new CardContent.Experience(), packet.getAmount());
        }
    }

    /** UUID 比对而不是引用比对：本地玩家实体在换维度时可能被重建。 */
    private static boolean isSelf(AbstractClientPlayer collector) {
        LocalPlayer self = Minecraft.getInstance().player;
        return self != null && self.getUUID().equals(collector.getUUID());
    }
}
