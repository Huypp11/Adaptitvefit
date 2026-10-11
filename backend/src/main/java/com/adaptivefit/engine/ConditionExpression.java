package com.adaptivefit.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bieu thuc dieu kien dang chuoi, de admin chinh duoc: cac nhom cach nhau boi "|" (HOAC),
 * trong nhom cac dieu kien cach nhau boi "&" (VA). "*" la nhom luon dung.
 * Vi du: missed_days>=3 | risk_rank>=HIGH&missed_days>=2
 */
public final class ConditionExpression {

    public record Condition(String field, String op, double value) {}

    public static final Map<String, String> FIELD_LABELS = new LinkedHashMap<>();
    static {
        FIELD_LABELS.put("sleep_hours", "Giờ ngủ");
        FIELD_LABELS.put("stress_level", "Mức căng thẳng");
        FIELD_LABELS.put("energy_level", "Mức năng lượng");
        FIELD_LABELS.put("mood", "Tâm trạng");
        FIELD_LABELS.put("motivation", "Động lực");
        FIELD_LABELS.put("free_time_min", "Thời gian rảnh (phút)");
        FIELD_LABELS.put("missed_days", "Số ngày chưa tập liên tiếp");
        FIELD_LABELS.put("workout_frequency_7d", "Số ngày có tập trong 7 ngày");
        FIELD_LABELS.put("risk_rank", "Mức nguy cơ ước tính");
    }

    private static final Pattern COND = Pattern.compile("^([a-z0-9_]+)\\s*(>=|<=|==|!=|>|<)\\s*([A-Za-z0-9_.\\-]+)$");
    private static final String[] RISK_NAMES = {"", "LOW", "MEDIUM", "HIGH"};

    private final List<List<Condition>> groups;

    private ConditionExpression(List<List<Condition>> groups) {
        this.groups = groups;
    }

    public static int riskRank(String level) {
        if (level == null) return 1;
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    /** Nem IllegalArgumentException (thong bao tieng Viet) neu bieu thuc sai. */
    public static ConditionExpression parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Điều kiện không được để trống (dùng * cho luôn đúng)");
        }
        List<List<Condition>> groups = new ArrayList<>();
        for (String rawGroup : text.split("\\|", -1)) {
            String g = rawGroup.trim();
            if (g.isEmpty()) throw new IllegalArgumentException("Có nhóm điều kiện rỗng quanh dấu |");
            List<Condition> conds = new ArrayList<>();
            if (!g.equals("*")) {
                for (String rawCond : g.split("&", -1)) {
                    conds.add(parseCondition(rawCond.trim()));
                }
            }
            groups.add(conds);
        }
        return new ConditionExpression(groups);
    }

    private static Condition parseCondition(String text) {
        Matcher m = COND.matcher(text);
        if (!m.matches()) {
            throw new IllegalArgumentException("Điều kiện sai cú pháp: '" + text + "' (ví dụ: missed_days>=3)");
        }
        String field = m.group(1), op = m.group(2), valueText = m.group(3);
        if (!FIELD_LABELS.containsKey(field)) {
            throw new IllegalArgumentException("Trường không hợp lệ: " + field + ". Cho phép: " + String.join(", ", FIELD_LABELS.keySet()));
        }
        double value;
        if (field.equals("risk_rank")) {
            value = switch (valueText.toUpperCase(Locale.ROOT)) {
                case "LOW" -> 1;
                case "MEDIUM" -> 2;
                case "HIGH" -> 3;
                default -> throw new IllegalArgumentException("risk_rank chỉ nhận LOW, MEDIUM hoặc HIGH");
            };
        } else {
            try {
                value = Double.parseDouble(valueText);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Giá trị không phải số: '" + valueText + "'");
            }
        }
        return new Condition(field, op, value);
    }

    /** Chi so cua nhom dau tien thoa, hoac -1. */
    public int firstMatch(Map<String, Double> facts) {
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).stream().allMatch(c -> holds(c, facts))) return i;
        }
        return -1;
    }

    static boolean holds(Condition c, Map<String, Double> facts) {
        Double actual = facts.get(c.field());
        if (actual == null) return false;
        return switch (c.op()) {
            case ">=" -> actual >= c.value();
            case "<=" -> actual <= c.value();
            case ">" -> actual > c.value();
            case "<" -> actual < c.value();
            case "==" -> Double.compare(actual, c.value()) == 0;
            case "!=" -> Double.compare(actual, c.value()) != 0;
            default -> false;
        };
    }

    /** Giai thich bang tieng Viet vi sao nhom nay thoa (de hien thi cho nguoi dung). */
    public List<String> explain(int groupIndex, Map<String, Double> facts) {
        List<String> out = new ArrayList<>();
        for (Condition c : groups.get(groupIndex)) {
            out.add(FIELD_LABELS.get(c.field()) + " là " + show(c.field(), facts.get(c.field()))
                    + " (điều kiện: " + opText(c.op()) + " " + show(c.field(), c.value()) + ")");
        }
        return out;
    }

    private static String show(String field, Double v) {
        if (v == null) return "?";
        if (field.equals("risk_rank")) return RISK_NAMES[Math.max(1, Math.min(3, (int) Math.round(v)))];
        return v == Math.rint(v) ? String.valueOf((long) Math.rint(v)) : String.valueOf(v);
    }

    private static String opText(String op) {
        return switch (op) {
            case ">=" -> "từ";
            case "<=" -> "tối đa";
            case ">" -> "lớn hơn";
            case "<" -> "nhỏ hơn";
            case "==" -> "bằng";
            default -> "khác";
        };
    }
}
