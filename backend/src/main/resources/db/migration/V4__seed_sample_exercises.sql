-- Thu vien bai tap MAU de phat trien/demo. Admin co the sua hoac tat qua API.
INSERT INTO exercises (id, name, description, duration_min, duration_max, difficulty, intensity, equipment, location, muscle_group, goal, energy_requirement, recovery_suitable, instructions, media_url, active) VALUES
(1, 'Đi bộ nhẹ', 'Đi bộ với tốc độ thoải mái, phù hợp khi mệt hoặc ít thời gian.', 10, 30, 1, 'LOW', 'NONE', 'ANY', 'full_body', 'STAY_ACTIVE', 1, 1, 'Đi với tốc độ nói chuyện được. Giữ lưng thẳng, vung tay tự nhiên.', NULL, 1),
(2, 'Giãn cơ toàn thân', 'Chuỗi giãn cơ nhẹ cho cổ, vai, lưng và chân.', 8, 15, 1, 'LOW', 'NONE', 'HOME', 'full_body', 'RECOVERY', 1, 1, 'Giữ mỗi tư thế 20-30 giây, thở đều, không giãn đến mức đau.', NULL, 1),
(3, 'Vận động khớp nhẹ', 'Xoay cổ, vai, hông, cổ chân theo vòng nhỏ.', 5, 12, 1, 'LOW', 'NONE', 'HOME', 'joints', 'RECOVERY', 1, 1, 'Xoay chậm mỗi khớp 10 lần theo mỗi hướng.', NULL, 1),
(4, 'Squat tay không', 'Squat với trọng lượng cơ thể.', 10, 20, 2, 'MEDIUM', 'NONE', 'HOME', 'legs', 'BUILD_MUSCLE', 3, 0, '3 hiệp x 10-15 lần, nghỉ 45 giây. Đầu gối cùng hướng mũi chân.', NULL, 1),
(5, 'Hít đất', 'Hít đất cơ bản, có thể hạ gối xuống nếu còn khó.', 10, 20, 3, 'MEDIUM', 'NONE', 'HOME', 'upper_body', 'BUILD_MUSCLE', 3, 0, '3 hiệp x 6-12 lần. Giữ thân thẳng từ đầu đến gót.', NULL, 1),
(6, 'Plank', 'Giữ tư thế plank để tập cơ lõi.', 5, 15, 3, 'MEDIUM', 'NONE', 'HOME', 'core', 'BUILD_MUSCLE', 3, 0, '3 hiệp x 20-40 giây. Siết bụng, không võng lưng.', NULL, 1),
(7, 'Đi bộ nhanh hoặc chạy nhẹ', 'Cardio ngoài trời cường độ vừa.', 20, 40, 3, 'MEDIUM', 'NONE', 'OUTDOOR', 'legs', 'ENDURANCE', 3, 0, 'Khởi động 3 phút, sau đó giữ nhịp đều. Giảm tốc nếu hụt hơi.', NULL, 1),
(8, 'Cardio tại chỗ 10 phút', 'Nhảy tại chỗ, nâng cao gối, đạp gót.', 10, 15, 2, 'MEDIUM', 'NONE', 'HOME', 'full_body', 'LOSE_WEIGHT', 3, 0, 'Luân phiên 40 giây vận động, 20 giây nghỉ.', NULL, 1),
(9, 'Yoga cơ bản', 'Chuỗi yoga nhẹ cho người mới.', 15, 30, 2, 'LOW', 'yoga_mat', 'HOME', 'full_body', 'RECOVERY', 2, 1, 'Chuyển tư thế chậm theo nhịp thở. Dừng nếu thấy đau.', NULL, 1);

INSERT INTO exercise_tags (exercise_id, tag) VALUES
(1,'beginner'),(1,'recovery'),(1,'low_energy'),(1,'cardio'),
(2,'beginner'),(2,'recovery'),(2,'low_energy'),(2,'stretching'),
(3,'beginner'),(3,'recovery'),(3,'low_energy'),(3,'mobility'),
(4,'bodyweight'),(4,'strength'),
(5,'bodyweight'),(5,'strength'),
(6,'bodyweight'),(6,'core'),
(7,'cardio'),(7,'outdoor'),
(8,'cardio'),(8,'short'),(8,'bodyweight'),
(9,'beginner'),(9,'recovery'),(9,'flexibility');
