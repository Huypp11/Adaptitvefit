# Backend timing — static path and available measurements

## Runtime evidence supplied by user

The attached Spring Boot output contains correlated timing events from the browser-triggered recommendation requests. No request was replayed as part of this audit.

For request `1670ed05-e7c7-437f-8573-764aa2cc10de`, endpoint `GET /api/recommendations/today`, user reference `8758e38f75edcf66`:

- `user_state.snapshot`: **14 ms**; constituent check-in/workout/feedback history queries completed in about 4–5 ms each in the excerpt.
- AI inference HTTP call: `ai.http_predict` **22 ms** (risk client stage 23 ms), result `success`.
- Model-version persistence: **5 ms**.
- `risk.prediction_persist`: **50,410 ms**, then failed with `PessimisticLockingFailureException`.
- `risk.assess`: **50,452 ms**; recommendation service: **81,723 ms**; controller: **81,726 ms**; HTTP request: **81,738 ms**, status 500.
- At `09:23:01.951+07:00`, Hibernate logged SQL error **1205**, SQLState **40001**, `Lock wait timeout exceeded`; the failed statement was `INSERT INTO risk_predictions ...`.

The same excerpt contains another concurrent request, `bbe8aa7b-1661-43d8-91d0-37b69841f533`, for the same endpoint and pseudonymous user, ending HTTP 500 after **51,256 ms** at `09:22:11.498+07:00`. This supports overlapping recommendation requests. The supplied excerpt does not contain its matching stage events/request start, so its exact failing operation cannot be attributed with certainty.

## POST `/api/checkins`

`CheckinController.create()` delegates to `CheckinService.create()` in one transaction. The service:

1. Resolves the authenticated user.
2. Checks whether a check-in already exists for today.
3. Maps request fields into a `DailyCheckin`.
4. Calls `repository.save(c)` and maps the entity to response.

It does **not** call `UserStateService`, AI, recommendation engine, scorer or snapshot persistence. The repository save is transaction-managed; commit/flush occurs as the request transaction completes. No timing data separates these small DB operations.

## First `GET /api/recommendations/today`

`RecommendationService.today()` is transactional and has a cache-fill path when no recommendation snapshot exists:

1. Resolve current user.
2. Read profile.
3. Read today's check-in with `PESSIMISTIC_WRITE`.
4. Read an existing recommendation snapshot with `PESSIMISTIC_WRITE`.
5. If snapshot is absent, call `UserStateService.snapshot()`.
6. Call `RiskService.assess()`; on a cache miss, request AI and possibly persist model-version and risk-prediction rows in separate `REQUIRES_NEW` transactions.
7. Query active rules and run `InterventionEngine.decide()`.
8. Load active exercises (the entity has eagerly loaded tags), map candidates and run `RecommendationScorer.recommend()`.
9. Serialize/write and flush recommendation snapshot.
10. Map response, including workout-by-recommendation and feedback-by-recommendation lookups.

The transaction holds the check-in row lock over this full orchestration, including the network call to AI and persistence. The supplied concurrent requests plus the long overall duration provide runtime evidence of overlapping work; stage timings identify a lock wait at risk-prediction insertion, but do not identify the database transaction holding the conflicting lock.

The supplied runtime log showed `RiskPredictionStore.save()` in `REQUIRES_NEW` waiting while the outer recommendation transaction held the parent check-in lock; the child risk row references that check-in. This strongly implicated the suspended outer transaction's exclusive row lock as the blocker, though no InnoDB wait-for report was captured.

The code is now changed so the recommendation cache-miss path calls `RiskService.assessForRecommendation()` and persists the risk row with `RiskPredictionStore.saveInCurrentTransaction()`. That joins the outer transaction owning the check-in lock and avoids a second transaction waiting on its own caller's parent lock. The independent `REQUIRES_NEW` save remains for other risk-service callers. This makes risk prediction and recommendation snapshot commit/rollback together on this path.

## Warm snapshot path

When a recommendation snapshot already exists, `today()` returns via `toResponse(existing)` before user-state aggregation, risk inference, rules, exercise loading, scoring, or snapshot creation. This explains why a later refresh can perform less work than the first recommendation request. It is a code-path difference, not a measured cause of the reported delay.

## Query footprint (static inspection only)

First snapshot miss includes at least the following logical repository operations: profile, locked check-in, locked snapshot lookup, three history queries (check-ins/workouts/feedback), risk lookup, model-version lookup and possible insert, risk-prediction insert, active rules, active exercises/tags, recommendation snapshot insert, and workout/feedback link lookups for the response. Actual SQL statement count depends on Hibernate fetch behavior, the active model result, empty/non-empty collections and whether a model-version row exists. No Hibernate SQL/statistics trace was available; this is not a measured query count.

`RecommendationScorer` iterates the in-memory candidate list and scores/sorts it. Its algorithm is deterministic CPU work; without catalog size/profile or profiler timings, no runtime cost is claimed.

## Concurrency and persistence-lock signal

React development `StrictMode` can create duplicate recommendation GETs from mount effects. `today()` takes a pessimistic check-in lock before processing. A duplicate request may consequently queue behind the first request. Prior MySQL configuration showed `innodb_lock_wait_timeout=50` seconds, but it was a global diagnostic observation, not an event-correlated lock measurement. This is a credible contributor to pending/timeout behavior, not a confirmed root cause.

The supplied logs confirm a database lock wait timeout on `risk_predictions` insertion, not just a generic slow query. AI and history-state computation were fast in the measured request; they are not the cause of the 50-second wait. The transaction path has been adjusted; absence of the lock timeout after deployment still needs runtime verification.

## Conclusion

For the supplied request, the dominant measured stage was risk-prediction persistence waiting on a MySQL lock, followed by HTTP 500. The AI call and user-state computation completed quickly. The same-transaction write fix is implemented and covered by unit tests; a MySQL-backed cache-miss/concurrency verification has not yet been run.

## Code references

- [CheckinController.java](../../backend/src/main/java/com/adaptivefit/controller/CheckinController.java)
- [CheckinService.java](../../backend/src/main/java/com/adaptivefit/service/CheckinService.java)
- [RecommendationService.java](../../backend/src/main/java/com/adaptivefit/service/RecommendationService.java)
- [UserStateService.java](../../backend/src/main/java/com/adaptivefit/service/UserStateService.java)
- [RiskService.java](../../backend/src/main/java/com/adaptivefit/service/RiskService.java)
- [RiskPredictionStore.java](../../backend/src/main/java/com/adaptivefit/service/RiskPredictionStore.java)
- [DailyCheckinRepository.java](../../backend/src/main/java/com/adaptivefit/repository/DailyCheckinRepository.java)
- [RecommendationHistoryRepository.java](../../backend/src/main/java/com/adaptivefit/repository/RecommendationHistoryRepository.java)
- [RecommendationScorer.java](../../backend/src/main/java/com/adaptivefit/engine/RecommendationScorer.java)
