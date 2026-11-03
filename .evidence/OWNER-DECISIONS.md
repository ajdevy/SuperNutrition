# Deferred owner decisions

## D-09 — license project-owned material under MIT

Decided on: 2026-11-02
Decided by: ajdevy (repository owner, who delegated the choice in this Codex chat)
Channel: Codex chat request to prepare the repository for public submission and select an accepted license

Select MIT from the accepted licenses in the submission checklist. Apply it to
project-owned material in this repository. Third-party dependencies, datasets,
and vendored material retain any applicable upstream terms. This decision
selects a source license; it does not change repository visibility or authorize
a repository publication.

## D-07 — retain the local meal diary and profile name until user deletion

Decided on: 2026-10-24
Decided by: human user in the current Codex chat (direct product-owner instruction)
Channel: Codex chat 01a16557-276b-7002-b45e-4a79f11f1c89, reply to call_78f14ed32d134919be8322b05ff5c3f5

The user explicitly approved: "Да, хранить локально до удаления пользователем".
Persist saved meal records and the editable profile name in app-private local
storage across app launches, until the user deletes them. Deletion and edits
must survive restart; a failed write must not be reported as saved. Do not
persist raw photos, inference attempts, remote-consent receipts or diagnostics
under this decision. Do not introduce server sync or cloud backup of these
records. This is the human's direct approval; it is not an implementer-authored
GitHub ratification and does not decide other deferred retention questions.

## D-08 — approve the pinned USDA SR Legacy package for explicit MVP nutrition

Decided on: 2026-10-24
Decided by: human user in the current Codex chat (direct product-owner instruction)
Channel: Codex chat 01a16557-276b-7002-b45e-4a79f11f1c89, reply to call_3e2731770fe64013b077614752e59d8b

The user explicitly approved: "Да, подключить USDA по этим правилам" after
being shown USDA FoodData Central SR Legacy (2018-04, CC0), US catalog coverage,
explicit product selection and weight, and unknown values for absent nutrients.
Adopt the concrete proposal in
`_bmad-output/implementation-artifacts/mvp-catalog-proposal-2026-10-24.md`:
preserve namespaced canonical IDs and reproducible, integrity-checked package
provenance; show product/preparation and source before confirmation; require
grams and never infer ingredient distribution from whole-meal weight. Missing
values remain unknown, including in totals; unsupported meals remain identity
only. Only documented catalog-owned conversions are allowed. This approval
does not select a privacy jurisdiction, activate remote processing, settle
G-1/G-3/G-4/G-5 or authorize public accuracy claims.

## D-06 — publish factual app legal notices and expose them in Profile

Decided on: 2026-10-22
Decided by: human user in the current Codex chat
Channel: Codex chat 01a16557-276b-7002-b45e-4a79f11f1c89, direct publication request and operator-identification reply

The user requested Privacy Policy and Terms & Conditions on supernutrition.app
and identified the operator as Flexsent labs SIA, a Latvian company. The
existing website already publishes hello@supernutrition.app as its contact.
Preserve the waitlist notice, describe current application behavior factually,
and connect Profile to the published app-policy and terms URLs. This entry
records the human publication instruction and supplied identity; it does not
assert GitHub account ratification or settle any pending provider, retention,
market-routing or diagnostic-collection decision. Diagnostics remain off.


Three findings from the review are real but are not mine to settle: each one
picks between defensible designs, and the choice binds a published contract or
a privacy posture. They were left alone deliberately. This records what the
current behaviour is, what the options cost, and what would have to change.

Nothing here is a blocker for either pull request. Each is written so it can be
decided without re-reading the review.

## 1. Rewrite `inference/v1` or publish `inference/v2`

**Current state.** `contracts/inference/v2` and
`contracts/inference-evidence/v2` exist and are published alongside v1. Both
releases describe the same surface. Their compatibility matrices mark unbuilt
consumers `not-yet-implemented` rather than `accepted`, and
`scripts/tests/test_v2_compatibility_claims.py` ties an `accepted` claim to a
real implementation, so nothing currently claims support it does not have.

**The decision.** Two published versions of one surface is a cost that
compounds: every consumer has to say which it speaks, every fixture exists
twice, and the negative fixtures have to mutate both. The alternatives:

- **Fold v2 back into v1.** v1 is frozen, so this is a contract break and every
  consumer needs a coordinated update. Cheapest long-term if v1 has no external
  consumers yet.
- **Keep both, deprecate v1.** No break, but the duplication stays until every
  consumer migrates, and `not-yet-implemented` rows have to be worked off one
  at a time.

**What settles it:** whether anything outside this repository already speaks
`inference/v1`. That is not visible from here.

## 2. Server-side market binding

**Current state.** The client sends `claimedMarketJurisdiction` and the gateway
resolves policy from it. `MarketPolicyResolver` owns the resolution, and the
response reports `resolvedMarketJurisdiction` and `resolvedProcessingRegion`,
which the client binds against the region the consent receipt promised — a
response from a region the user was not shown is refused.

**The gap.** The jurisdiction itself is still the client's claim. A client that
claims a permissive market gets that market's policy. The binding proves the
server answered consistently with what it was told, not that what it was told
was true.

**The decision.** Deriving the jurisdiction server-side means deriving it from
something — request geography, the installation attestation, or an account
attribute. Each has a privacy cost of its own, and inferring a user's country
from their IP to decide what their privacy rights are is a policy question, not
an implementation detail. Doing it wrong is worse than the current state,
because a wrongly-derived jurisdiction is not correctable by the user.

**What settles it:** which signal the product is willing to collect for this,
and what happens when it disagrees with the client's claim — refuse, or apply
the stricter of the two.

## 3. Consent receipt persistence

**Current state.** Receipts are verified per request and not stored. Verification
covers the digest binding, the attempt binding, expiry, and clock skew
(`CONSENT_CLOCK_SKEW_SECONDS`). The product does not keep a record of what the
user consented to after the request that carried it.

**The gap.** There is no way to answer "what was this user shown when they
agreed to this upload" after the fact. That is a question a regulator or the
user may ask.

**The decision.** Storing receipts means storing a record of what each user
consented to and when — durable personal data, with its own retention policy,
subject-access path and deletion obligation. AD-8 keeps images ephemeral and
makes no recall claims; a receipt store is a different kind of record from the
image, but it is still a record, and adding one to a product built around not
keeping things is a deliberate reversal rather than an oversight to correct.

**What settles it:** whether the obligation to evidence consent outweighs the
cost of holding it, and if so, what the retention period for the receipts
themselves is.

---

# Closing decisions (2026-08-13)

The three items above were recorded as deferred. Two of them are still the
owner's, for a specific reason. One is not, and is decided here.

## 1. `contracts/mobile-api/v1`: the rewrite stands — DECIDED

This slice edited three published v1 files to add the E4.3 gateway fields. The
immutability gate it also adds rejects exactly that, which is why
`acknowledged-edits.toml` carries three pinned entries.

**Decision: v1 keeps the rewrite. mobile-api/v2 is not published.**

The immutability rule exists to protect consumers that cannot be updated in
lockstep with the producer. `mobile-api/v1` has exactly one consumer, the mobile
client in this repository, and no build of it has reached a user. There is
nobody holding the old bytes. Publishing v2 to protect an audience of zero would
add a second live release, a second fixture set and a second migration story for
no reader.

What makes this safe is that it is bounded rather than general: the three
acknowledgements are pinned to exact digests, so any further edit to those files
fails the gate, and they expire 2026-09-30. That expiry is not a deadline for
this decision — it is the point at which a NEW edit would need its own
justification. Tracked so it cannot lapse silently.

This is reversible: publishing v2 later costs the same as publishing it now.

## 2. Server-side market binding — REMAINS THE OWNER'S

Not decided here, and deliberately so. Binding the market server-side means
choosing a signal to derive it from: request geography, installation
attestation, or an account attribute. Each is a decision to start collecting
something about users that the product does not collect today.

That is the kind of choice that is hard to walk back — once a jurisdiction
signal is collected and relied upon, removing it changes the privacy posture and
the legal basis, not just the code. It is business-irreversible in a way the
contract-versioning question is not.

Contained meanwhile: fixture markets in test mode, empty production policy, and
the response reports the resolved jurisdiction and region so the client can
refuse a reply that contradicts the receipt shown before consent.

