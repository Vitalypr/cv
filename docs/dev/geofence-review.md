# Geofence subsystem — how it works, and what was wrong (v1.1 review)

Written after the field report: *"sometimes doesn't record at all, sometimes
records a wrong hour, and when I visit the base twice a day it records only the
last one."* This is the end-to-end walkthrough plus the root-cause analysis.

## 1. The pipeline, end to end

```
Play Services  ──ENTER/EXIT──▶  GeofenceReceiver  ──▶ GeofenceEngine ──▶ OfficeFenceMachine (pure)
   (OS-managed)                  extracts event time      loads state +          decides actions
                                                          day facts                    │
        FenceStateStore  ◀──────── persists Outside/Inside/Leaving ◀──────────────────┘
                                                                                       │
                                              Notifier / DayRepository ◀───────────────┘
```

1. **Registration** (`GeofenceManager.sync()`): one office fence (configurable radius) + one 2 km
   fence per job location, `setInitialTrigger(0)`. Requires FINE **and**
   BACKGROUND location; otherwise nothing registers and `GeofenceStatus` says why.
2. **Delivery** (`GeofenceReceiver`): Play Services reports a crossing only when it
   next obtains a fix, so events arrive late and out of order. The receiver reads
   `triggeringLocation.time` as the event's own timestamp.
3. **Decision** (`OfficeFenceMachine`): pure state machine over
   `Outside / Inside(since) / Leaving(since, exitAt)`.
4. **Debounce**: an EXIT arms a 10-minute alarm; a re-entry cancels it. Only when
   it elapses is the exit believed.
5. **Effect**: notification suggestion, or a direct write in automatic mode.

## 2. Root causes found

### RC-1 — A missed EXIT poisoned the state permanently  *(→ "doesn't record at all")*

`ENTER` while already `Inside` was treated as a duplicate delivery and ignored,
**with no bound on how old the stored entry was**. Play Services misses exits
routinely (Doze, Samsung battery management, a lost fix). Once one exit was
missed the fence stayed `Inside(yesterday 08:00)` forever:

- every later ENTER → "duplicate", no prompt, nothing recorded — *silently, for ever*;
- the eventual EXIT → `exitDate(entered=yesterday, left=today)` → `null` → dropped.

One missed transition disabled the whole feature until the app's data was cleared.
**This is the primary cause of the reported silence.**

### RC-2 — A missed debounce alarm did the same  *(→ "doesn't record at all")*

`Leaving` + ENTER always resumed the *original* visit, however long ago the exit
was. `setAndAllowWhileIdle` can be deferred for hours in Doze and dropped
entirely by aggressive OEM battery management, so `Leaving` could persist across
days and then swallow the next day's arrival the same way as RC-1.

### RC-3 — Nothing was recorded without a notification tap  *(→ "only the last visit")*

`silentGeofence` defaulted to **false**, so every arrival and departure was only
ever a *suggestion*. A missed or swiped notification lost that fact permanently.
Two visits in a day therefore reduced to whichever one the user happened to tap:
miss the 08:00 prompt, tap the 14:00 one, and the day starts at 14:00. The user
asked for automatic recording; the app was built confirm-first.

### RC-4 — The event timestamp was trusted unconditionally  *(→ "wrong hour")*

`triggeringLocation.time` is the age of the *fix*, not of the crossing. A fused
location can be minutes old, so the recorded time was minutes early — and if it
was over 60 minutes old, `isStale()` **discarded the event entirely**, adding
another silent-loss path on top of RC-1.

### RC-5 — Registration could silently lapse

`sync()` skips re-registration when the fence set is unchanged (correct — it
avoids losing in-flight transitions), but the fingerprint is process-scoped with
**no expiry**. If Play Services dropped the fences while the process stayed alive
(location toggled off/on, GMS update, "clear location history"), the app believed
it was registered and never recovered.

### RC-6 — 150 m fence, not adjustable

The default office radius is at the tight end for urban GPS, and the Settings
screen displayed it without offering a way to change it. *(v2.0: the radius is
now chosen from 100 / 300 / 500 m / 1 / 1.5 / 2 km, default 300 m.)*

### RC-7 — No way to see what happened

There was no record of transitions, so a field failure could not be diagnosed
after the fact — only reproduced by guesswork.

## 3. Fixes

| RC | Fix |
|---|---|
| 1 | ENTER while `Inside` starts a **new visit** when the stored entry is from another day or older than `MAX_VISIT` (14 h). Same-visit duplicates still ignored. |
| 2 | ENTER while `Leaving` beyond the debounce window **settles the pending exit first** (the engine replays a `DebounceElapsed` step with the exit's own day context), then opens the new visit. |
| 3 | Automatic recording is the **default**. Arrival/departure are written as they happen (GEOFENCE source, never overwriting a manual value), with an informational notification carrying an עריכה action. |
| 4 | The receiver **clamps** the event time: a fix older than 10 minutes (or in the future) falls back to delivery time instead of producing a wrong hour or being dropped. |
| 5 | The fingerprint skip **expires after 30 minutes**, `PROVIDERS_CHANGED` re-registers, and a `GEOFENCE_NOT_AVAILABLE` error triggers a re-sync. |
| 6 | Office radius is selectable in Settings (100/150/200/300 m). |
| 7 | A rolling **diagnostics log** of the last 50 transitions and what each one did, visible in Settings. |

## 4. Invariants that did NOT change

- A GEOFENCE write never overwrites a MANUAL value.
- Decisions use the event's own logical day, never `now()`.
- An EXIT is acted on only when the matching ENTER was recorded.
- No leaving time is suggested for a stay under one hour; the arrival survives, amber.
- חופש/חג accept nothing.
