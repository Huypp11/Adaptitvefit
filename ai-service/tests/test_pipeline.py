import pytest

from app.ml import dataset_registry, predict, registry
from app.ml.demo_data import generate_demo_dataset
from app.ml.train import run_training

GOOD = {"sleep_hours": 7.5, "stress_level": 2, "energy_level": 4, "mood": 4, "motivation": 4,
        "free_time_min": 60, "missed_days": 0, "workout_frequency_7d": 4, "avg_duration_7d": 35, "avg_difficulty_7d": 2.5}
BAD = {"sleep_hours": 4.5, "stress_level": 5, "energy_level": 1, "mood": 1, "motivation": 1,
       "free_time_min": 5, "missed_days": 9, "workout_frequency_7d": 0, "avg_duration_7d": 0, "avg_difficulty_7d": 0}


def test_end_to_end_train_predict_and_activation_gate(isolated_dirs):
    df = generate_demo_dataset(100, 50, seed=7)
    manifest = dataset_registry.save_dataset(df, "t1", source="test")
    assert manifest["is_demo"] is True

    m = run_training("t1", seed=7, fast=True, n_jobs=1)
    assert m["version"] == "v1.0" and m["is_demo"]
    assert set(m["comparison"]) == {"logreg", "random_forest", "xgboost"}
    assert any("DEMO" in w for w in m["warnings"])
    assert 0.5 < m["selected"]["test"]["roc_auc"] <= 1.0

    low, high = predict.predict_risk(GOOD, "v1.0"), predict.predict_risk(BAD, "v1.0")
    assert 0 <= low["risk_score"] <= 1 and low["risk_level"] in {"LOW", "MEDIUM", "HIGH"}
    assert high["risk_score"] > low["risk_score"]
    assert high["is_demo_model"] is True

    # Model moi khong tu dong active; model DEMO bi chan neu khong co --allow-demo
    assert registry.active_version() is None
    with pytest.raises(ValueError, match="DEMO"):
        registry.activate("v1.0")
    registry.activate("v1.0", allow_demo=True)
    assert registry.active_version() == "v1.0"


def test_dataset_hash_detects_tampering_and_versions_are_not_overwritten(isolated_dirs):
    df = generate_demo_dataset(60, 45, seed=2)
    dataset_registry.save_dataset(df, "t2", source="test")
    with pytest.raises(FileExistsError):
        dataset_registry.save_dataset(df, "t2", source="test")
    path = dataset_registry.VERSIONS_DIR / "t2" / "dataset.csv"
    path.write_text(path.read_text() + "\n", encoding="utf-8")
    with pytest.raises(ValueError, match="hash"):
        dataset_registry.load_dataset("t2")


def test_import_csv_marks_real_or_demo(isolated_dirs, tmp_path):
    from app.ml.import_dataset import import_csv

    df = generate_demo_dataset(60, 45, seed=3).drop(columns=["is_demo"])
    f = tmp_path / "x.csv"
    df.to_csv(f, index=False)
    assert import_csv(f, "imp_demo", "test")["is_demo"] is True
    assert import_csv(f, "imp_real", "pilot", is_demo=False)["is_demo"] is False
