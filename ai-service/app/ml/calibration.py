"""Hieu chinh xac suat (Platt scaling) de risk_score co y nghia nhu xac suat, va doi tuong model luu xuong dia."""
from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression


def _logit(p) -> np.ndarray:
    p = np.clip(np.asarray(p, dtype=float), 1e-6, 1 - 1e-6)
    return np.log(p / (1 - p)).reshape(-1, 1)


class PlattCalibrator:
    """Hoi quy logistic 1 chieu tren logit cua diem goc; fit tren tap validation."""

    def fit(self, raw_scores, y):
        self.lr = LogisticRegression(C=1e6, max_iter=1000).fit(_logit(raw_scores), y)
        return self

    def transform(self, raw_scores) -> np.ndarray:
        return self.lr.predict_proba(_logit(raw_scores))[:, 1]


@dataclass
class RiskModel:
    estimator: object
    calibrator: PlattCalibrator
    feature_names: list
    threshold: float
    version: str | None = None
    meta: dict = field(default_factory=dict)

    def predict_proba(self, X: pd.DataFrame) -> np.ndarray:
        raw = self.estimator.predict_proba(X[self.feature_names])[:, 1]
        return self.calibrator.transform(raw)
