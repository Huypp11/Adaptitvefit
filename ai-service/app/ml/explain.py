"""Giai thich mot du doan bang SHAP.

QUAN TRONG: SHAP mo ta CACH MODEL DUNG du lieu de ra diem so, KHONG phai nguyen nhan thuc su cua viec bo tap.
Moi yeu to duoc so voi "nguoi dung dien hinh" (TRUNG VI cua tap nen lay tu du lieu huan luyen). Dung trung vi vi
nhieu dac trung (vi du so ngay chua tap) lech ve phia duoi dai, trung binh se cho mot moc tham chieu kho hieu.

- Logistic Regression: SHAP tinh chinh xac (tuyen tinh) tren log-odds cua xac suat DA HIEU CHINH.
- Model khac (Random Forest, XGBoost): SHAP dang hoan vi (permutation) tren cung dai luong.
- Quy doi sang "diem phan tram" bang do doc cat tuyen cua ham sigmoid giua muc nen va diem du doan,
  nen tong cac dong gop dung bang (diem du doan - muc nen). Day la xap xi de de doc.
"""
import json
from functools import lru_cache

import numpy as np
import pandas as pd
import shap
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline

from .. import performance
from . import registry
from .config import MODELS_DIR, SEED
from .dataset_registry import load_dataset
from .evaluate import risk_level
from .predict import load_model
from .schema import FEATURE_NAMES, validate_features
from .splits import group_split

BACKGROUND_SIZE = 200
MIN_IMPACT_PP = 0.3  # bo cac yeu to dong gop duoi 0.3 diem phan tram

LABELS = {
    "sleep_hours": ("Giờ ngủ", "giờ"), "stress_level": ("Mức căng thẳng", "/5"),
    "energy_level": ("Mức năng lượng", "/5"), "mood": ("Tâm trạng", "/5"), "motivation": ("Động lực", "/5"),
    "free_time_min": ("Thời gian rảnh", "phút"), "missed_days": ("Số ngày chưa tập liên tiếp", "ngày"),
    "workout_frequency_7d": ("Số ngày có tập trong 7 ngày", "ngày"),
    "avg_duration_7d": ("Thời lượng tập trung bình", "phút"), "avg_difficulty_7d": ("Độ khó trung bình", "/5"),
}
LEVEL_VI = {"LOW": "thấp", "MEDIUM": "trung bình", "HIGH": "cao"}


def _sigmoid(x: float) -> float:
    return 1.0 / (1.0 + np.exp(-x))


def _fmt(feature: str, v: float) -> str:
    unit = LABELS[feature][1]
    return f"{v:g}{unit}" if unit == "/5" else f"{v:g} {unit}"


def load_background(version: str) -> pd.DataFrame:
    path = MODELS_DIR / version / "background.csv"
    if path.exists():
        return pd.read_csv(path)[FEATURE_NAMES]
    # Model cu chua luu tap nen: dung lai tap train tu dataset va seed da luu trong metrics.json
    metrics = json.loads((MODELS_DIR / version / "metrics.json").read_text(encoding="utf-8"))
    df, _ = load_dataset(metrics["dataset"]["name"])
    train, _, _ = group_split(df, metrics["seed"])
    bg = train[FEATURE_NAMES].sample(min(BACKGROUND_SIZE, len(train)), random_state=SEED)
    bg.to_csv(path, index=False)
    return bg


