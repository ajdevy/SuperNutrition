# Proposed market postures — RESEARCH PROPOSAL, NOT APPROVED POLICY

**Status: none of this is entered in `mobile/config/quality/market-policy.toml`.**
Every market there remains `unresolved`. This document is a briefing for counsel.
It was produced by desk research, not by a lawyer, and it is not legal advice.
Each row is a starting hypothesis for a named approver to accept, reject, or
refine — and the contract will not accept any of it without that approver and an
expiry date.

## The finding that matters most

**Per-attempt consent probably answers lawfulness and does not answer transfer.**
Those are two separate questions under the GDPR, and the consent UI only answers
the first. EDPB Guidelines 2/2018 read the Article 49(1)(a) explicit-consent
derogation restrictively — for occasional, non-repetitive transfers. An app that
sends a payload every time someone logs a meal is systematic and repeated, which
is what the derogation is not for.

This is contested: the "occasional" gloss is EDPB interpretation, and the statute
attaches that word to other derogations but not to consent. No CJEU ruling
settles it. Contested is not a foundation — if the architecture depends on
consent as a transfer basis, that is the single largest legal exposure here.

**The clean answer is to name the EEA as the processing region.** Then no
transfer occurs for the EU, EEA and Norway, and the UK and Switzerland both treat
the EEA as adequate. One region string covers seven markets and removes Chapter V
from the analysis entirely. The product can afford this because it degrades to a
working offline product, which most products cannot.

## Two properties of this product that change the answer

**Publishing and permitting are separate decisions.** The deterministic floor
works offline on every device, so a market with no remote permission gets a
working app. The store footprint can be as wide as the stores allow while remote
processing is gated per jurisdiction. "Publish everywhere possible" and "process
everywhere" are not the same request, and only the second one is legally hard.

**Permission is already per modality, and that turns out to be the important
control.** The risk is asymmetric:

- A *meal photo* is probably not biometric data. Recital 51 says photographs are
  not systematically special-category, and Article 4(14) requires processing
  aimed at unique identification. Recognising a food is not recognising a person.
  The residual risk is incidental capture — other diners, religious dress, a
  visible insulin pump, a hospital tray.
- The *free-text box* is the sharp risk, and it is a field-design problem rather
  than a typical-input problem. "Two boiled eggs and rye toast" is benign. The
  same box accepts "halal", "Ramadan iftar", "vegan on ethical grounds",
  "gluten-free — coeliac", "post-chemo shake". You cannot control the payload of
  an unbounded text field, so any posture must assume Article 9 data will
  sometimes pass through it.

The Article 29 Working Party's February 2015 letter on lifestyle and wellbeing
apps names **diet** explicitly as data from which health status may be inferred.
It also supplies the best mitigating fact: accumulation is what makes it health
data — "a week's worth of step counter data may not be health data, but several
years' worth could." This product stores no record and is session-scoped, which
is a real control and should be documented as deliberate rather than incidental.

**Recommendation worth considering before any of the market rows:** launch with
`photo_remote_permission = permitted` and `text_remote_permission = forbidden`
in most markets. Typed descriptions stay on-device, where the deterministic floor
already answers them. That collapses most of the Article 9 exposure, and the
contract was built to express exactly this asymmetry.

## Proposed tiers

### Tier A — remote permitted, EEA region, no transfer problem
Ireland and the EU/EEA generally, Norway, United Kingdom, Switzerland, Brazil
(ANPD adequacy, Res. 32/2026), Argentina (AAIP list), Chile, Colombia, Israel,
South Africa, Kenya.

These accept an EEA processing region either because no transfer occurs or
because the EEA is adequate or "essentially equivalent" under their law. This is
the cheapest tier to operate: one region, one story.

### Tier B — remote permitted, region named, contract or assessment required
United States, Canada, Mexico, Australia, New Zealand, Japan, Singapore, South
Korea, India, Türkiye, Saudi Arabia, Nigeria.

Each needs something executed before it is real: US must exclude "covered person"
vendors under 28 CFR 202 regardless of where servers sit; Canada needs a
completed s.17 assessment naming the destination and vendor *before* the
transfer, not documenting it after; Türkiye needs KVKK SCCs and notification;
Nigeria has no adequacy list at all, so the region is defined by the instrument.
Japan is cleanest via a Tokyo region or an EEA/UK region under PPC equivalence.

### Tier C — local-only for now
China, Vietnam, Indonesia, Russia, Qatar, Egypt, Kazakhstan, UAE, and Italy
pending one Article 9(4) check.

**Russia deserves its own line, because it answers OQ-5.** 242-FZ requires
in-country storage. The recommendation is not to build Russian infrastructure but
to ship Russia with remote inference disabled entirely: if nothing leaves the
device and no database is maintained, Article 18(5) is not engaged at all. That
is cleaner and cheaper than the alternative, and OQ-5 has been open since
2026-07-27 waiting for exactly this. It also does not reduce the product in
Russia, because the floor answers typed descriptions offline.

Vietnam's Decree 13 is a genuine localization requirement; China's PIPL needs a
cleared cross-border route; Egypt requires a licence that has no published
process. Indonesia, Kazakhstan and UAE are `unresolved` rather than prohibited —
the research could not reach a defensible answer, which is the honest result.

## Where the EU and UK diverge, and why it matters

CJEU C-184/20 (*OT*) held that data "liable indirectly to reveal" sensitive
information falls within Article 9, apparently regardless of intent. The ICO
applies an intention test instead. These do not sit together. Practical
consequence: the EU analysis should assume Article 9 applies to dietary data; the
UK analysis may not have to. **Do not let one memo cover both.**

## Two conditional triggers to monitor, not to act on yet

- **France, Decree n°2026-209** (in force 27 March 2026, territoriality from
  27 September 2026): health data physically stored in the EU/EEA only. Probably
  out of scope for a no-account app that delivers no care — but French commentary
  warns that positioning as a "wellness app" is not a reliable escape.
- **Germany, §393 SGB V**: does not accept SCCs to a non-adequate country,
  requires BSI C5 and a German establishment. Becomes decisive the moment the
  product touches DiGA reimbursement or a statutory insurer.

## What I could not supply, and nobody should invent

A named legal approver and an expiry date for any row above. The contract
requires both, and treats a market lacking either as unresolved. That is the
point: this document is research, and research is not approval.

## What counsel should be asked first

1. Can we rely on an EEA processing region for Tier A, and does that remove
   Chapter V as intended?
2. Is dietary free text Article 9 data in the EU, and does the
   photo-permitted/text-forbidden split materially reduce exposure?
3. Is per-attempt consent "explicit" enough for Article 9(2)(a), and does the
   wording need to name special-category data?
4. Russia: does shipping local-only genuinely avoid 242-FZ, as proposed?
