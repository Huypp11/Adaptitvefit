# BẢN THUYẾT MINH SẢN PHẨM DỰ THI

## 1. Thông tin chung

- **Tên sản phẩm:** AdaptiveFit AI
- **Lĩnh vực:** Công nghệ thông tin, trí tuệ nhân tạo, sức khỏe số và hỗ trợ hình thành thói quen vận động
- **Loại sản phẩm:** Ứng dụng web gồm giao diện người dùng, máy chủ nghiệp vụ và dịch vụ phân tích AI
- **Đối tượng hướng đến:** Người muốn duy trì hoạt động thể chất nhưng cần kế hoạch linh hoạt theo thời gian, điều kiện tập luyện và trạng thái hằng ngày

## 2. Tóm tắt sản phẩm

AdaptiveFit AI là ứng dụng hỗ trợ người dùng xây dựng và duy trì thói quen tập luyện thông qua check-in hằng ngày, đánh giá nguy cơ bỏ lỡ buổi tập và gợi ý bài tập phù hợp. Thay vì đưa ra một lịch tập cố định, hệ thống kết hợp hồ sơ cá nhân, trạng thái do người dùng tự khai báo, lịch sử hoạt động và danh mục bài tập để điều chỉnh gợi ý theo từng ngày.

Sản phẩm sử dụng mô hình AI để ước tính nguy cơ người dùng không có buổi tập trong 7 ngày tiếp theo. Kết quả được trình bày cùng các yếu tố giải thích, sau đó được kết hợp với các quy tắc an toàn và bộ lọc bài tập. AI không tự quyết định mọi thứ: giới hạn cường độ, thời gian, thiết bị, địa điểm, mục tiêu và dấu hiệu mệt mỏi do người dùng khai báo vẫn được áp dụng trong quá trình tạo gợi ý.

Phiên bản hiện tại là sản phẩm đang phát triển và trình diễn. Mô hình hiện được huấn luyện bằng dữ liệu mô phỏng; sản phẩm chưa chứng minh hiệu quả bằng thử nghiệm người dùng thực tế và không phải công cụ chẩn đoán y khoa.

## 3. Vấn đề và nhu cầu thực tiễn

Nhiều người bắt đầu tập luyện với quyết tâm cao nhưng khó duy trì lâu dài. Một kế hoạch không phù hợp với lịch trình, thể lực, thiết bị sẵn có hoặc trạng thái trong ngày có thể khiến người tập bỏ buổi và dần từ bỏ mục tiêu.

Các kế hoạch cố định thường chưa giải quyết tốt các tình huống như:

- Người dùng có ít thời gian hơn dự kiến trong ngày.
- Mức năng lượng, giấc ngủ hoặc căng thẳng thay đổi.
- Người dùng chỉ có thể tập tại nhà hoặc không có đủ thiết bị.
- Bài tập được đề xuất quá khó, quá nặng hoặc không phù hợp với mục tiêu.
- Người dùng bỏ dở buổi tập nhưng hệ thống không ghi nhận nguyên nhân để điều chỉnh trải nghiệm sau đó.

AdaptiveFit AI hướng đến hỗ trợ việc duy trì thói quen bằng các gợi ý linh hoạt, có giải thích và có thể ghi nhận phản hồi, thay vì khẳng định rằng một kế hoạch duy nhất phù hợp với tất cả mọi người.

## 4. Ý tưởng và mục tiêu

Ý tưởng cốt lõi là tạo một vòng lặp hỗ trợ tập luyện:

**Khai báo trạng thái → đánh giá nguy cơ → nhận gợi ý thích ứng → thực hiện hoặc phản hồi → theo dõi tiến trình.**

Sản phẩm đặt ra các mục tiêu sau:

1. Giúp người dùng ghi nhận trạng thái và hoạt động tập luyện tại một nơi.
2. Phát hiện sớm những ngày người dùng có thể cần một kế hoạch nhẹ hơn hoặc dễ thực hiện hơn.
3. Đưa ra bài tập có xét đến điều kiện và giới hạn cá nhân.
4. Giải thích những yếu tố ảnh hưởng đến đánh giá nguy cơ, tăng tính minh bạch của AI.
5. Ghi nhận kết quả và phản hồi để đánh giá chất lượng gợi ý trong tương lai.
6. Chỉ sử dụng dữ liệu cho phân tích hoặc huấn luyện theo quy trình có kiểm soát và sự đồng ý phù hợp.

