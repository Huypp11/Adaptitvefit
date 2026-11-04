"""Run the AdaptiveFit Stage 2B mixed-data clustering experiment."""

from __future__ import annotations

import argparse
import json
import sys
import warnings
from pathlib import Path
from typing import Any

import joblib
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy.cluster.hierarchy import linkage
from scipy.spatial.distance import squareform
from sklearn.cluster import AgglomerativeClustering, KMeans
from sklearn.metrics import adjusted_rand_score, silhouette_score
from sklearn.preprocessing import StandardScaler

from clustering_pipeline import (
    GowerPreprocessor,
    KPrototypePreprocessor,
    NumericFeaturePreprocessor,
    nearest_medoid_assign,
)


SEED = 42
STABILITY_REPEATS = 8
K_VALUES = (2, 3, 4, 5)

SLEEP_ORDER = ["under_5_hours", "5_6_hours", "6_7_hours", "7_8_hours", "over_8_hours"]
FREE_TIME_ORDER = ["under_15_min", "15_30_min", "31_45_min", "46_60_min", "over_60_min"]
SESSION_ORDER = ["under_15_min", "15_30_min", "30_45_min", "46_60_min", "over_60_min"]
HISTORY_ORDER = [
    "consistent_no_week_long_break",
    "one_week_long_break",
    "multiple_week_long_breaks",
    "never_trained_consistently",
]

BASE_SPECS: dict[str, dict[str, Any]] = {
    "workout_days_last_7d": {"kind": "numeric", "min": 0, "max": 7},
    "workout_days_previous_7d": {"kind": "numeric", "min": 0, "max": 7},
    "usual_session_duration": {
        "kind": "ordinal_special",
        "order": SESSION_ORDER,
        "special": "not_currently_training",
    },
    "target_sessions_per_week": {"kind": "numeric", "min": 0, "max": 7},
    "sleep_duration": {"kind": "ordinal", "order": SLEEP_ORDER},
    "stress_level": {"kind": "ordinal", "order": [1, 2, 3, 4, 5]},
    "energy_level": {"kind": "ordinal", "order": [1, 2, 3, 4, 5]},
    "mood": {"kind": "ordinal", "order": [1, 2, 3, 4, 5]},
    "motivation": {"kind": "ordinal", "order": [1, 2, 3, 4, 5]},
    "daily_available_training_time": {"kind": "ordinal", "order": FREE_TIME_ORDER},
    "usual_training_location": {"kind": "nominal"},
    "training_history_status": {"kind": "nominal"},
    "response_when_tired": {"kind": "nominal"},
}

CONFIGURATIONS = {
    "A": [
        "workout_days_last_7d",
        "workout_days_previous_7d",
        "usual_session_duration",
        "target_sessions_per_week",
        "sleep_duration",
        "stress_level",
        "energy_level",
        "mood",
        "motivation",
        "daily_available_training_time",
    ],
    "B": [
        "workout_days_last_7d",
        "workout_days_previous_7d",
        "usual_session_duration",
        "target_sessions_per_week",
        "sleep_duration",
        "stress_level",
        "energy_level",
        "mood",
        "motivation",
        "daily_available_training_time",
        "usual_training_location",
        "training_history_status",
    ],
    "C": [
        "workout_days_last_7d",
        "workout_days_previous_7d",
        "usual_session_duration",
        "target_sessions_per_week",
        "sleep_duration",
        "stress_level",
        "energy_level",
        "mood",
        "motivation",
        "daily_available_training_time",
        "usual_training_location",
        "training_history_status",
        "response_when_tired",
    ],
    "A_gap": [
        "workout_days_previous_7d",
        "usual_session_duration",
        "goal_behavior_gap",
        "sleep_duration",
        "stress_level",
        "energy_level",
        "mood",
        "motivation",
        "daily_available_training_time",
    ],
}

GAP_SPEC = {"kind": "numeric", "min": -7, "max": 7}
NUMERIC_PROFILE_COLUMNS = [
    "workout_days_last_7d",
    "workout_days_previous_7d",
    "target_sessions_per_week",
    "goal_behavior_gap",
    "stress_level",
    "energy_level",
    "mood",
    "motivation",
]
CATEGORICAL_PROFILE_COLUMNS = [
    "usual_session_duration",
    "sleep_duration",
    "daily_available_training_time",
    "usual_training_location",
    "training_history_status",
    "response_when_tired",
    "helpful_after_rest",
]
MULTI_PROFILE_COLUMNS = ["stopping_reasons", "return_support_factors"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    default_data = Path(r"C:\Users\mitsu\OneDrive\Documents")
    parser.add_argument("--data", type=Path, default=default_data / "survey_cleaned.csv")
    parser.add_argument("--dictionary", type=Path, default=default_data / "feature_dictionary.csv")
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parent,
    )
    parser.add_argument("--stability-repeats", type=int, default=STABILITY_REPEATS)
    return parser.parse_args()


def spec_for(config_name: str) -> dict[str, dict[str, Any]]:
    specs = {}
    for column in CONFIGURATIONS[config_name]:
        specs[column] = GAP_SPEC if column == "goal_behavior_gap" else BASE_SPECS[column]
    return specs


