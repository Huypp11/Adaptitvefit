package com.adaptivefit.service;

import com.adaptivefit.dto.InterventionResponse;
import com.adaptivefit.dto.InterventionUpdateRequest;
import com.adaptivefit.engine.ConditionExpression;
import com.adaptivefit.engine.RuleDef;
import com.adaptivefit.engine.StrategyParams;
import com.adaptivefit.entity.Intervention;
import com.adaptivefit.exception.BadRequestException;
import com.adaptivefit.exception.NotFoundException;
import com.adaptivefit.repository.InterventionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterventionService {

    private final InterventionRepository repository;

    @Transactional(readOnly = true)
    public List<InterventionResponse> list() {
        return repository.findAllByOrderByPriorityAsc().stream().map(InterventionResponse::from).toList();
    }

    @Transactional
    public InterventionResponse update(Long id, InterventionUpdateRequest req) {
        Intervention i = repository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy quy tắc"));
        try {
            ConditionExpression.parse(req.conditionExpr());
            StrategyParams.parse(req.paramsExpr());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        i.setPriority(req.priority());
        i.setConditionExpr(req.conditionExpr().trim());
        i.setParamsExpr(req.paramsExpr() == null ? "" : req.paramsExpr().trim());
        i.setDescription(req.description());
        i.setActive(req.active());
        return InterventionResponse.from(repository.save(i));
    }

    @Transactional(readOnly = true)
    public List<RuleDef> activeRules() {
        return repository.findAllByOrderByPriorityAsc().stream()
                .map(i -> new RuleDef(i.getCode(), i.getPriority(), i.getConditionExpr(), i.getParamsExpr(),
                        i.getDescription(), Boolean.TRUE.equals(i.getActive())))
                .toList();
    }
}
