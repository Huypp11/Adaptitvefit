"""Profile and compare the three requested Stage 2B clustering candidates."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

import joblib
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parents[1] / "stage2b_clustering"
OUTPUT = Path(__file__).resolve().parent
DATA_PATH = Path(r"C:\Users\mitsu\OneDrive\Documents\survey_cleaned.csv")
DICTIONARY_PATH = Path(r"C:\Users\mitsu\OneDrive\Documents\feature_dictionary.csv")
sys.path.insert(0, str(ROOT))

CANDIDATES = [
    ("B|gower_agglomerative|k=2", "Gower + Agglomerative", "B"),
    ("A|kprototypes|k=2", "K-Prototypes", "A"),
    ("A|kmeans_numeric|k=2", "K-Means numeric baseline", "A"),
]

NUMERIC_VARIABLES = [
    "workout_days_last_7d",
    "workout_days_previous_7d",
    "target_sessions_per_week",
    "goal_behavior_gap",
    "stress_level",
    "energy_level",
    "mood",
    "motivation",
]

ORDINAL_ORDERS = {
    "sleep_duration": [
        "under_5_hours",
        "5_6_hours",
        "6_7_hours",
        "7_8_hours",
        "over_8_hours",
    ],
    "daily_available_training_time": [
        "under_15_min",
        "15_30_min",
        "31_45_min",
        "46_60_min",
        "over_60_min",
    ],
    "usual_session_duration": [
        "under_15_min",
        "15_30_min",
        "30_45_min",
        "46_60_min",
        "over_60_min",
    ],
}

CATEGORICAL_VARIABLES = [
    "usual_session_duration",
    "sleep_duration",
    "daily_available_training_time",
    "usual_training_location",
    "training_history_status",
    "response_when_tired",
    "last_break_duration",
    "helpful_after_rest",
]

MULTISELECT_VARIABLES = ["return_support_factors", "stopping_reasons"]

VARIABLE_GROUPS = {
    "workout_days_last_7d": "Hành vi tập luyện",
    "workout_days_previous_7d": "Hành vi tập luyện",
    "usual_session_duration": "Hành vi tập luyện",
    "target_sessions_per_week": "Hành vi tập luyện",
    "goal_behavior_gap": "Hành vi tập luyện",
    "sleep_duration": "Trạng thái cá nhân",
    "stress_level": "Trạng thái cá nhân",
    "energy_level": "Trạng thái cá nhân",
    "mood": "Trạng thái cá nhân",
    "motivation": "Trạng thái cá nhân",
    "daily_available_training_time": "Bối cảnh",
    "usual_training_location": "Bối cảnh",
    "training_history_status": "Bối cảnh",
    "response_when_tired": "Bối cảnh",
    "return_support_factors": "Biến profiling bổ sung",
    "last_break_duration": "Biến profiling bổ sung",
    "stopping_reasons": "Biến profiling bổ sung",
    "helpful_after_rest": "Biến profiling bổ sung",
}

VARIABLES = [
    "workout_days_last_7d",
    "workout_days_previous_7d",
    "usual_session_duration",
    "target_sessions_per_week",
    "goal_behavior_gap",
    "sleep_duration",
    "stress_level",
    "energy_level",
    "mood",
    "motivation",
    "daily_available_training_time",
    "usual_training_location",
    "training_history_status",
    "response_when_tired",
    "return_support_factors",
    "last_break_duration",
    "stopping_reasons",
    "helpful_after_rest",
]


def validate_inputs(
    data: pd.DataFrame,
    dictionary: pd.DataFrame,
    assignments: pd.DataFrame,
    metrics: pd.DataFrame,
    stage2b_profiles: pd.DataFrame,
    bundles: dict[str, dict[str, Any]],
) -> None:
    if len(data) != 322 or data.shape[1] != 19:
        raise ValueError(f"Expected cleaned survey 322x19, received {data.shape}")
    if set(dictionary["standardized_variable"]) != set(data.columns):
        raise ValueError("Feature Dictionary variables do not match cleaned survey columns")
    if len(assignments) != len(data):
        raise ValueError("Assignment row count does not match the cleaned survey")
    if not np.array_equal(assignments["survey_row_index"].to_numpy(), np.arange(len(data))):
        raise ValueError("Assignment rows are not aligned to the cleaned survey row order")
    if set(data.columns[data.isna().any()]) != {"last_break_duration", "stopping_reasons"}:
        raise ValueError("Unexpected missing-value pattern in cleaned survey")
    if data["last_break_duration"].isna().sum() != 96 or data["stopping_reasons"].isna().sum() != 96:
        raise ValueError("Expected 96 missing values in each of the Q8/Q9 profile variables")

    required_keys = {key for key, _, _ in CANDIDATES}
    for key in required_keys:
        if key not in assignments or key not in bundles:
            raise ValueError(f"Stage 2B assignment or artifact is missing: {key}")
        metric_rows = metrics[
            (metrics["configuration"] == key.split("|")[0])
            & (metrics["algorithm"] == key.split("|")[1])
            & (metrics["k"] == 2)
        ]
        if len(metric_rows) != 1:
            raise ValueError(f"Expected one Stage 2B metrics row for {key}")

        labels = assignments[key].to_numpy(dtype=int)
        bundle = bundles[key]
        artifact_labels = np.asarray(bundle["estimator"].labels_, dtype=int)
        if not np.array_equal(labels, artifact_labels):
            raise ValueError(f"Saved estimator labels do not reproduce the assignment CSV for {key}")
        if not np.array_equal(labels, np.asarray(bundle["labels"], dtype=int)):
            raise ValueError(f"Saved bundle labels do not reproduce the assignment CSV for {key}")

        counts = pd.Series(labels).value_counts().sort_index().to_dict()
        metric_sizes = json.loads(metric_rows.iloc[0]["cluster_sizes"])
        metric_sizes = {int(cluster): int(n) for cluster, n in metric_sizes.items()}
        if counts != metric_sizes:
            raise ValueError(f"Cluster sizes do not match Stage 2B metrics for {key}")
        for cluster, n in counts.items():
            rows = stage2b_profiles[
                (stage2b_profiles["configuration"] == key.split("|")[0])
                & (stage2b_profiles["algorithm"] == key.split("|")[1])
                & (stage2b_profiles["k"] == 2)
                & (stage2b_profiles["cluster"] == cluster)
            ]
            if len(rows) != 1 or int(rows.iloc[0]["n"]) != n:
                raise ValueError(f"Stage 2B profile size does not match assignments for {key}, cluster {cluster}")


def add_profile_row(
    rows: list[dict[str, Any]],
    candidate_key: str,
    algorithm: str,
    configuration: str,
    cluster: int,
    cluster_n: int,
    variable: str,
    statistic: str,
    value: Any,
    n_valid: int,
    n_missing: int,
    category: str = "",
    percent_valid: float | None = None,
    note: str = "",
) -> None:
    rows.append(
        {
            "candidate_key": candidate_key,
            "configuration": configuration,
            "algorithm": algorithm,
            "cluster": cluster,
            "cluster_n": cluster_n,
            "cluster_pct_total": 100 * cluster_n / 322,
            "domain": VARIABLE_GROUPS[variable],
            "variable": variable,
            "statistic": statistic,
            "category": category,
            "value": value,
            "percent_valid": percent_valid,
            "n_valid": n_valid,
            "n_missing": n_missing,
            "note": note,
        }
    )


def summarize_candidate(
    data: pd.DataFrame,
    labels: np.ndarray,
    candidate_key: str,
    algorithm: str,
    configuration: str,
    metric_row: pd.Series,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    frame = data.copy()
    frame["goal_behavior_gap"] = frame["target_sessions_per_week"] - frame["workout_days_last_7d"]
    frame["cluster"] = labels
    detail_rows: list[dict[str, Any]] = []
    compact_rows: list[dict[str, Any]] = []
    cluster_ids = sorted(np.unique(labels).tolist())

    for cluster in cluster_ids:
        group = frame[frame["cluster"] == cluster]
        n = len(group)
        nontraining = group["usual_session_duration"].eq("not_currently_training")
        status_percent = 100 * nontraining.mean()
        compact_rows.append(
            {
                "candidate_key": candidate_key,
                "configuration": configuration,
                "algorithm": algorithm,
                "k": 2,
                "silhouette": float(metric_row["silhouette"]),
                "silhouette_distance": metric_row["silhouette_distance"],
                "stability_ari_mean": float(metric_row["stability_ari_mean"]),
                "stability_ari_sd": float(metric_row["stability_ari_sd"]),
                "cluster": cluster,
                "cluster_n": n,
                "cluster_pct_total": 100 * n / len(frame),
                "not_currently_training_n": int(nontraining.sum()),
                "not_currently_training_pct": status_percent,
            }
        )

        for variable in NUMERIC_VARIABLES:
            values = pd.to_numeric(group[variable], errors="raise").dropna()
            missing = int(group[variable].isna().sum())
            n_valid = len(values)
            if n_valid == 0:
                continue
            q1, median, q3 = values.quantile([0.25, 0.5, 0.75]).tolist()
            for statistic, value in [
                ("mean", values.mean()),
                ("median", median),
                ("q1", q1),
                ("q3", q3),
                ("iqr", q3 - q1),
            ]:
                add_profile_row(
                    detail_rows,
                    candidate_key,
                    algorithm,
                    configuration,
                    cluster,
                    n,
                    variable,
                    statistic,
                    float(value),
                    n_valid,
                    missing,
                    note="goal_behavior_gap được tính bằng target_sessions_per_week - workout_days_last_7d."
                    if variable == "goal_behavior_gap"
                    else "",
                )

        for variable in CATEGORICAL_VARIABLES:
            source = group[variable]
            missing = int(source.isna().sum())
            valid = source.dropna()
            n_valid = len(valid)
            if n_valid == 0:
                add_profile_row(
                    detail_rows, candidate_key, algorithm, configuration, cluster, n,
                    variable, "no_valid_values", "", 0, missing,
                )
                continue
            counts = valid.astype(str).value_counts().sort_index()
            for category, count in counts.items():
                add_profile_row(
                    detail_rows,
                    candidate_key,
                    algorithm,
                    configuration,
                    cluster,
                    n,
                    variable,
                    "percent_valid",
                    100 * count / n_valid,
                    n_valid,
                    missing,
                    category=category,
                    percent_valid=100 * count / n_valid,
                )

            if variable in ORDINAL_ORDERS:
                order = ORDINAL_ORDERS[variable]
                ordered_values = valid.astype(str)
                if variable == "usual_session_duration":
                    ordered_values = ordered_values[ordered_values != "not_currently_training"]
                mapped = ordered_values.map({category: rank for rank, category in enumerate(order)}).dropna()
                if len(mapped):
                    median_rank = mapped.median()
                    q1_rank = mapped.quantile(0.25)
                    q3_rank = mapped.quantile(0.75)
                    for statistic, rank in [
                        ("median_category", median_rank),
                        ("q1_category", q1_rank),
                        ("q3_category", q3_rank),
                    ]:
                        category = order[int(np.floor(rank + 0.5))]
                        add_profile_row(
                            detail_rows,
                            candidate_key,
                            algorithm,
                            configuration,
                            cluster,
                            n,
                            variable,
                            statistic,
                            category,
                            len(mapped),
                            missing,
                            note=(
                                "Thứ bậc danh mục; với usual_session_duration chỉ tính người đang tập. "
                                "Không diễn giải khoảng cách giữa category như số phút bằng nhau."
                            ),
                        )
                    add_profile_row(
                        detail_rows,
                        candidate_key,
                        algorithm,
                        configuration,
                        cluster,
                        n,
                        variable,
                        "mean_order_rank",
                        float(mapped.mean()),
                        len(mapped),
                        missing,
                        note="Trung bình thứ hạng chỉ mang tính thứ bậc, không phải phút/giờ trung bình.",
                    )

        for variable in MULTISELECT_VARIABLES:
            source = group[variable]
            missing = int(source.isna().sum())
            valid = source.dropna().astype(str)
            n_valid = len(valid)
            selected_counts: dict[str, int] = {}
            for response in valid:
                selected = set(token.strip() for token in response.split(" | ") if token.strip())
                for token in selected:
                    selected_counts[token] = selected_counts.get(token, 0) + 1
            for category, count in sorted(selected_counts.items()):
                add_profile_row(
                    detail_rows,
                    candidate_key,
                    algorithm,
                    configuration,
                    cluster,
                    n,
                    variable,
                    "percent_of_valid_respondents_selecting",
                    100 * count / n_valid if n_valid else np.nan,
                    n_valid,
                    missing,
                    category=category,
                    percent_valid=100 * count / n_valid if n_valid else np.nan,
                    note="Câu hỏi nhiều lựa chọn; tỷ lệ không nhất thiết cộng thành 100%.",
                )

        for variable in ["last_break_duration", "stopping_reasons"]:
            compact_rows[-1][f"{variable}_valid_n"] = int(group[variable].notna().sum())
            compact_rows[-1][f"{variable}_missing_n"] = int(group[variable].isna().sum())

    # Put cluster-level comparisons on one row for each candidate.
    by_cluster = frame.groupby("cluster", sort=True)
    if len(cluster_ids) == 2:
        c0, c1 = cluster_ids
        left, right = by_cluster.get_group(c0), by_cluster.get_group(c1)
        for variable in [
            "workout_days_last_7d",
            "workout_days_previous_7d",
            "target_sessions_per_week",
            "goal_behavior_gap",
            "stress_level",
            "energy_level",
            "mood",
            "motivation",
        ]:
            means = [float(pd.to_numeric(g[variable]).mean()) for g in [left, right]]
            medians = [float(pd.to_numeric(g[variable]).median()) for g in [left, right]]
            for row, mean, median in zip(compact_rows, means, medians):
                row[f"{variable}_mean"] = mean
                row[f"{variable}_median"] = median
            compact_rows[0][f"cluster_{c1}_minus_{c0}_{variable}_mean"] = means[1] - means[0]
            compact_rows[0][f"cluster_{c1}_minus_{c0}_{variable}_median"] = medians[1] - medians[0]

        for variable in [
            "usual_session_duration",
            "daily_available_training_time",
            "usual_training_location",
            "training_history_status",
            "response_when_tired",
            "helpful_after_rest",
        ]:
            distributions = []
            for subgroup in [left, right]:
                values = subgroup[variable].dropna().astype(str)
                distributions.append(values.value_counts(normalize=True))
            categories = set(distributions[0].index) | set(distributions[1].index)
            tvd = 0.5 * sum(
                abs(float(distributions[0].get(category, 0)) - float(distributions[1].get(category, 0)))
                for category in categories
            )
            compact_rows[0][f"{variable}_total_variation_distance"] = tvd

        inactive_label = int(
            max(
                cluster_ids,
                key=lambda cluster: frame.loc[frame["cluster"] == cluster, "usual_session_duration"]
                .eq("not_currently_training")
                .mean(),
            )
        )
        compact_rows[0]["cluster_with_highest_not_training_pct"] = inactive_label
        compact_rows[0]["training_status_partition_purity"] = float(
            sum(
                frame.loc[frame["cluster"] == cluster, "usual_session_duration"]
                .eq("not_currently_training")
                .sum()
                if cluster == inactive_label
                else frame.loc[frame["cluster"] == cluster, "usual_session_duration"]
                .ne("not_currently_training")
                .sum()
                for cluster in cluster_ids
            )
            / len(frame)
        )
        # Candidate-level fields are repeated only on its first cluster row for clarity.
        compact_rows[0]["training_status_partition_purity_note"] = (
            "Best label-to-status matching accuracy; status is also an input feature for all three candidates."
        )
        compact_rows[0]["return_support_factors_overall_missing"] = int(data["return_support_factors"].isna().sum())
        compact_rows[0]["stopping_reasons_total_missing"] = int(data["stopping_reasons"].isna().sum())

    return detail_rows, compact_rows


def top_category(group: pd.DataFrame, column: str) -> tuple[str, float, int]:
    valid = group[column].dropna().astype(str)
    if valid.empty:
        return "không có dữ liệu hợp lệ", np.nan, 0
    counts = valid.value_counts()
    return str(counts.index[0]), 100 * float(counts.iloc[0]) / len(valid), len(valid)


def write_interpretation(
    data: pd.DataFrame,
    metrics: pd.DataFrame,
    assignment_frame: pd.DataFrame,
    comparison: pd.DataFrame,
) -> None:
    lines = [
        "# Stage 2C - Diễn giải hồ sơ cụm",
        "",
        "## Phạm vi và kiểm tra dữ liệu",
        "",
        "- Phân tích đúng ba ứng viên được yêu cầu: Gower + Agglomerative cấu hình B, K-Prototypes cấu hình A và K-Means numeric cấu hình A; cả ba đều có k=2.",
        "- Dữ liệu khảo sát sạch có 322 dòng, 19 cột. Nhãn cụm trong `clustering_results.csv` đã được đối chiếu theo đúng thứ tự dòng với estimator đã lưu trong artifact Stage 2B.",
        "- Không tạo nhãn dropout, không điền missing và không sửa artifact Stage 2B. `last_break_duration` và `stopping_reasons` vẫn thiếu 96 giá trị mỗi biến; mẫu số hợp lệ được báo cáo theo từng cụm.",
        "- Tỷ lệ biến phân loại được tính trên số câu trả lời hợp lệ trong cụm. Với câu hỏi nhiều lựa chọn, phần trăm là tỷ lệ người có câu trả lời hợp lệ chọn từng phương án; tổng có thể vượt 100%.",
        "- Các biến thứ bậc dạng khoảng thời gian được báo cáo theo danh mục/ thứ hạng; không quy đổi thành số phút/giờ trung bình.",
        "",
        "## Cách đọc nhãn cụm",
        "",
        "Số 0 và 1 chỉ là mã do thuật toán gán, không mang ý nghĩa tốt/xấu hoặc mức nguy cơ. Diễn giải bên dưới dựa trên số liệu thực tế của từng cụm, không dựa vào số nhãn.",
        "",
        "## Kích thước, hành vi và trạng thái không tập",
        "",
        "| Ứng viên | Cụm | n (%) | Đang không tập, n (%) trong cụm | Tập gần nhất, trung vị ngày/tuần | Tuần trước đó, trung vị | Mục tiêu, trung vị | Chênh lệch mục tiêu-thực tế, trung vị |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for row in comparison.sort_values(["candidate_key", "cluster"]).itertuples(index=False):
        lines.append(
            f"| {row.algorithm} ({row.configuration}) | {row.cluster} | {row.cluster_n} "
            f"({row.cluster_pct_total:.1f}%) | {row.not_currently_training_n} "
            f"({row.not_currently_training_pct:.1f}%) | {row.workout_days_last_7d_median:.1f} "
            f"| {row.workout_days_previous_7d_median:.1f} | {row.target_sessions_per_week_median:.1f} "
            f"| {row.goal_behavior_gap_median:.1f} |"
        )
    lines += [
        "",
        "### Chênh lệch mô tả giữa hai cụm",
        "",
        "Bảng dưới đây dùng trị tuyệt đối của chênh lệch trung bình (hoặc chênh lệch trung vị với goal gap) giữa cluster 0 và 1. Đây là mô tả trên chính các biến đầu vào/profiling, không phải kiểm định độc lập hay suy luận nhân quả.",
        "",
        "| Ứng viên | Chênh lệch TB ngày tập gần nhất | Chênh lệch TB tuần trước | Chênh lệch trung vị goal gap | Chênh lệch TB stress | Năng lượng | Tâm trạng | Động lực |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for key, label, _ in CANDIDATES:
        row = comparison[comparison["candidate_key"] == key].sort_values("cluster").iloc[0]
        lines.append(
            f"| {label} | {abs(row['cluster_1_minus_0_workout_days_last_7d_mean']):.2f} "
            f"| {abs(row['cluster_1_minus_0_workout_days_previous_7d_mean']):.2f} "
            f"| {abs(row['cluster_1_minus_0_goal_behavior_gap_median']):.1f} "
            f"| {abs(row['cluster_1_minus_0_stress_level_mean']):.2f} "
            f"| {abs(row['cluster_1_minus_0_energy_level_mean']):.2f} "
            f"| {abs(row['cluster_1_minus_0_mood_mean']):.2f} "
            f"| {abs(row['cluster_1_minus_0_motivation_mean']):.2f} |"
        )
    lines += [
        "",
        "- **Hành vi tập luyện:** K-Prototypes A có chênh lệch trung bình lớn nhất về số ngày tập gần nhất giữa hai cụm (2.84 ngày/tuần), sát sau là Gower B (2.78) và K-Means A (2.62). Với Gower B, khác biệt này trùng hoàn toàn với trạng thái không tập hiện tại; vì vậy không nên diễn giải như một phân nhóm hành vi độc lập với trạng thái.",
        "- **Trạng thái cá nhân:** trong ba phương án, K-Means A có chênh lệch trung bình lớn hơn ở năng lượng, tâm trạng và động lực (lần lượt khoảng 1.09, 1.12 và 1.37 điểm trên thang 1-5); chênh lệch này cũng đi cùng khác biệt về tần suất tập/trạng thái không tập. Đây là khác biệt mô tả, không chứng minh trạng thái cá nhân tạo ra hành vi tập.",
        "- **Mục tiêu so với thực tế:** Gower B có chênh lệch trung bình goal gap giữa hai cụm lớn nhất (2.53 buổi/tuần; trung vị chênh 2 buổi), theo sau là K-Prototypes A (2.23; trung vị chênh 2) và K-Means A (1.89; trung vị chênh 1). Cụm có gap cao hơn cũng tập ít hơn trong tuần gần nhất.",
        "- **Kích thước và khả năng giải thích:** kích thước nhỏ nhất là 68/322. Gower B dễ mô tả nhất nhưng chủ yếu chia người đang tập và không tập. K-Prototypes A và K-Means A tạo hai cụm cân bằng hơn, kết hợp khác biệt về hành vi và trạng thái cá nhân; cần kiểm chứng trên dữ liệu mới trước khi xem đó là phân khúc bền vững.",
    ]

    for key, label, config in CANDIDATES:
        candidate = comparison[comparison["candidate_key"] == key].sort_values("cluster")
        metric = metrics[
            (metrics["configuration"] == config)
            & (metrics["algorithm"] == key.split("|")[1])
            & (metrics["k"] == 2)
        ].iloc[0]
        lines += [
            "",
            f"### {label} - cấu hình {config}, k=2",
            "",
            f"- Stage 2B: silhouette **{metric['silhouette']:.3f}** trong không gian `{metric['silhouette_distance']}`; ARI stability **{metric['stability_ari_mean']:.3f} ± {metric['stability_ari_sd']:.3f}** qua {int(metric['stability_repeats'])} mẫu con. Silhouette chỉ được dùng để mô tả trong cùng metric, không so trực tiếp giữa các phương pháp.",
        ]
        inactive_clusters = candidate[
            candidate["not_currently_training_pct"] >= 99.999
        ]
        if not inactive_clusters.empty:
            inactive = inactive_clusters.iloc[0]
            lines.append(
                f"- Cụm {int(inactive['cluster'])} gồm {int(inactive['cluster_n'])} người và **100%** chọn `not_currently_training`; đây là cụm phân tách trạng thái hiện không tập rõ rệt."
            )
        else:
            lines.append(
                "- Không có cụm nào gồm toàn bộ người chọn `not_currently_training`; trạng thái tập/không tập không phải ranh giới hoàn toàn thuần của kết quả này."
            )
        for row in candidate.itertuples(index=False):
            group = data.loc[assignment_frame[key].to_numpy() == row.cluster]
            duration, duration_pct, duration_n = top_category(group, "usual_session_duration")
            sleep, sleep_pct, sleep_n = top_category(group, "sleep_duration")
            time, time_pct, time_n = top_category(group, "daily_available_training_time")
            place, place_pct, place_n = top_category(group, "usual_training_location")
            tired, tired_pct, tired_n = top_category(group, "response_when_tired")
            lines.append(
                f"- Cụm {int(row.cluster)} (n={int(row.cluster_n)}): thời lượng tập phổ biến nhất "
                f"`{duration}` ({duration_pct:.1f}%, n hợp lệ={duration_n}); ngủ `{sleep}` "
                f"({sleep_pct:.1f}%, n={sleep_n}); thời gian rảnh `{time}` ({time_pct:.1f}%, n={time_n}); "
                f"địa điểm `{place}` ({place_pct:.1f}%, n={place_n}); phản ứng khi mệt `{tired}` "
                f"({tired_pct:.1f}%, n={tired_n}). Trung bình số ngày tập tuần gần nhất "
                f"{row.workout_days_last_7d_mean:.2f}, năng lượng {row.energy_level_mean:.2f}, "
                f"tâm trạng {row.mood_mean:.2f}, động lực {row.motivation_mean:.2f}; "
                f"trung vị chênh lệch mục tiêu-thực tế {row.goal_behavior_gap_median:.1f} buổi/tuần."
            )

    lines += [
        "",
        "## Đối chiếu ba ứng viên",
        "",
        "- **Phân tách trạng thái đang tập/không tập:** Gower + Agglomerative B tách thành một cụm 68 người đều khai báo `not_currently_training`; 254 người còn lại đang tập với nhiều mức thời lượng khác nhau. K-Prototypes A và K-Means A cũng đưa trạng thái thời lượng vào feature nhưng không tạo cụm hoàn toàn thuần theo trạng thái. Lưu ý đây là đối chiếu với feature đầu vào, không phải bằng chứng độc lập.",
        "- **Khác biệt hành vi:** xem chênh lệch trung vị/trung bình của hai biến số ngày tập, mục tiêu và `goal_behavior_gap` trong `cluster_profiles_detailed.csv` và `candidate_comparison.csv`. K-Prototypes và K-Means cấu hình A có thể chia cả người đang tập lẫn không tập; diễn giải phải đọc cùng tỷ lệ trạng thái trong mỗi cụm.",
        "- **Khác biệt trạng thái cá nhân:** so sánh stress, năng lượng, tâm trạng và động lực bằng trung vị/IQR/trung bình trong bảng chi tiết. Các giá trị là tự khai báo cùng thời điểm, chỉ biểu thị khác biệt mô tả giữa nhóm.",
        "- **Khoảng cách mục tiêu-thực tế:** `goal_behavior_gap = mục tiêu - số ngày tập gần nhất`; số dương lớn hơn nghĩa là mục tiêu được khai báo cao hơn hành vi tuần vừa qua. Đây không phải nguyên nhân, không phải dự báo tương lai, và tuần gần nhất có thể không đại diện thói quen dài hạn.",
        "- Các cụm đều có quy mô đủ để mô tả ở mức thống kê mẫu này (nhỏ nhất là 68 người trong ba ứng viên), nhưng điều đó không chứng minh tính ổn định ngoài mẫu hoặc tính đại diện.",
        "",
        "## Độ ổn định có phù hợp với hồ sơ không?",
        "",
        "- Gower B có ARI 1.000 (SD 0.000) trên 8 mẫu con và tách rõ trạng thái hiện không tập. Đây là kết quả lặp lại ổn định trong phép lấy mẫu con đã dùng, nhưng cụm chủ yếu phản ánh trạng thái tập luyện, và trạng thái đó vốn là feature của cấu hình.",
        "- K-Means A có ARI 0.968 (SD 0.041), cao; hai cụm có quy mô 182/140. Cấu trúc phân nhóm có thể ổn định theo phép thử đã chạy nhưng không hàm ý là hai nhóm tự nhiên/ổn định ở mẫu khác.",
        "- K-Prototypes A có ARI 0.890 (SD 0.117), thấp hơn hai phương pháp kia và độ dao động lớn hơn. Cần thận trọng hơn khi xem các khác biệt giữa cụm là cấu trúc bền vững.",
        "- ARI ở đây so sánh kết quả trên các mẫu con với kết quả tham chiếu trên đúng các cá nhân đó; đây là kiểm tra độ nhạy nội bộ, không phải xác nhận ngoài mẫu.",
        "",
        "## Khả năng ứng dụng trong AdaptiveFit",
        "",
        "- **Thời lượng tập:** có thể dùng tỷ lệ thời lượng buổi tập khai báo và thời gian rảnh để gợi ý lựa chọn thử nghiệm; không nên mặc định người trong cùng cụm cần một thời lượng cố định.",
        "- **Cường độ:** các biến stress/năng lượng/tâm trạng/động lực có thể hỗ trợ điều chỉnh gợi ý theo câu trả lời hiện tại. Chúng là tự báo cáo và không đo sức khỏe hay khả năng tập an toàn.",
        "- **Địa điểm và loại bài:** Gower B có `usual_training_location` làm feature; K-Prototypes A và K-Means A không dùng địa điểm để tạo cụm, nhưng có thể dùng để profiling. Cả ba ứng viên đều có `response_when_tired` là biến profiling, không phải feature tạo cụm. Không suy diễn rằng nhóm thích địa điểm/bài tập nào nếu dữ liệu chỉ ghi nhận lựa chọn hiện tại.",
        "- **Khoảng cách mục tiêu-thực tế lớn:** có thể dùng gap để mở lời bằng kế hoạch nhỏ hơn hoặc hỏi người dùng muốn điều chỉnh mục tiêu hay thời gian tập. Đây là hướng thử nghiệm UX, không phải quan hệ nhân quả đã được chứng minh.",
        "- **Không dùng cụm như nhãn dropout:** khảo sát cắt ngang không cung cấp nhãn/kết quả bỏ tập trong tương lai; các cụm không phải dự báo rủi ro.",
        "",
        "## Hạn chế và cách đọc các tỷ lệ",
        "",
        "- Tỷ lệ phân loại dùng mẫu số câu trả lời hợp lệ trong từng cụm; `n_valid` và `n_missing` nằm trong từng dòng của CSV chi tiết.",
        "- `last_break_duration` và `stopping_reasons` mỗi biến thiếu 96 giá trị. Missing được giữ nguyên; phân tích Q8/Q9 chỉ dùng câu trả lời hợp lệ và không giả định lý do thiếu.",
        "- `return_support_factors` là câu hỏi nhiều lựa chọn: tỷ lệ của mỗi lựa chọn tính trên người trả lời hợp lệ; tổng tỷ lệ có thể lớn hơn 100%.",
        "- Nhóm `not_currently_training` là trạng thái đặc biệt, không phải thời lượng tập. Khi báo cáo thứ hạng thời lượng, chỉ những người đang tập được dùng; bảng vẫn giữ phân phối trạng thái đầy đủ.",
        "- Mẫu gồm 322 câu trả lời tự khai báo, cắt ngang và chưa xác lập tính đại diện. Không kết luận quan hệ nhân quả hoặc khả năng tổng quát hóa cho người trẻ Việt Nam.",
        "",
        "Các biểu đồ đi kèm: `figures_behavior_comparison.png`, `figures_personal_state_comparison.png` và `figures_stability_comparison.png`. Không vẽ một bảng xếp hạng silhouette chung giữa các metric không tương thích.",
    ]
    (OUTPUT / "cluster_interpretation.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_selection_recommendation(
    comparison: pd.DataFrame,
    metrics: pd.DataFrame,
) -> None:
    metrics_by_key = {
        f"{row.configuration}|{row.algorithm}|k={int(row.k)}": row
        for row in metrics.itertuples(index=False)
    }
    lines = [
        "# Stage 2C - Khuyến nghị lựa chọn mô hình",
        "",
        "## Kết luận",
        "",
        "**Chưa đủ căn cứ chọn mô hình để tích hợp vào AdaptiveFit AI.** Có thể tiếp tục đánh giá ngoại tuyến cả ba ứng viên, nhưng chưa nên đưa nhãn cụm vào recommendation engine hoặc dùng như phân loại người dùng ổn định.",
        "",
        "## Trade-off của từng ứng viên",
        "",
    ]
    for key, label, _ in CANDIDATES:
        metric = metrics_by_key[key]
        rows = comparison[comparison["candidate_key"] == key]
        sizes = rows.sort_values("cluster")["cluster_n"].astype(int).tolist()
        inactivity = rows.sort_values("cluster")["not_currently_training_pct"].round(1).tolist()
        lines.append(
            f"- **{label} ({key.split('|')[0]}, k=2):** silhouette {metric.silhouette:.3f} "
            f"({metric.silhouette_distance}; không so trực tiếp với metric khác), ARI "
            f"{metric.stability_ari_mean:.3f} ± {metric.stability_ari_sd:.3f}; kích thước cụm "
            f"{sizes}; tỷ lệ `not_currently_training` theo cụm {inactivity}%."
        )
    lines += [
        "",
        "Gower + Agglomerative B ổn định nhất trong phép lấy mẫu con (ARI=1.000) và cung cấp địa điểm/lịch sử tập làm feature, nhưng một cụm 68 người tách chính xác nhóm không tập hiện tại. Vì trạng thái này vốn là input, kết quả có thể hữu ích cho luồng người mới quay lại nhưng không chứng minh một phân nhóm nhu cầu rộng hơn; Agglomerative cũng không có `predict` gốc, nên gán medoid là xấp xỉ.",
        "",
        "K-Means A cho kích thước cụm cân bằng hơn (182/140) và ARI cao (0.968), nhưng bỏ các danh mục nominal; silhouette phải được hiểu trong không gian Euclidean numeric/ordinal. Nó có thể là baseline đơn giản để kiểm tra thêm, không đủ để chọn làm model sản phẩm.",
        "",
        "K-Prototypes A giữ dữ liệu hỗn hợp và cho hai cụm gần cân bằng (157/165), nhưng độ ổn định thấp hơn và dao động lớn hơn (ARI 0.890 ± 0.117). Silhouette báo cáo trong khoảng cách Gower chỉ là chẩn đoán bên ngoài, không phải objective của K-Prototypes.",
        "",
        "Không so sánh trực tiếp các silhouette giữa ba phương pháp. Cả ba dùng `usual_session_duration` và có sự liên hệ đầu vào giữa nhãn cụm với trạng thái tập luyện; profiling không phải kiểm định độc lập. Cả ba đều chưa được kiểm chứng trên mẫu mới, chưa đánh giá độ hữu ích cho người dùng, và không phải nhãn dropout.",
        "",
        "## Kiểm tra cần làm trước quyết định triển khai",
        "",
        "1. Kiểm định trên mẫu khảo sát mới đủ lớn, giữ nguyên quy tắc làm sạch và thứ bậc; đo mức ổn định nhãn giữa các mẫu độc lập.",
        "2. Làm kiểm tra độ nhạy: nhiều seed/lần khởi tạo cho K-Prototypes và K-Means; nhiều lần bootstrap/subsample cho cả ba; kiểm tra tác động khi bỏ `usual_session_duration` để xem phân cụm có còn chỉ phản ánh đang tập/không tập.",
        "3. Xác minh logic skip Q8/Q9, mẫu số missing và ý nghĩa của các lựa chọn trước khi dùng lịch sử ngừng tập trong profiling hoặc cá nhân hóa.",
        "4. Định nghĩa trước tiêu chí sản phẩm: các gợi ý khác nhau cụ thể thế nào, có sẵn dữ liệu đầu vào lúc chạy hay không, và thử nghiệm người dùng có đo kết quả phù hợp hay không.",
        "5. Nếu vẫn cân nhắc Agglomerative, kiểm định xấp xỉ nearest-medoid trên dữ liệu mới; không coi đây là `predict` tương đương thuật toán gốc.",
        "6. Chỉ sau các bước trên mới đề xuất một thử nghiệm integration có version/feature flag và cơ chế quay lui. Không chỉnh sửa API, backend, frontend, recommendation engine hoặc model registry trong Stage 2C.",
        "",
        "## Phạm vi dữ liệu và bảo toàn artifact",
        "",
        "- Stage 2C chỉ đọc đầu vào và artifact Stage 2B; các file Stage 2B không bị ghi đè.",
        "- Không tạo dữ liệu giả, không điền missing, không tạo nhãn dropout và không đưa ra kết luận nhân quả.",
    ]
    (OUTPUT / "model_selection_recommendation.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def plot_outputs(comparison: pd.DataFrame) -> None:
    candidates = [label for _, label, _ in CANDIDATES]
    fig, axes = plt.subplots(1, 3, figsize=(15, 5))
    for ax, variable, title, ylabel in [
        (axes[0], "workout_days_last_7d_mean", "Số ngày tập trong 7 ngày gần nhất", "Ngày/tuần (trung bình)"),
        (axes[1], "goal_behavior_gap_mean", "Khoảng cách mục tiêu - thực tế", "Buổi/tuần (trung bình)"),
        (axes[2], "not_currently_training_pct", "Đang không tập hiện tại", "% trong cụm"),
    ]:
        pivot = comparison.pivot(index="candidate_key", columns="cluster", values=variable)
        pivot = pivot.reindex([key for key, _, _ in CANDIDATES])
        pivot.index = candidates
        pivot.plot(kind="bar", ax=ax, rot=22, color=["#4477aa", "#ee6677"])
        ax.set_title(title)
        ax.set_ylabel(ylabel)
        ax.set_xlabel("")
        ax.legend(title="Cụm")
        ax.grid(axis="y", alpha=0.2)
    fig.suptitle("Hồ sơ hành vi và trạng thái theo cụm (chỉ so cụm trong từng ứng viên)")
    fig.tight_layout()
    fig.savefig(OUTPUT / "figures_behavior_comparison.png", dpi=160, bbox_inches="tight")
    plt.close(fig)

    state_variables = ["energy_level_mean", "mood_mean", "motivation_mean", "stress_level_mean"]
    labels = ["Năng lượng", "Tâm trạng", "Động lực", "Căng thẳng"]
    fig, axes = plt.subplots(1, 3, figsize=(15, 5), sharey=True)
    for ax, (key, name, _) in zip(axes, CANDIDATES):
        rows = comparison[comparison["candidate_key"] == key].sort_values("cluster")
        x = np.arange(len(state_variables))
        width = 0.36
        for cluster in [0, 1]:
            row = rows[rows["cluster"] == cluster].iloc[0]
            ax.bar(
                x + (cluster - 0.5) * width,
                [row[variable] for variable in state_variables],
                width,
                label=f"Cụm {cluster}",
                color=["#4477aa", "#ee6677"][cluster],
            )
        ax.set_xticks(x, labels, rotation=20)
        ax.set_title(name)
        ax.set_ylim(1, 5)
        ax.grid(axis="y", alpha=0.2)
    axes[0].set_ylabel("Trung bình thang 1-5")
    axes[-1].legend()
    fig.suptitle("Trạng thái cá nhân theo cụm (mô tả, không hàm ý nhân quả)")
    fig.tight_layout()
    fig.savefig(OUTPUT / "figures_personal_state_comparison.png", dpi=160, bbox_inches="tight")
    plt.close(fig)

    metrics = pd.read_csv(ROOT / "clustering_metrics.csv", encoding="utf-8-sig")
    keys = [key for key, _, _ in CANDIDATES]
    rows = []
    for key in keys:
        config, algorithm, _ = key.split("|")
        rows.append(
            metrics[
                (metrics["configuration"] == config)
                & (metrics["algorithm"] == algorithm)
                & (metrics["k"] == 2)
            ].iloc[0]
        )
    fig, ax = plt.subplots(figsize=(8, 4.5))
    ax.bar(
        [name for _, name, _ in CANDIDATES],
        [row["stability_ari_mean"] for row in rows],
        yerr=[row["stability_ari_sd"] for row in rows],
        capsize=5,
        color=["#4477aa", "#228833", "#ccbb44"],
    )
    ax.set_ylim(0, 1.05)
    ax.set_ylabel("ARI trung bình ± độ lệch chuẩn")
    ax.set_title("Độ ổn định nội bộ Stage 2B (8 mẫu con; không phải kiểm định ngoài mẫu)")
    ax.tick_params(axis="x", rotation=15)
    ax.grid(axis="y", alpha=0.2)
    fig.tight_layout()
    fig.savefig(OUTPUT / "figures_stability_comparison.png", dpi=160, bbox_inches="tight")
    plt.close(fig)


def main() -> int:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    data = pd.read_csv(DATA_PATH, encoding="utf-8-sig")
    dictionary = pd.read_csv(DICTIONARY_PATH, encoding="utf-8-sig")
    assignments = pd.read_csv(ROOT / "clustering_results.csv", encoding="utf-8-sig")
    metrics = pd.read_csv(ROOT / "clustering_metrics.csv", encoding="utf-8-sig")
    stage2b_profiles = pd.read_csv(ROOT / "cluster_profiles.csv", encoding="utf-8-sig")
    artifacts = joblib.load(ROOT / "artifacts" / "candidate_models.joblib")
    bundles = artifacts["candidate_bundles"]
    validate_inputs(data, dictionary, assignments, metrics, stage2b_profiles, bundles)

    detailed_rows: list[dict[str, Any]] = []
    compact_rows: list[dict[str, Any]] = []
    for key, label, config in CANDIDATES:
        algorithm = key.split("|")[1]
        metric_row = metrics[
            (metrics["configuration"] == config)
            & (metrics["algorithm"] == algorithm)
            & (metrics["k"] == 2)
        ].iloc[0]
        details, compact = summarize_candidate(
            data,
            assignments[key].to_numpy(dtype=int),
            key,
            label,
            config,
            metric_row,
        )
        detailed_rows.extend(details)
        compact_rows.extend(compact)

    detailed = pd.DataFrame(detailed_rows)
    comparison = pd.DataFrame(compact_rows)
    detailed.to_csv(OUTPUT / "cluster_profiles_detailed.csv", index=False, encoding="utf-8-sig")
    comparison.to_csv(OUTPUT / "candidate_comparison.csv", index=False, encoding="utf-8-sig")

    write_interpretation(data, metrics, assignments, comparison)
    write_selection_recommendation(comparison, metrics)
    plot_outputs(comparison)

    print(
        json.dumps(
            {
                "records": len(data),
                "missing_by_column": {
                    column: int(count)
                    for column, count in data.isna().sum().items()
                    if count
                },
                "candidate_clusters": {
                    key: pd.Series(assignments[key]).value_counts().sort_index().to_dict()
                    for key, _, _ in CANDIDATES
                },
                "detailed_profile_rows": len(detailed),
                "candidate_comparison_rows": len(comparison),
                "output": str(OUTPUT),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
