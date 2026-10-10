"""Dinh nghia feature dau vao cua model va kiem tra mot dong du lieu."""
from dataclasses import dataclass


@dataclass(frozen=True)
class Feature:
    name: str
    min: float
    max: float
    description: str


# Khong dung age/gender. adherence_7d chua dua vao v1 vi can "so buoi tap ke hoach moi tuan",
# thong tin nay hien chua co trong ho so nguoi dung.
FEATURES = [
    Feature("sleep_hours", 0, 14, "So gio ngu dem qua (check-in)"),
    Feature("stress_level", 1, 5, "Muc cang thang 1-5 (check-in)"),
    Feature("energy_level", 1, 5, "Muc nang luong 1-5 (check-in)"),
    Feature("mood", 1, 5, "Tam trang 1-5 (check-in)"),
    Feature("motivation", 1, 5, "Dong luc 1-5 (check-in)"),
    Feature("free_time_min", 0, 480, "So phut ranh hom nay (check-in)"),
    Feature("missed_days", 0, 60, "So ngay lien tiep khong tap tinh den hom nay"),
    Feature("workout_frequency_7d", 0, 7, "So ngay co tap trong 7 ngay gan nhat (gom hom nay)"),
    Feature("avg_duration_7d", 0, 300, "Thoi luong tap trung binh (phut) cac ngay co tap trong 7 ngay; 0 neu khong tap"),
    Feature("avg_difficulty_7d", 0, 5, "Do kho cam nhan trung binh 1-5 trong 7 ngay; 0 neu khong tap"),
]

FEATURE_NAMES = [f.name for f in FEATURES]
_BY_NAME = {f.name: f for f in FEATURES}


def validate_features(row: dict) -> dict:
    """Kiem tra thieu/thua/ngoai mien gia tri; tra ve dict feature da ep kieu float."""
    missing = [n for n in FEATURE_NAMES if n not in row]
    if missing:
        raise ValueError(f"Thieu feature: {', '.join(missing)}")
    unknown = [k for k in row if k not in _BY_NAME]
    if unknown:
        raise ValueError(f"Feature khong hop le: {', '.join(unknown)}")
    clean = {}
    for f in FEATURES:
        try:
            v = float(row[f.name])
        except (TypeError, ValueError):
            raise ValueError(f"{f.name} phai la so") from None
        if not (f.min <= v <= f.max):
            raise ValueError(f"{f.name}={v} ngoai khoang [{f.min}, {f.max}]")
        clean[f.name] = v
    return clean
