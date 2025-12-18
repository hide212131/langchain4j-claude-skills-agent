package io.github.hide212131.langchain4j.claude.skills.runtime;

import java.util.Locale;

/** 可視化ログの出力レベル。 */
public enum VisibilityLevel {
  BASIC,
  OFF;

  public static VisibilityLevel parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return BASIC;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "basic" -> BASIC;
      case "off" -> OFF;
      default -> throw new IllegalArgumentException("サポートされていない可視化レベルです: " + raw);
    };
  }
}
