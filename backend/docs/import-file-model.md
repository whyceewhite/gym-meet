# Import File Model

Reference for the CSV files produced by parsing an official artistic gymnastics results PDF into a shape ready for import into this
application's database.

Every table below has three columns: **Column** (the CSV header), **Description**
(what it represents), and **Entity Mapping** (the domain attribute it maps
to). Entity Mapping is left blank where no attribute exists yet — that's a
gap to close before that column can be imported as more than a
cross-check/reference value, not an oversight in this document.

## File families

| File | Scope | One file per |
|---|---|---|
| `EVENT.csv` | The competition(s) itself | One row per discipline+age sub-event |
| `<PREFIX>-PARTICIPANTS.csv` | Entry list | Discipline + Age Category |
| `<PREFIX>-JUDGES.csv` | Judging panels, all rounds | Discipline + Age Category |
| `<PREFIX>-SCORES-<ROUND>-<APP>.csv` | Individual results, one apparatus | Discipline + Age Category + Round + Apparatus |
| `<PREFIX>-SUMMARY-<ROUND>-{TEAM,AA}.csv` | Top-line team/all-around standings | Discipline + Age Category + Round |

`<PREFIX>` is `<EVENT-SLUG>-<DISCIPLINE>-<AGE-CATEGORY>`, e.g.
`2026-EUROPEAN-CHAMPIONSHIPS-MAG-SR`. `<ROUND>` is one of `QUAL` / `EVENT` /
`TEAM` / `AA`, matching the `Round` enum in `data-model.md` exactly — see
`instructions-results-parsing.md` §5.1 for the full naming rationale
(including why `SUMMARY-FINAL-TEAM`'s filename segment is `FINAL` while its
`Round` column value is `TEAM`).

Not every `<ROUND>`/file combination exists for every competition — a round
only gets a `SCORES`/`SUMMARY` file if the source PDF has an actual results
report for it, independent of what a schedule page might list. See
`instructions-results-parsing.md` §7.10 for how to tell "Qualification
directly decides this result" apart from "Qualification only qualifies into
a separate Final" — the deciding factor is always whether a distinct results
report exists, never the schedule's session labels.

## Self-describing metadata prefix

Every file below opens with a fixed run of metadata columns before its own
content, so the file is meaningful on its own without its filename intact.

| Column | Description | Entity Mapping |
|---|---|---|
| Event Name | The literal `Event.title` for this specific discipline+age sub-event (e.g. `37th Senior European Championships`) — not a coarser shared label. A single "European Championships" can produce several distinct `Event` rows (one per discipline+age), each with its own title, FIG event number, and date range; this column always matches the specific one this file belongs to. | `Event.title` |
| Discipline | `MAG` / `WAG`. Present in every file mainly to resolve which `Session` (or, for `PARTICIPANTS`, which `Athlete`) the row belongs to. | `Session.discipline` (also sets `Athlete.discipline` when importing `PARTICIPANTS`) |
| Age Category | `SR` / `JR` / `JR16` / `JR17`. `Event` itself has no age category field — a single `Event` row in this data set happens to be scoped to one age category, but that's convention, not a schema guarantee. | `Session.ageCategory` |
| Event Start Date | `Event.startDate`. Exists to disambiguate one year's edition from another, since `Event Name` alone doesn't carry a year. Present on `PARTICIPANTS`, `JUDGES`, `SUMMARY`. | `Event.startDate` |
| Session Start Date | The calendar date this specific `Round` (and, for `EVENT`, this specific apparatus) was actually competed — sourced from the PDF's Competition Schedule pages, **never** from a page footer's report-generation timestamp (the two can disagree by a day or more). Present on `SCORES` only, in place of `Event Start Date`. Named "Start" because a large-field round can in principle span more than one calendar day, even though every round in the current data set is single-day. | `Session.date` |

## `EVENT.csv`

One row per discipline+age sub-event making up the competition.

