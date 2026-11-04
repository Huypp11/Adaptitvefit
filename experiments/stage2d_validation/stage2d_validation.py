"""Reproduce internal sensitivity and new-record assignment checks for Stage 2D."""

from __future__ import annotations

import importlib.metadata
import json
import platform
import sys
from pathlib import Path
from typing import Any

import joblib
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy.optimize import linear_sum_assignment
from scipy.spatial.distance import cdist
from sklearn.metrics import (
    adjusted_rand_score,
    normalized_mutual_info_score,
    silhouette_score,
)
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler


ROOT = Path(__file__).resolve().parents[1]
STAGE2B = ROOT / "stage2b_clustering"
STAGE2C = ROOT / "stage2c_profiling"
OUTPUT = Path(__file__).resolve().parent
DATA_PATH = Path(r"C:\Users\mitsu\OneDrive\Documents\survey_cleaned.csv")
DICTIONARY_PATH = Path(r"C:\Users\mitsu\OneDrive\Documents\feature_dictionary.csv")
sys.path.insert(0, str(STAGE2B))

from clustering_experiment import (  # noqa: E402
    BASE_SPECS,
    CONFIGURATIONS,
    K_VALUES,
    create_analysis_frame,
    fit_kproto,
    make_model,
    numeric_spec,
    spec_for,
)
from clustering_pipeline import (  # noqa: E402
    GowerPreprocessor,
    NumericFeaturePreprocessor,
    nearest_medoid_assign,
)

CANDIDATES = [
    ("B|gower_agglomerative|k=2", "Gower + Agglomerative", "B", "gower_agglomerative"),
    ("A|kprototypes|k=2", "K-Prototypes", "A", "kprototypes"),
    ("A|kmeans_numeric|k=2", "K-Means numeric baseline", "A", "kmeans_numeric"),
]
TRAIN_FRACTIONS = (0.70, 0.80)
SPLIT_SEEDS = (13, 29, 42, 57, 71)
TEST_CASE_SEED = 42
STABILITY_COLUMNS = [
    "row_type",
    "candidate_key",
    "algorithm",
    "configuration",
    "train_fraction",
    "seed",
    "other_seed",
    "run_id",
    "other_run_id",
    "n_train",
    "n_test",
    "overlap_n",
    "train_cluster_sizes",
    "train_cluster_percent",
    "test_assignment_sizes",
    "test_assignment_percent",
    "test_silhouette",
    "test_silhouette_space",
    "test_silhouette_note",
    "test_ari_vs_stage2b_reference",
    "test_nmi_vs_stage2b_reference",
    "profile_tvd_mean",
    "profile_tvd_by_reference_cluster",
    "pairwise_test_ari",
    "pairwise_test_nmi",
    "notes",
]


def package_version(package: str) -> str:
    try:
        return importlib.metadata.version(package)
    except importlib.metadata.PackageNotFoundError:
        return "not installed"


def json_safe_scalar(value: Any) -> Any:
    if pd.isna(value):
        return None
    if isinstance(value, np.generic):
        return value.item()
    return value


def load_inputs() -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, dict[str, Any]]:
    data = pd.read_csv(DATA_PATH, encoding="utf-8-sig")
    dictionary = pd.read_csv(DICTIONARY_PATH, encoding="utf-8-sig")
    assignments = pd.read_csv(STAGE2B / "clustering_results.csv", encoding="utf-8-sig")
    artifacts = joblib.load(STAGE2B / "artifacts" / "candidate_models.joblib")
    stage2c_profiles = pd.read_csv(STAGE2C / "cluster_profiles_detailed.csv", encoding="utf-8-sig")
    stage2c_comparison = pd.read_csv(STAGE2C / "candidate_comparison.csv", encoding="utf-8-sig")
    stage2c_decision = (STAGE2C / "model_selection_recommendation.md").read_text(encoding="utf-8")
    if "Chưa đủ căn cứ" not in stage2c_decision:
        raise ValueError("Stage 2C model selection recommendation is missing or unexpected")
    for candidate_key, _, _, _ in CANDIDATES:
        if candidate_key not in set(stage2c_comparison["candidate_key"]):
            raise ValueError(f"Stage 2C comparison is missing candidate {candidate_key}")
        if candidate_key not in set(stage2c_profiles["candidate_key"]):
            raise ValueError(f"Stage 2C detailed profiles are missing candidate {candidate_key}")
    return data, dictionary, assignments, artifacts


def validate_inputs(
    data: pd.DataFrame,
    dictionary: pd.DataFrame,
    assignments: pd.DataFrame,
    artifacts: dict[str, Any],
) -> dict[str, dict[str, Any]]:
    if data.shape != (322, 19):
        raise ValueError(f"Expected cleaned survey shape (322, 19), got {data.shape}")
    if set(dictionary["standardized_variable"]) != set(data.columns):
        raise ValueError("Feature Dictionary does not match cleaned survey columns")
    if len(assignments) != len(data):
        raise ValueError("Stage 2B assignments do not match survey row count")
    if not np.array_equal(assignments["survey_row_index"].to_numpy(), np.arange(len(data))):
        raise ValueError("Stage 2B assignment order does not match survey row order")
    missing = data.isna().sum()
    actual_missing = {column: int(count) for column, count in missing.items() if count}
    if actual_missing != {"last_break_duration": 96, "stopping_reasons": 96}:
        raise ValueError(f"Unexpected survey missingness: {actual_missing}")

    bundles = artifacts["candidate_bundles"]
    for key, _, config, algorithm in CANDIDATES:
        if key not in assignments or key not in bundles:
            raise ValueError(f"Required Stage 2B candidate is missing: {key}")
        bundle = bundles[key]
        if bundle["algorithm"] != algorithm or bundle["configuration"] != config or int(bundle["k"]) != 2:
            raise ValueError(f"Candidate metadata mismatch for {key}")
        saved_labels = np.asarray(assignments[key], dtype=int)
        if not np.array_equal(saved_labels, np.asarray(bundle["labels"], dtype=int)):
            raise ValueError(f"Artifact labels do not match assignment CSV for {key}")
        fitted_labels = np.asarray(bundle["estimator"].labels_, dtype=int)
        if not np.array_equal(saved_labels, fitted_labels):
            raise ValueError(f"Estimator labels do not match assignment CSV for {key}")
        expected = set(CONFIGURATIONS[config])
        if set(bundle["feature_columns"]) != expected:
            raise ValueError(f"Stored feature set does not match configuration {config}")
    return bundles


def cluster_counts(labels: np.ndarray) -> tuple[str, str]:
    counts = pd.Series(labels).value_counts().sort_index()
    sizes = {str(int(key)): int(value) for key, value in counts.items()}
    percentages = {key: round(100 * value / len(labels), 3) for key, value in sizes.items()}
    return json.dumps(sizes), json.dumps(percentages)


def get_medoids(
    frame: pd.DataFrame,
    distance_matrix: np.ndarray,
    labels: np.ndarray,
) -> tuple[pd.DataFrame, np.ndarray]:
    medoid_rows = []
    medoid_labels = []
    for cluster in sorted(np.unique(labels)):
        member_indices = np.flatnonzero(labels == cluster)
        within_cluster = distance_matrix[np.ix_(member_indices, member_indices)]
        medoid_index = member_indices[int(np.argmin(within_cluster.sum(axis=1)))]
        medoid_rows.append(frame.iloc[medoid_index])
        medoid_labels.append(int(cluster))
    return pd.DataFrame(medoid_rows).reset_index(drop=True), np.asarray(medoid_labels, dtype=int)


