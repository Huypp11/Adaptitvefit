# Phase 9.3 - Đánh giá Recommendation Engine

> **Phạm vi tài liệu:** Đây là kết quả audit ban đầu trước khi triển khai Phase 9A. Các thay đổi đã làm và kiểm tra hiện tại được ghi trong [báo cáo triển khai Phase 9A](../phase9a_recommendation_fix/phase9a_implementation_report.md), [kết quả kịch bản Phase 9A](../phase9a_recommendation_fix/phase9a_test_results.csv) và [kết quả E2E sau V11](./phase9_e2e_results.csv).

## Luồng và tín hiệu thực tế

`GET /api/recommendations/today` lấy check-in hôm nay, profile và history; tính risk qua model hoặc history fallback; chạy `InterventionEngine`; sau đó chấm điểm, lọc và dựng plan từ exercise catalog. Kết quả được lưu thành snapshot recommendation gắn với check-in.

Thông tin có tác động:

- **Rule engine:** sleep, stress, energy, free time, missed days, workout frequency, exercised today và risk level.
- **Exercise scorer:** time budget, energy requirement, goal, fitness level/difficulty, equipment, location, preferred workout, recovery suitability, intensity và tags.
- **ML risk features:** 10 feature trong `model_evaluation.md`; risk là một input cho strategy, không phải bài tập tự sinh.
- **Không thấy sử dụng trực tiếp để chấm bài:** mood và motivation; profile không có trường intensity tối đa. Stress cao đơn lẻ cũng không kích hoạt rule; stress chỉ tham gia rule RECOVERY khi đồng thời ngủ <6h và energy <=2.
- Equipment, thời gian, năng lượng, intensity của strategy và recovery là hard filter. Location là điểm ưu tiên mềm; không có hard filter theo location.

UI hiển thị strategy/reasons, state, risk source/demo label, plan và scoring breakdown. Khi bắt đầu từ recommendation, backend xác minh bài/thời lượng thuộc snapshot và ghi exercise selection vào workout session.

## Kịch bản đã chạy

Đây là **constructed test input**, không phải dữ liệu người dùng hoặc dữ liệu khảo sát. Recommendation engine/scorer được chạy bằng JUnit với rule definition tương ứng migration V7/V9 và exercise fixtures từ seed SQL (bao gồm bài HIGH intensity). Chi tiết input và output từng ca nằm trong [test_scenarios.csv](./test_scenarios.csv) và [phase9_test_results.csv](./phase9_test_results.csv).

| Ca | Kết quả chạy | Đánh giá |
|---|---|---|
| Năng lượng/thời gian cao | CHALLENGE; plan 45/60 phút; MEDIUM/HIGH | Điều kiện strategy đạt. Không xem đây là lời khuyên an toàn/y tế. |
| Mệt, 10 phút | REDUCE; plan đúng 10 phút; bài được chọn LOW | Phù hợp với constraint thời gian/năng lượng fixture. |
| Stress 5, ngủ 7h, energy 4 | CHALLENGE; có bài HIGH | **Gap:** stress cao riêng lẻ không giảm cường độ/đổi strategy. |
| Bỏ lỡ 4 ngày | RESTART; plan 15 phút; LOW | Rule restart có tác dụng trong ca này. |
| Ở nhà, không dụng cụ | Có bài outdoor “Đi bộ nhanh hoặc chạy nhẹ” trong top 3 | **Gap:** vị trí chỉ là score; có thể trả về bài không đúng nơi tập. Equipment không cần vẫn được lọc. |
| Muốn tập gym | Không có bài `GYM` trong seed migrations; output là bài ANY/HOME | **Gap:** catalog seed 38 dòng có location ANY/HOME/OUTDOOR, không có GYM. Database runtime chưa đọc nên catalog thực tế có thể khác. |
| Không muốn cường độ cao | Dù test gửi text `low intensity`, CHALLENGE vẫn trả 2 bài HIGH | **Gap:** profile không biểu diễn max intensity; preferredWorkout là text, không phải constraint. |
| Quay lại sau nghỉ | RESTART; plan <=15 phút, LOW | Mapped qua missed days; chưa có feature riêng cho thời gian nghỉ dài. |
| Ngủ ít + stress cao + energy thấp | RECOVERY; plan 20 phút, chỉ LOW/recovery-suitable | Rule tổ hợp đạt tiêu chí test. |

Các status `GAP` là phát hiện cần đánh giá/thiết kế tiếp, không phải assertion thất bại của Java test suite. Scenario suite chạy thành công 1 test method, không chứng minh hành vi người dùng thật.

## Khả năng trả lời các câu hỏi sản phẩm

- Không có nghiên cứu nào chứng minh gợi ý tốt hơn check-in/history hoặc cải thiện adherence.
- Hệ thống có giải thích rule và điểm, nhưng không nên dùng cluster/risk để buộc người cùng nhóm nhận bài giống nhau.
- Gợi ý địa điểm chưa đủ ràng buộc; yêu cầu không tập cường độ cao chưa thể biểu diễn; stress đơn lẻ có thể vẫn nhận strategy CHALLENGE.
- Mood/motivation có mặt trong user state/model nhưng chưa thấy ảnh hưởng trực tiếp tới exercise scoring.
- Chưa có outcome thực tế để đánh giá recommendation acceptance, completion, fatigue, suitability hoặc tác động lên hành vi.
- Nội dung không thay cho tư vấn y tế; không suy ra độ an toàn tập luyện từ risk score, stress, energy hay cluster.

## Hướng xử lý trước khi đổi production

1. Xác định semantics: location là hard constraint hay soft preference; nếu gym không có bài thì hiển thị “không có bài phù hợp”, không giả gợi ý gym.
2. Thêm một biểu diễn rõ ràng, có validation cho intensity cap/không muốn cường độ cao; test cả NORMAL/CHALLENGE/RESTART.
3. Quyết định bằng product/safety review cách high stress đơn lẻ ảnh hưởng strategy; kiểm chứng trên dữ liệu và với người dùng trước khi sửa rule.
4. Thiết kế test catalog-backed bằng disposable DB, bao phủ item list, plan minutes, profile constraints, missing/unknown và API serialization.
5. Đo utility theo outcome có consent và so với baseline rule/check-in; không tuyên bố hiệu quả thực tế từ unit test.