| Column | Description | Entity Mapping |
|---|---|---|
| Prefix | The `<PREFIX>` used to name every other file for this sub-event | |
| Title | The sub-event's official title | `Event.title` |
| Discipline | The discipline of this sub-event covers | `Event.discipline` |
| Age Category | Age category of this sub-event: [AgeCategory](../src/main/java/doubletuck/meet/domain/AgeCategory.java) | |
| Level | The competition's tier: [EventLevel](../src/main/java/doubletuck/meet/domain/EventLevel.java) | `Event.level` |
| Start Date | The first day of competition of the event | `Event.startDate` |
| End Date | The final day of competition of the event | `Event.endDate` |
| City | The city where the event is hosted | `Event.city` |
| Country | The country code where the event is hosted | `Event.countryCode` |
| Venue | The name of the venue where the event is hosted | `Event.venue` |
| FIG Event Number | The FIG number that identifies the event if a FIG associated competition | `Event.figEventNumber` |
| Apparatus Suppliers | The equipment manufacturer for this competition | |

## `PARTICIPANTS.csv`

One row per athlete entry.

| Column | Description | Entity Mapping |
|---|---|---|
| Order | Entry-list position, as printed | |
| NOC Code | Country code the participant is competing under | `Participant.noc` |
| Bib Number | Assigned bib number to uniquely identify the participant in the competition | `Participant.bibNumber` |
| FIG License | FIG license number assigned to the gymnast | `Athlete.licenseNumber` |
| Name | Combined `SURNAME Given Name`, exactly as printed | |
| Surname | Participant's surname | `Athlete.surname` |
| Given Name | Participant's given name | `Athlete.givenName` |
| Birth Year | Participant's birth year | `Athlete.birthYear` |

## `JUDGES.csv`

One row per judge per apparatus panel per round — every round bundled into
one file per discipline+age.

| Column | Description | Entity Mapping |
|---|---|---|
| Round | The round of competition the judge is evaluating: [Round](../src/main/java/doubletuck/meet/domain/Round.java) | `Judge.round`  |
| Apparatus | The apparatus the judge is evaluating: [Apparatus](../src/main/java/doubletuck/meet/domain/Apparatus.java) | `Judge.apparatus` |
| Function | The function the judge is in when evaluating: [JudgeFunction](../src/main/java/doubletuck/meet/domain/JudgeFunction.java) | `Judge.function` |
| Name | Combined name of judge as printed | |
| Surname | Judge's surname | `Judge.surname` |
| Given Name | Judge's given name | `Judge.givenName` |
| NOC Code | Country that that the judge is representing | `Judge.nocCode` |

## `SCORES-<ROUND>-<APP>.csv`

