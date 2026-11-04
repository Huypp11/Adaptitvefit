# AdaptiveFit AI - Stage 2B: Thí nghiệm phân cụm

- Số bản ghi đầu vào: **322**; số cột đã làm sạch: 19; số dòng trong Feature Dictionary: 19.
- Thư mục đầu ra: `D:\adaptivefit-phase3b\adaptivefit\experiments\stage2b_clustering`.
- Không tạo nhãn dropout hay dữ liệu giả; không huấn luyện Logistic Regression; không thay đổi model registry, API, backend, frontend hoặc CSV nguồn.
- Giữ nguyên cả 322 dòng. Có 96 dòng thiếu Q8/Q9; hai câu hỏi này không nằm trong các feature thử nghiệm nên không có dòng nào bị loại vì thiếu dữ liệu.
- Độ ổn định: lấy mẫu con 80% không hoàn lại 8 lần; báo cáo ARI trung bình và độ lệch chuẩn.

## Phương pháp và cấu hình đã chạy

- Cấu hình: A, B, C, A_gap.
- Thuật toán: gower_agglomerative, kprototypes, kmeans_numeric; K-Prototypes đã có thư viện và được chạy.
- Số cụm thử nghiệm: k=2, 3, 4, 5.
- Gower + Agglomerative (average linkage) dùng ma trận khoảng cách Gower tính trước; silhouette dùng chính ma trận đó.
- K-Prototypes dùng biến numeric/ordinal đã chuẩn hóa và biến nominal dạng danh mục; silhouette Gower là chỉ số đánh giá bên ngoài, không phải hàm mục tiêu của thuật toán.
- K-Means là baseline chỉ dùng numeric/ordinal đã chuẩn hóa; silhouette dùng khoảng cách Euclidean. Không mã hóa category nominal thành số nguyên.

## Bảng so sánh các ứng viên

