package com.niuqu.pickupcard.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.niuqu.pickupcard.PickupCard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

@OnlyIn(Dist.CLIENT)
public final class SdfRectRenderer {
  private static final ResourceLocation SHADER_ID = ResourceLocation.fromNamespaceAndPath("pickupcard", "sdf_rect");
  @Nullable
  private static ShaderInstance shader;

  private SdfRectRenderer() {
  }

  public static void register(RegisterShadersEvent event) {
    try {
      event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER_ID, DefaultVertexFormat.POSITION_COLOR), loaded -> shader = loaded);
    } catch (Exception var2) {
      PickupCard.LOGGER.error("[pickupcard] SDF \u7740\u8272\u5668\u52a0\u8f7d\u5931\u8d25\uff0c\u5706\u89d2\u9000\u5316\u4e3a\u76f4\u89d2", var2);
    }
  }

  public static boolean available() {
    return shader != null;
  }

  public static void fill(GuiGraphics graphics, int x1, int y1, int x2, int y2, float radius, int argb) {
    draw(graphics, x1, y1, x2, y2, radius, argb, 0.0F, argb, null);
  }

  public static void fillWithBorder(GuiGraphics graphics, int x1, int y1, int x2, int y2, float radius, int fillArgb, float borderThickness, int borderArgb) {
    draw(graphics, x1, y1, x2, y2, radius, fillArgb, borderThickness, borderArgb, null);
  }

  public static void fillCard(
    GuiGraphics graphics, int x1, int y1, int x2, int y2, float radius, int fillArgb, float borderThickness, int borderArgb, SdfRectRenderer.Sheen sheen
  ) {
    draw(graphics, x1, y1, x2, y2, radius, fillArgb, borderThickness, borderArgb, sheen);
  }

  private static void draw(
    GuiGraphics graphics, int x1, int y1, int x2, int y2, float radius, int fillArgb, float borderThickness, int borderArgb, SdfRectRenderer.Sheen sheen
  ) {
    ShaderInstance shaderInstance = shader;
    if (x2 - x1 > 0 && y2 - y1 > 0) {
      float clamped = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2.0F);
      if (shaderInstance != null && !(clamped <= 0.0F)) {
        graphics.flush();
        Matrix4f pose = graphics.pose().last().pose();
        float a = pose.m00();
        float b = pose.m10();
        float tx = pose.m30();
        float c = pose.m01();
        float d = pose.m11();
        float ty = pose.m31();
        float det = a * d - b * c;
        if (Math.abs(det) < 1.0E-6F) {
          graphics.fill(x1, y1, x2, y2, fillArgb);
        } else {
          shaderInstance.safeGetUniform("u_LocalA").set(d / det, -b / det, (b * ty - d * tx) / det);
          shaderInstance.safeGetUniform("u_LocalB").set(-c / det, a / det, (c * tx - a * ty) / det);
          shaderInstance.safeGetUniform("u_Rect").set((x1 + x2) / 2.0F, (y1 + y2) / 2.0F, (x2 - x1) / 2.0F, (y2 - y1) / 2.0F);
          shaderInstance.safeGetUniform("u_Radius").set(clamped);
          shaderInstance.safeGetUniform("u_Border").set(borderThickness, borderThickness > 0.0F ? 1.0F : 0.0F);
          if (sheen != null) {
            shaderInstance.safeGetUniform("u_Sheen").set((float)sheen.centerLocalX(), (float)sheen.halfWidth(), (float)sheen.strength(), 1.0F);
            shaderInstance.safeGetUniform("u_SheenTint")
              .set((sheen.colorArgb() >> 16 & 0xFF) / 255.0F, (sheen.colorArgb() >> 8 & 0xFF) / 255.0F, (sheen.colorArgb() & 0xFF) / 255.0F);
          } else {
            shaderInstance.safeGetUniform("u_Sheen").set(0.0F, 0.0F, 0.0F, 0.0F);
            shaderInstance.safeGetUniform("u_SheenTint").set(1.0F, 1.0F, 1.0F);
          }

          shaderInstance.safeGetUniform("u_BorderColor")
            .set((borderArgb >> 16 & 0xFF) / 255.0F, (borderArgb >> 8 & 0xFF) / 255.0F, (borderArgb & 0xFF) / 255.0F, (borderArgb >>> 24 & 0xFF) / 255.0F);
          float fillA = (fillArgb >>> 24) / 255.0F;
          float fillR = (fillArgb >> 16 & 0xFF) / 255.0F;
          float fillG = (fillArgb >> 8 & 0xFF) / 255.0F;
          float fillB = (fillArgb & 0xFF) / 255.0F;
          boolean blendWasOn = GL11.glIsEnabled(3042);
          RenderSystem.enableBlend();
          RenderSystem.defaultBlendFunc();
          RenderSystem.setShader(() -> shaderInstance);
          BufferBuilder buffer = Tesselator.getInstance().getBuilder();
          buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
          buffer.vertex(pose, x1, y1, 0.0F).color(fillR, fillG, fillB, fillA).endVertex();
          buffer.vertex(pose, x1, y2, 0.0F).color(fillR, fillG, fillB, fillA).endVertex();
          buffer.vertex(pose, x2, y2, 0.0F).color(fillR, fillG, fillB, fillA).endVertex();
          buffer.vertex(pose, x2, y1, 0.0F).color(fillR, fillG, fillB, fillA).endVertex();
          BufferUploader.drawWithShader(buffer.end());
          if (!blendWasOn) {
            RenderSystem.disableBlend();
          }
        }
      } else {
        graphics.fill(x1, y1, x2, y2, fillArgb);
        if (borderThickness > 0.0F) {
          graphics.fill(x1, y1, x2, y1 + 1, borderArgb);
          graphics.fill(x1, y2 - 1, x2, y2, borderArgb);
          graphics.fill(x1, y1, x1 + 1, y2, borderArgb);
          graphics.fill(x2 - 1, y1, x2, y2, borderArgb);
        }
      }
    }
  }

  public record Sheen(double centerLocalX, double halfWidth, double strength, int colorArgb) {
  }
}
