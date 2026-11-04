# Điều tra lỗi tích hợp Recommendation API — Phase 9

**Ngày điều tra:** 2026-11-02  
**Phạm vi:** Điều tra chỉ đọc lỗi `GET /api/recommendations/today` sau migration V11.  
**Giới hạn bắt buộc:** Không sửa code, không gọi lại endpoint lỗi, không chạy migration/reset/xóa dữ liệu, không thay model/rule/scoring. Không ghi secret, mật khẩu, JWT hoặc thông tin đăng nhập vào báo cáo.

## Kết luận

**HTTP 400 do dùng admin đã được giải thích. Log runtime mới xác nhận nguyên nhân trực tiếp của browser HTTP 500:** insert `risk_predictions` bị MySQL lock wait timeout (error 1205, SQLState 40001), sau đó Spring trả `PessimisticLockingFailureException`. Stage insert chờ 50.410 giây; request kết thúc 500 sau 81.738 giây. AI client trước đó thành công trong 22 ms.

Sau đó, người dùng xác nhận trình duyệt khi hoàn tất check-in gọi `GET http://localhost:5173/api/recommendations/today` và nhận HTTP 500. Đây là bằng chứng request recommendation qua frontend proxy vẫn lỗi trong browser, dù một lần gọi Postman trả 200. Chưa có response body, thời gian phản hồi đầy đủ hoặc log backend cho request browser để xác định vì sao khác biệt.

Trong frontend, POST check-in thành công sẽ điều hướng về dashboard; dashboard tải recommendation cùng các API khác bằng `Promise.all`. Vì vậy lỗi GET recommendation có thể làm dashboard hiện thông báo lỗi sau khi check-in đã lưu. Cần xem riêng status POST `/api/checkins` trong Network để xác nhận dữ liệu check-in đã được lưu hay chưa.

Người dùng tiếp tục báo các phần AI khác trên web cũng lỗi. Code cho thấy `/api/recommendations/today` và `/api/ai/today` cùng gọi `RiskService.assess()`; vì vậy nếu cả hai endpoint trả 500, chúng có chung một nhánh điều tra ở risk/state/persistence. Chưa có Network evidence của `/api/ai/today` nên chưa xác nhận lỗi đó. `GET /api/ai/risk-history` chỉ đọc prediction đã lưu, không thực hiện inference.

### Những gì xác nhận được

- Database `adaptivefit` có Flyway V10 và V11 với `success = 1`.
- Hai user kiểm thử có ID 52 và 53; mỗi user có một profile và một check-in. Cả hai profile là `GYM`, intensity cap `LOW`.
- Sau hai lần lỗi, mỗi user có **0** `risk_predictions`, **0** `recommendation_histories`, **0** workout sessions và **0** feedback.
- V11 đã tạo được các hàng bài GYM: ba hàng mới có ID 161–163 cùng một hàng GYM đã có trước đó (ID 139). Cả ba hàng mới có tag; không thấy thiếu dữ liệu GYM trong database đang dùng.
- AI client được cấu hình mặc định gọi `http://localhost:8000/predict`, timeout connect/read 3000 ms, gửi `X-Internal-Key`, và bắt `RestClientException` để trả về fallback thay vì phát sinh lỗi API trực tiếp.
- Bộ dữ liệu check-in thỏa miền 10 feature theo schema AI nếu map đúng như `UserStateService`; payload thực tế gửi đi không được ghi log nên chưa thể xác nhận request/response AI đã thành công.
- Sau lỗi, MySQL trả 0 active query cho schema kiểm tra tại thời điểm truy vấn; đây là ảnh chụp sau sự kiện, không chứng minh trạng thái lúc 500/timeout.
- Request Postman bằng admin trả HTTP 400 do không có profile cho danh tính admin; sau đó người dùng đổi sang tài khoản user và báo HTTP 200. Không nhận hoặc lưu token.

## Những gì chưa có bằng chứng

- Exception đầu tiên, stack trace, `Caused by`, SQLState/MySQL error và response body của lần HTTP 500/timeout trước.
- ID user được xác thực trong request Postman mới nhất và liệu token đó có phải của user 52/53 không. Không chia sẻ token.
- Thời điểm bắt đầu/kết thúc chính xác của hai HTTP requests. Thời gian `created_at` của user trong DB là `2026-11-02 09:38:17` và `09:40:13`; timezone và quan hệ chính xác với thời điểm request không được xác lập.
- AI request/response/status thực tế do Spring gửi/nhận. Health endpoint độc lập không xác nhận giao tiếp inference.
- Slow query, lock wait hoặc deadlock gắn với hai tài khoản/request.
- Nội dung error log và slow-query log của MySQL: Windows ACL từ chối đọc file. DB account không có quyền `PROCESS`/đọc các performance schema cần thiết.
- Có hay không response mapping/Jackson error hoặc lỗi lưu `risk_predictions`/snapshot ở thời điểm request.

## Kết luận vận hành

Request bằng admin dừng ở profile lookup (HTTP 400); một lần Postman khác với user trả HTTP 200; browser lại có 500 do DB lock wait timeout trên risk prediction persistence. Các request khác thời điểm có thể khác cache/concurrency state, nên không mâu thuẫn. Code có cơ chế lock interaction đáng nghi: outer recommendation transaction giữ pessimistic check-in lock trong khi inner `REQUIRES_NEW` insert risk row tham chiếu check-in đó. Đây là lời giải thích mạnh nhất cho lock wait, nhưng lock-holder cụ thể chưa được xác minh bằng InnoDB wait-for graph. Chưa sửa code; Phase 9 chưa thể đóng cho tới khi user phê duyệt sửa và kiểm thử hồi quy.

Chi tiết:

- [request_flow_trace.md](./request_flow_trace.md)
- [log_analysis.md](./log_analysis.md)
- [database_diagnostics.md](./database_diagnostics.md)
- [root_cause_and_fix_plan.md](./root_cause_and_fix_plan.md)
- Kết quả kiểm thử trước đó: [phase9_e2e_results.csv](../phase9_evaluation/phase9_e2e_results.csv)