| Cấu hình | Thuật toán | k | Silhouette (khoảng cách) | ARI ổn định trung bình (ĐLC) | Số người trong từng cụm | Tỷ lệ cụm nhỏ nhất (%) | Entropy kích thước chuẩn hóa |
|---|---|---:|---|---:|---|---:|---:|
| B | gower_agglomerative | 2 | 0.301 (gower) | 1.000 (0.000) | {"0": 254, "1": 68} | 21.1 | 0.744 |
| A_gap | gower_agglomerative | 2 | 0.308 (gower) | 0.852 (0.268) | {"0": 254, "1": 68} | 21.1 | 0.744 |
| C | gower_agglomerative | 2 | 0.267 (gower) | 1.000 (0.000) | {"0": 254, "1": 68} | 21.1 | 0.744 |
| A | gower_agglomerative | 2 | 0.301 (gower) | 0.585 (0.348) | {"0": 254, "1": 68} | 21.1 | 0.744 |
| A | gower_agglomerative | 4 | 0.212 (gower) | 0.604 (0.116) | {"0": 6, "1": 145, "2": 103, "3": 68} | 1.9 | 0.813 |
| A | gower_agglomerative | 3 | 0.238 (gower) | 0.464 (0.170) | {"0": 151, "1": 68, "2": 103} | 21.1 | 0.954 |
| A_gap | gower_agglomerative | 4 | 0.217 (gower) | 0.567 (0.174) | {"0": 96, "1": 155, "2": 3, "3": 68} | 0.9 | 0.782 |
| C | gower_agglomerative | 3 | 0.185 (gower) | 0.579 (0.117) | {"0": 157, "1": 68, "2": 97} | 21.1 | 0.947 |
| A_gap | gower_agglomerative | 3 | 0.198 (gower) | 0.722 (0.240) | {"0": 251, "1": 68, "2": 3} | 0.9 | 0.515 |
| B | gower_agglomerative | 4 | 0.183 (gower) | 0.618 (0.194) | {"0": 147, "1": 105, "2": 2, "3": 68} | 0.6 | 0.781 |
| B | gower_agglomerative | 5 | 0.196 (gower) | 0.539 (0.112) | {"0": 70, "1": 105, "2": 2, "3": 68, "4": 77} | 0.6 | 0.869 |
| C | gower_agglomerative | 5 | 0.169 (gower) | 0.565 (0.080) | {"0": 97, "1": 59, "2": 69, "3": 68, "4": 29} | 9.0 | 0.962 |
| B | gower_agglomerative | 3 | 0.197 (gower) | 0.649 (0.255) | {"0": 252, "1": 68, "2": 2} | 0.6 | 0.502 |
| A | gower_agglomerative | 5 | 0.173 (gower) | 0.677 (0.164) | {"0": 103, "1": 145, "2": 5, "3": 68, "4": 1} | 0.3 | 0.705 |
| A_gap | gower_agglomerative | 5 | 0.182 (gower) | 0.664 (0.159) | {"0": 155, "1": 95, "2": 3, "3": 68, "4": 1} | 0.3 | 0.685 |
| C | gower_agglomerative | 4 | 0.165 (gower) | 0.508 (0.123) | {"0": 128, "1": 29, "2": 97, "3": 68} | 9.0 | 0.919 |
| A | kmeans_numeric | 2 | 0.223 (euclidean_numeric_scaled) | 0.968 (0.041) | {"0": 182, "1": 140} | 43.5 | 0.988 |
| B | kmeans_numeric | 2 | 0.223 (euclidean_numeric_scaled) | 0.968 (0.041) | {"0": 182, "1": 140} | 43.5 | 0.988 |
| C | kmeans_numeric | 2 | 0.223 (euclidean_numeric_scaled) | 0.968 (0.041) | {"0": 182, "1": 140} | 43.5 | 0.988 |
| A_gap | kmeans_numeric | 2 | 0.223 (euclidean_numeric_scaled) | 0.923 (0.063) | {"0": 188, "1": 134} | 41.6 | 0.980 |
| A | kmeans_numeric | 5 | 0.166 (euclidean_numeric_scaled) | 0.929 (0.044) | {"0": 70, "1": 63, "2": 67, "3": 70, "4": 52} | 16.1 | 0.996 |
| B | kmeans_numeric | 5 | 0.166 (euclidean_numeric_scaled) | 0.929 (0.044) | {"0": 70, "1": 63, "2": 67, "3": 70, "4": 52} | 16.1 | 0.996 |
| C | kmeans_numeric | 5 | 0.166 (euclidean_numeric_scaled) | 0.929 (0.044) | {"0": 70, "1": 63, "2": 67, "3": 70, "4": 52} | 16.1 | 0.996 |
| A_gap | kmeans_numeric | 4 | 0.187 (euclidean_numeric_scaled) | 0.813 (0.174) | {"0": 65, "1": 78, "2": 86, "3": 93} | 20.2 | 0.994 |
| A_gap | kmeans_numeric | 3 | 0.191 (euclidean_numeric_scaled) | 0.916 (0.035) | {"0": 131, "1": 123, "2": 68} | 21.1 | 0.967 |
| A_gap | kmeans_numeric | 5 | 0.173 (euclidean_numeric_scaled) | 0.853 (0.142) | {"0": 74, "1": 65, "2": 68, "3": 45, "4": 70} | 14.0 | 0.992 |
| A | kmeans_numeric | 3 | 0.170 (euclidean_numeric_scaled) | 0.806 (0.309) | {"0": 113, "1": 121, "2": 88} | 27.3 | 0.992 |
| B | kmeans_numeric | 3 | 0.170 (euclidean_numeric_scaled) | 0.806 (0.309) | {"0": 113, "1": 121, "2": 88} | 27.3 | 0.992 |
| C | kmeans_numeric | 3 | 0.170 (euclidean_numeric_scaled) | 0.806 (0.309) | {"0": 113, "1": 121, "2": 88} | 27.3 | 0.992 |
| A | kmeans_numeric | 4 | 0.170 (euclidean_numeric_scaled) | 0.781 (0.142) | {"0": 96, "1": 75, "2": 67, "3": 84} | 20.8 | 0.994 |
| B | kmeans_numeric | 4 | 0.170 (euclidean_numeric_scaled) | 0.781 (0.142) | {"0": 96, "1": 75, "2": 67, "3": 84} | 20.8 | 0.994 |
| C | kmeans_numeric | 4 | 0.170 (euclidean_numeric_scaled) | 0.781 (0.142) | {"0": 96, "1": 75, "2": 67, "3": 84} | 20.8 | 0.994 |
| A | kprototypes | 2 | 0.270 (gower_external_mixed_data) | 0.890 (0.117) | {"0": 157, "1": 165} | 48.8 | 1.000 |
| C | kprototypes | 2 | 0.209 (gower_external_mixed_data) | 0.933 (0.114) | {"0": 154, "1": 168} | 47.8 | 0.999 |
| B | kprototypes | 2 | 0.226 (gower_external_mixed_data) | 0.865 (0.126) | {"0": 156, "1": 166} | 48.4 | 0.999 |
| A_gap | kprototypes | 2 | 0.254 (gower_external_mixed_data) | 0.913 (0.091) | {"0": 146, "1": 176} | 45.3 | 0.994 |
| A_gap | kprototypes | 3 | 0.197 (gower_external_mixed_data) | 0.904 (0.099) | {"0": 117, "1": 96, "2": 109} | 29.8 | 0.997 |
| A | kprototypes | 3 | 0.193 (gower_external_mixed_data) | 0.842 (0.097) | {"0": 115, "1": 96, "2": 111} | 29.8 | 0.997 |
| A_gap | kprototypes | 4 | 0.197 (gower_external_mixed_data) | 0.765 (0.236) | {"0": 88, "1": 73, "2": 70, "3": 91} | 21.7 | 0.995 |
| C | kprototypes | 3 | 0.128 (gower_external_mixed_data) | 0.782 (0.244) | {"0": 115, "1": 100, "2": 107} | 31.1 | 0.999 |
| A_gap | kprototypes | 5 | 0.205 (gower_external_mixed_data) | 0.621 (0.085) | {"0": 69, "1": 53, "2": 64, "3": 79, "4": 57} | 16.5 | 0.994 |
| A | kprototypes | 5 | 0.202 (gower_external_mixed_data) | 0.555 (0.125) | {"0": 68, "1": 64, "2": 55, "3": 58, "4": 77} | 17.1 | 0.996 |
| B | kprototypes | 4 | 0.158 (gower_external_mixed_data) | 0.520 (0.111) | {"0": 81, "1": 83, "2": 74, "3": 84} | 23.0 | 0.999 |
| B | kprototypes | 3 | 0.158 (gower_external_mixed_data) | 0.626 (0.251) | {"0": 121, "1": 97, "2": 104} | 30.1 | 0.996 |
| B | kprototypes | 5 | 0.177 (gower_external_mixed_data) | 0.658 (0.109) | {"0": 76, "1": 62, "2": 71, "3": 51, "4": 62} | 15.8 | 0.994 |
| A | kprototypes | 4 | 0.190 (gower_external_mixed_data) | 0.596 (0.154) | {"0": 76, "1": 94, "2": 84, "3": 68} | 21.1 | 0.995 |
| C | kprototypes | 4 | 0.141 (gower_external_mixed_data) | 0.454 (0.147) | {"0": 78, "1": 86, "2": 73, "3": 85} | 22.7 | 0.998 |
| C | kprototypes | 5 | 0.142 (gower_external_mixed_data) | 0.569 (0.144) | {"0": 78, "1": 72, "2": 69, "3": 55, "4": 48} | 14.9 | 0.990 |

