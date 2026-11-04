# Temporary performance timing instrumentation

## Files changed

### Spring Boot backend

- `backend/src/main/java/com/adaptivefit/observability/PerformanceTiming.java` — opt-in stage/request logger, per-request context, process-salted pseudonymous user reference, and concurrent same-user/same-endpoint signal.
- `backend/src/main/java/com/adaptivefit/observability/PerformanceTimingFilter.java` — creates a UUID correlation ID for targeted API calls, returns it as `X-Request-ID`, and logs request start/end and total elapsed time.
- `backend/src/main/resources/application.yml` — adds `app.performance.enabled`, disabled by default and controlled by `PERFORMANCE_TIMING_ENABLED`.
- `backend/src/main/java/com/adaptivefit/controller/CheckinController.java` and `service/CheckinService.java` — controller/service timing, current-user lookup, duplicate-check query and save.
- `backend/src/main/java/com/adaptivefit/controller/RecommendationController.java` and `service/RecommendationService.java` — controller/service timing, user/profile lookups, check-in and snapshot lock queries, snapshot cache hit/miss, rules, engine, exercise retrieval/mapping, scorer, serialization, persistence and response mapping.
- `backend/src/main/java/com/adaptivefit/controller/AiController.java` and `service/RecommendationService.java` — timings for `/api/ai/today` and its check-in lookup.
- `backend/src/main/java/com/adaptivefit/service/UserStateService.java` — total snapshot computation and each check-in/workout/feedback history query.
- `backend/src/main/java/com/adaptivefit/service/RiskService.java` — prediction lookup/cache, AI call, history/model source, model-version persistence and risk-prediction persistence.
- `backend/src/main/java/com/adaptivefit/client/AiServiceClient.java` — timed `/predict` round trip, success/empty/fallback/disabled outcome, and propagation of `X-Request-ID`. No feature values or internal key are logged.

### AI Service

- `ai-service/app/performance.py` — environment-gated request correlation middleware and stage timing helper.
- `ai-service/app/main.py` — active model lookup/model version and end-to-end explanation timing for `/predict`.
- `ai-service/app/ml/explain.py` — model load, background load, explainer initialization/cache lookup, and prediction+SHAP timing.

### Report

- `experiments/phase9_first_checkin_performance/timing_instrumentation.md` — this usage and limitations guide.

No algorithm, model, scoring, rule, recommendation response, transaction boundary, lock behavior, migration, or retry behavior was intentionally changed. No user or check-in was created and no endpoint was called for benchmarking.

## Enable / disable

Instrumentation is **off by default**.

Set `PERFORMANCE_TIMING_ENABLED=true` in the environment of **both** the Spring Boot and AI Service processes, then restart those processes. The Spring property maps this environment variable to `app.performance.enabled`; Python reads the same environment variable. Set it to `false` or remove it and restart both services to turn timing logs off.

The backend filter only instruments `/api/checkins...`, `/api/recommendations...`, and `/api/ai...`. AI middleware only instruments `/predict`. When enabled, backend responses for those routes include `X-Request-ID`.

Enable briefly for a controlled diagnosis. Logs expose endpoint, pseudonymous user reference, status, model version, durations, cache state and exception type only; they do not log check-in fields, email, password, JWT, cookies, internal AI key, or request/response payloads. Existing application error logs may still include stack traces; review/redact them before sharing.

## Correlation and reading log entries

1. In browser DevTools Network, record the affected API request's `X-Request-ID`, status and duration. Do not copy Authorization/Cookie headers.
2. Search backend logs for that exact `requestId`. `request.start` and `request.end` include endpoint, UTC/localized runtime timestamp, status and total `elapsedMs`.
3. If an AI call was made, `AiServiceClient` forwards the same ID as `X-Request-ID`. Search AI Service logs for that ID to compare total `/predict` time with its `active_model_lookup`, `model_load`, `explainer_background_load`, `explainer_initialize`, `explainer_cache_lookup`, and `prediction_and_shap` stages.
4. For first recommendation cache fill, look for `recommendation.snapshot_cache result=miss`; a later response with `result=hit` takes a shorter code path. `risk.prediction_cache` separately identifies persisted prediction reuse versus AI/fallback.
5. Match paired `stage.start`/`stage.end` entries by request ID and stage name. The end entry has `elapsedMs` and `status=ok|error`. Request IDs are unique per HTTP request; repeated calls have distinct IDs.
6. `request.concurrency duplicate=true` means another in-flight request was observed for the same pseudonymous user and exact method/path. This can reveal duplicate browser GETs without logging the actual user ID.

AI explainer cache hit/miss values are process-wide `functools.lru_cache` counters, not an exact per-request attribution under concurrent predictions. A `model_load`, `explainer_background_load`, or `explainer_initialize` stage appearing for a request shows that the corresponding cached factory executed on that request.

The user reference is a short SHA-256 pseudonym salted randomly per backend process; it is stable only until that process restarts. It is not suitable for joining data across restarts.

## Lock timing limitations

`recommendation.checkin_lock_query` and `recommendation.snapshot_lock_query` measure elapsed time for the complete database call, including query execution and any time spent acquiring the pessimistic lock. JPA does not expose a safe portable split between SQL execution and lock wait here. A slow measurement is therefore a lock/query signal, not proof of pure lock-wait time. To prove lock waits, correlate with MySQL InnoDB lock diagnostics for the same interval using an appropriately privileged account.

`controller.recommendation.today` measures the service proxy call, including completion of the service transaction/commit. The outer HTTP request timing also includes controller/serialization and response handling. Service stage sums may not equal request total due to nested stages, transaction work, framework overhead and overlapping client activity.

## Test status

No tests, builds, benchmark requests, POSTs, GETs, migrations, or database changes were performed, as requested. The instrumentation is intended to collect real timings on the next user-approved normal browser flow.

## Follow-up fix and validation

After the user supplied runtime logs confirming a 50-second lock timeout on `risk_predictions` insert, backend code was updated so the recommendation-specific risk prediction write joins the transaction already holding the pessimistic check-in lock. Other risk-assessment callers retain independent writes. See [root_cause.md](./root_cause.md) and [../phase9_recommendation_failure/root_cause_and_fix_plan.md](../phase9_recommendation_failure/root_cause_and_fix_plan.md) for details.

The focused `RiskServiceTest` and complete backend Maven test suite passed (26 tests). No runtime API call or DB operation was made; the lock fix remains to be verified after restarting the backend against the disposable schema.
