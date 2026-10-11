-- Quy tac can thiep dang chuoi (admin chinh duoc). Cot condition_json cu khong con duoc su dung.
-- Cu phap dieu kien: nhom cach nhau boi "|" (HOAC), dieu kien trong nhom cach nhau boi "&" (VA), "*" luon dung.
ALTER TABLE interventions
  ADD COLUMN condition_expr VARCHAR(500) NULL,
  ADD COLUMN params_expr VARCHAR(500) NULL;

UPDATE interventions SET priority = 1, condition_expr = 'missed_days>=3 | risk_rank>=HIGH&missed_days>=2',
  params_expr = 'maxDurationMin=15;maxIntensity=LOW;difficultyShift=-1',
  description = 'Một buổi tập ngắn, dễ hoàn thành để khởi động lại thói quen.' WHERE code = 'RESTART';
UPDATE interventions SET priority = 2, condition_expr = 'sleep_hours<6&stress_level>=4&energy_level<=2',
  params_expr = 'maxDurationMin=20;maxIntensity=LOW;requireRecovery=true;difficultyShift=-1',
  description = 'Vận động nhẹ để phục hồi: đi bộ, giãn cơ, vận động khớp.' WHERE code = 'RECOVERY';
UPDATE interventions SET priority = 3, condition_expr = 'free_time_min<20 | energy_level<=2 | risk_rank==MEDIUM',
  params_expr = 'maxDurationMin=20;maxIntensity=MEDIUM;difficultyShift=-0.5',
  description = 'Giảm thời lượng hoặc cường độ so với kế hoạch thường ngày.' WHERE code = 'REDUCE';
UPDATE interventions SET priority = 4, condition_expr = 'risk_rank==LOW&energy_level>=4&workout_frequency_7d>=3',
  params_expr = 'minIntensity=MEDIUM;difficultyShift=0.7',
  description = 'Tăng nhẹ thử thách vì bạn đang duy trì tốt.' WHERE code = 'CHALLENGE';
UPDATE interventions SET priority = 5, condition_expr = '*', params_expr = '',
  description = 'Giữ kế hoạch bình thường.' WHERE code = 'NORMAL';
