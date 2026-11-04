# Nguyên nhân gốc và kế hoạch xử lý

## 1. Nguyên nhân gốc đã xác nhận?

**Đã xác nhận nguyên nhân trực tiếp của lần 500 được ghi trong log mới:** MySQL lock wait timeout khi insert vào `risk_predictions`, chuyển thành `PessimisticLockingFailureException` và HTTP 500. Thời gian chờ ở stage này là 50.410 giây. AI response trước đó thành công và chỉ mất 22 ms.

**Cơ chế nhiều khả năng gây lock:** transaction của `RecommendationService.today()` giữ `PESSIMISTIC_WRITE` trên check-in; `RiskPredictionStore.save()` mở `REQUIRES_NEW` và insert row con có FK về chính check-in. Transaction ngoài bị suspend nhưng vẫn giữ lock, có thể làm insert transaction bên trong chờ lock FK của parent. Pasted log không gồm InnoDB lock owner/wait-for graph, nên blocker cụ thể vẫn chưa được xác minh độc lập. Có hai GET cùng endpoint và pseudonymous user chạy chồng lấp; một request khác kết thúc 500 sau 51.256 giây.

## 2. Bằng chứng cụ thể

### Triệu chứng tái hiện trong lần E2E trước (không lặp lại ở điều tra này)

- Lần đầu `GET /api/recommendations/today` trả HTTP 500.
- Lần chẩn đoán thứ hai timeout sau 30 giây ở caller.
- Tài khoản, profile, check-in tạo thành công; profile round-trip `LOW` thành công.
- Không có response body, Spring stack trace, SQLState, request timestamp hoặc AI response trong output kiểm thử.
- Log runtime mới (được người dùng cung cấp, không phải request chạy trong lần audit này) cho thấy lỗi insert risk prediction như kết luận ở trên.
- Điều tra hiện tại **không gọi lại endpoint lỗi**.

### Trạng thái DB đọc được

- Flyway V11 success = 1; `max_exercise_intensity` đã được lưu/đọc qua profile API.
- Hai user ID 52/53 có profile GYM + LOW, mỗi người một check-in.
- Không có risk prediction/snapshot/workout/feedback cho hai user.
- Có bài GYM và tags sau V11; không phải thiếu tất cả bài GYM.
- MySQL không có query active trên schema tại lúc kiểm tra sau sự kiện; slow query count toàn server 11 không có request attribution.

### Code xác định các đường lỗi, nhưng chưa xác định đường đã xảy ra

