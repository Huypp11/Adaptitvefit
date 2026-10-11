package com.adaptivefit.engine;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Chon chien luoc can thiep: quy tac dung dau tien (theo do uu tien) quyet dinh. Khong dung LLM. */
public class InterventionEngine {

    public StrategyDecision decide(UserState state, List<RuleDef> rules) {
        Map<String, Double> facts = state.facts();
        List<RuleDef> ordered = rules.stream().filter(RuleDef::active)
                .sorted(Comparator.comparingInt(RuleDef::priority)).toList();
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
                List<String> reasons = expr.explain(g, facts);
                if (reasons.isEmpty()) reasons = List.of("Không có dấu hiệu cần điều chỉnh");
                return new StrategyDecision(r.code(), r.description(), reasons, params);
            }
        }
        return new StrategyDecision("NORMAL", "Giữ kế hoạch bình thường.",
                List.of("Không có quy tắc nào cần điều chỉnh"), StrategyParams.none());
    }
}
