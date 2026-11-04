# AI service timing — first inference and cache behavior

## Available runtime evidence

- An earlier E2E check recorded AI `GET /health` as HTTP 200 with active model `v2.0` and `is_demo_model=true`.
- No timestamped `/predict` request/response, elapsed time, AI-service access log, profiler trace, or exception from the browser failure was available.
- This audit did not call `/predict` or replay recommendation to benchmark inference.

The earlier `/health` result proves that a health request succeeded at that earlier time. It does not establish inference latency or prove the current AI process/configuration is the same process used by the latest request.

## Lazy work in the service

- FastAPI lifespan checks that `AI_INTERNAL_KEY` exists; it does not preload model or explainer.
- `/health` calls `predict.load_model(version)` if there is an active model, warming the process-local LRU cache for the model.
- `/predict` calls `explain.explain_risk()`. `get_explainer(version)` is separately `lru_cache`d and is lazy: the first prediction constructs `RiskExplainer`, loads the background dataset and initializes the SHAP explainer. Later predictions in the same process reuse it.
- The checked-in v2.0 registry entry is active, algorithm `logreg`; v2.0 `background.csv` exists. The checked-in model card marks the model as DEMO. These repository artifacts do not guarantee runtime files or active version in the live process.
- The current logistic path uses `shap.LinearExplainer`; exact initialization and prediction duration are unknown without timing.

## Call count and timeouts

- A first uncached recommendation can call risk inference once. `RiskService.assess()` returns an already stored prediction if one exists; on a miss, it calls `AiServiceClient.predict()` once, then stores the model result if returned.
- Recommendation GET is called separately by Dashboard and Today. In Vite development, StrictMode can replay each route's mount effect, potentially increasing calls. There is no client-side retry interceptor.
- Backend connect timeout and read timeout are each configured to 3000 ms by default. `AiServiceClient` catches `RestClientException`, logs a warning, and returns empty so `RiskService` uses history-based fallback. Thus an AI timeout alone is intended to degrade to fallback rather than directly return recommendation HTTP 500. The live process may override config; not verified.
- A timeout may still consume time before fallback, but no actual `/predict` duration is known. A backend 500 means an exception outside the caught AI network-error path, or another failure in the recommendation flow, remains possible.

## Conclusion

Cold model/explainer initialization is a supported first-request cost mechanism; it is **not measured or confirmed as the cause**. The first call can be slower than later calls due to lazy cache initialization, but later recommendation requests also short-circuit on the stored snapshot and therefore do not invoke AI at all. Those two warm paths must not be conflated.

To distinguish them, collect backend timings around AI HTTP start/end/status and AI-service timings around model/explainer cache miss and SHAP calculation on one already-authorized, existing check-in, only with approval and without a new POST.
