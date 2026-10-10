"""Nap va lam sach khao sat Google Form, xuat thong ke TONG HOP (khong chua du lieu ca nhan).

Chay: python -m app.ml.survey --input data/raw/<file>.xlsx
Ket qua:
  data/raw/survey_clean.csv    (tung dong da lam sach, an danh; KHONG commit)
  data/survey_profile.json     (chi gom phan bo tong hop, dung de hieu chinh du lieu DEMO)
"""
import argparse
import json
from datetime import datetime, timezone

import numpy as np
import pandas as pd

from .config import RAW_DIR, SURVEY_PROFILE_PATH

EXPECTED_COLUMNS = 21  # dau thoi gian + 20 cau hoi
COLS = ["timestamp"] + [f"q{i}" for i in range(1, 21)]

SLEEP = {"Dưới 5 giờ": 4.5, "5 - 6 giờ": 5.5, "6 - 7 giờ": 6.5, "7 - 8 giờ": 7.5, "Trên 8 giờ": 8.5}
FREE_TIME = {"Dưới 15": 10.0, "15 - 30 phút": 22.5, "30 - 60 phút": 45.0, "Trên 60 phút": 75.0}
FREQ = {"0 buổi": 0, "1 buổi": 1, "2 buổi": 2, "3 buổi": 3, "4 buổi": 4, "5 buổi trở lên": 5}
SESSION = {"15-30 phút": 22.5, "31-45 phút": 38.0, "46-60 phút": 53.0, "Trên 60 phút": 75.0}
QUIT_LEVEL = {"Chưa bao giờ": 0, "Đã từng 1 lần": 1, "Đã từng 2 -3 lần": 2, "Nhiều lần": 3}
BARRIERS = {
    "Không có thời gian": "no_time", "Học tập / công việc bận": "busy", "Thiếu động lực": "low_motivation",
    "Mệt mỏi": "fatigue", "Stress": "stress", "Thiếu ngủ": "sleep", "Bài tập nhàm chán": "boring",
    "Bài tập quá khó": "too_hard", "Lịch tập không phù hợp": "schedule", "Không có người tập cùng": "no_partner",
    "Do thời tiết/yếu tố bên ngoài": "weather", "Khác": "other",
}


def _norm(s: pd.Series) -> pd.Series:
    return s.astype(str).str.replace(r"\s+", " ", regex=True).str.strip()


def load_raw(path) -> pd.DataFrame:
    df = pd.read_excel(path) if str(path).lower().endswith((".xlsx", ".xls")) else pd.read_csv(path)
    if df.shape[1] != EXPECTED_COLUMNS:
        raise ValueError(f"Khao sat can {EXPECTED_COLUMNS} cot (dau thoi gian + 20 cau), file co {df.shape[1]} cot")
    df.columns = COLS
    return df


def clean(raw: pd.DataFrame) -> tuple[pd.DataFrame, dict]:
    """Tra ve (bang da lam sach, bao cao chat luong). Khong giu tuoi, nghe nghiep, dau thoi gian."""
    r = raw.copy()
    for c in COLS[1:]:
        if r[c].dtype == object:
            r[c] = _norm(r[c])

    out = pd.DataFrame(index=r.index)
    out["sleep_hours"] = r.q9.map(SLEEP)
    out["stress_level"] = pd.to_numeric(r.q10, errors="coerce")
    out["energy_level"] = pd.to_numeric(r.q11, errors="coerce")
    out["motivation"] = pd.to_numeric(r.q12, errors="coerce")
    out["free_time_min"] = r.q13.map(FREE_TIME)                      # "Tuy chon 4" (loi form) -> NaN
    out["sessions_per_week"] = r.q4.map(FREQ)
    out["session_min"] = r.q5.map(SESSION)
    out["schedule_fit"] = pd.to_numeric(r.q14, errors="coerce")
    out["missed_7d"] = r.q16.replace({"5 trở lên": 5}).pipe(pd.to_numeric, errors="coerce")
    out["return_likelihood"] = pd.to_numeric(r.q17, errors="coerce")
    out["want_adaptive"] = pd.to_numeric(r.q18, errors="coerce")
    out["quit_level"] = r.q7.map(QUIT_LEVEL)
    out["quit_after"] = r.q8
    out["location"] = r.q6
    out["goal"] = r.q3
    out["when_tired_choice"] = r.q19
    out["top_retention_factor"] = r.q20
    tokens = r.q15.str.split(",").apply(lambda xs: {x.strip() for x in xs})
    for text, slug in BARRIERS.items():
        out[f"barrier_{slug}"] = tokens.apply(lambda s, t=text: int(t in s))

    quality = {
        "placeholder_free_time_option": int((~r.q13.isin(FREE_TIME)).sum()),
        "q7_never_but_gave_quit_time": int(((r.q7 == "Chưa bao giờ") & (r.q8 != "Chưa từng bỏ tập")).sum()),
        "missed_7d_has_no_zero_option": bool(out["missed_7d"].min() >= 1),
        "respondents_under_18": int((r.q1 == "Dưới 18").sum()),
        "rows_with_missing_core_fields": int(out[["sleep_hours", "stress_level", "energy_level", "motivation"]].isna().any(axis=1).sum()),
    }
    return out, quality


def _pmf(s: pd.Series) -> dict:
    vc = s.dropna().value_counts(normalize=True).sort_index()
    return {"values": [float(v) for v in vc.index], "probs": [round(float(p), 6) for p in vc.values]}


def build_profile(clean_df: pd.DataFrame, quality: dict) -> dict:
    dist_cols = ["sleep_hours", "stress_level", "energy_level", "motivation", "free_time_min",
                 "sessions_per_week", "session_min"]
    barriers = {c.replace("barrier_", ""): int(clean_df[c].sum()) for c in clean_df.columns if c.startswith("barrier_")}
    return {
        "source": "google_form_survey",
        "n": int(len(clean_df)),
        "created_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "note": ("Chi gom phan bo tong hop tu khao sat mot lan, dung de hieu chinh phan phoi nen cua du lieu DEMO. "
                 "Khong co du lieu ca nhan va khong dung de huan luyen model chinh."),
        "distributions": {c: _pmf(clean_df[c]) for c in dist_cols},
        "summary": {
            "ever_quit_rate": round(float((clean_df.quit_level > 0).mean()), 4),
            "quit_many_times_rate": round(float((clean_df.quit_level == 3).mean()), 4),
            "barrier_counts": barriers,
            "when_tired_choice": clean_df.when_tired_choice.value_counts().to_dict(),
        },
        "quality": quality,
    }


def run(input_path) -> dict:
    cleaned, quality = clean(load_raw(input_path))
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    cleaned.to_csv(RAW_DIR / "survey_clean.csv", index=False, encoding="utf-8-sig")
    profile = build_profile(cleaned, quality)
    SURVEY_PROFILE_PATH.write_text(json.dumps(profile, ensure_ascii=False, indent=2), encoding="utf-8")
    return profile


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--input", required=True, help="Duong dan file .xlsx/.csv xuat tu Google Form")
    p = run(ap.parse_args().input)
    print(f"Da xu ly {p['n']} phan hoi. Ghi {SURVEY_PROFILE_PATH}")
    print("Chat luong:", json.dumps(p["quality"], ensure_ascii=False))


if __name__ == "__main__":
    main()
