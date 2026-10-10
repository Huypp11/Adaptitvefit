"""Cau hinh trung tam cho phan Machine Learning cua AdaptiveFit AI."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]          # thu muc ai-service/
DATA_DIR = ROOT / "data"
RAW_DIR = DATA_DIR / "raw"
VERSIONS_DIR = DATA_DIR / "versions"
MODELS_DIR = ROOT / "models"
SURVEY_PROFILE_PATH = DATA_DIR / "survey_profile.json"

LABEL_COL = "dropout_7d"
GROUP_COL = "user_id"
DAY_COL = "day_index"
HISTORY_DAYS = 7      # cac feature lich su tinh tren 7 ngay gan nhat (gom ca hom nay)
HORIZON_DAYS = 7      # nhan: khong tap ngay nao trong 7 ngay SAU ngay check-in

LABEL_DEFINITION = (
    "dropout_7d = 1 neu nguoi dung KHONG tap ngay nao trong 7 ngay ke tiep "
    "(ngay t+1 den t+7) sau ngay check-in t; feature chi dung du lieu den het ngay t."
)

# Nguong muc nguy co (tren xac suat da hieu chinh)
RISK_LOW_MAX = 0.35
RISK_HIGH_MIN = 0.65

# Tieu chi toi thieu truoc khi cho phep kich hoat mot model (xem registry.activate)
ACTIVATION_MIN_ROC_AUC = 0.65
ACTIVATION_MIN_RECALL = 0.60

SEED = 42
