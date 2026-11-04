# AdaptiveFit AI - Stage 2D: Kiểm chứng độ bền vững và khả năng gán cụm

## Tóm tắt và quyết định

**Quyết định: C. Chưa sẵn sàng tích hợp clustering vào Recommendation Engine.** Ba ứng viên có thể tiếp tục được đánh giá ngoại tuyến. Hiện chưa có dữ liệu khảo sát mới hay bằng chứng sản phẩm cho thấy cụm cung cấp giá trị tăng thêm so với check-in, lịch sử và rule-based recommendation.

Đây là kiểm tra độ nhạy nội bộ trên cùng 322 người khảo sát, bằng các phép chia train/test lặp lại. Không được diễn giải là xác thực ngoài mẫu hoặc bằng chứng cho toàn bộ người trẻ Việt Nam.

## Dữ liệu, artifact và tái lập

- Dữ liệu sạch: 322 dòng × 19 cột; Feature Dictionary có 19 biến.
- Đọc trực tiếp `stage2b_clustering/clustering_results.csv`, `clustering_metrics.csv`, `cluster_profiles.csv`, model bundles đã lưu, mã preprocessing Stage 2B; đọc thêm kết quả profiling và khuyến nghị Stage 2C.
- Đã xác nhận nhãn assignment CSV khớp nguyên thứ tự dòng với nhãn đã lưu trong bundle và `estimator.labels_` cho ba ứng viên.
- Missing gốc: `last_break_duration` 96/322 và `stopping_reasons` 96/322; không điền giá trị. Cả hai ngoài feature của ba ứng viên.
- Phiên bản Python: 3.14.2; thư viện: `{"numpy": "2.5.3", "pandas": "3.0.6", "scipy": "1.18.1", "scikit-learn": "1.9.1", "matplotlib": "3.11.2", "joblib": "1.6.0", "kmodes": "0.12.2"}`.
- Thiết kế lặp: train fractions=[0.7, 0.8], seeds=[13, 29, 42, 57, 71]; mỗi ứng viên có 10 lần fit; tổng cộng 30 lần fit.
- Mỗi lần chia: estimator, scaler và mọi preprocessing chỉ được fit trên train; tập test chỉ được transform và gán nhãn. K-Prototypes có thêm 5 lần khởi tạo nội bộ cho mỗi fit.

## Phương pháp kiểm chứng

- Mỗi ứng viên được fit từ đầu theo đúng feature/spec Stage 2B trên split train ngẫu nhiên 70/30 hoặc 80/20. Không dùng nhãn dropout.
- Gower + Agglomerative: fit ma trận Gower chỉ trên train; tính medoid của mỗi cụm từ train; gán test/người mới vào medoid gần nhất. Đây là mở rộng xấp xỉ, không phải `predict` nguyên bản.
- K-Prototypes: fit preprocessor và model trên train; dùng chính preprocessing đó và `KPrototypes.predict` cho test.
- K-Means: fit ordinal/numeric preprocessing, StandardScaler và KMeans trên train; tái sử dụng để transform/predict test.
- Silhouette trên test chỉ được tính trong metric phù hợp cho từng phương pháp; K-Prototypes dùng Gower như chẩn đoán ngoài objective. Không xếp hạng silhouette giữa các không gian khác nhau.
- ARI/NMI so với Stage 2B là mức tương đồng với phân cụm tham chiếu đã fit trên toàn bộ cùng khảo sát, không phải nhãn đúng. ARI/NMI giữa các lần chạy được tính trên phần cá nhân cùng có mặt trong cả hai test set.
- Profile drift là trung bình khoảng cách biến thiên tổng (TVD) của phân phối biên từng feature giữa cluster train đã căn chỉnh và cluster tham chiếu Stage 2B. Đây cũng là so sánh nội bộ, không phải validation ngoài mẫu.

## Kết quả độ nhạy

| Ứng viên | Số lần fit | ARI TB ± ĐLC so với tham chiếu Stage 2B trên test | NMI TB so với tham chiếu | Cặp overlap hợp lệ | ARI cặp (TB) | NMI cặp (TB) | Profile TVD TB | Silhouette test TB* |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Gower + Agglomerative | 10 | 0.617 ± 0.091 | 0.560 | 24/45 | 0.869 | 0.822 | 0.029 | 0.258 |
| K-Prototypes | 10 | 0.790 ± 0.119 | 0.733 | 24/45 | 0.769 | 0.732 | 0.062 | 0.281 |
| K-Means numeric baseline | 10 | 0.953 ± 0.035 | 0.924 | 24/45 | 0.967 | 0.957 | 0.037 | 0.232 |

*Silhouette test được tổng hợp chỉ trong từng thuật toán/không gian metric; không dùng để so sánh chéo thuật toán. Xem từng split, cluster sizes, profile TVD và ghi chú tính được trong `stability_validation.csv`.

ARI/NMI cặp giữa các split đo độ tương đồng trên phần bản ghi test giao nhau; các cặp có dưới 20 người giao nhau không được tính và được ghi NaN trong CSV. Các split dùng cùng nguồn 322 người và có nhiều quan sát trùng lặp. Kết quả chỉ phản ánh độ nhạy nội bộ, không chứng minh cấu trúc sẽ lặp lại ở người dùng mới ngoài khảo sát.

## Khả năng gán người dùng mới

