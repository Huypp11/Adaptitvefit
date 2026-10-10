import pandas as pd

from app.ml.survey import COLS, build_profile, clean


def _raw(n=6):
    row = {c: "3" for c in COLS}
    row.update({"timestamp": "2026-10-05", "q1": "21-23", "q2": "Sinh viên", "q3": "Cải thiện sức khỏe", "q4": "3 buổi",
                "q5": "15-30 phút", "q6": "Tại nhà", "q7": "Nhiều lần", "q8": "1 - 2 tuần", "q9": "6 - 7 giờ",
                "q13": "30 - 60 phút", "q15": "Mệt mỏi ,Stress", "q16": "5 trở lên", "q19": "Bài tập nhẹ 10–15 phút",
                "q20": "Thấy được tiến bộ"})
    df = pd.DataFrame([row] * n)
    df.loc[0, "q13"] = "Tùy chọn 4"                    # loi form
    df.loc[1, "q7"] = "Chưa bao giờ"                   # mau thuan voi q8
    df.loc[2, "q1"] = "Dưới 18"
    return df


def test_clean_handles_known_problems_and_drops_identifying_columns():
    out, quality = clean(_raw())
    assert pd.isna(out.loc[0, "free_time_min"])
    assert quality["placeholder_free_time_option"] == 1
    assert quality["q7_never_but_gave_quit_time"] == 1
    assert quality["respondents_under_18"] == 1
    assert out.loc[3, "missed_7d"] == 5 and out.loc[3, "sleep_hours"] == 6.5
    assert out.loc[3, "barrier_fatigue"] == 1 and out.loc[3, "barrier_stress"] == 1 and out.loc[3, "barrier_weather"] == 0
    assert not {"timestamp", "age", "q1", "q2"} & set(out.columns)


def test_profile_has_only_aggregates():
    out, quality = clean(_raw())
    prof = build_profile(out, quality)
    assert prof["n"] == 6 and "sleep_hours" in prof["distributions"]
    assert abs(sum(prof["distributions"]["sleep_hours"]["probs"]) - 1) < 1e-3
