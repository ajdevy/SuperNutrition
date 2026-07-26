---
title: NutriArch Product Brief
status: final
created: 2026-07-26
updated: 2026-07-26
sources:
  - /Users/aj/Downloads/NutriArch
  - /Users/aj/Downloads/report.md
---

# Product Brief: NutriArch

## Executive Summary

NutriArch is an Android and iOS nutrition app that turns a meal photo into an
editable, database-grounded nutrition estimate. Its product bet is that a fast,
honest estimate that users can confirm is more useful than a tedious manual food
log or an AI answer that presents uncertain calories as fact.

The first release uses a hybrid identification pipeline. It performs useful work
on the device whenever the device can do so reliably, including image quality
checks and compact food recognition. The app can ask a remote inference service for
help with ambiguous or recipe-level meals, but only after the user understands
that the photo will leave the device. Both paths return the same candidate and
uncertainty contract; confirmed foods are resolved against a versioned nutrition
database rather than letting a model invent nutrient values.

## The Problem

Conventional food logging asks people to search databases, estimate servings,
and navigate several screens before they receive value. Photo-first competitors
reduce some of that work but often hide whether analysis is local or remote,
overstate portion precision, and still struggle with mixed dishes, oils, sauces,
brands, and culturally diverse foods.

People need a quicker way to understand a meal without being misled about what a
single image can prove. They also need a usable path when connectivity is weak,
their device cannot run a larger model, or they do not want to upload a photo.

## The Solution

NutriArch opens into a camera-first flow:

1. The user takes or selects a meal photo.
2. The app immediately checks image quality and whether food is visible.
3. A capability-aware resolver runs the best available local engine and, when
   needed, offers remote analysis and invokes it only after consent.
4. The app presents food candidates, confidence, portion assumptions, and any
   questions needed to reduce uncertainty.
5. The user confirms or corrects the result.
6. NutriArch maps confirmed foods and servings to a versioned nutrition source
   and shows calories and macronutrients with visible assumptions or ranges.

The app must always provide a recovery path: retake the photo, edit a candidate,
enter a serving, search manually, or continue without remote upload.

## What Makes This Different

- **Local work is a product capability, not a marketing badge.** Broadly
  supported compact models provide immediate offline feedback; higher-end local
  models are optional device tiers rather than a universal requirement.
- **Remote inference is explicit and replaceable.** It is isolated behind the
  same contract as local inference, allowing provider evaluation without
  rewriting capture, confirmation, or nutrition grounding.
- **Nutrition is grounded, not generated.** Models propose visible foods and
  assumptions; a controlled nutrition source produces the numeric estimate.
- **Uncertainty is part of the result.** Mixed dishes, hidden ingredients,
  serving size, oil, sauce, and brand ambiguity are surfaced for confirmation.

## Who This Serves

The primary user is a health-conscious person who wants quick nutritional
awareness but does not maintain a detailed food diary. They value speed, plain
language, and the ability to correct a result more than false precision.

The initial commercial audience is users in high-income Tier 1 consumer markets.
The beta is English-first, but food data, units, privacy disclosures, and
evaluation slices must be ready for country-specific configuration rather than
assuming one US-only experience.

The first beta should also serve users with privacy or connectivity concerns who
want to know what can be done on their device before agreeing to a cloud upload.
NutriArch does not provide medical measurements or allergy detection and is not
a substitute for professional dietary advice.

## Success Criteria

The private beta succeeds when:

**Experience**

- a user can move from capture to a useful candidate result in at most two
  primary actions;
- the camera is ready within 2 seconds on supported test devices;
- local-path feedback is available within 4 seconds and remote-path feedback
  within 10 seconds under the defined test network;

**Trust and privacy**

- every nutrition result identifies its source, serving assumption, uncertainty,
  and inference route;
- no photo leaves the device on a local-only path;
- remote upload occurs only after the app clearly explains the upload and obtains
  consent, and raw photos are not retained by default;

**Validation and delivery**

- a representative, consented benchmark shows that the hybrid flow improves
  corrected item accuracy or time to an accepted result over local-only and
  remote-only baselines;
- Android and iOS internal builds complete the same end-to-end acceptance suite.

The product must not optimize first-pass acceptance rate at the expense of
correction quality, privacy, honest uncertainty, battery, or cloud cost.

## First-Release Scope

### In

- Android and iOS apps from one Kotlin Multiplatform codebase.
- Compose Multiplatform UI wherever it meets platform behavior and accessibility
  needs; native interop for camera and inference capabilities where required.
- Camera capture and photo-library import.
- Shared photo normalization, quality checks, candidate/result contracts, and
  nutrition grounding.
- Capability-aware on-device inference with platform- and device-specific
  implementations.
- Remote inference through a first-party gateway with provider adapters.
- Local-first routing, explicit cloud consent, visible engine provenance,
  confirmation/correction, retry, and manual recovery.
- Local and remote inference in separate modules and delivery tracks so different
  developers can implement them concurrently after the shared contract is stable.
- A versioned evaluation corpus and engine comparison harness.
- TestFlight and Google Play internal distribution with real-device validation.

### Out

- Accounts, social features, coaching, streaks, medical or allergy claims.
- Daily diary, long-term history, goals, wearable integration, and monetization.
- Claims of exact grams or calories, or complete hidden-ingredient detection,
  from a single RGB image.
- Silent cloud upload, raw-photo retention by default, or training on user photos
  without a separate opt-in.
- A single model or vendor treated as mandatory across all devices.

## Vision

If the first release proves the capture, correction, and grounding loop,
NutriArch can grow into a low-friction nutrition companion with optional history,
daily totals, barcode and label recognition, regional food packs, and mindful
logging modes. The durable advantage is not one model. It is the ability to
evaluate and replace local and remote engines without changing the user contract,
while learning from consented corrections and keeping nutrition provenance
visible.

## Current Assumptions

- **Working name:** NutriArch is the product name; trademark and store-name checks
  remain open.
- **Beta posture:** no login or persisted meal history is required for the first
  internal release.

## Open Questions

- **Remote gateway:** provider choice and production hosting will be selected
  after the benchmark spike; no provider key belongs in the app.
- **Nutrition coverage:** the initial database, locales, and licensing package
  require a separate source decision before public launch.
- **Launch markets:** "Tier 1" defines the market segment, not a legal country
  list; the launch-country sequence must be selected before store submission.
- **Evaluation thresholds:** exact accuracy, correction-rate, memory, thermal,
  battery, and cost-per-accepted-meal thresholds will be set from the first
  benchmark rather than invented in planning.