## Danh sách ứng viên xem xét theo thuật toán và lựa chọn mô hình

Các ứng viên chỉ được xếp hạng trong cùng một thuật toán. Thứ hạng trong mỗi thuật toán là trung bình thứ hạng của silhouette, độ ổn định qua lấy mẫu con và entropy kích thước chuẩn hóa. Đây chỉ là công cụ hỗ trợ xem xét, không phải quy tắc chọn mô hình đã được kiểm định thống kê. Các cấu hình K-Means chỉ dùng numeric có không gian feature trùng nhau được gộp khi lập shortlist vì thêm feature nominal không làm thay đổi đầu vào của K-Means. Không chọn một thuật toán thắng cuộc chung vì các thuật toán dùng không gian khoảng cách khác nhau. Không áp đặt số cụm; các cụm nhỏ vẫn được giữ và báo cáo.

| Thuật toán | Ứng viên trong danh sách xem xét (cấu hình / k) |
|---|---|
| gower_agglomerative | B / k=2, A_gap / k=2 |
| kprototypes | A / k=2, C / k=2 |
| kmeans_numeric | A / k=2, A_gap / k=2 |

Chưa thể kết luận mô hình phân cụm nào là tốt nhất chỉ từ khảo sát cắt ngang này. Cần xem xét hồ sơ cụm, độ nhạy của độ ổn định/thứ hạng, tính hữu ích trong thực tế và tốt nhất là lặp lại trên mẫu mới. Vì vậy, báo cáo không khẳng định một thuật toán hay giá trị k nào là tốt nhất trong mọi trường hợp.

