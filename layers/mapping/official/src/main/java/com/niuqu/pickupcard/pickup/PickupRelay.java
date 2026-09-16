package com.niuqu.pickupcard.pickup;

import com.niuqu.pickupcard.ui.PickupBoard;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 把"某个实体被捡起了"翻译成"某件物品被捡起了"，再交给界面。
 * <p>
 * 【为什么优先从世界里的实体读】改名过的、带附魔/NBT 的物品只有实体身上那份才是全的。
 * 退回按数字 id 造栈时只剩默认名字 —— 这是兜底，不是常态。
 * <p>
 * 【线程】调用方（Mixin）注入在原版 {@code ensureRunningOnSameThread} 之后，已经在主线程，
 * 所以这里可以直接碰 DOM。这个前提一旦被改掉，症状会是偶发崩溃，而不是必崩 —— 改注入点时
 * 记得一起看这里。
 */
public final class PickupRelay {

    private PickupRelay() {
    }

    public static void onTakeItem(@Nullable ClientLevel level, ClientboundTakeItemEntityPacket packet) {
        ItemStack stack = resolve(level, packet);
        if (stack.isEmpty()) return;
        PickupBoard.INSTANCE.offer(stack, packet.getAmount());
    }

    /**
     * 从包反查物品栈。世界里实体还在就用实体那份（含改名与 NBT），
     * 已经被移除才退回"按 id 造一个"。
     */
    static ItemStack resolve(@Nullable ClientLevel level, ClientboundTakeItemEntityPacket packet) {
        if (level != null) {
            Entity entity = level.getEntity(packet.getItemId());
            if (entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getItem();
                if (!stack.isEmpty()) {
                    // 必须复制：实体下一 tick 就可能被移除，而我们的卡要活好几秒
                    return stack.copy();
                }
            }
        }
        Item item = Item.byId(packet.getItemId());
        return item == null ? ItemStack.EMPTY : new ItemStack(item, packet.getAmount());
    }
}
