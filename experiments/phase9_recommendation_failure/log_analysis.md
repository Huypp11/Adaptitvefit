# Phân tích log và timeout

## Spring Boot

| Bằng chứng được yêu cầu | Kết quả điều tra |
|---|---|
| Exception đầu tiên / stack trace / `Caused by` | Không lấy được. Lần đầu caller thấy HTTP 500 nhưng output kiểm thử không lưu body/headers hoặc log backend. |
| SQL exception / SQLState | Không có bản ghi/log để đọc. |
| Exception từ AI client | Không có log correlate tới request. `AiServiceClient` chỉ ghi WARN khi bắt `RestClientException`; không có bằng chứng dòng đó xuất hiện. |
| Thời điểm bắt đầu/kết thúc request | Không được ghi. Chỉ biết lần đầu trả 500; lần chẩn đoán được caller timeout sau 30 giây. Không có correlation/request ID. |
| Log terminal hiện tại | Không có quyền đọc lại buffer terminal của tiến trình Spring Boot từ phiên điều tra. Không tìm thấy log file trong workspace. |

### Kết quả Postman mới nhất

- Thời điểm response: `2026-11-02T10:14:45.859007Z` = `2026-11-02 17:14:45.859+07:00`.
- HTTP `400 Bad Request`, path `/api/recommendations/today`.
- Message: `Hãy hoàn thiện hồ sơ trước để nhận gợi ý`.
- Đây là lỗi nghiệp vụ do `RecommendationService.today()` ném khi `profileRepository.findByUserId(user.getId())` không tìm thấy profile cho user đang đăng nhập. Nó xảy ra trước truy vấn check-in, risk/AI, rule/scoring và snapshot.
- Người dùng xác nhận lúc đó đăng nhập nhầm bằng admin; sau khi chuyển sang tài khoản user, endpoint trả HTTP 200. Do đó HTTP 400 được giải thích bởi danh tính admin không có profile, không phải bằng chứng profile user bị thiếu.
- HTTP 200 là kết quả thành công ở mức HTTP nhưng chưa có response body, thời gian phản hồi hoặc log backend để kiểm tra nội dung recommendation. Request mới này không tái hiện và cũng không giải thích lỗi 500/timeout cũ.

### Lỗi từ trình duyệt sau khi gửi check-in

- Người dùng quan sát console: `GET http://localhost:5173/api/recommendations/today 500 (Internal Server Error)` tại `endpoints.ts:66`.
- `endpoints.ts` gọi `GET /recommendations/today`; đường dẫn `/api` được thêm bởi cấu hình API client/base URL. Không có response body hoặc backend stack trace đi kèm thông báo này.
- Ảnh DevTools Network mới nhất cho thấy các request `GET /api/checkins?from=...&to=...` và `GET /api/workouts?page=0&size=50` đang hiển thị đều trả HTTP 200. Ảnh không hiển thị request `POST /api/checkins` hoặc hàng recommendation đang lỗi, nên riêng ảnh không chứng minh POST check-in đã lưu thành công và cũng không cung cấp response body của recommendation.
- Ảnh tiếp theo khi bấm mục “Gợi ý hôm nay” cho thấy lỗi console `GET .../api/recommendations/today 500` lặp lại và một dòng Network tên `today` còn ở trạng thái `(pending)` tại thời điểm chụp; các request exercise/profile hiển thị đều trả 200. Điều này xác nhận lỗi xảy ra cả khi dashboard tải gợi ý lẫn khi mở trang gợi ý, nhưng chưa xác định exception backend; dòng pending có thể là request mới chưa hoàn tất tại thời điểm chụp.
- Luồng giao diện xác nhận `CheckIn.tsx` điều hướng tới `/` ngay sau khi `POST /checkins` resolve; `Dashboard.tsx` tải profile, check-in list, workout history và recommendation trong một `Promise.all`. Khi recommendation trả 500, Promise bị reject và dashboard hiển thị thông báo lỗi tổng quát. Vì thế có thể thấy lỗi dashboard sau khi check-in đã lưu; cần xem status riêng của `POST /api/checkins` để xác nhận.
- Backend `GlobalExceptionHandler.handleOther()` trả HTTP 500 với message `Lỗi hệ thống` cho exception không xử lý được và log exception đầy đủ ở backend. Cần lấy stack trace cùng thời điểm browser request để định vị lỗi.
- Một lần Postman trả 200 không mâu thuẫn với browser 500 nếu khác user/token, thời điểm, dữ liệu hoặc backend/schema được gọi. Chưa có dữ liệu để chọn nguyên nhân nào.
- Người dùng báo các mục AI khác trên web cũng lỗi. Theo code, trang “Phân tích AI” gọi `/api/ai/today`, và endpoint này cùng `/api/recommendations/today` đi vào `RiskService.assess()` sau khi tạo user-state snapshot. Đây là nhánh chung đáng kiểm tra nếu cả hai thật sự trả 500; hiện chỉ có status 500 của recommendation được ghi nhận, chưa có Network status/body riêng của `/api/ai/today`.
- `GET /api/ai/risk-history` là luồng khác: đọc các `risk_predictions` đã lưu, không gọi AI inference. Một lỗi ở đây không nhất thiết cùng nguyên nhân với recommendation/today risk.
- `frontend/src/api/client.ts` chỉ thiết lập Axios `baseURL='/api'`, gắn bearer token từ localStorage và chuyển 401 về login; Vite proxy chuyển `/api` sang backend port 8080. HTTP 500 là phản hồi server/backend chứ không phải dấu hiệu trực tiếp cho thấy interceptor này hỏng. Chưa có bằng chứng cho lỗi ở frontend client/proxy.