One row per athlete's result on one apparatus per round. Column shape
depends on two independent things: whether the apparatus is Vault (a
double-attempt structure everywhere except Team/AA finals) and which round
it is (Qualification carries extra qualification-tracking columns that
Finals don't need). The `QUAL`/`EVENT`/`TEAM`/`AA` columns below mark which
round(s) a column actually appears in.

### Non-vault apparatus (`FX`, `PH`, `SR`, `PB`, `HB` for MAG; `FX`, `BB`, `UB` for WAG)

| Column | QUAL | EVENT | TEAM | AA | Description | Entity Mapping |
|---|:-:|:-:|:-:|:-:|---|---|
| Round | ✓ | ✓ | ✓ | ✓ | Which round this result is from | `Session.round` |
| Apparatus | ✓ | ✓ | ✓ | ✓ | Two-letter code | `Score.apparatus` (also `Session.apparatus` for `EVENT` round specifically) |
| Rank | ✓ | ✓ | ✓ | ✓ | Duplicate values for ties are legitimate, copied as printed | `Score.rank` |
| Bib | ✓ | ✓ | ✓ | ✓ | Identifies the athlete, joins to `PARTICIPANTS.csv` | *(join key to `Participant.bibNumber` — not re-stored on `Score`)* |
| Name | ✓ | ✓ | ✓ | ✓ | As printed on this report — cross-check against `PARTICIPANTS.csv` by Bib rather than trusting a possibly-truncated column here | *(not stored — verification only)* |
| NOC Code | ✓ | ✓ | ✓ | ✓ | | *(not stored — already on `Participant.noc`)* |
| Status | ✓ | ✓ | ✓ | ✓ | `COMPLETED`, `DNS`, or `INVALID_VAULT` — always written explicitly, never blank. Mirrors `Score.status` being `NOT NULL` | `Score.status` |
| D | ✓ | ✓ | ✓ | ✓ | Difficulty score | `Score.difficulty` |
| E | ✓ | ✓ | ✓ | ✓ | Execution score. MAG PH and every WAG apparatus print `E` before `D` in the source; the CSV always writes to these fixed column names regardless of source order | `Score.execution` |
| B | ✓ | ✓ | ✓ | ✓ | Bonus. **MAG only** — WAG's `FX`/`BB`/`UB` have no bonus concept and omit this column entirely (not just leave it blank). Blank on every MAG PH row (PH has no bonus, but the column is still present for header consistency across MAG apparatus) | `Score.bonus` |
| Penalty | ✓ | ✓ | ✓ | ✓ | Blank when no penalty applied — never `0` | `Score.penalty` |
| Total | ✓ | ✓ | ✓ | ✓ | Blank when `Status` is `DNS` | `Score.score` |
| Qualification | ✓ | | | | `Q`/`R1`/`R2`/`R3`/`NR`/blank, matching `QualificationResult` exactly. Only meaningful where there's a further round to qualify into | `Score.qualificationResult` |

### Vault, double-attempt shape (`QUAL` and `EVENT` rounds only)

Built by joining a single-vault listing (everyone, one row per bib) and a
double-vault listing (only those attempting a second vault) — see
`instructions-results-parsing.md` §4 for the join logic.

| Column | QUAL | EVENT | Description | Entity Mapping |
|---|:-:|:-:|---|---|
| Round | ✓ | ✓ | | `Session.round` |
| Apparatus | ✓ | ✓ | | `Score.apparatus` |
| Rank | ✓ | ✓ | Always comes from the single-vault list | `Score.rank` |
| Bib | ✓ | ✓ | | *(join key)* |
| Name | ✓ | ✓ | | *(not stored — verification only)* |
| NOC Code | ✓ | ✓ | | *(not stored)* |
| Status1 | ✓ | ✓ | Status of attempt 1: `COMPLETED`, `DNS`, or `INVALID_VAULT`. Always populated | `Score.status` |
| D1 | ✓ | ✓ | | `Score.difficulty` |
| E1 | ✓ | ✓ | | `Score.execution` |
| B1 | ✓ | ✓ | **MAG only** | `Score.bonus` |
| Penalty1 | ✓ | ✓ | | `Score.penalty` |
| SJ Penalty1 | ✓ | ✓ | **WAG only** — Superior Jury Penalty, distinct from the ordinary `Penalty1` | *(no `Score` field for a Superior Jury penalty yet — a real gap, not just an unmapped reference column)* |
| Total1 | ✓ | ✓ | | `Score.score` |
| Status2 | ✓ | ✓ | Status of attempt 2, **four-state**: blank = no second attempt was expected at all (maps to `Score.secondStatus` being null — genuinely different from the other three, not an omission); `COMPLETED`; `DNS` = a second attempt was expected (on the double-vault list) but not completed; `INVALID_VAULT` | `Score.secondStatus` |
| D2 | ✓ | ✓ | | `Score.secondDifficulty` |
| E2 | ✓ | ✓ | | `Score.secondExecution` |
| B2 | ✓ | ✓ | **MAG only** | `Score.secondBonus` |
| Penalty2 | ✓ | ✓ | | `Score.secondPenalty` |
| SJ Penalty2 | ✓ | ✓ | **WAG only** | *(same gap as `SJ Penalty1`)* |
| Total2 | ✓ | ✓ | Blank whenever `Status2` is blank or `DNS` | `Score.secondScore` |
| Bonus | ✓ | ✓ | **WAG only.** The source prints this as a `*` on the combined `Total` (worth +0.2), decoded here into its own column (`0.2` or blank) with `Total` holding the clean number | *(doesn't cleanly fit `Score.bonus`/`secondBonus`, both of which are per-attempt — this applies to the combined result. Needs a decision, not yet mapped)* |
| Total | ✓ | ✓ | The athlete's official counting result — `Total1` alone for a single-vault athlete, the published average for a completed two-vault athlete, blank when there's no valid combined result | `Score.totalScore` |
| Qualification | ✓ | | Only ever set from the double-vault list; blank for single-vault-only athletes | `Score.qualificationResult` |
| Qualification Rank | ✓ | | Rank **within the double-vault list specifically** — distinct from `Rank`. Blank for single-vault-only athletes | *(`Score.rank` is single-valued and already used for `Rank` above — see `instructions-results-parsing.md` §6 item 4 for the open question of whether this is worth its own field)* |

### Vault, single-attempt shape (`TEAM` and `AA` rounds)

Team Final and All-Around Final vault routines are performed once, not
twice — same columns and same Entity Mapping as the non-vault apparatus
table above (`Status`→`Score.status`, `D`→`Score.difficulty`, etc.), **not**
the `1`/`2` split. Don't assume "Vault" always means the double-attempt
shape — check the actual page.

## `SUMMARY-<ROUND>-TEAM.csv`

Top-line team standings only — one row per NOC, not the per-athlete scores
feeding into it (those live in `SCORES-TEAM-<APP>.csv` instead).

| Column | QUAL | FINAL | Description | Entity Mapping |
|---|:-:|:-:|---|---|
| Round | ✓ | ✓ | `QUAL` or `TEAM` (not `FINAL` — the filename's `<ROUND>` segment and the `Round` column intentionally differ for this one file; see naming doc) | `Session.round` |
| Rank | ✓ | ✓ | | `TeamResult.rank` |
| NOC Code | ✓ | ✓ | The source also prints a full federation name, not captured here — `NOC Code` alone is enough to join back to `PARTICIPANTS.csv`/`Athlete.country` | `TeamResult.noc` |
| `<APP> Total` (one column per apparatus) | ✓ | ✓ | Already the sum/drop-lowest total printed by the source, not recomputed. Apparatus set and column order is discipline-specific: `FX, PH, SR, VT, PB, HB` for MAG, `FX, BB, UB, VT` for WAG | *(`TeamResult` has no per-apparatus fields — only a grand `totalScore`. This breakdown isn't stored anywhere; it would need to be derived from `SCORES-TEAM-<APP>.csv` via `Score.countsTowardTeamResult`, not imported directly onto `TeamResult`)* |
| SJ Penalty | ✓ | ✓ | Superior Jury penalty applied to the team as a whole, distinct from an individual routine's `Penalty`. Unverified against a real non-blank example so far | *(no `TeamResult` field for a team-level penalty)* |
| Total | ✓ | ✓ | Team's grand total | `TeamResult.totalScore` |
| Qualification | ✓ | | `Q`/`R1`/`R2`/`R3`/`NR`/blank. **Omitted entirely from `FINAL`** — no further round to qualify into once the Team Final is done. If this column ever shows up, always blank, on a `FINAL` file, that's a bug to fix, not a value to import | `TeamResult.qualificationResult` |

Not every age category has both rounds — a `SUMMARY-FINAL-TEAM` file only
exists where the source PDF has an actual separate Team Final report (see
"File families" above).

## `SUMMARY-QUAL-AA.csv`

Top-line all-around standings, Qualification round. (No `SUMMARY-FINAL-AA`
variant exists in the data parsed so far — build one the same way if a
future competition's source PDF has a genuine separate All-Around Final
report.)

| Column | Description | Entity Mapping |
|---|---|---|
| Round | | `Session.round` |
| Rank | | `AllAroundResult.rank` |
| Bib | Identifies the athlete, joins to `PARTICIPANTS.csv` | *(join key to `Participant.bibNumber`)* |
| Name | As printed on this report — cross-check against `PARTICIPANTS.csv` by Bib | *(not stored — verification only)* |
| NOC Code | | *(not stored — already on `Participant.noc`)* |
| `<APP> Total` (one column per apparatus) | Same apparatus set/order convention as `SUMMARY-<ROUND>-TEAM.csv` | *(same gap as `SUMMARY-<ROUND>-TEAM.csv` — `AllAroundResult` has only a grand `totalScore`, no per-apparatus fields)* |
| SJ Penalty | | *(no `AllAroundResult` field)* |
| Total | Athlete's grand total | `AllAroundResult.totalScore` |
| Qualification | `Q`/`R1`/`R2`/`R3`/`NR`/blank — only meaningful if a further AA round exists to qualify into | `AllAroundResult.qualificationResult` |
