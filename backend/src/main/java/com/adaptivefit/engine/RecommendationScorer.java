package com.adaptivefit.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Goi y bai tap theo 3 buoc co the giai thich: (1) loc cung bai khong phu hop, (2) cham diem tung thanh phan,
 * (3) chon top-N da dang nhom co va dung buoc tap vua quy thoi gian. Khong dung LLM, khong sinh bai tap moi.
 */
public class RecommendationScorer {

    // Tu dong nghia cho dung cu: nguoi dung co the nhap ma hoac chu thuong tieng Viet.
    private static final Map<String, List<String>> EQUIPMENT_ALIASES = Map.of(
            "yoga_mat", List.of("yoga_mat", "tham", "yoga"),
            "jump_rope", List.of("jump_rope", "day nhay", "nhay day"),
            "bicycle", List.of("bicycle", "xe dap"),
            "stable_step", List.of("stable_step", "bac", "ghe", "buc"),
            "stable_surface", List.of("stable_surface", "stable_step", "ban", "ghe", "bac", "be"));

    private static final Map<String, Double> LEVEL_TARGET_DIFFICULTY = Map.of(
            "BEGINNER", 1.5, "INTERMEDIATE", 3.0, "ADVANCED", 4.5);

    private final Weights weights;

    public RecommendationScorer() {
        this(Weights.defaults());
    }

    public RecommendationScorer(Weights weights) {
        this.weights = weights.normalized();
    }

    public RecommendationResult recommend(UserState state, ProfileContext profile, StrategyDecision decision,
                                          List<ExerciseCandidate> all, int topN) {
        StrategyParams params = decision.params();
        int budget = state.freeTimeMin();
        if (params.maxDurationMin() != null) budget = Math.min(budget, params.maxDurationMin());
        if ("TOO_TIRED".equals(profile.recentAbandonmentReason())
                || "UNEXPECTED_WORK".equals(profile.recentAbandonmentReason())) {
            budget = Math.min(budget, 15);
        }

        Map<String, Integer> excluded = new LinkedHashMap<>();
        for (String k : List.of("location", "equipment", "time", "energy", "intensity", "intensity_conflict", "recovery")) {
            excluded.put(k, 0);
        }

        List<ScoredExercise> scored = new ArrayList<>();
        Map<Long, ExerciseCandidate> byId = new LinkedHashMap<>();
        for (ExerciseCandidate e : all) {
            String reason = hardFilter(e, state, profile, params, budget);
            if (reason != null) {
                excluded.merge(reason, 1, Integer::sum);
                continue;
            }
            scored.add(score(e, state, profile, decision, budget));
            byId.put(e.id(), e);
        }

        scored.sort(Comparator.comparingDouble(ScoredExercise::score).reversed().thenComparing(ScoredExercise::name));
        List<ScoredExercise> top = diversify(scored, byId, Math.max(1, topN));
        List<RecommendationResult.PlanItem> plan = buildPlan(top, byId, budget);
        top = syncWithPlan(top, plan, byId, state, profile, decision);

        String message = top.isEmpty() ? emptyMessage(excluded, budget) : null;
        return new RecommendationResult(budget, top, plan, excluded, message);
    }

    // ---------- (1) loc cung ----------
    private String hardFilter(ExerciseCandidate e, UserState s, ProfileContext p, StrategyParams params, int budget) {
        if (!locationOk(e.location(), p.location())) return "location";
        String equipment = "NO_EQUIPMENT".equals(p.recentAbandonmentReason()) ? "NONE" : p.equipment();
        if (!equipmentOk(e.equipment(), equipment)) return "equipment";
        if (e.durationMin() > budget) return "time";
        if (e.energyRequirement() > s.energy()) return "energy";
        int rank = StrategyParams.intensityRank(e.intensity());
        int userMaxRank = StrategyParams.intensityRank(p.maxExerciseIntensity());
        if ("TOO_TIRED".equals(p.recentAbandonmentReason())) userMaxRank = Math.min(userMaxRank, 1);
        int strategyMaxRank = params.maxIntensity() == null ? Integer.MAX_VALUE
                : StrategyParams.intensityRank(params.maxIntensity());
        int effectiveMaxRank = Math.min(userMaxRank, strategyMaxRank);
        if (params.minIntensity() != null
                && StrategyParams.intensityRank(params.minIntensity()) > effectiveMaxRank) return "intensity_conflict";
        if (rank > effectiveMaxRank) return "intensity";
        if (params.minIntensity() != null && rank < StrategyParams.intensityRank(params.minIntensity())) return "intensity";
        if (params.requireRecovery() && !e.recoverySuitable()) return "recovery";
        return null;
    }

    private boolean locationOk(String exerciseLocation, String preferredLocation) {
        if (exerciseLocation == null || preferredLocation == null) return false;
        return exerciseLocation.equalsIgnoreCase("ANY") || exerciseLocation.equalsIgnoreCase(preferredLocation);
    }

