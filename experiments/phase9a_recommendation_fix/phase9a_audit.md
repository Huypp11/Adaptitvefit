# Phase 9A.1 - Audit Recommendation Engine trước khi sửa

**Phạm vi:** code hiện tại trong repository AdaptiveFit; chỉ audit, chưa sửa production code, API contract, schema, catalog hay migration.

## Luồng request/response

1. `GET /api/recommendations/today` gọi `RecommendationService.today()`.
2. Service lấy `UserProfile`, check-in hôm nay, lịch sử qua `UserStateService`, và risk từ `RiskService` (model nếu có; history fallback nếu AI chưa sẵn sàng).
3. `InterventionEngine` chọn rule active đầu tiên theo priority từ database, dựa trên `UserState.facts()`.
4. `RecommendationScorer` lọc cứng exercise, chấm điểm, đa dạng muscle group, sau đó tạo plan theo time budget.
5. Backend lưu snapshot JSON theo check-in và trả `RecommendationResponse`. Frontend `Today.tsx` render strategy/reasons, state, risk/demo notice, exercise cards, plan và scoring breakdown.
6. `POST /api/workouts/start` liên kết workout với recommendation; nếu có recommendation ID, backend xác minh exercise IDs và phút đã chọn thuộc snapshot. Workout response/history dùng exercise đã lưu.

## Strategy và dữ liệu trạng thái

- Rule default trong `V7__intervention_rules.sql` / `V9__strategy_done.sql`: `DONE`, `RESTART`, `RECOVERY`, `REDUCE`, `CHALLENGE`, `NORMAL`, với priority theo thứ tự trên. `InterventionService` lấy rule active/priority từ DB; test engine có fixture tương ứng.
- `RESTART`: missed days >=3 hoặc HIGH risk và missed days >=2; cap 15 phút, LOW intensity.
- `RECOVERY`: sleep <6, stress >=4 và energy <=2 đồng thời; cap 20 phút, LOW và recovery suitable.
- `REDUCE`: free time <20, hoặc energy <=2, hoặc MEDIUM risk; cap 20 phút, tối đa MEDIUM.
- `CHALLENGE`: LOW risk, energy >=4, workout frequency >=3; min MEDIUM, hiện không có max intensity.
- `NORMAL`: fallback, không có giới hạn intensity riêng.
- `DONE`: đã tập hôm nay; tùy chọn tối đa 10 phút, LOW và recovery suitable.
- Stress chỉ tham gia trực tiếp vào điều kiện RECOVERY khi đồng thời sleep và energy thấp. Mood/motivation có trong `UserState.facts()` và explanation expression parser, nhưng không có rule seed dùng hai trường này.
- Energy được dùng trong REDUCE, hard filter `energyRequirement <= user energy` và thành phần score; sleep chỉ dùng trong RECOVERY. Model dropout risk là tín hiệu cho rule, nhưng không được dùng để suy ra mức cường độ an toàn.

## Filtering, scoring và explanations hiện tại

- Hard filters trong `RecommendationScorer`: equipment, minimum duration so với budget, energy requirement, `StrategyParams.maxIntensity`/`minIntensity`, `requireRecovery`.
- Location hiện chỉ được cộng điểm ở `preferenceMatch`; không phải hard filter. Vì vậy location HOME có thể nhận OUTDOOR nếu exercise được chọn.
- `StrategyParams.maxIntensity` là giới hạn do strategy đặt, không phải sở thích người dùng. `ProfileContext` không có intensity preference hoặc maximum intensity. `ProfileRequest`, `ProfileResponse`, `UserProfile` và frontend Profile cũng không có field intensity.
- `ProfileRequest` hiện kiểm tra profile location là `HOME|GYM|OUTDOOR`. Exercise locations trong UI cho admin có thêm `ANY`.
- Exercise card có reasons và breakdown; strategy reasons được sinh từ điều kiện thực tế đã match; thời lượng trong plan là `plannedMin`. Không có danh sách “bài bị loại” hoặc reason per exercise trong API; `excludedCounts` chỉ tổng hợp theo loại filter và được dùng tạo message nếu rỗng.
- Khi không có bài, scorer trả `items=[]`, `plan=[]` và message; không có fallback trả bài trái constraint.

## Catalog, enum, equipment

- Schema `exercises` lưu `location`, `intensity`, `equipment` dạng string; không có enum/constraint DB cho các trường này.
- Profile API location enum: `HOME`, `GYM`, `OUTDOOR`. Exercise UI cho phép thêm `ANY`; exercise seed dùng `HOME`, `OUTDOOR`, `ANY`.
- Đối chiếu seed SQL hiện tại: 38 exercise records; location seed chỉ gồm `HOME`, `OUTDOOR`, `ANY`; chưa có record `GYM`. Có `LOW`, `MEDIUM`, `HIGH` intensity.
- Catalog equipment có `NONE` và thiết bị như `yoga_mat`, `jump_rope`, `bicycle`, `stable_step`; profile UI giới hạn danh sách thiết bị theo `EQUIPMENT_OPTIONS`. Exercise cần thiết bị bị lọc cứng nếu profile không sở hữu đủ thiết bị.
- Có thể thêm các exercise GYM bằng migration additive dùng schema hiện có (không đổi schema), ưu tiên bài không cần thiết bị hoặc dùng đúng equipment đã khai báo. Cần chống thêm trùng theo tên khi seed.

## Khoảng trống đã xác nhận

1. Stress cao đơn lẻ không ngăn `CHALLENGE`; trạng thái tốt về energy/frequency/LOW risk có thể chọn strategy này.
2. HOME/OUTDOOR/GYM không được lọc cứng theo profile location; location chỉ tăng điểm preference khi khớp.
3. Seed catalog chưa có exercise `GYM`.
4. Không có field phân biệt intensity mong muốn / maximum user-accepted intensity trong profile/API/database. Strategy intensity params chỉ là rule-level caps.
5. Preferred workout là free text tối đa 50 ký tự, dùng match tên/tag như preference score; không thể dùng nó làm intensity contract rõ ràng mà không đổi semantics.
6. `WorkoutService` xác minh exercise/thời lượng thuộc recommendation snapshot, nhưng cần test hồi quy sau khi thay scoring/filtering để đảm bảo dữ liệu workout giữ đúng đề xuất.

## Quyết định trước khi triển khai

Location, stress-rule và gym seed có thể được sửa mà không đổi schema/API; gym seed cần migration mới, không chỉnh migration cũ. Intensity preference cần một quyết định contract:

- **Phương án A (đề xuất):** field optional `maxExerciseIntensity` với giá trị cho phép `LOW|MEDIUM|HIGH`, được lưu trong profile. Thêm cột nullable qua migration mới và field optional trong profile API/UI. User cũ/null dùng default cap `MEDIUM`; LOW là cap LOW; MEDIUM là cap MEDIUM; HIGH chỉ cho phép khi user chủ động chọn. Đây là API/database extension additive, nhưng vẫn là contract/schema change nên chưa triển khai khi chưa được duyệt.
- **Phương án B:** không thêm lựa chọn người dùng ở Phase 9A; áp dụng cap MEDIUM mặc định cho tất cả và giữ cap LOW cho strategy recovery/restart/done. Không có cách đáng tin cậy để phân biệt user chọn LOW với MEDIUM/HIGH; nếu muốn ghi nhận lựa chọn sau này vẫn cần API/schema change.

Theo yêu cầu Phase 9A, chưa bắt đầu sửa implementation vượt audit cho tới khi xác nhận phương án intensity. Không sửa model v2.0, model registry, API production, DB schema hoặc migrations trong bước audit này.
