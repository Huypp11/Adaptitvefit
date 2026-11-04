# Stage 2D - Quyết định lựa chọn mô hình

## Quyết định: C. Clustering chưa sẵn sàng tích hợp

Không chọn ứng viên nào để đưa vào Recommendation Engine hoặc Stage 3 pilot ở thời điểm này. Kết quả hiện có là kiểm tra độ nhạy nội bộ trên cùng 322 câu trả lời, không phải xác thực ngoài mẫu.

### Căn cứ

- **Gower + Agglomerative B:** ARI trung bình 0.617 (SD 0.091) so với nhãn tham chiếu Stage 2B trên test, ARI cặp trung bình 0.869 trên các split có đủ overlap. Gán được qua nearest-medoid, nhưng đây là xấp xỉ, không phải `predict` gốc. Cụm trạng thái không tập rõ nhưng trạng thái này vốn là input.
- **K-Prototypes A:** ARI tham chiếu 0.790 (SD 0.119), ARI cặp trung bình 0.769; các cụm gần cân bằng và có `predict` với preprocessing fit trên train. Độ nhất quán thay đổi theo split và không có xác suất dự đoán chuẩn.
- **K-Means numeric A:** ARI tham chiếu 0.953 (SD 0.035), ARI cặp trung bình 0.967; có `predict` đơn giản, nhưng chỉ dùng numeric/ordinal. Khoảng cách tới tâm không phải xác suất. Chưa chứng minh utility tăng thêm so với check-in và quy tắc trực tiếp.
- Silhouette của ba thuật toán thuộc các không gian metric khác nhau nên không dùng để xếp hạng chéo.
- Chưa có dữ liệu mới, outcome theo dõi hoặc thử nghiệm người dùng. Không có cơ sở cho clustering thành nhãn dropout hay nhóm nhận cùng một bài tập.

### Điều kiện trước khi xem xét Stage 3

1. Kiểm chứng trên mẫu khảo sát mới hoặc dữ liệu theo dõi được thu thập có consent; không dùng cùng 322 người làm bằng chứng ngoài mẫu.
2. Lặp nhiều seed/khởi tạo, kiểm tra profile drift và phân tích độ nhạy bỏ `usual_session_duration` để xem có còn cấu trúc ngoài việc tách trạng thái tập/không tập.
3. Định nghĩa chính sách input missing/unknown; không impute giá trị tùy tiện và không nhận category ordinal chưa xác minh.
4. So sánh offline với baseline dùng check-in, lịch sử và rule-based recommendation; định nghĩa trước tiêu chí utility và trải nghiệm người dùng.
5. Đánh giá an toàn riêng cho nội dung bài tập; không suy luận an toàn/y tế từ cụm hoặc thang tự khai stress/năng lượng/tâm trạng.
6. Nếu có evidence tăng giá trị, mới thiết kế pilot có kiểm soát, version, monitoring và rollback. Không tích hợp trực tiếp vào model dropout hoặc recommendation engine trong Stage 2D.

Chi tiết phương pháp, kết quả từng split, ca assignment và giới hạn nằm trong `stage2d_validation_report.md`.