## 5. Đối tượng sử dụng

### Người dùng cá nhân

Người muốn bắt đầu hoặc duy trì thói quen vận động, đặc biệt là người cần gợi ý linh hoạt theo quỹ thời gian, thiết bị, địa điểm và trạng thái tự khai báo.

### Quản trị viên hoặc người đánh giá thử nghiệm

Người cần quản lý danh mục bài tập, quy tắc can thiệp và xem các chỉ số tổng hợp về tương tác với gợi ý. Các dữ liệu phân tích trong phiên bản hiện tại nhằm hỗ trợ đánh giá sản phẩm, không được xem là bằng chứng lâm sàng hoặc kết luận nhân quả.

## 6. Chức năng chính

### 6.1. Tài khoản và hồ sơ cá nhân

Người dùng đăng nhập, khai báo hồ sơ và thiết lập các điều kiện tập luyện như mục tiêu, trình độ, địa điểm, thiết bị, thời lượng có thể dành cho buổi tập và cường độ tối đa mong muốn. Hồ sơ là một trong các nguồn thông tin để lọc và xếp hạng bài tập.

### 6.2. Check-in hằng ngày

Người dùng ghi nhận những thông tin như giấc ngủ, mức căng thẳng, năng lượng, tâm trạng, động lực, thời gian rảnh và hoạt động đã thực hiện trong ngày. Hệ thống kết hợp dữ liệu này với lịch sử tập luyện gần đây để tạo ngữ cảnh cho việc đánh giá và gợi ý.

### 6.3. Đánh giá nguy cơ bỏ lỡ buổi tập

AI ước tính nguy cơ không có buổi tập trong 7 ngày tiếp theo dựa trên các đặc trưng của check-in và lịch sử hoạt động. Ứng dụng hiển thị mức nguy cơ và phần giải thích các yếu tố đóng góp. Phần giải thích giúp người dùng hiểu những dữ liệu nào liên quan đến dự đoán; đây không phải lời khuyên y tế và không chứng minh quan hệ nhân quả.

Nếu dịch vụ AI không khả dụng, hệ thống có thể dùng phương án dự phòng dựa trên lịch sử. Nếu người dùng chưa check-in trong ngày, ứng dụng có thể tạo gợi ý thông thường từ hồ sơ và lịch sử gần đây, thay vì giả định rằng đã có dữ liệu check-in.

### 6.4. Gợi ý bài tập thích ứng

Hệ thống kết hợp trạng thái người dùng, mức nguy cơ, quy tắc can thiệp và danh mục bài tập. Bài tập được lọc và xếp hạng theo các yếu tố như mục tiêu, địa điểm, thiết bị, thời gian, độ khó, cường độ và khả năng phù hợp với phục hồi. Giao diện đưa ra một số lựa chọn để người dùng chủ động cân nhắc.

Khi có dấu hiệu nguy cơ hoặc mệt mỏi phù hợp, hệ thống có thể ưu tiên giảm tải hoặc gợi ý phục hồi theo các quy tắc đã định. Gợi ý không thay thế quyền quyết định của người dùng.

### 6.5. Ghi nhận buổi tập và phản hồi

Người dùng có thể bắt đầu buổi tập từ gợi ý, theo dõi trạng thái buổi tập, hoàn thành hoặc hủy buổi. Buổi tập có thể được liên kết với gợi ý đã tạo để phục vụ truy vết và đánh giá.

Hệ thống có cổng kiểm tra chất lượng buổi tập: buổi tập quá ngắn (dưới 3 phút) hoặc có thời lượng thực hiện dưới 30% tổng thời lượng dự kiến được đánh dấu không hợp lệ để tránh tính nhầm là một buổi hoàn thành. Khi hủy, người dùng có thể chọn lý do nhanh như mệt, thiếu thiết bị hoặc có việc đột xuất. Người dùng cũng có thể gửi phản hồi về gợi ý.

### 6.6. Lịch sử, tiến trình và công cụ quản trị

Người dùng xem lịch sử hoạt động, thông tin rủi ro và các gợi ý trước đó. Quản trị viên có thể quản lý bài tập, quy tắc và xem số liệu tổng hợp phục vụ đánh giá. Dữ liệu tổng hợp chỉ có ý nghĩa khi quy trình thu thập, consent, cửa sổ quan sát và chất lượng dữ liệu được bảo đảm.

## 7. Quy trình hoạt động

