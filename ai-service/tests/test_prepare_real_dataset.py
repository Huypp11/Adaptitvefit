import pandas as pd
import pytest

from app.ml.config import LABEL_COL
from app.ml.prepare_real_dataset import build_real_dataset
from app.ml.schema import FEATURE_NAMES


def _inputs():
    snapshots = pd.DataFrame([{
        "user_id": "pseudonym-1",
        "snapshot_date": "2026-01-01",
        "training_consent": True,
        **{name: 3 for name in FEATURE_NAMES},
    }])
    outcomes = pd.DataFrame([{
        "user_id": "pseudonym-1",
        "outcome_date": (pd.Timestamp("2026-01-01") + pd.Timedelta(days=offset)).date().isoformat(),
        "training_consent": True,
        "observed": True,
        "did_workout": False,
    } for offset in range(1, 8)])
    return snapshots, outcomes


def test_builds_positive_label_only_for_seven_observed_no_workout_days():
    snapshots, outcomes = _inputs()
    dataset, counts = build_real_dataset(snapshots, outcomes)

    assert len(dataset) == 1
    assert dataset.iloc[0][LABEL_COL] == 1
    assert dataset.iloc[0]["is_demo"] == False
    assert counts["included"] == 1


def test_any_observed_workout_makes_dropout_label_zero():
    snapshots, outcomes = _inputs()
    outcomes.loc[3, "did_workout"] = True

    dataset, _ = build_real_dataset(snapshots, outcomes)

    assert dataset.iloc[0][LABEL_COL] == 0


@pytest.mark.parametrize("change", [
    {"observed": False},
    {"training_consent": False},
    {"did_workout": None},
])
def test_incomplete_or_unconsented_outcome_window_is_excluded(change):
    snapshots, outcomes = _inputs()
    if "did_workout" in change:
        outcomes["did_workout"] = outcomes["did_workout"].astype(object)
    outcomes.loc[3, list(change)] = list(change.values())

    dataset, counts = build_real_dataset(snapshots, outcomes)

    assert dataset.empty
    assert counts["excluded_incomplete_or_unconsented_horizon"] == 1


def test_missing_day_is_not_mislabeled_as_no_workout():
    snapshots, outcomes = _inputs()
    outcomes = outcomes.drop(index=3)

    dataset, _ = build_real_dataset(snapshots, outcomes)

    assert dataset.empty


def test_training_consent_is_required_and_email_ids_are_rejected():
    snapshots, outcomes = _inputs()
    snapshots.loc[0, "training_consent"] = False
    dataset, counts = build_real_dataset(snapshots, outcomes)
    assert dataset.empty
    assert counts["excluded_without_training_consent"] == 1

    snapshots, outcomes = _inputs()
    snapshots.loc[0, "user_id"] = "person@example.com"
    with pytest.raises(ValueError, match="mã giả danh"):
        build_real_dataset(snapshots, outcomes)


def test_duplicate_user_day_is_rejected():
    snapshots, outcomes = _inputs()
    outcomes = pd.concat([outcomes, outcomes.iloc[[0]]], ignore_index=True)

    with pytest.raises(ValueError, match="outcome trùng"):
        build_real_dataset(snapshots, outcomes)
