# Báo cáo migration Phase 9A

## Migration mới

`backend/src/main/resources/db/migration/V11__recommendation_constraints.sql`

Migration chỉ bổ sung thay đổi:

- Thêm cột nullable `user_profiles.max_exercise_intensity`.
- Cập nhật điều kiện rule `CHALLENGE`; không sửa các migration đã phát hành.
- Thêm ba bài GYM và exercise tags bằng truy vấn có kiểm tra tên để tránh tạo bản ghi trùng.
- ID bài tập để database tự sinh, tránh xung đột ID cố định với catalog đã phát sinh thêm dữ liệu.

## Tương thích dữ liệu cũ

- Dữ liệu hồ sơ hiện hữu được giữ nguyên; cột mới nullable nên migration không phải gán giá trị giả cho người dùng cũ.
- Response hồ sơ và `ProfileContext` ánh xạ null sang `MEDIUM`.
- Tất cả request ghi hồ sơ mới thiếu field cũng nhận mặc định `MEDIUM`.
- Không thay đổi schema migration cũ, dữ liệu workout, model artifact hoặc registry.

## Trạng thái kiểm chứng

Migration đã được rà soát dựa trên schema ở `V1__init_schema.sql`, các migration seed exercise và migration tạo rule trước đó. Chưa chạy migration vì chưa xác nhận database đang có là database test biệt lập; không kết nối hoặc migrate database không rõ môi trường.

Trước khi triển khai:

1. Sao lưu database và xác nhận đúng môi trường.
2. Chạy Flyway trên bản database test/clone.
3. Kiểm tra `user_profiles.max_exercise_intensity`, rule `CHALLENGE`, số bài GYM mới và tags.
4. Gọi API profile với request không có field mới và xác nhận giá trị trả về là `MEDIUM`.
5. Gọi recommendation ở cả HOME, GYM và OUTDOOR, xác nhận không trả bài sai location và không vượt intensity cap.
