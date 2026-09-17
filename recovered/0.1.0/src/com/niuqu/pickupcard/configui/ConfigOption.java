package com.niuqu.pickupcard.configui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.EnumValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public final class ConfigOption {
  private final String section;
  private final String key;
  private final ConfigOption.Kind kind;
  private final ConfigValue<?> value;
  private final double min;
  private final double max;

  private ConfigOption(String section, String key, ConfigOption.Kind kind, ConfigValue<?> value, double min, double max) {
    this.section = section;
    this.key = key;
    this.kind = kind;
    this.value = value;
    this.min = min;
    this.max = max;
  }

  public static ConfigOption bool(String section, String key, BooleanValue value) {
    return new ConfigOption(section, key, ConfigOption.Kind.BOOLEAN, value, 0.0, 1.0);
  }

  public static ConfigOption intRange(String section, String key, IntValue value, int min, int max) {
    return new ConfigOption(section, key, ConfigOption.Kind.INT, value, min, max);
  }

  public static ConfigOption doubleRange(String section, String key, DoubleValue value, double min, double max) {
    return new ConfigOption(section, key, ConfigOption.Kind.DOUBLE, value, min, max);
  }

  public static ConfigOption enumOf(String section, String key, EnumValue<?> value) {
    return new ConfigOption(section, key, ConfigOption.Kind.ENUM, value, 0.0, 1.0);
  }

  public static ConfigOption text(String section, String key, ConfigValue<String> value) {
    return new ConfigOption(section, key, ConfigOption.Kind.STRING, value, 0.0, 1.0);
  }

  public static ConfigOption list(String section, String key, ConfigValue<List<? extends String>> value) {
    return new ConfigOption(section, key, ConfigOption.Kind.LIST, value, 0.0, 1.0);
  }

  public String section() {
    return this.section;
  }

  public String key() {
    return this.key;
  }

  public ConfigOption.Kind kind() {
    return this.kind;
  }

  public Component label() {
    return Component.m_237115_("pickupcard.config." + this.key);
  }

  public List<FormattedCharSequence> tooltip(int wrapWidth) {
    String tipKey = "pickupcard.config." + this.key + ".tooltip";
    return !I18n.m_118936_(tipKey) ? List.of() : Minecraft.m_91087_().f_91062_.m_92923_(Component.m_237115_(tipKey), wrapWidth);
  }

  public boolean asBool() {
    return Boolean.TRUE.equals(this.value.get());
  }

  public double asNumber() {
    return this.value.get() instanceof Number number ? number.doubleValue() : 0.0;
  }

  public String asText() {
    Object raw = this.value.get();
    if (!(raw instanceof List<?> list)) {
      return String.valueOf(raw);
    } else {
      List<String> parts = new ArrayList<>();

      for (Object element : list) {
        parts.add(String.valueOf(element));
      }

      return String.join(", ", parts);
    }
  }

  public double fraction() {
    if (this.kind == ConfigOption.Kind.BOOLEAN) {
      return this.asBool() ? 1.0 : 0.0;
    } else if (this.kind != ConfigOption.Kind.INT && this.kind != ConfigOption.Kind.DOUBLE) {
      return 0.0;
    } else {
      double span = this.max - this.min;
      return span <= 0.0 ? 0.0 : (this.asNumber() - this.min) / span;
    }
  }

  public void setBool(boolean flag) {
    this.set(flag);
  }

  public void setFraction(double fraction) {
    double clamped = Math.max(0.0, Math.min(1.0, fraction));
    if (this.kind == ConfigOption.Kind.INT) {
      this.set((int)Math.round(this.min + clamped * (this.max - this.min)));
    } else if (this.kind == ConfigOption.Kind.DOUBLE) {
      double raw = this.min + clamped * (this.max - this.min);
      this.set(Math.round(raw * 20.0) / 20.0);
    }
  }

  public void cycle() {
    if (this.kind == ConfigOption.Kind.BOOLEAN) {
      this.setBool(!this.asBool());
    } else {
      if (this.kind == ConfigOption.Kind.ENUM && this.value.get() instanceof Enum<?> current) {
        Enum[] constants = (Enum[])current.getDeclaringClass().getEnumConstants();
        if (constants == null || constants.length == 0) {
          return;
        }

        int next = (current.ordinal() + 1) % constants.length;
        this.set(constants[next]);
      }
    }
  }

  public void setText(String text) {
    if (this.kind == ConfigOption.Kind.STRING) {
      this.set(text);
    } else if (this.kind == ConfigOption.Kind.LIST) {
      List<String> parts = new ArrayList<>();

      for (String piece : text.split(",")) {
        String trimmed = piece.trim();
        if (!trimmed.isEmpty()) {
          parts.add(trimmed);
        }
      }

      this.value.set(parts);
    }
  }

  public String displayValue() {
    return switch (this.kind) {
      case BOOLEAN -> this.asBool() ? "\u5f00" : "\u5173";
      case INT -> Integer.toString((int)Math.round(this.asNumber()));
      case DOUBLE -> String.format("%.2f", this.asNumber());
      default -> this.asText();
    };
  }

  private void set(Object raw) {
    this.value.set(raw);
  }

  public static enum Kind {
    BOOLEAN,
    INT,
    DOUBLE,
    ENUM,
    STRING,
    LIST;

    private Kind() {
    }
  }
}
