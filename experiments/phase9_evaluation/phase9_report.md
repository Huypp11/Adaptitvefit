# Phase 9 - Đánh giá mô hình AI và Recommendation Engine

**Ngày kiểm tra:** 2026-11-02  
**Phạm vi:** audit artifact/code hiện có, chạy test AI/backend/frontend, kiểm tra FastAPI bằng `TestClient`, chạy kịch bản Recommendation Engine và đánh giá các thay đổi Phase 9A.

## Kết luận tóm tắt

- Model đang active là **v2.0, Logistic Regression**. Manifest và model card xác nhận dataset `demo_v1` được sinh mô phỏng; 53 hồ sơ khảo sát chỉ cung cấp phân phối nền (`survey_profile(n=53)`). Không có bằng chứng model được huấn luyện trực tiếp trên 322 phản hồi survey hoặc nhãn dropout thực tế.
- Metric v2.0 được tính trên 4.480 dòng của 80 người mô phỏng được giữ riêng theo `user_id`. Đây là test split nội bộ trên dữ liệu mô phỏng, **không phải xác thực ngoài mẫu thực tế**.
- Phase 9A đã bổ sung giới hạn cường độ trong hồ sơ, lọc cứng theo location, điều kiện stress/sleep/mood/motivation cho `CHALLENGE`, và ba bài GYM qua migration V11. Đã thêm kiểm thử hợp đồng profile và 21 kịch bản nội bộ cho Recommendation Engine.
- Feedback loop đã có liên kết recommendation, workout, completion/abandonment và feedback. Chưa ghi nhận riêng các sự kiện recommendation được hiển thị, bỏ qua, hoặc lựa chọn bài bị thay đổi.
- Kiểm tra lại ngày 2026-11-02: AI service **21/21 pytest passed**, backend **24/24 Maven tests passed**, frontend TypeScript và production build thành công. Build frontend còn cảnh báo bundle JavaScript khoảng 680 kB.
- Người dùng xác nhận Flyway đã nhận migration V11. Trong kiểm tra tích hợp, profile API ghi/đọc lại `maxExerciseIntensity=LOW` thành công, xác nhận runtime truy cập được cột mới.
- Đã bắt đầu kiểm tra trên database người dùng xác nhận là disposable: đăng ký hai tài khoản test, lưu profile/check-in hợp lệ; validation profile/check-in ngoài miền trả 4xx. Tuy nhiên `GET /api/recommendations/today` trả HTTP 500 ở lần đầu và hết thời gian chờ sau 30 giây ở lần chẩn đoán. Không tiếp tục retry. Workout start/complete vì vậy chưa chạy. Hai tài khoản và dữ liệu liên quan được giữ trong schema disposable, không ghi thông tin đăng nhập vào báo cáo. Chi tiết ở [phase9_e2e_results.csv](./phase9_e2e_results.csv).
- AI Service health và frontend HTTP trả 200; chưa xác minh UI sau đăng nhập. Bộ test hiện có unit/service tests, chưa có test `@SpringBootTest`, `@DataJpaTest`, `MockMvc` hoặc Testcontainers.

## Kết quả theo pha

| Phần | Kết quả |
|---|---|
| 9.1 Audit model và tích hợp | Đã đối chiếu registry, manifest, model card, schema, pipeline, SHAP, FastAPI và Spring client |
| 9.2 Đánh giá dropout | Đã xác minh metric test của demo; không retrain và không dùng survey 322 dòng làm nhãn |
| 9.3 Recommendation Engine | Audit ban đầu chạy 9 tình huống; Phase 9A xử lý các gap đã duyệt ở stress/CHALLENGE, location, cường độ và catalog GYM; 21 scenario tests sau sửa đều đạt |
| 9.4 Feedback loop | Đã đối chiếu controller/service/entity/migration/frontend; có trạng thái cốt lõi, thiếu một số interaction event |
| 9.5 Kiểm thử tích hợp | AI Service bằng TestClient và một phần API sau V11 đã kiểm tra; recommendation trả 500/timeout nên luồng E2E chưa hoàn tất; chưa kiểm tra workout lifecycle hoặc UI đã đăng nhập |

## Giới hạn và quyết định

Model dropout v2.0 và registry không bị thay đổi; Phase 9A có thay đổi additive ở profile/API/schema và migration V11. Metric cao trên dữ liệu mô phỏng không đủ căn cứ để triển khai dự đoán như kết quả nghiên cứu. Audit, các sửa đổi recommendation và unit/build tests đã hoàn tất, nhưng **Phase 9 chưa đủ điều kiện đóng**: endpoint recommendation lỗi HTTP 500/timeout trong kiểm thử tích hợp sau V11; chưa kiểm tra được workout lifecycle hoặc UI đã xác thực. Cần lấy backend/MySQL log và sửa nguyên nhân trước khi chạy lại. Feedback loop cũng chưa lưu riêng event hiển thị, bỏ qua chủ động và lịch sử thay đổi lựa chọn bài. Không tuyên bố hiệu quả thực tế của model hoặc recommendation.

Chi tiết: [model_evaluation.md](./model_evaluation.md), [recommendation_evaluation.md](./recommendation_evaluation.md), [feedback_loop_audit.md](./feedback_loop_audit.md), [test_scenarios.csv](./test_scenarios.csv), [phase9_test_results.csv](./phase9_test_results.csv), [kết quả kiểm thử E2E sau V11](./phase9_e2e_results.csv), [phase9_summary.md](./phase9_summary.md), [báo cáo triển khai Phase 9A](../phase9a_recommendation_fix/phase9a_implementation_report.md), [kịch bản kiểm thử Phase 9A](../phase9a_recommendation_fix/phase9a_test_cases.csv), [kết quả kiểm thử Phase 9A](../phase9a_recommendation_fix/phase9a_test_results.csv).
