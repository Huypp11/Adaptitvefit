"""Build a real training dataset from consented daily snapshots and observed outcomes.

Snapshots need one row per prediction date. Outcomes need one row per user and
calendar day, including explicit observation coverage and workout occurrence.
Missing or unobserved days are never treated as days without a workout.
"""
import argparse

import pandas as pd

from .config import DAY_COL, GROUP_COL, HORIZON_DAYS, LABEL_COL
from .dataset_registry import save_dataset
from .schema import FEATURE_NAMES

SNAPSHOT_COLUMNS = [GROUP_COL, "snapshot_date", "training_consent", *FEATURE_NAMES]
OUTCOME_COLUMNS = [GROUP_COL, "outcome_date", "training_consent", "observed", "did_workout"]


def _parse_bool(value, field: str, allow_missing: bool = False):
    if pd.isna(value):
        if allow_missing:
            return None
        raise ValueError(f"{field} không được để trống")
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)) and value in (0, 1):
        return bool(value)
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"true", "1", "yes"}:
            return True
        if normalized in {"false", "0", "no"}:
            return False
    raise ValueError(f"{field} chỉ nhận true/false")


def _normalize_ids(frame: pd.DataFrame, label: str) -> pd.Series:
    ids = frame[GROUP_COL].astype("string").str.strip()
    if ids.isna().any() or ids.eq("").any():
        raise ValueError(f"{label}: user_id không được để trống")
    if ids.str.contains("@", regex=False).any():
        raise ValueError(f"{label}: không dùng email làm user_id; hãy dùng mã giả danh")
    return ids


def build_real_dataset(snapshots: pd.DataFrame, outcomes: pd.DataFrame) -> tuple[pd.DataFrame, dict]:
    """Derive dropout_7d only when all seven consented outcome days are observed."""
    missing_snapshots = [c for c in SNAPSHOT_COLUMNS if c not in snapshots.columns]
    missing_outcomes = [c for c in OUTCOME_COLUMNS if c not in outcomes.columns]
    if missing_snapshots:
        raise ValueError(f"Snapshots thiếu cột: {', '.join(missing_snapshots)}")
    if missing_outcomes:
        raise ValueError(f"Outcomes thiếu cột: {', '.join(missing_outcomes)}")

    snap = snapshots[SNAPSHOT_COLUMNS].copy()
    daily = outcomes[OUTCOME_COLUMNS].copy()
    snap[GROUP_COL] = _normalize_ids(snap, "Snapshots")
    daily[GROUP_COL] = _normalize_ids(daily, "Outcomes")
    snap["snapshot_date"] = pd.to_datetime(snap["snapshot_date"], errors="coerce").dt.date
    daily["outcome_date"] = pd.to_datetime(daily["outcome_date"], errors="coerce").dt.date
    if snap["snapshot_date"].isna().any() or daily["outcome_date"].isna().any():
        raise ValueError("Ngày snapshot/outcome không hợp lệ")
    if snap.duplicated([GROUP_COL, "snapshot_date"]).any():
        raise ValueError("Có snapshot trùng user_id và snapshot_date")
    if daily.duplicated([GROUP_COL, "outcome_date"]).any():
        raise ValueError("Có outcome trùng user_id và outcome_date")

    snap["training_consent"] = snap["training_consent"].map(
        lambda value: _parse_bool(value, "training_consent"))
    daily["training_consent"] = daily["training_consent"].map(
        lambda value: _parse_bool(value, "training_consent"))
    daily["observed"] = daily["observed"].map(lambda value: _parse_bool(value, "observed"))
    daily["did_workout"] = daily["did_workout"].map(
        lambda value: _parse_bool(value, "did_workout", allow_missing=True))

    outcome_by_date = daily.set_index([GROUP_COL, "outcome_date"])
    first_snapshot = snap.groupby(GROUP_COL)["snapshot_date"].min().to_dict()
    records = []
    counts = {
        "snapshots": len(snap),
        "excluded_without_training_consent": 0,
        "excluded_incomplete_or_unconsented_horizon": 0,
        "included": 0,
    }

    for row in snap.itertuples(index=False, name=None):
        values = dict(zip(SNAPSHOT_COLUMNS, row))
        if not values["training_consent"]:
            counts["excluded_without_training_consent"] += 1
            continue

        observed_workouts = []
        complete_horizon = True
        for offset in range(1, HORIZON_DAYS + 1):
            outcome_date = values["snapshot_date"] + pd.Timedelta(days=offset)
            outcome_date = outcome_date.date() if hasattr(outcome_date, "date") else outcome_date
            key = (values[GROUP_COL], outcome_date)
            if key not in outcome_by_date.index:
                complete_horizon = False
                break
            outcome = outcome_by_date.loc[key]
            did_workout = outcome["did_workout"]
            if not outcome["training_consent"] or not outcome["observed"] or pd.isna(did_workout):
                complete_horizon = False
                break
            observed_workouts.append(bool(did_workout))

        if not complete_horizon:
            counts["excluded_incomplete_or_unconsented_horizon"] += 1
            continue

        record = {
            GROUP_COL: values[GROUP_COL],
            DAY_COL: (values["snapshot_date"] - first_snapshot[values[GROUP_COL]]).days,
            **{name: values[name] for name in FEATURE_NAMES},
            LABEL_COL: int(not any(observed_workouts)),
            "is_demo": False,
        }
        records.append(record)
        counts["included"] += 1

    columns = [GROUP_COL, DAY_COL, *FEATURE_NAMES, LABEL_COL, "is_demo"]
    return pd.DataFrame.from_records(records, columns=columns), counts


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--snapshots", required=True, help="CSV snapshot: feature tại ngày dự đoán")
    parser.add_argument("--outcomes", required=True, help="CSV outcome từng ngày, có cờ quan sát rõ ràng")
    parser.add_argument("--name", required=True, help="Tên dataset mới trong data/versions/")
    parser.add_argument("--source", required=True, help="Nguồn pilot, không chứa thông tin định danh")
    parser.add_argument("--notes", default="")
    args = parser.parse_args()

    dataset, counts = build_real_dataset(pd.read_csv(args.snapshots), pd.read_csv(args.outcomes))
    notes = (
        "Dữ liệu thật; chỉ gồm snapshot và đủ 7 ngày outcome liên tục được quan sát, "
        "training_consent còn hiệu lực ở snapshot và mọi ngày outcome. "
        "Không suy diễn ngày thiếu/không quan sát thành ngày không tập. "
        + args.notes
    ).strip()
    manifest = save_dataset(dataset, args.name, source=args.source, notes=notes, params=counts)
    print(f"Đã lưu {manifest['name']}: {manifest['rows']} dòng, {manifest['users']} người")
    print("Số liệu chuẩn bị:", counts)
    print("Dataset này chưa được huấn luyện/kích hoạt; hãy đánh giá trên tập test thực tế riêng.")


if __name__ == "__main__":
    main()
