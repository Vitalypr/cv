# Architecture

Google's official app-architecture guidance, kept minimal: **UI → ViewModel → Repository → Room/DataStore**, unidirectional data flow with Kotlin Flow. Two Gradle modules with one dependency edge: `:app → :domain`.

## Layers

| Layer | Module | Contains | Must not |
|---|---|---|---|
| Domain | `:domain` | immutable models (`DaySnapshot`, `ActivityEntry`, `FieldJob`), `ReportBuilder`, `StatsCalculator`, time/format utils, status derivation | import anything Android; do I/O |
| Data | `:app` `data/` | Room entities+DAOs+`DayLogDb`, `DayRepository` (maps entities ↔ domain models), `SettingsRepository` (DataStore) | leak Room entities above the repository |
| UI | `:app` `ui/` | one ViewModel + one `UiState` data class per screen; stateless composables | touch repositories directly from composables; hold Android `Context` in ViewModels beyond injected app context |
| System | `:app` `reminder/`, `geofence/`, `reporting/`, `notifications/` | AlarmManager scheduling, geofence receiver decision table, share-intent construction, notification channels/variants | contain business rules — they call into `:domain`/repositories |

## State flow

- Repositories expose `Flow<DaySnapshot>` / `Flow<Settings>`; ViewModels `combine` + `stateIn` into a single `StateFlow<UiState>`.
- One-shot user actions are ViewModel functions; effects (share intent launch, toasts) are a `Channel`-backed effects flow collected by the screen.
- Day status (`EMPTY / LOGGED / REPORTED / REPORTED_EDITED / OFF / HOLIDAY`) is **derived** in `:domain`, never stored.

## DI (Hilt)

`@HiltAndroidApp` on `DayLogApp`; modules in `di/`: `DatabaseModule` (Room, DAOs), `DataModule` (repositories), `SystemModule` (AlarmManager wrapper, clock). Receivers get dependencies via `@AndroidEntryPoint`/EntryPoint accessors. **Inject a `Clock`/`() -> LocalDate` provider everywhere time is read** — tests fix the clock; production uses system.

## Why not more modules

Single developer, one app: `:domain` (JVM-fast tests, purity firewall) + `:app` is the correct size. Feature modules/KMP would be structure without payoff; revisit only if an iOS/KMP port becomes real (then Room 3.x KMP is the migration path).
