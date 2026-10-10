"""Cac model ung vien va luoi sieu tham so (nho, de tranh qua khop va chay nhanh)."""
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from xgboost import XGBClassifier

# Thu tu uu tien khi cac model gan bang nhau: de giai thich nhat dung truoc
INTERPRETABILITY_ORDER = ["logreg", "random_forest", "xgboost"]


def candidates(seed: int, scale_pos_weight: float, fast: bool = False) -> dict:
    logreg = Pipeline([
        ("scale", StandardScaler()),
        ("clf", LogisticRegression(max_iter=2000, class_weight="balanced", random_state=seed)),
    ])
    rf = RandomForestClassifier(n_estimators=60 if fast else 300, class_weight="balanced_subsample",
                                n_jobs=1, random_state=seed)
    xgb = XGBClassifier(subsample=0.8, colsample_bytree=0.8, eval_metric="logloss",
                        scale_pos_weight=scale_pos_weight, n_jobs=1, random_state=seed, verbosity=0)
    if fast:
        grids = {"logreg": {"clf__C": [0.3, 1]}, "random_forest": {"max_depth": [4]},
                 "xgboost": {"max_depth": [2], "learning_rate": [0.1], "n_estimators": [60]}}
    else:
        grids = {
            "logreg": {"clf__C": [0.03, 0.1, 0.3, 1, 3]},
            "random_forest": {"max_depth": [4, 6, 8], "min_samples_leaf": [10, 30]},
            "xgboost": {"max_depth": [2, 3, 4], "learning_rate": [0.05, 0.1], "n_estimators": [150, 300]},
        }
    return {"logreg": (logreg, grids["logreg"]), "random_forest": (rf, grids["random_forest"]),
            "xgboost": (xgb, grids["xgboost"])}