def validate_inputs(data: pd.DataFrame, dictionary: pd.DataFrame) -> None:
    required_dictionary = {
        "standardized_variable",
        "data_type",
        "variable_type",
        "include_in_clustering",
        "reason",
    }
    if not required_dictionary.issubset(dictionary.columns):
        raise ValueError(f"Feature Dictionary missing columns: {sorted(required_dictionary - set(dictionary.columns))}")
    if set(dictionary.standardized_variable) != set(data.columns):
        raise ValueError("Feature Dictionary variables do not match cleaned CSV columns")
    if len(data) != 322:
        raise ValueError(f"Expected 322 rows, received {len(data)}")
    if data["timestamp"].isna().any():
        raise ValueError("Timestamp must not be missing")
    if data["timestamp"].duplicated().any():
        warnings.warn("Duplicate timestamps detected; timestamps remain excluded from clustering")
    if data[["last_break_duration", "stopping_reasons"]].isna().any(axis=1).sum() != 96:
        raise ValueError("Expected 96 records with joint Q8/Q9 missingness")
    if data["last_break_duration"].notna().sum() != 226 or data["stopping_reasons"].notna().sum() != 226:
        raise ValueError("Expected 226 non-missing Q8/Q9 records")
    for column in ["workout_days_last_7d", "workout_days_previous_7d", "target_sessions_per_week"]:
        values = pd.to_numeric(data[column], errors="raise")
        if not values.between(0, 7).all():
            raise ValueError(f"Out-of-range values in {column}")
    for column in ["stress_level", "energy_level", "mood", "motivation"]:
        values = pd.to_numeric(data[column], errors="raise")
        if not values.between(1, 5).all():
            raise ValueError(f"Out-of-range values in {column}")
    actual_missing = set(data.columns[data.isna().any()])
    if actual_missing != {"last_break_duration", "stopping_reasons"}:
        raise ValueError(f"Unexpected missing columns: {actual_missing}")


def create_analysis_frame(data: pd.DataFrame, config_name: str) -> pd.DataFrame:
    frame = data.copy()
    frame["goal_behavior_gap"] = (
        frame["target_sessions_per_week"] - frame["workout_days_last_7d"]
    )
    selected = frame[CONFIGURATIONS[config_name]].copy()
    if selected.isna().any().any():
        raise ValueError(f"Configuration {config_name} has missing values")
    return selected.reset_index(drop=True)


def gower_matrix(frame: pd.DataFrame, specs: dict[str, dict[str, Any]]) -> tuple[GowerPreprocessor, np.ndarray]:
    preprocessor = GowerPreprocessor(specs).fit(frame)
    return preprocessor, preprocessor.pairwise(frame)


def make_model(method: str, k: int, seed: int, n_init: int = 3):
    if method == "gower_agglomerative":
        return AgglomerativeClustering(
            n_clusters=k,
            metric="precomputed",
            linkage="average",
        )
    if method == "kmeans_numeric":
        return KMeans(n_clusters=k, n_init=20, random_state=seed)
    if method == "kprototypes":
        from kmodes.kprototypes import KPrototypes

        return KPrototypes(
            n_clusters=k,
            init="Cao",
            n_init=n_init,
            max_iter=100,
            random_state=seed,
            verbose=0,
        )
    raise ValueError(method)


def numeric_spec(specs: dict[str, dict[str, Any]]) -> dict[str, dict[str, Any]]:
    return {
        name: spec
        for name, spec in specs.items()
        if spec["kind"] in {"numeric", "ordinal", "ordinal_special"}
    }


def fit_predict(
    method: str,
    frame: pd.DataFrame,
    specs: dict[str, dict[str, Any]],
    k: int,
    seed: int,
    preprocessor: Any | None = None,
    n_init: int = 3,
) -> tuple[np.ndarray, Any, Any]:
    if method == "gower_agglomerative":
        if preprocessor is None:
            preprocessor, distances = gower_matrix(frame, specs)
        else:
            distances = preprocessor.pairwise(frame)
        model = make_model(method, k, seed, n_init)
        return model.fit_predict(distances), model, preprocessor
    if method == "kmeans_numeric":
        prep = preprocessor or NumericFeaturePreprocessor(numeric_spec(specs))
        numeric = prep.fit_transform(frame) if not prep.fitted_ else prep.transform(frame)
        scaler = StandardScaler()
        if preprocessor is None:
            numeric = scaler.fit_transform(numeric)
        else:
            scaler = preprocessor["scaler"]
            numeric = scaler.transform(numeric)
        model = make_model(method, k, seed, n_init)
        return model.fit_predict(numeric), {"model": model, "scaler": scaler, "features": prep}, prep
    if method == "kprototypes":
        from kmodes.kprototypes import KPrototypes

        prep = preprocessor or KPrototypePreprocessor(specs)
        if preprocessor is None:
            matrix, categorical = prep.fit_transform(frame)
        else:
            matrix, categorical = prep.transform(frame)
        model = make_model(method, k, seed, n_init)
        labels = model.fit_predict(matrix, categorical=categorical)
        return labels, {"model": model, "categorical_indices": categorical, "features": prep}, prep
    raise ValueError(method)


