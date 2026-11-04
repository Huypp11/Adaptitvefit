# Stage 2C - Diễn giải hồ sơ cụm

## Phạm vi và kiểm tra dữ liệu

- Phân tích đúng ba ứng viên được yêu cầu: Gower + Agglomerative cấu hình B, K-Prototypes cấu hình A và K-Means numeric cấu hình A; cả ba đều có k=2.
- Dữ liệu khảo sát sạch có 322 dòng, 19 cột. Nhãn cụm trong `clustering_results.csv` đã được đối chiếu theo đúng thứ tự dòng với estimator đã lưu trong artifact Stage 2B.
- Không tạo nhãn dropout, không điền missing và không sửa artifact Stage 2B. `last_break_duration` và `stopping_reasons` vẫn thiếu 96 giá trị mỗi biến; mẫu số hợp lệ được báo cáo theo từng cụm.
- Tỷ lệ biến phân loại được tính trên số câu trả lời hợp lệ trong cụm. Với câu hỏi nhiều lựa chọn, phần trăm là tỷ lệ người có câu trả lời hợp lệ chọn từng phương án; tổng có thể vượt 100%.
- Các biến thứ bậc dạng khoảng thời gian được báo cáo theo danh mục/ thứ hạng; không quy đổi thành số phút/giờ trung bình.

## Cách đọc nhãn cụm

Số 0 và 1 chỉ là mã do thuật toán gán, không mang ý nghĩa tốt/xấu hoặc mức nguy cơ. Diễn giải bên dưới dựa trên số liệu thực tế của từng cụm, không dựa vào số nhãn.

## Kích thước, hành vi và trạng thái không tập

| Ứng viên | Cụm | n (%) | Đang không tập, n (%) trong cụm | Tập gần nhất, trung vị ngày/tuần | Tuần trước đó, trung vị | Mục tiêu, trung vị | Chênh lệch mục tiêu-thực tế, trung vị |
|---|---:|---:|---:|---:|---:|---:|---:|
| K-Means numeric baseline (A) | 0 | 182 (56.5%) | 0 (0.0%) | 3.0 | 3.0 | 4.0 | 1.0 |
| K-Means numeric baseline (A) | 1 | 140 (43.5%) | 68 (48.6%) | 0.0 | 0.0 | 3.0 | 2.0 |
| K-Prototypes (A) | 0 | 157 (48.8%) | 0 (0.0%) | 4.0 | 4.0 | 4.0 | 0.0 |
| K-Prototypes (A) | 1 | 165 (51.2%) | 68 (41.2%) | 0.0 | 0.0 | 3.0 | 2.0 |
| Gower + Agglomerative (B) | 0 | 254 (78.9%) | 0 (0.0%) | 3.0 | 3.0 | 3.0 | 1.0 |
| Gower + Agglomerative (B) | 1 | 68 (21.1%) | 68 (100.0%) | 0.0 | 0.0 | 3.0 | 3.0 |

### Chênh lệch mô tả giữa hai cụm

Bảng dưới đây dùng trị tuyệt đối của chênh lệch trung bình (hoặc chênh lệch trung vị với goal gap) giữa cluster 0 và 1. Đây là mô tả trên chính các biến đầu vào/profiling, không phải kiểm định độc lập hay suy luận nhân quả.

| Ứng viên | Chênh lệch TB ngày tập gần nhất | Chênh lệch TB tuần trước | Chênh lệch trung vị goal gap | Chênh lệch TB stress | Năng lượng | Tâm trạng | Động lực |
|---|---:|---:|---:|---:|---:|---:|---:|
| Gower + Agglomerative | 2.78 | 2.84 | 2.0 | 0.51 | 0.50 | 0.69 | 0.82 |
| K-Prototypes | 2.84 | 2.82 | 2.0 | 0.77 | 0.95 | 0.95 | 1.31 |
| K-Means numeric baseline | 2.62 | 2.61 | 1.0 | 0.88 | 1.09 | 1.12 | 1.37 |

