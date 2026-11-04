# Database timing and query audit

## Available diagnostics

Prior read-only investigation of the disposable `adaptivefit` schema established:

- Flyway V10 and V11 both had `success=1`.
- MySQL slow-query logging was enabled with `long_query_time=10` seconds and `log_output=FILE`.
- Global values observed then: `Slow_queries=11`, `Max_used_connections=13`, `Threads_connected=13`, `Threads_running=2`.
- Active query count for `adaptivefit` was zero at the later diagnostic instant.
- The MySQL log files could not be read with the available Windows account; the DB account lacked `PROCESS` and access to required Performance Schema diagnostics.

These are not measurements from the first check-in request, are global or post-event snapshots, and cannot be attributed to the reported delay. Current-user data may also differ from the two previously inspected test users.

The user later supplied request-correlated Spring logs. These confirm MySQL error 1205 / SQLState 40001 while inserting a `risk_predictions` row: the insert stage waited 50.410 seconds before failing. This is runtime evidence of a lock wait on the write path, unlike the earlier global slow-query counters. It still does not identify the blocking InnoDB transaction.

## Query and transaction behavior

### Check-in write

`CheckinService.create()` does one existence check and one repository save in a transaction. No AI or recommendation call occurs in the check-in transaction. No SQL timing or query count was captured.

### Recommendation cache miss

The recommendation request opens a transaction and obtains pessimistic write locks on the check-in and an existing snapshot lookup. It then executes history reads, risk/model-version/prediction persistence, rule/exercise reads and snapshot persistence. The transaction spans the remote AI request, so any slow inference extends lock duration and increases the impact of simultaneous calls.

The supplied timing narrows the observed incident: state snapshot took 14 ms, AI call 22 ms, while `risk_predictions` insertion waited 50.410 seconds and failed. `RecommendationService.today()` holds the check-in `PESSIMISTIC_WRITE` lock; risk save is `REQUIRES_NEW` and inserts a row whose check-in foreign key references the locked row. This is a strong self-blocking transaction hypothesis. Without MySQL lock owner/wait-for diagnostics it remains unknown whether the outer suspended transaction was the exact lock holder or another concurrent DB transaction.

The active-exercise query maps each entity's eager tags. Hibernate may issue extra collection reads depending on the mapping/fetch plan and result count. Without SQL tracing, an N+1 problem is neither confirmed nor excluded.

### Snapshot hit

An existing snapshot avoids history aggregation, AI, rule/exercise loading, scoring and snapshot insert. Response mapping still reads linked workout and feedback. Therefore a fast reload after a successful initial recommendation is consistent with the code path, even if the initial cache-fill was slow.

## Connection pool and duplicate snapshots

- No application-specific Hikari pool size/timeout or Hibernate statistics configuration was found in the inspected `application.yml`; Spring Boot defaults apply unless environment or external runtime settings override them.
- Historical MySQL max-used-connections was 13, which by itself does not prove or disprove Hikari pool exhaustion. No pool metrics from the incident exist.
- Snapshot lookup is serialized by the check-in pessimistic lock for concurrent calls to this service method. Snapshot uniqueness/constraints are present from prior schema inspection, but no request-correlated duplicate check was performed for the user's current account during this audit.
- Prior inspected test users 52/53 had zero recommendation/risk/workout/feedback rows at the earlier diagnostic time. Do not generalize those counts to the current browser account.

## Conclusion

Two overlapping recommendation GETs for the same pseudonymous user are visible in supplied logs. A lock wait on the risk-prediction insert is confirmed; duplicate calls can additionally wait on the check-in lock. The exact database lock holder is still unconfirmed. No N+1 query count or pool exhaustion evidence was supplied.
