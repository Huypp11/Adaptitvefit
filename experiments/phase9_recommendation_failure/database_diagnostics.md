# Chẩn đoán database — chỉ đọc

## Phương thức

Các truy vấn chạy qua MySQL CLI trong transaction `READ ONLY`, sau đó `ROLLBACK`. Chỉ chọn tài khoản test theo prefix đã dùng cho E2E (`phase9e2e-` và `phase9diag-`); không truy vấn/ghi/xóa người dùng khác. Không chạy migration hoặc DML.

## Trạng thái schema và migration

| Kiểm tra | Kết quả |
|---|---|
| `DATABASE()` | `adaptivefit` |
| Flyway V10 | `recommendation feedback loop`, success = 1 |
| Flyway V11 | `recommendation constraints`, success = 1 |

`application.yml` không có `DB_URL` override trong `.env` tại lúc điều tra, nên backend dùng URL mặc định đến schema `adaptivefit`. Điều này khớp với schema được truy vấn. Người dùng đã xác nhận đây là database disposable.

## Hai user kiểm thử

Chỉ ghi ID, không ghi email/password/JWT:

| User ID | Profile | Check-in | Risk prediction | Recommendation snapshot | Workout | Feedback |
|---:|---:|---:|---:|---:|---:|---:|
| 52 | 1 | 1 | 0 | 0 | 0 | 0 |
| 53 | 1 | 1 | 0 | 0 | 0 | 0 |

Profile của cả hai: `location=GYM`, `max_exercise_intensity=LOW`, equipment rỗng.

Check-in của cả hai: ngày 2026-11-02, sleep 7, stress 2, energy 5, mood 4, free time 30, motivation 4, exercised_today false; duration/difficulty null. Không có workout sessions hoặc feedback liên quan.

Không có hàng recommendation hoặc prediction được lưu cho hai test user sau lỗi. Điều này cho biết chưa có snapshot/prediction tồn tại khi chụp DB; **không đủ để xác định** request thất bại trước inference, ở inference fallback, khi lưu prediction hay ở bước tiếp theo. (Prediction được lưu ở transaction riêng nếu AI response và save thành công, nên số hàng 0 loại trừ một prediction đã commit thành công cho các check-in này.)

## Rule, catalog và quan hệ

- Rule `CHALLENGE` tồn tại với điều kiện V11:
  `risk_rank==LOW&energy_level>=4&workout_frequency_7d>=3&stress_level<4&sleep_hours>=6&mood>=3&motivation>=3`
  và params `minIntensity=MEDIUM;difficultyShift=0.7`.
- Có 4 exercise rows `location=GYM`, active: một hàng cũ ID 139 và ba hàng do migration V11 thêm (ID 161–163). Ba hàng V11 có tags `low_energy`, `bodyweight`, `step_up` tương ứng.
- Chuỗi tiếng Việt từ CLI bị mojibake do encoding đầu ra; không dùng nội dung tên bị lỗi hiển thị làm bằng chứng dữ liệu tên sai. ID/location/intensity và presence/tag là các trường truy vấn được.
- Đã xác nhận các foreign key liên quan: `user_profiles -> users`, `daily_checkins -> users`, `risk_predictions -> daily_checkins/model_versions/interventions`, `recommendation_histories -> users/daily_checkins/risk_predictions`, `workout_sessions -> users/risk_predictions/recommendation_histories`.
- Không phát hiện quan hệ FK thiếu trong các bảng đã kiểm tra. Không kiểm tra toàn bộ mọi bảng trong schema.

## Query/lock/connection diagnostics

- MySQL slow log được bật; ngưỡng 10 giây và output là FILE. `Slow_queries=11` là bộ đếm toàn server, không có attribution tới request.
- `Threads_connected=13`, `Max_used_connections=13`, `Threads_running=2` tại thời điểm diagnostic; chưa thấy bằng chứng pool exhaustion.
- Đếm active query cho schema `adaptivefit` trả 0 tại thời điểm truy vấn.
- Không đọc được `innodb_trx`/process-level diagnostics vì thiếu quyền `PROCESS`. Không thể xác nhận/loại trừ lock wait hoặc deadlock trong thời điểm request.
- Error log và slow-query file bị Windows ACL từ chối đọc. MySQL `performance_schema` digest table bị từ chối; performance_schema error log table không có.

## Kết luận database

V11 hiện diện và các hàng profile V11 truy cập được; catalog GYM cũng có dữ liệu. Không có snapshot/prediction/workout được lưu cho hai user. Chưa có SQL error, lock wait, slow query hay connection-pool exhaustion gắn với request; nguyên nhân database/transaction chưa xác nhận.
