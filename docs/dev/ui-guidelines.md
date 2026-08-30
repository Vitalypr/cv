# UI guidelines

The approved look is `docs/daylog-mockup.html` — Material 3, light, clean. UI code must stay restyle-friendly: **all design decisions are tokens in `ui/theme/`**, screens consume tokens only.

## Design tokens (from the approved mockup)

| Token | Value | Use |
|---|---|---|
| `Petrol` | `#0B6E6A` (container tint `#E4F1F0`, deep `#085250`) | brand accent: primary actions, selected chips, active nav |
| `SendGreen` | `#1BA65B` (tint `#E6F5EC`, dark `#178A4C`) | ONLY send/sent semantics |
| `Amber` | `#A9770F` / tint `#FBF3DF` | "logged, not sent" status |
| `Ink` | `#1B2733`, secondary `#5A6B77`, muted `#8A99A4` | text — text never wears series/status colors |
| Surfaces | ground `#F7F9FA`, card `#FFFFFF`, line `#E2E9EC` | |
| Chart series | base `#00897B`, home `#4054B2`, field `#9E6410` | CVD-validated trio, one hue per work mode — separable by hue AND lightness; do not change one without re-validating all three |
| `Warn` | `#B3261E` / tint `#FCEDEC` | a real contradiction the user must fix — today only the over-allocated time budget. Distinct from Amber, which means "unconfirmed", not "wrong" |
| Shape | cards 18dp, chips/full-round, sheets 24dp top | |
| Type | Rubik (display: titles, big numbers) + Heebo (all UI text); `FontFeature tnum`/tabular for every time value | bundle as font resources |

Dynamic color (Material You) may *tint* neutrals but the semantic trio (petrol/send-green/amber) is fixed — status must stay recognizable.

**Widget surface (`res/values/colors.xml`)** mirrors these tokens for RemoteViews, which cannot read Compose values — change shared tokens in both files. It adds two widget-only colors by product-owner decision (spec §5.6): `widget_missing` `#FF8A80` for a time not yet logged and `widget_check` `#4CD98A` for the ✓ beside a logged one. These *are* text wearing status color — a deliberate exception, because at a glance on a home screen the color is the whole message. They are light variants chosen for contrast against the dark pills; do not reuse them in the app UI.

## RTL & Hebrew (non-negotiable)

- `android:supportsRtl="true"`; default `values/` strings are Hebrew; layout via `start/end` only — never `left/right`.
- Compose gets direction from locale; snapshot tests pin `he-IL` + RTL so regressions are caught visually.
- **Reports:** every line starts with RLM `‏` (ReportBuilder owns this). Time ranges `10:00–13:30` use en-dash between LTR runs. Golden tests are canonical.
- Numerals: Western digits, 24h times, `dd.MM.yyyy`, week starts Sunday (א׳).

## Charts

Follow the dataviz method: stacked thin bars, 2dp surface gaps between segments, rounded 2dp data ends, dashed average reference line, RTL time axis (day 1 at the right), legend above plot, tap tooltip with exact values, y-axis labels muted. Exact values must also exist as text (KPI tiles / share text) — charts are never the only source.

## How to restyle safely

1. Change tokens in `ui/theme/` (colors/type/shape) — screens update globally.
2. Re-record snapshots (`-Proborazzi.test.record=true`), eyeball the diff images, commit them with the change.
3. Never restyle by editing screen files with literal values; if a screen needs a new semantic role, add a token first.