def fit_and_assign(
    data: pd.DataFrame,
    config: str,
    algorithm: str,
    train_indices: np.ndarray,
    test_indices: np.ndarray,
    seed: int,
) -> dict[str, Any]:
    full_frame = create_analysis_frame(data, config)
    specs = spec_for(config)
    train_frame = full_frame.iloc[train_indices].reset_index(drop=True)
    test_frame = full_frame.iloc[test_indices].reset_index(drop=True)

    if algorithm == "gower_agglomerative":
        preprocessor = GowerPreprocessor(specs).fit(train_frame)
        train_distances = preprocessor.pairwise(train_frame)
        estimator = make_model(algorithm, 2, seed)
        train_labels = estimator.fit_predict(train_distances)
        medoids, medoid_labels = get_medoids(train_frame, train_distances, train_labels)
        test_to_medoids = preprocessor.pairwise(test_frame, medoids)
        test_labels, nearest_distance = nearest_medoid_assign(
            preprocessor, medoids, medoid_labels, test_frame
        )
        order = np.argsort(test_to_medoids, axis=1)
        margin = test_to_medoids[np.arange(len(test_frame)), order[:, 1]] - nearest_distance
        test_distances = preprocessor.pairwise(test_frame)
        silhouette_space = "khoảng cách Gower"
        inference_note = (
            "Agglomerative không có predict gốc; gán bằng medoid gần nhất, chỉ là xấp xỉ."
        )
        distance_note = "Khoảng cách Gower tới medoid gần nhất; không phải xác suất."
        fitted = {
            "preprocessor": preprocessor,
            "estimator": estimator,
            "medoids": medoids,
            "medoid_labels": medoid_labels,
        }
    elif algorithm == "kprototypes":
        train_labels, fitted_model, _ = fit_kproto(
            train_frame,
            specs,
            2,
            seed,
            n_init=5,
        )
        preprocessor = fitted_model["features"]
        test_matrix, categorical_indices = preprocessor.transform(test_frame)
        estimator = fitted_model["model"]
        test_labels = estimator.predict(test_matrix, categorical=categorical_indices)
        test_labels = np.asarray(test_labels, dtype=int)
        train_gower = GowerPreprocessor(specs).fit(train_frame)
        test_distances = train_gower.pairwise(test_frame)
        silhouette_space = "khoảng cách Gower (chỉ số ngoài cho K-Prototypes)"
        inference_note = "Dùng KPrototypes.predict với đúng preprocessing fit trên train."
        distance_note = "Estimator không cung cấp xác suất dự đoán; không báo độ tin cậy."
        nearest_distance = np.full(len(test_frame), np.nan)
        margin = np.full(len(test_frame), np.nan)
        fitted = {
            "preprocessor": preprocessor,
            "estimator": estimator,
        }
    elif algorithm == "kmeans_numeric":
        feature_preprocessor = NumericFeaturePreprocessor(numeric_spec(specs))
        train_values = feature_preprocessor.fit_transform(train_frame)
        scaler = StandardScaler().fit(train_values)
        train_scaled = scaler.transform(train_values)
        estimator = make_model(algorithm, 2, seed)
        train_labels = estimator.fit_predict(train_scaled)
        test_values = feature_preprocessor.transform(test_frame)
        test_scaled = scaler.transform(test_values)
        test_labels = estimator.predict(test_scaled)
        test_labels = np.asarray(test_labels, dtype=int)
        distances_to_centers = cdist(test_scaled, estimator.cluster_centers_, metric="euclidean")
        order = np.argsort(distances_to_centers, axis=1)
        nearest_distance = distances_to_centers[np.arange(len(test_frame)), order[:, 0]]
        margin = distances_to_centers[np.arange(len(test_frame)), order[:, 1]] - nearest_distance
        diff = test_scaled[:, None, :] - test_scaled[None, :, :]
        test_distances = np.sqrt(np.einsum("ijk,ijk->ij", diff, diff))
        silhouette_space = "Euclidean trên numeric/ordinal đã chuẩn hóa"
        inference_note = "Dùng KMeans.predict với preprocessor và scaler fit trên train."
        distance_note = "Khoảng cách tới tâm gần nhất và margin tới tâm thứ hai; không phải xác suất."
        fitted = {
            "preprocessor": feature_preprocessor,
            "scaler": scaler,
            "estimator": estimator,
        }
    else:
        raise ValueError(f"Unsupported candidate algorithm: {algorithm}")

    test_silhouette: float | None = None
    silhouette_note = ""
    if len(np.unique(test_labels)) < 2:
        silhouette_note = "Không tính được: tập kiểm tra được gán vào một cụm duy nhất."
    else:
        try:
            if algorithm == "kmeans_numeric":
                test_silhouette = float(silhouette_score(test_distances, test_labels, metric="precomputed"))
            else:
                test_silhouette = float(silhouette_score(test_distances, test_labels, metric="precomputed"))
        except ValueError as exc:
            silhouette_note = f"Không tính được silhouette hợp lệ: {exc}"

    return {
        "train_indices": np.asarray(train_indices, dtype=int),
        "test_indices": np.asarray(test_indices, dtype=int),
        "train_frame": train_frame,
        "test_frame": test_frame,
        "train_labels": np.asarray(train_labels, dtype=int),
        "test_labels": test_labels,
        "test_silhouette": test_silhouette,
        "test_silhouette_space": silhouette_space,
        "test_silhouette_note": silhouette_note,
        "inference_note": inference_note,
        "distance_note": distance_note,
        "nearest_distance": nearest_distance,
        "distance_margin": margin,
        "fitted": fitted,
    }


def align_cluster_ids(
    run_labels: np.ndarray,
    reference_labels: np.ndarray,
) -> dict[int, int]:
    run_ids = sorted(np.unique(run_labels).tolist())
    ref_ids = sorted(np.unique(reference_labels).tolist())
    contingency = np.zeros((len(run_ids), len(ref_ids)), dtype=int)
    for i, run_id in enumerate(run_ids):
        for j, ref_id in enumerate(ref_ids):
            contingency[i, j] = int(np.sum((run_labels == run_id) & (reference_labels == ref_id)))
    row_indices, col_indices = linear_sum_assignment(-contingency)
    return {int(run_ids[i]): int(ref_ids[j]) for i, j in zip(row_indices, col_indices)}


def marginal_tvd(
    reference: pd.Series,
    candidate: pd.Series,
) -> float:
    ref = reference.astype(str).value_counts(normalize=True)
    cand = candidate.astype(str).value_counts(normalize=True)
    categories = set(ref.index) | set(cand.index)
    return float(
        0.5
        * sum(
            abs(float(ref.get(category, 0.0)) - float(cand.get(category, 0.0)))
            for category in categories
        )
    )


