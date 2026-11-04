# Frontend flow — first check-in performance

**Audit date:** 2026-11-02  
**Scope:** Static code trace plus browser observations already shared. No API was replayed and no write request was sent for timing.

## Flow after submitting check-in

1. `CheckIn` initially loads `GET /api/checkins/today` and `GET /api/workouts?page=0&size=100` in one `Promise.all`.
2. On submit, the handler sends one `POST /api/checkins`.
3. If POST resolves successfully, it navigates to `/` (Dashboard). It does not call AI or recommendation itself.
4. Dashboard loads profile, check-in list, workout history and `GET /api/recommendations/today` concurrently in one `Promise.all`.
5. Dashboard does not render its main content until every promise resolves. A recommendation failure therefore shows the page-level error even if the check-in POST and other GETs succeeded.
6. Opening `/today` mounts `Today`, which loads recommendation, exercise library and profile concurrently. Recommendation is called again; loading remains visible until all three resolve.

## Duplicate requests and retries

- `main.tsx` wraps the app in React `StrictMode`. In the Vite development build, React intentionally replays mount effects. Dashboard's loading `useEffect` and Today's loading `useEffect` each contain API requests; they can therefore issue duplicate GETs in development.
- The submit handler is an event handler, not a mount effect; StrictMode does not itself replay that user's click/POST. No POST retry was found in the handler.
- Axios client has no retry interceptor. A 401 clears the local session; other errors are rejected to the page. No automatic retry for 500 was found.
- Dashboard invokes recommendation once per effect mount. Today invokes it again when that route mounts. This is an additional separate call, not an Axios retry.

## Known observations

- A browser screenshot showed profile/exercise or check-in/workout-history GETs with HTTP 200 while recommendation `today` requests showed 500 and a separate `today` row was pending at capture time.
- A prior Postman request with the user account returned HTTP 200. Another request using admin returned the expected missing-profile HTTP 400. These observations refer to different requests and do not provide comparable durations or prove identical auth/environment/data.
- No captured Network HAR contains `POST /api/checkins` timing/status. It is not confirmed from the screenshots whether that POST returned 201 before Dashboard showed its error.
- No exact browser request duration was supplied. The earlier 30-second timeout was a caller observation from a previous test, not a measurement of the latest browser flow.

## Code references

- [CheckIn.tsx](../../frontend/src/pages/user/CheckIn.tsx): startup `Promise.all`, submit, navigation.
- [Dashboard.tsx](../../frontend/src/pages/user/Dashboard.tsx): concurrent dashboard loads and page-level error.
- [Today.tsx](../../frontend/src/pages/user/Today.tsx): recommendation/exercise/profile load and loading state.
- [main.tsx](../../frontend/src/main.tsx): development `StrictMode`.
- [endpoints.ts](../../frontend/src/api/endpoints.ts): endpoint methods.
- [client.ts](../../frontend/src/api/client.ts): `/api` base URL, bearer attachment and 401 handling; no retry.
- [vite.config.ts](../../frontend/vite.config.ts): `/api` proxy to backend port 8080 by default.

## Frontend conclusion

The check-in POST and recommendation GET are separate operations. The likely point at which the user perceives the delay is the post-submit Dashboard's recommendation request, because Dashboard waits for it along with other data before rendering. In development, StrictMode can multiply GET work. Neither fact measures the request duration or establishes the backend exception.
