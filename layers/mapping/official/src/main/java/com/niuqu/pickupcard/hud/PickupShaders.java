package com.niuqu.pickupcard.hud;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * 形状着色器的注册与持有。SDF 方案参考 ModernUI 的形状渲染（LGPL，仅借鉴算法思路），
 * 实现为本仓库清室重写的 MC core shader（uniform 驱动，一形状一个四边形一次 draw）。
 * <p>
 * 【为什么是 core shader 而不是手搓 Tesselator 状态】第一版自建 GL 状态管理在真机上
 * 整层不可见且无日志可查；core shader 走 vanilla 自己的 ShaderInstance 管线
 * （apply 时自动上传 ModelViewMat/ProjMat 并应用我们设置的 uniform），与原版
 * position_color 等着色器同一套机制，行为可预期。
 */
@Mod.EventBusSubscriber(modid = "pickupcard", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PickupShaders {

    @Nullable
    private static volatile ShaderInstance guiShape;

    private PickupShaders() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new net.minecraft.resources.ResourceLocation("pickupcard", "gui_shape"),
                DefaultVertexFormat.POSITION_TEX_COLOR), shader -> guiShape = shader);
    }

    @Nullable
    public static ShaderInstance guiShape() {
        return guiShape;
    }
}
