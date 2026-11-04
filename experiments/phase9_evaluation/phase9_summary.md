# Phase 9 - Tóm tắt và trạng thái

## Đã kiểm tra

- Registry cho thấy v2.0 Logistic Regression đang active; manifest cho thấy training dataset là synthetic `demo_v1`, 22.400 rows/400 mô phỏng users, với survey profile nền `n=53`.
- Đối chiếu 10 feature, StandardScaler/Logistic Regression, Platt calibration, F2 threshold, user-level split, metric, SHAP, FastAPI validation và Spring fallback.
- Audit recommendation rules/scoring, UI explanations, workout attribution, feedback schema/service; chạy 9 kịch bản code-level.
- FastAPI TestClient chạy active v2.0: health/prediction 200, thiếu key 401, ngoài miền 422.
- Phase 9A: thêm `maxExerciseIntensity` vào profile/API/UI, location hard filter, rule `CHALLENGE` xét stress/sleep/mood/motivation, ba bài GYM qua V11; không thay model dropout hoặc registry.
- Kiểm thử lại ngày 2026-11-02: Python tests **21 passed**; Maven backend tests **24 passed** (15 engine, 1 scenario suite, 2 profile contract, 6 feedback); frontend `tsc --noEmit` và Vite build thành công (921 modules; cảnh báo bundle khoảng 680 kB).
- Phase 9A scenario suite xác minh 21 ca đầu vào nội bộ, CSV có 21/21 `PASS`; các ca này không phải dữ liệu người dùng thực.
- Người dùng xác nhận migration V11 đã được nhận; profile API round-trip giá trị LOW thành công.
- E2E trên schema disposable: đăng ký hai user test, validation profile, lưu profile có field V11, validation check-in và tạo check-in đạt; AI/frontend health đạt. Tuy nhiên recommendation API trả HTTP 500 rồi timeout 30 giây trong lần chẩn đoán; không retry tiếp. Workout start/complete và UI sau đăng nhập chưa chạy. Hai user cùng dữ liệu profile/check-in còn trong schema test; không có thông tin đăng nhập trong báo cáo. Xem [phase9_e2e_results.csv](./phase9_e2e_results.csv).

## Phát hiện quan trọng

1. Metrics dropout chỉ phản ánh dữ liệu mô phỏng. Test split giữ riêng 80 users mô phỏng; không phải validation trên người dùng AdaptiveFit.
2. Trên test synthetic: ROC-AUC 0,886; PR-AUC 0,742; precision 0,482; recall 0,948; F1 0,639; Brier 0,119; threshold 0,14; FP 1.198/FN 61. Baseline missed-days có precision và accuracy cao hơn, recall thấp hơn.
3. Các gap recommendation ban đầu về stress/CHALLENGE, location, cường độ tối đa và GYM đã được xử lý trong Phase 9A, có kiểm thử unit/scenario. Kiểm tra API sau V11 xác nhận profile field; endpoint recommendation vẫn lỗi HTTP 500/timeout.
4. Feedback loop lưu generated snapshot/workout lifecycle/feedback, nhưng không lưu impression/display, explicit skip, hoặc selection-change history thành event riêng.
5. Luồng full-stack E2E chưa hoàn tất: recommendation lỗi nên workout/UI sau đăng nhập chưa kiểm thử. Database disposable được người dùng xác nhận; hai tài khoản test, profile và check-in đã được tạo trong đó. Không thử lại endpoint trước khi xem log.

## Quyết định

**Audit, Phase 9A changes và unit/build tests đã hoàn tất; Phase 9 chưa đủ điều kiện đóng.** Dù V11 được xác nhận và profile round-trip thành công, luồng recommendation lỗi HTTP 500/timeout khi test tích hợp; workout lifecycle chưa chạy. Cần chẩn đoán bằng log backend/MySQL, sửa lỗi, rồi chạy lại luồng trên disposable schema. Không retrain model, không đổi registry. Không coi constructed scenario inputs là người dùng thật.

## Điều kiện tiếp theo

- Kiểm tra log backend/MySQL cho lỗi `GET /api/recommendations/today`, sửa nguyên nhân và chạy lại recommendation, workout start/complete, cùng UI đã đăng nhập trên disposable schema.
- Bổ sung event capture additive với idempotency nếu Phase 10 cần phân tích recommendation exposure/skip/change; hiện trạng và thiết kế được mô tả trong `feedback_loop_audit.md`.
- Thu thập dữ liệu outcome có consent trước khi đánh giá model/recommendation ngoài mô phỏng; không tạo dropout labels từ survey cắt ngang.

## File đầu ra

- [phase9_report.md](./phase9_report.md)
- [model_evaluation.md](./model_evaluation.md)
- [recommendation_evaluation.md](./recommendation_evaluation.md)
- [feedback_loop_audit.md](./feedback_loop_audit.md)
- [test_scenarios.csv](./test_scenarios.csv)
- [phase9_test_results.csv](./phase9_test_results.csv)
- [Phase 9 recommendation scenario test source](../../backend/src/test/java/com/adaptivefit/engine/Phase9RecommendationScenarioTest.java)
- [Phase 9A implementation report](../phase9a_recommendation_fix/phase9a_implementation_report.md)
- [Phase 9A migration report](../phase9a_recommendation_fix/phase9a_migration_report.md)
- [Phase 9A scenario results](../phase9a_recommendation_fix/phase9a_test_results.csv)
- [Post-V11 E2E results](./phase9_e2e_results.csv)
