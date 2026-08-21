/**
 * Story 4.4, criterion 4 — RESOLVED 2026-08-20 (reading (a), bound to an
 * enforced invariant).
 *
 * The criterion as written said: "Every key carries an expiry no longer than
 * the retention the request's market policy allows, so nothing outlives its own
 * deletion obligation." Applied literally to a fixture market with
 * `retentionDays = 0`, that gives the at-most-once guard a zero-second expiry —
 * deleting the guard at the moment it is needed, permitting a duplicate
 * provider call, and so causing a SECOND upload of the user's photo. The
 * privacy clause, enforced literally, produces the privacy harm.
 *
 * The decision is that market retention governs USER CONTENT, and these keys
 * hold none. That is not an assertion of convenience — it is the property the
 * whole gateway design already maintains (AD-8), and it is what makes the
 * amendment sound rather than merely convenient:
 *
 *   ratelimit:<layer>:<principal>        timestamps in a sorted set
 *   effect:<attemptId>:<effectId>        a claim marker
 *   request-id:request-id:<requestId>    a spent-replay marker
 *   attempt:<attemptId>                  <ownerTokenDigest>:<instanceId>
 *   cancelled:<attemptId>                a cancellation marker
 *   idem:<sha256(userId, key)>           a status, a request digest, a meal id
 *
 * The request-id keyspace really does repeat itself: it is a second
 * `RedisEffectStore` composed with the prefix `request-id`, and the pipeline
 * passes `'request-id'` as the attempt id. An earlier version of this list said
 * `reqid:<attemptId>:<requestId>`, which is a keyspace that has never existed —
 * a documentation error in the one file that claims to be the inventory of what
 * Redis holds.
 *
 * Identifiers and markers. No photo, no bytes, no dish, no nutrition. A meal's
 * content stays in Postgres, where the market's retention and deletion
 * obligations do bind it and where erasure actually removes it.
 *
 * So the amended criterion is CONDITIONAL, and the condition is tested:
 *
 *   No Redis value holds user content; because of that, and only because of it,
 *   market retention does not bind these keys. They instead carry a fixed
 *   expiry no greater than the at-most-once horizon below — the shortest
 *   expiry for which the guarantee they exist to provide still holds for as
 *   long as the gateway claims it.
 *
 * If a future key ever holds user content, this amendment does not cover it and
 * market retention binds it. `retention.test.ts` fails on any coordination TTL
 * above the ceiling or any new TTL constant absent from its inventory, and the
 * two-instance suite fails on a key with no expiry or a value carrying content.
 *
 * This is an engineering decision about which obligation applies to which data,
 * recorded so a reviewer can disagree with it explicitly. It is not legal
 * advice, and a market whose counsel reads retention as binding coordination
 * identifiers too needs reading (b) and a shorter horizon — which is a
 * deliberate weakening of at-most-once, to be taken knowingly.
 */
export const COORDINATION_TTL_CEILING_SECONDS = 86_400;