**Recommendation if asked:** derive from installation attestation rather than IP.
It is already collected for AD-21, it is not a location, and it does not change
what the product knows about a user.

## 3. Consent-receipt persistence — REMAINS THE OWNER'S

Also not decided here, for the same class of reason. Storing receipts means
holding a durable record of what each user agreed to and when: personal data
with its own retention period, subject-access path and deletion obligation.

AD-8 keeps images ephemeral and makes no recall claims. Adding a consent store
to a product built around not keeping things is a deliberate reversal, and
un-collecting it later is not possible for records already written.

Contained meanwhile: every receipt is verified per request against digest,
attempt binding, expiry and clock skew. What is missing is evidence after the
fact, not enforcement during.

**Recommendation if asked:** if the obligation to evidence consent is real, store
the receipt digest and decision only — not the receipt body — with the same
retention as the inference run it belongs to.

---

# Owner decisions D1–D3 (2026-08-13, against main 401daec)

Recorded when the stacked photo-analysis pair was closed out. main is
unchanged by any of it: the slice awaits a fresh pull request.

## D1 — PR #39 is superseded and closed

main advanced twice past #39's base: Epic 3 (local inference) and Epic 7 (#37 —
typed identification, `contracts/inference/v3` as the **evidence** release,
market policy). Epic 7 re-implemented most of #39's scope independently.

The only content unique to #39 was `mobile/inference/remote` and its engine
contract. It has zero consumers, and its contract collides by path with main's
published v3. It was **not** re-homed to v4: a second engine release with no
consumer is a contract nobody reads.

Closed with a per-finding record on the PR itself. Every finding is either
satisfied-on-main or parked in issue #43, and no parked item's code path is
reachable on main today, so closing carries no shipping delta.

## D2 — PR #38 is the merge candidate

Merged as `401daec` and then reverted from main at the owner's instruction, so
the slice now waits on this branch for a fresh pull request. Nothing about its
content changed: `401daec`'s tree and this branch's tree are the same work.
Carries all of handover-38: H1–H3, M1–M18, every Low group, and the
three deferred contract decisions above.

Two defects surfaced only when the merge first ran the workflow against a tree
where Epic 7's rename had landed, and both are recorded in their own commits:

- `ci-required.yml` called three `inference-v2-*` scripts that Epic 7 had
  renamed to v3. main's own required check was red for this reason before the
  merge; the merge only surfaced it.
- the immutability gate rejected M14's own market-policy digest correction. It
  is acknowledged narrowly, pinned to both digests and expiring 2026-09-30,
  rather than reverted or waved through.

## D3 — gap audit against main 401daec: no relocations

Every handover-38 finding is on main. Of handover-39, four are satisfied-on-main
(P1-1, P1-2, P1-3, P1-6, plus two P2s) and the rest are parked.

Three findings are genuinely absent from main and were **not** relocated,
because none is reachable in a shipped flow:

| finding | why it is not reachable |
|---|---|
| P1-5 | `LocalFirstRouter` is on main and complete; the coordinator never consults it. No remote path exists on main, so the policy cannot be violated in a way that reaches a provider. |
| P1-11 | main's v3 matrix marks `remote-v3` and `evaluation-v3` accepted while `mobile/inference/remote` and `mobile/evaluation/harness` are both absent. |
| P1-12 | `MealSearchScreen` rows carry no selection callback, and no app module depends on `meal-search-ui`. |

That is the terminal state: nothing left to port.

## Follow-ups

- **#43** — remote inference: the engine module, its contract, and P1-4/5/7/8/9/10/11/12
  plus three P2s parked with them.
- **evaluation-v2** — `contracts/inference/v2` marks it accepted with no harness
  in the tree. Unchanged by decision; same class as P1-11 above.

---

# Owner decisions on D-01 and D-02 (2026-08-15)

Source: the owner's `pr44-handover-decisions.md` (2026-08-15), which reviews
handover-3 and handover-4 and adopts a recommendation for each open contract
decision. Recorded here because an issue comment is not the durable record; both
are also posted to #48 and #50.

