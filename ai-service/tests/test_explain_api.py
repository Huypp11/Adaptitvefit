import numpy as np
import pandas as pd
import pytest
from fastapi.testclient import TestClient

from app import main
from app.ml import dataset_registry, registry
from app.ml.calibration import PlattCalibrator, RiskModel
from app.ml.config import LABEL_COL
from app.ml.demo_data import generate_demo_dataset
from app.ml.explain import RiskExplainer
from app.ml.models import candidates
from app.ml.schema import FEATURE_NAMES
from app.ml.train import run_training

HIGH = {"sleep_hours": 5, "stress_level": 4, "energy_level": 2, "mood": 2, "motivation": 2, "free_time_min": 15,
        "missed_days": 4, "workout_frequency_7d": 1, "avg_duration_7d": 20, "avg_difficulty_7d": 3}
LOW = {"sleep_hours": 7.5, "stress_level": 2, "energy_level": 4, "mood": 4, "motivation": 4, "free_time_min": 60,
       "missed_days": 0, "workout_frequency_7d": 4, "avg_duration_7d": 35, "avg_difficulty_7d": 2.5}


@pytest.fixture(scope="module")
def data():
    df = generate_demo_dataset(80, 45, seed=11)
    return df[FEATURE_NAMES], df[LABEL_COL]


def _make_explainer(name, data):
    X, y = data
    est = candidates(seed=1, scale_pos_weight=2.0, fast=True)[name][0].fit(X, y)
    cal = PlattCalibrator().fit(est.predict_proba(X)[:, 1], y)
    model = RiskModel(est, cal, FEATURE_NAMES, 0.3, "vT", {"is_demo": True, "algorithm": name})
    return RiskExplainer(model, X.sample(100, random_state=1)), model


@pytest.mark.parametrize("name,mode", [("logreg", "linear"), ("random_forest", "permutation")])
def test_explanation_is_additive_and_matches_model(name, mode, data):
    ex, model = _make_explainer(name, data)
    assert ex.mode == mode
    for feats in (HIGH, LOW):
        x = pd.DataFrame([feats])[FEATURE_NAMES]
        phi, base_logit = ex._shap(x)
        score = float(model.predict_proba(x)[0])
        total = 1 / (1 + np.exp(-(base_logit + phi.sum())))
        assert abs(total - score) < 1e-4                                  # SHAP cong gop dung bang du doan cua model
        out = ex.explain(feats)
        shown = sum(f["impact_pp"] for f in out["shap_factors"]) + out["other_impact_pp"]
        assert abs(shown - (score - out["base_risk"]) * 100) < 0.1
        assert out["risk_level"] in {"LOW", "MEDIUM", "HIGH"} and out["summary"]


def test_linear_shap_equals_closed_form(data):
    ex, model = _make_explainer("logreg", data)
    est = model.estimator
    clf, scaler = est.named_steps["clf"], est.named_steps["scale"]
    a = model.calibrator.lr.coef_[0][0]
    x = pd.DataFrame([HIGH])[FEATURE_NAMES]
    expected = a * clf.coef_[0] / scaler.scale_ * (x.iloc[0].to_numpy() - ex.reference.to_numpy())
    phi, _ = ex._shap(x)
    assert np.allclose(phi, expected, atol=1e-6)


def test_wording_is_never_causal(data):
    ex, _ = _make_explainer("logreg", data)
    for feats in (HIGH, LOW):
        out = ex.explain(feats)
        assert out["notes"] and "không phải nguyên nhân" in out["notes"][0]
        for f in out["shap_factors"]:
            assert "góp phần" in f["text"] and "dự đoán" in f["text"]
            assert "gây ra" not in f["text"] and "bởi vì" not in f["text"] and "do bạn" not in f["text"]
        assert any("DEMO" in n for n in out["notes"])


def test_high_risk_example_scores_higher_than_low(data):
    ex, _ = _make_explainer("logreg", data)
    assert ex.explain(HIGH)["risk_score"] > ex.explain(LOW)["risk_score"]


# ---------------- API ----------------
@pytest.fixture
def api(isolated_dirs, monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_KEY", "test-key")
    with TestClient(main.app) as client:
        yield client


def _train_and_activate():
    dataset_registry.save_dataset(generate_demo_dataset(100, 50, seed=7), "t1", source="test")
    run_training("t1", seed=7, fast=True, n_jobs=1)
    registry.activate("v1.0", allow_demo=True)


def test_api_without_active_model_returns_503(api):
    assert api.get("/health").json() == {"status": "ok", "active_model": None, "is_demo_model": None}
    r = api.post("/predict", json=HIGH, headers={"X-Internal-Key": "test-key"})
    assert r.status_code == 503


def test_api_requires_key_validates_input_and_returns_explanation(api):
    _train_and_activate()
    assert api.get("/health").json()["active_model"] == "v1.0"
    assert api.post("/predict", json=HIGH).status_code == 401
    assert api.post("/predict", json=HIGH, headers={"X-Internal-Key": "wrong"}).status_code == 401

    ok = api.post("/predict", json=HIGH, headers={"X-Internal-Key": "test-key"})
    assert ok.status_code == 200
    body = ok.json()
    assert {"risk_score", "risk_level", "base_risk", "model_version", "shap_factors", "summary", "notes"} <= set(body)
    assert body["model_version"] == "v1.0" and body["is_demo_model"] is True

    h = {"X-Internal-Key": "test-key"}
    assert api.post("/predict", json={**HIGH, "stress_level": 9}, headers=h).status_code == 422
    assert api.post("/predict", json={**HIGH, "age": 20}, headers=h).status_code == 422
    missing = {k: v for k, v in HIGH.items() if k != "mood"}
    assert api.post("/predict", json=missing, headers=h).status_code == 422
    assert api.get("/models", headers=h).status_code == 200
    assert api.post("/reload", headers=h).json()["active_model"] == "v1.0"


def test_service_refuses_to_start_without_key(isolated_dirs, monkeypatch):
    monkeypatch.delenv("AI_INTERNAL_KEY", raising=False)
    with pytest.raises(RuntimeError):
        with TestClient(main.app):
            pass
