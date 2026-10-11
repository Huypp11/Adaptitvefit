package com.adaptivefit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "interventions")
@Getter @Setter @NoArgsConstructor
public class Intervention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private Integer priority;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Boolean active = true;

    /** Bieu thuc dieu kien, xem ConditionExpression. */
    @Column(name = "condition_expr", length = 500)
    private String conditionExpr;

    /** Rang buoc chon bai tap, xem StrategyParams. */
    @Column(name = "params_expr", length = 500)
    private String paramsExpr;
}