- **Hành vi tập luyện:** K-Prototypes A có chênh lệch trung bình lớn nhất về số ngày tập gần nhất giữa hai cụm (2.84 ngày/tuần), sát sau là Gower B (2.78) và K-Means A (2.62). Với Gower B, khác biệt này trùng hoàn toàn với trạng thái không tập hiện tại; vì vậy không nên diễn giải như một phân nhóm hành vi độc lập với trạng thái.
- **Trạng thái cá nhân:** trong ba phương án, K-Means A có chênh lệch trung bình lớn hơn ở năng lượng, tâm trạng và động lực (lần lượt khoảng 1.09, 1.12 và 1.37 điểm trên thang 1-5); chênh lệch này cũng đi cùng khác biệt về tần suất tập/trạng thái không tập. Đây là khác biệt mô tả, không chứng minh trạng thái cá nhân tạo ra hành vi tập.
- **Mục tiêu so với thực tế:** Gower B có chênh lệch trung bình goal gap giữa hai cụm lớn nhất (2.53 buổi/tuần; trung vị chênh 2 buổi), theo sau là K-Prototypes A (2.23; trung vị chênh 2) và K-Means A (1.89; trung vị chênh 1). Cụm có gap cao hơn cũng tập ít hơn trong tuần gần nhất.
- **Kích thước và khả năng giải thích:** kích thước nhỏ nhất là 68/322. Gower B dễ mô tả nhất nhưng chủ yếu chia người đang tập và không tập. K-Prototypes A và K-Means A tạo hai cụm cân bằng hơn, kết hợp khác biệt về hành vi và trạng thái cá nhân; cần kiểm chứng trên dữ liệu mới trước khi xem đó là phân khúc bền vững.

### Gower + Agglomerative - cấu hình B, k=2

- Stage 2B: silhouette **0.301** trong không gian `gower`; ARI stability **1.000 ± 0.000** qua 8 mẫu con. Silhouette chỉ được dùng để mô tả trong cùng metric, không so trực tiếp giữa các phương pháp.
- Cụm 1 gồm 68 người và **100%** chọn `not_currently_training`; đây là cụm phân tách trạng thái hiện không tập rõ rệt.
- Cụm 0 (n=254): thời lượng tập phổ biến nhất `30_45_min` (28.7%, n hợp lệ=254); ngủ `6_7_hours` (39.4%, n=254); thời gian rảnh `31_45_min` (30.3%, n=254); địa điểm `home` (46.5%, n=254); phản ứng khi mệt `short_light_workout` (32.3%, n=254). Trung bình số ngày tập tuần gần nhất 2.78, năng lượng 3.07, tâm trạng 3.32, động lực 3.33; trung vị chênh lệch mục tiêu-thực tế 1.0 buổi/tuần.
- Cụm 1 (n=68): thời lượng tập phổ biến nhất `not_currently_training` (100.0%, n hợp lệ=68); ngủ `5_6_hours` (42.6%, n=68); thời gian rảnh `15_30_min` (42.6%, n=68); địa điểm `not_currently_training` (100.0%, n=68); phản ứng khi mệt `rest` (33.8%, n=68). Trung bình số ngày tập tuần gần nhất 0.00, năng lượng 2.57, tâm trạng 2.63, động lực 2.51; trung vị chênh lệch mục tiêu-thực tế 3.0 buổi/tuần.

### K-Prototypes - cấu hình A, k=2

- Stage 2B: silhouette **0.270** trong không gian `gower_external_mixed_data`; ARI stability **0.890 ± 0.117** qua 8 mẫu con. Silhouette chỉ được dùng để mô tả trong cùng metric, không so trực tiếp giữa các phương pháp.
- Không có cụm nào gồm toàn bộ người chọn `not_currently_training`; trạng thái tập/không tập không phải ranh giới hoàn toàn thuần của kết quả này.
- Cụm 0 (n=157): thời lượng tập phổ biến nhất `46_60_min` (29.3%, n hợp lệ=157); ngủ `6_7_hours` (36.3%, n=157); thời gian rảnh `46_60_min` (36.3%, n=157); địa điểm `home` (45.9%, n=157); phản ứng khi mệt `short_light_workout` (32.5%, n=157). Trung bình số ngày tập tuần gần nhất 3.64, năng lượng 3.45, tâm trạng 3.66, động lực 3.83; trung vị chênh lệch mục tiêu-thực tế 0.0 buổi/tuần.
- Cụm 1 (n=165): thời lượng tập phổ biến nhất `not_currently_training` (41.2%, n hợp lệ=165); ngủ `6_7_hours` (38.2%, n=165); thời gian rảnh `15_30_min` (42.4%, n=165); địa điểm `not_currently_training` (41.2%, n=165); phản ứng khi mệt `rest` (37.0%, n=165). Trung bình số ngày tập tuần gần nhất 0.81, năng lượng 2.50, tâm trạng 2.71, động lực 2.52; trung vị chênh lệch mục tiêu-thực tế 2.0 buổi/tuần.

