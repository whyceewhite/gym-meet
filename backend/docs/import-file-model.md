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
| `<PREFIX>-SUMMARY-FINAL-<APP>.csv` | Combined standings on one apparatus for a round split into parts (US Championships' two-day apparatus titles) | Discipline + Age Category + Apparatus |
| `<PREFIX>-IMPORT.json` | Manifest: the event's metadata plus every file above that belongs to this discipline+age | Discipline + Age Category |

`<PREFIX>` is exactly the `Prefix` column of the event's `EVENT.csv`:
`<EVENT-SLUG>-<DISCIPLINE>-<AGE-CATEGORY>`, e.g.
`2026-EUROPEAN-CHAMPIONSHIPS-MAG-SR`. `<ROUND>` is one of `QUAL` / `EVENT` /
`TEAM` / `AA`, matching the `Round` enum in `data-model.md` exactly, and
`<APP>` is the two-letter `Apparatus` code.

Naming notes:

- `SCORES` files are per-athlete routine detail; `SUMMARY` files are the
  published roll-up (team or all-around standings, or multi-part combined
  standings), captured as published rather than computed.
- A summary's first segment is `QUAL` or `FINAL` ("the decisive standings"),
  so `SUMMARY-FINAL-TEAM`'s `Round` column is `TEAM` and
  `SUMMARY-FINAL-AA`'s is `AA` — the filename segment and the column differ on
  purpose.
- `SR` is both the Senior age code and the Still Rings apparatus code;
  position disambiguates (`…-MAG-SR-SCORES-QUAL-SR.csv` is Senior Rings).
- Older files in git history used `QUALS-<APP>`, `FINALS-<APP>`, `TEAM-<APP>`,
  `AA-<APP>`, `QUALS-TEAM`, `FINALS-TEAM` for what are now `SCORES-QUAL-`,
  `SCORES-EVENT-`, `SCORES-TEAM-`, `SCORES-AA-`, `SUMMARY-QUAL-TEAM` and
  `SUMMARY-FINAL-TEAM`.

Not every `<ROUND>`/file combination exists for every competition — a round
only gets a `SCORES`/`SUMMARY` file if the source PDF has an actual results
report for it, independent of what a schedule page might list
(`instructions-results-parsing.md` §4.4, §4.9).

## General conventions

These apply to every file below.

- **Empty means empty.** An empty cell for "not applicable / not printed" —
  never `N/A`, `-` or a placeholder `0`. A penalty that wasn't applied is
  blank, not `0`.
- **`Status` is always written** (`COMPLETED`, `DNS`, `INVALID_VAULT`), never
  blank to mean completed, because `Routine.status` is required. The one
  exception is `Status2` on vault, where blank means "no second attempt was
  expected".
- **Values are copied as printed**: `0.0` stays `0.0`, `5.70` stays `5.70`,
  `-0.10` keeps its sign, and a leading `!` on a D score (under inquiry) stays
  part of the value (on import it's stripped and sets
  `Routine.difficultyInquiry`). The exceptions are documented per column (a `+` sign on a
  bonus and tie markers on ranks are dropped; a vault bonus asterisk becomes
  `Combined Bonus`).
- **Ties**: duplicate `Rank` values are copied as printed; ranks are never
  recomputed.
- **Every file states its own context** through the metadata prefix below,
  including a `Round` column that is constant within most files.

## Self-describing metadata prefix

Every file below opens with a fixed run of metadata columns before its own
content, so the file is meaningful on its own without its filename intact.

| Column | Description | Entity Mapping |
|---|---|---|
| Event Name | The literal `Event.title` for this specific discipline+age sub-event (e.g. `37th Senior European Championships`) — not a coarser shared label. A single "European Championships" can produce several distinct `Event` rows (one per discipline+age), each with its own title, FIG event number, and date range; this column always matches the specific one this file belongs to. | `Event.title` |
| Discipline | `MAG` / `WAG`. Present in every file mainly to resolve which `Session` (or, for `PARTICIPANTS`, which `Athlete`) the row belongs to. | `Session.discipline` (also sets `Athlete.discipline` when importing `PARTICIPANTS`) |
| Age Category | `SR` / `JR` / `JR16` / `JR17`. `Event` itself has no age category field — a single `Event` row in this data set happens to be scoped to one age category, but that's convention, not a schema guarantee. | `Session.ageCategory` |
| Event Start Date | `Event.startDate`. Exists to disambiguate one year's edition from another, since `Event Name` alone doesn't carry a year. Present on `PARTICIPANTS`, `JUDGES`, `SUMMARY`. | `Event.startDate` |
| Session Start Date | The calendar date this specific `Round` (and, for `EVENT`, this specific apparatus; for a round split into parts, this part) was actually competed — sourced from the PDF's Competition Schedule pages, **never** from a page footer's report-generation timestamp (the two can disagree by a day or more). Present on `SCORES` only, in place of `Event Start Date`. Named "Start" because a large-field round can in principle span more than one calendar day. | `Session.date` |
| Part | Which part of a round split across several sessions this row belongs to (`1`, `2`, … — US Championships' Day 1 and Day 2). Blank for an ordinary single-session round, and on `SUMMARY` rows holding the combined standings of a split round (the whole-round session). Present on `SCORES` and `SUMMARY`, right after `Round`. | `Session.part` |

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
| Country | The IOC code of the country hosting the event (`GER`, `NED` — not ISO `DEU`, `NLD`) | `Event.countryCode` |
| Venue | The name of the venue where the event is hosted | `Event.venue` |
| FIG Event Number | The FIG number that identifies the event if a FIG associated competition | `Event.figEventNumber` |
| Apparatus Suppliers | The equipment manufacturer for this competition | `Event.apparatusSuppliers` |

## `PARTICIPANTS.csv`

One row per athlete entry.

| Column | Description | Entity Mapping |
|---|---|---|
| Order | Entry-list position, as printed | |
| NOC Code | Country code the participant is competing under, as printed — 3 characters, or 4 for FIG's numbered neutral-athlete codes (`AIN1`, `AIN2`). Some entry lists print it only on each country's first row; it applies to the rows below until the next one | `Participant.noc` |
| Bib Number | Assigned bib number to uniquely identify the participant in the competition | `Participant.bibNumber` |
| FIG License | FIG license number assigned to the gymnast | `Athlete.licenseNumber` |
| Name | Combined name exactly as printed — `SURNAME Given Name` in FIG sources, `Given Surname` in US Championships | |
| Surname | Participant's surname | `Athlete.surname` |
| Given Name | Participant's given name | `Athlete.givenName` |
| Birth Year | Participant's birth year | `Athlete.birthYear` (only when `Birth Date` is blank) |
| Birth Date | Participant's full birth date (`YYYY-MM-DD`), when the entry list publishes one (FIG World Championships does; European Championships prints only a year). Blank otherwise | `Athlete.birthDate` |
| Club | **US Championships only.** The athlete's gym/club as printed | `Participant.club` → `Club.name` (the club's country is the participant's NOC) |

## `JUDGES.csv`

One row per judge per apparatus panel per round — every round bundled into
one file per discipline+age.

| Column | Description | Entity Mapping |
|---|---|---|
| Round | The round of competition the judge is evaluating: [Round](../src/main/java/doubletuck/meet/domain/Round.java) | `Judge.round`  |
| Apparatus | The apparatus the judge is evaluating: [Apparatus](../src/main/java/doubletuck/meet/domain/Apparatus.java) | `Judge.apparatus` |
| Function | The function the judge is in when evaluating: [JudgeFunction](../src/main/java/doubletuck/meet/domain/JudgeFunction.java). Superior Jury roles are written as printed (`President`, `Vice-President`, `Member`) and resolve through `JudgeFunction.find`; reserve judges are `R` (single, unnumbered) or `R1`…`R6`; World Cup panels print `DS1` (Difficulty Supervisor) and, on beam, `pen5` — written as `T2` (mapping table: `instructions-results-parsing.md` §4.7) | `Judge.function` |
| Name | Combined name of judge as printed | |
| Surname | Judge's surname | `Judge.surname` |
| Given Name | Judge's given name | `Judge.givenName` |
| NOC Code | Federation the judge is credited to, as printed. Blank when none is printed (e.g. a Superior Jury member) | `Judge.nocCode` |

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
| Rank | ✓ | ✓ | ✓ | ✓ | Published apparatus placement. Duplicate values for ties are legitimate, copied as printed. Blank where the source prints no per-apparatus rank (e.g. the 2026 Worlds Team Final) | `Score.rank` |
| Bib | ✓ | ✓ | ✓ | ✓ | Identifies the athlete, joins to `PARTICIPANTS.csv` | *(join key to `Participant.bibNumber` — not re-stored on `Score`)* |
| Name | ✓ | ✓ | ✓ | ✓ | As printed on this report — cross-check against `PARTICIPANTS.csv` by Bib rather than trusting a possibly-truncated column here | *(not stored — verification only)* |
| NOC Code | ✓ | ✓ | ✓ | ✓ | | *(not stored — already on `Participant.noc`)* |
| Status | ✓ | ✓ | ✓ | ✓ | `COMPLETED`, `DNS`, or `INVALID_VAULT` — always written explicitly, never blank. Mirrors `Routine.status` being required | `Score.routine.status` |
| D | ✓ | ✓ | ✓ | ✓ | Difficulty score. A leading `!` marks it under inquiry | `Score.routine.difficulty`, `Score.routine.difficultyInquiry` |
| E | ✓ | ✓ | ✓ | ✓ | Execution score. Some sources print `E` before `D` (MAG PH and every WAG apparatus at the 2026 Europeans); the CSV always writes to these fixed column names regardless of source order | `Score.routine.execution` |
| B | ✓ | ✓ | ✓ | ✓ | Bonus. Populated for MAG (blank on every MAG PH row specifically — PH has no bonus). Present but always blank for WAG — kept for column alignment between disciplines rather than omitted | `Score.routine.bonus` |
| Penalty | ✓ | ✓ | ✓ | ✓ | Blank when no penalty applied — never `0` | `Score.routine.penalty` |
| SJ Penalty | ✓ | ✓ | ✓ | ✓ | Superior Jury Penalty, distinct from `Penalty`. Not observed with a non-blank value on any non-vault apparatus so far | `Score.routine.sjPenalty` |
| Total | ✓ | ✓ | ✓ | ✓ | Blank when `Status` is `DNS` | `Score.routine.score` (and `Score.totalScore`, which equals it for a single routine) |
| Qualification | ✓ | | | | `Q`/`R1`–`R4`/`NR`/blank, matching `QualificationResult` exactly. Only meaningful where there's a further round to qualify into. Where the qualification table prints no markers but the final's start list names the finalists and ordered reserves (2026 Cottbus World Cup), they're taken from the start list | `Score.qualificationResult` |
| Qualification Rank | ✓ | | | | Placement in the ranking that decides the apparatus final. On every non-vault apparatus that's the same ranking as `Rank`, so the value is always equal to `Rank` — written out anyway so every `QUAL` file pairs `Qualification` with its own rank, vault included | `Score.qualificationRank` |

### Vault, double-attempt shape (`QUAL` and `EVENT` rounds only)

Built by joining a single-vault listing (everyone, one row per bib) and a
double-vault listing (only those attempting a second vault) — see
`instructions-results-parsing.md` §4.5 for the join logic. In `QUAL`, the two
listings are two different rankings over the same attempts: `Rank` ranks the
whole field on the first vault (the one that counts for all-around and team);
`Qualification Rank` ranks only the two-vault entrants on their combined
result, which decides the Vault final.

| Column | QUAL | EVENT | Description | Entity Mapping |
|---|:-:|:-:|---|---|
| Round | ✓ | ✓ | | `Session.round` |
| Apparatus | ✓ | ✓ | | `Score.apparatus` |
| Rank | ✓ | ✓ | `QUAL`: from the single-vault list (whole field, ranked on vault 1). `EVENT`: the final's placement | `Score.rank` |
| Bib | ✓ | ✓ | | *(join key)* |
| Name | ✓ | ✓ | | *(not stored — verification only)* |
| NOC Code | ✓ | ✓ | | *(not stored)* |
| Status1 | ✓ | ✓ | Status of attempt 1: `COMPLETED`, `DNS`, or `INVALID_VAULT`. Always populated | `Score.routine.status` |
| D1 | ✓ | ✓ | | `Score.routine.difficulty`, `Score.routine.difficultyInquiry` |
| E1 | ✓ | ✓ | | `Score.routine.execution` |
| B1 | ✓ | ✓ | Bonus applied to attempt 1 itself. Populated for MAG, always blank for WAG so far | `Score.routine.bonus` |
| Penalty1 | ✓ | ✓ | | `Score.routine.penalty` |
| SJ Penalty1 | ✓ | ✓ | Superior Jury Penalty, attempt 1 — distinct from the ordinary `Penalty1`. Seen for WAG at the 2026 Europeans | `Score.routine.sjPenalty` |
| Total1 | ✓ | ✓ | | `Score.routine.score` |
| Status2 | ✓ | ✓ | Status of attempt 2, **four-state**: blank = no second attempt was expected at all (`Score.secondRoutine` is null); `COMPLETED`; `DNS` = a second attempt was expected (on the double-vault list) but not completed; `INVALID_VAULT` | `Score.secondRoutine.status` |
| D2 | ✓ | ✓ | | `Score.secondRoutine.difficulty`, `Score.secondRoutine.difficultyInquiry` |
| E2 | ✓ | ✓ | | `Score.secondRoutine.execution` |
| B2 | ✓ | ✓ | Bonus applied to attempt 2 itself. Same MAG/WAG split as `B1` | `Score.secondRoutine.bonus` |
| Penalty2 | ✓ | ✓ | | `Score.secondRoutine.penalty` |
| SJ Penalty2 | ✓ | ✓ | Superior Jury Penalty, attempt 2 | `Score.secondRoutine.sjPenalty` |
| Total2 | ✓ | ✓ | Blank whenever `Status2` is blank or `DNS` | `Score.secondRoutine.score` |
| Combined Bonus | ✓ | ✓ | A bonus applied to the *combined* two-vault result, not either attempt — distinct from `B1`/`B2`. Some sources print it as a `*` on the combined `Total` (WAG's +0.2), others as its own `Vault Bonus` column; either way it lands here as a number or blank, with `Total` holding the clean number. Present for MAG too (always blank so far) for column alignment | `Score.combinedBonus` |
| Total | ✓ | ✓ | The result the Vault final is decided on — `Total1` alone for a single-vault athlete, the published average plus `Combined Bonus` for a completed two-vault athlete, blank when there's no valid combined result | `Score.totalScore` |
| Qualification | ✓ | | Only ever set from the double-vault list; blank for single-vault-only athletes | `Score.qualificationResult` |
| Qualification Rank | ✓ | | Rank **within the double-vault list** — distinct from `Rank`. Blank for single-vault-only athletes | `Score.qualificationRank` |

### Vault, single-attempt shape (`TEAM` and `AA` rounds)

Team Final and All-Around Final vault routines are performed once, not
twice — same columns and same Entity Mapping as the non-vault apparatus
table above (`Status`→`Score.routine.status`, `D`→`Score.routine.difficulty`,
etc.), **not** the `1`/`2` split. Don't assume "Vault" always means the
double-attempt shape — check the actual page: US Championships' `AA` vault
uses the **double-attempt** shape (without `Qualification` columns) for the
levels that hold a two-vault vault event, since the same session decides the
vault title.

### Bonus breakdown columns (US Championships)

USA Gymnastics MAG scoring itemizes bonuses that FIG folds into one `B`.
Those files carry the standard columns (`B` is the sum of the parts) plus
the itemized parts as trailing columns, so nothing printed is lost. Absent
from FIG files.

| Column | Description | Entity Mapping |
|---|---|---|
| D+ | Difficulty bonus | `Score.routine.bonusDetail` entry, description "Difficulty bonus" |
| E+ | Execution bonus (printed `0.0` on every routine in 2026) | `Score.routine.bonusDetail` entry, description "Execution bonus" |
| S+ | Stick bonus | `Score.routine.bonusDetail` entry, description "Stick bonus" |

On the double-attempt vault shape they're `D+1`/`E+1`/`S+1` and
`D+2`/`E+2`/`S+2`, mapping to `routine` and `secondRoutine` respectively.
`E+2` is always blank: the second-vault listings don't print it. US `ND`
(neutral deduction) is written to the standard `Penalty` column. WAG files
carry the breakdown columns too, always blank.

## `SUMMARY-<ROUND>-{TEAM,AA}.csv`

Top-line standings only — one row per NOC (Team) or per athlete
(All-Around) — not the detailed per-athlete/per-apparatus scores feeding
into it (those live in `SCORES-<ROUND>-<APP>.csv` instead). Not every round
has both files for every age category — a `SUMMARY-FINAL-TEAM`/
`SUMMARY-FINAL-AA` file only exists where the source PDF has an actual
separate Final report for it (see "File families" above; the first
`SUMMARY-FINAL-AA` files come from the 2026 World Championships).

For a round split into parts (US Championships), `SUMMARY-FINAL-AA` holds a
row per athlete per part (`Part` = 1, 2) plus a row per athlete with `Part`
blank for the combined standings — the result the title is decided on. The
combined rows leave the `<APP> Total` columns blank (the source prints only
the combined grand total), and a part's `Rank` is blank where the source
publishes no per-part placement.

| Column | Description | Entity Mapping |
|---|---|---|
| Round | The round this summary is from: [Round](../src/main/java/doubletuck/meet/domain/Round.java). Note: `SUMMARY-FINAL-TEAM`'s filename segment is `FINAL` while this column's value is `TEAM` | `Session.round` |
| Part | See the metadata prefix. Blank on combined rows of a split round | `Session.part` (blank → the whole-round session) |
| Rank | Placement in the standings | `TeamResult.rank` / `AllAroundResult.rank` |
| Bib | Identifies the athlete. **All-Around summary only** | |
| Name | Athlete name as printed. **All-Around summary only** | |
| NOC Code | Country/federation the row represents | `TeamResult.noc` |
| `<APP> Total` (one column per apparatus) | Already the sum/drop-lowest total printed by the source, not recomputed. Apparatus set and column order is discipline-specific: `FX, PH, SR, VT, PB, HB` for MAG, `FX, BB, UB, VT` for WAG | |
| SJ Penalty | Superior Jury penalty applied to the row as a whole, distinct from an individual routine's `Penalty` | |
| Total | Grand total | `TeamResult.totalScore` / `AllAroundResult.totalScore` |
| Qualification | [QualificationResult](../src/main/java/doubletuck/meet/domain/QualificationResult.java). Omitted entirely from a `FINAL` summary — no further round to qualify into. The 2026 Worlds All-Around qualification also uses `R4` | `TeamResult.qualificationResult` / `AllAroundResult.qualificationResult` |

## `SUMMARY-FINAL-<APP>.csv`

Combined standings on one apparatus for a round split into parts — US
Championships' two-day apparatus titles. One row per athlete in the source's
two-day apparatus report, which lists only athletes who competed on that
apparatus on both days; for vault, only the two-vault entrants who contested
the vault title. All rows belong to the whole-round session, so `Part` is
always blank.

| Column | Description | Entity Mapping |
|---|---|---|
| Event Name, Discipline, Age Category, Event Start Date, Round, Part | Metadata prefix | `Session` (whole-round: `part` null) |
| Apparatus | Two-letter code | `ApparatusResult.apparatus` |
| Rank | Combined placement, as published | `ApparatusResult.rank` |
| Bib | Joins to `PARTICIPANTS.csv` | *(join key)* |
| Name | As printed | *(not stored — verification only)* |
| NOC Code | | *(not stored)* |
| Part 1 Total, Part 2 Total | Each part's result as printed in the combined report — for vault, the two-vault result. Cross-check against that part's `SCORES` row `Total` | *(not stored — already `Score.totalScore` on each part)* |
| Total | Combined total | `ApparatusResult.totalScore` |
