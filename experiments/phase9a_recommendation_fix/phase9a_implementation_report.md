# Báo cáo triển khai Phase 9A — Recommendation Engine

## Mục tiêu

Khắc phục các điểm đã xác nhận trong audit: cường độ tối đa chưa thể hiện được theo lựa chọn của người dùng, location chưa được áp dụng như điều kiện lọc, catalog thiếu bài GYM và rule `CHALLENGE` chưa xét đầy đủ trạng thái cá nhân. Thay đổi chỉ mở rộng hành vi Recommendation Engine; không thay mô hình dropout, model registry hay kiến trúc API hiện có.

## Thay đổi đã thực hiện

1. **Giới hạn cường độ trong hồ sơ**
   - Bổ sung `maxExerciseIntensity` với các giá trị `LOW`, `MEDIUM`, `HIGH` vào request/response hồ sơ, entity và UI.
   - Request cũ hoặc giá trị null mặc định thành `MEDIUM`; hồ sơ DB cũ đang null cũng được ánh xạ thành `MEDIUM`.
   - Recommendation Engine kết hợp giới hạn người dùng với giới hạn của strategy, dùng mức nghiêm ngặt hơn. Strategy có `minIntensity` vượt quá giới hạn của người dùng sẽ bị bỏ qua và có giải thích.

2. **Lọc cứng theo địa điểm**
   - Chỉ giữ bài có location trùng với hồ sơ hoặc `ANY`.
   - Nếu không còn bài phù hợp, trả danh sách rỗng cùng thông báo giải thích; không fallback sang bài ở địa điểm khác.

3. **Rule và catalog**
   - Migration V11 cập nhật điều kiện `CHALLENGE`: stress dưới 4, ngủ ít nhất 6 giờ, mood và motivation ít nhất 3, cùng các điều kiện trạng thái trước đó.
   - Migration bổ sung ba bài tập GYM không cần dụng cụ hoặc dùng `stable_step`, và tag tương ứng.
   - Bản ghi exercise mới dùng ID tự tăng; truy vấn theo tên tránh thêm trùng khi chạy trên catalog đã có bài cùng tên.

4. **Tương thích frontend/API**
   - UI hồ sơ có selector giới hạn cường độ và giá trị mặc định MEDIUM.
   - Giá trị nhận từ select được kiểm tra theo danh sách hợp lệ; TypeScript build không cần cast không an toàn.

## Kiểm thử và kết quả

| Kiểm tra | Kết quả |
|---|---|
| Backend `mvn test` | Đạt: 24 tests, 0 failures, 0 errors |
| Scenario Recommendation Engine | Đạt: 21 kịch bản được xuất trong CSV |
| Profile intensity contract | Đạt: 2 tests cho default, tương thích constructor cũ và validation |
| Frontend `npm run build` | Đạt: TypeScript và Vite build thành công |
| AI service `python -m pytest` | Đạt trước chỉnh sửa cuối: 21 tests; Phase 9A không thay đổi AI service |
| Migration trên DB riêng | Chưa chạy; chưa xác nhận database hiện tại là môi trường test riêng |

Các scenario unit test dùng `UserState`, hồ sơ và catalog được dựng trong test để xác minh các ràng buộc; chúng **không phải dữ liệu người dùng thực tế**, không tạo nhãn dropout và không chứng minh hiệu quả thực tế của gợi ý.

## Giới hạn còn lại

- Chưa xác minh migration bằng Flyway trên database test biệt lập; cần backup và kiểm thử migration trước khi triển khai.
- Chưa chạy kiểm thử E2E Frontend → Backend → MySQL/AI Service do chưa có môi trường tích hợp được xác nhận an toàn.
- API hiện tái sử dụng recommendation snapshot đã tạo cho check-in trong ngày. Thay đổi hồ sơ giữa ngày có thể chưa phản ánh vào snapshot cho tới khi có luồng tạo recommendation mới; đây là giới hạn hiện hữu, chưa thay đổi semantics snapshot trong Phase 9A.
- Các rule và lựa chọn cường độ là ràng buộc sản phẩm, không phải đánh giá y tế hoặc bảo đảm an toàn tập luyện.
- Build frontend còn cảnh báo bundle JavaScript lớn hơn 500 kB; build vẫn thành công và cảnh báo này không phát sinh từ thay đổi chức năng.

## Kết luận

Thay đổi mã và unit tests Phase 9A đã hoàn tất. Chưa thể coi migration hoặc luồng tích hợp database đã được kiểm chứng cho tới khi V11 được chạy trên database test tách biệt và kiểm tra lại dữ liệu/API sau migration.
