"""Opt-in request correlation and timing helpers for temporary performance diagnosis."""
import contextvars
import logging
import os
import time
from datetime import datetime, timezone
from typing import Callable, TypeVar
from uuid import uuid4

from fastapi import Request

T = TypeVar("T")
REQUEST_ID: contextvars.ContextVar[str | None] = contextvars.ContextVar("request_id", default=None)
LOGGER = logging.getLogger("adaptivefit.performance")


def enabled() -> bool:
    return os.environ.get("PERFORMANCE_TIMING_ENABLED", "").strip().lower() in {"1", "true", "yes", "on"}


def measure(stage: str, operation: Callable[[], T]) -> T:
    if not enabled():
        return operation()
    started = time.perf_counter()
    request_id = REQUEST_ID.get() or "unavailable"
    LOGGER.info(
        "perf event=stage.start stage=%s requestId=%s timestamp=%s",
        stage, request_id, datetime.now(timezone.utc).isoformat(),
    )
    try:
        result = operation()
    except Exception as error:
        LOGGER.info(
            "perf event=stage.end stage=%s requestId=%s timestamp=%s elapsedMs=%.3f status=error exception=%s",
            stage, request_id, datetime.now(timezone.utc).isoformat(),
            (time.perf_counter() - started) * 1000, type(error).__name__,
        )
        raise
    LOGGER.info(
        "perf event=stage.end stage=%s requestId=%s timestamp=%s elapsedMs=%.3f status=ok",
        stage, request_id, datetime.now(timezone.utc).isoformat(),
        (time.perf_counter() - started) * 1000,
    )
    return result


def event(name: str, **fields: str | int | float | bool) -> None:
    if not enabled():
        return
    details = " ".join(f"{key}={value}" for key, value in fields.items())
    LOGGER.info(
        "perf event=%s requestId=%s timestamp=%s %s",
        name, REQUEST_ID.get() or "unavailable", datetime.now(timezone.utc).isoformat(), details,
    )


async def middleware(request: Request, call_next):
    if not enabled() or request.url.path != "/predict":
        return await call_next(request)

    request_id = request.headers.get("X-Request-ID") or str(uuid4())
    context_token = REQUEST_ID.set(request_id)
    started = time.perf_counter()
    endpoint = f"{request.method} {request.url.path}"
    LOGGER.info(
        "perf event=request.start requestId=%s endpoint=%s timestamp=%s",
        request_id, endpoint, datetime.now(timezone.utc).isoformat(),
    )
    try:
        response = await call_next(request)
        response.headers["X-Request-ID"] = request_id
        LOGGER.info(
            "perf event=request.end requestId=%s endpoint=%s timestamp=%s elapsedMs=%.3f status=%s",
            request_id, endpoint, datetime.now(timezone.utc).isoformat(),
            (time.perf_counter() - started) * 1000, response.status_code,
        )
        return response
    except Exception as error:
        LOGGER.info(
            "perf event=request.end requestId=%s endpoint=%s timestamp=%s elapsedMs=%.3f status=error exception=%s",
            request_id, endpoint, datetime.now(timezone.utc).isoformat(),
            (time.perf_counter() - started) * 1000, type(error).__name__,
        )
        raise
    finally:
        REQUEST_ID.reset(context_token)