def metric_distance(
    method: str,
    frame: pd.DataFrame,
    specs: dict[str, dict[str, Any]],
    fitted: Any,
) -> tuple[np.ndarray, str]:
    if method == "gower_agglomerative":
        return fitted.pairwise(frame), "gower"
    if method == "kmeans_numeric":
        prep = fitted["features"]
        scaler = fitted["scaler"]
        matrix = scaler.transform(prep.transform(frame))
        diff = matrix[:, None, :] - matrix[None, :, :]
        return np.sqrt(np.einsum("ijk,ijk->ij", diff, diff)), "euclidean_numeric_scaled"
    if method == "kprototypes":
        return fitted["features"].gower.pairwise(frame), "gower"
    raise ValueError(method)


def fit_kproto(
    frame: pd.DataFrame,
    specs: dict[str, dict[str, Any]],
    k: int,
    seed: int,
    preprocessor: KPrototypePreprocessor | None = None,
    n_init: int = 3,
) -> tuple[np.ndarray, dict[str, Any], KPrototypePreprocessor]:
    from kmodes.kprototypes import KPrototypes

    prep = preprocessor or KPrototypePreprocessor(specs)
    if preprocessor is None:
        matrix, categorical = prep.fit_transform(frame)
    else:
        matrix, categorical = prep.transform(frame)
    model = KPrototypes(n_clusters=k, init="Cao", n_init=n_init, max_iter=100, random_state=seed, verbose=0)
    labels = model.fit_predict(matrix, categorical=categorical)
    return labels, {"model": model, "categorical_indices": categorical, "features": prep}, prep


def stability_ari(
    method: str,
    frame: pd.DataFrame,
    specs: dict[str, dict[str, Any]],
    reference_labels: np.ndarray,
    k: int,
    seed: int,
    repeats: int,
) -> tuple[float, list[float]]:
    rng = np.random.default_rng(seed)
    scores = []
    sample_n = max(k + 2, int(round(0.8 * len(frame))))
    for repeat in range(repeats):
        indices = np.sort(rng.choice(len(frame), size=sample_n, replace=False))
        sample = frame.iloc[indices].reset_index(drop=True)
        if method == "gower_agglomerative":
            prep, distances = gower_matrix(sample, specs)
            labels = make_model(method, k, seed + repeat + 1).fit_predict(distances)
        elif method == "kmeans_numeric":
            prep = NumericFeaturePreprocessor(numeric_spec(specs))
            numeric = prep.fit_transform(sample)
            scaler = StandardScaler()
            scaled = scaler.fit_transform(numeric)
            labels = make_model(method, k, seed + repeat + 1).fit_predict(scaled)
        elif method == "kprototypes":
            labels, _, _ = fit_kproto(
                sample,
                specs,
                k,
                seed + repeat + 1,
                n_init=2,
            )
        else:
            raise ValueError(method)
        scores.append(float(adjusted_rand_score(reference_labels[indices], labels)))
    return float(np.mean(scores)), scores


def cluster_medoid(frame: pd.DataFrame, distances: np.ndarray, labels: np.ndarray) -> tuple[pd.DataFrame, np.ndarray]:
    medoid_indices = []
    medoid_labels = []
    for label in sorted(np.unique(labels)):
        members = np.flatnonzero(labels == label)
        sub = distances[np.ix_(members, members)]
        medoid_indices.append(int(members[np.argmin(sub.sum(axis=1))]))
        medoid_labels.append(int(label))
    return frame.iloc[medoid_indices].reset_index(drop=True), np.asarray(medoid_labels)


def balance_metrics(labels: np.ndarray) -> dict[str, float]:
    counts = np.bincount(labels.astype(int))
    proportions = counts / counts.sum()
    entropy = -np.sum(proportions * np.log(proportions + 1e-12))
    normalized_entropy = entropy / np.log(len(counts)) if len(counts) > 1 else 0.0
    return {
        "min_cluster_n": int(counts.min()),
        "max_cluster_n": int(counts.max()),
        "min_cluster_pct": float(100 * proportions.min()),
        "max_cluster_pct": float(100 * proportions.max()),
        "normalized_size_entropy": float(normalized_entropy),
    }


