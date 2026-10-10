"""Luu va nap cac phien ban dataset kem manifest (hash, so dong, DEMO hay that, dinh nghia nhan)."""
import hashlib
import json
from datetime import datetime, timezone

import pandas as pd

from .config import GROUP_COL, LABEL_COL, LABEL_DEFINITION, VERSIONS_DIR
from .schema import FEATURE_NAMES
from .validation import validate_dataset


def _sha256(path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def save_dataset(df: pd.DataFrame, name: str, source: str, notes: str = "", params: dict | None = None) -> dict:
    report = validate_dataset(df)
    report.raise_if_failed()
    folder = VERSIONS_DIR / name
    if folder.exists():
        raise FileExistsError(f"Dataset '{name}' da ton tai. Hay dung ten phien ban moi.")
    folder.mkdir(parents=True)
    csv_path = folder / "dataset.csv"
    df.to_csv(csv_path, index=False)
    manifest = {
        "name": name, "source": source, "is_demo": bool(df["is_demo"].all()),
        "created_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "rows": report.stats["rows"], "users": report.stats["users"],
        "positive_rate": report.stats["positive_rate"],
        "features": FEATURE_NAMES, "label": LABEL_COL, "group": GROUP_COL,
        "label_definition": LABEL_DEFINITION, "sha256": _sha256(csv_path),
        "warnings": report.warnings, "notes": notes, "params": params or {},
    }
    (folder / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return manifest


def load_dataset(name: str) -> tuple[pd.DataFrame, dict]:
    folder = VERSIONS_DIR / name
    manifest_path = folder / "manifest.json"
    if not manifest_path.exists():
        raise FileNotFoundError(f"Khong tim thay dataset '{name}' trong {VERSIONS_DIR}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if _sha256(folder / "dataset.csv") != manifest["sha256"]:
        raise ValueError(f"Dataset '{name}' da bi thay doi so voi luc luu (sai hash)")
    return pd.read_csv(folder / "dataset.csv"), manifest


def list_datasets() -> list[dict]:
    out = []
    for p in sorted(VERSIONS_DIR.glob("*/manifest.json")):
        m = json.loads(p.read_text(encoding="utf-8"))
        out.append({k: m[k] for k in ("name", "rows", "users", "positive_rate", "is_demo", "created_at")})
    return out
