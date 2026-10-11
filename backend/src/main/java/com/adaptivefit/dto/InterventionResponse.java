package com.adaptivefit.dto;

import com.adaptivefit.entity.Intervention;

public record InterventionResponse(Long id, String code, Integer priority, String description, Boolean active,
                                   String conditionExpr, String paramsExpr) {
    public static InterventionResponse from(Intervention i) {
        return new InterventionResponse(i.getId(), i.getCode(), i.getPriority(), i.getDescription(), i.getActive(),
                i.getConditionExpr(), i.getParamsExpr());
    }
}
