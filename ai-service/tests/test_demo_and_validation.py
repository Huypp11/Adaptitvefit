import pandas as pd
import pytest

from app.ml.config import GROUP_COL, LABEL_COL
from app.ml.demo_data import generate_demo_dataset
from app.ml.schema import FEATURE_NAMES
from app.ml.splits import group_split
from app.ml.validation import validate_dataset


@pytest.fixture(scope="module")
def demo():
    return generate_demo_dataset(60, 50, seed=1)


def test_demo_dataset_is_valid_and_flagged(demo):
    rep = validate_dataset(demo)
    assert rep.ok, rep.errors
    assert demo["is_demo"].all()
    assert any("DEMO" in w for w in rep.warnings)


def test_reproducible_with_same_seed(demo):
    pd.testing.assert_frame_equal(demo, generate_demo_dataset(60, 50, seed=1))


def test_label_and_history_are_consistent_without_leakage(demo):
    """Neu ngay t co nhan=1 (khong tap t+1..t+7) thi ngay t+1 chua tap: missed_days(t+1) = missed_days(t)+1.
    Neu ngay t+1 co tap (missed_days=0) thi nhan cua ngay t phai = 0."""
    d = demo.sort_values([GROUP_COL, "day_index"])
    nxt = d.groupby(GROUP_COL).shift(-1)
    ok = nxt["day_index"] == d["day_index"] + 1
    pos = d[ok & (d[LABEL_COL] == 1)]
    assert (nxt.loc[pos.index, "missed_days"] == pos["missed_days"] + 1).all()
    trained_next = d[ok & (nxt["missed_days"] == 0)]
    assert (trained_next[LABEL_COL] == 0).all()


def test_history_features_are_internally_consistent(demo):
    assert ((demo.missed_days >= 7) <= (demo.workout_frequency_7d == 0)).all()
    assert ((demo.workout_frequency_7d == 0) <= (demo.avg_duration_7d == 0)).all()


def test_split_has_no_user_overlap(demo):
    tr, va, te = group_split(demo, seed=3)
    users = [set(x[GROUP_COL]) for x in (tr, va, te)]
    assert not (users[0] & users[1] or users[0] & users[2] or users[1] & users[2])
    assert sum(len(u) for u in users) == demo[GROUP_COL].nunique()


def test_validation_catches_problems(demo):
    assert not validate_dataset(demo.drop(columns=["mood"])).ok
    bad = demo.copy(); bad.loc[0, "stress_level"] = 9
    assert any("stress_level" in e for e in validate_dataset(bad).errors)
    nulls = demo.copy(); nulls.loc[0, "mood"] = None
    assert not validate_dataset(nulls).ok
    leaky = demo.copy(); leaky["exercised_next_day"] = 1
    assert any("ro ri" in e for e in validate_dataset(leaky).errors)
    mixed = demo.copy(); mixed.loc[:10, "is_demo"] = False
    assert any("DEMO va du lieu that" in e for e in validate_dataset(mixed).errors)
    dup = pd.concat([demo, demo.iloc[:1]])
    assert any("trung" in e for e in validate_dataset(dup).errors)
    assert set(FEATURE_NAMES) <= set(demo.columns)