- Controller delegate đơn giản: [RecommendationController.java](../../backend/src/main/java/com/adaptivefit/controller/RecommendationController.java#L19).
- Orchestration/transaction: [RecommendationService.java](../../backend/src/main/java/com/adaptivefit/service/RecommendationService.java#L94).
- Hai pessimistic locks: [DailyCheckinRepository.java](../../backend/src/main/java/com/adaptivefit/repository/DailyCheckinRepository.java#L17), [RecommendationHistoryRepository.java](../../backend/src/main/java/com/adaptivefit/repository/RecommendationHistoryRepository.java#L16).
- Ba history reads và 10 feature fields: [UserStateService.java](../../backend/src/main/java/com/adaptivefit/service/UserStateService.java#L49).
- AI/fallback và ghi prediction: [RiskService.java](../../backend/src/main/java/com/adaptivefit/service/RiskService.java#L51), [RiskPredictionStore.java](../../backend/src/main/java/com/adaptivefit/service/RiskPredictionStore.java#L26).
- AI connect/read timeout 3s, POST `/predict`, catches `RestClientException`: [AiServiceClient.java](../../backend/src/main/java/com/adaptivefit/client/AiServiceClient.java#L43).
- Rule, exercise load, score, snapshot save: [RecommendationService.java](../../backend/src/main/java/com/adaptivefit/service/RecommendationService.java#L108).

### Timing/log evidence mới

- Request `1670ed05-e7c7-437f-8573-764aa2cc10de`: user-state 14 ms; AI HTTP 22 ms thành công; model-version persistence 5 ms.
- `risk.prediction_persist` chờ 50,410 ms và lỗi khi chạy `INSERT INTO risk_predictions`.
- MySQL error 1205, SQLState 40001; message `Lock wait timeout exceeded`.
- `RecommendationService` mất 81,723 ms và HTTP request kết thúc 500 sau 81,738 ms.
- Request `bbe8aa7b-1661-43d8-91d0-37b69841f533`, cùng endpoint/userRef, cũng kết thúc 500 sau 51,256 ms tại thời điểm request còn lại đã qua các history query. Exact stage failure của request này không thể gán từ excerpt vì thiếu timing span tương ứng.

## 3. Phân loại các giả thuyết

| Nhóm | Trạng thái | Căn cứ / việc còn thiếu |
|---|---|---|
| Backend exception | Chưa xác định | HTTP 500 xác nhận exception/error response; chưa có type, message hoặc stack trace. |
| DB query/transaction/lock | Lock wait tại risk insert đã xác nhận; lock owner chưa rõ | SQL error 1205/40001 trên `INSERT risk_predictions`, stage 50.410s. Parent check-in lock + `REQUIRES_NEW` FK insert là cơ chế self-blocking có căn cứ mạnh, nhưng chưa có wait-for graph. |
| AI communication | Không phải công đoạn chậm trong request có timing | `ai.http_predict` thành công trong 22 ms. Chưa có AI-service-side stage logs để phân tích model/SHAP riêng. |
| Request/response mapping | Chưa xác định | Feature map suy ra khớp 10 trường/range; actual serialized payload/AI JSON không có. |
| Rule/scoring | Ít dấu hiệu từ DB, vẫn chưa loại trừ | Rule CHALLENGE hợp lệ và GYM exercises tồn tại; chưa có runtime rule/candidate/scorer trace. |
| Snapshot persistence | Chưa xác định | Snapshot table trống; có unique/FK/column limit. Chưa có SQL exception. |
| Timeout / pool | Lock timeout xác nhận; pool exhaustion chưa có bằng chứng | MySQL trả error 1205 sau khoảng 50s. Không có Hikari metrics hoặc InnoDB lock owner report để kết luận về pool/blocker cụ thể. |

Không gán xác suất hoặc xếp hạng giả thuyết khi chưa có log.

## 4. Bản sửa đã triển khai

- `RiskPredictionStore.saveInCurrentTransaction()` dùng propagation `REQUIRED`.
- `RiskService.assessForRecommendation()` chọn phương thức lưu cùng transaction thay vì `REQUIRES_NEW`.
- `RecommendationService.today()` gọi luồng mới khi cache miss. Khóa check-in và transaction boundary hiện tại được giữ nguyên.
- Các caller khác (`todayRisk`, `strategyContextFor`) vẫn dùng `assess()`/`REQUIRES_NEW` như trước.
- Kết quả prediction được commit/rollback cùng recommendation snapshot trong luồng này; đây là thay đổi atomicity có chủ đích. API response, feature, model, rule, scoring và định dạng snapshot không đổi.
- Không tăng timeout, không thêm retry, không thay model/scoring. Không migration hoặc sửa dữ liệu.

## 5. Kiểm thử và giới hạn

- Thêm `RiskServiceTest`: xác nhận recommendation assessment gọi save trong transaction hiện tại và assessment thường vẫn gọi save độc lập.
- `mvn -q -Dtest=RiskServiceTest test`: PASS.
- `mvn -q test` từ `backend/`: PASS, 26 tests, 0 failures/errors/skips.
- Chưa có MySQL-backed test chứng minh FK insert trong cùng transaction dưới lock hoặc stress test cạnh tranh; runtime verification trên schema disposable vẫn cần thực hiện sau khi backend được rebuild/restart. Không tự gọi API trong phiên sửa này.

## 6. Hồi quy cần xác nhận sau khi build/restart

1. Trên disposable schema với check-in đã có, gọi recommendation một lần sau khi backend restart; xem `risk.prediction_persist` không còn chờ ~50s và endpoint không trả 500.
2. Xác nhận có tối đa một risk prediction và một recommendation snapshot cho check-in đó, bằng truy vấn chỉ đọc.
3. Gọi lại recommendation để xác nhận cache-hit trả cùng snapshot ID.
4. Nếu chạy song song request, xác nhận request thứ hai không tạo thêm prediction/snapshot và không có lock timeout.
5. Test `GET /api/ai/today` và `POST /api/workouts/start` riêng để đảm bảo các caller khác của `RiskService.assess()` giữ hành vi cũ.

## 7. Test hồi quy bổ sung có thể thực hiện sau

- Integration test `RecommendationService.today()` với disposable MySQL/Flyway V11: profile/check-in -> risk -> strategy -> exercise catalog -> snapshot -> đọc lại snapshot.
- Test AI client contract: 10 feature names/types/ranges, valid response DTO; 401/422/5xx/network timeout kích hoạt fallback; response malformed được ghi nhận rõ.
- Persistence tests cho `risk_predictions` và snapshot: unique/FK/JSON column-size behavior, transaction rollback/commit và retry.
- Lock/concurrency test cho hai lần GET đồng thời cùng check-in; assert không tạo snapshot trùng và request thứ hai có giới hạn thời gian phù hợp.
- API tests cho thiếu profile/check-in, dữ liệu check-in hợp lệ/không hợp lệ, profile cap và location filtering.
- End-to-end workout start từ recommendation, complete, đọc lại session; không chạy tới bước này cho tới khi recommendation API pass.

Các bài test phải dùng database disposable, không gọi model thật trừ khi đó là kiểm thử hợp đồng có kiểm soát; không dùng dữ liệu/nhãn giả để tuyên bố hiệu quả model.

## 8. Kịch bản kiểm thử lại an toàn

1. Giữ nguyên schema test/disposable và dữ liệu hiện có; không xóa dữ liệu.
2. Rebuild/restart backend để nạp bản sửa; không cần migration.
3. Dùng một GET trên check-in đã có để xác nhận cache-miss sau khi fix (GET có thể tạo risk prediction/snapshot, do đó phải là request có chủ đích).
4. Lưu status, duration, request ID và log timing redacted; xác nhận không có SQL 1205.
5. Xác minh counts read-only và cache-hit lần sau. Không gửi lại POST check-in.
6. Chỉ chạy các luồng workout sau khi recommendation pass và được cho phép.

## Điểm dừng

Đã áp dụng bản sửa code và chạy test backend; chưa gọi API, migration, reset hoặc xóa dữ liệu. Cần rebuild/restart backend và kiểm thử runtime có kiểm soát để xác nhận MySQL lock timeout đã hết.