def profile_row(
    full: pd.DataFrame,
    labels: np.ndarray,
    config: str,
    method: str,
    k: int,
    silhouette: float,
    stability: float,
) -> list[dict[str, Any]]:
    profile = full.copy()
    profile["goal_behavior_gap"] = profile["target_sessions_per_week"] - profile["workout_days_last_7d"]
    profile["cluster"] = labels.astype(int)
    rows = []
    for cluster in sorted(np.unique(labels)):
        group = profile[profile.cluster == cluster]
        n = len(group)
        row: dict[str, Any] = {
            "configuration": config,
            "algorithm": method,
            "k": k,
            "cluster": int(cluster),
            "n": int(n),
            "percent": 100 * n / len(profile),
            "silhouette": silhouette,
            "stability_ari_mean": stability,
        }
        for column in NUMERIC_PROFILE_COLUMNS:
            if column not in group:
                continue
            row[f"{column}_mean"] = float(group[column].mean())
            row[f"{column}_median"] = float(group[column].median())
        for column in CATEGORICAL_PROFILE_COLUMNS:
            if column not in group:
                continue
            counts = group[column].value_counts(dropna=False)
            row[f"{column}_mode"] = str(counts.index[0])
            row[f"{column}_mode_pct"] = float(100 * counts.iloc[0] / n)
            row[f"{column}_distribution"] = json.dumps(
                {str(k): int(v) for k, v in counts.items()},
                ensure_ascii=False,
            )
        for column in MULTI_PROFILE_COLUMNS:
            if column not in group:
                continue
            valid = group[column].dropna().astype(str)
            tokens: dict[str, int] = {}
            for value in valid:
                for token in value.split(" | "):
                    tokens[token] = tokens.get(token, 0) + 1
            denom = len(valid)
            row[f"{column}_valid_n"] = denom
            row[f"{column}_selected_pct"] = json.dumps(
                {token: round(100 * count / denom, 2) for token, count in sorted(tokens.items())}
                if denom else {},
                ensure_ascii=False,
            )
        rows.append(row)
    return rows


def summarize_candidate(
    labels: np.ndarray,
    distances: np.ndarray,
    metric_name: str,
    frame: pd.DataFrame,
    method: str,
    config: str,
    k: int,
    stability: float,
    stability_scores: list[float],
) -> dict[str, Any]:
    score = float(silhouette_score(distances, labels, metric="precomputed"))
    counts = np.bincount(labels.astype(int))
    result: dict[str, Any] = {
        "configuration": config,
        "algorithm": method,
        "k": k,
        "n": len(labels),
        "silhouette": score,
        "silhouette_distance": metric_name,
        "stability_ari_mean": stability,
        "stability_ari_sd": float(np.std(stability_scores, ddof=1)) if len(stability_scores) > 1 else 0.0,
        "stability_repeats": len(stability_scores),
        "cluster_sizes": json.dumps({str(i): int(v) for i, v in enumerate(counts)}, ensure_ascii=False),
    }
    result.update(balance_metrics(labels))
    return result


