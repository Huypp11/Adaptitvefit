# AdaptiveFit AI

Phase 2A: backend foundation (Spring Boot + MySQL + JWT auth + profile).

## Chay
1. `cp .env.example .env` va doi cac gia tri
2. `docker compose up -d`
3. `cd backend && mvn spring-boot:run`  (chay tu thu muc backend/ de doc ../.env)

> Du lieu trong du an giai doan nay la DEMO DATA de phat trien, chua phai du lieu nghien cuu thuc te.

## Phase 8 - Recommendation feedback loop
- Flyway migration `V10__recommendation_feedback_loop.sql` stores one immutable recommendation snapshot per user check-in. Repeated `GET /api/recommendations/today` calls for that check-in reuse the same ID and snapshot.
- The signed-in user can retrieve their paginated snapshots from `GET /api/recommendations/history?page=0&size=20`.
- A response is not treated as proof that the user saw the recommendation; the app does not collect a separate display-confirmation event.
- Starting from the Today recommendation sends `recommendationId` to `POST /api/workouts/start`. The backend verifies ownership and that the selected exercises and planned minutes are in the snapshot, then links the workout. Repeating an identical start request returns the already-started workout.
- Optional recommendation feedback uses `POST /api/feedback/recommendations/{id}` and `PUT /api/feedback/recommendations/{id}`. `GET /api/feedback` returns only feedback belonging to the signed-in user. Existing workout feedback at `POST /api/feedback` remains supported.
- Admin-only aggregate statistics are available at `GET /api/admin/feedback/stats`. `startRate` is linked workouts divided by saved recommendations; `workoutCompletionRate` is completed linked workouts divided by all linked workouts. Feedback status counts are user-reported, while completed workout counts come from workout records. No feedback is not counted as NOT_DONE.
- Phase 8 collects and summarizes feedback only; new feedback does not trigger model training.