def profile_drift(
    data: pd.DataFrame,
    full_frame: pd.DataFrame,
    feature_columns: list[str],
    train_indices: np.ndarray,
    train_labels: np.ndarray,
    reference_labels: np.ndarray,
) -> tuple[float, dict[str, float]]:
    mapping = align_cluster_ids(train_labels, reference_labels[train_indices])
    cluster_drift: dict[str, float] = {}
    for reference_cluster in sorted(np.unique(reference_labels).tolist()):
        run_cluster = next(
            (label for label, ref_label in mapping.items() if ref_label == reference_cluster),
            None,
        )
        if run_cluster is None:
            cluster_drift[str(reference_cluster)] = float("nan")
            continue
        run_mask = train_labels == run_cluster
        reference_mask = reference_labels == reference_cluster
        feature_scores = []
        for column in feature_columns:
            train_values = full_frame.iloc[train_indices].loc[run_mask, column]
            reference_values = full_frame.loc[reference_mask, column]
            feature_scores.append(marginal_tvd(reference_values, train_values))
        cluster_drift[str(reference_cluster)] = float(np.mean(feature_scores))
    return float(np.mean(list(cluster_drift.values()))), cluster_drift


def select_profile_row(
    data: pd.DataFrame,
    pool: pd.DataFrame,
    predicate,
    case_name: str,
    used_indices: set[int],
) -> pd.Series:
    matches = pool[predicate(pool) & ~pool.index.isin(used_indices)]
    if matches.empty:
        raise ValueError(f"No real held-out survey record matches required case: {case_name}")
    selected_index = matches.index[0]
    used_indices.add(int(selected_index))
    return data.loc[selected_index].copy()


def build_new_user_cases(
    data: pd.DataFrame,
    holdout_indices: np.ndarray,
) -> dict[str, tuple[pd.Series, str, str | None, Any]]:
    pool = data.loc[holdout_indices]
    cases: dict[str, tuple[pd.Series, str, str | None, Any]] = {}
    used_indices: set[int] = set()
    cases["actual_regular_training"] = (
        select_profile_row(
            data,
            pool,
            lambda x: (x["workout_days_last_7d"] >= 4)
            & (x["usual_session_duration"] != "not_currently_training"),
            "người đang tập thường xuyên",
            used_indices,
        ),
        "Dòng khảo sát thực tế được giữ ngoài tập train; đang tập ít nhất 4 ngày tuần gần nhất.",
        None,
        None,
    )
    cases["actual_below_goal"] = (
        select_profile_row(
            data,
            pool,
            lambda x: (x["workout_days_last_7d"] > 0)
            & (x["workout_days_last_7d"] < x["target_sessions_per_week"]),
            "người đang tập ít hơn mục tiêu",
            used_indices,
        ),
        "Dòng thực tế held-out có số ngày tập tuần trước thấp hơn số buổi mục tiêu.",
        None,
        None,
    )
    cases["actual_not_training"] = (
        select_profile_row(
            data,
            pool,
            lambda x: x["usual_session_duration"] == "not_currently_training",
            "người hiện không tập",
            used_indices,
        ),
        "Dòng khảo sát thực tế held-out có trạng thái not_currently_training.",
        None,
        None,
    )
    cases["actual_high_stress_low_energy"] = (
        select_profile_row(
            data,
            pool,
            lambda x: (x["stress_level"] >= 4) & (x["energy_level"] <= 2),
            "stress cao và năng lượng thấp",
            used_indices,
        ),
        "Dòng thực tế held-out có stress >=4 và năng lượng <=2 trên thang 1-5.",
        None,
        None,
    )
    cases["actual_limited_free_time"] = (
        select_profile_row(
            data,
            pool,
            lambda x: x["daily_available_training_time"].isin(["under_15_min", "15_30_min"]),
            "thời gian rảnh hạn chế",
            used_indices,
        ),
        "Dòng khảo sát thực tế held-out thuộc một trong hai mức thời gian khả dụng thấp nhất.",
        None,
        None,
    )
    cases["actual_q8_q9_missing"] = (
        select_profile_row(
            data,
            pool,
            lambda x: x["last_break_duration"].isna() & x["stopping_reasons"].isna(),
            "bản ghi thực tế thiếu Q8/Q9",
            used_indices,
        ),
        "Dòng thực tế held-out thiếu Q8/Q9; hai trường này không thuộc feature của ba ứng viên.",
        None,
        None,
    )

    source = cases["actual_below_goal"][0].copy()
    missing_case = source.copy()
    missing_case["stress_level"] = np.nan
    cases["fault_missing_required_feature"] = (
        missing_case,
        "Fault-injection trên một dòng khảo sát thật held-out: đặt stress_level thành missing; không phải dữ liệu người dùng mới.",
        "stress_level",
        np.nan,
    )

    unknown_ordinal = source.copy()
    unknown_ordinal["sleep_duration"] = "unseen_ordinal_category"
    cases["fault_unknown_ordinal"] = (
        unknown_ordinal,
        "Fault-injection trên dòng thật: thay sleep_duration bằng category ordinal chưa từng thấy; không phải nhãn thật.",
        "sleep_duration",
        "unseen_ordinal_category",
    )

    unknown_nominal = source.copy()
    unknown_nominal["usual_training_location"] = "unseen_location_category"
    cases["fault_unknown_nominal"] = (
        unknown_nominal,
        "Fault-injection trên dòng thật: thay địa điểm bằng category nominal chưa từng thấy; không phải dữ liệu khảo sát mới.",
        "usual_training_location",
        "unseen_location_category",
    )
    return cases


def assignment_preflight(
    row: pd.Series,
    feature_columns: list[str],
    specs: dict[str, dict[str, Any]],
    train_frame: pd.DataFrame,
) -> tuple[str, str, list[str]]:
    issues = []
    missing = []
    unknown_ordinal = []
    unknown_nominal = []
    for column in feature_columns:
        value = row[column]
        spec = specs[column]
        if pd.isna(value):
            missing.append(column)
            continue
        kind = spec["kind"]
        if kind in {"ordinal", "ordinal_special"}:
            valid = {str(category) for category in spec["order"]}
            if kind == "ordinal_special":
                valid.add(str(spec["special"]))
            if str(value) not in valid:
                unknown_ordinal.append(column)
        elif kind == "numeric":
            number = pd.to_numeric(pd.Series([value]), errors="coerce").iloc[0]
            if pd.isna(number) or not float(spec["min"]) <= float(number) <= float(spec["max"]):
                issues.append(f"{column}: giá trị numeric không hợp lệ hoặc ngoài miền")
        elif kind == "nominal":
            known = set(train_frame[column].dropna().astype(str))
            if str(value) not in known:
                unknown_nominal.append(column)
    if missing:
        return "refused_missing", f"Thiếu feature bắt buộc: {', '.join(missing)}; không tự điền.", issues
    if unknown_ordinal:
        return (
            "refused_unknown_ordinal",
            f"Category ordinal chưa biết: {', '.join(unknown_ordinal)}; từ chối gán, không tự mã hóa.",
            issues,
        )
    if issues:
        return "refused_invalid_value", "; ".join(issues), issues
    if unknown_nominal:
        return (
            "assigned_unknown_nominal",
            f"Category nominal mới ở {', '.join(unknown_nominal)} được coi như mismatch trong Gower; kết quả không được hiệu chuẩn.",
            unknown_nominal,
        )
    return "assigned", "Đầu vào hợp lệ theo feature và miền giá trị của ứng viên.", []


