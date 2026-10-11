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

        Map<String, Integer> excluded = new LinkedHashMap<>();
        for (String k : List.of("equipment", "time", "energy", "intensity", "recovery")) excluded.put(k, 0);

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

        String message = top.isEmpty() ? emptyMessage(excluded, budget) : null;
        return new RecommendationResult(budget, top, plan, excluded, message);
    }

    // ---------- (1) loc cung ----------
    private String hardFilter(ExerciseCandidate e, UserState s, ProfileContext p, StrategyParams params, int budget) {
        if (!equipmentOk(e.equipment(), p.equipment())) return "equipment";
        if (e.durationMin() > budget) return "time";
        if (e.energyRequirement() > s.energy()) return "energy";
        int rank = StrategyParams.intensityRank(e.intensity());
        if (params.maxIntensity() != null && rank > StrategyParams.intensityRank(params.maxIntensity())) return "intensity";
        if (params.minIntensity() != null && rank < StrategyParams.intensityRank(params.minIntensity())) return "intensity";
        if (params.requireRecovery() && !e.recoverySuitable()) return "recovery";
        return null;
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

        ScoredExercise.Breakdown b = new ScoredExercise.Breakdown(r3(time), r3(energy), r3(goal),
                r3(difficulty), r3(preference), r3(recovery));
        return new ScoredExercise(e.id(), e.name(), planned, r3(total), b, reasons(b, e, s, planned, d));
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

    private List<String> reasons(ScoredExercise.Breakdown b, ExerciseCandidate e, UserState s, int planned, StrategyDecision d) {
        List<String> r = new ArrayList<>();
        if (b.time() >= 0.99) r.add("Vừa với " + planned + " phút bạn có hôm nay");
        if (b.energy() >= 0.85) r.add("Hợp với mức năng lượng hiện tại (" + s.energy() + "/5)");
        if (b.goal() >= 0.99) r.add("Đúng mục tiêu của bạn");
        if (b.recovery() >= 0.99 && d.code().equals("RECOVERY")) r.add("Phù hợp để phục hồi");
        if (b.preference() >= 0.8) r.add("Hợp nơi tập và sở thích của bạn");
        if (b.difficulty() >= 0.85) r.add("Độ khó vừa sức");
        if (r.isEmpty()) r.add("Có điểm tổng hợp cao nhất trong các bài phù hợp");
        return r.size() > 3 ? new ArrayList<>(r.subList(0, 3)) : r;
    }

    // ---------- (3) chon top-N da dang va buoc tap ----------
    private List<ScoredExercise> diversify(List<ScoredExercise> ranked, Map<Long, ExerciseCandidate> byId, int topN) {
        List<ScoredExercise> picked = new ArrayList<>();
        Set<String> groups = new HashSet<>();
        for (ScoredExercise s : ranked) {
            if (picked.size() >= topN) break;
            String g = String.valueOf(byId.get(s.exerciseId()).muscleGroup());
            if (groups.add(g)) picked.add(s);
        }
        for (ScoredExercise s : ranked) {
            if (picked.size() >= topN) break;
            if (!picked.contains(s)) picked.add(s);
        }
        return picked;
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
            case "time" -> "Các bài hiện có đều dài hơn thời gian bạn rảnh hôm nay.";
            case "energy" -> "Hầu hết bài cần nhiều năng lượng hơn mức hiện tại của bạn.";
            default -> "Không có bài nào khớp ràng buộc của chiến lược hôm nay.";
        };
    }

    private static double r3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
