package com.adaptivefit.engine;

import java.util.Locale;
import java.util.Map;

/**
 * Rang buoc cua mot chien luoc khi chon bai tap. Dang chuoi "khoa=gia tri;khoa=gia tri", vi du:
 * maxDurationMin=15;maxIntensity=LOW;difficultyShift=-1;requireRecovery=true
 */
public record StrategyParams(Integer maxDurationMin, String maxIntensity, String minIntensity,
                             boolean requireRecovery, double difficultyShift) {

    private static final Map<String, Integer> INTENSITY_RANK = Map.of("LOW", 1, "MEDIUM", 2, "HIGH", 3);

    public static StrategyParams none() {
        return new StrategyParams(null, null, null, false, 0.0);
    }

    public static int intensityRank(String intensity) {
        return INTENSITY_RANK.getOrDefault(intensity == null ? "" : intensity.toUpperCase(Locale.ROOT), 2);
    }

    public static StrategyParams parse(String text) {
        if (text == null || text.isBlank()) return none();
        Integer maxDur = null;
        String maxInt = null, minInt = null;
        boolean recovery = false;
        double shift = 0.0;
        for (String part : text.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            if (eq <= 0) throw new IllegalArgumentException("Tham số sai cú pháp: '" + p + "' (dạng khoá=giá trị)");
            String key = p.substring(0, eq).trim(), val = p.substring(eq + 1).trim();
            switch (key) {
                case "maxDurationMin" -> {
                    maxDur = parseInt(key, val);
                    if (maxDur < 3 || maxDur > 240) throw new IllegalArgumentException("maxDurationMin phải từ 3 đến 240");
                }
                case "maxIntensity" -> maxInt = parseIntensity(key, val);
                case "minIntensity" -> minInt = parseIntensity(key, val);
                case "requireRecovery" -> {
                    if (!val.equalsIgnoreCase("true") && !val.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException("requireRecovery chỉ nhận true hoặc false");
                    }
                    recovery = Boolean.parseBoolean(val);
                }
                case "difficultyShift" -> {
                    try {
                        shift = Double.parseDouble(val);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("difficultyShift phải là số");
                    }
                    if (shift < -2 || shift > 2) throw new IllegalArgumentException("difficultyShift phải từ -2 đến 2");
                }
                default -> throw new IllegalArgumentException("Tham số không hợp lệ: " + key
                        + ". Cho phép: maxDurationMin, maxIntensity, minIntensity, requireRecovery, difficultyShift");
            }
        }
        if (maxInt != null && minInt != null && intensityRank(minInt) > intensityRank(maxInt)) {
            throw new IllegalArgumentException("minIntensity không được cao hơn maxIntensity");
        }
        return new StrategyParams(maxDur, maxInt, minInt, recovery, shift);
    }

    private static int parseInt(String key, String val) {
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phải là số nguyên");
        }
    }

    private static String parseIntensity(String key, String val) {
        String v = val.toUpperCase(Locale.ROOT);
        if (!INTENSITY_RANK.containsKey(v)) throw new IllegalArgumentException(key + " chỉ nhận LOW, MEDIUM hoặc HIGH");
        return v;
    }
}
