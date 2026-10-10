"""Nap dataset tu file CSV (du lieu that hoac du lieu khac) thanh mot phien ban co manifest.

CSV can co cac cot: user_id, day_index, 10 feature (xem app/ml/schema.py) va dropout_7d.
Mac dinh coi la DU LIEU DEMO; chi them --real khi day la du lieu that da duoc kiem tra.
"""
import argparse

import pandas as pd

from .dataset_registry import save_dataset


def import_csv(csv_path, name: str, source: str, is_demo: bool = True, notes: str = "") -> dict:
    df = pd.read_csv(csv_path)
    df["is_demo"] = is_demo
    return save_dataset(df, name, source=source, notes=notes)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--csv", required=True)
    ap.add_argument("--name", required=True, help="Ten phien ban moi, vi du real_v1")
    ap.add_argument("--source", required=True, help="Nguon du lieu, vi du pilot_2026_11")
    ap.add_argument("--real", action="store_true", help="Danh dau day la du lieu that (is_demo=False)")
    ap.add_argument("--notes", default="")
    a = ap.parse_args()
    m = import_csv(a.csv, a.name, a.source, is_demo=not a.real, notes=a.notes)
    print(f"Da luu {m['name']}: {m['rows']} dong, {m['users']} nguoi dung, is_demo={m['is_demo']}")
    for w in m["warnings"]:
        print("CANH BAO:", w)


if __name__ == "__main__":
    main()