def predict_new_row(
    row: pd.Series,
    fit_result: dict[str, Any],
    config: str,
    algorithm: str,
) -> tuple[int, float | None, float | None]:
    single = pd.DataFrame([{column: row[column] for column in fit_result["train_frame"].columns}])
    model = fit_result["fitted"]["estimator"]
    if algorithm == "gower_agglomerative":
        labels, distances = nearest_medoid_assign(
            fit_result["fitted"]["preprocessor"],
            fit_result["fitted"]["medoids"],
            fit_result["fitted"]["medoid_labels"],
            single,
        )
        all_distances = fit_result["fitted"]["preprocessor"].pairwise(
            single, fit_result["fitted"]["medoids"]
        )[0]
        ordered = np.sort(all_distances)
        margin = float(ordered[1] - ordered[0]) if len(ordered) > 1 else None
        return int(labels[0]), float(distances[0]), margin
    if algorithm == "kprototypes":
        values, categorical_indices = fit_result["fitted"]["preprocessor"].transform(single)
        labels = model.predict(values, categorical=categorical_indices)
        return int(labels[0]), None, None
    frame = create_analysis_frame(pd.DataFrame([row]), config)
    fitted = fit_result["fitted"]
    preprocessor = fitted["preprocessor"]
    numeric = preprocessor.transform(frame)
    scaled = fitted["scaler"].transform(numeric)
    distances = cdist(scaled, model.cluster_centers_, metric="euclidean")[0]
    ordered = np.sort(distances)
    labels = model.predict(scaled)
    return int(labels[0]), float(ordered[0]), float(ordered[1] - ordered[0])


def make_assignment_tests(
    data: pd.DataFrame,
    fit_results: dict[str, dict[str, Any]],
) -> pd.DataFrame:
    holdout_indices = fit_results[CANDIDATES[0][0]]["test_indices"]
    cases = build_new_user_cases(data, holdout_indices)
    rows = []
    for key, label, config, algorithm in CANDIDATES:
        fitted = fit_results[key]
        bundle_features = list(fitted["train_frame"].columns)
        specs = spec_for(config)
        for case_name, (row, description, mutated_feature, mutated_value) in cases.items():
            status, explanation, issues = assignment_preflight(
                row, bundle_features, specs, fitted["train_frame"]
            )
            assigned_cluster = None
            nearest_distance = None
            distance_margin = None
            assignment_status = status
            if status in {"assigned", "assigned_unknown_nominal"}:
                assigned_cluster, nearest_distance, distance_margin = predict_new_row(
                    row, fitted, config, algorithm
                )
            values = {column: json_safe_scalar(row[column]) for column in bundle_features}
            used_unknown_nominal = [
                feature
                for feature in issues
                if feature in bundle_features and specs.get(feature, {}).get("kind") == "nominal"
            ]
            if status == "assigned" and case_name == "fault_unknown_nominal":
                assignment_status = "assigned_unknown_field_not_used"
                explanation = (
                    "Cột địa điểm category mới không nằm trong cấu hình A nên bị bỏ qua; "
                    "kết quả chỉ dựa trên feature cấu hình A."
                )
            if used_unknown_nominal and status == "assigned_unknown_nominal":
                assignment_status = "assigned_unknown_nominal"
            rows.append(
                {
                    "candidate_key": key,
                    "algorithm": label,
                    "configuration": config,
                    "case": case_name,
                    "case_description": description,
                    "input_features_json": json.dumps(values, ensure_ascii=False, allow_nan=False, default=str),
                    "mutated_feature_for_robustness_test": mutated_feature or "",
                    "assignment_status": assignment_status,
                    "assigned_cluster": assigned_cluster,
                    "nearest_representative_distance": nearest_distance,
                    "distance_margin_to_second_representative": distance_margin,
                    "confidence_probability": np.nan,
                    "distance_or_confidence_note": fitted["distance_note"],
                    "input_handling": explanation,
                    "rejected_fields": json.dumps(issues, ensure_ascii=False),
                    "split_train_fraction": 0.80,
                    "split_seed": TEST_CASE_SEED,
                    "is_actual_unmodified_heldout_record": case_name.startswith("actual_"),
                    "label_is_ground_truth": False,
                }
            )
    return pd.DataFrame(rows)