1. Người dùng đăng nhập và hoàn thiện hồ sơ.
2. Người dùng thực hiện check-in hoặc yêu cầu gợi ý dựa trên hồ sơ khi chưa check-in.
3. Backend lấy trạng thái hiện tại và lịch sử hoạt động cần thiết.
4. Khi có đủ dữ liệu và dịch vụ AI sẵn sàng, backend gửi đặc trưng cần thiết đến dịch vụ AI để nhận điểm nguy cơ và thông tin giải thích.
5. Bộ quy tắc chọn chiến lược phù hợp; bộ xếp hạng lọc bài tập theo hồ sơ, trạng thái và các giới hạn đã đặt.
6. Gợi ý được lưu thành bản ghi để có thể dùng lại và truy vết với cùng check-in.
7. Người dùng chọn bài tập, thực hiện buổi tập và gửi phản hồi nếu muốn.
8. Khi được cho phép bằng consent tương ứng, hệ thống ghi nhận sự kiện phục vụ đánh giá tổng hợp. Dữ liệu này không tự động làm thay đổi mô hình.

## 8. Công nghệ và kiến trúc

Sản phẩm được tổ chức thành các thành phần độc lập:

- **Frontend:** React, TypeScript và Vite; cung cấp giao diện check-in, gợi ý, tập luyện, lịch sử và quản trị.
- **Backend:** Java 21, Spring Boot và Maven; cung cấp REST API, xác thực, xử lý nghiệp vụ và kết nối cơ sở dữ liệu.
- **Cơ sở dữ liệu:** MySQL; lưu tài khoản, hồ sơ, check-in, bài tập, buổi tập, phản hồi và các bản ghi liên quan.
- **Quản lý thay đổi cơ sở dữ liệu:** Flyway; quản lý schema bằng các migration có phiên bản.
- **AI service:** Python và FastAPI; nhận dữ liệu dự đoán từ backend, trả về đánh giá nguy cơ và giải thích.
- **Mô hình và đánh giá:** quy trình ML có các mô hình phân loại như Logistic Regression, Random Forest và XGBoost, so sánh với baseline, kiểm tra dữ liệu và tách tập đánh giá theo người dùng.

Backend là lớp điều phối giữa giao diện, cơ sở dữ liệu và dịch vụ AI. Các quy tắc nghiệp vụ và giới hạn an toàn được duy trì ở backend thay vì phụ thuộc hoàn toàn vào đầu ra của mô hình.

## 9. Điểm đáng chú ý và tính sáng tạo

1. **Cá nhân hóa theo trạng thái trong ngày:** gợi ý không chỉ dựa trên mục tiêu dài hạn mà còn xét đến check-in và lịch sử hoạt động.
2. **Kết hợp AI với quy tắc có thể kiểm soát:** mô hình ước tính nguy cơ, còn bộ quy tắc và bộ lọc áp dụng giới hạn về cường độ, thời gian và điều kiện tập luyện.
3. **Có giải thích cho dự đoán:** cung cấp các yếu tố ảnh hưởng để kết quả không chỉ là một con số khó hiểu.
4. **Theo dõi vòng đời gợi ý:** lưu snapshot gợi ý, liên kết với buổi tập và nhận phản hồi để hỗ trợ đánh giá.
5. **Đặt chất lượng dữ liệu và quyền riêng tư vào thiết kế:** tách dữ liệu vận hành khỏi telemetry, có cơ chế đồng ý riêng cho việc thu thập sự kiện, và không coi thiếu dữ liệu là thất bại của người dùng.
6. **Không tự động cập nhật mô hình:** dữ liệu phản hồi không làm mô hình thay đổi ngay; việc huấn luyện và phát hành phiên bản mới cần được đánh giá riêng.

## 10. An toàn, quyền riêng tư và giới hạn

- AdaptiveFit AI hỗ trợ duy trì hoạt động thể chất, không chẩn đoán bệnh, đánh giá tình trạng sức khỏe lâm sàng hoặc thay thế chuyên gia y tế.
- Người dùng có thể chọn không làm theo gợi ý. Hệ thống không nên khuyến khích vượt quá giới hạn cá nhân đã khai báo.
- Dữ liệu mô phỏng chỉ phục vụ phát triển và trình diễn; không được mô tả thành dữ liệu thực nghiệm chứng minh hiệu quả.
- Telemetry về gợi ý được thiết kế theo hướng opt-in, có phiên bản chính sách và cho phép rút lại consent. Consent telemetry không đồng nghĩa với consent dùng dữ liệu để huấn luyện AI.
- Đánh giá đầu ra phải xét đến việc người dùng có đủ thời gian quan sát hay không; thiếu phản hồi hoặc thiếu dữ liệu không được tự động gán là kết quả xấu.
- Chưa có cơ sở để khẳng định sản phẩm làm giảm tỷ lệ bỏ tập trong thực tế. Cần thử nghiệm có quy trình và người tham gia phù hợp trước khi đưa ra kết luận.