### K-Means numeric baseline - cấu hình A, k=2

- Stage 2B: silhouette **0.223** trong không gian `euclidean_numeric_scaled`; ARI stability **0.968 ± 0.041** qua 8 mẫu con. Silhouette chỉ được dùng để mô tả trong cùng metric, không so trực tiếp giữa các phương pháp.
- Không có cụm nào gồm toàn bộ người chọn `not_currently_training`; trạng thái tập/không tập không phải ranh giới hoàn toàn thuần của kết quả này.
- Cụm 0 (n=182): thời lượng tập phổ biến nhất `46_60_min` (26.9%, n hợp lệ=182); ngủ `6_7_hours` (40.7%, n=182); thời gian rảnh `31_45_min` (33.5%, n=182); địa điểm `home` (45.1%, n=182); phản ứng khi mệt `short_light_workout` (31.3%, n=182). Trung bình số ngày tập tuần gần nhất 3.33, năng lượng 3.44, tâm trạng 3.66, động lực 3.76; trung vị chênh lệch mục tiêu-thực tế 1.0 buổi/tuần.
- Cụm 1 (n=140): thời lượng tập phổ biến nhất `not_currently_training` (48.6%, n hợp lệ=140); ngủ `5_6_hours` (41.4%, n=140); thời gian rảnh `15_30_min` (44.3%, n=140); địa điểm `not_currently_training` (48.6%, n=140); phản ứng khi mệt `rest` (36.4%, n=140). Trung bình số ngày tập tuần gần nhất 0.71, năng lượng 2.35, tâm trạng 2.54, động lực 2.39; trung vị chênh lệch mục tiêu-thực tế 2.0 buổi/tuần.

## Đối chiếu ba ứng viên

- **Phân tách trạng thái đang tập/không tập:** Gower + Agglomerative B tách thành một cụm 68 người đều khai báo `not_currently_training`; 254 người còn lại đang tập với nhiều mức thời lượng khác nhau. K-Prototypes A và K-Means A cũng đưa trạng thái thời lượng vào feature nhưng không tạo cụm hoàn toàn thuần theo trạng thái. Lưu ý đây là đối chiếu với feature đầu vào, không phải bằng chứng độc lập.
- **Khác biệt hành vi:** xem chênh lệch trung vị/trung bình của hai biến số ngày tập, mục tiêu và `goal_behavior_gap` trong `cluster_profiles_detailed.csv` và `candidate_comparison.csv`. K-Prototypes và K-Means cấu hình A có thể chia cả người đang tập lẫn không tập; diễn giải phải đọc cùng tỷ lệ trạng thái trong mỗi cụm.
- **Khác biệt trạng thái cá nhân:** so sánh stress, năng lượng, tâm trạng và động lực bằng trung vị/IQR/trung bình trong bảng chi tiết. Các giá trị là tự khai báo cùng thời điểm, chỉ biểu thị khác biệt mô tả giữa nhóm.
- **Khoảng cách mục tiêu-thực tế:** `goal_behavior_gap = mục tiêu - số ngày tập gần nhất`; số dương lớn hơn nghĩa là mục tiêu được khai báo cao hơn hành vi tuần vừa qua. Đây không phải nguyên nhân, không phải dự báo tương lai, và tuần gần nhất có thể không đại diện thói quen dài hạn.
- Các cụm đều có quy mô đủ để mô tả ở mức thống kê mẫu này (nhỏ nhất là 68 người trong ba ứng viên), nhưng điều đó không chứng minh tính ổn định ngoài mẫu hoặc tính đại diện.