def product_usefulness_rows(
    stability: pd.DataFrame,
    assignment_tests: pd.DataFrame,
) -> pd.DataFrame:
    assessments = {
        "Gower + Agglomerative": {
            "Stability": ("Trung bình", "", "ARI giữa split với nhãn tham chiếu thấp hơn độ tương đồng giữa các test overlap; đây vẫn chỉ là kiểm tra nội bộ."),
            "Assignment": ("Có điều kiện", "", "Đây là mở rộng xấp xỉ, Agglomerative không có predict gốc."),
            "Interpretability": ("Cao nhưng hẹp", "Một cụm 68 người đều đang không tập; rất dễ mô tả trạng thái này.", "Có nguy cơ chủ yếu tách theo trạng thái trong feature."),
            "Behavioral value": ("Trung bình", "Ngoài trạng thái không tập, cụm còn mô tả số ngày tập và gap.", "Chưa chứng minh thêm thông tin vượt qua các feature gốc."),
            "Product usefulness": ("Trung bình-thấp", "Có thể là ứng viên luồng khảo sát/người hiện không tập trong pilot offline.", "Chưa có đánh giá người dùng hoặc kết quả recommendation."),
            "Missing robustness": ("Thấp", "Missing feature bắt buộc bị từ chối rõ ràng; Q8/Q9 ngoài cấu hình được bỏ qua.", "Không có xử lý missing-aware hay imputation."),
            "Operational complexity": ("Cao", "Cần lưu medoids, Gower spec, schema và logic gán mở rộng.", "Medoid assignment có thể lệch khỏi clustering gốc."),
        },
        "K-Prototypes": {
            "Stability": ("Trung bình", "", "ARI giữa các split thấp hơn hai phương pháp còn lại; chưa có mẫu độc lập."),
            "Assignment": ("Cao trên dữ liệu hợp lệ", "", "Không có xác suất/confidence chuẩn; unknown ordinal bị từ chối."),
            "Interpretability": ("Trung bình", "Hai cụm gần cân bằng, có khác biệt hành vi và trạng thái cá nhân.", "Không có tên cụm tự nhiên; silhouette là Gower ngoài objective."),
            "Behavioral value": ("Trung bình-cao", "A tách nhóm có tần suất tập và gap khác nhau.", "Các khác biệt cùng lúc được dùng tạo cụm, chưa phải giá trị tăng thêm."),
            "Product usefulness": ("Trung bình", "Có thể thử mapping gợi ý riêng ở offline replay.", "Chưa đo hiệu quả so với rule-based/check-in."),
            "Missing robustness": ("Thấp", "Missing feature bắt buộc bị từ chối; không imputing.", "Cần đầy đủ mọi feature cấu hình A."),
            "Operational complexity": ("Trung bình-cao", "Cần giữ đúng encoding hỗn hợp và thư viện kmodes.", "Cần theo dõi phiên bản và unknown values."),
        },
        "K-Means numeric baseline": {
            "Stability": ("Cao trong kiểm tra nội bộ", "", "Độ ổn định không chứng minh tính hữu ích ngoài mẫu."),
            "Assignment": ("Cao trên dữ liệu hợp lệ", "", "Khoảng cách/margin không phải xác suất hiệu chuẩn."),
            "Interpretability": ("Trung bình-cao", "Hai cụm cân bằng, khác về tần suất tập, gap và trạng thái cá nhân.", "Các feature được chuẩn hóa; distance không trực quan với người dùng."),
            "Behavioral value": ("Trung bình-cao", "Dễ triển khai baseline numeric/ordinal với nhóm tập ít/nhiều.", "Không dùng địa điểm hay biến nominal để tạo cụm."),
            "Product usefulness": ("Trung bình", "Có thể đánh giá offline như baseline segment.", "Chưa chứng minh vượt qua rule/check-in dùng trực tiếp feature."),
            "Missing robustness": ("Thấp", "Missing feature bắt buộc bị từ chối; Q8/Q9 ngoài cấu hình A không ảnh hưởng.", "Không tự xử lý missing đầu vào."),
            "Operational complexity": ("Thấp", "Dùng scikit-learn scaler và KMeans; pipeline dự đoán đơn giản.", "Phải khóa thứ tự feature và scaler đã fit."),
        },
    }
    rows = []
    criterion_translations = {
        "Stability": "Độ ổn định",
        "Assignment": "Khả năng gán người dùng mới",
        "Interpretability": "Khả năng giải thích",
        "Behavioral value": "Giá trị mô tả hành vi",
        "Product usefulness": "Khả năng hỗ trợ sản phẩm",
        "Missing robustness": "Khả năng chịu missing",
        "Operational complexity": "Độ phức tạp vận hành",
    }
    for key, label, _, _ in CANDIDATES:
        runs = stability[
            (stability["row_type"] == "run") & (stability["algorithm"] == label)
        ]
        pairs = stability[
            (stability["row_type"] == "pairwise")
            & (stability["algorithm"] == label)
            & stability["pairwise_test_ari"].notna()
        ]
        actual_tests = assignment_tests[
            (assignment_tests["candidate_key"] == key)
            & assignment_tests["case"].str.startswith("actual_")
        ]
        assigned_actual = int(actual_tests["assigned_cluster"].notna().sum())
        missing_tests = assignment_tests[
            (assignment_tests["candidate_key"] == key)
            & (assignment_tests["case"] == "fault_missing_required_feature")
        ]
        unknown_tests = assignment_tests[
            (assignment_tests["candidate_key"] == key)
            & (assignment_tests["case"] == "fault_unknown_ordinal")
        ]
        evidence_by_criterion = {
            "Stability": (
                f"10 lần fit; ARI trung bình so với nhãn Stage 2B trên test "
                f"{runs['test_ari_vs_stage2b_reference'].mean():.3f}; "
                f"ARI cặp trên {len(pairs)} cặp overlap hợp lệ "
                f"{pairs['pairwise_test_ari'].mean():.3f}."
            ),
            "Assignment": (
                f"Gán được {assigned_actual}/{len(actual_tests)} ca khảo sát thật held-out; "
                f"missing feature bắt buộc và unknown ordinal lần lượt "
                f"{missing_tests['assignment_status'].iloc[0]} và "
                f"{unknown_tests['assignment_status'].iloc[0]}."
            ),
        }
        for criterion, (rating, evidence, limitation) in assessments[label].items():
            if criterion in evidence_by_criterion:
                evidence = evidence_by_criterion[criterion]
            rows.append(
                {
                    "candidate": label,
                    "candidate_key": key,
                    "criterion": criterion_translations[criterion],
                    "assessment": rating,
                    "evidence": evidence,
                    "limitation": limitation,
                    "integration_recommendation": "Chưa chọn tích hợp; tiếp tục kiểm chứng ngoại tuyến.",
                }
            )
    return pd.DataFrame(rows)


def create_charts(
    stability: pd.DataFrame,
    assignment_tests: pd.DataFrame,
    usefulness: pd.DataFrame,
) -> None:
    run_rows = stability[stability["row_type"] == "run"].copy()
    candidate_order = [label for _, label, _, _ in CANDIDATES]
    grouped = run_rows.groupby("algorithm")["test_ari_vs_stage2b_reference"]
    means = [float(grouped.mean()[label]) for label in candidate_order]
    stds = [float(grouped.std(ddof=1)[label]) for label in candidate_order]
    fig, ax = plt.subplots(figsize=(9, 5))
    ax.bar(candidate_order, means, yerr=stds, capsize=5, color=["#4477aa", "#228833", "#ccbb44"])
    ax.set_ylim(0, 1.05)
    ax.set_ylabel("ARI so với nhãn tham chiếu Stage 2B trên test (TB ± ĐLC)")
    ax.set_title("Độ nhạy lặp lại trên cùng khảo sát; không phải xác thực ngoài mẫu")
    ax.tick_params(axis="x", rotation=16)
    ax.grid(axis="y", alpha=0.25)
    fig.tight_layout()
    fig.savefig(OUTPUT / "figures_stability_validation.png", dpi=160, bbox_inches="tight")
    plt.close(fig)

    valid_assignments = assignment_tests[
        assignment_tests["assigned_cluster"].notna()
    ].copy()
    pivot = valid_assignments.pivot_table(
        index="case",
        columns="algorithm",
        values="assigned_cluster",
        aggfunc="first",
    )
    fig, ax = plt.subplots(figsize=(12, 6))
    image = ax.imshow(pivot.to_numpy(dtype=float), cmap="Set1", vmin=0, vmax=1, aspect="auto")
    ax.set_xticks(np.arange(len(pivot.columns)), list(pivot.columns), rotation=16, ha="right")
    ax.set_yticks(np.arange(len(pivot.index)), [str(case) for case in pivot.index])
    ax.set_title("Nhãn gán cho các ca test held-out (nhãn không phải ground truth)")
    for i in range(len(pivot.index)):
        for j in range(len(pivot.columns)):
            value = pivot.iloc[i, j]
            if pd.notna(value):
                ax.text(j, i, f"Cụm {int(value)}", ha="center", va="center", fontsize=8)
    fig.colorbar(image, ax=ax, ticks=[0, 1], label="Mã cụm")
    fig.tight_layout()
    fig.savefig(OUTPUT / "figures_cluster_assignment.png", dpi=160, bbox_inches="tight")
    plt.close(fig)

    selected = usefulness[
        usefulness["criterion"].isin(
            [
                "Độ ổn định",
                "Khả năng gán người dùng mới",
                "Khả năng giải thích",
                "Giá trị mô tả hành vi",
                "Khả năng hỗ trợ sản phẩm",
                "Khả năng chịu missing",
                "Độ phức tạp vận hành",
            ]
        )
    ]
    rating_order = {
        "Thấp": 1,
        "Thấp-cao": 1,
        "Trung bình-thấp": 2,
        "Trung bình": 3,
        "Trung bình-cao": 4,
        "Cao trong kiểm tra nội bộ": 4,
        "Cao trên dữ liệu hợp lệ": 4,
        "Cao": 5,
    }
    matrix = selected.pivot(index="candidate", columns="criterion", values="assessment")

    def rating(value: Any) -> int:
        text = str(value)
        for prefix, score in rating_order.items():
            if text.startswith(prefix):
                return score
        return 3

    numeric = matrix.apply(lambda column: column.map(rating))
    fig, ax = plt.subplots(figsize=(12, 4))
    image = ax.imshow(numeric.to_numpy(dtype=float), cmap="RdYlGn", vmin=1, vmax=5, aspect="auto")
    ax.set_xticks(np.arange(len(numeric.columns)), list(numeric.columns), rotation=25, ha="right")
    ax.set_yticks(np.arange(len(numeric.index)), list(numeric.index))
    ax.set_title("Đánh giá định tính về giá trị ứng dụng (thang màu chỉ để đọc bảng)")
    ax.set_xlabel("Không thay thế phần giải thích và giới hạn bằng số hóa")
    fig.colorbar(image, ax=ax, ticks=[1, 2, 3, 4, 5], label="Thấp → Cao")
    fig.tight_layout()
    fig.savefig(OUTPUT / "figures_candidate_usefulness.png", dpi=160, bbox_inches="tight")
    plt.close(fig)


