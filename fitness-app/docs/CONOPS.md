# CONOPS — Concept of Operations

How the app is meant to be used in the real world. This document anchors every
product decision; if a feature doesn't serve the operating context below, question it.

## 1. Mission

Help one person execute and adhere to an 8-week strength + cardio program: know what
to train today, log it fast, track nutrition and body change, see progress, and (target
state) keep that data flowing to/from Samsung Health.

## 2. Primary user & context

- **Who:** a single adult, intermediate trainee (the owner). Not multi-user.
- **Where:** in a gym or at home, phone in hand.
- **When:** mid-set, between sets (rest windows of 30–180s), and briefly post-workout.
- **Constraints in the moment:** one hand free, possibly sweaty fingers, glances not
  reading, wants minimum taps and minimum typing. Bottom-of-screen thumb zone matters.
- **Connectivity:** must assume **offline**. Gyms have poor signal; the app must fully
  work with the radio off.
- **Device:** Android phone (Samsung). Hebrew UI, RTL.

## 3. Operating scenarios (the jobs to be done)

1. **"What do I do today?"** Open app → it shows today's scheduled workout (week N, day M
   of the 8-week cycle) without tapping. Override to another day if needed.
2. **"Log this set."** Pick exercise (pre-filled for today), see *last time's* numbers as
   a hint, enter weight×reps (steppers/keypad), one tap to confirm → rest timer auto-starts.
3. **"Am I progressing?"** Glance at per-exercise history/trend; see PRs and whether today
   beat last time.
4. **"Track my body."** Log weight/waist/steps/sleep; see honest date-scaled trend charts.
5. **"Eat to target."** Quick-add meals or templates; see calories/protein vs target.
6. **"How do I do this exercise?"** Open the exercise; see an illustration + cues.
7. **"Don't lose my data."** Export a backup; restore from one. Optionally sync with
   Samsung Health (steps/weight in, workouts out).
8. **"Remind me."** Rest-timer alerts; optional weekly backup nudge.

## 4. Success criteria (what "good" feels like)

- Logging a full set takes **≤ 3 taps and ≤ 0 mandatory keystrokes** when repeating last
  session (placeholder pre-fill + confirm).
- App opens straight to the correct day; no orientation needed.
- Nothing the user typed is ever silently lost (notes, in-progress set).
- Charts tell the truth about time (date-scaled, not index-scaled).
- Works with airplane mode on.

## 5. Non-goals (explicitly out of scope)

- No multi-user, social, sharing, or cloud accounts.
- No server-side anything. No analytics/telemetry.
- No giant food database; curated templates + quick manual entry only.
- No coaching AI / auto-programming the plan (the plan is author-curated).
- No Apple Watch / iOS Live Activity parity (native-only; not achievable on this stack).
- We do **not** change the *training/nutrition content* — that is the owner's domain.
  Engineering, UX, architecture, and data only.

## 6. Data ownership & safety

- All data is the user's, on the device, under their control.
- Export (JSON) is the canonical safety net and must always work.
- Any storage migration must be reversible/forward-merging and never destructive.
- Health Connect sync is **opt-in**, scoped to the minimum data types, and revocable.

## 7. Quality attributes (priority order)

1. **Data integrity** (never lose/corrupt logs)
2. **Offline reliability**
3. **Speed of logging** (in-gym ergonomics)
4. **Honesty of stats**
5. **Accessibility** (labels, focus, contrast, RTL)
6. **Maintainability** (so it can keep evolving)
