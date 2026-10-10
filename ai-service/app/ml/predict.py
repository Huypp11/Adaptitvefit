"""Du doan nguy co bo tap cho mot dong feature. (SHAP se duoc them o Phase 6.)

Vi du: python -m app.ml.predict --version v1.0 --json "{...10 feature...}"
"""
import argparse
import json
from functools import lru_cache

import joblib
import pandas as pd

from . import registry
from .config import MODELS_DIR
from .evaluate import risk_level
from .schema import validate_features


@lru_cache(maxsize=4)
def load_model(version: str | None = None):
    version = version or registry.active_version() or registry.latest_version()
    if version is None:
        raise FileNotFoundError("Chua co model nao. Hay huan luyen truoc: python -m app.ml.train --dataset <ten>")
    return joblib.load(MODELS_DIR / version / "model.joblib")


def predict_risk(features: dict, version: str | None = None) -> dict:
    clean = validate_features(features)
    model = load_model(version)
    score = float(model.predict_proba(pd.DataFrame([clean]))[0])
    return {
        "risk_score": round(score, 4), "risk_level": risk_level(score),
        "model_version": model.version, "is_demo_model": bool(model.meta.get("is_demo", False)),
    }


def main():
    ap = argparse.ArgumentParser(description="Du doan nguy co bo tap")
    ap.add_argument("--json", required=True, help="Chuoi JSON chua dung 10 feature")
    ap.add_argument("--version", default=None)
    a = ap.parse_args()
    print(json.dumps(predict_risk(json.loads(a.json), a.version), indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
