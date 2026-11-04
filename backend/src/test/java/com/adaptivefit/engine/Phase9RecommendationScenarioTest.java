package com.adaptivefit.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase9RecommendationScenarioTest {

    private static final List<RuleDef> RULES = List.of(
            new RuleDef("DONE", 1, "exercised_today==1",
                    "maxDurationMin=10;maxIntensity=LOW;requireRecovery=true;difficultyShift=-1", "done", true),
            new RuleDef("RESTART", 2, "missed_days>=3 | risk_rank>=HIGH&missed_days>=2",
                    "maxDurationMin=15;maxIntensity=LOW;difficultyShift=-1", "restart", true),
            new RuleDef("RECOVERY", 3, "sleep_hours<6&stress_level>=4&energy_level<=2",
                    "maxDurationMin=20;maxIntensity=LOW;requireRecovery=true;difficultyShift=-1", "recovery", true),
            new RuleDef("REDUCE", 4, "free_time_min<20 | energy_level<=2 | risk_rank==MEDIUM",
                    "maxDurationMin=20;maxIntensity=MEDIUM;difficultyShift=-0.5", "reduce", true),
            new RuleDef("CHALLENGE", 5,
                    "risk_rank==LOW&energy_level>=4&workout_frequency_7d>=3&stress_level<4&sleep_hours>=6&mood>=3&motivation>=3",
                    "minIntensity=MEDIUM;difficultyShift=0.7", "challenge", true),
            new RuleDef("NORMAL", 6, "*", "", "normal", true));

    private static final ExerciseCandidate LOW_HOME = exercise(1, "Đi bộ nhẹ", 5, 15, "LOW", "NONE", "HOME", 1, true);
    private static final ExerciseCandidate LOW_ANY = exercise(2, "Giãn cơ linh hoạt", 5, 15, "LOW", "NONE", "ANY", 1, true);
    private static final ExerciseCandidate MEDIUM_HOME = exercise(3, "Squat tay không", 5, 15, "MEDIUM", "NONE", "HOME", 3, false);
    private static final ExerciseCandidate HIGH_HOME = exercise(4, "Nhảy co gối", 5, 10, "HIGH", "NONE", "HOME", 4, false);
    private static final ExerciseCandidate MEDIUM_OUTDOOR = exercise(5, "Đi bộ nhanh", 10, 20, "MEDIUM", "NONE", "OUTDOOR", 3, false);
    private static final ExerciseCandidate LOW_GYM = exercise(6, "Đi bộ trong gym", 5, 15, "LOW", "NONE", "GYM", 1, true);
    private static final ExerciseCandidate MEDIUM_GYM = exercise(7, "Squat tại gym", 5, 15, "MEDIUM", "NONE", "GYM", 2, false);
    private static final ExerciseCandidate STEP_GYM = exercise(8, "Bước bục tại gym", 5, 10, "MEDIUM", "stable_step", "GYM", 3, false);
    private static final List<ExerciseCandidate> CATALOG =
            List.of(LOW_HOME, LOW_ANY, MEDIUM_HOME, HIGH_HOME, MEDIUM_OUTDOOR, LOW_GYM, MEDIUM_GYM, STEP_GYM);

    @Test
    void recommendationConstraintsStressAndCombinedScenarios() throws IOException {
        List<Scenario> scenarios = List.of(
                scenario("INT01", "Giới hạn cường độ LOW", state(7, 2, 5, 40, 0, 5, "LOW"), profile("HOME", "LOW"),
                        CATALOG, "NORMAL", 40, false, "LOW", "Bỏ chiến lược tăng thử thách do giới hạn người dùng; chỉ trả bài LOW."),
                scenario("INT02", "Giới hạn cường độ MEDIUM", state(7, 2, 5, 40, 0, 5, "LOW"), profile("HOME", "MEDIUM"),
                        CATALOG, "CHALLENGE", 40, false, "MEDIUM", "Cho phép LOW và MEDIUM, không cho HIGH."),
                scenario("INT03", "Cho phép cường độ tối đa HIGH", state(7, 2, 5, 40, 0, 5, "LOW"),
                        profile("HOME", "HIGH"), CATALOG, "CHALLENGE", 40, false, "HIGH", "HIGH được phép nhưng không bắt buộc chọn."),
                scenario("INT04", "Hồ sơ cũ thiếu cường độ mặc định MEDIUM", state(7, 2, 3, 40, 0, 2, "LOW"),
                        profile("HOME", null), CATALOG, "NORMAL", 40, false, "MEDIUM", "Giữ tương thích với hồ sơ cũ."),
                scenario("INT05", "Người dùng chọn HIGH, chiến lược giới hạn LOW", state(5, 4, 2, 40, 0, 1, "LOW"),
                        profile("HOME", "HIGH"), CATALOG, "RECOVERY", 20, false, "LOW", "Giới hạn của chiến lược nghiêm ngặt hơn."),
                scenario("INT06", "Người dùng chọn MEDIUM, chiến lược giới hạn LOW", state(5, 4, 2, 40, 0, 1, "LOW"),
                        profile("HOME", "MEDIUM"), CATALOG, "RECOVERY", 20, false, "LOW", "Không trả bài cường độ MEDIUM."),
                scenario("STR01", "Stress thấp và trạng thái phù hợp", state(7, 2, 5, 40, 0, 5, "LOW"),
                        profile("HOME", "HIGH"), CATALOG, "CHALLENGE", 40, false, "HIGH", "Có thể challenge khi đủ điều kiện."),
                scenario("STR02", "Căng thẳng cao, năng lượng cao", state(7, 4, 5, 40, 0, 5, "LOW"),
                        profile("HOME", "HIGH"), CATALOG, "NORMAL", 40, false, "HIGH", "Căng thẳng cao chặn chiến lược tăng thử thách."),
                scenario("STR03", "Căng thẳng cao nhưng ngủ đủ", state(8, 5, 4, 40, 0, 4, "LOW"),
                        profile("HOME", "HIGH"), CATALOG, "NORMAL", 40, false, "HIGH", "Không tự động ép chiến lược phục hồi."),
                scenario("STR04", "Căng thẳng cao, ngủ ít, năng lượng thấp", state(5, 5, 2, 40, 0, 1, "LOW"),
                        profile("HOME", "HIGH"), CATALOG, "RECOVERY", 20, false, "LOW", "Giữ điều kiện phục hồi hiện tại."),
                scenario("STR05", "Căng thẳng bình thường, năng lượng thấp", state(7, 2, 2, 40, 0, 3, "LOW"),
                        profile("HOME", "HIGH"), CATALOG, "REDUCE", 20, false, "MEDIUM", "Năng lượng thấp kích hoạt chiến lược giảm tải."),
                scenario("COM01", "Tập tại nhà, giới hạn LOW, căng thẳng cao", state(7, 5, 5, 30, 0, 4, "LOW"),
                        profile("HOME", "LOW"), CATALOG, "NORMAL", 30, false, "LOW", "Tuân thủ địa điểm và cường độ; không tăng thử thách."),
                scenario("COM02", "Tập tại GYM, giới hạn MEDIUM, năng lượng thấp", state(7, 2, 2, 40, 0, 2, "LOW"),
                        profile("GYM", "MEDIUM"), CATALOG, "REDUCE", 20, false, "MEDIUM", "Chỉ chọn bài GYM/ANY và không vượt MEDIUM."),
                scenario("COM03", "Tập ngoài trời với giới hạn MEDIUM", state(7, 2, 4, 40, 0, 2, "LOW"),
                        profile("OUTDOOR", "MEDIUM"), CATALOG, "NORMAL", 40, false, "MEDIUM", "Không chọn bài HOME/GYM."),
                scenario("COM04", "Tập tại nhà, không dụng cụ, chỉ có 10 phút", state(7, 2, 1, 10, 0, 2, "LOW"),
                        profile("HOME", "LOW"), CATALOG, "REDUCE", 10, false, "LOW", "Bài không dụng cụ, đúng địa điểm, tổng kế hoạch không quá 10 phút."),
                scenario("COM05", "Căng thẳng và động lực cao", state(7, 5, 5, 40, 0, 5, "LOW", 5, 5),
                        profile("HOME", "HIGH"), CATALOG, "NORMAL", 40, false, "HIGH", "Căng thẳng vẫn được xét khi tâm trạng và động lực tốt."),
                scenario("COM06", "Quay lại sau nghỉ", state(7, 3, 3, 40, 4, 0, "HIGH"),
                        profile("HOME", "HIGH"), CATALOG, "RESTART", 15, false, "LOW", "Không tự động đề xuất intensity cao."),
                scenario("LOC01", "Hồ sơ HOME chỉ nhận bài HOME/ANY", state(7, 2, 4, 40, 0, 2, "LOW"),
                        profile("HOME", "HIGH"), CATALOG, "NORMAL", 40, false, "HIGH", "Không chọn bài OUTDOOR hoặc GYM."),
                scenario("LOC02", "Gym có bài phù hợp", state(7, 2, 2, 40, 0, 2, "LOW"),
                        profile("GYM", "MEDIUM"), CATALOG, "REDUCE", 20, false, "MEDIUM", "Có exercise GYM; energy lọc step nếu energy thấp."),
                scenario("LOC03", "Hồ sơ OUTDOOR chỉ nhận bài OUTDOOR/ANY", state(7, 2, 4, 40, 0, 2, "LOW"),
                        profile("OUTDOOR", "MEDIUM"), CATALOG, "NORMAL", 40, false, "MEDIUM", "Không chọn bài HOME/GYM."),
                scenario("LOC04", "Không có bài phù hợp địa điểm", state(7, 2, 4, 40, 0, 2, "LOW"),
                        profile("HOME", "HIGH"), List.of(LOW_GYM), "NORMAL", 40, true, "HIGH",
                        "Kết quả rỗng kèm lý do location, không fallback sai địa điểm."));

        List<String> testCases = new ArrayList<>();
        List<String> testResults = new ArrayList<>();
        testCases.add("ma_kiem_thu,kich_ban,nguon_du_lieu,trang_thai_dau_vao,ho_so,tieu_chi_dat");
        testResults.add("ma_kiem_thu,kich_ban,chien_luoc,ly_do_chien_luoc,bai_tap,thoi_luong_du_kien_phut,cuong_do,ly_do_bai_tap,ngan_sach_phut,so_luong_bi_loai,thong_bao,ket_qua");

        InterventionEngine engine = new InterventionEngine();
        RecommendationScorer scorer = new RecommendationScorer();
        for (Scenario scenario : scenarios) {
            StrategyDecision decision = engine.decide(scenario.state(), RULES, scenario.profile().maxExerciseIntensity());
            RecommendationResult result = scorer.recommend(
                    scenario.state(), scenario.profile(), decision, scenario.catalog(), 3);
            List<ExerciseCandidate> selected = result.items().stream()
                    .map(item -> scenario.catalog().stream()
                            .filter(exercise -> exercise.id() == item.exerciseId()).findFirst().orElseThrow())
                    .toList();
            int totalMinutes = result.plan().stream().mapToInt(RecommendationResult.PlanItem::plannedMin).sum();

            assertEquals(scenario.expectedStrategy(), decision.code(), scenario.id() + " strategy");
            assertEquals(scenario.expectEmpty(), selected.isEmpty(), scenario.id() + " empty result");
            assertTrue(totalMinutes <= result.budgetMinutes(), scenario.id() + " time budget");
            assertTrue(result.budgetMinutes() <= scenario.maxPlanMinutes(), scenario.id() + " strategy budget");
            assertTrue(selected.stream().allMatch(exercise ->
                    StrategyParams.intensityRank(exercise.intensity()) <= StrategyParams.intensityRank(scenario.expectedMaxIntensity())),
                    scenario.id() + " effective intensity cap");
            assertTrue(selected.stream().allMatch(exercise ->
                    "ANY".equals(exercise.location()) || scenario.profile().location().equals(exercise.location())),
                    scenario.id() + " hard location filter");
            assertTrue(selected.stream().allMatch(exercise -> equipmentOk(exercise.equipment(), scenario.profile().equipment())),
                    scenario.id() + " equipment filter");
            assertTrue(selected.stream().allMatch(exercise ->
                    exercise.durationMin() <= scenario.state().freeTimeMin()), scenario.id() + " minimum duration");
            if (scenario.expectEmpty()) {
                assertTrue(result.message() != null && result.message().contains("địa điểm"));
            } else {
                assertTrue(result.items().stream().flatMap(item -> item.reasons().stream())
                        .anyMatch(reason -> reason.contains("địa điểm")));
                assertTrue(result.items().stream().flatMap(item -> item.reasons().stream())
                        .anyMatch(reason -> reason.contains("mức tối đa bạn chọn")));
            }
            if (scenario.id().equals("INT01")) {
                assertTrue(decision.reasons().stream().anyMatch(reason -> reason.contains("cường độ tối thiểu")));
            }

            String selectedNames = selected.stream().map(ExerciseCandidate::name).collect(Collectors.joining(" | "));
            String planned = result.plan().stream().map(item -> Integer.toString(item.plannedMin()))
                    .collect(Collectors.joining(" | "));
            String intensities = selected.stream().map(ExerciseCandidate::intensity).collect(Collectors.joining(" | "));
            String explanations = result.items().stream()
                    .map(item -> item.name() + ": " + String.join("; ", item.reasons()))
                    .collect(Collectors.joining(" | "));
            testCases.add(csv(scenario.id(), scenario.name(), "Đầu vào unit test dựng sẵn, không phải dữ liệu người dùng",
                    stateJson(scenario.state()), profileJson(scenario.profile()), scenario.acceptanceCriteria()));
            testResults.add(csv(scenario.id(), scenario.name(), decision.code(), String.join("; ", decision.reasons()),
                    selectedNames, planned, intensities, explanations, Integer.toString(result.budgetMinutes()),
                    result.excluded().toString(), result.message() == null ? "" : result.message(), "PASS"));
        }

        Path output = Path.of(System.getProperty("phase9a.exportDir", "target/phase9a_recommendation_fix"));
        Files.createDirectories(output);
        Files.writeString(output.resolve("phase9a_test_cases.csv"), String.join(System.lineSeparator(), testCases)
                + System.lineSeparator());
        Files.writeString(output.resolve("phase9a_test_results.csv"), String.join(System.lineSeparator(), testResults)
                + System.lineSeparator());
    }

    private static Scenario scenario(String id, String name, UserState state, ProfileContext profile,
                                    List<ExerciseCandidate> catalog, String expectedStrategy, int maxPlanMinutes,
                                    boolean expectEmpty, String expectedMaxIntensity, String acceptanceCriteria) {
        return new Scenario(id, name, state, profile, catalog, expectedStrategy, maxPlanMinutes,
                expectEmpty, expectedMaxIntensity, acceptanceCriteria);
    }

    private static boolean equipmentOk(String required, String owned) {
        return required == null || required.isBlank() || "NONE".equalsIgnoreCase(required)
                || (owned != null && java.util.Arrays.stream(required.split(","))
                .allMatch(code -> java.util.Arrays.stream(owned.split(","))
                        .anyMatch(item -> item.trim().equalsIgnoreCase(code.trim()))));
    }

    private static ExerciseCandidate exercise(long id, String name, int min, int max, String intensity,
                                              String equipment, String location, int energy, boolean recovery) {
        return new ExerciseCandidate(id, name, min, max, 2, intensity, equipment, location, "full_body",
                "STAY_ACTIVE", energy, recovery, Set.of());
    }

    private static UserState state(double sleep, int stress, int energy, int freeTime, int missed, int frequency,
                                   String risk) {
        return state(sleep, stress, energy, freeTime, missed, frequency, risk, 3, 3);
    }

    private static UserState state(double sleep, int stress, int energy, int freeTime, int missed, int frequency,
                                   String risk, int mood, int motivation) {
        return new UserState(sleep, stress, energy, mood, motivation, freeTime, false, missed, frequency, risk,
                UserState.RISK_SOURCE_HISTORY);
    }

    private static ProfileContext profile(String location, String maxIntensity) {
        return profile(location, maxIntensity, "NONE");
    }

    private static ProfileContext profile(String location, String maxIntensity, String equipment) {
        return new ProfileContext("INTERMEDIATE", "STAY_ACTIVE", location, equipment, "", maxIntensity);
    }

    private static String stateJson(UserState state) {
        return "{\"sleep_hours\":" + state.sleepHours() + ",\"stress_level\":" + state.stress()
                + ",\"energy_level\":" + state.energy() + ",\"mood\":" + state.mood()
                + ",\"motivation\":" + state.motivation() + ",\"free_time_min\":" + state.freeTimeMin()
                + ",\"missed_days\":" + state.missedDays() + ",\"workout_frequency_7d\":"
                + state.workoutFrequency7d() + ",\"risk_level\":\"" + state.riskLevel() + "\"}";
    }

    private static String profileJson(ProfileContext profile) {
        return "{\"location\":\"" + profile.location() + "\",\"equipment\":\"" + profile.equipment()
                + "\",\"maxExerciseIntensity\":\"" + profile.maxExerciseIntensity() + "\"}";
    }

    private static String csv(String... values) {
        return java.util.Arrays.stream(values)
                .map(value -> "\"" + value.replace("\"", "\"\"") + "\"")
                .collect(Collectors.joining(","));
    }

    private record Scenario(String id, String name, UserState state, ProfileContext profile,
                            List<ExerciseCandidate> catalog, String expectedStrategy, int maxPlanMinutes,
                            boolean expectEmpty, String expectedMaxIntensity, String acceptanceCriteria) {}
}
