"""Reusable preprocessing and distance helpers for Stage 2B clustering artifacts."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import numpy as np
import pandas as pd


@dataclass
class GowerPreprocessor:
    """Gower dissimilarity for explicitly typed mixed features."""

    specs: dict[str, dict[str, Any]]

    def __post_init__(self):
        self.feature_names = list(self.specs)
        self.fitted_ = False

    def fit(self, frame: pd.DataFrame) -> "GowerPreprocessor":
        missing = [c for c in self.feature_names if c not in frame.columns]
        if missing:
            raise ValueError(f"Missing Gower features: {missing}")
        self.fitted_categories_: dict[str, list[str]] = {}
        self.fitted_ = True
        return self

    def _values(self, frame: pd.DataFrame, column: str) -> np.ndarray:
        if column not in frame:
            raise ValueError(f"Missing feature {column!r}")
        if frame[column].isna().any():
            raise ValueError(f"Gower input contains missing values in {column!r}")
        return frame[column].to_numpy()

    def pairwise(self, left: pd.DataFrame, right: pd.DataFrame | None = None) -> np.ndarray:
        if not self.fitted_:
            raise RuntimeError("Call fit before pairwise")
        right = left if right is None else right
        n_left, n_right = len(left), len(right)
        distances = np.zeros((n_left, n_right), dtype=float)
        for column, spec in self.specs.items():
            kind = spec["kind"]
            x = self._values(left, column)
            y = self._values(right, column)
            if kind == "numeric":
                width = float(spec["max"] - spec["min"])
                if width <= 0:
                    raise ValueError(f"Invalid numeric range for {column}")
                part = np.abs(x.astype(float)[:, None] - y.astype(float)[None, :]) / width
                distances += np.clip(part, 0.0, 1.0)
            elif kind == "ordinal":
                order = spec["order"]
                lookup = {str(value): i for i, value in enumerate(order)}
                try:
                    xi = np.array([lookup[str(v)] for v in x], dtype=float)
                    yi = np.array([lookup[str(v)] for v in y], dtype=float)
                except KeyError as exc:
                    raise ValueError(f"Unknown ordinal value in {column}: {exc}") from exc
                denom = max(len(order) - 1, 1)
                distances += np.abs(xi[:, None] - yi[None, :]) / denom
            elif kind == "ordinal_special":
                order = spec["order"]
                special = spec["special"]
                lookup = {value: i for i, value in enumerate(order)}
                xi = np.array([lookup.get(str(v), -1) for v in x], dtype=int)
                yi = np.array([lookup.get(str(v), -1) for v in y], dtype=int)
                x_special = np.equal(x, special)
                y_special = np.equal(y, special)
                part = np.ones((n_left, n_right), dtype=float)
                both_valid = (~x_special)[:, None] & (~y_special)[None, :]
                denom = max(len(order) - 1, 1)
                part[both_valid] = (
                    np.abs(xi[:, None] - yi[None, :])[both_valid] / denom
                )
                part[x_special[:, None] & y_special[None, :]] = 0.0
                distances += part
            elif kind == "nominal":
                distances += (x[:, None] != y[None, :]).astype(float)
            else:
                raise ValueError(f"Unsupported Gower kind {kind!r} for {column}")
        distances /= len(self.feature_names)
        return np.clip(distances, 0.0, 1.0)


@dataclass
class NumericFeaturePreprocessor:
    """Build numeric/ordinal features for K-Means and scale them."""

    specs: dict[str, dict[str, Any]]

    def __post_init__(self):
        self.feature_names = list(self.specs)
        self.fitted_ = False

    def fit_transform(self, frame: pd.DataFrame) -> np.ndarray:
        output = self._unscaled(frame, fit=True)
        self.fitted_ = True
        return output

    def transform(self, frame: pd.DataFrame) -> np.ndarray:
        if not self.fitted_:
            raise RuntimeError("Call fit_transform before transform")
        return self._unscaled(frame, fit=False)

    def _unscaled(self, frame: pd.DataFrame, fit: bool) -> np.ndarray:
        columns: list[np.ndarray] = []
        names: list[str] = []
        for column, spec in self.specs.items():
            if column not in frame:
                raise ValueError(f"Missing numeric feature {column!r}")
            kind = spec["kind"]
            series = frame[column]
            if kind == "numeric":
                arr = pd.to_numeric(series, errors="raise").to_numpy(dtype=float)
                names.append(column)
                columns.append(arr)
            elif kind == "ordinal":
                lookup = {str(value): i for i, value in enumerate(spec["order"])}
                arr = series.astype(str).map(lookup)
                if arr.isna().any():
                    raise ValueError(f"Unknown ordinal value in {column}")
                denom = max(len(spec["order"]) - 1, 1)
                names.append(column)
                columns.append(arr.to_numpy(dtype=float) / denom)
            elif kind == "ordinal_special":
                lookup = {value: i for i, value in enumerate(spec["order"])}
                special = spec["special"]
                is_special = series.eq(special).to_numpy(dtype=float)
                arr = series.map(lookup).astype(float)
                arr.loc[series.eq(special)] = np.nan
                if fit:
                    self.duration_median_ = float(arr.median())
                arr = arr.fillna(self.duration_median_)
                denom = max(len(spec["order"]) - 1, 1)
                names.extend([f"{column}_rank", f"{column}_not_training"])
                columns.extend([arr.to_numpy(dtype=float) / denom, is_special])
            else:
                continue
        if not columns:
            raise ValueError("No numeric or ordinal features available")
        self.output_feature_names_ = names
        return np.column_stack(columns)


@dataclass
class KPrototypePreprocessor:
    """Scale numeric/ordinal features and preserve nominal features for K-Prototypes."""

    specs: dict[str, dict[str, Any]]

    def __post_init__(self):
        self.feature_names = list(self.specs)
        self.fitted_ = False

    def fit_transform(self, frame: pd.DataFrame) -> tuple[np.ndarray, list[int]]:
        return self._transform(frame, fit=True)

    def transform(self, frame: pd.DataFrame) -> tuple[np.ndarray, list[int]]:
        if not self.fitted_:
            raise RuntimeError("Call fit_transform before transform")
        return self._transform(frame, fit=False)

    def _transform(self, frame: pd.DataFrame, fit: bool) -> tuple[np.ndarray, list[int]]:
        values: list[np.ndarray] = []
        categorical_indices: list[int] = []
        self.output_columns_: list[str] = []
        for column, spec in self.specs.items():
            if column not in frame:
                raise ValueError(f"Missing feature {column!r}")
            kind = spec["kind"]
            series = frame[column]
            if kind == "numeric":
                arr = pd.to_numeric(series, errors="raise").to_numpy(dtype=float)
                arr = (arr - float(spec["min"])) / float(spec["max"] - spec["min"])
                values.append(arr)
                self.output_columns_.append(column)
            elif kind == "ordinal":
                lookup = {str(value): i for i, value in enumerate(spec["order"])}
                arr = series.astype(str).map(lookup)
                if arr.isna().any():
                    raise ValueError(f"Unknown ordinal value in {column}")
                denom = max(len(spec["order"]) - 1, 1)
                values.append(arr.to_numpy(dtype=float) / denom)
                self.output_columns_.append(column)
            elif kind == "ordinal_special":
                lookup = {value: i for i, value in enumerate(spec["order"])}
                special = spec["special"]
                is_special = series.eq(special).to_numpy(dtype=float)
                rank = series.map(lookup).astype(float)
                rank.loc[series.eq(special)] = np.nan
                if fit:
                    self.duration_median_ = float(rank.median())
                rank = rank.fillna(self.duration_median_)
                denom = max(len(spec["order"]) - 1, 1)
                values.extend([rank.to_numpy(dtype=float) / denom, is_special.astype(object)])
                categorical_indices.append(len(values) - 1)
                self.output_columns_.extend([f"{column}_rank", f"{column}_not_training"])
            elif kind == "nominal":
                values.append(series.astype(str).to_numpy(dtype=object))
                categorical_indices.append(len(values) - 1)
                self.output_columns_.append(column)
            else:
                raise ValueError(f"Unsupported K-Prototypes kind {kind!r}")
        self.fitted_ = True
        matrix = np.column_stack(values).astype(object)
        return matrix, categorical_indices


def nearest_medoid_assign(
    preprocessor: GowerPreprocessor,
    medoids: pd.DataFrame,
    medoid_labels: np.ndarray,
    new_rows: pd.DataFrame,
) -> tuple[np.ndarray, np.ndarray]:
    """Approximate assignment by nearest saved medoid (not Agglomerative predict)."""
    distances = preprocessor.pairwise(new_rows, medoids)
    indices = distances.argmin(axis=1)
    return medoid_labels[indices], distances[np.arange(len(new_rows)), indices]