class RiskExplainer:
    def __init__(self, model, background: pd.DataFrame):
        self.model = model
        self.bg = background[FEATURE_NAMES].astype(float).reset_index(drop=True)
        self.reference = self.bg.median()
        self._ref_row = pd.DataFrame([self.reference.to_dict()])[FEATURE_NAMES]
        a = float(model.calibrator.lr.coef_[0][0])      # do doc va tung do cua Platt scaling
        b = float(model.calibrator.lr.intercept_[0])
        est = model.estimator
        clf = est.named_steps.get("clf") if isinstance(est, Pipeline) else None
        if isinstance(clf, LogisticRegression):
            scaler = est.named_steps["scale"]
            w = clf.coef_[0] / scaler.scale_
            icpt = clf.intercept_[0] - float(np.sum(clf.coef_[0] * scaler.mean_ / scaler.scale_))
            self.mode = "linear"
            self._explainer = shap.LinearExplainer((a * w, a * icpt + b), self._ref_row)
        else:
            self.mode = "permutation"
            self._explainer = shap.Explainer(self._logit_fn, self._ref_row, algorithm="permutation")

    def _logit_fn(self, X) -> np.ndarray:
        p = self.model.predict_proba(pd.DataFrame(X, columns=FEATURE_NAMES))
        p = np.clip(p, 1e-6, 1 - 1e-6)
        return np.log(p / (1 - p))

    def _shap(self, x: pd.DataFrame) -> tuple[np.ndarray, float]:
        if self.mode == "linear":
            return np.asarray(self._explainer.shap_values(x.to_numpy())[0], dtype=float), float(self._explainer.expected_value)
        ex = self._explainer(x.to_numpy(), max_evals=2 * len(FEATURE_NAMES) + 1 + 400)
        return np.asarray(ex.values[0], dtype=float), float(ex.base_values[0])

    def explain(self, features: dict, top_k: int = 5) -> dict:
        clean = validate_features(features)
        x = pd.DataFrame([clean])[FEATURE_NAMES]
        score = float(self.model.predict_proba(x)[0])
        phi, base_logit = self._shap(x)

        total_logit = base_logit + float(phi.sum())
        p_base, p_total = _sigmoid(base_logit), _sigmoid(total_logit)
        s = float(phi.sum())
        slope = (p_total - p_base) / s if abs(s) > 1e-6 else p_base * (1 - p_base)

        rows = []
        for name, f in zip(FEATURE_NAMES, phi):
            pp = float(f) * slope * 100
            if abs(pp) < MIN_IMPACT_PP:
                continue
            inc = f > 0
            label = LABELS[name][0]
            ref = float(self.reference[name])
            rows.append({
                "feature": name, "label": label, "value": clean[name], "reference": round(ref, 2),
                "direction": "increase" if inc else "decrease",
                "impact_logodds": round(float(f), 4), "impact_pp": round(pp, 2),
                "text": f"{label} ({_fmt(name, clean[name])}; người dùng điển hình: {_fmt(name, round(ref, 1))}) "
                        f"đang góp phần làm {'tăng' if inc else 'giảm'} dự đoán nguy cơ của mô hình",
            })
        rows.sort(key=lambda r: -abs(r["impact_pp"]))
        rows = rows[:top_k]
        total_pp = (p_total - p_base) * 100
        other_pp = round(total_pp - sum(r["impact_pp"] for r in rows), 2)   # cac yeu to nho con lai

        level = risk_level(score)
        ups = [r["label"] for r in rows if r["direction"] == "increase"][:3]
        downs = [r["label"] for r in rows if r["direction"] == "decrease"][:3]
        summary = f"Mô hình dự đoán nguy cơ {score * 100:.0f}% (mức {LEVEL_VI[level]}), so với mức nền {p_base * 100:.0f}% của một người dùng điển hình."
        if ups:
            summary += " Các yếu tố đang góp phần làm tăng dự đoán: " + ", ".join(ups) + "."
        if downs:
            summary += " Các yếu tố đang góp phần làm giảm: " + ", ".join(downs) + "."

        notes = ["Giải thích mô tả cách mô hình dùng dữ liệu để ra điểm, không phải nguyên nhân thực sự của việc bỏ tập."]
        if self.model.meta.get("is_demo"):
            notes.append("Mô hình đang huấn luyện trên dữ liệu DEMO nên con số chỉ mang tính minh hoạ, chưa phải kết quả nghiên cứu.")
        return {
            "risk_score": round(score, 4), "risk_level": level, "base_risk": round(p_base, 4),
            "model_version": self.model.version, "algorithm": self.model.meta.get("algorithm"),
            "is_demo_model": bool(self.model.meta.get("is_demo", False)), "explainer": self.mode,
            "shap_factors": rows, "other_impact_pp": other_pp, "summary": summary, "notes": notes,
        }


@lru_cache(maxsize=4)
def get_explainer(version: str) -> RiskExplainer:
    model = performance.measure("model_load", lambda: load_model(version))
    background = performance.measure("explainer_background_load", lambda: load_background(version))
    return performance.measure("explainer_initialize", lambda: RiskExplainer(model, background))


def explain_risk(features: dict, version: str | None = None, top_k: int = 5) -> dict:
    version = version or registry.active_version() or registry.latest_version()
    if version is None:
        raise FileNotFoundError("Chua co model nao")
    explainer = performance.measure("explainer_cache_lookup", lambda: get_explainer(version))
    cache_info = get_explainer.cache_info()
    performance.event("explainer.cache_stats", hits=cache_info.hits, misses=cache_info.misses)
    return performance.measure("prediction_and_shap", lambda: explainer.explain(features, top_k))
