# AdaptiveFit AI — Tóm tắt bàn giao cho AI mới

## 1. Mục tiêu sản phẩm

AdaptiveFit AI là ứng dụng web hỗ trợ người dùng theo dõi check-in sức khỏe/tập luyện hằng ngày, xem đánh giá nguy cơ, nhận gợi ý bài tập, ghi nhận buổi tập và gửi phản hồi về recommendation. Dự án hiện dùng dữ liệu demo để phát triển; không xem dữ liệu hiện có là dữ liệu nghiên cứu thực tế.

## 2. Cấu trúc và công nghệ

- `frontend/`: React + TypeScript + Vite; giao diện người dùng, gọi API backend.
- `backend/`: Java + Spring Boot + Maven; REST API, JWT/security, MySQL persistence, recommendation/workout/feedback orchestration.
- `ai-service/`: Python + FastAPI; nhận feature qua `/predict`, trả risk prediction và giải thích.
- `backend/src/main/resources/db/migration/`: Flyway migrations.
- `experiments/`: audit, đánh giá và ghi chú các giai đoạn.
- `README.md`: hướng dẫn khởi chạy tổng quát và mô tả một số API/behavior.

Hệ thống có ba tiến trình chính: MySQL, Spring Boot backend, AI service; frontend chạy riêng trong quá trình phát triển. Backend gọi AI service qua HTTP. Recommendation và fallback theo lịch sử được điều phối phía backend.

## 3. Luồng nghiệp vụ chính

1. Người dùng đăng nhập và lưu hồ sơ (vị trí tập, thiết bị, cường độ tối đa).
2. Người dùng gửi check-in trong ngày.
3. Frontend điều hướng tới Dashboard; Dashboard tải dữ liệu và `GET /api/recommendations/today`.
4. Khi chưa có recommendation snapshot cho check-in:
   - Backend tính trạng thái/feature người dùng.
   - Risk service đọc prediction hiện có hoặc gọi AI service; nếu AI không khả dụng, dùng fallback theo lịch sử.
   - Intervention engine chọn strategy/rule.
   - Scorer lọc và xếp hạng bài tập theo ngữ cảnh người dùng, vị trí và giới hạn cường độ.
   - Backend lưu recommendation snapshot để các lần GET sau dùng lại.
5. Người dùng có thể bắt đầu workout từ recommendation; workout được liên kết với recommendation.
6. Người dùng có thể gửi feedback. Feedback được lưu để tổng hợp; Phase 8 không tự huấn luyện lại model.

Snapshot tồn tại được tái sử dụng cho cùng check-in; vì vậy đổi profile sau khi snapshot đã tạo có thể không làm recommendation hôm đó tự sinh lại.

## 4. Các giai đoạn/chức năng đã có trong code

- AI risk prediction tích hợp với backend, giải thích SHAP và history fallback.
- Recommendation engine dựa trên trạng thái người dùng, strategy/rules, catalog bài tập và scoring.
- Phase 8: recommendation history/snapshot, liên kết workout với recommendation, feedback cho recommendation và thống kê tổng hợp admin.
- Phase 9A: profile có `maxExerciseIntensity` (`LOW`/`MEDIUM`/`HIGH`, mặc định `MEDIUM`); strategy/scorer áp dụng giới hạn cường độ và vị trí. Migration `V11__recommendation_constraints.sql` thêm schema/seed/rule.
- Phase 9 performance instrumentation: log timing/correlation ID opt-in cho backend và AI service; tắt mặc định, bật bằng `PERFORMANCE_TIMING_ENABLED=true` trên process tương ứng.

## 5. Sự cố Recommendation gần nhất và bản sửa

Log người dùng cung cấp trước đó ghi nhận `GET /api/recommendations/today` trả 500. Request mất khoảng 81.7 giây; AI call thành công trong 22 ms, user-state snapshot khoảng 14 ms, nhưng insert vào `risk_predictions` chờ khoảng 50.4 giây rồi lỗi MySQL 1205 / SQLState 40001 (`Lock wait timeout exceeded`). Có hai request recommendation cùng user chạy chồng lấp.

Nguyên nhân trực tiếp trong log là lock timeout ở thao tác lưu risk prediction; AI inference không phải bước chậm trong request đó. Cơ chế code được nghi ngờ mạnh: transaction recommendation giữ `PESSIMISTIC_WRITE` trên check-in, trong khi risk prediction trước đây được lưu qua transaction `REQUIRES_NEW` và có khóa ngoại về check-in đang bị khóa. Không có InnoDB lock wait-for report, nên transaction giữ lock cụ thể chưa được xác minh độc lập.

