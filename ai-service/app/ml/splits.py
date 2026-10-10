"""Chia train/validation/test THEO NGUOI DUNG: moi nguoi chi nam o mot tap, tranh ro ri giua cac ngay lien tiep."""
import numpy as np
import pandas as pd

from .config import GROUP_COL, LABEL_COL, SEED


def group_split(df: pd.DataFrame, seed: int = SEED, val_size: float = 0.2, test_size: float = 0.2):
    users = np.array(sorted(df[GROUP_COL].unique()))
    np.random.default_rng(seed).shuffle(users)
    n_test, n_val = int(round(len(users) * test_size)), int(round(len(users) * val_size))
    groups = {"test": users[:n_test], "val": users[n_test:n_test + n_val], "train": users[n_test + n_val:]}

    parts = {k: df[df[GROUP_COL].isin(v)].reset_index(drop=True) for k, v in groups.items()}
    sets = [set(v) for v in groups.values()]
    assert not (sets[0] & sets[1] or sets[0] & sets[2] or sets[1] & sets[2]), "Nguoi dung bi trung giua cac tap"
    for k, p in parts.items():
        if p[LABEL_COL].nunique() < 2:
            raise ValueError(f"Tap '{k}' chi co mot lop; can them du lieu hoac doi seed")
    return parts["train"], parts["val"], parts["test"]
