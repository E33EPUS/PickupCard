package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.anim.AnimationSpec;
import com.niuqu.pickupcard.anim.AnimationStyle;
import com.niuqu.pickupcard.configui.ConfigOption;
import com.niuqu.pickupcard.layout.GrowthDirection;
import com.niuqu.pickupcard.layout.LayoutSpec;
import com.niuqu.pickupcard.layout.ScreenAnchor;
import com.niuqu.pickupcard.pickup.Pickup;
import com.niuqu.pickupcard.render.NoticeRenderSettings;
import com.niuqu.pickupcard.style.NoticeStyle;
import com.niuqu.pickupcard.text.CountFormat;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.EnumValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public final class ClientConfig {
  private static final String KEY = "pickupcard.config.";
  public static final ForgeConfigSpec SPEC;
  public static final List<ConfigOption> OPTIONS;
  private static final BooleanValue ENABLED;
  private static final EnumValue<NoticeStyle> STYLE;
  private static final BooleanValue SHOW_ITEM_NAME;
  private static final EnumValue<CountFormat> COUNT_FORMAT;
  private static final ConfigValue<List<? extends String>> ITEM_BLACKLIST;
  private static final IntValue ENTER_MS;
  private static final IntValue HOLD_MS;
  private static final IntValue EXIT_MS;
  private static final EnumValue<AnimationStyle> ANIMATION_STYLE;
  private static final DoubleValue ENTER_TILT;
  private static final BooleanValue PULSE_ENABLED;
  private static final IntValue PULSE_MS;
  private static final DoubleValue PULSE_STRENGTH;
  private static final IntValue MERGE_ROLL_MS;
  private static final BooleanValue MERGE_ENABLED;
  private static final IntValue MERGE_WINDOW_MS;
  private static final EnumValue<ScreenAnchor> ANCHOR;
  private static final EnumValue<GrowthDirection> GROWTH;
  private static final IntValue MARGIN_X;
  private static final IntValue MARGIN_Y;
  private static final IntValue SEPARATION;
  private static final IntValue SLIDE_DISTANCE;
  private static final DoubleValue SCALE;
  private static final IntValue MAX_ON_SCREEN;
  private static final IntValue QUEUE_SIZE;
  private static final ConfigValue<String> BACKGROUND;
  private static final ConfigValue<String> BORDER;
  private static final DoubleValue BORDER_THICKNESS;
  private static final IntValue CORNER_RADIUS;
  private static final DoubleValue FRAME_THICKNESS;
  private static final ConfigValue<String> NAME_COLOR;
  private static final ConfigValue<String> ACCENT_COLOR;
  private static final BooleanValue SHOW_SHEEN;
  private static final BooleanValue SHOW_DURABILITY;
  private static final BooleanValue SHOW_NEW_BADGE;
  private static final BooleanValue RARITY_SKINS;
  private static final IntValue NAME_MAX_WIDTH;
  private static final BooleanValue SHOW_ITEM_ID;
  private static final String STYLE_MENU = "\u52a8\u753b\u98ce\u683c\uff08\u4e00\u4e2a\u9009\u9879\u540c\u65f6\u51b3\u5b9a \u8fdb\u573a / \u843d\u5b9a / \u9000\u573a / \u8109\u51b2\uff0c\u4e0d\u7528\u4f60\u53bb\u6311\u66f2\u7ebf\uff09\uff1a\n  CLEAN    \u5e72\u51c0\u5229\u843d\uff1a\u5feb\u8fdb\u5feb\u51fa\uff0c\u4e0d\u5f39\u4e0d\u7f29\n  SOFT     \u67d4\u548c\uff1a\u4e24\u5934\u90fd\u7f13\uff0c\u8fdb\u573a\u672b\u5c3e\u8f7b\u8f7b\u843d\u5b9a\u4e00\u4e0b\n  SPRINGY  \u6709\u5f39\u6027\uff1a\u8fdb\u573a\u8f7b\u5fae\u8fc7\u51b2\u518d\u56de\u6b63\uff0c\u9000\u573a\u6536\u4e00\u70b9\uff08\u63a8\u8350\uff09\n  BOUNCY   \u8e66\u8df3\uff1a\u8fc7\u51b2\u660e\u663e\u3001\u8109\u51b2\u66f4\u5927\uff0c\u60f3\u8ba9\u4eba\u4e00\u773c\u6ce8\u610f\u5230\u5c31\u7528\u5b83\n\u900f\u660e\u5ea6\u6c38\u8fdc\u8d70\u6700\u5e73\u6ed1\u7684\u66f2\u7ebf\uff08\u5b83\u4e0d\u80fd\u8fc7\u51b2\uff0c\u8fc7\u51b2\u5c31\u4f1a\u95ea\u4e00\u4e0b\uff09\uff0c\u4e0e\u4f60\u9009\u7684\u98ce\u683c\u65e0\u5173\u3002";

  private ClientConfig() {
  }

  public static boolean enabled() {
    return (Boolean)ENABLED.get();
  }

  public static NoticeStyle style() {
    return (NoticeStyle)STYLE.get();
  }

  public static boolean mergeEnabled() {
    return (Boolean)MERGE_ENABLED.get();
  }

  public static int mergeWindowMs() {
    return (Integer)MERGE_WINDOW_MS.get();
  }

  public static int maxOnScreen() {
    return (Integer)MAX_ON_SCREEN.get();
  }

  public static int queueSize() {
    return (Integer)QUEUE_SIZE.get();
  }

  public static ScreenAnchor anchor() {
    return (ScreenAnchor)ANCHOR.get();
  }

  public static GrowthDirection growth() {
    return (GrowthDirection)GROWTH.get();
  }

  public static AnimationSpec animationSpec() {
    AnimationStyle style = (AnimationStyle)ANIMATION_STYLE.get();
    return new AnimationSpec(
      (Integer)ENTER_MS.get(),
      (Integer)HOLD_MS.get(),
      (Integer)EXIT_MS.get(),
      style,
      (Boolean)PULSE_ENABLED.get(),
      (Integer)PULSE_MS.get(),
      (Double)PULSE_STRENGTH.get() * style.pulseScale(),
      (Double)ENTER_TILT.get() * style.tiltScale()
    );
  }

  public static NoticeRenderSettings renderSettings() {
    return new NoticeRenderSettings(
      (Double)SCALE.get(),
      parseArgb((String)BACKGROUND.get(), -1273491674),
      parseArgb((String)BORDER.get(), 956290923),
      ((Double)BORDER_THICKNESS.get()).floatValue(),
      (Integer)CORNER_RADIUS.get(),
      parseArgb((String)NAME_COLOR.get(), -988929),
      parseArgb((String)ACCENT_COLOR.get(), -10389),
      (Boolean)SHOW_ITEM_NAME.get(),
      (CountFormat)COUNT_FORMAT.get(),
      (Integer)MERGE_ROLL_MS.get(),
      ((Double)FRAME_THICKNESS.get()).floatValue(),
      (Boolean)SHOW_SHEEN.get(),
      (Boolean)SHOW_DURABILITY.get(),
      (Boolean)SHOW_NEW_BADGE.get(),
      (Integer)NAME_MAX_WIDTH.get(),
      (Boolean)SHOW_ITEM_ID.get(),
      (Boolean)RARITY_SKINS.get()
    );
  }

  public static LayoutSpec layoutSpec(int entryWidth, int entryHeight) {
    return new LayoutSpec(
      (ScreenAnchor)ANCHOR.get(),
      (GrowthDirection)GROWTH.get(),
      (Integer)MARGIN_X.get(),
      (Integer)MARGIN_Y.get(),
      (Integer)SEPARATION.get(),
      (Integer)SLIDE_DISTANCE.get(),
      entryWidth,
      entryHeight
    );
  }

  public static boolean passesFilter(Pickup pickup) {
    List<? extends String> blacklist = (List<? extends String>)ITEM_BLACKLIST.get();
    if (blacklist.isEmpty()) {
      return true;
    } else {
      String id = BuiltInRegistries.ITEM.getKey(pickup.stack().getItem()).toString();
      return !blacklist.contains(id);
    }
  }

  private static int parseArgb(String raw, int fallback) {
    if (raw == null) {
      return fallback;
    } else {
      String text = raw.trim();
      if (text.startsWith("#")) {
        text = text.substring(1);
      }

      if (text.length() != 8) {
        return fallback;
      } else {
        try {
          return (int)Long.parseLong(text, 16);
        } catch (NumberFormatException var4) {
          return fallback;
        }
      }
    }
  }

  static {
    Builder builder = new Builder();
    builder.comment(
        "\u62fe\u8d77\u5361\u7247 / Pickup Card \u2014\u2014 \u7eaf\u5ba2\u6237\u7aef\uff0c\u6240\u6709\u8bbe\u7f6e\u53ea\u5f71\u54cd\u4f60\u81ea\u5df1\u770b\u5230\u7684\u753b\u9762\u3002"
      )
      .push("general");
    ENABLED = builder.translation("pickupcard.config.enabled")
      .comment("\u603b\u5f00\u5173\u3002\u5173\u6389\u4e4b\u540e\u6361\u4e1c\u897f\u4e0d\u518d\u5f39\u5361\u7247\u3002")
      .define("enabled", true);
    STYLE = builder.translation("pickupcard.config.style")
      .comment(
        "\u5361\u7247\u5916\u89c2\uff1a\n  CARD     \u5b8c\u6574\u5361\u7247\uff08\u7a00\u6709\u5ea6\u5361\u6846 + \u56fe\u6807\u5361\u69fd + \u7269\u54c1\u540d + \u6570\u91cf\u80f6\u56ca\uff09\n  MINIMAL  \u6781\u7b80\uff08\u53ea\u6709\u56fe\u6807 + \u6570\u91cf\uff0c\u51e0\u4e4e\u4e0d\u6321\u89c6\u91ce\uff09"
      )
      .defineEnum("style", NoticeStyle.CARD);
    SHOW_ITEM_NAME = builder.translation("pickupcard.config.showItemName")
      .comment("CARD \u5916\u89c2\u4e0b\u662f\u5426\u663e\u793a\u7269\u54c1\u540d\u5b57\u3002\u5173\u6389\u53ea\u7559\u56fe\u6807\u548c\u6570\u91cf\u3002")
      .define("showItemName", true);
    COUNT_FORMAT = builder.translation("pickupcard.config.countFormat")
      .comment(
        "\u6570\u91cf\u600e\u4e48\u5199\uff1a\n  X_PREFIX      \u00d764\n  PLAIN         64\n  ABBREVIATED   1.2K\uff08\u6570\u91cf\u5f88\u5927\u65f6\u4e0d\u5360\u5bbd\u5ea6\uff09"
      )
      .defineEnum("countFormat", CountFormat.X_PREFIX);
    ITEM_BLACKLIST = builder.translation("pickupcard.config.itemBlacklist")
      .comment("\u8fd9\u4e9b\u7269\u54c1\u6361\u5230\u65f6\u4e0d\u5f39\u5361\u7247\u3002\u5199\u6cd5\uff1aminecraft:cobblestone")
      .defineListAllowEmpty("itemBlacklist", List.of(), o -> o instanceof String);
    builder.pop();
    builder.comment("\u52a8\u753b / animation").push("animation");
    ENTER_MS = builder.translation("pickupcard.config.enterMs")
      .comment(
        "\u51fa\u73b0\uff08\u6de1\u5165\uff09\u7528\u591a\u5c11\u6beb\u79d2\u3002\u592a\u77ed\u4f1a\u663e\u5f97\u201c\u556a\u201d\u4e00\u4e0b\u8e66\u51fa\u6765\uff0c260~320 \u6700\u8212\u670d\u3002"
      )
      .defineInRange("enterMs", 280, 0, 5000);
    HOLD_MS = builder.translation("pickupcard.config.holdMs")
      .comment("\u505c\u7559\u591a\u4e45\uff08\u6beb\u79d2\uff09\u3002")
      .defineInRange("holdMs", 2400, 100, 60000);
    EXIT_MS = builder.translation("pickupcard.config.exitMs")
      .comment("\u6d88\u5931\uff08\u6de1\u51fa\uff09\u7528\u591a\u5c11\u6beb\u79d2\u3002")
      .defineInRange("exitMs", 420, 0, 5000);
    ANIMATION_STYLE = builder.translation("pickupcard.config.animationStyle")
      .comment(
        "\u52a8\u753b\u98ce\u683c\uff08\u4e00\u4e2a\u9009\u9879\u540c\u65f6\u51b3\u5b9a \u8fdb\u573a / \u843d\u5b9a / \u9000\u573a / \u8109\u51b2\uff0c\u4e0d\u7528\u4f60\u53bb\u6311\u66f2\u7ebf\uff09\uff1a\n  CLEAN    \u5e72\u51c0\u5229\u843d\uff1a\u5feb\u8fdb\u5feb\u51fa\uff0c\u4e0d\u5f39\u4e0d\u7f29\n  SOFT     \u67d4\u548c\uff1a\u4e24\u5934\u90fd\u7f13\uff0c\u8fdb\u573a\u672b\u5c3e\u8f7b\u8f7b\u843d\u5b9a\u4e00\u4e0b\n  SPRINGY  \u6709\u5f39\u6027\uff1a\u8fdb\u573a\u8f7b\u5fae\u8fc7\u51b2\u518d\u56de\u6b63\uff0c\u9000\u573a\u6536\u4e00\u70b9\uff08\u63a8\u8350\uff09\n  BOUNCY   \u8e66\u8df3\uff1a\u8fc7\u51b2\u660e\u663e\u3001\u8109\u51b2\u66f4\u5927\uff0c\u60f3\u8ba9\u4eba\u4e00\u773c\u6ce8\u610f\u5230\u5c31\u7528\u5b83\n\u900f\u660e\u5ea6\u6c38\u8fdc\u8d70\u6700\u5e73\u6ed1\u7684\u66f2\u7ebf\uff08\u5b83\u4e0d\u80fd\u8fc7\u51b2\uff0c\u8fc7\u51b2\u5c31\u4f1a\u95ea\u4e00\u4e0b\uff09\uff0c\u4e0e\u4f60\u9009\u7684\u98ce\u683c\u65e0\u5173\u3002"
      )
      .defineEnum("animationStyle", AnimationStyle.SPRINGY);
    ENTER_TILT = builder.translation("pickupcard.config.enterTilt")
      .comment(
        "\u51fa\u73b0\u65f6\u5361\u7247\u7684\u503e\u659c\u89d2\u5ea6\uff08\u5ea6\uff09\uff0c0 = \u4e0d\u503e\u659c\u3001\u76f4\u63a5\u6de1\u5165\u3002\n\u60f3\u8c61\u6210\u628a\u4e00\u5f20\u724c\u7529\u5230\u684c\u9762\u4e0a\uff1a\u6570\u503c\u8d8a\u5927\uff0c\u8fdb\u573a\u65f6\u8d8a\"\u6b6a\"\uff0c\u8f6c\u6b63\u7684\u8fc7\u7a0b\u8d8a\u660e\u663e\u30027~10 \u6bd4\u8f83\u81ea\u7136\u3002"
      )
      .defineInRange("enterTilt", 8.0, 0.0, 20.0);
    PULSE_ENABLED = builder.translation("pickupcard.config.pulseEnabled")
      .comment("\u77ed\u65f6\u95f4\u5185\u53c8\u6361\u5230\u540c\u4e00\u79cd\u7269\u54c1\u65f6\uff0c\u5361\u7247\u201c\u5f39\u4e00\u4e0b\u201d\u3002")
      .define("pulseEnabled", true);
    PULSE_MS = builder.translation("pickupcard.config.pulseMs")
      .comment("\u5f39\u4e00\u4e0b\u6301\u7eed\u591a\u5c11\u6beb\u79d2\u3002")
      .defineInRange("pulseMs", 350, 0, 3000);
    PULSE_STRENGTH = builder.translation("pickupcard.config.pulseStrength")
      .comment("\u5f39\u7684\u5e45\u5ea6\uff1a0.12 = \u6700\u5927\u653e\u5927 12%\u3002")
      .defineInRange("pulseStrength", 0.12, 0.0, 1.0);
    MERGE_ROLL_MS = builder.translation("pickupcard.config.mergeRollMs")
      .comment("\u5408\u5e76\u540c\u7c7b\u65f6\uff0c\u6570\u5b57\u4ece\u65e7\u503c\u6eda\u5230\u65b0\u503c\u7528\u591a\u5c11\u6beb\u79d2\u3002")
      .defineInRange("mergeRollMs", 260, 0, 2000);
    MERGE_ENABLED = builder.translation("pickupcard.config.mergeEnabled")
      .comment(
        "\u77ed\u65f6\u95f4\u5185\u6361\u5230\u540c\u4e00\u79cd\u7269\u54c1\uff0c\u5408\u5e76\u6210\u4e00\u5f20\u5361\u7247\uff08\u800c\u4e0d\u662f\u5237\u5c4f\uff09\u3002"
      )
      .define("mergeEnabled", true);
    MERGE_WINDOW_MS = builder.translation("pickupcard.config.mergeWindowMs")
      .comment(
        "\u591a\u4e45\u4e4b\u5185\u7b97\u201c\u540c\u4e00\u6b21\u201d\uff1a\u8d85\u8fc7\u8fd9\u4e2a\u65f6\u95f4\u5c31\u662f\u4e24\u5f20\u72ec\u7acb\u7684\u5361\u7247\u3002"
      )
      .defineInRange("mergeWindowMs", 1200, 0, 10000);
    builder.pop();
    builder.comment("\u4f4d\u7f6e\u4e0e\u5806\u53e0 / layout").push("layout");
    ANCHOR = builder.translation("pickupcard.config.anchor")
      .comment(
        "\u5361\u7247\u8d34\u5c4f\u5e55\u7684\u54ea\u4e2a\u89d2\uff1a\n  BOTTOM_RIGHT  \u53f3\u4e0b\u89d2\uff08\u9ed8\u8ba4\uff0c\u8ba9\u5f00\u5feb\u6377\u680f\uff09\n  BOTTOM_LEFT   \u5de6\u4e0b\u89d2\n  TOP_RIGHT     \u53f3\u4e0a\u89d2\n  TOP_LEFT      \u5de6\u4e0a\u89d2"
      )
      .defineEnum("anchor", ScreenAnchor.BOTTOM_RIGHT);
    GROWTH = builder.translation("pickupcard.config.growth")
      .comment(
        "\u591a\u5f20\u5361\u7247\u600e\u4e48\u6392\uff1a\n  NATURAL   \u65b0\u6765\u7684\u8d34\u4f4f\u89d2\u843d\uff0c\u65e7\u7684\u88ab\u5f80\u5916\u63a8\uff08\u9ed8\u8ba4\uff09\n  REVERSED  \u53cd\u8fc7\u6765\uff1a\u65b0\u6765\u7684\u5f80\u5916\u957f"
      )
      .defineEnum("growth", GrowthDirection.NATURAL);
    MARGIN_X = builder.translation("pickupcard.config.marginX")
      .comment("\u79bb\u5c4f\u5e55\u5de6/\u53f3\u8fb9\u591a\u5c11\u50cf\u7d20\u3002")
      .defineInRange("marginX", 12, 0, 512);
    MARGIN_Y = builder.translation("pickupcard.config.marginY")
      .comment(
        "\u79bb\u5c4f\u5e55\u4e0b/\u4e0a\u8fb9\u591a\u5c11\u50cf\u7d20\u3002\u9ed8\u8ba4 64 \u662f\u4e3a\u4e86\u8ba9\u5f00\u5feb\u6377\u680f\uff08\u8fde\u540c\u72b6\u6001\u6761\uff09\u3002"
      )
      .defineInRange("marginY", 64, 0, 512);
    SEPARATION = builder.translation("pickupcard.config.separation")
      .comment("\u4e24\u5f20\u5361\u7247\u4e4b\u95f4\u7684\u7a7a\u9699\uff08\u50cf\u7d20\uff09\u3002")
      .defineInRange("separation", 3, 0, 32);
    SLIDE_DISTANCE = builder.translation("pickupcard.config.slideDistance")
      .comment(
        "\u51fa\u73b0\u65f6\u4ece\u5c4f\u5e55\u5916\u201c\u6ed1\u201d\u8fdb\u6765\u7684\u8ddd\u79bb\uff08\u50cf\u7d20\uff09\u30020 = \u53ea\u6de1\u5165\u4e0d\u6ed1\u52a8\u3002"
      )
      .defineInRange("slideDistance", 14, 0, 128);
    SCALE = builder.translation("pickupcard.config.scale")
      .comment("\u6574\u4f53\u7f29\u653e\u30021.0 = \u8ddf\u968f\u6e38\u620f\u754c\u9762\u7f29\u653e\u6bd4\u4f8b\u3002")
      .defineInRange("scale", 1.0, 0.5, 3.0);
    MAX_ON_SCREEN = builder.translation("pickupcard.config.maxOnScreen")
      .comment("\u5c4f\u5e55\u4e0a\u6700\u591a\u540c\u65f6\u663e\u793a\u51e0\u5f20\u5361\u7247\u3002")
      .defineInRange("maxOnScreen", 5, 1, 32);
    QUEUE_SIZE = builder.translation("pickupcard.config.queueSize")
      .comment(
        "\u663e\u793a\u4e0d\u4e0b\u65f6\u6700\u591a\u6392\u961f\u51e0\u5f20\u3002\u8fde\u6392\u961f\u4e5f\u6ee1\u4e86\u5c31\u76f4\u63a5\u4e22\u5f03\uff08\u5237\u602a\u5854\u524d\u9762\u4e0d\u6392\u957f\u961f\uff09\u3002"
      )
      .defineInRange("queueSize", 9, 0, 128);
    builder.pop();
    builder.comment("\u5916\u89c2 / appearance").push("appearance");
    BACKGROUND = builder.translation("pickupcard.config.background")
      .comment(
        "\u5361\u9762\u5e95\u8272\uff0c\u683c\u5f0f #AARRGGBB\uff08\u524d\u4e24\u4f4d\u662f\u900f\u660e\u5ea6\uff09\u3002\u7528\u8d34\u56fe\u5361\u9762\u65f6\u5b83\u4e0d\u751f\u6548 \u2014\u2014 \u989c\u8272\u5728\u8d34\u56fe\u91cc\u3002"
      )
      .define("background", "#B4180F26");
    BORDER = builder.translation("pickupcard.config.border")
      .comment(
        "\u5361\u6846\u53e0\u52a0\u8272\uff0c\u683c\u5f0f #AARRGGBB\u3002\n\u6ce8\u610f\u5361\u6846\u6700\u7ec8\u989c\u8272 = \u7269\u54c1\u7a00\u6709\u5ea6\u8272 \u4e0e \u8fd9\u4e2a\u989c\u8272\u6309 alpha \u53e0\u52a0\uff0c\n\u6240\u4ee5\u8fd9\u91cc\u4e00\u822c\u53ea\u5199\u4e00\u5c42\u8584\u8584\u7684\u767d\uff08\u4f8b\u5982 #3CFFFFFF\uff09\u6765\u63d0\u4eae\u6846\u7ebf\u3002"
      )
      .define("border", "#38FFD76B");
    BORDER_THICKNESS = builder.translation("pickupcard.config.borderThickness")
      .comment("\u5361\u6846\u7c97\u7ec6\uff08\u50cf\u7d20\uff09\u30020 = \u4e0d\u753b\u6846\u3002")
      .defineInRange("borderThickness", 0.0, 0.0, 3.0);
    CORNER_RADIUS = builder.translation("pickupcard.config.cornerRadius")
      .comment(
        "\u5706\u89d2\u534a\u5f84\uff08\u50cf\u7d20\uff09\u30020 = \u76f4\u89d2\u3002\u5361\u9762\u8d34\u56fe\u91cc\u7684\u5706\u89d2\u56fa\u5b9a\u662f 4px\uff0c\u8fd9\u91cc\u5f71\u54cd NEW \u80f6\u56ca\u4e0e\u53e0\u52a0\u6846\u3002"
      )
      .defineInRange("cornerRadius", 4, 0, 16);
    NAME_MAX_WIDTH = builder.translation("pickupcard.config.nameMaxWidth")
      .comment(
        "\u7269\u54c1\u540d\u6700\u5927\u5bbd\u5ea6\uff08\u50cf\u7d20\uff09\uff1a\u8d85\u51fa\u622a\u65ad\u52a0\u7701\u7565\u53f7\u30020 = \u4e0d\u9650\u3002"
      )
      .defineInRange("nameMaxWidth", 120, 0, 400);
    SHOW_ITEM_ID = builder.translation("pickupcard.config.showItemId")
      .comment(
        "\u7528 minecraft:gold_ingot \u8fd9\u79cd\u7269\u54c1 id \u4ee3\u66ff\u540d\u5b57\uff08\u7ec8\u7aef\u5473\u6700\u91cd\uff0c\u4f46\u66f4\u8d39\u52b2\u8bfb\uff09\u3002"
      )
      .define("showItemId", false);
    FRAME_THICKNESS = builder.translation("pickupcard.config.frameThickness")
      .comment("\u7a00\u6709\u5ea6\u8272\u5916\u6846\u7684\u7c97\u7ec6\uff08\u50cf\u7d20\uff09\u30020 = \u4e0d\u663e\u793a\u7a00\u6709\u5ea6\u6846\u3002")
      .defineInRange("frameThickness", 0.0, 0.0, 3.0);
    NAME_COLOR = builder.translation("pickupcard.config.nameColor")
      .comment(
        "\u7269\u54c1\u540d\u989c\u8272\uff0c\u683c\u5f0f #AARRGGBB\u3002\u5f00\u542f\u7a00\u6709\u5ea6\u5206\u7ea7\u65f6\uff0c\u56db\u6863\u5404\u7528\u81ea\u5df1\u7684\u5361\u9762\u540c\u8c03\u8272\u8986\u76d6\u5b83\u3002"
      )
      .define("nameColor", "#FFF0E8FF");
    ACCENT_COLOR = builder.translation("pickupcard.config.accentColor")
      .comment("\u5f3a\u8c03\u8272\uff1a\u6570\u91cf\u8bfb\u6570\u4e0e NEW \u6807\u7b7e\uff08\u938f\u91d1\uff09\u3002\u683c\u5f0f #AARRGGBB\u3002")
      .define("accentColor", "#FFFFD76B");
    SHOW_SHEEN = builder.translation("pickupcard.config.showSheen")
      .comment(
        "\u51fa\u73b0\u65f6\u6709\u4e00\u9053\u4eae\u5149\u626b\u8fc7\u5361\u9762\uff1b\u9644\u9b54\u7269\u54c1\u6539\u6210\u5e38\u9a7b\u7684\u7d2b\u8272\u6d41\u5149\u3002"
      )
      .define("showSheen", true);
    SHOW_DURABILITY = builder.translation("pickupcard.config.showDurability")
      .comment(
        "\u6361\u5230\u6709\u8010\u4e45\u635f\u8017\u7684\u5de5\u5177/\u88c5\u5907\u65f6\uff0c\u5728\u5361\u5e95\u753b\u4e00\u6761\u8010\u4e45\u6761\u3002"
      )
      .define("showDurability", true);
    SHOW_NEW_BADGE = builder.translation("pickupcard.config.showNewBadge")
      .comment(
        "\u7b2c\u4e00\u6b21\u6361\u5230\u67d0\u79cd\u7269\u54c1\u65f6\uff0c\u5361\u7247\u4e0a\u591a\u4e00\u4e2a NEW \u5c0f\u6807\u7b7e\uff08\u672c\u6b21\u6e38\u620f\u5185\u6709\u6548\uff09\u3002"
      )
      .define("showNewBadge", true);
    RARITY_SKINS = builder.translation("pickupcard.config.raritySkins")
      .comment(
        "\u7a00\u6709\u5ea6\u5206\u7ea7\u5916\u89c2\uff1a\u666e\u901a/\u7f55\u89c1/\u7a00\u6709/\u53f2\u8bd7\u56db\u6863\u5404\u6709\u4e00\u5957\u5361\u9762\u914d\u8272\u4e0e\u88c5\u9970\uff0c\n\u4ece\"\u6728\u724c\"\u4e00\u8def\u5347\u5230\"\u6697\u7d2b\u938f\u91d1 + \u5916\u53d1\u5149 + \u7b26\u6587\u7ebf\"\u3002\n\u5173\u6389 = \u56db\u6863\u5168\u90e8\u7528\u666e\u901a\u6863\u7684\u6728\u724c\u5361\u9762\uff0c\u957f\u5f97\u4e00\u6a21\u4e00\u6837\u3002"
      )
      .define("raritySkins", true);
    builder.pop();
    SPEC = builder.build();
    OPTIONS = List.of(
      ConfigOption.bool("general", "enabled", ENABLED),
      ConfigOption.enumOf("general", "style", STYLE),
      ConfigOption.bool("general", "showItemName", SHOW_ITEM_NAME),
      ConfigOption.enumOf("general", "countFormat", COUNT_FORMAT),
      ConfigOption.list("general", "itemBlacklist", ITEM_BLACKLIST),
      ConfigOption.intRange("animation", "enterMs", ENTER_MS, 0, 5000),
      ConfigOption.intRange("animation", "holdMs", HOLD_MS, 100, 60000),
      ConfigOption.intRange("animation", "exitMs", EXIT_MS, 0, 5000),
      ConfigOption.enumOf("animation", "animationStyle", ANIMATION_STYLE),
      ConfigOption.doubleRange("animation", "enterTilt", ENTER_TILT, 0.0, 20.0),
      ConfigOption.bool("animation", "pulseEnabled", PULSE_ENABLED),
      ConfigOption.intRange("animation", "pulseMs", PULSE_MS, 0, 3000),
      ConfigOption.doubleRange("animation", "pulseStrength", PULSE_STRENGTH, 0.0, 1.0),
      ConfigOption.intRange("animation", "mergeRollMs", MERGE_ROLL_MS, 0, 2000),
      ConfigOption.bool("animation", "mergeEnabled", MERGE_ENABLED),
      ConfigOption.intRange("animation", "mergeWindowMs", MERGE_WINDOW_MS, 0, 10000),
      ConfigOption.enumOf("layout", "anchor", ANCHOR),
      ConfigOption.enumOf("layout", "growth", GROWTH),
      ConfigOption.intRange("layout", "marginX", MARGIN_X, 0, 512),
      ConfigOption.intRange("layout", "marginY", MARGIN_Y, 0, 512),
      ConfigOption.intRange("layout", "separation", SEPARATION, 0, 32),
      ConfigOption.intRange("layout", "slideDistance", SLIDE_DISTANCE, 0, 128),
      ConfigOption.doubleRange("layout", "scale", SCALE, 0.5, 3.0),
      ConfigOption.intRange("layout", "maxOnScreen", MAX_ON_SCREEN, 1, 32),
      ConfigOption.intRange("layout", "queueSize", QUEUE_SIZE, 0, 128),
      ConfigOption.text("appearance", "background", BACKGROUND),
      ConfigOption.text("appearance", "border", BORDER),
      ConfigOption.doubleRange("appearance", "borderThickness", BORDER_THICKNESS, 0.0, 3.0),
      ConfigOption.intRange("appearance", "cornerRadius", CORNER_RADIUS, 0, 16),
      ConfigOption.doubleRange("appearance", "frameThickness", FRAME_THICKNESS, 0.0, 3.0),
      ConfigOption.text("appearance", "nameColor", NAME_COLOR),
      ConfigOption.text("appearance", "accentColor", ACCENT_COLOR),
      ConfigOption.bool("appearance", "showSheen", SHOW_SHEEN),
      ConfigOption.bool("appearance", "showDurability", SHOW_DURABILITY),
      ConfigOption.bool("appearance", "showNewBadge", SHOW_NEW_BADGE),
      ConfigOption.bool("appearance", "raritySkins", RARITY_SKINS),
      ConfigOption.intRange("appearance", "nameMaxWidth", NAME_MAX_WIDTH, 0, 400),
      ConfigOption.bool("appearance", "showItemId", SHOW_ITEM_ID)
    );
  }
}