Các ca kiểm thử hợp lệ được chọn từ các dòng khảo sát thật trong test partition của split train=80%, seed=42. Không có nhãn thật cho các ca; cluster được gán chỉ là output estimator. Các ca thiếu feature bắt buộc và category ordinal chưa biết là fault-injection trên một dòng khảo sát thật để kiểm tra xử lý lỗi, không phải dữ liệu người dùng giả hay nhãn thật. Ca category nominal mới cũng là fault-injection.

- Missing trong feature bắt buộc: từ chối gán, không điền tự động.
- Unknown ordinal: từ chối gán vì thứ tự không được xác định.
- Unknown nominal trong Gower B: được tính như mismatch và trả nearest-medoid, nhưng giá trị chưa từng thấy làm khoảng cách khó diễn giải; không có hiệu chuẩn confidence.
- Unknown nominal ở cấu hình A của K-Prototypes/K-Means: địa điểm không phải input nên bị bỏ qua; phép thử không chứng minh khả năng xử lý unknown của feature nominal không có trong mô hình.
- Q8/Q9 missing thực tế: có thể gán vì không nằm trong feature của ba ứng viên. Đây không phải xử lý missing-aware cho các feature đang dùng.
- K-Means cung cấp khoảng cách tới centroid gần nhất và margin; K-Means không cung cấp xác suất đã hiệu chuẩn. K-Prototypes không có probability/confidence chuẩn trong API predict được dùng ở đây. Gower cung cấp khoảng cách/margin tới medoid, không phải xác suất.

`new_user_assignment_tests.csv` ghi từng đầu vào feature, trạng thái, nhãn gán nếu có, khoảng cách/margin nếu có và lý do từ chối/giới hạn. Không diễn giải nhãn từ một trường hợp đơn lẻ thành nhóm người dùng ổn định.

## Giá trị ứng dụng AdaptiveFit

Xem `product_usefulness_comparison.csv` cho từng tiêu chí, bằng chứng và giới hạn.

- Cụm chủ yếu tổng hợp lại hành vi, thời lượng, mục tiêu và trạng thái cá nhân vốn đã hiện diện trong input. Chưa có phân tích chứng minh thông tin tăng thêm so với rule/check-in trực tiếp.
- Các thống kê có thể gợi ý giả thuyết để thử nghiệm điều chỉnh thời lượng hoặc hỏi về mục tiêu/thời gian rảnh; không nên để cụm ép người cùng nhóm nhận cùng bài tập/thời lượng.
- Không suy ra cường độ an toàn/y tế từ stress, năng lượng hay tâm trạng; các biến tự khai báo không thay cho đánh giá sức khỏe.
- Nếu chỉ dùng check-in hiện tại + lịch sử, chưa có bằng chứng hiện tại cho thấy sẽ mất thông tin quan trọng: các feature tạo cụm đều được tạo từ những câu trả lời đó, và chưa đo utility increment.
- Không dùng clustering như nhãn dropout hoặc nguy cơ bỏ tập.

## Hạn chế

- Không có survey mẫu mới, dữ liệu theo dõi, outcome recommendation hay thử nghiệm người dùng; không thể xác nhận khả năng khái quát hoặc giá trị sản phẩm.
- Chia train/test trên cùng survey không tạo dữ liệu độc lập; repeated splits chồng lặp người trả lời.
- Agglomerative assignment bằng medoid là xấp xỉ và có thể gán khác kết quả nếu chạy lại clustering trên toàn tập.
- Một người có missing/unknown trong feature ordinal bị từ chối. Chưa thiết kế chiến lược xử lý input thiếu/unknown cho sản phẩm.
- NMI/ARI giữa các split phụ thuộc số lượng bản ghi test giao nhau; `overlap_n` được lưu cho từng cặp để kiểm tra.
- Dữ liệu 322 phản hồi tự khai báo, cắt ngang và chưa xác lập đại diện toàn bộ người trẻ Việt Nam.

## Kết luận cuối cùng

**C. Clustering chưa sẵn sàng tích hợp.** Có thể duy trì K-Means A như baseline assignment dễ triển khai, K-Prototypes A như phương án hỗn hợp và Gower B như phép tách trạng thái không tập để tiếp tục kiểm chứng. Chưa chọn ứng viên Stage 3 vì tính ổn định hiện tại là nội bộ; chưa chứng minh cụm đem lại lợi ích vượt qua input gốc/rule-based; và gán người mới còn yêu cầu schema đầy đủ, xử lý unknown rõ ràng, cùng kiểm chứng ngoài mẫu.

Trước Stage 3 cần thu thập một mẫu khảo sát mới hoặc dữ liệu theo dõi có sự đồng thuận; đăng ký trước tiêu chí ổn định/giá trị ứng dụng; kiểm tra độ nhạy khi bỏ trạng thái `not_currently_training`; xử lý missing/unknown theo quy tắc có căn cứ; và so sánh offline với baseline dùng check-in/lịch sử. Chỉ xem xét pilot có kiểm soát sau khi có bằng chứng tăng giá trị.

## Artifact

- `stability_validation.csv`
- `new_user_assignment_tests.csv`
- `product_usefulness_comparison.csv`
- `figures_stability_validation.png`, `figures_cluster_assignment.png`, `figures_candidate_usefulness.png`
- `stage2d_validation.py` để tái tạo đầu ra.
- Không sửa hoặc ghi đè artifact Stage 2B/2C, model dropout v2.0, model registry, API, backend, frontend hay Recommendation Engine.
