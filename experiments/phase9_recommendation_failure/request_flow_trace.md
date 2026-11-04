# Truy vết luồng Recommendation API

Endpoint là `GET /api/recommendations/today`. Bước trục trặc chưa xác định vì không có log theo request. Thứ tự bên dưới là thứ tự thực tế trong code.

## Sơ đồ luồng

```text
HTTP GET /api/recommendations/today
  -> RecommendationController.today()
  -> RecommendationService.today() [một transaction]
     -> CurrentUserService.get() / JWT principal
     -> UserProfileRepository.findByUserId()
     -> DailyCheckinRepository.findByUserIdAndCheckinDateForUpdate()
        [PESSIMISTIC_WRITE]
     -> RecommendationHistoryRepository.findForUpdate()
        [PESSIMISTIC_WRITE]
     -> nếu đã có snapshot: deserialize snapshot và trả response
     -> UserStateService.snapshot()
        -> check-in history
        -> completed workout history
        -> completed-workout feedback
        -> tạo UserState + đúng 10 AI features
     -> RiskService.assess()
        -> RiskPredictionStore.find() [REQUIRES_NEW, read-only]
        -> nếu chưa có prediction: AiServiceClient.predict()
           -> POST {AI_BASE_URL}/predict, header X-Internal-Key
           -> response mapping hoặc catch RestClientException và fallback lịch sử
        -> nếu AI response thành công: đảm bảo ModelVersion,
           lưu risk_predictions [REQUIRES_NEW]
     -> InterventionService.activeRules()
     -> InterventionEngine.decide()
     -> ExerciseRepository.findByActiveTrue()
     -> RecommendationScorer.recommend()
     -> ObjectMapper serialize snapshot
     -> RecommendationHistoryRepository.saveAndFlush()
     -> toResponse(): deserialize snapshot, tìm workout/feedback liên kết
  -> HTTP response
```

## Các điểm có thể lỗi hoặc chờ lâu

| Bước | File / method | Rủi ro kỹ thuật cần đối chiếu với log |
|---|---|---|
| Auth/current user | `RecommendationController.today()` và `RecommendationService.today()` | JWT/principal hoặc truy vấn user; các endpoint profile/check-in đã hoạt động với cùng hai tài khoản nên auth cơ bản đã qua trong các request đó. |
| Profile | `RecommendationService.today()` | Không tìm thấy profile sẽ ném `BadRequestException`; profile round-trip đã thành công cho cả hai user. |
| Lock check-in | `DailyCheckinRepository.findByUserIdAndCheckinDateForUpdate()` | Khóa pessimistic nằm trong transaction `today()`; có thể chờ lock. Không có trạng thái lock tại thời điểm sự cố. |
| Lock snapshot | `RecommendationHistoryRepository.findForUpdate()` | Khóa pessimistic thứ hai; query trả `Optional.empty` theo số liệu snapshot bằng 0 sau lỗi. Không chứng minh request đã hoàn tất bước này. |
| Lịch sử | `UserStateService.snapshot()` | Thực hiện ba truy vấn DB cho check-in/workout/feedback. Dữ liệu test nhỏ, không có workout/feedback; không có query timing theo request. |
| Inference | `RiskService.assess()` / `AiServiceClient.predict()` | AI network timeout được đặt 3000 ms ở connect/read; lỗi `RestClientException` bị chuyển thành fallback. Không có log request/status/elapsed/response. |
| Ghi prediction | `RiskPredictionStore.ensureModelVersion()` / `save()` | Chạy `REQUIRES_NEW`, `saveAndFlush`; có thể lỗi FK/schema/serialization/SQL. DB không có prediction row cho hai user sau lỗi. |
| Rule và engine | `InterventionService.activeRules()` / `InterventionEngine.decide()` | Query rule và parse expression/params. Database trả rule `CHALLENGE` đã được cập nhật bởi V11; chưa có bằng chứng exception tại bước này. |
| Catalog và scorer | `ExerciseRepository.findByActiveTrue()` / `RecommendationScorer.recommend()` | Load exercises và tags; V11 có 3 bài GYM mới. Chưa có trace xác nhận candidate list hoặc lỗi mapping ở runtime. |
| Snapshot | `RecommendationService.today()` | Jackson serialize rồi `saveAndFlush` vào `snapshot_json`; có unique/FK/column-size constraints. Không có snapshot rows sau lỗi; SQL exception chưa thu được. |
| Response | `RecommendationService.toResponse()` | Deserialize JSON và query linked workout/feedback; lỗi mapping có thể xảy ra sau khi snapshot được lưu. DB hiện không có snapshot nên chưa biết có tới bước này không. |

## Dữ liệu của hai test user và feature map dự kiến

DB chỉ đọc cho thấy cả hai check-in đều: sleep 7, stress 2, energy 5, mood 4, free time 30, motivation 4, `exercised_today=false`; không có workout hoàn thành và feedback.

Theo thuật toán hiện tại trong `UserStateService.snapshot()`, nếu `LocalDate.now()`/ngày user được tạo đúng như check-in `2026-11-02`, feature map được suy ra là:

```text
sleep_hours=7
stress_level=2
energy_level=5
mood=4
motivation=4
free_time_min=30
missed_days=1
workout_frequency_7d=0
avg_duration_7d=0
avg_difficulty_7d=0
```

Đây là **giá trị tính từ code và dữ liệu DB**, không phải payload được capture từ HTTP client. Nếu ngày chạy backend khác ngày check-in do timezone, `missed_days` có thể khác; chưa có runtime clock/log để chốt.

## Bản đồ mã nguồn

- [RecommendationController.java](../../backend/src/main/java/com/adaptivefit/controller/RecommendationController.java) — route và delegate.
- [RecommendationService.java](../../backend/src/main/java/com/adaptivefit/service/RecommendationService.java) — transaction, orchestration, snapshot.
- [UserStateService.java](../../backend/src/main/java/com/adaptivefit/service/UserStateService.java) — history query và feature generation.
- [RiskService.java](../../backend/src/main/java/com/adaptivefit/service/RiskService.java) — cache, AI, fallback, lưu prediction.
- [AiServiceClient.java](../../backend/src/main/java/com/adaptivefit/client/AiServiceClient.java) — HTTP client và timeout.
- [InterventionEngine.java](../../backend/src/main/java/com/adaptivefit/engine/InterventionEngine.java) và [RecommendationScorer.java](../../backend/src/main/java/com/adaptivefit/engine/RecommendationScorer.java) — chọn strategy và bài.
