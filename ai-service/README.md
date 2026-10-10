# AdaptiveFit AI - ai-service (Phase 5a: ML baseline offline)

Python: pipeline huan luyen, so sanh va luu model du doan **nguy co bo tap trong 7 ngay toi**. Chua ket noi voi web
(se noi qua FastAPI o Phase 7) va chua co SHAP (Phase 6).

> **DU LIEU DEMO:** model hien tai huan luyen tren du lieu mo phong. Quan he giua trang thai va viec bo tap la **gia dinh**
> cua nguoi xay dung, nen metric chi chung minh quy trinh chay dung, **khong phai ket qua nghien cuu**.
> Khao sat 53 nguoi chi dung de hieu chinh phan phoi nen (ngu, stress, nang luong, dong luc, thoi gian ranh, tan suat tap).

## Cai dat (Windows PowerShell, chay trong thu muc `ai-service`)
```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1        # neu bi chan: Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
pip install -r requirements.txt
```

## Chay quy trinh
```powershell
# 1. (tuy chon) Xu ly lai file khao sat. Dat file vao data/raw/ truoc. Da kem san data/survey_profile.json
python -m app.ml.survey --input "data/raw/Data_nguoi_dung.xlsx"

# 2. Sinh dataset DEMO (co gan co is_demo, kem manifest va hash)
python -m app.ml.demo_data --name demo_v1

# 3. Huan luyen va so sanh Logistic Regression, Random Forest, XGBoost (mat vai phut; them --fast de thu nhanh)
python -m app.ml.train --dataset demo_v1

# 4. Xem cac phien ban model, du doan thu, kich hoat
python -m app.ml.registry list
python -m app.ml.predict --version v1.0 --json "{\"sleep_hours\":5,\"stress_level\":4,\"energy_level\":2,\"mood\":2,\"motivation\":2,\"free_time_min\":15,\"missed_days\":4,\"workout_frequency_7d\":1,\"avg_duration_7d\":20,\"avg_difficulty_7d\":3}"
python -m app.ml.registry activate v1.0 --allow-demo   # model DEMO chi duoc kich hoat khi co --allow-demo

# 5. Kiem thu tu dong
python -m pytest
```
Ket qua moi lan train nam o `models/<version>/`: `model.joblib`, `metrics.json`, `comparison.csv`, `feature_schema.json`, `MODEL_CARD.md`.

## Thiet ke chinh
- **Nhan:** `dropout_7d = 1` neu khong tap ngay nao trong 7 ngay SAU ngay check-in. Feature chi dung du lieu den het ngay check-in.
- **Chong ro ri:** chia train/validation/test **theo nguoi dung**; co kiem thu tinh nhat quan giua nhan va lich su.
- **Chon model:** theo ROC-AUC tren validation; neu cac model chenh <= 0.01 thi uu tien model de giai thich nhat. Test chi dung de bao cao cuoi.
- **Xac suat da hieu chinh** (Platt) nen `risk_score` co the doc nhu xac suat; muc nguy co: LOW < 0.35 <= MEDIUM < 0.65 <= HIGH.
- **Nguong phan loai** chon tren validation theo F2 (uu tien Recall nhom bo tap), nen Precision thap hon la danh doi co chu dich.
- **Baseline:** luon so voi quy tac don gian `missed_days >= 3`; model phai vuot moc nay moi dang gia.
- **Kich hoat:** model moi khong tu dong active; `registry activate` kiem tra ROC-AUC >= 0.65 va Recall >= 0.60, va chan model DEMO neu khong co `--allow-demo`.
- **Do quan trong feature** (permutation) mo ta hanh vi cua model, **khong** phai quan he nhan qua.

## Thay du lieu DEMO bang du lieu that
Chuan bi CSV co cac cot `user_id, day_index`, 10 feature (xem `app/ml/schema.py`) va `dropout_7d`, roi:
```powershell
python -m app.ml.import_dataset --csv data/raw/real.csv --name real_v1 --source pilot_2026_11 --real
python -m app.ml.train --dataset real_v1
```
Khong tron DEMO va du lieu that trong cung mot phien ban (bo kiem tra se tu choi).

## Cau truc
```text
ai-service/
  app/ml/  config.py schema.py survey.py demo_data.py validation.py dataset_registry.py import_dataset.py
           splits.py models.py calibration.py evaluate.py train.py registry.py predict.py
  data/    survey_profile.json (chi thong ke tong hop), raw/ (khong commit), versions/ (dataset da sinh)
  models/  cac phien ban model
  tests/
```
