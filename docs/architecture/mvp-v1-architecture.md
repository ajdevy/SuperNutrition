# SuperNutrition MVP v1

## Product & Architecture Specification

# Vision

SuperNutrition is an AI-powered nutrition companion designed to help users build sustainable eating habits through meal tracking, nutrition intelligence, behavioral coaching, gamification, and long-term progress monitoring.

The product is intentionally centered around eating events rather than food photos.

A photo is only one possible input mechanism.

The primary business entity is a Meal Event.

The product's success is measured by habit formation and consistent meal logging rather than recognition accuracy alone.

---

# Product Philosophy

## Meal Event First

The entire platform is built around a single core entity:

```text
Meal Event
```

A Meal Event represents a user's eating action regardless of how it was recorded.

Supported creation channels:

* Photo Scan
* Manual Entry
* Voice Entry

All ingestion methods must be normalized into a unified Meal Event structure.

This ensures consistent business logic, analytics, coaching, and future extensibility.

---

## User Is the Authority

The hierarchy of truth within the system is:

```text
User Confirmation
    >
Verified Nutrition Catalog
    >
AI Inference
```

AI is never considered the final authority.

AI generates hypotheses.

The user validates the final result.

Whenever uncertainty exists, the system should prefer user confirmation over automated assumptions.

---

## No Durable Raw Photos

User privacy is a core architectural principle.

Raw meal photos are processed only for inference purposes.

After analysis is completed:

* results are persisted
* the original image is deleted

The system stores only:

* structured analysis results
* nutrition snapshots
* inference evidence
* metadata

This reduces storage costs, privacy risks, and regulatory exposure.

---

## AI Is Replaceable

The product must remain independent from any specific AI provider.

AI vendors are infrastructure dependencies, not business dependencies.

All providers must be integrated through standardized contracts and interfaces.

Potential providers include:

* OpenAI
* Gemini
* Claude
* Passio
* LogMeal
* Future Providers

Replacing one provider with another must not require changes to business-domain logic.

---

## Nutrition Facts Must Be Grounded

Nutrition values must never originate directly from a language model.

All nutritional information must be resolved through authoritative nutrition catalogs.

MVP source:

```text
USDA FoodData Central
```

Future architecture:

```text
USDA
    +
Open Food Facts
    +
Commercial Nutrition Sources
    +
AI Fallback Resolver
```

This guarantees reproducibility, consistency, and trustworthiness.

---

# Product Domains

## Domain A — Meal Capture

Responsible for creating Meal Events.

Supported inputs:

* Photo Capture
* Manual Logging
* Voice Logging

Output:

```text
Meal Event
```

---

## Domain B — Nutrition Intelligence

Responsible for transforming meal inputs into structured nutrition data.

Capabilities:

* food recognition
* dish identification
* ingredient extraction
* portion estimation
* nutrition catalog matching
* nutrient calculation

---

## Domain C — Coaching

Responsible for generating user-facing guidance.

Examples:

* recommendations
* motivation
* behavior nudges
* educational insights

Coaching cannot alter factual nutrition data.

It only interprets existing facts.

---

## Domain D — Gamification

Responsible for user engagement systems.

Capabilities:

* XP
* Streaks
* Achievements
* Badges
* Challenges
* Failure States

---

## Domain E — Progress

Responsible for analytics and historical tracking.

Capabilities:

* history
* calendar views
* trend analysis
* nutrient summaries
* goal adherence metrics
* habit tracking

---

# MVP User Experience

## Screen 1 — Capture

Primary user action.

Allows:

* meal photo capture
* manual meal entry
* voice meal entry

Displays:

* detected foods
* nutrition information
* coaching insight
* earned XP

---

## Screen 2 — Journey

Behavior and engagement dashboard.

Displays:

* meal history
* achievements
* streaks
* missed days
* habit milestones

---

## Screen 3 — Progress

Health and nutrition dashboard.

Displays:

* calories
* protein
* fat
* carbohydrates
* goal compliance
* behavioral trends

---

# User Goals

Default objective:

```text
Healthy Eating
```

Additional goals:

```text
Weight Loss
Weight Gain
Weight Maintenance
Custom Goal
```

Goals influence interpretation and coaching.

Goals do not alter the underlying nutrition data model.

This separation simplifies architecture and prevents domain coupling.

---

# AI Architecture

## Inference Orchestrator

The central AI coordination service.

Responsibilities:

* provider selection
* model routing
* retry strategies
* confidence evaluation
* cost optimization
* fallback execution

All AI requests pass through the orchestrator.

No downstream service communicates directly with AI providers.

---

## Two-Pass Inference Strategy

### Pass 1 — Fast Analysis

Objective:

* identify foods
* estimate confidence
* minimize latency

Optimized for speed and cost.

---

### Pass 2 — Context-Aware Analysis

Triggered only when confidence falls below a predefined threshold.

Additional context may include:

* user country
* nutrition goal
* meal type
* historical meals
* prior confirmations

Optimized for accuracy.

---

## Confidence Routing

```text
confidence >= threshold
    -> accept result

confidence < threshold
    -> execute second pass

confidence still low
    -> request user confirmation
```

This architecture balances speed, cost, and reliability.

---

# Nutrition Resolution Pipeline

Primary MVP source:

```text
USDA
```

Resolution flow:

```text
AI Candidate
    ->
Catalog Search
    ->
Best Match Selection
    ->
Nutrition Snapshot
```

The Nutrition Snapshot becomes immutable historical data.

Future catalog changes must not alter historical records.

---

# Gamification MVP

Initial achievements:

* First Meal
* 3-Day Streak
* 7-Day Streak
* Healthy Week
* Protein Hero

XP is awarded for:

* meal logging
* goal completion
* streak maintenance

Gamification exists to reinforce consistency rather than competition.

---

# Backend Architecture

## Services

```text
auth

meal

inference

nutrition

coach

gamification

progress

user-goals

catalog
```

Services should communicate through clearly defined application contracts.

Domain boundaries must remain independent.

---

## Core Entity

```text
Meal Event
```

Every major business capability is built around this entity.

The Meal Event acts as the system's aggregate root.

---

# Data Model

## users

User profile and preferences.

---

## user_goals

User objectives and targets.

---

## meal_events

Canonical record of eating events.

---

## meal_items

Food items associated with a Meal Event.

---

## nutrition_snapshots

Immutable nutrition records generated during analysis.

---

## inference_evidence

Evidence generated by AI processing.

Stored fields:

* provider
* confidence
* candidates
* latency
* model metadata

---

## achievements

Achievement definitions.

---

## user_achievements

Achievement ownership records.

---

## streaks

Consecutive engagement tracking.

---

## daily_progress

Daily aggregated nutrition and habit metrics.

---

## coach_messages

Generated coaching history.

---

# Future Roadmap

Planned platform extensions:

* Open Food Facts Integration
* Barcode Scanning
* Subscription System
* Payment Processing
* Social Features
* AI Coach Chat
* Meal Planning
* Advanced Analytics
* Multiple AI Providers
* Enterprise Nutrition Catalogs

---

# Success Metrics

## Primary Metric

Meal Logging Consistency

The core purpose of the product is to create a daily nutrition-tracking habit.

---

## Secondary Metrics

* Weekly Active Users
* Meals Logged Per Week
* Streak Retention
* Goal Adherence
* User Confirmation Rate
* Coaching Engagement

---

# Product Thesis

SuperNutrition is not a food-recognition application.

SuperNutrition is a behavior-change platform powered by nutrition intelligence.

Food recognition is an enabling capability.

Habit formation is the product.
