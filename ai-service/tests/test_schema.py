import pytest

from app.ml.schema import FEATURE_NAMES, validate_features

OK = {"sleep_hours": 7, "stress_level": 3, "energy_level": 3, "mood": 3, "motivation": 3,
      "free_time_min": 30, "missed_days": 1, "workout_frequency_7d": 2, "avg_duration_7d": 30, "avg_difficulty_7d": 2}


def test_valid_row_passes():
    assert set(validate_features(OK)) == set(FEATURE_NAMES)


def test_missing_unknown_and_out_of_range():
    with pytest.raises(ValueError, match="Thieu"):
        validate_features({"sleep_hours": 7})
    with pytest.raises(ValueError, match="khong hop le"):
        validate_features({**OK, "age": 20})
    with pytest.raises(ValueError, match="ngoai khoang"):
        validate_features({**OK, "stress_level": 9})
    with pytest.raises(ValueError, match="phai la so"):
        validate_features({**OK, "mood": "abc"})
