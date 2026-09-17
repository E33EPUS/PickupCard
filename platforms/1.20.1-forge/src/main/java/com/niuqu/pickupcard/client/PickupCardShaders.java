package com.niuqu.pickupcard.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.render.shape.ShapeShaders;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/**
 * 形状着色器的注册——Forge 专有那一半。
 * <p>
 * 【为什么注册在平台层而不是形状层】RegisterShadersEvent 是 Forge API，而形状层要能
 * 同时编给 NeoForge（那边的注册入口不同）。平台层注册完把实例塞进 {@link ShapeShaders}，
 * 形状层就只认一个引用，不认任何加载器。
 */
@Mod.EventBusSubscriber(modid = PickupCard.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PickupCardShaders {

    private static final ResourceLocation SHAPE_ID = new ResourceLocation("pickupcard", "gui_shape");

    private PickupCardShaders() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHAPE_ID,
                        DefaultVertexFormat.POSITION_TEX_COLOR),
                ShapeShaders::bind);
        PickupCard.LOGGER.info("形状着色器已注册: {}", SHAPE_ID);
    }
}