def main() -> int:
    args = parse_args()
    if args.stability_repeats < 2:
        raise ValueError("--stability-repeats must be at least 2")
    args.output.mkdir(parents=True, exist_ok=True)
    data = pd.read_csv(args.data, encoding="utf-8-sig")
    dictionary = pd.read_csv(args.dictionary, encoding="utf-8-sig")
    validate_inputs(data, dictionary)
    if not data["timestamp"].astype(str).str.match(r"^\d{4}-\d{2}-\d{2}T").all():
        raise ValueError("Timestamps are not the expected ISO strings")

    try:
        import kmodes  # noqa: F401
        kprototypes_available = True
    except ImportError:
        kprototypes_available = False
        warnings.warn("kmodes is unavailable; K-Prototypes will be skipped")

    metrics_rows: list[dict[str, Any]] = []
    profile_rows: list[dict[str, Any]] = []
    assignments: dict[str, np.ndarray] = {}
    bundles: dict[str, dict[str, Any]] = {}
    methods = ["gower_agglomerative"] + (["kprototypes"] if kprototypes_available else [])

    for config in CONFIGURATIONS:
        frame = create_analysis_frame(data, config)
        specs = spec_for(config)
        for method in methods:
            if method == "gower_agglomerative":
                prep, distances = gower_matrix(frame, specs)
            for k in K_VALUES:
                if method == "gower_agglomerative":
                    model = make_model(method, k, SEED)
                    labels = model.fit_predict(distances)
                    fitted = prep
                    metric_distances = distances
                    metric_name = "gower"
                else:
                    labels, fitted, _ = fit_kproto(
                        frame,
                        specs,
                        k,
                        SEED,
                        n_init=5,
                    )
                    metric_distances = GowerPreprocessor(specs).fit(frame).pairwise(frame)
                    metric_name = "gower_external_mixed_data"
                stability, stability_scores = stability_ari(
                    method, frame, specs, labels, k, SEED + k, args.stability_repeats
                )
                key = f"{config}|{method}|k={k}"
                assignments[key] = labels
                result = summarize_candidate(
                    labels, metric_distances, metric_name, frame, method, config, k,
                    stability, stability_scores,
                )
                result["stability_ari_replicates"] = json.dumps(stability_scores)
                metrics_rows.append(result)
                profile_rows.extend(
                    profile_row(data, labels, config, method, k, result["silhouette"], stability)
                )
                bundles[key] = {
                    "configuration": config,
                    "algorithm": method,
                    "k": k,
                    "feature_columns": list(frame.columns),
                    "specs": specs,
                    "labels": labels,
                    "reference_rows": frame.copy(),
                    "metrics": result,
                    "estimator": model if method == "gower_agglomerative" else fitted["model"],
                    "preprocessor": fitted,
                }

    # K-Means is intentionally a numeric/ordinal-only baseline, without nominal variables.
    for config in CONFIGURATIONS:
        frame = create_analysis_frame(data, config)
        specs = spec_for(config)
        for k in K_VALUES:
            prep = NumericFeaturePreprocessor(numeric_spec(specs))
            unscaled = prep.fit_transform(frame)
            scaler = StandardScaler()
            scaled = scaler.fit_transform(unscaled)
            model = make_model("kmeans_numeric", k, SEED)
            labels = model.fit_predict(scaled)
            diff = scaled[:, None, :] - scaled[None, :, :]
            distances = np.sqrt(np.einsum("ijk,ijk->ij", diff, diff))
            stability, stability_scores = stability_ari(
                "kmeans_numeric", frame, specs, labels, k, SEED + k, args.stability_repeats
            )
            result = summarize_candidate(
                labels, distances, "euclidean_numeric_scaled", frame,
                "kmeans_numeric", config, k, stability, stability_scores,
            )
            result["stability_ari_replicates"] = json.dumps(stability_scores)
            result["numeric_baseline_features"] = json.dumps(prep.output_feature_names_)
            metrics_rows.append(result)
            profile_rows.extend(
                profile_row(data, labels, config, "kmeans_numeric", k, result["silhouette"], stability)
            )
            key = f"{config}|kmeans_numeric|k={k}"
            assignments[key] = labels
            bundles[key] = {
                "configuration": config,
                "algorithm": "kmeans_numeric",
                "k": k,
                "feature_columns": list(frame.columns),
                "specs": specs,
                "labels": labels,
                "reference_rows": frame.copy(),
                "metrics": result,
                "estimator": model,
                "preprocessor": {"features": prep, "scaler": scaler},
            }

    metrics = pd.DataFrame(metrics_rows)
    profiles = pd.DataFrame(profile_rows)
    metrics["min_cluster_pct"] = metrics["min_cluster_pct"].round(3)
    metrics["max_cluster_pct"] = metrics["max_cluster_pct"].round(3)
    metrics.to_csv(args.output / "clustering_metrics.csv", index=False, encoding="utf-8-sig")
    profiles.to_csv(args.output / "cluster_profiles.csv", index=False, encoding="utf-8-sig")

    assignment_df = pd.DataFrame({
        "survey_row_index": np.arange(len(data)),
        **{key: labels for key, labels in assignments.items()},
    })
    assignment_df.to_csv(args.output / "clustering_results.csv", index=False, encoding="utf-8-sig")

    # Multi-criterion shortlist: do not auto-select a final scientific model.
    metrics["ranking_key"] = metrics.apply(
        lambda row: (
            f"k={int(row.k)}|features={row.numeric_baseline_features}"
            if row.algorithm == "kmeans_numeric"
            else f"{row.configuration}|k={int(row.k)}"
        ),
        axis=1,
    )
    rank_metrics = metrics.drop_duplicates(["algorithm", "ranking_key"]).copy()
    rank_metrics["silhouette_rank_within_algorithm"] = rank_metrics.groupby("algorithm")["silhouette"].rank(
        ascending=False, method="min"
    )
    rank_metrics["stability_rank_within_algorithm"] = rank_metrics.groupby("algorithm")["stability_ari_mean"].rank(
        ascending=False, method="min"
    )
    rank_metrics["balance_rank_within_algorithm"] = rank_metrics.groupby("algorithm")["normalized_size_entropy"].rank(
        ascending=False, method="min"
    )
    rank_metrics["multi_criterion_rank_mean"] = rank_metrics[
        [
            "silhouette_rank_within_algorithm",
            "stability_rank_within_algorithm",
            "balance_rank_within_algorithm",
        ]
    ].mean(axis=1)
    rank_columns = [
        "silhouette_rank_within_algorithm",
        "stability_rank_within_algorithm",
        "balance_rank_within_algorithm",
        "multi_criterion_rank_mean",
    ]
    rank_lookup = {
        (algorithm, key): tuple(values)
        for algorithm, key, *values in rank_metrics[
            ["algorithm", "ranking_key", *rank_columns]
        ].itertuples(index=False, name=None)
    }
    for column_index, column in enumerate(rank_columns):
        metrics[column] = [
            rank_lookup[(algorithm, key)][column_index]
            for algorithm, key in metrics[["algorithm", "ranking_key"]].itertuples(
                index=False, name=None
            )
        ]
    metrics = metrics.sort_values(
        ["algorithm", "multi_criterion_rank_mean", "silhouette", "stability_ari_mean"],
        ascending=[True, True, False, False],
    )

    shortlist_keys: list[str] = []
    shortlist_rows: list[Any] = []
    for algorithm, group in metrics.groupby("algorithm", sort=True):
        signatures: set[tuple[Any, ...]] = set()
        selected = 0
        for row in group.itertuples(index=False):
            key = f"{row.configuration}|{row.algorithm}|k={int(row.k)}"
            signature = (algorithm, row.ranking_key)
            if signature in signatures:
                continue
            signatures.add(signature)
            shortlist_keys.append(key)
            shortlist_rows.append(row)
            selected += 1
            if selected == 2:
                break
    metrics = metrics.drop(columns=["ranking_key"])

    # Candidate artifact stores every tested model plus out-of-sample preprocessing.
    for key, bundle in bundles.items():
        if bundle["algorithm"] == "gower_agglomerative":
            distances = bundle["preprocessor"].pairwise(bundle["reference_rows"])
            medoids, medoid_labels = cluster_medoid(
                bundle["reference_rows"], distances, bundle["labels"]
            )
            bundle["medoids"] = medoids
            bundle["medoid_labels"] = medoid_labels
            bundle["new_row_assignment"] = (
                "nearest saved cluster medoid using fitted Gower preprocessing; approximate, "
                "because AgglomerativeClustering has no predict"
            )
        elif bundle["algorithm"] == "kmeans_numeric":
            bundle["new_row_assignment"] = "numeric/ordinal feature preprocessing, saved scaler, and KMeans.predict"
        else:
            bundle["new_row_assignment"] = "saved KPrototypePreprocessor and fitted KPrototypes.predict"

    artifact_dir = args.output / "artifacts"
    artifact_dir.mkdir(exist_ok=True)
    joblib.dump(
        {
            "stage": "2B",
            "data_rows": len(data),
            "seed": SEED,
            "candidate_bundles": bundles,
            "assignment_utility": nearest_medoid_assign,
            "warning": "Exploratory survey clustering only; not a dropout predictor.",
        },
        artifact_dir / "candidate_models.joblib",
        compress=3,
    )
    # Save algorithm-specific candidates for review; do not choose a cross-method winner.
    shortlist_bundles = {}
    for key in shortlist_keys:
        bundle = dict(bundles[key])
        bundle["selection_status"] = (
            "algorithm-specific review shortlist only; not declared a scientifically selected best model"
        )
        shortlist_bundles[key] = bundle
    joblib.dump(
        {
            "selection_status": "No cross-algorithm winner selected; compare profiles and external validation.",
            "candidate_keys": shortlist_keys,
            "candidate_bundles": shortlist_bundles,
        },
        artifact_dir / "shortlisted_candidates.joblib",
        compress=3,
    )
    (artifact_dir / "preprocessing_spec.json").write_text(
        json.dumps(
            {
                "candidate_files": ["candidate_models.joblib", "shortlisted_candidates.joblib"],
                "shortlist_keys": shortlist_keys,
                "selection_status": "No cross-algorithm winner selected.",
                "categorical_and_ordinal_maps": {
                    name: spec
                    for name, spec in BASE_SPECS.items()
                    if spec["kind"] in {"ordinal", "ordinal_special"}
                },
                "timestamp_excluded": True,
                "respondent_context_excluded": True,
                "q8_q9_missing_features_excluded": True,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    # Add rank columns to saved metric report after selection ordering.
    metrics.to_csv(args.output / "clustering_metrics.csv", index=False, encoding="utf-8-sig")

    # Candidate metrics chart.
    fig, ax = plt.subplots(figsize=(11, 6))
    markers = {"gower_agglomerative": "o", "kprototypes": "s", "kmeans_numeric": "^"}
    for (algorithm, configuration), subset in metrics.groupby(["algorithm", "configuration"]):
        ax.scatter(
            subset["silhouette"],
            subset["stability_ari_mean"],
            marker=markers[algorithm],
            label=f"{algorithm}/{configuration}",
            s=50,
            alpha=0.8,
        )
        for _, row in subset.iterrows():
            ax.annotate(f"k={int(row.k)}", (row.silhouette, row.stability_ari_mean), fontsize=6, alpha=.7)
    ax.set_xlabel("Silhouette (không gian khoảng cách khác nhau theo thuật toán)")
    ax.set_ylabel("ARI ổn định trung bình qua các mẫu con")
    ax.set_title("Chất lượng và độ ổn định của ứng viên (không so trực tiếp silhouette giữa thuật toán)")
    ax.grid(alpha=.2)
    ax.legend(fontsize=6, ncol=2, loc="best")
    fig.tight_layout()
    fig.savefig(args.output / "figures_candidate_metrics.png", bbox_inches="tight")
    plt.close(fig)

    # Biểu đồ kích thước cụm cho một ứng viên mỗi thuật toán; không hàm ý chọn thuật toán thắng cuộc.
    representative_algorithms = ["gower_agglomerative"]
    if kprototypes_available:
        representative_algorithms.append("kprototypes")
    representative_algorithms.append("kmeans_numeric")
    fig, axs = plt.subplots(
        1,
        len(representative_algorithms),
        figsize=(4.5 * len(representative_algorithms), 4),
        squeeze=False,
    )
    representative_rows = []
    for algorithm in representative_algorithms:
        representative_rows.append(next(row for row in shortlist_rows if row.algorithm == algorithm))
    for ax, row in zip(axs[0], representative_rows):
        key = f"{row.configuration}|{row.algorithm}|k={int(row.k)}"
        labels = assignments[key]
        sizes = pd.Series(labels).value_counts().sort_index()
        ax.bar(sizes.index.astype(str), sizes.values, color="#4477aa")
        ax.set_title(f"{row.configuration} / {row.algorithm}\nk={int(row.k)}")
        ax.set_xlabel("Cụm"); ax.set_ylabel("Số người trả lời"); ax.grid(axis="y", alpha=.2)
    fig.suptitle("Kích thước cụm của một ứng viên xem xét cho mỗi thuật toán")
    fig.tight_layout()
    fig.savefig(args.output / "figures_cluster_sizes.png", bbox_inches="tight")
    plt.close(fig)

    report_lines = [
        "# AdaptiveFit AI - Stage 2B: Thí nghiệm phân cụm",
        "",
        f"- Số bản ghi đầu vào: **{len(data)}**; số cột đã làm sạch: {data.shape[1]}; số dòng trong Feature Dictionary: {len(dictionary)}.",
        f"- Thư mục đầu ra: `{args.output}`.",
        "- Không tạo nhãn dropout hay dữ liệu giả; không huấn luyện Logistic Regression; không thay đổi model registry, API, backend, frontend hoặc CSV nguồn.",
        "- Giữ nguyên cả 322 dòng. Có 96 dòng thiếu Q8/Q9; hai câu hỏi này không nằm trong các feature thử nghiệm nên không có dòng nào bị loại vì thiếu dữ liệu.",
        f"- Độ ổn định: lấy mẫu con 80% không hoàn lại {args.stability_repeats} lần; báo cáo ARI trung bình và độ lệch chuẩn.",
        "",
        "## Phương pháp và cấu hình đã chạy",
        "",
        f"- Cấu hình: {', '.join(CONFIGURATIONS)}.",
        f"- Thuật toán: {', '.join(methods + ['kmeans_numeric'])}; K-Prototypes {'đã có thư viện và được chạy' if kprototypes_available else 'bị bỏ qua vì chưa có thư viện (cài requirements.txt để chạy)'}.",
        f"- Số cụm thử nghiệm: k={', '.join(map(str, K_VALUES))}.",
        "- Gower + Agglomerative (average linkage) dùng ma trận khoảng cách Gower tính trước; silhouette dùng chính ma trận đó.",
        "- K-Prototypes dùng biến numeric/ordinal đã chuẩn hóa và biến nominal dạng danh mục; silhouette Gower là chỉ số đánh giá bên ngoài, không phải hàm mục tiêu của thuật toán.",
        "- K-Means là baseline chỉ dùng numeric/ordinal đã chuẩn hóa; silhouette dùng khoảng cách Euclidean. Không mã hóa category nominal thành số nguyên.",
        "",
        "## Bảng so sánh các ứng viên",
        "",
        "| Cấu hình | Thuật toán | k | Silhouette (khoảng cách) | ARI ổn định trung bình (ĐLC) | Số người trong từng cụm | Tỷ lệ cụm nhỏ nhất (%) | Entropy kích thước chuẩn hóa |",
        "|---|---|---:|---|---:|---|---:|---:|",
    ]
    for row in metrics.itertuples(index=False):
        report_lines.append(
            f"| {row.configuration} | {row.algorithm} | {int(row.k)} | {row.silhouette:.3f} ({row.silhouette_distance}) | "
            f"{row.stability_ari_mean:.3f} ({row.stability_ari_sd:.3f}) | {row.cluster_sizes} | "
            f"{row.min_cluster_pct:.1f} | {row.normalized_size_entropy:.3f} |"
        )
    report_lines += [
        "",
        "## Danh sách ứng viên xem xét theo thuật toán và lựa chọn mô hình",
        "",
        "Các ứng viên chỉ được xếp hạng trong cùng một thuật toán. Thứ hạng trong mỗi thuật toán là trung bình thứ hạng của silhouette, độ ổn định qua lấy mẫu con và entropy kích thước chuẩn hóa. Đây chỉ là công cụ hỗ trợ xem xét, không phải quy tắc chọn mô hình đã được kiểm định thống kê. Các cấu hình K-Means chỉ dùng numeric có không gian feature trùng nhau được gộp khi lập shortlist vì thêm feature nominal không làm thay đổi đầu vào của K-Means. Không chọn một thuật toán thắng cuộc chung vì các thuật toán dùng không gian khoảng cách khác nhau. Không áp đặt số cụm; các cụm nhỏ vẫn được giữ và báo cáo.",
        "",
        "| Thuật toán | Ứng viên trong danh sách xem xét (cấu hình / k) |",
        "|---|---|",
        *[
            f"| {algorithm} | "
            + ", ".join(
                f"{row.configuration} / k={int(row.k)}"
                for row in shortlist_rows
                if row.algorithm == algorithm
            )
            + " |"
            for algorithm in representative_algorithms
        ],
        "",
        "Chưa thể kết luận mô hình phân cụm nào là tốt nhất chỉ từ khảo sát cắt ngang này. Cần xem xét hồ sơ cụm, độ nhạy của độ ổn định/thứ hạng, tính hữu ích trong thực tế và tốt nhất là lặp lại trên mẫu mới. Vì vậy, báo cáo không khẳng định một thuật toán hay giá trị k nào là tốt nhất trong mọi trường hợp.",
        "",
        "## Hồ sơ các cụm",
        "",
        "`cluster_profiles.csv` có một dòng cho mỗi ứng viên và mỗi cụm, gồm kích thước/tỷ lệ cụm, trung bình và trung vị của biến numeric/ordinal, mode/phân phối của biến danh mục, và tỷ lệ lựa chọn của câu hỏi nhiều lựa chọn kèm mẫu số hợp lệ. Những biến không có trong cấu hình dùng để tạo cụm chỉ được sử dụng để mô tả sau phân cụm, không quyết định thành viên cụm.",
        "",
        "## Độ ổn định và hạn chế",
        "",
        "- Độ ổn định được đo bằng ARI trên từng mẫu con 80%, so với kết quả tham chiếu chỉ trên cùng các bản ghi đó. Đây là kiểm tra độ nhạy khi thay đổi mẫu, không phải kiểm định khả năng tổng quát hóa trên dữ liệu ngoài mẫu.",
        "- Gower gán trọng số bằng nhau cho từng feature; bổ sung feature bối cảnh làm thay đổi cả thông tin và thành phần khoảng cách. So sánh giữa các cấu hình là phân tích độ nhạy.",
        "- K-Prototypes có hàm mục tiêu khác Gower; silhouette của K-Prototypes được báo cáo trong không gian Gower chỉ như một chỉ số đánh giá tách biệt bên ngoài.",
        "- K-Means không dùng biến nominal nên kết quả không tương đương trực tiếp với các phương pháp xử lý dữ liệu hỗn hợp.",
        "- 322 câu trả lời tự khai báo theo mẫu thuận tiện là dữ liệu nhỏ và cắt ngang; chưa có căn cứ xem mẫu này đại diện cho người trẻ tại Việt Nam.",
        "- Cụm chỉ mô tả mức độ tương đồng giữa câu trả lời khảo sát. Chúng không dự đoán việc bỏ tập trong tương lai và không chứng minh tác động nhân quả của gợi ý.",
        "- 96 giá trị thiếu Q8/Q9 trùng với các nhóm lịch sử tập luyện quan sát được ở Stage 1B/EDA; logic bỏ qua câu hỏi của khảo sát gốc vẫn chưa được xác minh. Hai biến này không được dùng để tạo cụm.",
        "",
        "## Artifact đã lưu và khả năng sử dụng sau này",
        "",
        "- Nhãn cụm của các ứng viên: `clustering_results.csv`.",
        "- Metrics của tất cả ứng viên và giá trị k: `clustering_metrics.csv`.",
        "- Hồ sơ cụm: `cluster_profiles.csv`.",
        "- Model đã khớp và preprocessing của các ứng viên: `artifacts/candidate_models.joblib`.",
        "- Shortlist xem xét theo từng thuật toán: `artifacts/shortlisted_candidates.joblib`.",
        "- Ánh xạ tiền xử lý và trạng thái: `artifacts/preprocessing_spec.json`.",
        "- Agglomerative không có `predict` tích hợp; gán bản ghi mới theo medoid gần nhất chỉ là phương án xấp xỉ và cần được kiểm định. K-Means và K-Prototypes có lưu model/preprocessing để transform và predict.",
        "",
        "## Các bước tiếp theo trước khi tích hợp vào sản phẩm",
        "",
        "1. Xem lại hồ sơ cụm và xác định các mẫu hình có thể diễn giải, hữu ích; chưa nên đặt tên cụm quá sớm.",
        "2. Chỉ chọn thuật toán/k sau khi đánh giá nhiều tiêu chí và độ ổn định; không chọn chỉ dựa trên silhouette.",
        "3. Kiểm định trên mẫu khảo sát mới hoặc dữ liệu người dùng được thu thập về sau; xác định yêu cầu về quyền riêng tư, đồng thuận và khả năng thu thập feature.",
        "4. Xác nhận cách gán cụm cho người dùng mới. Với Agglomerative, cần kiểm định phương án medoid gần nhất hoặc chọn thuật toán có khả năng predict thực sự.",
        "5. Sau đó mới thiết kế hồ sơ cụm có version và thử tích hợp vào hệ thống gợi ý qua feature flag; giữ phân cụm tách biệt với dự đoán nguy cơ bỏ tập.",
    ]
    (args.output / "clustering_report.md").write_text("\n".join(report_lines) + "\n", encoding="utf-8")

    print(f"Completed: n={len(data)}, candidates={len(metrics)}, profiles={len(profiles)}")
    print(f"K-Prototypes available: {kprototypes_available}")
    print(f"Algorithm-specific shortlist candidates: {', '.join(shortlist_keys)}")
    print(metrics[[
        "configuration", "algorithm", "k", "silhouette", "stability_ari_mean",
        "min_cluster_pct", "normalized_size_entropy", "cluster_sizes",
    ]].to_string(index=False))
    print(f"Outputs: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
