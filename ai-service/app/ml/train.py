"""Huan luyen, so sanh va luu mot phien ban model.

Quy trinh: validate -> chia theo nguoi dung -> GridSearch (GroupKFold) tren train -> hieu chinh xac suat va chon nguong
tren validation -> chon model theo validation -> bao cao cuoi cung tren test (chua tung dung de chon model).
"""
import argparse
import json
from datetime import datetime, timezone

import joblib
import numpy as np
import pandas as pd
from sklearn.model_selection import GridSearchCV, GroupKFold

from . import registry
from .calibration import PlattCalibrator, RiskModel
from .config import GROUP_COL, LABEL_COL, MODELS_DIR, SEED
from .dataset_registry import load_dataset
from .evaluate import (binary_metrics, calibration_table, choose_threshold, level_distribution,
                       permutation_importance, rule_baseline)
from .models import INTERPRETABILITY_ORDER, candidates
from .schema import FEATURE_NAMES, FEATURES
from .splits import group_split
from .validation import validate_dataset

AUC_TIE_MARGIN = 0.01


def _default(o):
    if isinstance(o, (np.floating, np.integer)):
        return o.item()
    if isinstance(o, np.ndarray):
        return o.tolist()
    raise TypeError(f"Khong serialize duoc {type(o)}")


def select_model(val_auc: dict) -> tuple[str, str]:
    """Chon theo ROC-AUC tren validation; neu nhieu model chenh nhau trong 0.01 thi chon model de giai thich nhat."""
    best = max(val_auc.values())
    close = [n for n in INTERPRETABILITY_ORDER if n in val_auc and best - val_auc[n] <= AUC_TIE_MARGIN]
    chosen = close[0]
    reason = (f"ROC-AUC validation cao nhat la {best:.3f}; cac model chenh khong qua {AUC_TIE_MARGIN} "
              f"({', '.join(close)}) nen chon model de giai thich nhat: {chosen}.")
    return chosen, reason