    boolean equipmentOk(String required, String owned) {
        if (required == null || required.isBlank() || required.equalsIgnoreCase("NONE")) return true;
        String have = TextUtil.padded(owned);
        for (String code : required.split(",")) {
            String c = code.trim().toLowerCase();
            if (c.isEmpty() || c.equals("none")) continue;
            List<String> aliases = EQUIPMENT_ALIASES.getOrDefault(c, List.of(c));
            boolean found = aliases.stream().anyMatch(a -> have.contains(" " + TextUtil.normalize(a) + " "));
            if (!found) return false;
        }
        return true;
    }

    // ---------- (2) cham diem ----------
    private ScoredExercise score(ExerciseCandidate e, UserState s, ProfileContext p, StrategyDecision d, int budget) {
        int planned = Math.min(e.durationMax(), budget);               // luon >= durationMin nho loc cung
        double time = planned >= budget ? 1.0 : Math.max(0.5, (double) planned / budget);
        double energy = Math.max(0.4, 1.0 - 0.15 * (s.energy() - e.energyRequirement()));
        double goal = goalMatch(e, p, d);
        double difficulty = difficultyMatch(e, p, d.params());
        double preference = preferenceMatch(e, p);
        double recovery = recoveryMatch(e, d.code());

        double total = weights.time() * time + weights.energy() * energy + weights.goal() * goal
                + weights.difficulty() * difficulty + weights.preference() * preference + weights.recovery() * recovery;
        total = Math.min(1.0, total + recentAbandonmentBonus(e, planned, p));

        ScoredExercise.Breakdown b = new ScoredExercise.Breakdown(r3(time), r3(energy), r3(goal),
                r3(difficulty), r3(preference), r3(recovery));
        return new ScoredExercise(e.id(), e.name(), planned, r3(total), b, reasons(b, e, s, p, planned, d));
    }

    private double goalMatch(ExerciseCandidate e, ProfileContext p, StrategyDecision d) {
        if (e.goal() == null || e.goal().isBlank()) return 0.5;
        if (e.goal().equalsIgnoreCase(p.goal())) return 1.0;
        if (e.goal().equalsIgnoreCase("RECOVERY") && d.code().equals("RECOVERY")) return 1.0;
        if (e.goal().equalsIgnoreCase("STAY_ACTIVE")) return 0.6;      // bai duy tri van dong phu hop moi muc tieu
        return 0.3;
    }

    private double difficultyMatch(ExerciseCandidate e, ProfileContext p, StrategyParams params) {
        double base = LEVEL_TARGET_DIFFICULTY.getOrDefault(p.fitnessLevel() == null ? "" : p.fitnessLevel(), 2.5);
        double target = Math.max(1.0, Math.min(5.0, base + params.difficultyShift()));
        return Math.max(0.0, 1.0 - Math.abs(e.difficulty() - target) / 4.0);
    }

    private double preferenceMatch(ExerciseCandidate e, ProfileContext p) {
        double score = 0.5;
        if (e.location() != null && (e.location().equalsIgnoreCase("ANY") || e.location().equalsIgnoreCase(p.location()))) {
            score += 0.3;
        }
        if (matchesPreferredWorkout(e, p.preferredWorkout())) score += 0.2;
        return Math.min(1.0, score);
    }

    private boolean matchesPreferredWorkout(ExerciseCandidate e, String preferred) {
        if (preferred == null || preferred.isBlank()) return false;
        String name = TextUtil.padded(e.name());
        Set<String> tags = new HashSet<>();
        if (e.tags() != null) e.tags().forEach(t -> tags.add(TextUtil.normalize(t)));
        for (String phrase : preferred.split("[,;]|\\bva\\b|\\bvà\\b")) {
            String ph = TextUtil.normalize(phrase);
            if (ph.length() < 3) continue;
            if (name.contains(" " + ph + " ") || name.contains(" " + ph) || tags.contains(ph)) return true;
        }
        return false;
    }

    private double recoveryMatch(ExerciseCandidate e, String code) {
        return switch (code) {
            case "RECOVERY" -> e.recoverySuitable() ? 1.0 : 0.0;
            case "REDUCE", "RESTART" -> e.recoverySuitable() ? 0.8 : 0.5;
            default -> 0.5;
        };
    }