**The source file is not committed to this repository**, so its operative text
is quoted verbatim below rather than referenced. Quoting was the owner's choice
between that and committing the file (2026-08-23). The original is in Russian
and is reproduced as written; the surrounding entries state the same decisions
in English.

> **§2, «Рекомендация» (D-01):**
>
> **Вариант 2** — авторизованная переопределённая семантика через существующий
> механизм acknowledgement (pinned digest, срок действия — как у трёх текущих),
> **не молчаливая правка**. Платить сейчас за v2 и миграцию клиента — значит
> защищать потребителей, которых пока не существует.
>
> С жёстким условием: **до первого внешнего потребителя или отгруженного билда**
> выпустить v2, куда эти изменения войдут честным образом (привязать к дедлайну
> #47).

> **§2, «Ключевой факт для решения» (why v1 may be redefined at all):**
>
> У `mobile-api/v1` **ровно один потребитель** — мобильный клиент в этом же
> репозитории. **Ни один билд не дошёл до пользователя.** […] Но ситуация
> временная: как только появится внешний потребитель или отгруженный билд,
> вариант 2 закрывается.

> **§3, «Рекомендация» (D-02):**
>
> **Вариант 2**, согласен с автором issue. Одно расхождение с ним: он предлагает
> отложить до «следующего релиза» — но поскольку #48 всё равно требует трогать
> тот же опубликованный контракт, **делайте оба одним раундом**: одно решение
> владельца, один acknowledgement, один заход.

> **§3, «Варианты», on changing the code to match the published order:**
>
> **3. Изменить код под контракт** | Возвращает неаутентифицированный 8 МБ
> memory-pin | **Никогда**

Both decisions bear on `contracts/mobile-api/v1`, a published immutable release.
Neither is implemented by this branch: PR #44 documents the divergences in code
and tracks them, and claims neither closed.

> **Addendum, 2026-08-21 — the sentence above is no longer accurate for D-01.**
>
> Recorded as an addendum rather than an edit, because this file is the durable
> record and rewriting what it said is exactly the failure it exists to prevent.
>
> D-01's admission-entry half — #48(a), "a transfer is entered on admission,
> never on hand-off" — IS implemented on this branch. It is in
> `contracts/mobile-api/v1/transfer-states.toml` ("Entered on admission, never
> on hand-off: an attempt a provider never accepted has not transferred"), and
> the gateway follows it: `resultSuppressed` in
> `routes/gateway/transfer-state.ts` was rewritten because #48 stopped promoting
> the state at hand-off, which changed what a cancellation arriving mid-flight
> must report.
>
> The rest of D-01, and all of D-02, remain unimplemented and untracked as
> closed. Found by the PR #44 review (handover-10 item 20).

## D-01 — transfer state machine (#48)

Decided on: 2026-08-15
Decided by: repository owner
Channel: pr44-handover-decisions.md, posted to #48

**Decided: authorize the redefinition under the existing acknowledgement
mechanism rather than publishing `mobile-api/v2`, with a hard expiry condition.**

The reasoning the owner gives: `mobile-api/v1` has exactly one consumer, the
mobile client in this repository, and no build has reached a user. Publishing v2
to protect an audience of zero costs a second live release and a client
migration. The same reasoning already approved the E4.3 rewrite under three
pinned acknowledgements expiring 2026-09-30 (#47), and handover-4 explicitly
permits either path "while no client has shipped".

**The hard condition:** before the first external consumer or a shipped build,
these changes go into a real `v2`. Tied to the #47 deadline so it cannot lapse
silently.

### Correction to the framing — this is two divergences, not one

Reading `transfer-states.toml:35-46` and
`fixtures/cancellation-transferred.toml` against the code, the single decision
above actually covers two separate problems with different costs. This matters
because most of #48 does not need a contract decision at all:

**(a) When `transferred` is entered — no contract change required.**
The release says `provider_called = true` for the `transferred` state. The code
sets it before `orchestrator.analyze()` runs. Moving the transition to after the
provider accepts the payload makes the code *conform* to the published contract.
It is a pure code fix. It needs no acknowledgement, no v2, and no owner
decision — only the work.

**(b) `downstream_deletion_requested = true` — blocked by G-4, not by the
contract mechanism.**
The release requires `downstream_deletion_requested = true` when a `transferred`
attempt is cancelled, and the canonical fixture asserts it. The code returns
`false`, truthfully, because no provider adapter exposes an erasure operation —
which is what H-02 corrected. The release also carries an `honest_claim` for
this state reading "a deletion request was sent downstream", an assertion the
product cannot currently make.

This is the only part that needs the contract decision above. And note what
gates it: making the code satisfy the contract requires a real provider deletion
capability, which is a provider/DPA/legal decision under **G-4** — an open
deferral this document elsewhere instructs us not to touch.

**Consequence for sequencing.** (a) can be implemented immediately and
independently. (b) cannot be closed by an acknowledgement alone: an
acknowledgement authorizes editing the released file, but what to edit it to
depends on whether provider deletion will ever exist. Two coherent shapes:

- Record that `downstream_deletion_requested` is `false` until a provider
  deletion capability exists, and correct the `honest_claim` to match — the
  AD-8-honest option, available now, and one that stops the contract asserting
  something untrue.
- Wait for G-4 and then require `true` with a real adapter invocation behind it.

The first is recommended: leaving a published contract claiming a deletion
request that is never sent is the same class of defect H-02 fixed in the code.

## D-02 — validation order (#50)

Decided on: 2026-08-15
Decided by: repository owner
Channel: pr44-handover-decisions.md, posted to #50

**Decided: option 2 — split the concepts in the published list.**
`streamed-size-guard` stays pre-authentication, since it reads a length header
rather than a body; `magic-byte-sniff` and `dimension-bounds` are explicitly
marked post-authentication and pre-provider.

Option 3 (change the code to match the contract) is refused outright: it
reinstates the unauthenticated 8 MiB memory pin that the auth-first ordering was
introduced to remove. The code is right and the published text is wrong.

Nothing that these checks would reject reaches a provider, which is the property
the order exists to guarantee and states in its own comment. Only the relative
position of `authentication` diverges.

**Amendment to #50 as filed, adopted:** #50 recommended deferring to the next
release. The owner overrides that — #48 has to touch the same frozen contract
anyway, so both go in one round: one decision, one acknowledgement, one pass.

## D-03 — where Product idempotency state lives (#49)

Decided on: 2026-08-26
Decided by: ajdevy (repository owner)
Channel: https://github.com/flexsent-labs/SuperNutrition/pull/44#issuecomment-5424935408

> **Attribution history — read this before treating the fields above as an
> oversight.**
>
> These three fields were filled on 2026-08-22 with
> `iggdrassilon (RMD, https://github.com/iggdrassilon) — owner of PR #44`, and a
> ratifying comment was posted from that account
> ([issuecomment-5382807819](https://github.com/flexsent-labs/SuperNutrition/pull/44#issuecomment-5382807819)).
>
> The PR #44 review (handover-11, EV-2) then established that `iggdrassilon` is
> the PR **author** — the implementer account — and that the repository owner is
> **`ajdevy`**. The PR author confirmed this on 2026-08-23. So the comment and
> the attribution record a ratification by the implementing side, which is
> exactly what EV-1 was about; they are not the owner's.
>
> Reset to PENDING rather than corrected in place, because there is nothing yet
> to correct them TO: the owner has not ratified. `verify-owner-decisions.sh
> --strict` fails on these two, which is the accurate state. Kept here rather
> than deleted, because the record of who signed what and when is the whole
> point of this file.
>
> The same correction is public on the PR
> ([issuecomment-5390241666](https://github.com/flexsent-labs/SuperNutrition/pull/44#issuecomment-5390241666)),
> posted alongside the original rather than replacing it — a reviewer reading
> #44 top to bottom sees the claim and its withdrawal, which deleting the first
> comment would have hidden.
>
> **To close:** one confirmation from `ajdevy` on PR #44, after which these
> fields name that comment.

**Decided: Redis holds the status, the request digest and the meal's id. Never
the response body.**

Two replicas each accepted the same `Idempotency-Key` and each created a durable
meal, because the idempotency claim was the last protected store still living in
one process. Sharing it is what lifts the single-instance fence. The question
this decision answers is not *whether* to share it but *what* to put in the
shared store.

A meal is a diary entry: it carries `userId`, dish names, times of day and
nutrition figures. Redis is a coordination store here — its retention rules were
written for identifiers, and it is readable by anyone holding the connection. So
the body stays in Postgres, where deletion actually deletes, and a replay
re-reads it through the controller. The owner check therefore runs again on the
replay path: a stored reference is never authority by itself.

**Consequence, since accepted.** A replay costs one Postgres read rather than a
Redis read. If the meal was deleted between the original request and the retry,
there is nothing to replay and the key conflicts rather than inventing a
response — refusing is worse for the client than a cached body would have been,
and better than both the alternatives (replaying a body for a row that no longer
exists, or creating a second meal under a key that already made one).

**Related, decided later and separately (handover-10, DS-F7):** the key itself
is `sha256(userId, client key)` rather than the readable composite. That is an
implementation correction to this decision, not a change to it.

Implemented in commit 2c8d55b7. Recorded here 2026-08-21, after the PR #44
review found it had been implemented without a record (EV-1).

## D-04 — what market retention binds (#49, story 4.4 criterion 4)

Decided on: 2026-08-26
Decided by: ajdevy (repository owner)
Channel: https://github.com/flexsent-labs/SuperNutrition/pull/44#issuecomment-5424935408

> **Attribution history — read this before treating the fields above as an
> oversight.**
>
> These three fields were filled on 2026-08-22 with
> `iggdrassilon (RMD, https://github.com/iggdrassilon) — owner of PR #44`, and a
> ratifying comment was posted from that account
> ([issuecomment-5382807819](https://github.com/flexsent-labs/SuperNutrition/pull/44#issuecomment-5382807819)).
>
> The PR #44 review (handover-11, EV-2) then established that `iggdrassilon` is
> the PR **author** — the implementer account — and that the repository owner is
> **`ajdevy`**. The PR author confirmed this on 2026-08-23. So the comment and
> the attribution record a ratification by the implementing side, which is
> exactly what EV-1 was about; they are not the owner's.
>
> Reset to PENDING rather than corrected in place, because there is nothing yet
> to correct them TO: the owner has not ratified. `verify-owner-decisions.sh
> --strict` fails on these two, which is the accurate state. Kept here rather
> than deleted, because the record of who signed what and when is the whole
> point of this file.
>
> The same correction is public on the PR
> ([issuecomment-5390241666](https://github.com/flexsent-labs/SuperNutrition/pull/44#issuecomment-5390241666)),
> posted alongside the original rather than replacing it — a reviewer reading
> #44 top to bottom sees the claim and its withdrawal, which deleting the first
> comment would have hidden.
>
> **To close:** one confirmation from `ajdevy` on PR #44, after which these
> fields name that comment.

**Decided: reading (a) — market retention governs user content. Coordination
identifiers keep the at-most-once horizon.**

Criterion 4 as written said every Redis key must expire within the retention the
request's market policy allows. The fixture markets carry `retentionDays = 0`.
Read literally against one of those, the at-most-once guard gets a zero-second
expiry — it is deleted at the moment it is needed, a duplicate provider call
becomes possible, and the user's photo is uploaded a second time. The privacy
clause, enforced literally, produces the privacy harm.

So the clause is read as binding user CONTENT, and the keys in question hold
none: rate-limit timestamps, claim markers, an owner-token digest, and a status
with a meal id. The meal itself is in Postgres, where retention and erasure do
bind it.

**The condition this rests on, and it is tested.** Retention does not bind these
keys *because* they hold no user content — so if that ever stops being true, this
decision stops applying. Three tests hold the line: the TTL inventory
(`security/retention.test.ts`) fails on any coordination TTL above the horizon
and on any new TTL constant that does not declare which obligation bounds it;
the two-instance suite reads every key's expiry from the live server; and the
same suite scans every value and every key name for user content.

**The dissenting reading, still available.** Reading (b) — retention binds
everything keyed to a user's action — shrinks the at-most-once window to the
retention period and, where retention is zero, accepts the guarantee as weaker.
A market whose counsel requires that reading needs (b) and a shorter horizon.
That is a deliberate weakening, to be taken knowingly, not a default.

This is an engineering decision about which obligation applies to which data. It
is not legal advice.

Implemented in commit 39d30c86. Recorded here 2026-08-21, after the PR #44
review found it had been implemented without a record (EV-1).

## D-05 — the crash-before-transfer window on a claimed attempt (#49)

Decided on: 2026-08-26
Decided by: ajdevy (repository owner)
Channel: https://github.com/flexsent-labs/SuperNutrition/pull/44#issuecomment-5424935408

**Decided: option (a) — accept it as inherent for now, and revisit if it is ever
observed.** The recommendation below was adopted as written; option (b), the
pre-transfer release marker, is not built. It requires no code change, which is
why this entry closes without one.

**What the window is.** `claimAttempt` is re-entrant for the identical holder
(DS-F12): the same owner token on the same instance may re-claim its own attempt,
which is what stops a lost reply locking an id for 24 hours. What it cannot do is
tell "the holder is retrying" from "the holder is gone". If the instance that
claimed an attempt dies after the claim and before the transfer, the record
survives with a matching owner and a dead instance, and that attempt id stays
held fleet-wide for `TRANSFER_REGISTRY_TTL_SECONDS` (24h) with nothing sent.

The user-visible effect: a client that retries the same attempt id after a
replica died gets `attempt_already_used` about a photo no provider ever saw. The
remedy available to it — a new attempt id — is one it has no reason to know it
needs.

**Why it is not just fixed.** Releasing on the owner token alone would let a
SECOND replica claim an attempt the first is still transferring, which is the
duplicate provider call — a second upload of the user's photo — that this
registry exists to prevent. Trading a 24-hour stall for a duplicate upload is
the wrong direction, and this is precisely the kind of trade an implementer
should not make alone.

**The two options.**

- **(a) Accept as inherent.** The window needs a process death inside a specific
  short interval, and its cost is a stalled attempt id, not lost data or a
  duplicate call. The gateway is single-instance today (`GATEWAY_STATE_BACKEND`
  defaults to `memory`), so the case cannot arise in the shipping configuration
  at all. Costs nothing; leaves a rough edge for whoever meets it.
- **(b) Pre-transfer release marker.** The holder writes a short-lived liveness
  marker beside the claim and refreshes it while the attempt is pre-transfer; a
  claim whose marker has lapsed and which never reached `transferring` may be
  taken over. Closes the window, and adds a second key, a refresh path, and a new
  way to be wrong — a marker that lapses under load while the transfer is
  genuinely running would hand the attempt to a second replica, which is the
  harm being avoided.

**Why (a) is proposed.** (b) buys a rare recovery by adding a mechanism whose own
failure mode is the exact outcome the registry exists to prevent. That is a bad
trade until the window is actually observed. If it is, (b) is the shape to
build, and it should be built against the transfer state machine's own
`pre-transfer` state rather than a timer.

**Recorded because the code says so.** The comment at
`security/stores.ts` calls this "an owner decision, not an implementer's —
raised rather than guessed at". AGENTS.md's rule is that a decision binding a
correctness guarantee belongs here, not only in a comment. Drafted 2026-08-24
after the PR #44 review (handover-12) pointed out that the comment was the only
record.

## Sequencing

1. Decisions recorded (this entry, plus comments on #48 and #50).
2. Merge #44 — **the owner's action.** D-01/D-02 do not block it.
3. Implement #48 and #50 together against the contract. Note that #48(a) needs
   no contract change and can start immediately; #48(b) needs the shape above
   settled first.
4. #49 (distributed state) strictly after #48, or the current state machine gets
   spread across instances and redone. Already in #49's acceptance criteria.
   AD-8 is unbreakable there: Redis carries identifiers, ownership, expiry and
   cancellation — never image bytes.
5. #42 then #41, in that order. Verified disjoint: #42 adds
   `docs/food-image-datasets-research.md` (+140); #41 removes
   `docs/food-photo-recognition-report.md` (-712) and
   `food-photo-recognition-research/outline.yaml` (-37). No conflict, and the
   order avoids a window with no current document.
6. Branch cleanup, and close #22/#23/#26/#27 as superseded by #44.

## Standing, unaffected

#45 (server-side market binding) and #46 (consent-receipt persistence) remain
the owner's; #47 expires 2026-09-30. G-4 continues to block production market,
production authentication, and provider/DPA terms. None of these is touched.

## D-10 — private source repository for Android food-identification runtime

Decided on: 2026-11-02
Decided by: ajdevy (repository owner)
Channel: Codex chat request to prepare the public repository and extract the Android food-identification library

Place the existing Android Food-101/YOLO-World runtime source in a separate
private repository and import its compiled runtime-only AAR into the public
SuperNutrition project. Keep the existing model weights as separately tracked
assets in the public project. The owner confirms redistribution under the
applicable upstream terms. This packaging decision does not close G-3 device
evidence, production model approval, or any separate submission approval
required by upstream terms. Existing SuperNutrition history and commit IDs are
preserved; prior copies of this source and the model assets remain reachable in
that history.

## D-11 — remove AGPL-bound model material and personal sample data before public release

Decided on: 2026-11-02
Decided by: ajdevy (repository owner, direct instruction in this Codex chat)
Channel: Codex chat request to remove AGPL material from GitHub history and public-release checklist requirements

Remove the YOLO-World region model and its app integration from the SuperNutrition
repository, retaining Food-101 dish classification until a separately cleared
replacement is selected. Remove the real meal photographs stored as user samples
and use made-up fixtures instead. Keep the canonical Android runtime source and
AAR in the private library repository, and remove their historical copies from
SuperNutrition. Remove the Apple developer account name/email and fixed team ID
from setup docs and project defaults; contributors supply their own signing
identity. Redact developer-machine absolute paths from generated repository
metadata. Rewrite reachable GitHub history so these model, source, binary,
personal-sample, and machine-identity details are absent from all branches and
tags, while preserving the remaining commit sequence and author identities where
possible. Keep the SuperNutrition repository private; this decision does not
authorize making it public. The private runtime AAR must not carry a LICENSE file
or publish a license entry in its Maven metadata; the source repository retains
its own license.

## D-12 — redact local physical-device identifiers before public release

Decided on: 2026-11-02
Decided by: ajdevy (repository owner, direct instruction in this Codex chat)
Channel: Codex chat request to remove real personal or customer data before public release

Replace physical-device and workstation-created simulator UDIDs in committed
documentation and evidence with placeholders. Preserve general device model,
OS, and test results when useful, but do not publish device identifiers. Apply
the same replacements to reachable Git history.

## D-13 — remove AGPL-dependent model integrations and generated outputs

Decided on: 2026-11-02
Decided by: ajdevy (repository owner, direct instruction in this Codex chat)
Channel: Codex chat request to rewrite GitHub history to remove AGPL material

Remove the remaining runnable FPB/Ultralytics integration, its tests and
generated predictions/evidence, the standalone detector Android benchmark,
and the Nutrition5k detector-fusion harness, outputs, and presentations from
SuperNutrition. Remove these artifacts from every reachable branch and tag
while retaining the project's commit sequence and linked author identities.
Keep any necessary license/provenance discussion as plain research
documentation only; it must not imply these models or runners are part of the
app or distributable runtime.

## D-14 — use MPL 2.0 for the SuperNutrition project

Decided on: 2026-11-03
Decided by: ajdevy (repository owner, direct instruction in this Codex chat)
Channel: Codex chat request to select a copyleft license from the accepted list

License the SuperNutrition project under the Mozilla Public License 2.0, an
accepted copyleft option that does not require the additional approval listed
for GPL and AGPL. Under MPL 2.0, modifications to covered files must remain
available under MPL when distributed, with source made available to recipients.
Private use does not require public source publication, and independent files
in a larger work are not automatically covered. This license does not replace
the separate terms for the private inference AAR or third-party materials.

## D-15 — leave GitHub pull-request refs unchanged

Decided on: 2026-11-03
Decided by: ajdevy (repository owner, direct instruction in this Codex chat)
Channel: Codex chat reply declining a GitHub Support request

Do not submit a GitHub Support request to remove or rewrite the retained
pull-request refs or cached views. Leave those refs unchanged. Keep the
SuperNutrition repository private while those refs still expose the five real
user meal photographs and removed model and benchmark artifacts. This decision
does not authorize making the repository public while the photographs remain
reachable; revisit public release only after the owner makes a separate,
informed decision about the retained personal data.
