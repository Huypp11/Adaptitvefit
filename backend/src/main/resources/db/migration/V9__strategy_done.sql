-- Chien luoc DONE: hom nay da tap thi khong goi y buoi tap chinh nua, chi tuy chon thu gian nhe.
UPDATE interventions SET priority = priority + 1 WHERE code IN ('RESTART', 'RECOVERY', 'REDUCE', 'CHALLENGE', 'NORMAL');
INSERT INTO interventions (code, priority, description, active, condition_expr, params_expr)
VALUES ('DONE', 1, 'Bạn đã tập hôm nay nên không cần tập thêm. Nếu muốn, có thể thư giãn nhẹ.', 1,
        'exercised_today==1', 'maxDurationMin=10;maxIntensity=LOW;requireRecovery=true;difficultyShift=-1');
