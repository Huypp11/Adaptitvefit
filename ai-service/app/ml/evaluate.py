"""Chi so danh gia, chon nguong, muc nguy co, baseline quy tac, do quan trong feature."""
import numpy as np
import pandas as pd
from sklearn.metrics import (average_precision_score, brier_score_loss, confusion_matrix,
                             precision_score, recall_score, f1_score, roc_auc_score)

from .config import RISK_HIGH_MIN, RISK_LOW_MAX, SEED


def risk_level(p: float) -> str:
    if p < RISK_LOW_MAX:
        return "LOW"
    return "HIGH" if p >= RISK_HIGH_MIN else "MEDIUM"


def binary_metrics(y, p, threshold: float) -> dict:
    y, p = np.asarray(y), np.asarray(p)
    pred = (p >= threshold).astype(int)
    tn, fp, fn, tp = confusion_matrix(y, pred, labels=[0, 1]).ravel()
    return {
        "threshold": round(float(threshold), 3), "n": int(len(y)), "positives": int(y.sum()),
        "accuracy": float((pred == y).mean()),
        "precision": float(precision_score(y, pred, zero_division=0)),
        "recall": float(recall_score(y, pred, zero_division=0)),
        "f1": float(f1_score(y, pred, zero_division=0)),
        "roc_auc": float(roc_auc_score(y, p)), "pr_auc": float(average_precision_score(y, p)),
        "brier": float(brier_score_loss(y, p)),
        "confusion_matrix": {"tn": int(tn), "fp": int(fp), "fn": int(fn), "tp": int(tp)},
    }


def choose_threshold(y, p, beta: float = 2.0) -> float:
    """Chon nguong tren validation toi da hoa F-beta (beta=2 uu tien Recall cua nhom bo tap)."""
    y, p = np.asarray(y), np.asarray(p)
    best_t, best = 0.5, -1.0
    for t in np.arange(0.05, 0.951, 0.01):
        pred = (p >= t).astype(int)
        pr, rc = precision_score(y, pred, zero_division=0), recall_score(y, pred, zero_division=0)
        score = 0.0 if pr + rc == 0 else (1 + beta**2) * pr * rc / (beta**2 * pr + rc)
        if score >= best:
            best, best_t = score, float(t)
    return round(best_t, 2)


def level_distribution(y, p) -> dict:
    levels = np.array([risk_level(v) for v in p])
    y = np.asarray(y)
    return {lv: {"n": int((levels == lv).sum()),
                 "observed_dropout_rate": float(y[levels == lv].mean()) if (levels == lv).any() else None}
            for lv in ("LOW", "MEDIUM", "HIGH")}


def calibration_table(y, p, bins: int = 10) -> list:
    y, p = np.asarray(y), np.asarray(p)
    edges = np.linspace(0, 1, bins + 1)
    out = []
    for lo, hi in zip(edges[:-1], edges[1:]):
        m = (p >= lo) & ((p < hi) if hi < 1 else (p <= hi))
        if m.any():
            out.append({"bin": f"{lo:.1f}-{hi:.1f}", "n": int(m.sum()),
                        "mean_predicted": float(p[m].mean()), "observed_rate": float(y[m].mean())})
    return out


def rule_baseline(df: pd.DataFrame, y, missed_days_threshold: int = 3) -> dict:
    """Baseline quy tac don gian: missed_days >= 3 thi coi la nguy co. Model phai vuot duoc moc nay."""
    score = df["missed_days"].to_numpy(dtype=float)
    pred = (score >= missed_days_threshold).astype(int)
    y = np.asarray(y)
    return {
        "rule": f"missed_days >= {missed_days_threshold}", "accuracy": float((pred == y).mean()),
        "precision": float(precision_score(y, pred, zero_division=0)),
        "recall": float(recall_score(y, pred, zero_division=0)),
        "f1": float(f1_score(y, pred, zero_division=0)), "roc_auc": float(roc_auc_score(y, score)),
    }


def permutation_importance(model, X: pd.DataFrame, y, n_repeats: int = 10, seed: int = SEED) -> list:
    """Do giam ROC-AUC khi xao tron tung feature tren tap test. Mo ta hanh vi cua model, khong phai quan he nhan qua."""
    rng = np.random.default_rng(seed)
    base = roc_auc_score(y, model.predict_proba(X))
    rows = []
    for col in model.feature_names:
        drops = []
        for _ in range(n_repeats):
            Xp = X.copy()
            Xp[col] = rng.permutation(Xp[col].to_numpy())
            drops.append(base - roc_auc_score(y, model.predict_proba(Xp)))
        rows.append({"feature": col, "auc_drop_mean": float(np.mean(drops)), "auc_drop_std": float(np.std(drops))})
    return sorted(rows, key=lambda r: -r["auc_drop_mean"])
