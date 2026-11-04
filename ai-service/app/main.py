"""Dich vu AI (FastAPI) cho AdaptiveFit. Chi Spring Boot duoc goi (header X-Internal-Key).


"""
import os
import secrets
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import ConfigDict, Field, create_model

from .env import load_dotenv
from . import performance
from .ml import explain, predict, registry
from .ml.schema import FEATURES

load_dotenv()

# Request sinh tu schema de luon dong bo voi model: dung 10 feature, cam truong la, kiem tra mien gia tri
PredictRequest = create_model(
    "PredictRequest",
    __config__=ConfigDict(extra="forbid"),
    **{f.name: (float, Field(ge=f.min, le=f.max, description=f.description)) for f in FEATURES},
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not os.environ.get("AI_INTERNAL_KEY"):
        raise RuntimeError("Hay dat AI_INTERNAL_KEY (trong file .env o thu muc goc hoac bien moi truong)")
    yield


app = FastAPI(title="AdaptiveFit AI service", version="0.1.0", lifespan=lifespan)
app.middleware("http")(performance.middleware)


def require_key(x_internal_key: str | None = Header(default=None)) -> None:
    expected = os.environ.get("AI_INTERNAL_KEY", "")
    if not x_internal_key or not secrets.compare_digest(x_internal_key, expected):
        raise HTTPException(status_code=401, detail="Khong hop le")


def _active_version() -> str:
    version = registry.active_version()
    if version is None:
        raise HTTPException(status_code=503, detail="Chua co model nao duoc kich hoat (python -m app.ml.registry activate <ver>)")
    return version


@app.get("/health")
def health():
    version = registry.active_version()
    is_demo = None
    if version:
        is_demo = bool(predict.load_model(version).meta.get("is_demo", False))
    return {"status": "ok", "active_model": version, "is_demo_model": is_demo}


@app.post("/predict", dependencies=[Depends(require_key)])
def predict_endpoint(req: PredictRequest):  # type: ignore[valid-type]
    version = performance.measure("active_model_lookup", _active_version)
    performance.event("model.selected", model_version=version)
    try:
        features = req.model_dump()
        return performance.measure("explain_risk", lambda: explain.explain_risk(features, version))
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e


@app.get("/models", dependencies=[Depends(require_key)])
def models():
    return registry.list_models()


@app.post("/reload", dependencies=[Depends(require_key)])
def reload_models():
    """Goi sau khi kich hoat model moi de xoa bo nho dem."""
    predict.load_model.cache_clear()
    explain.get_explainer.cache_clear()
    return {"active_model": registry.active_version()}
