"""Sinh du lieu DEMO theo chuoi ngay cua tung nguoi dung.

LUU Y QUAN TRONG
- Phan phoi NEN cua tung nguoi (ngu, stress, nang luong, dong luc, thoi gian ranh, tan suat tap)
  lay mau tu thong ke tong hop cua khao sat (data/survey_profile.json) neu co.
- Quan he giua trang thai va viec tap/bo tap la GIA DINH cua nguoi xay dung, KHONG rut ra tu du lieu.
  Vi vay metric tren du lieu DEMO chi chung minh quy trinh chay dung, khong co gia tri ket luan.
- Nhan duoc sinh ra tu qua trinh ngau nhien theo chuoi ngay (co trang thai an "ngung tap" khong
  quan sat duoc), khong phai cong thuc truc tiep cua feature, de tranh model chi hoc lai cong thuc.
"""
import argparse
import json

import numpy as np
import pandas as pd

from .config import (DAY_COL, GROUP_COL, HISTORY_DAYS, HORIZON_DAYS, LABEL_COL, SEED,
                     SURVEY_PROFILE_PATH)
from .dataset_registry import save_dataset
from .schema import FEATURE_NAMES

_DEFAULTS = {
    "sleep_hours": {"values": [4.5, 5.5, 6.5, 7.5, 8.5], "probs": [0.08, 0.2, 0.35, 0.28, 0.09]},
    "stress_level": {"values": [1, 2, 3, 4, 5], "probs": [0.2, 0.2, 0.2, 0.2, 0.2]},
    "energy_level": {"values": [1, 2, 3, 4, 5], "probs": [0.1, 0.17, 0.43, 0.19, 0.11]},
    "motivation": {"values": [1, 2, 3, 4, 5], "probs": [0.15, 0.15, 0.5, 0.06, 0.14]},
    "free_time_min": {"values": [10.0, 22.5, 45.0, 75.0], "probs": [0.27, 0.27, 0.27, 0.19]},
    "sessions_per_week": {"values": [0, 1, 2, 3, 4, 5], "probs": [0.17, 0.17, 0.15, 0.3, 0.06, 0.15]},
    "session_min": {"values": [22.5, 38.0, 53.0, 75.0], "probs": [0.5, 0.13, 0.15, 0.22]},
}


def load_distributions() -> tuple[dict, str]:
    if SURVEY_PROFILE_PATH.exists():
        prof = json.loads(SURVEY_PROFILE_PATH.read_text(encoding="utf-8"))
        return prof["distributions"], f"survey_profile(n={prof['n']})"
    return _DEFAULTS, "default_distributions"


def _draw(dist: dict, rng) -> float:
    return float(rng.choice(dist["values"], p=np.array(dist["probs"]) / np.sum(dist["probs"])))


def _sig(x: float) -> float:
    return 1.0 / (1.0 + np.exp(-x))