## Độ ổn định có phù hợp với hồ sơ không?

- Gower B có ARI 1.000 (SD 0.000) trên 8 mẫu con và tách rõ trạng thái hiện không tập. Đây là kết quả lặp lại ổn định trong phép lấy mẫu con đã dùng, nhưng cụm chủ yếu phản ánh trạng thái tập luyện, và trạng thái đó vốn là feature của cấu hình.
- K-Means A có ARI 0.968 (SD 0.041), cao; hai cụm có quy mô 182/140. Cấu trúc phân nhóm có thể ổn định theo phép thử đã chạy nhưng không hàm ý là hai nhóm tự nhiên/ổn định ở mẫu khác.
- K-Prototypes A có ARI 0.890 (SD 0.117), thấp hơn hai phương pháp kia và độ dao động lớn hơn. Cần thận trọng hơn khi xem các khác biệt giữa cụm là cấu trúc bền vững.
- ARI ở đây so sánh kết quả trên các mẫu con với kết quả tham chiếu trên đúng các cá nhân đó; đây là kiểm tra độ nhạy nội bộ, không phải xác nhận ngoài mẫu.

## Khả năng ứng dụng trong AdaptiveFit

- **Thời lượng tập:** có thể dùng tỷ lệ thời lượng buổi tập khai báo và thời gian rảnh để gợi ý lựa chọn thử nghiệm; không nên mặc định người trong cùng cụm cần một thời lượng cố định.
- **Cường độ:** các biến stress/năng lượng/tâm trạng/động lực có thể hỗ trợ điều chỉnh gợi ý theo câu trả lời hiện tại. Chúng là tự báo cáo và không đo sức khỏe hay khả năng tập an toàn.
- **Địa điểm và loại bài:** Gower B có `usual_training_location` làm feature; K-Prototypes A và K-Means A không dùng địa điểm để tạo cụm, nhưng có thể dùng để profiling. Cả ba ứng viên đều có `response_when_tired` là biến profiling, không phải feature tạo cụm. Không suy diễn rằng nhóm thích địa điểm/bài tập nào nếu dữ liệu chỉ ghi nhận lựa chọn hiện tại.
- **Khoảng cách mục tiêu-thực tế lớn:** có thể dùng gap để mở lời bằng kế hoạch nhỏ hơn hoặc hỏi người dùng muốn điều chỉnh mục tiêu hay thời gian tập. Đây là hướng thử nghiệm UX, không phải quan hệ nhân quả đã được chứng minh.
- **Không dùng cụm như nhãn dropout:** khảo sát cắt ngang không cung cấp nhãn/kết quả bỏ tập trong tương lai; các cụm không phải dự báo rủi ro.

## Hạn chế và cách đọc các tỷ lệ

- Tỷ lệ phân loại dùng mẫu số câu trả lời hợp lệ trong từng cụm; `n_valid` và `n_missing` nằm trong từng dòng của CSV chi tiết.
- `last_break_duration` và `stopping_reasons` mỗi biến thiếu 96 giá trị. Missing được giữ nguyên; phân tích Q8/Q9 chỉ dùng câu trả lời hợp lệ và không giả định lý do thiếu.
- `return_support_factors` là câu hỏi nhiều lựa chọn: tỷ lệ của mỗi lựa chọn tính trên người trả lời hợp lệ; tổng tỷ lệ có thể lớn hơn 100%.
- Nhóm `not_currently_training` là trạng thái đặc biệt, không phải thời lượng tập. Khi báo cáo thứ hạng thời lượng, chỉ những người đang tập được dùng; bảng vẫn giữ phân phối trạng thái đầy đủ.
- Mẫu gồm 322 câu trả lời tự khai báo, cắt ngang và chưa xác lập tính đại diện. Không kết luận quan hệ nhân quả hoặc khả năng tổng quát hóa cho người trẻ Việt Nam.

Các biểu đồ đi kèm: `figures_behavior_comparison.png`, `figures_personal_state_comparison.png` và `figures_stability_comparison.png`. Không vẽ một bảng xếp hạng silhouette chung giữa các metric không tương thích.