def build_report(
    data: pd.DataFrame,
    dictionary: pd.DataFrame,
    stability: pd.DataFrame,
    assignment_tests: pd.DataFrame,
    usefulness: pd.DataFrame,
) -> None:
    versions = {
        package: package_version(package)
        for package in ["numpy", "pandas", "scipy", "scikit-learn", "matplotlib", "joblib", "kmodes"]
    }
    run_rows = stability[stability["row_type"] == "run"]
    pair_rows = stability[stability["row_type"] == "pairwise"]
    lines = [
        "# AdaptiveFit AI - Stage 2D: Kiểm chứng độ bền vững và khả năng gán cụm",
        "",
        "## Tóm tắt và quyết định",
        "",
        "**Quyết định: C. Chưa sẵn sàng tích hợp clustering vào Recommendation Engine.** Ba ứng viên có thể tiếp tục được đánh giá ngoại tuyến. Hiện chưa có dữ liệu khảo sát mới hay bằng chứng sản phẩm cho thấy cụm cung cấp giá trị tăng thêm so với check-in, lịch sử và rule-based recommendation.",
        "",
        "Đây là kiểm tra độ nhạy nội bộ trên cùng 322 người khảo sát, bằng các phép chia train/test lặp lại. Không được diễn giải là xác thực ngoài mẫu hoặc bằng chứng cho toàn bộ người trẻ Việt Nam.",
        "",
        "## Dữ liệu, artifact và tái lập",
        "",
        f"- Dữ liệu sạch: {len(data)} dòng × {data.shape[1]} cột; Feature Dictionary có {len(dictionary)} biến.",
        "- Đọc trực tiếp `stage2b_clustering/clustering_results.csv`, `clustering_metrics.csv`, `cluster_profiles.csv`, model bundles đã lưu, mã preprocessing Stage 2B; đọc thêm kết quả profiling và khuyến nghị Stage 2C.",
        "- Đã xác nhận nhãn assignment CSV khớp nguyên thứ tự dòng với nhãn đã lưu trong bundle và `estimator.labels_` cho ba ứng viên.",
        "- Missing gốc: `last_break_duration` 96/322 và `stopping_reasons` 96/322; không điền giá trị. Cả hai ngoài feature của ba ứng viên.",
        f"- Phiên bản Python: {platform.python_version()}; thư viện: `{json.dumps(versions, ensure_ascii=False)}`.",
        f"- Thiết kế lặp: train fractions={list(TRAIN_FRACTIONS)}, seeds={list(SPLIT_SEEDS)}; mỗi ứng viên có {len(TRAIN_FRACTIONS) * len(SPLIT_SEEDS)} lần fit; tổng cộng {len(run_rows)} lần fit.",
        "- Mỗi lần chia: estimator, scaler và mọi preprocessing chỉ được fit trên train; tập test chỉ được transform và gán nhãn. K-Prototypes có thêm 5 lần khởi tạo nội bộ cho mỗi fit.",
        "",
        "## Phương pháp kiểm chứng",
        "",
        "- Mỗi ứng viên được fit từ đầu theo đúng feature/spec Stage 2B trên split train ngẫu nhiên 70/30 hoặc 80/20. Không dùng nhãn dropout.",
        "- Gower + Agglomerative: fit ma trận Gower chỉ trên train; tính medoid của mỗi cụm từ train; gán test/người mới vào medoid gần nhất. Đây là mở rộng xấp xỉ, không phải `predict` nguyên bản.",
        "- K-Prototypes: fit preprocessor và model trên train; dùng chính preprocessing đó và `KPrototypes.predict` cho test.",
        "- K-Means: fit ordinal/numeric preprocessing, StandardScaler và KMeans trên train; tái sử dụng để transform/predict test.",
        "- Silhouette trên test chỉ được tính trong metric phù hợp cho từng phương pháp; K-Prototypes dùng Gower như chẩn đoán ngoài objective. Không xếp hạng silhouette giữa các không gian khác nhau.",
        "- ARI/NMI so với Stage 2B là mức tương đồng với phân cụm tham chiếu đã fit trên toàn bộ cùng khảo sát, không phải nhãn đúng. ARI/NMI giữa các lần chạy được tính trên phần cá nhân cùng có mặt trong cả hai test set.",
        "- Profile drift là trung bình khoảng cách biến thiên tổng (TVD) của phân phối biên từng feature giữa cluster train đã căn chỉnh và cluster tham chiếu Stage 2B. Đây cũng là so sánh nội bộ, không phải validation ngoài mẫu.",
        "",
        "## Kết quả độ nhạy",
        "",
        "| Ứng viên | Số lần fit | ARI TB ± ĐLC so với tham chiếu Stage 2B trên test | NMI TB so với tham chiếu | Cặp overlap hợp lệ | ARI cặp (TB) | NMI cặp (TB) | Profile TVD TB | Silhouette test TB* |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for _, label, _, _ in CANDIDATES:
        runs = run_rows[run_rows["algorithm"] == label]
        pairs = pair_rows[
            (pair_rows["algorithm"] == label) & pair_rows["pairwise_test_ari"].notna()
        ]
        silhouette_values = runs["test_silhouette"].dropna()
        silhouette_summary = f"{silhouette_values.mean():.3f}" if len(silhouette_values) else "N/A"
        lines.append(
            f"| {label} | {len(runs)} | "
            f"{runs['test_ari_vs_stage2b_reference'].mean():.3f} ± {runs['test_ari_vs_stage2b_reference'].std(ddof=1):.3f} | "
            f"{runs['test_nmi_vs_stage2b_reference'].mean():.3f} | "
            f"{len(pairs)}/{len(runs) * (len(runs) - 1) // 2} | "
            f"{pairs['pairwise_test_ari'].mean():.3f} | {pairs['pairwise_test_nmi'].mean():.3f} | "
            f"{runs['profile_tvd_mean'].mean():.3f} | {silhouette_summary} |"
        )
    lines += [
        "",
        "*Silhouette test được tổng hợp chỉ trong từng thuật toán/không gian metric; không dùng để so sánh chéo thuật toán. Xem từng split, cluster sizes, profile TVD và ghi chú tính được trong `stability_validation.csv`.",
        "",
        "ARI/NMI cặp giữa các split đo độ tương đồng trên phần bản ghi test giao nhau; các cặp có dưới 20 người giao nhau không được tính và được ghi NaN trong CSV. Các split dùng cùng nguồn 322 người và có nhiều quan sát trùng lặp. Kết quả chỉ phản ánh độ nhạy nội bộ, không chứng minh cấu trúc sẽ lặp lại ở người dùng mới ngoài khảo sát.",
        "",
        "## Khả năng gán người dùng mới",
        "",
        "Các ca kiểm thử hợp lệ được chọn từ các dòng khảo sát thật trong test partition của split train=80%, seed=42. Không có nhãn thật cho các ca; cluster được gán chỉ là output estimator. Các ca thiếu feature bắt buộc và category ordinal chưa biết là fault-injection trên một dòng khảo sát thật để kiểm tra xử lý lỗi, không phải dữ liệu người dùng giả hay nhãn thật. Ca category nominal mới cũng là fault-injection.",
        "",
        "- Missing trong feature bắt buộc: từ chối gán, không điền tự động.",
        "- Unknown ordinal: từ chối gán vì thứ tự không được xác định.",
        "- Unknown nominal trong Gower B: được tính như mismatch và trả nearest-medoid, nhưng giá trị chưa từng thấy làm khoảng cách khó diễn giải; không có hiệu chuẩn confidence.",
        "- Unknown nominal ở cấu hình A của K-Prototypes/K-Means: địa điểm không phải input nên bị bỏ qua; phép thử không chứng minh khả năng xử lý unknown của feature nominal không có trong mô hình.",
        "- Q8/Q9 missing thực tế: có thể gán vì không nằm trong feature của ba ứng viên. Đây không phải xử lý missing-aware cho các feature đang dùng.",
        "- K-Means cung cấp khoảng cách tới centroid gần nhất và margin; K-Means không cung cấp xác suất đã hiệu chuẩn. K-Prototypes không có probability/confidence chuẩn trong API predict được dùng ở đây. Gower cung cấp khoảng cách/margin tới medoid, không phải xác suất.",
        "",
        "`new_user_assignment_tests.csv` ghi từng đầu vào feature, trạng thái, nhãn gán nếu có, khoảng cách/margin nếu có và lý do từ chối/giới hạn. Không diễn giải nhãn từ một trường hợp đơn lẻ thành nhóm người dùng ổn định.",
        "",
        "## Giá trị ứng dụng AdaptiveFit",
        "",
        "Xem `product_usefulness_comparison.csv` cho từng tiêu chí, bằng chứng và giới hạn.",
        "",
        "- Cụm chủ yếu tổng hợp lại hành vi, thời lượng, mục tiêu và trạng thái cá nhân vốn đã hiện diện trong input. Chưa có phân tích chứng minh thông tin tăng thêm so với rule/check-in trực tiếp.",
        "- Các thống kê có thể gợi ý giả thuyết để thử nghiệm điều chỉnh thời lượng hoặc hỏi về mục tiêu/thời gian rảnh; không nên để cụm ép người cùng nhóm nhận cùng bài tập/thời lượng.",
        "- Không suy ra cường độ an toàn/y tế từ stress, năng lượng hay tâm trạng; các biến tự khai báo không thay cho đánh giá sức khỏe.",
        "- Nếu chỉ dùng check-in hiện tại + lịch sử, chưa có bằng chứng hiện tại cho thấy sẽ mất thông tin quan trọng: các feature tạo cụm đều được tạo từ những câu trả lời đó, và chưa đo utility increment.",
        "- Không dùng clustering như nhãn dropout hoặc nguy cơ bỏ tập.",
        "",
        "## Hạn chế",
        "",
        "- Không có survey mẫu mới, dữ liệu theo dõi, outcome recommendation hay thử nghiệm người dùng; không thể xác nhận khả năng khái quát hoặc giá trị sản phẩm.",
        "- Chia train/test trên cùng survey không tạo dữ liệu độc lập; repeated splits chồng lặp người trả lời.",
        "- Agglomerative assignment bằng medoid là xấp xỉ và có thể gán khác kết quả nếu chạy lại clustering trên toàn tập.",
        "- Một người có missing/unknown trong feature ordinal bị từ chối. Chưa thiết kế chiến lược xử lý input thiếu/unknown cho sản phẩm.",
        "- NMI/ARI giữa các split phụ thuộc số lượng bản ghi test giao nhau; `overlap_n` được lưu cho từng cặp để kiểm tra.",
        "- Dữ liệu 322 phản hồi tự khai báo, cắt ngang và chưa xác lập đại diện toàn bộ người trẻ Việt Nam.",
        "",
        "## Kết luận cuối cùng",
        "",
        "**C. Clustering chưa sẵn sàng tích hợp.** Có thể duy trì K-Means A như baseline assignment dễ triển khai, K-Prototypes A như phương án hỗn hợp và Gower B như phép tách trạng thái không tập để tiếp tục kiểm chứng. Chưa chọn ứng viên Stage 3 vì tính ổn định hiện tại là nội bộ; chưa chứng minh cụm đem lại lợi ích vượt qua input gốc/rule-based; và gán người mới còn yêu cầu schema đầy đủ, xử lý unknown rõ ràng, cùng kiểm chứng ngoài mẫu.",
        "",
        "Trước Stage 3 cần thu thập một mẫu khảo sát mới hoặc dữ liệu theo dõi có sự đồng thuận; đăng ký trước tiêu chí ổn định/giá trị ứng dụng; kiểm tra độ nhạy khi bỏ trạng thái `not_currently_training`; xử lý missing/unknown theo quy tắc có căn cứ; và so sánh offline với baseline dùng check-in/lịch sử. Chỉ xem xét pilot có kiểm soát sau khi có bằng chứng tăng giá trị.",
        "",
        "## Artifact",
        "",
        "- `stability_validation.csv`",
        "- `new_user_assignment_tests.csv`",
        "- `product_usefulness_comparison.csv`",
        "- `figures_stability_validation.png`, `figures_cluster_assignment.png`, `figures_candidate_usefulness.png`",
        "- `stage2d_validation.py` để tái tạo đầu ra.",
        "- Không sửa hoặc ghi đè artifact Stage 2B/2C, model dropout v2.0, model registry, API, backend, frontend hay Recommendation Engine.",
    ]
    (OUTPUT / "stage2d_validation_report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_decision() -> None:
    text = """# Stage 2D - Quyết định lựa chọn mô hình

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
"""
    (OUTPUT / "model_selection_decision.md").write_text(text, encoding="utf-8")


def main() -> int:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    data, dictionary, assignments, artifacts = load_inputs()
    bundles = validate_inputs(data, dictionary, assignments, artifacts)
    reference_labels = {
        key: assignments[key].to_numpy(dtype=int)
        for key, _, _, _ in CANDIDATES
    }
    runs_by_candidate: dict[str, list[dict[str, Any]]] = {key: [] for key, _, _, _ in CANDIDATES}
    stability_rows: list[dict[str, Any]] = []
    fixed_case_fits: dict[str, dict[str, Any]] = {}

    for key, label, config, algorithm in CANDIDATES:
        full_frame = create_analysis_frame(data, config)
        feature_columns = list(full_frame.columns)
        for train_fraction in TRAIN_FRACTIONS:
            for seed in SPLIT_SEEDS:
                indices = np.arange(len(data))
                train_indices, test_indices = train_test_split(
                    indices,
                    test_size=1 - train_fraction,
                    random_state=seed,
                    shuffle=True,
                )
                fit = fit_and_assign(
                    data,
                    config,
                    algorithm,
                    train_indices,
                    test_indices,
                    seed,
                )
                run_id = f"{train_fraction:.2f}-{seed}"
                run_ari = adjusted_rand_score(
                    reference_labels[key][test_indices],
                    fit["test_labels"],
                )
                run_nmi = normalized_mutual_info_score(
                    reference_labels[key][test_indices],
                    fit["test_labels"],
                )
                train_sizes, train_percent = cluster_counts(fit["train_labels"])
                test_sizes, test_percent = cluster_counts(fit["test_labels"])
                profile_mean, profile_by_cluster = profile_drift(
                    data,
                    full_frame,
                    feature_columns,
                    train_indices,
                    fit["train_labels"],
                    reference_labels[key],
                )
                notes = fit["inference_note"]
                if train_fraction == 0.80 and seed == TEST_CASE_SEED:
                    fixed_case_fits[key] = fit
                run = {
                    "candidate_key": key,
                    "algorithm": label,
                    "configuration": config,
                    "run_id": run_id,
                    "train_fraction": train_fraction,
                    "seed": seed,
                    "n_train": len(train_indices),
                    "n_test": len(test_indices),
                    "train_cluster_sizes": train_sizes,
                    "train_cluster_percent": train_percent,
                    "test_assignment_sizes": test_sizes,
                    "test_assignment_percent": test_percent,
                    "test_silhouette": fit["test_silhouette"],
                    "test_silhouette_space": fit["test_silhouette_space"],
                    "test_silhouette_note": fit["test_silhouette_note"],
                    "test_ari_vs_stage2b_reference": float(run_ari),
                    "test_nmi_vs_stage2b_reference": float(run_nmi),
                    "profile_tvd_mean": profile_mean,
                    "profile_tvd_by_reference_cluster": json.dumps(profile_by_cluster),
                    "notes": notes,
                    "fit": fit,
                }
                runs_by_candidate[key].append(run)
                stability_rows.append(
                    {
                        "row_type": "run",
                        "candidate_key": key,
                        "algorithm": label,
                        "configuration": config,
                        "train_fraction": train_fraction,
                        "seed": seed,
                        "other_seed": "",
                        "run_id": run_id,
                        "other_run_id": "",
                        "n_train": len(train_indices),
                        "n_test": len(test_indices),
                        "overlap_n": "",
                        "train_cluster_sizes": train_sizes,
                        "train_cluster_percent": train_percent,
                        "test_assignment_sizes": test_sizes,
                        "test_assignment_percent": test_percent,
                        "test_silhouette": fit["test_silhouette"],
                        "test_silhouette_space": fit["test_silhouette_space"],
                        "test_silhouette_note": fit["test_silhouette_note"],
                        "test_ari_vs_stage2b_reference": float(run_ari),
                        "test_nmi_vs_stage2b_reference": float(run_nmi),
                        "profile_tvd_mean": profile_mean,
                        "profile_tvd_by_reference_cluster": json.dumps(profile_by_cluster),
                        "pairwise_test_ari": np.nan,
                        "pairwise_test_nmi": np.nan,
                        "notes": notes,
                    }
                )

        # Compare independently refit models only on respondents held out by both runs.
        runs = runs_by_candidate[key]
        for first_index in range(len(runs)):
            for second_index in range(first_index + 1, len(runs)):
                first = runs[first_index]
                second = runs[second_index]
                first_test = first["fit"]["test_indices"]
                second_test = second["fit"]["test_indices"]
                overlap = np.intersect1d(first_test, second_test)
                if len(overlap) < 20:
                    pair_ari = np.nan
                    pair_nmi = np.nan
                    pair_note = "Không tính ARI/NMI: giao của hai test set nhỏ hơn 20 bản ghi."
                else:
                    first_lookup = dict(
                        zip(first_test.tolist(), first["fit"]["test_labels"].tolist())
                    )
                    second_lookup = dict(
                        zip(second_test.tolist(), second["fit"]["test_labels"].tolist())
                    )
                    first_labels = np.asarray([first_lookup[int(i)] for i in overlap])
                    second_labels = np.asarray([second_lookup[int(i)] for i in overlap])
                    pair_ari = float(adjusted_rand_score(first_labels, second_labels))
                    pair_nmi = float(normalized_mutual_info_score(first_labels, second_labels))
                    pair_note = "So sánh nhãn dự đoán của hai lần fit trên cùng các dòng khảo sát test giao nhau."
                stability_rows.append(
                    {
                        "row_type": "pairwise",
                        "candidate_key": key,
                        "algorithm": label,
                        "configuration": config,
                        "train_fraction": (
                            f"{first['train_fraction']:.2f}"
                            if first["train_fraction"] == second["train_fraction"]
                            else "mixed"
                        ),
                        "seed": first["seed"],
                        "other_seed": second["seed"],
                        "run_id": first["run_id"],
                        "other_run_id": second["run_id"],
                        "n_train": "",
                        "n_test": "",
                        "overlap_n": len(overlap),
                        "train_cluster_sizes": "",
                        "train_cluster_percent": "",
                        "test_assignment_sizes": "",
                        "test_assignment_percent": "",
                        "test_silhouette": np.nan,
                        "test_silhouette_space": "",
                        "test_silhouette_note": "",
                        "test_ari_vs_stage2b_reference": np.nan,
                        "test_nmi_vs_stage2b_reference": np.nan,
                        "profile_tvd_mean": np.nan,
                        "profile_tvd_by_reference_cluster": "",
                        "pairwise_test_ari": pair_ari,
                        "pairwise_test_nmi": pair_nmi,
                        "notes": pair_note,
                    }
                )

    if len(fixed_case_fits) != len(CANDIDATES):
        raise RuntimeError("Fixed 80/20 seed=42 fit was not created for every candidate")

    stability = pd.DataFrame(stability_rows, columns=STABILITY_COLUMNS)
    stability.to_csv(OUTPUT / "stability_validation.csv", index=False, encoding="utf-8-sig")
    assignment_tests = make_assignment_tests(data, fixed_case_fits)
    assignment_tests.to_csv(OUTPUT / "new_user_assignment_tests.csv", index=False, encoding="utf-8-sig")
    usefulness = product_usefulness_rows(stability, assignment_tests)
    usefulness.to_csv(OUTPUT / "product_usefulness_comparison.csv", index=False, encoding="utf-8-sig")
    create_charts(stability, assignment_tests, usefulness)
    build_report(data, dictionary, stability, assignment_tests, usefulness)
    write_decision()

    summary = {
        "records": len(data),
        "candidate_runs": int((stability["row_type"] == "run").sum()),
        "pairwise_comparisons": int((stability["row_type"] == "pairwise").sum()),
        "assignment_test_rows": len(assignment_tests),
        "assignment_statuses": assignment_tests["assignment_status"].value_counts().to_dict(),
        "product_usefulness_rows": len(usefulness),
        "versions": {
            package: package_version(package)
            for package in ["numpy", "pandas", "scipy", "scikit-learn", "matplotlib", "joblib", "kmodes"]
        },
        "output_directory": str(OUTPUT),
    }
    print(json.dumps(summary, ensure_ascii=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
