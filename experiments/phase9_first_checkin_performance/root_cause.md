# Root cause — slow first check-in flow

**Status: direct database lock-wait failure is confirmed, and a targeted transaction fix is implemented. Runtime verification against MySQL is still pending.**

## Findings

### Confirmed from code

1. The check-in POST only validates, saves the check-in and returns. It does not invoke AI or recommendation.
2. After POST success, CheckIn navigates to Dashboard. Dashboard awaits check-in list, profile, workout history and recommendation concurrently; a single failed/slow request blocks its main content or displays the generic error.
3. A first recommendation with no snapshot performs state history queries, risk assessment/possibly AI, rule selection, exercise retrieval/scoring and snapshot persistence.
4. Later recommendation GETs with a snapshot return early and bypass AI, rule/exercise loading and scoring. This makes first-use vs reload work materially different and is consistent with the reported faster refresh.
5. The recommendation transaction locks the check-in row pessimistically and keeps that transaction open across the AI request and the remainder of generation.
6. Frontend is wrapped in React StrictMode. In Vite development, mount effects may run twice. Dashboard and Today each call recommendation in a mount effect; duplicate GETs can therefore be issued and contend on the check-in lock. The Axios client contains no automatic retry.
7. AI model/explainer are lazily cached. `/health` can load the model, while first `/predict` can still initialize the SHAP explainer. The active v2.0 artifact is logistic and its background CSV exists in the checked-in repository.
8. AI client defaults to 3000 ms connect and read timeouts and catches `RestClientException` to use history fallback. AI timeout alone is not expected to produce a 500 through that catch path.

### Not confirmed by runtime evidence

- Whether the specific check-in POST returned 201 before the Dashboard error.
- Which transaction/session held the conflicting InnoDB lock.
- Whether the first overlapping request was the direct blocker for the second request's insert, or whether both were blocked by an additional transaction.
- Whether the same underlying deadlock explains the earlier unrelated 500/30-second timeout from the previous test.
- Whether React StrictMode alone produced the two concurrent calls; two same-user/same-endpoint requests are present, but the exact client trigger is not captured.
- Whether pool exhaustion or exercise-tag N+1 queries exist; neither is indicated by the supplied exception/timing.
- Why Postman returned 200 while browser calls returned 500; auth identity, backend instance, request time, data and prior snapshot state have not been compared.

## Most evidence-aligned explanation

Runtime logs confirm this sequence for the supplied browser request:

- Two `GET /api/recommendations/today` requests ran concurrently for the same pseudonymous user; one ended HTTP 500 after 51.256 seconds.
- The other request's user-state work took 14 ms and the AI inference call took 22 ms and returned `success`.
- Its `risk_predictions` insert then blocked for 50.410 seconds and failed with MySQL error 1205 / SQLState 40001 (`Lock wait timeout exceeded`). The request returned HTTP 500 after 81.738 seconds.

The strongest code-level cause is a transaction/foreign-key lock interaction: `RecommendationService.today()` holds a `PESSIMISTIC_WRITE` lock on the check-in row in its outer transaction; `RiskPredictionStore.save()` suspends that transaction with `REQUIRES_NEW` and inserts a `risk_predictions` row referencing the locked check-in. The outer transaction remains suspended while retaining its lock, so the inner insert's FK check can wait on the parent-row lock held by its own suspended caller. The exact InnoDB lock holder was not included in the pasted output, so this mechanism is strongly supported by timing, failed SQL and code, but should be verified with a lock wait-for report before describing the holder as proven.

Concurrent duplicate recommendation calls can add a separate wait on the check-in lock and help explain the 81.7-second total; the log excerpt proves overlap but does not contain the stage timings for the 51-second request. The direct cause of the observed 500 is the risk-prediction insert lock timeout, not a slow AI inference.

## Fix implemented

The recommendation path now saves a newly computed risk prediction in the already-open `RecommendationService.today()` transaction instead of suspending it with `REQUIRES_NEW`. It retains the existing pessimistic check-in lock, so concurrent recommendation requests remain serialized; the FK child insert now runs in the same transaction that owns the parent lock. Other `RiskService.assess()` callers continue using the existing independent transaction behavior, including the read-only risk endpoint.

This makes recommendation risk-prediction persistence atomic with its recommendation snapshot: if generation fails later, both writes roll back together. That is an intentional consistency change in the affected cache-miss path; response DTO, risk calculation, model, scoring, rule and snapshot format remain unchanged.

Unit tests assert the recommendation path uses the joining save method, while regular risk assessment still uses the independent save method. Backend unit suite passes. No live endpoint was called, no data changed, and the original lock timeout has not yet been verified absent against MySQL.

## API/stage likely involved

- User-visible wait: post-submit Dashboard waits for `GET /api/recommendations/today` in its `Promise.all`.
- Confirmed failing stage: `RiskPredictionStore.save()` inserting `risk_predictions`, called from `RiskService.assess()`.
- Fixed transaction interaction: `RecommendationService.today()` / `DailyCheckinRepository.findByUserIdAndCheckinDateForUpdate()` now saves risk prediction through `RiskPredictionStore.saveInCurrentTransaction()`.
- Possible concurrent amplification: overlapping GET calls from Dashboard/Today and development StrictMode; exact initiation source not captured.

## Regression coverage and remaining verification

1. Frontend integration test: successful check-in POST navigates; dashboard handles a slow/failed recommendation distinctly; verify no POST retry and account for StrictMode.
2. Recommendation service integration test: first cache-miss creates one prediction/snapshot; repeat GET returns the same snapshot and does not reinvoke AI/scorer.
3. Concurrent recommendation test for one check-in: bounded response, no duplicate snapshot, no long blocking/deadlock. This still needs a MySQL-backed integration test.
4. AI client tests: cold/slow AI response, 3-second timeout fallback, malformed/5xx responses and valid result mapping.
5. SQL-count/performance test on representative exercise catalog to detect collection-fetch N+1 and record bounded query counts.
6. E2E observability assertion: capture POST status and timings for dashboard calls with correlation ID; do not use synthetic data to claim production performance.

## Safe runtime verification

Do not repeat the check-in POST or delete snapshots. After rebuilding/restarting the backend with the fix, the user may open the already-existing recommendation once in the disposable/test environment. The expected cache-hit path should be fast if a snapshot exists; if there is no snapshot and a new one is generated, compare the new `risk.prediction_persist` timing and ensure no MySQL lock timeout/HTTP 500 occurs. This GET can write risk/snapshot rows, so no request was run automatically.

For a true cache-miss reproduction or concurrency test, use a designated disposable schema and an approved existing test check-in. Capture a MySQL lock wait-for report as well as correlated request timing; do not use POST replay to create a new check-in solely for performance measurement.
