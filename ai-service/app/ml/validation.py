"""Kiem tra du lieu truoc khi huan luyen: schema, mien gia tri, trung lap, ro ri, DEMO/that."""
from dataclasses import dataclass, field

import pandas as pd

from .config import DAY_COL, GROUP_COL, LABEL_COL
from .schema import FEATURES, FEATURE_NAMES

MIN_ROWS = 100
MIN_USERS = 20
MIN_PER_CLASS = 20
LEAK_HINTS = ("next", "future", "after", "outcome")


@dataclass
class ValidationReport:
    errors: list = field(default_factory=list)
    warnings: list = field(default_factory=list)
    stats: dict = field(default_factory=dict)

    @property
    def ok(self) -> bool:
        return not self.errors

    def raise_if_failed(self):
        if self.errors:
            raise ValueError("Du lieu khong hop le:\n- " + "\n- ".join(self.errors))


def validate_dataset(df: pd.DataFrame) -> ValidationReport:
    rep = ValidationReport()
    required = FEATURE_NAMES + [LABEL_COL, GROUP_COL, DAY_COL, "is_demo"]
    missing = [c for c in required if c not in df.columns]
    if missing:
        rep.errors.append(f"Thieu cot: {', '.join(missing)}")
        return rep

    leaky = [c for c in df.columns if c not in required and any(h in c.lower() for h in LEAK_HINTS)]
    if leaky:
        rep.errors.append(f"Cot nghi ro ri thong tin tuong lai: {', '.join(leaky)}")

    cols = FEATURE_NAMES + [LABEL_COL]
    nulls = df[cols].isna().sum()
    if nulls.any():
        rep.errors.append("Co gia tri thieu: " + ", ".join(f"{k}={v}" for k, v in nulls[nulls > 0].items()))
        return rep

    for f in FEATURES:
        bad = int((~df[f.name].between(f.min, f.max)).sum())
        if bad:
            rep.errors.append(f"{f.name}: {bad} dong ngoai khoang [{f.min}, {f.max}]")
    if not set(df[LABEL_COL].unique()) <= {0, 1}:
        rep.errors.append(f"{LABEL_COL} chi duoc la 0 hoac 1")
    if df.duplicated([GROUP_COL, DAY_COL]).any():
        rep.errors.append(f"Co dong trung ({GROUP_COL}, {DAY_COL})")

    n_users = df[GROUP_COL].nunique()
    pos = int(df[LABEL_COL].sum())
    neg = len(df) - pos
    rep.stats = {"rows": len(df), "users": int(n_users), "positives": pos, "positive_rate": round(pos / len(df), 4)}
    if len(df) < MIN_ROWS:
        rep.errors.append(f"Qua it dong ({len(df)} < {MIN_ROWS})")
    if n_users < MIN_USERS:
        rep.errors.append(f"Qua it nguoi dung ({n_users} < {MIN_USERS}); can chia train/val/test theo nguoi dung")
    if min(pos, neg) < MIN_PER_CLASS:
        rep.errors.append(f"Mot lop co qua it mau (duong={pos}, am={neg}; can >= {MIN_PER_CLASS})")
    elif not 0.05 <= pos / len(df) <= 0.95:
        rep.warnings.append("Ty le lop rat lech; can than khi doc Accuracy")

    demo = df["is_demo"].astype(bool)
    if demo.any() and not demo.all():
        rep.errors.append("Khong tron DEMO va du lieu that trong cung mot phien ban dataset")
    elif demo.all():
        rep.warnings.append("DU LIEU DEMO: ket qua chi chung minh quy trinh chay dung, KHONG phai ket qua nghien cuu")
    return rep
