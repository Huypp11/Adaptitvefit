# Stage 2B - Cấu hình feature cho thí nghiệm phân cụm

## Kiểm tra và xử lý dữ liệu đầu vào

Thí nghiệm đọc `survey_cleaned.csv` và `feature_dictionary.csv` của Stage 1B, rồi kiểm tra tên cột, kiểu dữ liệu thực tế, các giá trị danh mục và dữ liệu thiếu trước khi phân cụm. Dữ liệu có 322 dòng và 19 cột đã làm sạch. `last_break_duration` và `stopping_reasons` mỗi cột có 96 giá trị thiếu; cả hai không được dùng trong các cấu hình ban đầu. Không xóa dòng hoặc tự điền giá trị thiếu. `timestamp` và `respondent_context` không được dùng để phân cụm.

Các cấu hình chỉ sử dụng feature có căn cứ trong Feature Dictionary và EDA. Thứ bậc của các biến ordinal được khai báo rõ ràng, không tùy tiện gán mã số cho category. Khoảng cách Gower xử lý riêng biến numeric, ordinal, nominal và trạng thái đặc biệt `not_currently_training` của thời lượng buổi tập. K-Prototypes chuẩn hóa biến numeric/ordinal và giữ nguyên biến nominal dạng danh mục. K-Means là phương pháp baseline chỉ dùng numeric/ordinal; các biến danh mục được loại khỏi phương pháp này.

## Các cấu hình feature

| Mã | Feature | Mục đích |
|---|---|---|
| A | `workout_days_last_7d`, `workout_days_previous_7d`, `usual_session_duration`, `target_sessions_per_week`, `sleep_duration`, `stress_level`, `energy_level`, `mood`, `motivation`, `daily_available_training_time` | Bộ feature chính về hành vi tập luyện và trạng thái cá nhân. |
| B | Toàn bộ feature của A, cộng `usual_training_location`, `training_history_status` | Đánh giá việc bổ sung bối cảnh và lịch sử tập luyện. |
| C | Toàn bộ feature của B, cộng `response_when_tired` | Phân tích độ nhạy khi bổ sung bối cảnh phản ứng lúc mệt. |
| A_gap | Thay `target_sessions_per_week` và `workout_days_last_7d` trong A bằng `goal_behavior_gap = target_sessions_per_week - workout_days_last_7d` | Thử nghiệm chênh lệch giữa mục tiêu và hành vi mà không đồng thời đưa cả hai biến thành phần vào cấu hình. Vẫn giữ `workout_days_previous_7d`. Đây là biến mô tả, không phải kết quả cần dự đoán. |

`respondent_context`, `timestamp`, `last_break_duration`, `stopping_reasons`, `return_support_factors` và `helpful_after_rest` không phải feature phân cụm trong các thí nghiệm chính này. Một số biến có thể dùng để mô tả cụm sau khi phân cụm. Không điền giá trị thiếu của Q8/Q9.

## Thuật toán và đánh giá

- **Gower + Agglomerative Clustering (average linkage):** dùng ma trận khoảng cách Gower tính trước; thử `k=2,3,4,5`. Silhouette được tính trên đúng ma trận Gower đó (`metric="precomputed"`).
- **K-Prototypes:** biến numeric và ordinal được chuẩn hóa; biến nominal vẫn là category; thử `k=2,3,4,5`. Silhouette được tính bằng khoảng cách Gower như một chỉ số đánh giá tách biệt bên ngoài, không phải hàm mục tiêu của K-Prototypes.
- **K-Means numeric baseline:** chỉ dùng feature numeric và ordinal đã chuẩn hóa; thử `k=2,3,4,5`. Các cấu hình A/B/C có cùng không gian numeric. Thời lượng buổi tập được biểu diễn bằng thứ bậc thời lượng và một cờ riêng cho trạng thái không tập. Silhouette của K-Means dùng khoảng cách Euclidean trong không gian numeric đã chuẩn hóa.

Độ ổn định được ước lượng qua nhiều lần lấy mẫu con 80% không hoàn lại. Mỗi lần, Adjusted Rand Index (ARI) so sánh nhãn cụm của mẫu con với kết quả tham chiếu trên đúng các bản ghi đó. Đây là kiểm tra độ nhạy trên mẫu hiện có, không phải xác thực ngoài mẫu.

Việc đánh giá xem xét đồng thời silhouette, độ ổn định, mức cân bằng kích thước cụm, khả năng diễn giải của bộ feature và hạn chế của thuật toán. Không tự động loại cụm nhỏ. Không giả định trước số cụm; mọi giá trị k được yêu cầu đều được giữ lại và báo cáo.

## Gán cụm cho bản ghi mới

Agglomerative là phương pháp phân cụm dựa trên toàn bộ dữ liệu và không có hàm `predict` tích hợp. Artifact lưu đặc tả tiền xử lý Gower đã khớp, dữ liệu tham chiếu khi huấn luyện và medoid của các cụm. Hàm tiện ích đi kèm có thể gán bản ghi mới vào medoid gần nhất như một phép xấp xỉ đã ghi rõ; cách này không tương đương với việc chạy lại Agglomerative và cần được kiểm định trước khi dùng thực tế.

Estimator K-Prototypes và K-Means cùng các đối tượng tiền xử lý cũng được lưu cho từng ứng viên. Artifact nằm riêng trong thư mục thí nghiệm và không thay đổi model registry của AI.