    /**
     * Ly do hien cho nguoi dung: truoc het la ly do RIENG cua bai nay (nang luong, muc tieu, do kho, so thich),
     * sau do moi den cac dieu kien da kiem tra (dia diem, dung cu, cuong do, thoi gian) vi bai nao qua loc cung
     * cung thoa chung. Khong cat bot de cac dieu kien da kiem tra luon con trong danh sach.
     */
    private List<String> reasons(ScoredExercise.Breakdown b, ExerciseCandidate e, UserState s,
                                 ProfileContext p, int planned, StrategyDecision d) {
        List<String> r = new ArrayList<>();
        if (b.energy() >= 0.85) r.add("Hợp với mức năng lượng hiện tại (" + s.energy() + "/5)");
        if (b.goal() >= 0.99) r.add("Đúng mục tiêu của bạn");
        if (b.recovery() >= 0.99 && d.code().equals("RECOVERY")) r.add("Phù hợp để phục hồi");
        if (b.preference() >= 0.99) r.add("Đúng dạng bài bạn thích tập");
        if (b.difficulty() >= 0.85) r.add("Độ khó vừa sức");
        if ("TOO_TIRED".equals(p.recentAbandonmentReason())) {
            r.add("Ưu tiên bài nhẹ và ngắn hơn sau phản hồi buổi trước");
        } else if ("NO_EQUIPMENT".equals(p.recentAbandonmentReason())) {
            r.add("Ưu tiên bài không cần dụng cụ sau phản hồi buổi trước");
        } else if ("UNEXPECTED_WORK".equals(p.recentAbandonmentReason())) {
            r.add("Ưu tiên buổi ngắn hơn sau phản hồi buổi trước");
        }
        if (r.isEmpty()) r.add("Được xếp hạng cao trong các bài còn phù hợp");

        String where = locationLabel(p.location());
        if ("ANY".equalsIgnoreCase(e.location())) {
            r.add("Bài tập linh hoạt địa điểm, phù hợp với lựa chọn " + where);
        } else {
            r.add("Đúng địa điểm bạn đã chọn (" + where + ")");
        }
        if (e.equipment() == null || e.equipment().isBlank() || "NONE".equalsIgnoreCase(e.equipment())) {
            r.add("Không cần dụng cụ bổ sung");
        } else {
            r.add("Có đủ dụng cụ cần thiết: " + equipmentLabel(e.equipment()));
        }
        r.add("Cường độ " + intensityLabel(e.intensity()) + " không vượt mức tối đa bạn chọn ("
                + intensityLabel(p.maxExerciseIntensity()) + ")");
        r.add("Thời lượng " + planned + " phút nằm trong quỹ " + s.freeTimeMin() + " phút hôm nay");
        return r;
    }

    private double recentAbandonmentBonus(ExerciseCandidate exercise, int planned, ProfileContext profile) {
        return switch (profile.recentAbandonmentReason() == null ? "" : profile.recentAbandonmentReason()) {
            case "TOO_TIRED" -> (StrategyParams.intensityRank(exercise.intensity()) == 1 ? 0.08 : 0.0)
                    + (planned <= 15 ? 0.05 : 0.0);
            case "UNEXPECTED_WORK" -> planned <= 15 ? 0.12 : 0.0;
            default -> 0.0;
        };
    }

    private static String locationLabel(String code) {
        if (code == null) return "";
        return switch (code.toUpperCase(java.util.Locale.ROOT)) {
            case "HOME" -> "ở nhà";
            case "GYM" -> "phòng tập";
            case "OUTDOOR" -> "ngoài trời";
            case "ANY" -> "linh hoạt";
            default -> code;
        };
    }

    private static String intensityLabel(String code) {
        if (code == null) return "";
        return switch (code.toUpperCase(java.util.Locale.ROOT)) {
            case "LOW" -> "nhẹ";
            case "MEDIUM" -> "vừa";
            case "HIGH" -> "nặng";
            default -> code;
        };
    }

    private static final Map<String, String> EQUIPMENT_LABELS = Map.of(
            "yoga_mat", "thảm tập", "jump_rope", "dây nhảy", "bicycle", "xe đạp",
            "stable_step", "bậc hoặc ghế chắc chắn", "stable_surface", "mặt phẳng chắc chắn");

    private static String equipmentLabel(String required) {
        List<String> parts = new ArrayList<>();
        for (String code : required.split(",")) {
            String c = code.trim();
            if (c.isEmpty() || c.equalsIgnoreCase("none")) continue;
            parts.add(EQUIPMENT_LABELS.getOrDefault(c.toLowerCase(java.util.Locale.ROOT), c));
        }
        return String.join(", ", parts);
    }

    // ---------- (3) chon top-N da dang va buoc tap ----------
    /** Toi thieu so bai de goi y khi con du ung vien, du cac bai cung dang van dong. */
    private static final int MIN_ITEMS = 2;

