# AdaptiveFit AI

Phase 2A: backend foundation (Spring Boot + MySQL + JWT auth + profile).

## Chay
1. `cp .env.example .env` va doi cac gia tri
2. Cai va khoi dong MySQL, tao database `adaptivefit` (mac dinh `DB_URL=jdbc:mysql://localhost:3306/adaptivefit`; doi `DB_URL` trong `.env` neu dung dia chi khac). Flyway tu tao bang khi backend khoi dong.
3. `cd backend && mvn spring-boot:run`  (chay tu thu muc backend/ de doc ../.env)

> Du lieu trong du an giai doan nay la DEMO DATA de phat trien, chua phai du lieu nghien cuu thuc te.

## Phase 8 - Recommendation feedback loop
- Flyway migration `V10__recommendation_feedback_loop.sql` stores one immutable recommendation snapshot per user check-in. Repeated `GET /api/recommendations/today` calls for that check-in reuse the same ID and snapshot.
- The signed-in user can retrieve their paginated snapshots from `GET /api/recommendations/history?page=0&size=20`.
- A response is not treated as proof that the user saw the recommendation. Phase 10 records `DISPLAYED` only after the Today page is visible and only while the user has opted in.
- Starting from the Today recommendation sends `recommendationId` to `POST /api/workouts/start`. The backend verifies ownership and that the selected exercises and planned minutes are in the snapshot, then links the workout. Repeating an identical start request returns the already-started workout.
- Optional recommendation feedback uses `POST /api/feedback/recommendations/{id}` and `PUT /api/feedback/recommendations/{id}`. `GET /api/feedback` returns only feedback belonging to the signed-in user. Existing workout feedback at `POST /api/feedback` remains supported.
- Admin-only aggregate statistics are available at `GET /api/admin/feedback/stats`. `startRate` is linked workouts divided by saved recommendations; `workoutCompletionRate` is completed linked workouts divided by eligible linked workouts (excluding `INVALID_WORKOUT`). Feedback status counts are user-reported, while completed workout counts come from workout records. No feedback is not counted as NOT_DONE.
- Phase 8 collects and summarizes feedback only; new feedback does not trigger model training.

## Adaptive recommendation safeguards
- If today's check-in is missing, `GET /api/recommendations/today` returns a `PROFILE_FALLBACK` recommendation using profile limits and recent completed-workout/check-in history. This response is ephemeral (`recommendationId: null`), uses `NORMAL`, does not create a check-in or snapshot, and does not call the AI risk model. Starting its workout does not link a persisted recommendation.
- A workout is recorded as `INVALID_WORKOUT` when its actual duration is below 3 minutes **or** below 30% of its planned exercise duration. It is not treated as completed; only `COMPLETED` workouts feed streak and risk-model history.
- Abandoning a workout can optionally store `TOO_TIRED`, `NO_EQUIPMENT`, or `UNEXPECTED_WORK`. The latest reason from the previous calendar day adjusts recommendation filters/scoring only; it is not added to the AI feature vector.
- User-facing risk badges classify the predicted missed-workout probability: up to 30% low, above 30% through 60% needs attention, and above 60% high. Above 60% selects `RECOVERY` only when check-in fatigue evidence exists; otherwise it selects `REDUCE`. This is not a health assessment.

## Phase 10 - Recommendation measurement and consent
- Flyway migration `V12__recommendation_events_and_consent.sql` creates versioned, separate telemetry consent and an append-only recommendation event log with per-user idempotency keys. The migration is source-only until the backend is started against a database; it has not been applied as part of this change.
- Event collection is opt-in. The user can review or withdraw consent at `/recommendation-data`; withdrawal deletes their recommendation telemetry but does not delete operational workout, check-in, or feedback records. No recommendation event is written before consent.
- `DISPLAYED` and user-initiated `SKIPPED` events come from Today. Accept/selection-change, workout lifecycle, invalid-workout, and feedback events are captured server-side when consent is active. The event log is purged daily after 90 days; the admin aggregate report at `/admin/recommendation-analytics` uses the same maximum window and separates CHECKIN from PROFILE_FALLBACK impressions.
- The primary measured outcome is a valid `COMPLETED` workout within 24 hours of an impression. An impression must have a complete 24-hour observation window to enter the denominator; missing feedback is reported separately and is not treated as failure. These measures are observational and do not automatically retrain or change recommendation scoring.
