# Tóm tắt Phase 9A

## Hoàn thành

- Thêm lựa chọn cường độ tối đa `LOW|MEDIUM|HIGH` vào hồ sơ và Recommendation Engine.
- Giữ tương thích: hồ sơ/request cũ hoặc null mặc định MEDIUM.
- Lọc cứng bài tập theo địa điểm, dụng cụ, thời lượng, năng lượng và cường độ.
- Điều chỉnh điều kiện `CHALLENGE` để xét stress, giấc ngủ, mood và motivation.
- Thêm ba bài GYM qua migration V11 mới.
- Thêm 21 scenario unit test và kiểm thử hợp đồng profile.

## Kiểm chứng thực tế

- Backend: `mvn test` — 24 tests passed.
- Frontend: `npm run build` — thành công; có cảnh báo bundle lớn hơn 500 kB.
- AI service: `python -m pytest` — 21 tests passed; service không bị thay đổi trong Phase 9A.
- Scenario CSV được tạo từ test inputs để kiểm tra logic; không đại diện người dùng thực và không đo hiệu quả sản phẩm.

## Chưa hoàn thành / cần làm trước triển khai

- Chưa chạy migration trên database test riêng hoặc database production.
- Chưa chạy E2E/integration đầy đủ qua Frontend, Backend, AI Service và database.
- Recommendation snapshot hiện có trong ngày có thể không được tái tạo ngay khi hồ sơ vừa thay đổi.
- Cần kiểm tra migration trên database clone, sau đó chạy smoke test API và xác minh catalog GYM.

**Kết luận:** code Phase 9A đã được triển khai và kiểm thử unit/build; chưa được xác nhận triển khai tích hợp cho tới khi migration được kiểm chứng trên môi trường database an toàn.
