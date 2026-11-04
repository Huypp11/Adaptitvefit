# Phase 9.4 - Audit Feedback Loop

## Bằng chứng trong code/schema hiện tại

`V10__recommendation_feedback_loop.sql` thêm bảng recommendation history và foreign-key links từ workout/feedback. `RecommendationService.today()` tạo snapshot duy nhất theo check-in, với unique constraint `recommendation_histories.checkin_id`. `WorkoutService` liên kết recommendation khi bắt đầu, chặn session STARTED trùng và kiểm tra selection/thời lượng thuộc snapshot. Workout có status STARTED/COMPLETED/ABANDONED, `started_at`, `finished_at`. Feedback có timestamp, unique `session_id`/`recommendation_id`, trạng thái và thông tin đánh giá; backend test xác nhận duplicate create bị chặn và update feedback hiện có hoạt động.

## Sự kiện được lưu / chưa được lưu riêng

| Event | Bằng chứng hiện tại | Đánh giá |
|---|---|---|
| Recommendation generated | Snapshot history được lưu khi gọi `GET /today`; unique theo check-in | Có, nhưng GET là lúc tạo; không phải event log độc lập |
| Recommendation displayed | Frontend ghi rõ mở trang không được theo dõi | Chưa lưu |
| Người dùng chấp nhận | Bắt đầu workout từ recommendation và link session | Suy ra được từ workout start; không có accepted event riêng |
| Thay đổi lựa chọn bài | Session lưu exercise IDs/minutes đã chọn; backend kiểm tra với snapshot | Giá trị cuối có thể xem, nhưng không có event/old-vs-new để biết người dùng đã đổi |
| Bỏ qua recommendation | Feedback `NOT_DONE`/`NOT_SUITABLE` do người dùng gửi tùy chọn | Không ghi nhận tự động khi skip/không tương tác |
| Workout started | Workout session STARTED + `started_at` | Có |
| Completed | Status COMPLETED + `finished_at` | Có |
| Abandoned | Status ABANDONED + `finished_at` | Có |
| Feedback gửi | Feedback theo session/recommendation, thời gian và nội dung | Có; cập nhật ghi đè cùng row, không giữ lịch sử từng lần sửa |

`RecommendationFeedbackStatus` đang biểu diễn outcome/feedback tổng hợp, không thay thế event stream. `GET /today` có thể trả snapshot cũ; không cho biết lần nào UI thực sự hiển thị.

## Rủi ro phân tích

- Không có impression denominator nên không thể tính chính xác tỷ lệ recommendation được nhìn thấy rồi bỏ qua.
- `NOT_DONE` là tự báo cáo tùy chọn, khác với không có hành động; không nên coi absence là skip.
- Không có event riêng cho selection change; không phân biệt recommendation được chấp nhận nguyên trạng hay chọn một phần.
- Feedback update overwrite trạng thái cũ; chỉ có `created_at`/`updated_at`, không có audit trail thay đổi.
- Các bảng có timestamp và khóa liên kết, nhưng phải dùng transaction/idempotency cẩn thận khi thêm event mới để tránh đếm hai lần completion/feedback.

## Thiết kế bổ sung đề xuất (chưa triển khai)

Giữ nguyên bảng hiện có và cân nhắc một bảng append-only `recommendation_events`:

- `id`, `user_id`, `recommendation_id` nullable, `workout_session_id` nullable.
- `event_type` với whitelist: `DISPLAYED`, `ACCEPTED`, `SELECTION_CHANGED`, `SKIPPED`, `WORKOUT_STARTED`, `WORKOUT_COMPLETED`, `WORKOUT_ABANDONED`, `FEEDBACK_SUBMITTED`.
- `occurred_at` UTC; `idempotency_key` duy nhất theo user để chặn retry trùng; `payload_json` nhỏ, versioned.
- Foreign keys phù hợp và index theo `(user_id, occurred_at)` / `(recommendation_id, occurred_at)`.
- Ghi workout transition + event trong cùng transaction hoặc dùng outbox; event không thay thế status canonical trong `workout_sessions`.
- Ghi `DISPLAYED` sau khi UI thực sự render/hiển thị recommendation; không suy ra từ GET. Chỉ ghi `SKIPPED` khi user chủ động bỏ qua, không dùng timeout/absence làm label.
- Lưu selected exercise IDs và so với snapshot để ghi `SELECTION_CHANGED`; hạn chế payload, không lưu dữ liệu nhạy cảm không cần thiết.

Chưa tạo migration hay sửa backend/API trong đợt audit này. Cần thống nhất semantics, retention/privacy và migration rollout trước khi triển khai.
