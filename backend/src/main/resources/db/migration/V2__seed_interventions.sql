-- Dinh dang condition_json la ban nhap; se chot khi xay Intervention Engine (Phase 4).
INSERT INTO interventions (code, priority, condition_json, description, active) VALUES
('RESTART', 1, '{"rule":"missed_days>=3 OR (risk=HIGH AND missed_days>=2)"}', 'Mot buoi tap ngan, de hoan thanh de khoi dong lai thoi quen.', 1),
('RECOVERY', 2, '{"rule":"sleep<6 AND stress>=4 AND energy<=2"}', 'Van dong nhe: di bo, gian co, mobility.', 1),
('REDUCE', 3, '{"rule":"free_time<20 OR energy<=2 OR risk=MEDIUM"}', 'Giam thoi luong hoac cuong do so voi ke hoach.', 1),
('CHALLENGE', 4, '{"rule":"risk=LOW AND energy>=4 AND adherence_7d>=0.8"}', 'Tang nhe thu thach khi dang duy tri tot.', 1),
('NORMAL', 5, '{"rule":"default"}', 'Giu ke hoach binh thuong.', 1);