def _simulate_user(rng, dist: dict, n_days: int) -> dict:
    base_sleep = _draw(dist["sleep_hours"], rng) + rng.uniform(-0.5, 0.5)
    base_stress = np.clip(_draw(dist["stress_level"], rng) + rng.normal(0, 0.3), 1, 5)
    base_energy = np.clip(_draw(dist["energy_level"], rng) + rng.normal(0, 0.3), 1, 5)
    base_motiv = np.clip(_draw(dist["motivation"], rng) + rng.normal(0, 0.3), 1, 5)
    base_free = _draw(dist["free_time_min"], rng) * float(np.exp(rng.normal(0, 0.2)))
    base_dur = _draw(dist["session_min"], rng)
    p_base = float(np.clip(_draw(dist["sessions_per_week"], rng) / 7.0, 0.05, 0.85))
    resil = float(rng.beta(2, 2))                  # khong quan sat duoc
    p0 = np.log(p_base / (1 - p_base)) - 0.9 * p_base + 0.4

    sleep, stress, energy, motiv = base_sleep, base_stress, base_energy, base_motiv
    habit, engaged, shock, prev_ex = p_base, 1, 0, 0
    cols = {k: np.zeros(n_days) for k in ("sleep", "stress", "energy", "mood", "motiv", "free", "ex", "dur", "diff")}

    for t in range(n_days):
        weekend = (t % 7) >= 5
        if shock == 0 and rng.random() < 0.04:
            shock = int(rng.integers(3, 6))        # giai doan ban bat thuong
        busy = shock > 0
        shock = max(0, shock - 1)

        sleep = base_sleep + 0.35 * (sleep - base_sleep) + rng.normal(0, 0.8) + (0.3 if weekend else 0)
        stress = base_stress + 0.7 * (stress - base_stress) + rng.normal(0, 0.7) + (0.8 if busy else 0) - (0.2 if weekend else 0)
        energy = (base_energy + 0.6 * (energy - base_energy) + 0.35 * (sleep - base_sleep)
                  - 0.25 * (stress - base_stress) + rng.normal(0, 0.6))
        motiv = base_motiv + 0.8 * (motiv - base_motiv) + 0.3 * (prev_ex - 0.3) + rng.normal(0, 0.4)
        free = base_free * float(np.exp(rng.normal(0, 0.5))) * (1.4 if weekend else 1.0) * (0.3 if busy else 1.0)

        s_ = float(np.clip(round(stress), 1, 5)); e_ = float(np.clip(round(energy), 1, 5))
        m_ = float(np.clip(round(motiv), 1, 5)); sl_ = float(np.clip(round(sleep * 2) / 2, 2, 12))
        mood = float(np.clip(round(3 + 0.5 * (e_ - 3) - 0.3 * (s_ - 3) + rng.normal(0, 0.6)), 1, 5))
        free_ = float(np.clip(round(free / 5) * 5, 0, 480))

        zs, ze, zm, zsl = s_ - 3, e_ - 3, m_ - 3, sl_ - 7
        if engaged:
            q = _sig(-4.5 + 0.6 * zs - 0.5 * zm - 0.3 * zsl + (0.5 if busy else 0) - 1.8 * (resil - 0.5))
            if rng.random() < q:
                engaged = 0
        elif rng.random() < _sig(-1.6 + 0.5 * zm + 0.3 * ze + 1.8 * (resil - 0.5)):
            engaged = 1

        logit = (p0 + 0.7 * zm + 0.5 * ze - 0.4 * zs + 0.25 * zsl
                 + 0.35 * np.log(max(free_, 5) / 30) + 0.9 * habit + (0.2 if weekend else 0))
        if not engaged:
            logit -= 2.5
        ex = int(rng.random() < _sig(logit))
        habit = 0.9 * habit + 0.1 * ex
        prev_ex = ex

        cols["sleep"][t], cols["stress"][t], cols["energy"][t] = sl_, s_, e_
        cols["mood"][t], cols["motiv"][t], cols["free"][t], cols["ex"][t] = mood, m_, free_, ex
        if ex:
            cols["dur"][t] = float(np.clip(round(min(base_dur * np.exp(rng.normal(0, 0.25)), max(10, free_)) / 5) * 5, 5, 300))
            cols["diff"][t] = float(np.clip(round(2.6 + 0.35 * (3 - e_) + 0.2 * (s_ - 3) + rng.normal(0, 0.7)), 1, 5))
    return cols


def _rows_for_user(uid: int, c: dict, n_days: int) -> list[dict]:
    ex, rows = c["ex"], []
    for t in range(HISTORY_DAYS, n_days - HORIZON_DAYS):
        window = slice(t - HISTORY_DAYS + 1, t + 1)
        done = ex[window] > 0
        missed = 0
        while t - missed >= 0 and ex[t - missed] == 0:
            missed += 1
        rows.append({
            GROUP_COL: uid, DAY_COL: t,
            "sleep_hours": c["sleep"][t], "stress_level": c["stress"][t], "energy_level": c["energy"][t],
            "mood": c["mood"][t], "motivation": c["motiv"][t], "free_time_min": c["free"][t],
            "missed_days": min(missed, 60),
            "workout_frequency_7d": int(done.sum()),
            "avg_duration_7d": float(c["dur"][window][done].mean()) if done.any() else 0.0,
            "avg_difficulty_7d": float(c["diff"][window][done].mean()) if done.any() else 0.0,
            LABEL_COL: int(ex[t + 1:t + 1 + HORIZON_DAYS].sum() == 0),
        })
    return rows


def generate_demo_dataset(n_users: int = 400, n_days: int = 70, seed: int = SEED, dist: dict | None = None) -> pd.DataFrame:
    dist = dist or load_distributions()[0]
    rng = np.random.default_rng(seed)
    rows: list[dict] = []
    for uid in range(1, n_users + 1):
        rows += _rows_for_user(uid, _simulate_user(rng, dist, n_days), n_days)
    df = pd.DataFrame(rows)
    df["is_demo"] = True
    return df[[GROUP_COL, DAY_COL] + FEATURE_NAMES + [LABEL_COL, "is_demo"]].round(3)


def main():
    ap = argparse.ArgumentParser(description="Sinh va luu dataset DEMO (co gan co is_demo)")
    ap.add_argument("--name", required=True, help="Ten phien ban, vi du demo_v1")
    ap.add_argument("--users", type=int, default=400)
    ap.add_argument("--days", type=int, default=70)
    ap.add_argument("--seed", type=int, default=SEED)
    a = ap.parse_args()
    dist, src = load_distributions()
    df = generate_demo_dataset(a.users, a.days, a.seed, dist)
    m = save_dataset(df, a.name, source=f"demo_simulation[{src}]",
                     notes="DEMO DATA de phat trien, chua phai du lieu nghien cuu thuc te.",
                     params={"users": a.users, "days": a.days, "seed": a.seed, "distributions": src})
    print(f"Da luu {a.name}: {m['rows']} dong, {m['users']} nguoi dung, ty le dropout_7d={m['positive_rate']:.1%}")
    print("DAY LA DU LIEU DEMO, chua phai du lieu nghien cuu thuc te.")


if __name__ == "__main__":
    main()
