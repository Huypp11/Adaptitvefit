ALTER TABLE user_profiles
  ADD COLUMN max_exercise_intensity VARCHAR(10) NULL;

UPDATE interventions
SET condition_expr = 'risk_rank==LOW&energy_level>=4&workout_frequency_7d>=3&stress_level<4&sleep_hours>=6&mood>=3&motivation>=3',
    description = 'Tăng nhẹ thử thách khi nguy cơ thấp, trạng thái thể chất và tinh thần phù hợp, và căng thẳng dưới mức cao.'
WHERE code = 'CHALLENGE';

INSERT INTO exercises
    (name, description, duration_min, duration_max, difficulty, intensity, equipment, location,
     muscle_group, goal, energy_requirement, recovery_suitable, instructions, media_url, active,
     met, steps, media_urls, tips)
SELECT 'Đi bộ nhẹ trong phòng gym', 'Đi bộ thoải mái trong khu vực đi bộ của phòng tập.',
       5, 15, 1, 'LOW', 'NONE', 'GYM', 'full_body', 'STAY_ACTIVE', 1, 1,
       'Đi với tốc độ dễ chịu, giữ tư thế thẳng và giảm tốc từ từ trước khi dừng.',
       NULL, 1, 2.8,
       'Đi bộ chậm 1-2 phút để làm quen.\nDuy trì tốc độ vẫn có thể nói chuyện.\nGiảm tốc độ trong 1-2 phút cuối.',
       NULL, 'Chọn lối đi thông thoáng, mang giày phù hợp.'
WHERE NOT EXISTS (SELECT 1 FROM exercises WHERE name = 'Đi bộ nhẹ trong phòng gym');

INSERT INTO exercises
    (name, description, duration_min, duration_max, difficulty, intensity, equipment, location,
     muscle_group, goal, energy_requirement, recovery_suitable, instructions, media_url, active,
     met, steps, media_urls, tips)
SELECT 'Squat tay không tại phòng gym', 'Squat không dùng tạ, phù hợp khi không muốn sử dụng máy tập.',
       5, 12, 2, 'MEDIUM', 'NONE', 'GYM', 'legs', 'BUILD_MUSCLE', 2, 0,
       'Đẩy hông ra sau như ngồi xuống ghế, giữ lưng thẳng và đứng lên có kiểm soát.',
       NULL, 1, 3.8,
       'Đứng vững, hai chân rộng bằng vai.\nHạ người trong phạm vi thoải mái, đầu gối cùng hướng mũi chân.\nĐẩy qua bàn chân để đứng lên, không khóa gối.',
       NULL, 'Không tập qua mức đau; có thể bám nhẹ vào điểm tựa chắc chắn nếu cần.'
WHERE NOT EXISTS (SELECT 1 FROM exercises WHERE name = 'Squat tay không tại phòng gym');

INSERT INTO exercises
    (name, description, duration_min, duration_max, difficulty, intensity, equipment, location,
     muscle_group, goal, energy_requirement, recovery_suitable, instructions, media_url, active,
     met, steps, media_urls, tips)
SELECT 'Bước lên bục thấp tại phòng gym', 'Bước lên bục thấp với nhịp chậm và có kiểm soát.',
       5, 10, 2, 'MEDIUM', 'stable_step', 'GYM', 'legs', 'ENDURANCE', 3, 0,
       'Chỉ thực hiện khi có bục thấp chắc chắn tương thích với thiết bị người dùng khai báo.',
       NULL, 1, 4.0,
       'Đặt bục thấp, chắc chắn trên mặt sàn phẳng.\nBước từng chân lên và xuống chậm, luân phiên hai bên.\nDừng nếu bục không ổn định.',
       NULL, 'Cần có stable_step trong hồ sơ; không dùng ghế/bàn không chắc chắn.'
WHERE NOT EXISTS (SELECT 1 FROM exercises WHERE name = 'Bước lên bục thấp tại phòng gym');

INSERT INTO exercise_tags (exercise_id, tag)
SELECT e.id, 'low_energy' FROM exercises e
WHERE e.name = 'Đi bộ nhẹ trong phòng gym'
  AND NOT EXISTS (SELECT 1 FROM exercise_tags t WHERE t.exercise_id = e.id AND t.tag = 'low_energy');

INSERT INTO exercise_tags (exercise_id, tag)
SELECT e.id, 'bodyweight' FROM exercises e
WHERE e.name = 'Squat tay không tại phòng gym'
  AND NOT EXISTS (SELECT 1 FROM exercise_tags t WHERE t.exercise_id = e.id AND t.tag = 'bodyweight');

INSERT INTO exercise_tags (exercise_id, tag)
SELECT e.id, 'step_up' FROM exercises e
WHERE e.name = 'Bước lên bục thấp tại phòng gym'
  AND NOT EXISTS (SELECT 1 FROM exercise_tags t WHERE t.exercise_id = e.id AND t.tag = 'step_up');