Bản sửa hiện có:

- Recommendation cache-miss gọi `RiskService.assessForRecommendation()`.
- Risk prediction được lưu bằng `RiskPredictionStore.saveInCurrentTransaction()` trong transaction recommendation đang giữ khóa check-in.
- Các caller khác của `RiskService.assess()` vẫn dùng thao tác lưu `REQUIRES_NEW`.
- Prediction và recommendation snapshot trên đường này hiện commit/rollback cùng transaction.
- Không đổi model, AI request/response, rules, scoring, snapshot format; không tăng timeout, thêm retry hay migration cho bản sửa lock này.

## 6. Kiểm tra đã chạy và điều chưa xác nhận

- `mvn -q -Dtest=RiskServiceTest test`: pass.
- `mvn -q test` từ `backend/`: pass, 26 tests, 0 failures/errors/skips ở lần chạy được ghi nhận gần nhất.
- Chưa xác nhận runtime fix trên MySQL thật; trong lần làm việc gần nhất không chạy backend, không gọi endpoint và không truy cập DB. Không khẳng định ứng dụng hiện đang kết nối MySQL.
- Không gửi lại POST check-in để benchmark. GET recommendation có thể tạo risk prediction/snapshot nếu đang cache miss, nên chỉ chạy khi có chủ đích trong môi trường phù hợp.
- Migration V11 chưa được xác nhận đã chạy. Trước khi chạy migration phải xác nhận đúng database môi trường test/dev, có backup theo quy trình và được người dùng cho phép. Không tự reset/xóa dữ liệu.

Tài liệu chi tiết:

- `experiments/phase9_first_checkin_performance/root_cause.md`
- `experiments/phase9_first_checkin_performance/backend_timing.md`
- `experiments/phase9_first_checkin_performance/timing_instrumentation.md`
- `experiments/phase9_recommendation_failure/root_cause_and_fix_plan.md`
- `experiments/phase9a_recommendation_fix/` (nếu có trong workspace)

## 7. Quy tắc quan trọng khi tiếp tục

- **Trước mọi chỉnh sửa, kiểm tra trạng thái thay đổi hiện có.** Worktree đang có nhiều file modified/untracked từ các giai đoạn trước; không reset, checkout, xóa hoặc ghi đè các thay đổi không thuộc yêu cầu.
- Không commit trừ khi được yêu cầu. Hiện chưa xác nhận các thay đổi đã commit.
- Không mở hoặc chia sẻ `.env`; không đưa password, JWT, API/internal keys hay dữ liệu cá nhân vào prompt/log.
- Không chạy migration, reset database, cleanup dữ liệu hoặc POST tạo check-in nếu chưa được cho phép rõ ràng.
- Giữ nguyên semantics snapshot, model và feedback trừ khi yêu cầu cụ thể nói khác.
- Khi kiểm tra lỗi, phân biệt code/test evidence với runtime evidence. Test unit pass không chứng minh đã kết nối MySQL thật hoặc lỗi đã hết trên runtime.
- Nếu cần kiểm thử runtime, ưu tiên DB disposable/test có xác nhận; dùng check-in đã có nếu phù hợp và cảnh báo trước nếu GET có thể ghi snapshot.

## 8. Hướng dẫn khởi chạy tham khảo

Kiểm tra `README.md`, `backend/README.md` (nếu có), `ai-service/README.md` và file cấu hình hiện tại trước khi chạy vì biến môi trường/phụ thuộc có thể khác theo máy.

Thông thường cần:

1. Khởi động MySQL và xác nhận database/schema đích.
2. Khởi động backend từ thư mục `backend/` để ứng dụng đọc cấu hình môi trường theo README.
3. Khởi động AI service từ `ai-service/` bằng môi trường Python của dự án.
4. Khởi động frontend từ `frontend/` bằng script npm phù hợp trong `package.json`.

Không chép secrets hoặc nội dung `.env` vào bản tóm tắt này.

---

**Lời nhắc cho AI tiếp nhận:** Trước tiên đọc trạng thái code hiện tại và các tài liệu Phase 9 nêu trên. Không giả định rằng MySQL đang kết nối, migration đã chạy, hoặc bản sửa lock đã được xác nhận trên runtime. Hỏi người dùng trước khi thực hiện thao tác có thể ghi dữ liệu.
