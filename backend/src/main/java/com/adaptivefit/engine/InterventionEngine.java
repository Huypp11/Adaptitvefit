package com.adaptivefit.engine;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Chon chien luoc can thiep: quy tac dung dau tien (theo do uu tien) quyet dinh. Khong dung LLM. */
public class InterventionEngine {

    public StrategyDecision decide(UserState state, List<RuleDef> rules) {
        return decide(state, rules, "HIGH");
    }

    public StrategyDecision decide(UserState state, List<RuleDef> rules, String userMaxIntensity) {
        Map<String, Double> facts = state.facts();
        List<RuleDef> ordered = rules.stream().filter(RuleDef::active)
                .sorted(Comparator.comparingInt(RuleDef::priority)).toList();
        List<String> deferredReasons = new java.util.ArrayList<>();
        for (RuleDef r : ordered) {
            ConditionExpression expr;
            StrategyParams params;
            try {
                expr = ConditionExpression.parse(r.conditionExpr());
                params = StrategyParams.parse(r.paramsExpr());
            } catch (IllegalArgumentException invalid) {
                continue; // quy tac hong da bi chan khi luu; bo qua de khong lam hong ca he thong
            }
            int g = expr.firstMatch(facts);
            if (g >= 0) {
                if (params.minIntensity() != null
                        && StrategyParams.intensityRank(params.minIntensity())
                        > StrategyParams.intensityRank(userMaxIntensity)) {
                    deferredReasons.add("Không áp dụng chiến lược " + r.code()
                            + " vì cường độ tối thiểu của chiến lược vượt mức tối đa bạn chấp nhận ("
                            + userMaxIntensity + ").");
                    continue;
                }
                List<String> reasons = expr.explain(g, facts);
                if (reasons.isEmpty()) reasons = List.of("Không có dấu hiệu cần điều chỉnh");
                reasons = new java.util.ArrayList<>(reasons);
                reasons.addAll(deferredReasons);
                return new StrategyDecision(r.code(), r.description(), reasons, params);
            }
        }
        if (!deferredReasons.isEmpty()) {
            StrategyDecision fallback = new StrategyDecision("NORMAL", "Giữ kế hoạch bình thường.",
                    List.of("Không có quy tắc nào cần điều chỉnh"), StrategyParams.none());
            List<String> reasons = new java.util.ArrayList<>(fallback.reasons());
            reasons.addAll(deferredReasons);
            return new StrategyDecision(fallback.code(), fallback.description(), reasons, fallback.params());
        }
        return new StrategyDecision("NORMAL", "Giữ kế hoạch bình thường.",
                List.of("Không có quy tắc nào cần điều chỉnh"), StrategyParams.none());
    }
}
