package com.adaptivefit.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTest {

    // Bo quy tac mac dinh, phai KHOP voi migration V7__intervention_rules.sql
    private static final List<RuleDef> RULES = List.of(
            new RuleDef("RESTART", 1, "missed_days>=3 | risk_rank>=HIGH&missed_days>=2",
                    "maxDurationMin=15;maxIntensity=LOW;difficultyShift=-1", "restart", true),
            new RuleDef("RECOVERY", 2, "sleep_hours<6&stress_level>=4&energy_level<=2",
                    "maxDurationMin=20;maxIntensity=LOW;requireRecovery=true;difficultyShift=-1", "recovery", true),
            new RuleDef("REDUCE", 3, "free_time_min<20 | energy_level<=2 | risk_rank==MEDIUM",
                    "maxDurationMin=20;maxIntensity=MEDIUM;difficultyShift=-0.5", "reduce", true),
            new RuleDef("CHALLENGE", 4, "risk_rank==LOW&energy_level>=4&workout_frequency_7d>=3",
                    "minIntensity=MEDIUM;difficultyShift=0.7", "challenge", true),
            new RuleDef("NORMAL", 5, "*", "", "normal", true));

    private static UserState state(double sleep, int stress, int energy, int free, int missed, int freq) {
        return new UserState(sleep, stress, energy, 3, 3, free, false, missed, freq,
                HistoryRisk.level(missed, freq), UserState.RISK_SOURCE_HISTORY);
    }

    private static String decide(UserState s) {
        return new InterventionEngine().decide(s, RULES).code();
    }

    // ---------- bieu thuc dieu kien ----------
    @Test
    void parsesAndEvaluatesGroups() {
        ConditionExpression e = ConditionExpression.parse("missed_days>=3 | risk_rank>=HIGH&stress_level>=4");
        assertEquals(0, e.firstMatch(state(7, 3, 3, 30, 3, 1).facts()));
        assertEquals(1, e.firstMatch(new UserState(7, 4, 3, 3, 3, 30, false, 2, 1, "HIGH", "x").facts()));
        assertEquals(-1, e.firstMatch(state(7, 3, 3, 30, 1, 3).facts()));
        assertEquals(0, ConditionExpression.parse("*").firstMatch(state(7, 3, 3, 30, 0, 3).facts()));
    }

    @Test
    void rejectsInvalidExpressions() {
        assertThrows(IllegalArgumentException.class, () -> ConditionExpression.parse(""));
        assertThrows(IllegalArgumentException.class, () -> ConditionExpression.parse("age>=3"));
        assertThrows(IllegalArgumentException.class, () -> ConditionExpression.parse("missed_days>=abc"));
        assertThrows(IllegalArgumentException.class, () -> ConditionExpression.parse("missed_days>=3 |"));
        assertThrows(IllegalArgumentException.class, () -> ConditionExpression.parse("risk_rank>=EXTREME"));
        assertThrows(IllegalArgumentException.class, () -> ConditionExpression.parse("missed_days 3"));
    }

    @Test
    void explainsMatchedConditionInVietnamese() {
        ConditionExpression e = ConditionExpression.parse("missed_days>=3");
        List<String> why = e.explain(0, state(7, 3, 3, 30, 4, 0).facts());
        assertEquals(1, why.size());
        assertTrue(why.get(0).contains("Số ngày chưa tập liên tiếp là 4"));
        assertTrue(why.get(0).contains("từ 3"));
    }

    @Test
    void strategyParamsParseAndValidate() {
        StrategyParams p = StrategyParams.parse("maxDurationMin=15;maxIntensity=LOW;difficultyShift=-1;requireRecovery=true");
        assertEquals(15, p.maxDurationMin());
        assertEquals("LOW", p.maxIntensity());
        assertEquals(-1.0, p.difficultyShift(), 1e-9);
        assertTrue(p.requireRecovery());
        assertFalse(StrategyParams.parse("").requireRecovery());
        assertThrows(IllegalArgumentException.class, () -> StrategyParams.parse("foo=1"));
        assertThrows(IllegalArgumentException.class, () -> StrategyParams.parse("maxDurationMin=1"));
        assertThrows(IllegalArgumentException.class, () -> StrategyParams.parse("minIntensity=HIGH;maxIntensity=LOW"));
        assertThrows(IllegalArgumentException.class, () -> StrategyParams.parse("difficultyShift=9"));
    }

    // ---------- chon chien luoc ----------
    @Test
    void historyRiskThresholds() {
        assertEquals("HIGH", HistoryRisk.level(4, 3));
        assertEquals("MEDIUM", HistoryRisk.level(2, 3));
        assertEquals("MEDIUM", HistoryRisk.level(0, 1));
        assertEquals("LOW", HistoryRisk.level(1, 3));
        assertEquals("LOW", HistoryRisk.level(1, 0, 2));      // nguoi moi: chua du lich su thi khong bi coi la it tap
        assertEquals("MEDIUM", HistoryRisk.level(2, 0, 2));   // nhung nghi tu 2 ngay van tinh
    }

    @Test
    void choosesStrategyByPriority() {
        assertEquals("RESTART", decide(state(7, 2, 4, 60, 3, 1)));
        assertEquals("RESTART", decide(state(5, 4, 2, 60, 5, 0)));          // RESTART co uu tien cao hon RECOVERY
        assertEquals("RECOVERY", decide(state(5, 4, 2, 60, 0, 3)));
        assertEquals("REDUCE", decide(state(7, 2, 4, 15, 0, 3)));           // it thoi gian
        assertEquals("REDUCE", decide(state(7, 2, 2, 60, 0, 3)));           // nang luong thap, chua du dieu kien phuc hoi
        assertEquals("REDUCE", decide(state(7, 2, 4, 60, 2, 3)));           // nguy co trung binh
        assertEquals("CHALLENGE", decide(state(8, 2, 5, 60, 0, 5)));
        assertEquals("NORMAL", decide(state(7, 3, 3, 60, 0, 2)));
    }

    @Test
    void usesNormalFallbackAndSkipsBrokenOrInactiveRules() {
        List<RuleDef> broken = List.of(
                new RuleDef("RESTART", 1, "garbage??", "", "x", true),
                new RuleDef("REDUCE", 2, "*", "", "x", false));
        assertEquals("NORMAL", new InterventionEngine().decide(state(7, 3, 3, 60, 0, 3), broken).code());
        assertEquals("NORMAL", new InterventionEngine().decide(state(7, 3, 3, 60, 0, 3), List.of()).code());
    }

    // ---------- goi y bai tap ----------
    private static ExerciseCandidate ex(long id, String name, int dmin, int dmax, int diff, String intensity, String equip,
                                        String loc, String muscle, String goal, int energy, boolean recovery, String... tags) {
        return new ExerciseCandidate(id, name, dmin, dmax, diff, intensity, equip, loc, muscle, goal, energy, recovery, Set.of(tags));
    }

    private static final ProfileContext PROFILE = new ProfileContext("BEGINNER", "STAY_ACTIVE", "HOME", "", "");

    private static StrategyDecision strategy(String code, String params) {
        return new StrategyDecision(code, code, List.of("test"), StrategyParams.parse(params));
    }

    @Test
    void hardFiltersEquipmentTimeEnergyAndRecovery() {
        List<ExerciseCandidate> all = List.of(
                ex(1, "Giãn cơ", 5, 10, 1, "LOW", "NONE", "HOME", "back", "RECOVERY", 1, true),
                ex(2, "Nhảy dây", 5, 15, 3, "HIGH", "jump_rope", "ANY", "full", "ENDURANCE", 4, false),
                ex(3, "Bài dài", 30, 40, 2, "MEDIUM", "NONE", "HOME", "legs", "STAY_ACTIVE", 2, false),
                ex(4, "Bài cần sức", 5, 10, 3, "MEDIUM", "NONE", "HOME", "core", "STAY_ACTIVE", 4, false),
                ex(5, "Bài nặng", 5, 10, 2, "HIGH", "NONE", "HOME", "arms", "STAY_ACTIVE", 2, false));
        RecommendationResult r = new RecommendationScorer().recommend(state(7, 3, 2, 20, 0, 3), PROFILE,
                strategy("REDUCE", "maxDurationMin=20;maxIntensity=MEDIUM"), all, 3);
        assertEquals(1, r.items().size());
        assertEquals(1L, r.items().get(0).exerciseId());
        assertEquals(1, r.excluded().get("equipment"));
        assertEquals(1, r.excluded().get("time"));
        assertEquals(1, r.excluded().get("energy"));
        assertEquals(1, r.excluded().get("intensity"));

        RecommendationResult withRope = new RecommendationScorer().recommend(state(7, 3, 5, 30, 0, 3),
                new ProfileContext("BEGINNER", "STAY_ACTIVE", "HOME", "Dây nhảy, tạ tay", ""),
                strategy("NORMAL", ""), all, 5);
        assertTrue(withRope.items().stream().anyMatch(i -> i.exerciseId() == 2L));

        RecommendationResult rec = new RecommendationScorer().recommend(state(5, 4, 2, 20, 0, 3), PROFILE,
                strategy("RECOVERY", "requireRecovery=true;maxIntensity=LOW"), all, 3);
        assertEquals(List.of(1L), rec.items().stream().map(ScoredExercise::exerciseId).toList());
    }

    @Test
    void goalAndDifficultyAffectRanking() {
        ExerciseCandidate match = ex(1, "Đúng mục tiêu", 10, 20, 2, "MEDIUM", "NONE", "HOME", "a", "LOSE_WEIGHT", 2, false);
        ExerciseCandidate other = ex(2, "Khác mục tiêu", 10, 20, 2, "MEDIUM", "NONE", "HOME", "b", "BUILD_MUSCLE", 2, false);
        ProfileContext p = new ProfileContext("INTERMEDIATE", "LOSE_WEIGHT", "HOME", "", "");
        RecommendationResult r = new RecommendationScorer().recommend(state(7, 3, 3, 30, 0, 3), p,
                strategy("NORMAL", ""), List.of(other, match), 2);
        assertEquals(1L, r.items().get(0).exerciseId());
        assertTrue(r.items().get(0).score() > r.items().get(1).score());
        assertEquals(1.0, r.items().get(0).breakdown().goal(), 1e-9);
    }

    @Test
    void preferredWorkoutBoostsMatchingExercise() {
        ExerciseCandidate walk = ex(1, "Đi bộ nhẹ", 10, 30, 1, "LOW", "NONE", "ANY", "full", "STAY_ACTIVE", 1, true);
        ExerciseCandidate squat = ex(2, "Squat tay không", 10, 20, 1, "LOW", "NONE", "ANY", "legs", "STAY_ACTIVE", 1, true);
        ProfileContext p = new ProfileContext("BEGINNER", "STAY_ACTIVE", "HOME", "", "đi bộ, yoga");
        RecommendationResult r = new RecommendationScorer().recommend(state(7, 3, 3, 30, 0, 3), p,
                strategy("NORMAL", ""), List.of(squat, walk), 2);
        assertEquals(1L, r.items().get(0).exerciseId());
        assertEquals(1.0, r.items().get(0).breakdown().preference(), 1e-9);
    }

    @Test
    void planFitsBudgetAndPicksDistinctMuscleGroups() {
        List<ExerciseCandidate> all = List.of(
                ex(1, "Chân 1", 10, 20, 2, "MEDIUM", "NONE", "HOME", "legs", "STAY_ACTIVE", 2, false),
                ex(2, "Chân 2", 10, 20, 2, "MEDIUM", "NONE", "HOME", "legs", "STAY_ACTIVE", 2, false),
                ex(3, "Lưng", 10, 20, 2, "MEDIUM", "NONE", "HOME", "back", "BUILD_MUSCLE", 2, false),
                ex(4, "Bụng", 10, 20, 2, "MEDIUM", "NONE", "HOME", "core", "BUILD_MUSCLE", 2, false));
        RecommendationResult r = new RecommendationScorer().recommend(state(7, 3, 3, 45, 0, 3), PROFILE,
                strategy("NORMAL", ""), all, 3);
        long distinct = r.items().stream().map(ScoredExercise::exerciseId).filter(id -> id != 2L).count();
        assertEquals(3, r.items().size());
        assertEquals(3L, distinct);                                      // bai "Chan 2" (cung nhom) khong duoc chon
        int total = r.plan().stream().mapToInt(RecommendationResult.PlanItem::plannedMin).sum();
        assertTrue(total <= r.budgetMinutes());
        assertEquals(45, total);
        assertEquals(3, r.plan().size());                                 // du 3 bai nho chia thoi gian khon ngoan
        for (RecommendationResult.PlanItem item : r.plan()) assertTrue(item.plannedMin() >= 10);
    }

    @Test
    void emptyResultExplainsWhy() {
        RecommendationResult none = new RecommendationScorer().recommend(state(7, 3, 3, 0, 0, 3), PROFILE,
                strategy("NORMAL", ""), List.of(ex(1, "A", 5, 10, 1, "LOW", "NONE", "HOME", "a", "STAY_ACTIVE", 1, true)), 3);
        assertTrue(none.items().isEmpty());
        assertTrue(none.message() != null && !none.message().isBlank());
        assertTrue(none.plan().isEmpty());
    }

    @Test
    void weightsAreNormalizedAndValidated() {
        Weights w = new Weights(2, 2, 2, 2, 1, 1).normalized();
        assertEquals(1.0, w.time() + w.energy() + w.goal() + w.difficulty() + w.preference() + w.recovery(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> new Weights(0, 0, 0, 0, 0, 0).normalized());
    }
}