def run_training(dataset: str, seed: int = SEED, fast: bool = False, notes: str = "", n_jobs: int = -1) -> dict:
    df, manifest = load_dataset(dataset)
    report = validate_dataset(df)
    report.raise_if_failed()

    train, val, test = group_split(df, seed)
    X_tr, y_tr, g_tr = train[FEATURE_NAMES], train[LABEL_COL], train[GROUP_COL]
    X_va, y_va = val[FEATURE_NAMES], val[LABEL_COL]
    X_te, y_te = test[FEATURE_NAMES], test[LABEL_COL]
    spw = float((y_tr == 0).sum() / max((y_tr == 1).sum(), 1))

    results: dict = {}
    fitted: dict = {}
    for name, (est, grid) in candidates(seed, spw, fast).items():
        gs = GridSearchCV(est, grid, scoring="roc_auc", cv=GroupKFold(n_splits=3 if fast else 5),
                          n_jobs=n_jobs, refit=True)
        gs.fit(X_tr, y_tr, groups=g_tr)
        best = gs.best_estimator_
        raw_val = best.predict_proba(X_va)[:, 1]
        cal = PlattCalibrator().fit(raw_val, y_va)
        p_val = cal.transform(raw_val)
        thr = choose_threshold(y_va, p_val)
        model = RiskModel(best, cal, FEATURE_NAMES, thr)
        fitted[name] = model
        results[name] = {
            "best_params": gs.best_params_, "cv_roc_auc_train": float(gs.best_score_),
            "validation": binary_metrics(y_va, p_val, thr),
            "test": binary_metrics(y_te, model.predict_proba(X_te), thr),
        }

    chosen, reason = select_model({n: r["validation"]["roc_auc"] for n, r in results.items()})
    model = fitted[chosen]
    p_test = model.predict_proba(X_te)
    version = registry.next_version()

    warnings = list(report.warnings)  # da gom canh bao DU LIEU DEMO neu co

    selected_info = {
        "algorithm": chosen, "best_params": results[chosen]["best_params"], "threshold": model.threshold,
        "validation": results[chosen]["validation"], "test": results[chosen]["test"],
        "risk_levels_test": level_distribution(y_te, p_test), "calibration_test": calibration_table(y_te, p_test),
        "permutation_importance_test": permutation_importance(model, X_te, y_te),
    }
    if chosen == "logreg":
        clf = model.estimator.named_steps["clf"]
        selected_info["standardized_coefficients"] = dict(zip(FEATURE_NAMES, map(float, clf.coef_[0])))

    metrics = {
        "version": version, "created_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "seed": seed, "fast_mode": fast, "is_demo": manifest["is_demo"],
        "dataset": {k: manifest[k] for k in ("name", "source", "rows", "users", "positive_rate", "sha256", "label_definition")},
        "split": {k: {"users": int(d[GROUP_COL].nunique()), "rows": len(d), "positive_rate": float(d[LABEL_COL].mean())}
                  for k, d in (("train", train), ("validation", val), ("test", test))},
        "selection": {"rule": "ROC-AUC tren validation; chenh <= 0.01 thi uu tien model de giai thich",
                      "chosen": chosen, "reason": reason},
        "selected": selected_info, "comparison": results,
        "baselines": {"rule_missed_days_ge_3_test": rule_baseline(test, y_te),
                      "no_skill_roc_auc": 0.5, "base_rate_test": float(y_te.mean())},
        "warnings": warnings, "notes": notes,
    }

    out = MODELS_DIR / version
    out.mkdir(parents=True, exist_ok=False)
    model.version = version
    model.meta = {"is_demo": manifest["is_demo"], "algorithm": chosen, "dataset": dataset}
    joblib.dump(model, out / "model.joblib")
    (out / "metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2, default=_default), encoding="utf-8")
    (out / "feature_schema.json").write_text(json.dumps(
        [{"name": f.name, "min": f.min, "max": f.max, "description": f.description} for f in FEATURES],
        ensure_ascii=False, indent=2), encoding="utf-8")
    pd.DataFrame([{"model": n, "cv_auc_train": r["cv_roc_auc_train"],
                   **{f"val_{k}": r["validation"][k] for k in ("roc_auc", "pr_auc", "recall", "precision", "f1", "brier")},
                   **{f"test_{k}": r["test"][k] for k in ("roc_auc", "pr_auc", "recall", "precision", "f1", "brier")}}
                  for n, r in results.items()]).to_csv(out / "comparison.csv", index=False)
    (out / "MODEL_CARD.md").write_text(_model_card(metrics), encoding="utf-8")

    t = results[chosen]["test"]
    registry.register({
        "version": version, "algorithm": chosen, "dataset": dataset, "is_demo": manifest["is_demo"],
        "created_at": metrics["created_at"], "path": str(out),
        "metrics": {"roc_auc": t["roc_auc"], "recall": t["recall"], "precision": t["precision"],
                    "f1": t["f1"], "brier": t["brier"], "threshold": model.threshold},
    })
    return metrics


def _model_card(m: dict) -> str:
    s, t = m["selected"], m["selected"]["test"]
    cm = t["confusion_matrix"]
    lines = [f"# Model {m['version']} ({s['algorithm']})", ""]
    lines += [f"> **CANH BAO:** {w}" for w in m["warnings"]] + [""]
    lines += [
        f"- Dataset: `{m['dataset']['name']}` ({m['dataset']['rows']} dong, {m['dataset']['users']} nguoi dung)",
        f"- Nhan: {m['dataset']['label_definition']}",
        f"- Chon model: {m['selection']['reason']}",
        f"- Nguong phan loai (chon tren validation, F2): {s['threshold']}",
        "", "## Ket qua tren tap test (chua dung de chon model)",
        f"- ROC-AUC {t['roc_auc']:.3f} | PR-AUC {t['pr_auc']:.3f} | Brier {t['brier']:.3f}",
        f"- Precision {t['precision']:.3f} | Recall (nhom bo tap) {t['recall']:.3f} | F1 {t['f1']:.3f} | Accuracy {t['accuracy']:.3f}",
        f"- Confusion matrix: TN={cm['tn']} FP={cm['fp']} FN={cm['fn']} TP={cm['tp']}",
        f"- Baseline quy tac ({m['baselines']['rule_missed_days_ge_3_test']['rule']}): "
        f"Recall {m['baselines']['rule_missed_days_ge_3_test']['recall']:.3f}, "
        f"Precision {m['baselines']['rule_missed_days_ge_3_test']['precision']:.3f}, "
        f"ROC-AUC {m['baselines']['rule_missed_days_ge_3_test']['roc_auc']:.3f}",
        "", "## Gioi han",
        "- Do quan trong feature mo ta hanh vi cua model, khong phai quan he nhan qua.",
        "- He thong ho tro duy tri thoi quen tap luyen, khong phai cong cu chan doan hay dieu tri.",
    ]
    return "\n".join(lines) + "\n"


def main():
    ap = argparse.ArgumentParser(description="Huan luyen va so sanh cac model du doan dropout")
    ap.add_argument("--dataset", required=True, help="Ten phien ban dataset trong data/versions")
    ap.add_argument("--seed", type=int, default=SEED)
    ap.add_argument("--fast", action="store_true", help="Luoi sieu tham so nho, chi de thu nhanh")
    ap.add_argument("--notes", default="")
    a = ap.parse_args()
    m = run_training(a.dataset, a.seed, a.fast, a.notes)
    print(f"\n=== Model {m['version']} ===")
    for w in m["warnings"]:
        print("CANH BAO:", w)
    cols = ("roc_auc", "pr_auc", "recall", "precision", "f1", "brier")
    print(f"{'model':14}" + "".join(f"{c:>10}" for c in cols) + "   (tap test)")
    for n, r in m["comparison"].items():
        print(f"{n:14}" + "".join(f"{r['test'][c]:>10.3f}" for c in cols))
    b = m["baselines"]["rule_missed_days_ge_3_test"]
    print(f"{'rule(>=3 ngay)':14}{b['roc_auc']:>10.3f}{'':>10}{b['recall']:>10.3f}{b['precision']:>10.3f}{b['f1']:>10.3f}")
    print("Chon:", m["selection"]["reason"])
    print("Top feature (permutation):", ", ".join(f"{r['feature']}({r['auc_drop_mean']:.3f})"
                                                  for r in m["selected"]["permutation_importance_test"][:4]))
    print(f"Artifacts: {MODELS_DIR / m['version']}")


if __name__ == "__main__":
    main()