### Spring Boot timing/stack trace người dùng gửi

- Request `1670ed05-e7c7-437f-8573-764aa2cc10de`, endpoint `GET /api/recommendations/today`, user reference `8758e38f75edcf66`: `user_state.snapshot=14 ms`; AI HTTP call thành công trong `22 ms`; model-version persistence `5 ms`.
- `risk.prediction_persist` bắt đầu lúc `09:22:11.544+07:00`, kéo dài `50,410 ms` và kết thúc lỗi `PessimisticLockingFailureException`.
- Hibernate log: SQL error `1205`, SQLState `40001`, `Lock wait timeout exceeded`; failed SQL là `INSERT INTO risk_predictions ...`.
- Request `1670...` kết thúc HTTP 500 sau `81,738 ms`. Một request khác cùng endpoint/user reference (`bbe8aa7b-1661-43d8-91d0-37b69841f533`) kết thúc HTTP 500 sau `51,256 ms` lúc `09:22:11.498+07:00`, cho thấy hai GET cùng user chạy chồng lấp. Log trích không có stage span gắn correlation ID cho request `bbe...`.
- **Nguyên nhân trực tiếp đã xác nhận:** MySQL lock wait timeout trong lúc lưu risk prediction gây backend 500; không phải chậm AI trong request `1670...`.
- **Lock-holder cụ thể chưa xác nhận độc lập:** code đã xem cho thấy outer `RecommendationService.today()` giữ pessimistic lock trên check-in; `RiskPredictionStore.save()` mở `REQUIRES_NEW` rồi insert risk row có FK về check-in. Outer transaction bị suspend nhưng vẫn có thể giữ lock, khiến child insert chờ FK parent lock của chính outer transaction. Cơ chế này phù hợp rất sát với SQL, stage 50.410s và hai request nối tiếp; cần InnoDB lock wait-for diagnostics để chứng minh session/transaction giữ lock.

Một thread dump hậu sự cố được chụp lúc `2026-11-02 16:41:28` cho thấy các worker `http-nio-8080-exec-*` đang chờ task mới trong Tomcat queue tại **thời điểm chụp**. Đây không phải stack trace của request lỗi và không xác định request trước đó bị kẹt ở đâu.

## MySQL

MySQL service đang chạy từ `C:\ProgramData\MySQL\MySQL Server 26.7\Data`; biến MySQL xác nhận:

- `log_error = .\DESKTOP-DUAB9QJ.err`
- `slow_query_log = ON`
- `slow_query_log_file = DESKTOP-DUAB9QJ-slow.log`
- `log_output = FILE`
- `long_query_time = 10` giây
- `innodb_lock_wait_timeout = 50` giây
- `Slow_queries = 11`, `Connections = 121`, `Max_used_connections = 13`, `Threads_connected = 13`, `Threads_running = 2` tại lúc truy vấn chẩn đoán.
- Đếm query đang hoạt động trên `adaptivefit` trả 0 tại lúc truy vấn; đây không phải thời điểm request lỗi.

Không thể đọc nội dung error/slow log qua filesystem hiện tại: PowerShell nhận `UnauthorizedAccessException` ở cả hai file. MySQL user không có `PROCESS` privilege để đọc trạng thái InnoDB transaction; query `performance_schema.events_statements_summary_by_digest` và `performance_schema.error_log` cũng bị từ chối/không tồn tại. Do đó không thể quy `Slow_queries=11` cho recommendation request.

## Timeout interpretation

- Caller timeout 30 giây là quan sát ở công cụ kiểm thử; không phải timeout cấu hình của endpoint Spring. Log mới thể hiện một request browser trả sau 81.738 giây.
- AI connect/read timeout được cấu hình 3000 ms trong [AiServiceClient.java](../../backend/src/main/java/com/adaptivefit/client/AiServiceClient.java). `RestClientException` được catch và biến thành `Optional.empty()` để `RiskService` dùng history fallback.
- MySQL lock wait limit quan sát trước đó là 50 giây; log mới ghi nhận wait 50.410 giây khi insert risk prediction, phù hợp với lock wait timeout.
- Hai request mới có cùng endpoint và pseudonymous user reference, thời gian kết thúc cho thấy chúng chạy chồng lấp. Request `1670...` có một đoạn 50.410 giây rõ ràng ở insert; thời gian còn lại chưa được phân bổ đầy đủ trong đoạn log.

## Điều cần lấy tiếp theo

1. Lock timeout đã xác nhận; nếu cần xác định transaction giữ khóa thì thu InnoDB lock wait-for diagnostics với quyền phù hợp, không cần gọi lại endpoint chỉ để xác nhận timeout.
2. Nếu trang “Phân tích AI” cũng lỗi, lấy riêng status/response body của `GET /api/ai/today` và timestamp. Hai endpoint này dùng chung `RiskService.assess()`; kiểm tra `/api/ai/risk-history` độc lập nếu cần.
3. So sánh user đang đăng nhập và backend/base URL của Postman 200 với browser 500, nhưng không chia sẻ token.
4. Chỉ sau khi được phép sửa, giải quyết transaction/lock interaction rồi mới chạy test hồi quy trên disposable schema; không gửi lại POST check-in.