## Hồ sơ các cụm

`cluster_profiles.csv` có một dòng cho mỗi ứng viên và mỗi cụm, gồm kích thước/tỷ lệ cụm, trung bình và trung vị của biến numeric/ordinal, mode/phân phối của biến danh mục, và tỷ lệ lựa chọn của câu hỏi nhiều lựa chọn kèm mẫu số hợp lệ. Những biến không có trong cấu hình dùng để tạo cụm chỉ được sử dụng để mô tả sau phân cụm, không quyết định thành viên cụm.

## Độ ổn định và hạn chế

- Độ ổn định được đo bằng ARI trên từng mẫu con 80%, so với kết quả tham chiếu chỉ trên cùng các bản ghi đó. Đây là kiểm tra độ nhạy khi thay đổi mẫu, không phải kiểm định khả năng tổng quát hóa trên dữ liệu ngoài mẫu.
- Gower gán trọng số bằng nhau cho từng feature; bổ sung feature bối cảnh làm thay đổi cả thông tin và thành phần khoảng cách. So sánh giữa các cấu hình là phân tích độ nhạy.
- K-Prototypes có hàm mục tiêu khác Gower; silhouette của K-Prototypes được báo cáo trong không gian Gower chỉ như một chỉ số đánh giá tách biệt bên ngoài.
- K-Means không dùng biến nominal nên kết quả không tương đương trực tiếp với các phương pháp xử lý dữ liệu hỗn hợp.
- 322 câu trả lời tự khai báo theo mẫu thuận tiện là dữ liệu nhỏ và cắt ngang; chưa có căn cứ xem mẫu này đại diện cho người trẻ tại Việt Nam.
- Cụm chỉ mô tả mức độ tương đồng giữa câu trả lời khảo sát. Chúng không dự đoán việc bỏ tập trong tương lai và không chứng minh tác động nhân quả của gợi ý.
- 96 giá trị thiếu Q8/Q9 trùng với các nhóm lịch sử tập luyện quan sát được ở Stage 1B/EDA; logic bỏ qua câu hỏi của khảo sát gốc vẫn chưa được xác minh. Hai biến này không được dùng để tạo cụm.

## Artifact đã lưu và khả năng sử dụng sau này

- Nhãn cụm của các ứng viên: `clustering_results.csv`.
- Metrics của tất cả ứng viên và giá trị k: `clustering_metrics.csv`.
- Hồ sơ cụm: `cluster_profiles.csv`.
- Model đã khớp và preprocessing của các ứng viên: `artifacts/candidate_models.joblib`.
- Shortlist xem xét theo từng thuật toán: `artifacts/shortlisted_candidates.joblib`.
- Ánh xạ tiền xử lý và trạng thái: `artifacts/preprocessing_spec.json`.
- Agglomerative không có `predict` tích hợp; gán bản ghi mới theo medoid gần nhất chỉ là phương án xấp xỉ và cần được kiểm định. K-Means và K-Prototypes có lưu model/preprocessing để transform và predict.

## Các bước tiếp theo trước khi tích hợp vào sản phẩm

1. Xem lại hồ sơ cụm và xác định các mẫu hình có thể diễn giải, hữu ích; chưa nên đặt tên cụm quá sớm.
2. Chỉ chọn thuật toán/k sau khi đánh giá nhiều tiêu chí và độ ổn định; không chọn chỉ dựa trên silhouette.
3. Kiểm định trên mẫu khảo sát mới hoặc dữ liệu người dùng được thu thập về sau; xác định yêu cầu về quyền riêng tư, đồng thuận và khả năng thu thập feature.
4. Xác nhận cách gán cụm cho người dùng mới. Với Agglomerative, cần kiểm định phương án medoid gần nhất hoặc chọn thuật toán có khả năng predict thực sự.
5. Sau đó mới thiết kế hồ sơ cụm có version và thử tích hợp vào hệ thống gợi ý qua feature flag; giữ phân cụm tách biệt với dự đoán nguy cơ bỏ tập.