    /**
     * Chon top-N: uu tien khac nhom co VA khac dang van dong (vd "Hit dat" va "Hit dat tay rong" la mot dang).
     * Chi khi chua du MIN_ITEMS moi cho phep lap lai dang van dong. Co the tra ve it hon N bai.
     */
    private List<ScoredExercise> diversify(List<ScoredExercise> ranked, Map<Long, ExerciseCandidate> byId, int topN) {
        List<ScoredExercise> picked = new ArrayList<>();
        Set<String> groups = new HashSet<>();
        Set<String> families = new HashSet<>();
        for (ScoredExercise s : ranked) {
            if (picked.size() >= topN) break;
            String g = String.valueOf(byId.get(s.exerciseId()).muscleGroup());
            String f = family(s.name());
            if (!groups.contains(g) && !families.contains(f)) {
                groups.add(g);
                families.add(f);
                picked.add(s);
            }
        }
        for (ScoredExercise s : ranked) {
            if (picked.size() >= topN) break;
            if (picked.contains(s)) continue;
            if (families.add(family(s.name()))) picked.add(s);
        }
        for (ScoredExercise s : ranked) {
            if (picked.size() >= Math.min(topN, MIN_ITEMS)) break;
            if (!picked.contains(s)) picked.add(s);
        }
        return picked;
    }

    /** Dang van dong suy ra tu hai tu dau cua ten bai (chua co cot rieng trong catalog). */
    static String family(String name) {
        String[] t = TextUtil.normalize(name).split(" ");
        if (t.length == 0 || t[0].isEmpty()) return "";
        return t.length == 1 ? t[0] : t[0] + " " + t[1];
    }

    /**
     * Dua thoi luong va ly do cua tung bai ve dung so phut trong ke hoach de the bai, ke hoach va nut bat dau
     * cung mot con so. Bai khong nam trong ke hoach giu thoi luong rieng cua no. Thu hang va diem khong doi.
     */
    private List<ScoredExercise> syncWithPlan(List<ScoredExercise> top, List<RecommendationResult.PlanItem> plan,
                                              Map<Long, ExerciseCandidate> byId, UserState s, ProfileContext p,
                                              StrategyDecision d) {
        Map<Long, Integer> planned = new LinkedHashMap<>();
        for (RecommendationResult.PlanItem item : plan) planned.put(item.exerciseId(), item.plannedMin());
        List<ScoredExercise> out = new ArrayList<>();
        for (ScoredExercise item : top) {
            int minutes = planned.getOrDefault(item.exerciseId(), item.plannedMin());
            out.add(new ScoredExercise(item.exerciseId(), item.name(), minutes, item.score(), item.breakdown(),
                    reasons(item.breakdown(), byId.get(item.exerciseId()), s, p, minutes, d)));
        }
        return out;
    }

    /** Xep du so bai theo thoi luong toi thieu truoc, roi chia phan thoi gian con thua cho cac bai theo thu tu xep hang. */
    private List<RecommendationResult.PlanItem> buildPlan(List<ScoredExercise> top, Map<Long, ExerciseCandidate> byId, int budget) {
        List<ExerciseCandidate> chosen = new ArrayList<>();
        int used = 0;
        for (ScoredExercise s : top) {
            ExerciseCandidate e = byId.get(s.exerciseId());
            if (used + e.durationMin() <= budget) {
                chosen.add(e);
                used += e.durationMin();
            }
        }
        int extra = budget - used;
        List<RecommendationResult.PlanItem> plan = new ArrayList<>();
        for (ExerciseCandidate e : chosen) {
            int add = Math.min(extra, e.durationMax() - e.durationMin());
            plan.add(new RecommendationResult.PlanItem(e.id(), e.name(), e.durationMin() + add));
            extra -= add;
        }
        return plan;
    }

    private String emptyMessage(Map<String, Integer> excluded, int budget) {
        if (budget < 3) return "Hôm nay bạn gần như không có thời gian rảnh. Thử một vài phút giãn cơ hoặc đi bộ nhẹ.";
        int max = excluded.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (max == 0) return "Thư viện bài tập đang trống.";
        String top = excluded.entrySet().stream().filter(x -> x.getValue() == max).findFirst().get().getKey();
        return switch (top) {
            case "equipment" -> "Hầu hết bài cần dụng cụ bạn chưa có. Hãy cập nhật dụng cụ trong Hồ sơ.";
            case "location" -> "Không có bài tập phù hợp với địa điểm bạn đã chọn.";
            case "time" -> "Các bài hiện có đều dài hơn thời gian bạn rảnh hôm nay.";
            case "energy" -> "Hầu hết bài cần nhiều năng lượng hơn mức hiện tại của bạn.";
            case "intensity_conflict" -> "Giới hạn cường độ của bạn không tương thích với chiến lược hiện tại.";
            case "intensity" -> "Các bài hiện có vượt giới hạn cường độ bạn chấp nhận.";
            case "recovery" -> "Không có bài phù hợp với yêu cầu phục hồi hiện tại.";
            default -> "Không có bài nào khớp ràng buộc của chiến lược hôm nay.";
        };
    }

    private static double r3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
