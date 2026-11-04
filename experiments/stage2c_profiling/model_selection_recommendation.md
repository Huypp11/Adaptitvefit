# Stage 2C - Khuyến nghị lựa chọn mô hình

## Kết luận

**Chưa đủ căn cứ chọn mô hình để tích hợp vào AdaptiveFit AI.** Có thể tiếp tục đánh giá ngoại tuyến cả ba ứng viên, nhưng chưa nên đưa nhãn cụm vào recommendation engine hoặc dùng như phân loại người dùng ổn định.

## Trade-off của từng ứng viên

- **Gower + Agglomerative (B, k=2):** silhouette 0.301 (gower; không so trực tiếp với metric khác), ARI 1.000 ± 0.000; kích thước cụm [254, 68]; tỷ lệ `not_currently_training` theo cụm [0.0, 100.0]%.
- **K-Prototypes (A, k=2):** silhouette 0.270 (gower_external_mixed_data; không so trực tiếp với metric khác), ARI 0.890 ± 0.117; kích thước cụm [157, 165]; tỷ lệ `not_currently_training` theo cụm [0.0, 41.2]%.
- **K-Means numeric baseline (A, k=2):** silhouette 0.223 (euclidean_numeric_scaled; không so trực tiếp với metric khác), ARI 0.968 ± 0.041; kích thước cụm [182, 140]; tỷ lệ `not_currently_training` theo cụm [0.0, 48.6]%.

Gower + Agglomerative B ổn định nhất trong phép lấy mẫu con (ARI=1.000) và cung cấp địa điểm/lịch sử tập làm feature, nhưng một cụm 68 người tách chính xác nhóm không tập hiện tại. Vì trạng thái này vốn là input, kết quả có thể hữu ích cho luồng người mới quay lại nhưng không chứng minh một phân nhóm nhu cầu rộng hơn; Agglomerative cũng không có `predict` gốc, nên gán medoid là xấp xỉ.

K-Means A cho kích thước cụm cân bằng hơn (182/140) và ARI cao (0.968), nhưng bỏ các danh mục nominal; silhouette phải được hiểu trong không gian Euclidean numeric/ordinal. Nó có thể là baseline đơn giản để kiểm tra thêm, không đủ để chọn làm model sản phẩm.

K-Prototypes A giữ dữ liệu hỗn hợp và cho hai cụm gần cân bằng (157/165), nhưng độ ổn định thấp hơn và dao động lớn hơn (ARI 0.890 ± 0.117). Silhouette báo cáo trong khoảng cách Gower chỉ là chẩn đoán bên ngoài, không phải objective của K-Prototypes.

Không so sánh trực tiếp các silhouette giữa ba phương pháp. Cả ba dùng `usual_session_duration` và có sự liên hệ đầu vào giữa nhãn cụm với trạng thái tập luyện; profiling không phải kiểm định độc lập. Cả ba đều chưa được kiểm chứng trên mẫu mới, chưa đánh giá độ hữu ích cho người dùng, và không phải nhãn dropout.

## Kiểm tra cần làm trước quyết định triển khai

1. Kiểm định trên mẫu khảo sát mới đủ lớn, giữ nguyên quy tắc làm sạch và thứ bậc; đo mức ổn định nhãn giữa các mẫu độc lập.
2. Làm kiểm tra độ nhạy: nhiều seed/lần khởi tạo cho K-Prototypes và K-Means; nhiều lần bootstrap/subsample cho cả ba; kiểm tra tác động khi bỏ `usual_session_duration` để xem phân cụm có còn chỉ phản ánh đang tập/không tập.
3. Xác minh logic skip Q8/Q9, mẫu số missing và ý nghĩa của các lựa chọn trước khi dùng lịch sử ngừng tập trong profiling hoặc cá nhân hóa.
4. Định nghĩa trước tiêu chí sản phẩm: các gợi ý khác nhau cụ thể thế nào, có sẵn dữ liệu đầu vào lúc chạy hay không, và thử nghiệm người dùng có đo kết quả phù hợp hay không.
5. Nếu vẫn cân nhắc Agglomerative, kiểm định xấp xỉ nearest-medoid trên dữ liệu mới; không coi đây là `predict` tương đương thuật toán gốc.
6. Chỉ sau các bước trên mới đề xuất một thử nghiệm integration có version/feature flag và cơ chế quay lui. Không chỉnh sửa API, backend, frontend, recommendation engine hoặc model registry trong Stage 2C.

## Phạm vi dữ liệu và bảo toàn artifact

- Stage 2C chỉ đọc đầu vào và artifact Stage 2B; các file Stage 2B không bị ghi đè.
- Không tạo dữ liệu giả, không điền missing, không tạo nhãn dropout và không đưa ra kết luận nhân quả.
