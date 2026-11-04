# Phase 9.1-9.2 - Audit và đánh giá model dropout

## Artifact đang được sử dụng

- `ai-service/models/registry.json` đánh dấu **v2.0** là `ACTIVE`; thuật toán `logreg`; v1.0 và v3.0 không active.
- `models/v2.0/MODEL_CARD.md`, `metrics.json`, `feature_schema.json` và `registry.json` đồng nhất với nhau về version, thuật toán, ngưỡng và test metrics.
- `models/v2.0/model.joblib` được tải thành công khi gọi active FastAPI model; endpoint trả version `v2.0`, `is_demo_model=true`.

## Nguồn dữ liệu và nhãn

Manifest của `demo_v1` ghi:

- Nguồn: `demo_simulation[survey_profile(n=53)]`.
- 22.400 dòng của 400 user được mô phỏng theo chuỗi ngày; seed 42.
- Positive rate toàn bộ 29,8%.
- Dữ liệu survey 53 người chỉ cung cấp phân phối nền; quan hệ giữa trạng thái, hành vi tập và dropout được giả định trong chương trình mô phỏng.
- Nhãn là `dropout_7d=1` nếu không có buổi tập trong 7 ngày tiếp theo. Đây là nhãn sinh từ chuỗi hành vi mô phỏng, không phải outcome quan sát thực tế.

Do đó không được diễn giải các metric bên dưới là khả năng dự đoán dropout của người dùng AdaptiveFit ngoài đời.

## Thuật toán, feature và preprocessing

Model v2.0 là Logistic Regression với `C=0.03`, `class_weight="balanced"` trong pipeline có `StandardScaler`. Quy trình huấn luyện fit scaler/model trên train; dùng GroupKFold theo `user_id` để chọn tham số; hiệu chỉnh Platt và chọn threshold F2 trên validation.

10 feature, không gồm ID: `sleep_hours`, `stress_level`, `energy_level`, `mood`, `motivation`, `free_time_min`, `missed_days`, `workout_frequency_7d`, `avg_duration_7d`, `avg_difficulty_7d`.

Schema API yêu cầu đủ feature, từ chối trường thừa và kiểm tra miền giá trị. Feature bị thiếu hoặc sai miền bị FastAPI trả 422; không có imputation tự động ở endpoint. Trong backend, thời lượng/độ khó trung bình được đặt 0 khi không có hoạt động gần đây; đây là một quy ước biểu diễn đầu vào, không phải khôi phục giá trị thiếu.

## Tách dữ liệu và metric test đã lưu

`group_split` giữ toàn bộ ngày của một user trong cùng một phần dữ liệu. Split đã lưu:

| Phần | User | Dòng | Positive rate |
|---|---:|---:|---:|
| Train | 240 | 13.440 | 31,0% |
| Validation | 80 | 4.480 | 29,9% |
| Test | 80 | 4.480 | 26,2% |

Ngưỡng 0,14 được chọn trên validation theo F2, không chọn theo test.

| Metric tại threshold 0,14 | Kết quả test |
|---|---:|
| ROC-AUC | 0,886 |
| PR-AUC | 0,742 |
| Precision | 0,482 |
| Recall | 0,948 |
| F1 | 0,639 |
| Accuracy | 0,719 |
| Brier score | 0,119 |
| Confusion matrix | TN 2.108, FP 1.198, FN 61, TP 1.113 |

Với prevalence 26,2%, precision thấp hơn recall: mô hình gắn positive cho 2.311/4.480 dòng (51,6%). Đây là trade-off threshold ưu tiên recall, không phải chỉ số accuracy đơn thuần. Baseline `missed_days >= 3` có recall 0,802, precision 0,541, ROC-AUC 0,848 và accuracy 0,770 trên cùng test split: model tăng recall nhưng cũng tăng false positive và có accuracy/precision thấp hơn baseline.

## Calibration và ngưỡng

Brier 0,119 và bảng calibration 10 bin có trong `metrics.json`. ECE 10-bin tính lại từ bảng test là khoảng **0,029**. Một số sai lệch quan sát: bin 0,2-0,3 dự đoán trung bình 0,250, observed 0,313 (n=368); bin 0,5-0,6 dự đoán 0,553, observed 0,470 (n=270). Calibration chỉ được đo trên tập mô phỏng; không xác nhận calibration trên người dùng thật.

Threshold 0,14 làm tăng số false positive; không nên dùng các mức LOW/MEDIUM/HIGH như xác suất rủi ro đã được xác nhận lâm sàng hoặc xác nhận ngoài mẫu.

## SHAP, inference và lỗi

- FastAPI yêu cầu `X-Internal-Key`, dùng so sánh constant-time; endpoint có `extra="forbid"` và giới hạn feature theo schema.
- SHAP giải thích đóng góp vào dự đoán, không phải nguyên nhân. Logistic Regression dùng linear explainer; các model khác dùng permutation explainer. Baseline là median của background train đã lưu.
- Spring `AiServiceClient` dùng timeout 3 giây. Khi AI không bật hoặc request lỗi, `RiskService` cảnh báo log và dùng risk theo lịch sử thay vì trả kết quả model giả; fallback không được lưu như prediction model.
- Test thực tế bằng TestClient trên model đang active: health 200; request hợp lệ 200; thiếu key 401; feature ngoài miền 422. Test 200 trả v2.0/demo và SHAP factors.

## Đề xuất

Không huấn luyện lại bằng CSV survey 322 dòng vì không có nhãn outcome dropout. Giữ cảnh báo `DEMO` ở UI/API; không dùng model làm bằng chứng hiệu quả hoặc quyết định tự động. Muốn đánh giá giá trị dự đoán cần dữ liệu theo dõi consented, nhãn outcome được định nghĩa trước, split theo người dùng/thời gian phù hợp, đánh giá calibration và utility so với baseline trên mẫu độc lập. Không thay model artifact hay registry trong Phase 9 này.