## 11. Hiện trạng phát triển

Trong mã nguồn hiện có, các thành phần chính gồm giao diện web, backend nghiệp vụ, cơ sở dữ liệu, dịch vụ AI, quy trình đánh giá mô hình, gợi ý bài tập, ghi nhận buổi tập và phản hồi. Có mã nguồn cho consent và recommendation telemetry ở Phase 10.

Tuy nhiên, cần phân biệt **mã nguồn đã được xây dựng** với **tính năng đã được xác nhận hoạt động trên môi trường triển khai**. Migration telemetry V12 chưa được xác nhận đã áp dụng lên cơ sở dữ liệu cụ thể; chưa có pilot người dùng thực tế được xác nhận; chưa có bộ dữ liệu thực đã kiểm duyệt để huấn luyện. Vì vậy, phiên bản phù hợp hiện nay là demo hoặc thử nghiệm có kiểm soát, không phải sản phẩm đã được chứng minh hiệu quả ngoài thực tế.

## 12. Hướng phát triển

1. Hoàn thiện kiểm thử tích hợp trên cơ sở dữ liệu local hoặc staging dùng riêng, có sao lưu và xác nhận trước khi chạy migration.
2. Tổ chức pilot nhỏ với thông tin giải thích rõ ràng, consent phù hợp và quy trình bảo vệ dữ liệu.
3. Chuẩn hóa snapshot, kết quả quan sát và nhãn; chỉ tạo mẫu huấn luyện khi có đủ cửa sổ theo dõi, consent huấn luyện và dữ liệu chất lượng.
4. So sánh mô hình mới với baseline bằng cách tách tập theo người dùng, báo cáo đầy đủ chỉ số và kiểm tra sai lệch.
5. Chỉ phát hành mô hình mới sau khi có người đánh giá, quy trình rollback và bằng chứng phù hợp; không bật tự huấn luyện trực tiếp từ dữ liệu người dùng.
6. Tiếp tục cải thiện khả năng truy cập, trải nghiệm check-in, giải thích gợi ý và công cụ quản trị.

## 13. Kịch bản trình diễn đề xuất

1. Đăng nhập bằng tài khoản demo và giới thiệu hồ sơ tập luyện.
2. Thực hiện một check-in mẫu với thời gian, năng lượng và trạng thái trong ngày.
3. Mở màn hình nguy cơ, trình bày mức đánh giá cùng các yếu tố giải thích; nói rõ đây là mô hình demo.
4. Mở gợi ý hôm nay, chỉ ra cách các lựa chọn phù hợp với thiết bị, địa điểm, thời lượng và cường độ đã khai báo.
5. Bắt đầu một buổi tập từ gợi ý, sau đó kết thúc hợp lệ hoặc minh họa cách hệ thống xử lý buổi tập bị hủy.
6. Xem lịch sử hoặc gửi phản hồi.
7. Nếu trình diễn trang phân tích, giải thích rằng đây là số liệu quan sát và chỉ ghi nhận khi người dùng đã đồng ý theo chính sách tương ứng.

## 14. Kết luận

AdaptiveFit AI hướng đến biến việc duy trì tập luyện thành một quá trình có thể thích ứng: hiểu trạng thái hiện tại, đề xuất lựa chọn phù hợp, ghi nhận trải nghiệm và cải thiện sản phẩm bằng đánh giá có kiểm soát. Giá trị của sản phẩm không chỉ nằm ở việc sử dụng AI, mà còn ở cách kết hợp AI với quy tắc minh bạch, dữ liệu có chất lượng, quyền lựa chọn của người dùng và quy trình đánh giá thận trọng.

Trong giai đoạn hiện tại, sản phẩm chứng minh kiến trúc và luồng chức năng cho một bản demo. Bước tiếp theo là kiểm thử an toàn trên môi trường phù hợp và thu thập bằng chứng từ pilot trước khi khẳng định hiệu quả hoặc sử dụng dữ liệu thực để huấn luyện mô hình.
