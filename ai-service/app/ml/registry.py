"""So dang ky phien ban model. Model moi KHONG tu dong duoc kich hoat; phai qua activate() va dat tieu chi toi thieu."""
import argparse
import json

from .config import ACTIVATION_MIN_RECALL, ACTIVATION_MIN_ROC_AUC, MODELS_DIR


def _path():
    return MODELS_DIR / "registry.json"


def _load() -> list:
    p = _path()
    return json.loads(p.read_text(encoding="utf-8")) if p.exists() else []


def _save(entries: list):
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    _path().write_text(json.dumps(entries, ensure_ascii=False, indent=2), encoding="utf-8")


def list_models() -> list:
    return _load()


def next_version() -> str:
    return f"v{len(_load()) + 1}.0"


def register(entry: dict):
    entries = _load()
    if any(e["version"] == entry["version"] for e in entries):
        raise ValueError(f"Phien ban {entry['version']} da ton tai")
    entries.append({**entry, "status": "TRAINED", "is_active": False})
    _save(entries)


def get(version: str) -> dict:
    for e in _load():
        if e["version"] == version:
            return e
    raise KeyError(f"Khong co model {version}")


def active_version() -> str | None:
    return next((e["version"] for e in _load() if e["is_active"]), None)


def latest_version() -> str | None:
    entries = _load()
    return entries[-1]["version"] if entries else None


def activate(version: str, allow_demo: bool = False) -> dict:
    entries = _load()
    target = next((e for e in entries if e["version"] == version), None)
    if target is None:
        raise KeyError(f"Khong co model {version}")
    m = target["metrics"]
    problems = []
    if m["roc_auc"] < ACTIVATION_MIN_ROC_AUC:
        problems.append(f"ROC-AUC {m['roc_auc']:.3f} < {ACTIVATION_MIN_ROC_AUC}")
    if m["recall"] < ACTIVATION_MIN_RECALL:
        problems.append(f"Recall {m['recall']:.3f} < {ACTIVATION_MIN_RECALL}")
    if target["is_demo"] and not allow_demo:
        problems.append("Model huan luyen tren du lieu DEMO; dung --allow-demo neu chi de phat trien/demo")
    if problems:
        raise ValueError("Khong kich hoat duoc: " + "; ".join(problems))
    for e in entries:
        e["is_active"] = e["version"] == version
        e["status"] = "ACTIVE" if e["is_active"] else ("ARCHIVED" if e["status"] == "ACTIVE" else e["status"])
    _save(entries)
    return target


def main():
    ap = argparse.ArgumentParser(description="Quan ly phien ban model")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list")
    a = sub.add_parser("activate")
    a.add_argument("version")
    a.add_argument("--allow-demo", action="store_true")
    args = ap.parse_args()
    if args.cmd == "list":
        for e in _load():
            m = e["metrics"]
            print(f"{e['version']:6} {e['algorithm']:14} {e['status']:9} AUC={m['roc_auc']:.3f} "
                  f"Recall={m['recall']:.3f} dataset={e['dataset']} demo={e['is_demo']}")
    else:
        activate(args.version, args.allow_demo)
        print(f"Da kich hoat {args.version}")


if __name__ == "__main__":
    main()
