package com.adaptivefit.engine;

/** Mot quy tac can thiep (tach khoi entity de engine thuan Java, de kiem thu). */
public record RuleDef(String code, int priority, String conditionExpr, String paramsExpr,
                      String description, boolean active) {}
