# Instructions: Parsing Official Results PDFs into CSV

This document is a working reference for turning an official FIG/meet-organizer
results PDF into the CSV files under `data/<year>/<event>/`. It captures the
mechanical process (tools, gotchas, sanity checks) and the CSV column standard
those files should follow so they can later be imported against the schema in
[`data-model.md`](data-model.md).

It was written after manually parsing the 2026 European Championships MAG
results PDF (entry lists, judges assignments, and per-apparatus qualification
results). Treat it as a living document — update it when the next PDF breaks
one of these assumptions.

## 1. Tools

- **poppler-utils** (`brew install poppler`) — provides `pdftotext` and
  `pdftoppm`. This is the whole toolchain; no PDF library code is needed.
- `pdftotext -layout <file>.pdf out.txt` — **always use `-layout`**. It
  preserves column alignment as whitespace, which the parsing approach below
  depends on. Without it, table structure is lost.
- `pdftoppm -f <n> -l <n> -r 150 -png <file>.pdf <prefix>` — renders one
  physical page to a PNG. Needed whenever a column's meaning isn't fully
  captured as text (see §3.4).

## 2. Finding the right section of the PDF

These results PDFs are really a long concatenation of many small,
independently-generated reports (entry lists, judge panels, per-apparatus
results, team compositions, etc.), each with its own footer stamp like:

```
GARM1APFX-------------QUAL--------_C73I 1.0        Wed, 19 Aug 2026 21:26:04   Page 1 / 5
```

That footer string is usually a **stable, unique identifier** for one
report — use it, not eyeballed page numbers, to locate content. It is not
*always* unique, though — see rule 5 below before assuming a single grep
hit is the whole story. Rules:

1. Run `pdftotext -layout` once on the whole PDF and split the result on the
   form-feed character (`\f`) to get a physical-page-indexed list
   (`pages[i]` = physical page `i+1`). This mapping is required input to
   `pdftoppm -f/-l`, which wants physical page numbers, not the "Page X / Y"
   logical numbering printed in the footer.
2. `grep -n "<the exact footer string>"` (or search the split `pages[]` list)
   to find every physical page belonging to that report. Multi-page reports
   repeat the same footer string on every page (`Page 1/5`, `Page 2/5`, ...).
3. **A page's footer describes the content *above* it, not below.** After
   `pdftotext -layout`, a page's own footer is its last line before the next
   page's title block starts. Don't assume a section starts right after a
   footer line you grepped for elsewhere — confirm by reading the page.
4. Report boundaries don't always match intuition. In this PDF, "Results By
   Apparatus" (the per-apparatus results table) is a *separate* report/footer
   from "Judges Assignment" and "Entry List By Bib" even when they're on
   physically adjacent pages of the same round. Always check where a footer
   string changes rather than assuming a whole logical section (e.g. "all of
   Qualification/SENIORS") shares one footer.
5. **The same footer code can legitimately appear more than once, for
   genuinely different reports.** Found parsing Junior Men: the Apparatus
   Final and All-Around Final "Results By Apparatus" pages for FX, PH, SR,
   and HB all use the *identical* footer code (e.g.
   `GARM1APFX---JNR-------FNL---------_C73I`) — it's a report-template ID,
   not a per-generation unique key. Two things disambiguate them: the
   **date/time stamp** printed next to "Page X / Y" (each generation gets
   its own timestamp even when the code is reused), and — more
   reliably — the **round title actually printed on the page**
   ("Apparatus Final" vs. "All-Around Final"). Never trust the footer code
   alone to mean "one report": grep it, then check how many distinct
   physical-page groups it maps to and read each one's title before
   assuming they're the same data. (VT and PB happened to get distinct
   codes for their two Junior reports in this PDF — don't count on that
   holding for every apparatus; check each one.)

## 3. Parsing the table itself

### 3.1 Read the header row first, every time

Column presence and order are **not** consistent across apparatus, even
within the same report type. Observed so far:

| Apparatus | Column order |
|---|---|
| FX, SR, PB, HB, VT (single-vault list) | Rank, Bib, Name, NOC Code, **D, E, B**, Penalty, Total |
| PH | Rank, Bib, Name, NOC Code, **E, D**, Penalty, Total — **no Bonus column at all** |
| VT (two-vault list) | Rank, Bib, Name, NOC, Vault(#), E, D, B, Pen, "Vault Score" (per-attempt total), Total (overall average) |

Parsing PH with the FX column mapping silently produces garbage (this
happened once — see §3.5). **Print and read the literal header line before
writing the parser for a new apparatus/report type.**

### 3.2 Tokenizing a fixed-width row

Primary tool: `re.split(r'\s{2,}', line.strip())`. Two-or-more spaces
separate columns; a single space only ever occurs inside a multi-word value
(a name like `IANEZ RAMOS Miguel Angel`).

**Gotcha — trailing one-character markers break this.** The qualification
marker (§5.5) is right-aligned in its own column, so a one-character marker
(`Q`) ends up with *more* leading spaces than a two-character marker (`R1`,
`NR`):

```
...    14.833    Q      <- 4 spaces before Q
...    13.833 R1        <- 1 space before R1, fuses with Total on a naive split
```

Fix: strip the marker with an anchored regex **before** tokenizing the rest
of the row:

```python
MARKER_RE = re.compile(r'\s+(Q|R1|R2|R3|NR)\s*$')
```

**Gotcha — never trust a fixed character-column offset copied from one row
and applied to another.** These PDFs are not monospace-rendered; column
start positions drift by a character or two row-to-row. Whitespace
tokenizing is robust to this; slicing by a fixed `line[a:b]` computed from
the header is not (confirmed by trying it — it silently misaligned columns).

### 3.3 Incomplete rows (DNS, DNF, etc.)

Athletes who didn't start, or (vault-only) didn't finish the two-vault
protocol, produce rows with fewer populated fields than a normal result row.
**Detect these by token count, not by trying to force them through the
normal column mapping.** For example, a full DNS row on a 5-column apparatus
table tokenizes to just `[Bib, Name, NOC, E-value-of-0.000, 'DNS']` — Rank,
D, B, Penalty are simply absent, and the literal `0.000` in the E slot is a
placeholder, not a real score (clear it back to blank — see §5.5).

### 3.4 When a column is an icon, not text

The Judges Assignment tables show which apparatus each judging panel covers
as an **icon**, not text — `pdftotext` produces nothing for that column.
Whenever a table layout is ambiguous or a column appears to carry no text
value, render the page with `pdftoppm` and look at it before guessing. In
this PDF the apparatus-icon order was consistently Floor Exercise, Pommel
Horse (top row) / Rings, Vault (middle row) / Parallel Bars, Horizontal Bar
(bottom row) across every Judges Assignment page checked — but that
consistency was verified by rendering every single page as an image, not
assumed after the first one.

### 3.5 Always cross-validate row counts

Before trusting a parse, independently verify it against the source text:

- Count expected data rows with a regex independent of your parser, e.g.
  `^\s*(\d+)\s+\d+\s+\S` for ranked rows, and compare to `max(rank)`.
- Count DNS/marker occurrences (`grep -c DNS`, etc.) and confirm your parsed
  output has exactly that many.
- A parser that silently drops rows is worse than one that errors — prefer
  asserting/printing on any row shape you don't recognize rather than
  skipping it quietly.

This check caught a real bug in this session: a first pass at Pommel Horse
(which has a different column order, per §3.1) produced 2 rows instead of
125 — the mismatch was invisible without an independent count.

### 3.6 Name truncation isn't just a Vault thing

§4 already covers the narrow Name column on the two-vault listing. The same
failure mode shows up anywhere a name is unusually long relative to a fixed
column width — found in a Junior Qualification table (a normal-width
apparatus results table, not Vault) where `BORDELAIS-MAISONNABLE Ruddly`
printed as just `BORDELAIS-MAISONNABLE`, given name silently dropped,
because the 22-character surname alone nearly filled the column. This
wasn't a one-off: it recurred on four different apparatus tables for the
same athlete. **Don't rely on "does this table have a narrow Name column"
as the trigger for checking** — instead, after parsing any results file,
diff every `(Bib, Name)` pair against `PARTICIPANTS.csv` (the master name
source, §5.10) and fix any mismatch from there, regardless of which report
or column width it came from. A single-word-name heuristic isn't
sufficient either — a truncated multi-word surname (e.g. `VAN DER AA`
losing its given name) would still look like a plausible multi-word value
and pass a naive "is this suspiciously short" check.

## 4. Vault: two separate listings

Vault is the one apparatus where the PDF publishes **two independent
tables** for the same round:

1. **Single-vault list** — every competitor's *first* (counting) vault only.
   This is the vault score that counts toward all-around/team results, and
   it ranks the *entire field*, whether or not they attempted a second
   vault.
2. **Double-vault list** — only the subset of athletes who opted to perform
   a second vault to be considered for the Vault apparatus final. It gives
   both attempts, their average ("Total" on that list), a *separate* rank
   among just this subset, and the Q/R1/R2/R3/NR marker (which is decided
   here, not on the single-vault list — the single list carries no marker at
   all).

**Join the two by Bib number.** Every double-vault Bib should also appear on
the single-vault list; treat a Bib on the double list with no single-list
match as a parse error, not a data oddity.

**Names can be truncated on the double-vault list** (narrower column —
e.g. `SPONEVIK` with no given name, where the single list has
`SPONEVIK Sebastian`). Always take Name/NOC from the single-vault list as
authoritative; use the double list only for the Vault-2 figures, the overall
average, and the qualification marker/rank.

**A DNF case**: an athlete can complete vault 1 but not vault 2 (injury,
withdrawal). The source shows this as `DNF` in the double-list's overall-total
position and `DNS` in the Vault-2 total position, with Vault-2's D/E/Penalty
blank. See §5.5 for how this is represented in the CSV.

## 5. CSV standard

### 5.1 File naming

Score-bearing files use two patterns, both anchored on the same event
prefix:

```
<EVENT-PREFIX>-SCORES-<ROUND>-<APPARATUS>.csv
<EVENT-PREFIX>-SUMMARY-<ROUND>-<TEAM-OR-AA>.csv
```

`<EVENT-PREFIX>` is exactly the `Prefix` column value in
`data/<year>/<event>/<EVENT>-EVENT.csv` (e.g.
`2026-EUROPEAN-CHAMPIONSHIPS-MAG-SR`) — i.e.
`<YEAR>-<EVENT-NAME>-<DISCIPLINE>-<AGE-LEVEL>`, where `DISCIPLINE` ∈
`MAG`/`WAG` and `AGE-LEVEL` ∈ `SR`/`JR`/`JR16`/`JR17` (matching the
`Discipline`/`AgeCategory` enums in `data-model.md`).

`SCORES` is per-athlete (or per-team-on-one-apparatus) detail — one row per
competitor. `SUMMARY` is a rolled-up, one-row-per-team-or-athlete total —
derivable from the `SCORES` files in principle, but published as its own
table in the source PDF, so captured as its own file rather than computed.
This split replaces an earlier scheme (still visible in git history) that
folded both into one ambiguous first segment — see the note at the end of
this section if you're working from an older mental model of the naming.

`<ROUND>` is one of:

| Round token | Meaning |
|---|---|
| `QUAL` | Qualification |
| `EVENT` | Apparatus Final (also called "Event Finals") |
| `AA` | All-Around Final |
| `TEAM` | Team Final |

`<APPARATUS>` is the two-letter `Apparatus` enum code from `data-model.md`:
`FX`, `PH`, `SR` (Still Rings), `PB`, `HB`, `VT` (MAG); `UB`, `BB` (WAG,
plus shared `FX`/`VT`). `<TEAM-OR-AA>` (summary files only) is the literal
word `TEAM` or `AA`, naming which roll-up the row belongs to.

Full suffix reference:

| Suffix | Contents | One file covers |
|---|---|---|
| `PARTICIPANTS` | Entry list (one row per athlete) | Every round, all apparatus |
| `JUDGES` | Judges Assignment (one row per judge per panel) | Every round in one file, distinguished by a `Round` column |
| `SCORES-QUAL-<APP>` | Results By Apparatus, Qualification round | One apparatus, one round |
| `SCORES-EVENT-<APP>` | Results By Apparatus, (individual) Apparatus Final round | One apparatus, one round |
| `SCORES-AA-<APP>` | Results By Apparatus, All-Around Final round | One apparatus, one round |
| `SCORES-TEAM-<APP>` | Results By Apparatus, Team Final round (per-athlete detail feeding the team score) | One apparatus, one round |
| `SUMMARY-QUAL-TEAM` | Team Results, top-line team totals only, Qualification round | Every team, one round |
| `SUMMARY-FINAL-TEAM` | Team Final Results, top-line team totals only, Team Final round | Every qualified team, one round |
| `SUMMARY-QUAL-AA` / `SUMMARY-FINAL-AA` | All-Around Results, top-line athlete totals | Every athlete, one round (for a round split into parts: each part plus the combined standings, §9) |
| `SUMMARY-FINAL-<APP>` | Combined standings on one apparatus for a round split into parts (§9) | One apparatus |
| `IMPORT.json` | Manifest of the event metadata and every file above for one discipline+age | — |

`SUMMARY-QUAL-TEAM`/`SUMMARY-FINAL-TEAM` use `QUAL`/`FINAL` rather than the
`SCORES`-side `QUAL`/`TEAM` vocabulary for the *first* `<ROUND>` segment —
Qualification and Team Final are the two rounds a team summary exists for,
and "Final" here reads as "the summary is finalized"/"decisive," matching
how an eventual All-Around summary would be `SUMMARY-QUAL-AA` (who
qualifies to the All-Around Final) and `SUMMARY-FINAL-AA` (final
all-around standings) — not yet built; see §6 note 6 for the mapping this
implies once they are.

> **Known ambiguity, accepted deliberately:** `SR` means Still Rings as an
> apparatus code but Senior as an age-level code, so a filename can contain
> `SR` twice with different meanings, e.g.
> `2026-EUROPEAN-CHAMPIONSHIPS-MAG-SR-SCORES-QUAL-SR.csv` (Seniors, Rings).
> This mirrors FIG's own source codes (`GARM1APSR...`) rather than
> inventing a non-standard apparatus code — position in the filename
> disambiguates it (4th segment = age level, 7th = apparatus).

Files not covered by a results PDF at all (`EVENT.csv`) are out of scope
here — see the existing file for its shape.

**Superseded scheme, for reading old commits:** files were previously named
`QUALS-<APP>`, `FINALS-<APP>`, `TEAM-<APP>`, `AA-<APP>`, `QUALS-TEAM`, and
`FINALS-TEAM`. All were renamed 1:1 to the table above (`QUALS-<APP>` →
`SCORES-QUAL-<APP>`, `FINALS-<APP>` → `SCORES-EVENT-<APP>`, `TEAM-<APP>` →
`SCORES-TEAM-<APP>`, `AA-<APP>` → `SCORES-AA-<APP>`, `QUALS-TEAM` →
`SUMMARY-QUAL-TEAM`, `FINALS-TEAM` → `SUMMARY-FINAL-TEAM`); no column
content changed. The old scheme's first segment conflated "which round"
with "is this a summary," which needed a fair amount of this document to
explain away — the `SCORES`/`SUMMARY` split exists specifically to remove
that ambiguity, so don't reintroduce a similar collision when adding a new
file type.

### 5.2 Universal conventions

- **Empty means empty.** Use an actual empty CSV cell for "not applicable /
  not present." Never `N/A`, `-`, `0`, or similar placeholder text.
- **`Status` is always written explicitly — `COMPLETED`, `DNS`, or
  `INVALID_VAULT`.** Every `SCORES-*` file has a `Status` column (or
  `Status1`/`Status2` for the double-vault shape) and every row states one of
  these three values; never leave it blank to mean "completed." This mirrors
  `Score.routine.status` being required in `data-model.md` — a blank cell
  would otherwise force the importer to invent a default rather than reading
  it off the row. (Earlier drafts of this file used blank-means-completed to
  keep rows terse; that convention was dropped once the DB's `NOT NULL`
  constraint made blank ambiguous — see §6 item 2 for the full reasoning.
  `Status2` on Vault is the one deliberate exception: blank there still means
  "no second attempt was expected," a genuinely different thing from
  `COMPLETED`/`DNS`/`INVALID_VAULT` — see §5.5.)
- Preserve values exactly as printed (`0.0` stays `0.0`, not `0.00`;
  `-0.10` keeps its sign) — don't renormalize numeric formatting from the
  source. This includes a leading `!` on a D score (e.g. `!5.2`), which
  means "this score was under inquiry" per the source's own NOTE text — keep
  it as part of the string rather than stripping it out or splitting it into
  a separate column.
- Duplicate `Rank` values for ties are legitimate — copy them as printed,
  don't invent a tiebreaker.
- `Round` values are exactly the four `Round` enum constants —
  `QUAL`/`EVENT`/`AA`/`TEAM` — the same vocabulary as the `<ROUND>` filename
  segment from §5.1 and `data-model.md`'s `Round` enum (and, in turn,
  `Round.java` in code). All three used to disagree (a longer
  `QUALIFICATION`/`APPARATUS_FINAL`/`TEAM_FINAL`/`ALL_AROUND_FINAL`
  vocabulary in this column and in `data-model.md`'s enum, one apparatus
  final per apparatus rather than one shared `EVENT` value, and a `Round`
  column value that wasn't even the same string as the filename's `<ROUND>`
  segment) — reconciled in one pass; see `data-model.md`'s `Round` and
  `Session` sections for the full reasoning, including why collapsing the
  per-apparatus final values into one `EVENT` needed a new
  `Session.apparatus` field to avoid losing precision.
  - `JUDGES.csv` bundles every round into one file (distinguished by this
    column), so every value shows up somewhere in it: `QUAL`, `EVENT`,
    `TEAM`, `AA`.
  - Every other file is already scoped to one round by its filename, so its
    `Round` column is constant for every row — but still write the real
    value rather than leaving it blank, since a downstream import shouldn't
    have to infer it from the filename.
  - `EVENT` is shared by every apparatus's Apparatus Final — the `Apparatus`
    column (or, for `SCORES-AA-<APP>`, `Round=AA` with the apparatus
    likewise in the `Apparatus` column) is what disambiguates which one,
    matching how `data-model.md`'s `Score.apparatus` already carried this
    independently of `Round`.

### 5.2a Self-describing metadata prefix

Every file in §5 opens with a fixed run of metadata columns, in this order,
before any of the columns documented in its own section below. The point is
that each file stands alone — readable, joinable, and unambiguous — without
needing its filename intact; filenames were the *only* source for this
information until this convention was added, which made a rename or a
concatenation of two files silently lose meaning.

**`PARTICIPANTS.csv`, `JUDGES.csv`, `SUMMARY-*.csv`:**

| Column | Notes |
|---|---|
| Event Name | The literal `Event.title` (`data-model.md`) for this specific discipline+age sub-event — e.g. `37th Senior European Championships` for MAG SR, `36th Senior European Championships` for WAG SR. **Not** a coarser shared label like "2026 European Championships" — this competition already has four separate `Event` rows (`EVENT.csv`: MAG-SR, MAG-JR, WAG-SR, WAG-JR), each with its own title, FIG event number, and date range, and this column matches that 1:1. There is no parent "meet" concept above `Event` in the domain model, and this column deliberately doesn't invent one. |
| Discipline | `MAG` / `WAG` |
| Age Category | `SR` / `JR` / `JR16` / `JR17` (`AgeCategory` enum) |
| Event Start Date | `Event.startDate`, from `EVENT.csv`. Exists specifically to disambiguate one year's edition from another (`Event.title` alone doesn't carry a year, e.g. `"37th Senior European Championships"`) — without this, two competitions' files could only be told apart by ordering `figEventNumber`s. |

**`SCORES-*.csv`** gets the same first three columns, but a `Session Start
Date` instead of `Event Start Date`:

| Column | Notes |
|---|---|
| Event Name, Discipline, Age Category | As above |
| Session Start Date | The calendar date this specific `Round` (and, for `EVENT`, this specific apparatus — see below; for a round split into parts, this part) was actually competed, sourced from the PDF's **Competition Schedule** pages, cross-referenced by round name and time window — **never** from a page footer's report-generation timestamp (§5.2b). Named "Start" rather than plain "Date" on purpose: most rounds are single-day in this data set, but a large-field round (e.g. a Worlds-sized Qualification) can genuinely span two calendar days, and the column name shouldn't imply otherwise even though `Session.date` (`data-model.md`) is presently a single field, not a range. If a future competition's round really does span multiple days, that's a `Session`/schema question to revisit, not something to paper over with this column. |
| Part | Right after `Round` on every `SCORES-*` and `SUMMARY-*` file. `1`, `2`, … for each part of a round split across several sessions (US Championships' Day 1/Day 2, §9); blank for an ordinary single-session round and for the combined rows of a split round. Maps to `Session.part`. |

**`SUMMARY-*.csv` uses `Event Start Date`, not `Session Start Date`,
deliberately** — these files are already scoped to one round by their
filename (`SUMMARY-QUAL-TEAM`, `SUMMARY-FINAL-TEAM`, `SUMMARY-QUAL-AA`), so a
per-session date wasn't judged worth the extra precision the way it was for
`SCORES-*`. Likewise `JUDGES.csv` bundles every round into one file (its own
`Round` column distinguishes them, §5.2) but only gets one `Event Start
Date` for the whole file, not a per-round session date — judges aren't yet
tied to a specific `Session` at all; see the note below.

**Per-apparatus session-date example (MAG SR only):** MAG Senior's Apparatus
Final genuinely runs across two calendar days — the Competition Schedule
lists "Senior Men's Individual Apparatus Finals, Part [unlabeled — the part
number doesn't extract as text from the source PDF] 18:00-20:10" on one day
and a second "Part ... 16:30-19:00" block the next day, with no apparatus
breakdown in the schedule text itself. Resolved using each apparatus's
`SCORES-EVENT-<APP>` report-generation timestamp, which clusters tightly
inside one or the other scheduled window (FX/PH/SR all generated
18:29–20:26 on day one; VT/PB/HB all generated 17:02–18:45 on day two) —
this is a narrower, safer use of the footer than trusting it for *which day
a whole round happened* (§5.2b's caution): here the day is already known
from the schedule, and the footer is only disambiguating which of two known
days a given apparatus falls into, corroborated by tight same-day clustering
per apparatus. Result: MAG-SR `SCORES-EVENT-{FX,PH,SR}.csv` get one date,
`SCORES-EVENT-{VT,PB,HB}.csv` get the next day. No other file in this data
set needed a split — every other round's apparatus all share one date.

**Judges aren't tied to a `Session` yet.** `JUDGES.csv` getting `Event Name`/
`Discipline`/`Age Category`/`Event Start Date` closes the "which sub-event is
this file for" gap, but doesn't by itself let an importer resolve a judge
assignment to a specific `Session` row — that still requires matching this
file's `Round` column (and, once one exists, `Apparatus`) against the
`Session`s created from the `SCORES-*`/`SUMMARY-*` files for the same
`Event`/`Discipline`/`AgeCategory`. There is currently no `Judge`/
`JudgeAssignment` domain entity at all (checked: none exists in
`domain/`) — that needs to be designed before this file can actually be
imported, not just parsed.

### 5.2b Don't trust the footer's report-generation timestamp as a competition date

The page footer this whole document otherwise relies on for *locating* a
report (§2–§4) carries a timestamp too (e.g. `Wed, 19 Aug 2026 21:26:04`),
and it is tempting to reuse it as "the date this round happened." **Don't —
it's a publish/regeneration timestamp, not the competition date.** Confirmed
case: WAG Qualification's footers all read "Thu, 13 Aug," but the
Competition Schedule shows Qualification actually took place "Wed, 12 Aug"
— results were compiled and (re-)stamped the next day. The authoritative
source for `Session Start Date` is always the PDF's own **Competition
Schedule** section (near the front of the file, one page per day, listing
time / subdivision / round name) — read the actual date next to the actual
round name there. The one narrow exception is disambiguating *which of two
already-schedule-confirmed days* a specific apparatus falls into, per the
MAG SR example above — that's a corroboration technique, not a replacement
for reading the schedule first.

### 5.3 `PARTICIPANTS.csv`

One row per athlete entry. Columns:

| Column | Notes |
|---|---|
| Order | As printed on the entry list |
| NOC Code | |
| Bib Number | |
| FIG License | |
| Name | Combined "SURNAME Given Name" exactly as printed |
| Surname | **Derived, see §5.10.** This file is the *master* record for an athlete's name — derive it once here. |
| Given Name | Derived, same rule |
| Birth Year | Always populated. The Europeans entry list gives only a birth **year** — see §6 item 5. |
| Birth Date | `YYYY-MM-DD` when the entry list prints a full date of birth (FIG Worlds, §8.1); blank otherwise. Added after the Europeans files were built, so those files don't have the column yet. |

### 5.4 `JUDGES.csv`

One row per judge per apparatus panel per round, all rounds in one file.

| Column | Notes |
|---|---|
| Round | `QUAL` / `EVENT` / `TEAM` / `AA` |
| Apparatus | The two-letter code (`FX`/`PH`/`SR`/`PB`/`HB`/`VT` for MAG, `FX`/`BB`/`UB`/`VT` for WAG), or **blank** for a Superior Jury row (President/Member — not tied to one apparatus). **Not** the spelled-out name the icon stands for (`Floor Exercise`, `Rings`, ...) — identifying the panel's apparatus by icon (§3.4) is a separate step from writing the column value, and the first pass at this file wrote the spelled-out label straight through for all four MAG/WAG `JUDGES.csv` files before it was caught and fixed. Map to the short code explicitly; don't let the icon lookup's own label leak into the output. |
| Function | `SV`, `D1`, `D2`, `E1`–`E7`, `T`/`T1`/`T2`, `L`/`L1`/`L2`, `R`/`R1`–`R6`, `President`, `Vice-President`, `Member` — each must resolve through `JudgeFunction.find` (code or long name, case-insensitive). Reserves are `R<n>`, not `RES <n>`; a single reserve the source doesn't number is plain `R`, the same way `L`/`T` work |
| Name | Combined name as printed |
| Surname | **Derived, every time.** There is no separate master file for judges (unlike athletes), so this file derives the split itself using the rule in §5.10. |
| Given Name | Derived, same rule |
| NOC Code | |

### 5.5 Results-by-apparatus, Qualification round (`SCORES-QUAL-<APP>`)

**Non-vault apparatus** (`FX`, `PH`, `SR`, `PB`, `HB`, `UB`, `BB`):

| Column | Notes |
|---|---|
| Round | UPPER_SNAKE_CASE, §5.2 |
| Apparatus | Two-letter code |
| Rank | Blank for a DNS row |
| Bib | |
| Name | As printed — **do not derive Surname/Given Name here** (§5.10) |
| NOC Code | |
| Status | `COMPLETED`, or `DNS` if the athlete didn't start this apparatus. Always populated, never blank (§5.2). |
| D | Blank on PH's absent-Bonus rows is *not* a thing — PH just never has a `B` value; leave `D`/`E` populated normally |
| E | |
| B | Blank if the apparatus/row has no bonus value (e.g. every PH row). Present on every apparatus/discipline for column alignment (§7.2) even where never observed non-blank |
| Penalty | Blank when no penalty was applied (not `0`) |
| SJ Penalty | Superior Jury Penalty — distinct from `Penalty`. Present for column alignment with Vault (§7.3); not observed non-blank on any non-vault apparatus for either discipline so far |
| Total | Blank when `Status` is `DNS` |
| Qualification | `Q` / `R1`–`R4` / `NR` / blank — matches `QualificationResult` exactly |
| Qualification Rank | Placement in the ranking that decides the apparatus final. On non-vault apparatus that's the same ranking as `Rank`, so copy `Rank` (blank for a DNS row). Kept anyway so every `QUAL` file pairs `Qualification` with its own rank the same way Vault does. The two-per-country rule never changes a rank — it only turns a would-be `Q` into `NR` |

**Vault** (`VT`) — built by joining the single- and double-vault listings
(§4):

| Column | Notes |
|---|---|
| Round, Apparatus, Rank, Bib, Name, NOC Code | As above. `Rank` always comes from the **single-vault** list — the whole field ranked on vault 1, which is the vault that counts for all-around and team. |
| Status1 | Status of vault attempt 1: `COMPLETED`, `DNS` (never vaulted at all), or `INVALID_VAULT` (see below). Always populated — this is the one status the domain requires non-null (`Score.routine.status`), unlike `Status2`. |
| D1, E1, B1, Penalty1, Total1 | Vault 1 figures, from the single-vault list. `B1` populated for MAG, always blank for WAG so far (§7.2) |
| SJ Penalty1 | Superior Jury Penalty, attempt 1 — distinct from `Penalty1`. Populated for WAG, always blank for MAG so far (§7.3) — the opposite split from `B1` |
| Status2 | Status of vault attempt 2, **four-state**: blank = no second attempt was expected at all (this athlete isn't on the double-vault list, and `Score.secondRoutine` is null for exactly this case); `COMPLETED` = a real second vault happened; `DNS` = a second vault was intended (athlete is on the double-vault list) but not completed; `INVALID_VAULT` = a second vault was attempted but ruled invalid. Don't collapse "opted out" (blank) and "started but didn't finish" (`DNS`) into the same value. |
| D2, E2, B2, Penalty2, Total2 | Vault 2 figures, from the double-vault list. Blank whenever `Status2` is blank or `DNS`. Same MAG/WAG `B2` split as `B1` |
| SJ Penalty2 | Same WAG/MAG split as `SJ Penalty1` |
| Combined Bonus | A bonus applied to the **combined** two-vault result, not to either attempt (WAG's +0.2 — §7.3, §8.2). Present for MAG too, always blank so far |
| Total | The result the Vault final is decided on: `Total1` alone for a single-vault athlete, the published average plus `Combined Bonus` for a completed two-vault athlete, blank when there's no valid combined result (`Status1=DNS` or `Status2=DNS`) |
| Qualification | `Q`/`R1`–`R4`/`NR`/blank — only ever set from the double-vault list; always blank for single-vault-only athletes |
| Qualification Rank | This athlete's rank **within the double-vault list** (i.e. among just the Vault-final hopefuls, on the combined result) — distinct from `Rank`. Blank for single-vault-only athletes. |

**`INVALID_VAULT` confirmed, with a real example:** WAG-SR-QUAL-VT, Bib 111
(DEVREKER Lena, BEL) — `D1=0.0`, `E1=0.000`, `Total1=0.000`, and she never
appears on the double-vault list at all (no second attempt, no `DNF`/`DNS`
marker anywhere for her). The source PDF gives no distinct symbol or
footnote for this — an invalid vault just shows up as `D=0.0`/`Total=0.000`
on the single-vault list, which is otherwise indistinguishable at a glance
from a genuine (if extremely poor) `D=0.0` score. What actually distinguishes
it from a `DNS` case (compare Bib 135/CZE and Bib 301/AUT in the same
files, both also printed as `0.000` on the single-vault list) is the
double-vault list: a `DNS` athlete is *present* there with an explicit `DNF`
marker (mapped to `Status1=Status2=DNS` per §6 item 2), while an invalid-vault
athlete like Bib 111 is simply *absent* from it, same as anyone who validly
chose to skip a second attempt. Confirm this reading — `D=0.0` on a
completed attempt, nothing on the double list — before treating it as the
general rule for future PDFs; it's only been seen once so far.

### 5.6 Results-by-apparatus, Apparatus Final round (`SCORES-EVENT-<APP>`)

The Apparatus Final report is much simpler than Qualification's — one flat
8-row table (the field is already cut down to the 8 finalists), a single
result each, no DNS cases observed, and **no qualification marker at all**
(there's no further round to qualify into). So this file drops everything
that only existed to track qualification, but keeps `Status` — every
`SCORES-*` file does, per §5.2, even where every row so far reads
`COMPLETED`:

**Non-vault apparatus:**

| Column | Notes |
|---|---|
| Round | `EVENT` (§5.2) |
| Apparatus | Two-letter code |
| Rank, Bib, Name, NOC Code | As in §5.5 |
| Status | `COMPLETED` for every row seen so far — no withdrawal has shown up in a Final yet, but the column is present regardless (§5.2) so a future one doesn't require a schema change, just a different value |
| D, E, B, Penalty, SJ Penalty, Total | As in §5.5. PH again has no non-blank `B` value and prints `E` before `D` in the source — same header-order check from §3.1 applies |

No `Qualification` column — there's no further round to qualify into, so
there's nothing for it to hold.

**Vault** (`VT`) — every finalist performs two vaults in the actual final
(unlike Qualification, where a second vault was optional), so there is only
one listing here, not two, and every row is fully populated:

| Column | Notes |
|---|---|
| Round, Apparatus, Rank, Bib, Name, NOC Code | As above |
| Status1, Status2 | Both `COMPLETED` for every row seen so far — every finalist completes both attempts, but the columns are present regardless (§5.2) |
| D1, E1, B1, Penalty1, SJ Penalty1, Total1 | Vault 1. `B1`/`SJ Penalty1` follow the same MAG/WAG split as §5.5 |
| D2, E2, B2, Penalty2, SJ Penalty2, Total2 | Vault 2, same split |
| Total | The published average of the two vaults |

`Qualification`/`Qualification Rank` don't apply here (same reasoning as the
non-vault Finals table above).

As with the Qualification double-vault list (§4), the Vault Final table's
narrow Name column can truncate a given name (seen here for Bib 633,
printed as `SPONEVIK` with no given name) — cross-check against
`PARTICIPANTS.csv` by Bib and use its `Name` instead of trusting this
table's version verbatim.

### 5.7 Results-by-apparatus, All-Around Final round (`SCORES-AA-<APP>`)

Only seen so far for Junior Men, which — unlike the Senior data this
standard was first built from — has a separate All-Around Final round in
addition to the Apparatus Final, each with its own full six-apparatus
results-by-apparatus breakdown. See §5.1 for why this gets a distinct
`SCORES-AA-<APP>` filename rather than colliding with `SCORES-EVENT-<APP>`.

Columns and conventions are otherwise identical to §5.6 — same trimmed-down
shape (`Status`, no `Qualification`), same per-apparatus header-order
quirks — with one value changed and one structural surprise:

| Column | Notes |
|---|---|
| Round | `AA` (§5.2) — the one column that actually differs from §5.6 |
| Apparatus, Rank, Bib, Name, NOC Code, Status, D, E, B, Penalty, SJ Penalty, Total | As in §5.6 |

**Vault is the single-attempt shape here** — `Status`/`D`/`E`/`B`/`Penalty`/`SJ Penalty`/`Total`,
not the `Status1`/`Status2`/`D1`/`D2` split. This is the opposite of what the filenames alone
would suggest (`SCORES-EVENT-VT` is the two-attempt shape, per §5.6;
`SCORES-AA-VT` is the plain one), and it makes sense once you think about
what each round is actually for: the Apparatus Final's vault specialists
are being judged on vault specifically, so they get two attempts; an
All-Around finalist's vault score is just one contributor to a
six-apparatus total, so it's a single attempt like every other apparatus in
their all-around routine. The takeaway isn't "AA is always single-attempt"
so much as **check the actual page's header before assuming either shape
carries over** — the same caution as §3.1 and the `SCORES-TEAM-VT` note in
§5.8.

### 5.8 Results-by-apparatus, Team Final round (`SCORES-TEAM-<APP>`)

In the Team Final, the qualified teams' athletes perform each apparatus
again — this is the per-athlete result feeding into the team score (§5.9
covers the team-level summary those add up to). Structurally this report is
just like Qualification (§5.5) with the qualification-tracking columns
removed, same as the individual Apparatus Final (§5.6) and All-Around
Final (§5.7):

| Column | Notes |
|---|---|
| Round | `TEAM` (§5.2) |
| Apparatus | Two-letter code. Like Judges Assignment (§3.4) and the `SUMMARY-QUAL-TEAM` top-line report (§5.9), the apparatus is an **icon** in the page header, not text — confirmed here by rendering all six pages, same FX/PH/SR/VT/PB/HB order as everywhere else. |
| Rank, Bib, Name, NOC Code | As in §5.5/§5.6/§5.7. Ties are common here (the field size is fixed at "qualified teams × roster count on that apparatus," not a ranked cut, so more ties survive than in Qualification/individual Finals) — copy duplicate ranks as printed. |
| Status | As in §5.6/§5.7 — `COMPLETED` for every row seen so far |
| D, E, B, Penalty, SJ Penalty, Total | As in §5.5/§5.6/§5.7. PH again has no non-blank `B` value and prints `E` before `D`. |

No `Qualification`/`Qualification Rank` columns — no further round to
qualify into.

**Vault is a single-attempt table here** — unlike both Qualification's
double-vault list and the individual Vault Final (§5.6, though *not*
Junior's All-Around Final, §5.7 — check the page), a team-final vault
routine is only performed once per athlete, so `SCORES-TEAM-VT.csv` uses
the same plain `Status`/`D`/`E`/`B`/`Penalty`/`SJ Penalty`/`Total` shape as
every other apparatus in this section, **not** the
`Status1`/`Status2`/`D1`/`D2`-style split. Don't assume "Vault" always means the two-attempt shape — check the
actual page, the same way §3.1 says to check the header row before assuming
a column layout carries over.

### 5.9 Team Results, top-line only (`SUMMARY-QUAL-TEAM`, `SUMMARY-FINAL-TEAM`)

A separate report from the individual apparatus results (§5.5, §5.6, §5.7, §5.8) — the
"Team Results" report shows, per NOC, a summary row (rank, six apparatus
totals, penalty, grand total, qualification marker where applicable)
followed by every team member's individual per-apparatus scores underneath
it (with the discarded/dropped score on a team of more than the counting
number shown in italics). This shape shows up twice: once for Qualification
(`GARMTEAM...QUAL..._C73C`, 29 teams) and once for the Team Final itself
(`GARMTEAM...FNL..._C73C`, just the 8 teams that qualified) — titled "Team
Results" and "Team Final Results" respectively, but otherwise the same
report.

**This CSV captures only the summary row — not the per-athlete breakdown.**
If a future task needs the per-athlete team scores, that's a different,
larger parse of the same report and should get its own file/section here
when it's actually built; don't assume the summary-row columns below cover
it. (That per-athlete detail is a *different* report from `SCORES-TEAM-<APP>`,
§5.8, too — §5.8 is the official per-apparatus results feeding into
scoring, individually paginated per apparatus; the breakdown embedded in
*this* report is the same underlying scores laid out per-team instead, with
the drop-lowest score marked in italics.)

Juniors, at least in this PDF, have no Team Final at all —
`SUMMARY-QUAL-TEAM` exists for them but there's no `SUMMARY-FINAL-TEAM`
counterpart, and the Qualification-round summary carries no `Qualification`
marker for any team (confirmed: the letter `Q` doesn't appear anywhere in
that report) since there's no team round to qualify into. Don't treat a
missing `SUMMARY-FINAL-TEAM` file as a parsing gap for a level/discipline
that turns out not to have the round at all.

Like Judges Assignment (§3.4), the six apparatus columns are headed by an
**icon**, not text — confirmed by rendering the page. The order was the same
FX, PH, SR, VT, PB, HB used everywhere else in this document, cross-checked
against an athlete's individual apparatus score elsewhere in the same PDF
(e.g. a team's first column total included a `14.833` matching that
athlete's already-parsed `SCORES-QUAL-FX.csv` row).

| Column | Notes |
|---|---|
| Round | `QUAL` for `SUMMARY-QUAL-TEAM`, `TEAM` for `SUMMARY-FINAL-TEAM` — note the second one doesn't literally match its own file's `<ROUND>` filename segment (`FINAL`, not `TEAM`); that's expected for `SUMMARY-FINAL-TEAM` specifically, since §5.1 deliberately names the *file* after "this is the decisive/final version of the team summary" while the `Round` *column* still names the actual round (`TEAM` — the Team Final) that produced it |
| Rank | Team placement. Ties/no ties as printed. |
| NOC Code | The report also prints a full team/federation name (e.g. `World Gymnastics 2`, `Great Britain`) — **not captured**, since `NOC Code` is enough to join back to `PARTICIPANTS.csv`/`Athlete.country` and the standard doesn't otherwise carry a display name column. |
| FX Total, PH Total, SR Total, VT Total, PB Total, HB Total | Each apparatus's counting team total (already the sum/drop-lowest result printed by the source — not recomputed) |
| SJ Penalty | Superior Jury penalty applied to the team, distinct from an individual routine's `Penalty`. Blank when none was applied — every team in both rounds had a blank value here, so this column is unverified against a real example; watch for one in a future PDF and confirm the parsing still holds. |
| Total | Team's grand total |
| Qualification | `Q`/`R1`/`R2`/`R3`/`NR`/blank, same vocabulary as everywhere else. **`SUMMARY-FINAL-TEAM` omits this column entirely** — there's no further round to qualify into once the Team Final is done, same reasoning as §5.6/§5.7/§5.8 dropping it. (The first pass at `WAG-SR-SUMMARY-FINAL-TEAM.csv` wrote this column anyway, always blank — caught during a MAG/WAG column-consistency review and removed; MAG's equivalent file never had the bug. If a future `SUMMARY-FINAL-TEAM` file turns up with this column, drop it rather than carrying it along empty.) |

### 5.10 Name splitting (Surname / Given Name)

Rule: split `Name` on spaces; every **leading** word that is entirely
uppercase is part of the surname (handles multi-word family names like
`VAN DER AA`, `CHAPARRO WERT`); the remaining word(s) are the given name
(also handles multi-word given names like `Mehmet Ayberk`). In Python:

```python
def split_name(name):
    words = name.split(' ')
    i = 0
    while i < len(words) and words[i].isupper():
        i += 1
    return ' '.join(words[:i]), ' '.join(words[i:])
```

Note `str.isupper()` correctly treats an accented given name (`Sébastien`,
`André`) as *not* all-uppercase, since the accented lowercase letter isn't
uppercase — no special Unicode handling needed.

**Extension needed for the Worlds PDF:** a leading lowercase particle
(`van POL Vera`, `de MUNCK Loran`, `le ROCH Tanguy`, `LIDE de LEON Alyiah`)
and a `Mc` prefix (`McDONALD Kate`, `Mc CLENAGHAN Rhys`) both fail
`isupper()`, which would push the whole name into Given Name. Treat a
particle (`van`, `der`, `de`, `le`, `Mc`, ...) or a word matching
`(Mc|Mac)?<no lowercase>` as still part of the surname run. Don't instead
use "everything up to the *last* uppercase word" — a trailing initial
(`SOTO RODRIGUEZ Jensuel J`) would then swallow the given name.

**Where to apply this:**

- **`PARTICIPANTS.csv` is the master record for an athlete's name.** Derive
  Surname/Given Name there, once, keyed by Bib.
- **`JUDGES.csv` has no equivalent master file**, so it derives Surname/Given
  Name itself, every time.
- **Results-by-apparatus files do not derive Surname/Given Name at all.**
  They carry `Bib` and the combined `Name` (for human readability /
  spot-checking only), and are expected to be joined against
  `PARTICIPANTS.csv` by `Bib` to get the authoritative Surname/Given Name at
  import time. Don't re-derive it independently in every results file — one
  source of truth, matched on Bib.

## 6. Open items — reconcile with `data-model.md` before/at import time

These were noticed while writing this doc and are flagged rather than
silently resolved, since they affect the schema, not just this parsing
process:

1. ~~**`Round` granularity.**~~ **Resolved.** The filename `<ROUND>`
   segment (§5.1), the CSV `Round` column (§5.2), `data-model.md`'s `Round`
   enum, and `Round.java` in code all now use the same four values —
   `QUAL`/`EVENT`/`AA`/`TEAM` — with no translation step needed between
   them. This replaced an earlier three-vocabulary mismatch (a longer
   `QUALIFICATION`/`APPARATUS_FINAL`/`TEAM_FINAL`/`ALL_AROUND_FINAL` column
   vocabulary, and a `Round` enum with one value per apparatus final —
   `FX_FINAL`, `PH_FINAL`, etc.). Collapsing the per-apparatus final values
   into one `EVENT` meant `Session` needed a new `apparatus` field (nullable,
   set only for `EVENT` rounds) to keep the "each apparatus final is its
   own session" precision the old per-apparatus enum values used to carry —
   see `data-model.md`'s `Round`/`Session` sections. One residual, expected
   asymmetry: `SUMMARY-FINAL-TEAM`'s filename segment is `FINAL` (naming
   convention: "this is the decisive team summary") while its `Round`
   column is `TEAM` (the actual round) — see §5.9.

   This also surfaced a real, separate gap worth flagging on its own: once
   `apparatus` joined `Session`'s unique constraint, the constraint no
   longer catches a duplicate `QUAL`/`AA`/`TEAM` session at the database
   level (standard SQL: two nulls in a unique constraint aren't considered
   equal), since `apparatus` is null for those rounds. That protection now
   lives in application code (`SessionService.createSession`) instead of
   being fully DB-enforced — revisit with a Postgres partial unique index
   once real Flyway migrations exist (none do yet; schema is currently
   test-only via Hibernate `ddl-auto`).
2. **No `DNF` in `ScoreStatus`.** The vault DNF case (§4) is deliberately
   *not* represented as a `DNF` status value anywhere in the CSV standard —
   `data-model.md`'s `ScoreStatus` enum only has `COMPLETED` / `DNS` /
   `INVALID_VAULT`. Instead it's expressed as `Status1=` (completed) +
   `Status2=DNS` (intended second attempt, not completed), which maps
   directly onto `Score.routine.status`/`Score.secondRoutine.status` without
   needing a new enum value. A null `secondRoutine` means no second attempt
   was expected at all; an expected-but-missing one is a `secondRoutine`
   with status `DNS`.
3. **`INVALID_VAULT` — now observed, see §5.5.** WAG-SR-QUAL-VT Bib 111
   (DEVREKER Lena) is a confirmed case: `D=0.0`/`Total=0.000` on the
   single-vault list, no distinct symbol or footnote at all (the `!`
   "under inquiry" prefix from §5.2 is a different, unrelated marker — a
   score still stands but is flagged, not invalidated). Only one example
   exists so far; keep watching for whether every invalid vault looks this
   plain or whether this was a simplest-case instance.
4. ~~**Two ranks for Vault, one `rank` field on `Score`.**~~ **Resolved.**
   `Score` now has both: `rank` (the published apparatus ranking — the
   single-vault list for vault) and `qualificationRank` (the ranking that
   decides the apparatus final — the double-vault list for vault, equal to
   `rank` everywhere else), with `qualificationResult` read against
   `qualificationRank`. Every `SCORES-QUAL-*` file now carries a
   `Qualification Rank` column, not just Vault. See `data-model.md` §4.
5. **Birth year, not birth date.** `PARTICIPANTS.csv`'s `Birth Year` is
   coarser than `Athlete.birthDate` (a full `Date`). The entry-list PDF
   simply doesn't publish more precision than a year. Decide at import time
   whether to store a placeholder date (e.g. Jan 1 of that year) with a
   precision caveat, or to leave `birthDate` null when only a year is known.
6. **`SUMMARY-QUAL-AA` and `SUMMARY-FINAL-AA` not yet built.** §5.1's naming
   table has room for an all-around top-line summary (who qualifies to the
   All-Around Final; final all-around standings) at both rounds, mirroring
   `SUMMARY-QUAL-TEAM`/`SUMMARY-FINAL-TEAM`. Both exist as their own pages
   in the source PDF — noticed while designing the naming, deliberately
   *not* parsed yet. When they are, they'll need their own subsection near
   §5.9 documenting the actual report shape (unverified: does the AA
   ranking table look like the Team one — a summary row followed by a
   per-apparatus breakdown — or something else entirely?).

## 7. WAG-specific differences

Everything above was written against the MAG PDF. Parsing WAG for the first
time (`2026-EUROPEAN-CHAMPIONSHIPS-WAG-RESULTS.pdf`) confirmed the naming
standard (§5.1) and most of the CSV standard (§5) carry over unchanged —
`<EVENT-PREFIX>-SCORES-<ROUND>-<APP>.csv` with `DISCIPLINE=WAG` and
`<APP>` ∈ `FX`/`BB`/`UB`/`VT` needed no new suffix types. These are the
real deltas found:

### 7.1 E-before-D is the WAG default, not an exception

MAG had exactly one apparatus (PH) that printed `E` before `D`. In WAG,
**every** apparatus — `FX`, `BB`, `UB`, and both attempts of `VT` — prints
`E` before `D`. Don't carry over "assume `D` before `E` unless it's the
PH-like exception" as a mental shortcut for a new discipline; re-check the
header per §3.1 regardless of what discipline you're on. The CSV's own
`D`/`E` column *names* and order stay fixed the way §5.5 already
established (parse from wherever the source actually put them, write to
the fixed-name columns) — this is a parsing-input difference, not an
output-schema change.

### 7.2 No `B`/Bonus concept on WAG's non-vault apparatus — but the column is still present

Like MAG's PH, none of WAG's `FX`/`BB`/`UB` source pages print a bonus value
— but for WAG this is true of every non-vault apparatus, not one exception.
This was originally reflected by omitting the `B` column from WAG's
`SCORES-QUAL-<APP>`/`SCORES-EVENT-<APP>`/`SCORES-TEAM-<APP>` files entirely
(not just leaving it blank) — since corrected: per your call, MAG and WAG's
`SCORES-*` column sets are now kept aligned, so WAG's non-vault files carry
a `B` column too, always blank, the same way MAG's PH rows already were
(§5.5). The underlying PDF fact is unchanged (WAG genuinely has no bonus
concept here) — only the CSV's willingness to represent an always-blank
column for cross-discipline consistency changed.

### 7.3 WAG Vault has two extra concepts MAG's schema doesn't need

Confirmed directly from the PDF (not inherited from the pre-existing
ad-hoc WAG files, which got both of these wrong — see §7.6):

- **A real `SJ Pen.` (Superior Jury Penalty) column, per vault attempt.**
  Distinct from the ordinary `Penalty` column. Add `SJ Penalty1`/
  `SJ Penalty2` columns to Vault's schema, positioned after each attempt's
  `Penalty`.
- **Bonus is a `*` printed on the combined `Total`, not a per-attempt
  numeric column.** The source's own NOTE spells it out: *"Asterisk (*) at
  Vt Score informs that Bonus(+0.2) was applied."* Decoded into a single
  `Combined Bonus` column (`0.2` or blank; originally named `Bonus`, renamed
  so it can't be confused with the per-attempt `B1`/`B2`) sitting just before `Total`, with `Total`
  itself holding the clean number (asterisk stripped) — chosen over
  preserving the `*` inline, unlike the `!` inquiry marker (§5.2), because
  this value is immediately needed as a number downstream and the
  legacy WAG files already established that shape as reasonable.

Full `SCORES-QUAL-VT` column set (WAG):

```
Round,Apparatus,Rank,Bib,Name,NOC Code,
Status1,D1,E1,B1,Penalty1,SJ Penalty1,Total1,
Status2,D2,E2,B2,Penalty2,SJ Penalty2,Total2,
Combined Bonus,Total,Qualification,Qualification Rank
```

MAG's vault files have exactly the same columns — `Combined Bonus` included,
always blank so far.

(`B1`/`B2` included for column alignment with MAG's Vault, §5.5 — always
blank for WAG, same reasoning as §7.2.)

`SCORES-EVENT-VT` is the same shape minus `Qualification`/`Qualification
Rank` (no further round to qualify into — same reasoning as §5.6), but keeps
`Status1`/`Status2` per §5.2/§5.6 (`COMPLETED` for every row seen so far).
Both `QUAL`/`EVENT` are built by joining a single-vault list and a
double-vault list exactly per §4 — WAG has the identical two-listing
structure, DNF/DNS conventions included.

**`SCORES-TEAM-VT` never had `SJ Penalty` or a combined bonus in the source.**
Confirmed by rendering the actual page: Team Final vault is single-attempt
for WAG (matching MAG, §5.8). The CSV still carries a blank `SJ Penalty`
column here for alignment with the rest of §5.8's table (`B` too — WAG's
`SCORES-TEAM-VT.csv` is `Status,D,E,B,Penalty,SJ Penalty,Total`, both always
blank), but there is no `*`/`SJ Pen.` on the actual page and no `Bonus`
column at all on this particular report — don't assume Vault always carries
the combined-bonus concept; it only shows up on the two double-attempt
reports.

### 7.4 The shared-footer-code trap bit again — verify by title, not just by grep count

§2 rule 5 already documents that a footer code can cover more than one
report. WAG's Vault Qualification section is a sharper version of this:
the single-vault list, the double-vault list, *and* two separate Tie Break
sections all share variations of the same handful of footer codes, with
the double-vault list sandwiched between two Tie-Break blocks under
codes that look superficially distinguishable but aren't reliably so.
A first pass at this mis-identified the double-vault list as a
differently-coded block based on where a `grep` hit landed, and only
caught the error by re-deriving the physical page from a unique string
match and checking its printed title. **Always print the title line for
every physical page in a `grep` hit's range before writing the parser** —
don't infer a block's identity from occurrence-counting the footer code
alone, even when it looks like a clean pattern.

### 7.5 Long-surname truncation recurred, with no master name file to fall back on

§3.6 already covers a long surname overflowing a fixed column width. It
recurred here for three WAG athletes (`CHRISTODOULOU Mariantrea`,
`ADALSTEINSDOTTIR Thelma`, `THORBJARNARDOTTIR Audur Anna`, each truncated
to just the surname on every apparatus table, not only Vault's narrow
one). §3.6's fix assumes a `PARTICIPANTS.csv` master to diff against —
**WAG has no `PARTICIPANTS.csv` yet**, so the fallback used instead was
building a Bib → full-name lookup out of every other apparatus/round table
already being parsed in the same batch (a truncated name on one table is
reliably full-width on at least one other), then applying it everywhere.
This works as a stopgap but isn't a substitute for the real fix: build
`PARTICIPANTS.csv` (entry list) for WAG as its own task, then switch to
diffing against it per §3.6 once it exists.

**Update: `PARTICIPANTS.csv` now exists for WAG** (§7.8), and re-diffing
every already-built `SCORES-*` file against it caught four *more*
truncations the cross-file lookup above had missed — this time a
multi-word **given name** cut off (`HWANG Filippa Hailey` →
`HWANG Filippa Hailey Hogberg`, `GUTIERREZ MAYORDOMO` →
`GUTIERREZ MAYORDOMO Lucia`, two more like it), not just a long surname.
The lookup-of-convenience approach only catches a truncation if *some*
other table in the batch happened to print that Bib's name in full; a
name that's long enough to get clipped everywhere it appears will pass
that check silently. Now that a master file exists, always diff every
`SCORES-*`/`SUMMARY-*` file's `(Bib, Name)` pairs against it directly
(§3.6) rather than relying on cross-file agreement.

### 7.6 The pre-existing ad-hoc WAG files had real bugs, not just non-standard naming

Before this pass, WAG had 20 CSVs already parsed under an earlier, informal
naming scheme (`QUALS-<APP>`, `FINALS-<APP>`, `TEAM-<APP>`, no `Round`/
`Apparatus` columns). Aligning them turned into a full re-parse rather than
a rename, because the old files had actual data bugs, confirmed against
the source PDF:

- Names truncated in places a full re-parse fixed (§7.5's cases plus
  ordinary narrow-column Vault truncation, §3.6).
- A `DNS` value found sitting in the wrong column for at least one row
  (misaligned during whatever ad-hoc process produced the file).
- An `Inquiry` Yes/No column standing in for the `!` inquiry marker (§5.2
  says to keep `!` inline on the D score — the ad-hoc files instead added a
  side column, and inconsistently: present on some apparatus files,
  missing on others for no apparent reason).
- Column naming drift *within* the same file family — the qualification
  round's Vault file used `E1`/`D1`.../`E2`/`D2`, while that same
  competition's Finals/Team Vault files used `Vault 1 E`/`Vault 1 D`...
  for the identical concept.

The lesson generalizes: if a future data set turns up already
partially parsed by an earlier, informal process, don't assume the only
gap is naming — diff a sample of its values against the actual PDF before
deciding a rename is sufficient.

### 7.7 `PARTICIPANTS.csv` and `JUDGES.csv` for WAG

Both now exist (`WAG-SR`/`WAG-JR`, each), built exactly per §5.3/§5.4 — the
entry list and Judges Assignment reports have the same shape as MAG's, one
real difference aside:

- The entry list's own footer code differs (`WAG_SEN_000234`/
  `WAG_JUN_000234` vs. MAG's `MAG_SEN_...`/`MAG_JUN_...`), and the page
  title reads **"Entry List By BIB"** (all-caps `BIB`) rather than MAG's
  "By Bib" — cosmetic, but don't pattern-match the title string literally
  when hunting for this report in a new PDF.
- Judges Assignment is a **2×2 icon grid** (`Vault`/`Uneven Bars` top row,
  `Balance Beam`/`Floor Exercise` bottom row), not MAG's 3×2 — because WAG
  has four apparatus, not six. Confirmed by rendering all five
  Judges Assignment pages (`QUAL`/`EVENT`/`TEAM` for `SR`, `QUAL`/`EVENT`
  for `JR` — no `TEAM` for `JR`, §7.10) rather than assuming the grid
  shape carries over from MAG. The parsing code itself (Superior Jury
  block, then N panel-pair blocks split by the second `Function` column
  position) needed no changes beyond swapping the apparatus list and
  panel-pair count from 3 to 2 — see §3.4's Judges Assignment guidance.

### 7.8 Team/All-Around Results: icon order confirmed, and it doesn't match the printed LEGEND text order

Both the `SUMMARY-QUAL-TEAM`/`SUMMARY-FINAL-TEAM` "Team Results" report and
the `SUMMARY-QUAL-AA` "All-Around Results" report head their apparatus
columns with icons, same as Judges Assignment (§3.4). Rendering confirmed
the actual column order is **Vault, Uneven Bars, Balance Beam, Floor
Exercise** — but the page's own printed `LEGEND` text below the table lists
them in a *different* order ("Floor exercise, Balance beam, Uneven bars,
Vault"). The legend is correct as a glossary (which icon means which
apparatus) but wrong as a guide to column order — **always confirm column
order from the rendered table header itself, never from the legend's
listing order**, even when the legend is right there on the same page.

### 7.9 All-Around Results: a four-line-per-athlete layout, and a regex gotcha

Unlike Team Results (one athlete per data row, apparatus sub-columns
inline), the individual All-Around Results report spreads **one athlete
across four physical text lines**: a line of `D`/`Total`/`Rk` triples (one
per apparatus) *above* the identity line, the `Rank`/`Bib`/`Name`/`NOC`
identity line itself, a line of `E`/`Pen` values *below* that, and the
combined all-around `Total` sitting alone on a further line after that.
Parsing this means locating the identity line first, then reading
backwards one line for the per-apparatus totals and forwards past the E/Pen
line for the overall total — not a per-row tokenize like every other
report in this document.

Extracting just the `Total` from each apparatus's `D Total Rk` triple used
a regex over the D/Total/Rk line rather than whitespace-splitting it (the
triples run together with only one space in places, the same
marker-spacing problem as §3.2, here between `Rk` and the next apparatus's
`D`). First pass at that regex used a character class of `[\d.\s]` to
recognize the E/Pen line while scanning forward for the overall total, and
silently produced blank totals for every row with a negative penalty on
that line — a plain minus sign isn't in `[\d.\s]`, so the scan mistook a
penalized row's E/Pen line for the end of the block and gave up early.
Fixed by adding `-` to the class. Generalizes: a whitespace/digit-only
character class over one column of a results table is never actually safe
to assume, because a penalty column can always turn negative on the exact
row you use to write it.

### 7.10 A schedule line naming a round doesn't mean that round has its own session — check the time block, not just the label

**Correction to an earlier read of this section:** this was originally
written as "the schedule says these rounds exist but the results PDF
doesn't have them" — a real discrepancy. That was wrong. Re-reading the
Competition Schedule pages properly (while building §5.2a's `Session Start
Date` column) showed the schedule agrees with the results all along: it
lists **"Women's Junior Team Final"** and **"Women's Senior/Junior
Individual All-Around Final"** as their own line items, but each one is
stamped with the *identical* time block as the Qualification subdivisions
printed directly beneath it (e.g. WAG Senior AA Final: `09:30 - 21:20`,
exactly matching the full span of that day's five Qualification
subdivisions combined). The schedule is using three different labels for
one physical session, not describing three separate sessions — the
takeaway is **read the time column, not just the session-name column**,
before concluding a round has its own results to go looking for. The
underlying data conclusion below (no separate session/results for these)
was correct then and still is now — only the "schedule vs. PDF disagree"
framing was wrong.

Neither has any corresponding *separate* results in this PDF:

- No `GARWTEAM----JNR-------FNL---------` footer code exists anywhere, and
  no page anywhere titles itself "Team Final"/"JUNIORS" outside the
  schedule. Junior WAG genuinely has no `SCORES-TEAM-<APP>` or
  `SUMMARY-FINAL-TEAM` files in this data set — matches the MAG Junior
  gap, now confirmed for a second discipline.
- "All-Around Final" never appears as a page title outside the schedule
  either, for *either* age level. The only individual all-around ranking
  in the whole document is `GARW1AA...QUAL...` (§7.9), which prints
  **"All-Around Medallists" directly on the Qualification-round page** —
  meaning for WAG, at least in this event, there is no separate All-Around
  Final round at all. The Qualification round's combined score *is* the
  final all-around result. Consequently there is a `SUMMARY-QUAL-AA` file
  for both `SR` and `JR`, but **no `SUMMARY-FINAL-AA`** — not an oversight,
  there is nothing to parse.

The general lesson: a distinct name on the schedule doesn't mean a distinct
session — check whether its time block is actually its own slot or just a
relabeling of a block already accounted for by another round (§5.2a). And
regardless of what the schedule implies either way, confirm a round's
results actually exist as their own report (a real footer code, a real page
title) before building a file for it — don't infer a file should exist just
because the schedule names the round.

### 7.11 A team can be missing an entire apparatus

One Junior team (Kosovo, `SUMMARY-QUAL-TEAM`) competed on only 3 of the 4
apparatus — the source prints a blank in that apparatus's column entirely
(not a `0.000`) and `DNF` in the `Total` position, the same
incomplete-team-result idea as an individual `DNF` (§4) but at the team
level. This one row didn't fit the normal 5-or-6-token parse (a genuinely
missing column shifts every token after it), and rather than generalize
the parser for an `n=1` case, it was corrected by hand after generation,
using the source's own column *positions* (not token count) to confirm
which specific apparatus was blank. If this recurs, worth generalizing;
for one row, hand-verification against the rendered/positional source was
faster and just as reliable.

## 8. FIG World Championships results book differences

Parsing `2026-WORLD-CHAMPIONSHIPS-ALL-RESULTS.pdf` (one 329-page book, MAG
and WAG, Seniors only) confirmed the footer-code approach (§2) still works,
but the report set and several layouts differ from the Europeans PDF. The
checks in §8.3 are what made the output trustworthy.

The parser is
[`tools/results-parsing/parse_2026_world_championships.py`](../tools/results-parsing/parse_2026_world_championships.py)
— run it with the PDF path; it reads `EVENT.csv` from the same folder,
writes every file next to the PDF, and exits non-zero if any §8.3 check
fails. It is the starting point for the next FIG results book, but its
session dates, footer codes and layout assumptions are specific to this one.

### 8.1 Report inventory

Print `footer code + page title` for every physical page first (§2) — this
book also has Medallists, Start Lists, Lists of Qualifiers and **Tie Break
Report** pages (`_77A`–`_77D`) interleaved with the results, none of which
are parsed. Things that changed:

- **Entry list** (`GARM----..._32D` / `GARW----..._32D`, "Entry List by Bib
  Number") prints a full **Date of Birth**, so `PARTICIPANTS.csv` gets a
  `Birth Date` column (§5.3). There is no printed Order column; `Order` is
  the row's position in the bib-ordered list.
- **No per-apparatus report for the Team Final or All-Around Final.** The
  per-athlete scores for those rounds exist only inside the Team Results
  (`GARMTEAM..._73C`, `GARWTEAM..._73D`) and All-Around Results
  (`GARM1AA..._73G`, `GARW1AA..._73H`) reports. `SCORES-TEAM-<APP>` and
  `SCORES-AA-<APP>` are built from those (§8.3). Team-final rows have no
  per-apparatus rank, so `Rank` is blank there; the AA report prints one
  (`(Rk)`), so `SCORES-AA-<APP>` has it.
- `SUMMARY-FINAL-AA` exists for the first time (the AA Final report), with
  the `SUMMARY-QUAL-AA` columns minus `Qualification`.
- Two reports share `GARM1AA...QUAL..._73G`: "Men's Qualifications" (every
  athlete, any apparatus count) and "Men's All-Around Qualification" (the
  AA ranking). Select by the printed title (§2 rule 5); only the second
  feeds `SUMMARY-QUAL-AA`. The AA ranking still lists non-all-arounders at
  the bottom with `DNS` apparatus and a `DNF`/`DNS` total — kept as rows
  with blank `Rank`/`Total`, as printed.
- Vault qualification: the double-vault list is `_73J` ("Vault
  Qualification"), the single-vault list is `_73I` titled "Qualifications -
  Vault 1".
- The AA qualification marker includes **`R4`** (added to
  `QualificationResult` for this).

### 8.2 Column changes on the apparatus tables

- **D is printed before E everywhere**, including MAG PH and all WAG
  apparatus (the opposite of §7.1). Keep reading the header (§3.1).
- MAG tables have separate `Pen.` and `Bonus` columns, either of which can
  be empty. Tokens collapse, so tell them apart by **sign**: bonus is always
  positive, penalty always negative.
- WAG vault prints the +0.2 as its own `Vault Bonus` column (not a `*`,
  §7.3); it goes to `Combined Bonus`. It sits *after* the attempt's Vault Score, while MAG's per-attempt
  bonus sits *before* it — 3-decimal tokens are scores, 1-decimal tokens
  are penalty/bonus.
- No `SJ Pen.` column appears anywhere, so every `SJ Penalty` is blank.
- Apparatus Finals tables have trailing `Qualification Rank` and `Start
  Order` columns, which are not captured — the qualification rank is the
  finalist's `Qualification Rank` from the `QUAL` file, so it can be joined
  rather than stored twice.
- An invalid second vault prints as `0.000 0.000 0.000` with the published
  average halved (`ZAPATA Rayderley`, MAG QUAL) → `Status2=INVALID_VAULT`.

### 8.3 Parsing the Team/All-Around report blocks

Each athlete spans three lines: `D` (D value + score per apparatus), `E`,
and `BP` for MAG / `EP` or `E P` for WAG. Apparatus are identified by
**column position**, not token order, because an athlete can skip any
apparatus:

- Team reports: match each D/score (or `DNS`) item to the team line's
  `(rank) total` columns by **right edge** — `DNS` is right-aligned under the
  score, so matching by start position put one in the wrong apparatus.
- E values go to the nearest D item; bonus/penalty values go to the
  closest D item at or left of them.
- Column order follows the icon legend: FX PH SR VT PB HB (MAG), VT UB BB FX
  (WAG). This was verified against the per-apparatus qualification tables,
  not just read off the legend (§7.8).
- pdftotext occasionally drops a row's NOC and total onto the next line or
  joins NOC to the name with one space; the identity regex has to tolerate
  both.

Checks that must all pass (they did, with zero exceptions):

1. `D + E + B + Penalty == Score` for every routine in every table.
2. Every Team/AA qualification-report score equals the same athlete's
   per-apparatus `SCORES-QUAL-<APP>` total (proves the column mapping).
3. Team apparatus totals equal the best three athlete scores; AA totals
   equal the apparatus sum; vault averages equal (V1+V2)/2 + Bonus.
4. Single- and double-vault lists agree on vault 1.
5. Row counts match an independent regex count; `PARTICIPANTS` totals
   match the "Number of Entries by NOC" page (231 men, 221 women).

### 8.4 Judges Assignments

- Grid order (rendered all eight pages): MAG FX/PH, SR/VT, PB/HB; WAG
  VT/UB, BB/FX.
- Apparatus supervisors appear in the Superior Jury block
  (`<Apparatus> Supervisor`) → `Function=SV` with that apparatus. Superior
  Jury rows have no NOC.
- Functions are spelled out: `Time Judge [n]` → `T`/`T1`/`T2`,
  `Line Judge [n]` → `L`/`L1`/`L2`. Reserves are a shared list (`R1`…`R6`)
  → `R<n>`, apparatus blank.
- The last reserve can land on the same text line as the `Legend:` label
  (MAG apparatus finals' `R6`) — a first pass silently dropped it.
- WAG has a `Vice-President` (→ `JudgeFunction.VICE_PRESIDENT`).

## 9. US Championships (USA Gymnastics ProScore) differences

`2026-US-CHAMPIONSHIPS-ALL-RESULTS.pdf` is a USA Gymnastics ProScore
printout, not a FIG results book: no footer codes, no NOCs, no FIG licences,
and names printed `Given Surname`. The parser is
[`tools/results-parsing/parse_2026_us_championships.py`](../tools/results-parsing/parse_2026_us_championships.py)
— run it with the PDF path; it reads `EVENT.csv` and the existing
`PARTICIPANTS` files (names, Surname/Given Name split and club, keyed by
bib) from the same folder and exits non-zero if any check below fails.

### 9.1 Competition format: one round in two parts

Each level competes the All-Around on two days. Day 1 is `Round=AA`,
`Part=1`; Day 2 is `Part=2`. The titles are decided on two-day combined
totals — the AA title on the combined AA total, each apparatus title on the
combined apparatus total — so the data model holds those combined standings
on a whole-round session (`Part` blank; `data-model.md` §3 `Session`, §5.3):

- `SUMMARY-FINAL-AA`: one row per athlete per part, plus a `Part`-blank row
  with the combined total and rank.
- `SUMMARY-FINAL-<APP>`: the combined apparatus standings (`Part` blank),
  with each day's figure alongside.

Day 2 has no per-day placements in the source, so `Rank` is blank on every
`Part=2` row. Athletes who only competed on Day 1 (several MAG Seniors) have
Day 1 rows only and no combined rows.

### 9.2 Reports used

| Report | Gives |
|---|---|
| Day 1 "Meet Results" | Day 1 routines (D/E, D+/E+, S+/ND, Final), apparatus places, AA total and place |
| Day 1 "Event Results - Vault" (MAG SR, WAG SR, WAG JR) | Day 1 two-vault list: both attempts, the day's vault result, and the Day 1 vault rank |
| Day 2 "Meet Results - Multi" | Day 2 routines ("Finals" block) and the combined AA total/rank; its "Prelims" block repeats Day 1 and is cross-checked against it |
| Day 2 "Event Results - Multi - <apparatus>" | Combined apparatus standings; for vault, both days' attempts |
| "USAG 10-Point Results" | National-team selection points — **not parsed** |

**The two-day reports number the days backwards:** `#1` is Day 2 and `#2`
is Day 1 (printed as the first column). Map by the heading, not by order.

### 9.3 Layout gotchas

- **Missing values print as underscores** (`_._`, `_.__`, `__.___`). An
  apparatus whose Final is `__.___` / `_.__` means the athlete wasn't entered
  on it (a specialist) — **no row is written**, even though Day 1 still prints
  a place for it (e.g. `45=`). A routine printed `0.00 / 0.000` was scratched
  → `Status=DNS`, blank scores and rank.
- **Two-day Meet Results omit the D/E pair for skipped apparatus** but still
  print placeholder D+/E+ and S+/ND pairs. Assign each line's values to
  apparatus by: every apparatus if the count matches, otherwise only the
  apparatus with a printed Final.
- **Day 1 apparatus places are positional** — the vault place is blank for
  levels with a vault event — so match each place to the Final value it sits
  under, never by order.
- **Tie markers** differ by report (`1T`, `45=`, `2*`); the stored rank is
  the number alone.
- **Asterisks before names** (`*Danila Leykin`) are stripped.
- **Vault.** MAG juniors vault once each day (their two-day vault report
  prints the empty second slot as `x/0.000`). MAG Seniors, WAG Seniors and WAG
  Juniors have a two-vault vault event each day, so their `SCORES-AA-VT`
  uses the double-attempt shape: vault 1 is the AA vault (counts for the AA),
  vault 2 and the day's two-vault result come from the vault lists. Day 1
  second vaults come from Day 1's vault list, because the two-day vault
  report omits athletes who didn't contest the vault title on both days
  (Day 1-only athletes, and e.g. WAG JR bib 31, who vaulted once on Day 2).
  Single vaulters don't contest the vault title and aren't in the two-day
  vault report. WAG's +0.2 (`Bns`) goes to `Combined Bonus`; MAG has none.
- **Bonuses.** MAG prints D+ (difficulty bonus), E+ (execution bonus),
  S+ (stick bonus) and ND (neutral deduction). `B` = D+ + E+ + S+, `Penalty`
  = ND, and D+/E+/S+ are kept as trailing breakdown columns
  (`import-file-model.md`). WAG prints only ND (and the vault `Bns`).
- **AA totals for partial all-arounders are printed** (e.g. a pommel
  specialist's AA total is his pommel score) and kept as published.
- No `NOC` is printed; every participant is written as `USA` (a national
  championship, and `Participant.noc` is required).

### 9.4 Checks (all pass)

1. D + E + D+ + E+ + S+ + ND = Final for every routine; apparatus Finals sum
   to the AA total.
2. Day 2's "Prelims" block equals Day 1's results; Day 1 + Day 2 AA = the
   combined total.
3. Each routine's day score equals the two-day apparatus report's figure
   for that day, for every athlete that report lists; the report's combined
   total equals the sum of its two days.
4. Vault 1 is identical in the AA results and the vault lists; each vault
   list's day result equals (V1 + V2)/2 + Bns; Day 1's vault list and the
   two-day vault report agree.
5. Every printed bib/name matches `PARTICIPANTS`.

Re-deriving the earlier hand-built files with this parser reproduced every
one of their 1,215 routine values exactly; the differences were the
intended ones (not-entered rows dropped, `UNKNOWN` → `DNS`, second vaults
and Day 1 vault ranks added).

## 10. Apparatus World Cup (Cottbus) differences

`2026-APPARATUS-WORLD-CUP-COTTBUS-ALL-RESULTS.pdf` is the organizer's (CABI-Berlin)
media results book for an FIG Apparatus World Cup. The parser is
[`tools/results-parsing/parse_2026_apparatus_world_cup_cottbus.py`](../tools/results-parsing/parse_2026_apparatus_world_cup_cottbus.py);
run it with the book's path. It exits non-zero if any check below fails.

### 10.1 Competition format

An apparatus competition only: **qualification and apparatus finals per
apparatus, no team or all-around round**, so there are no `SUMMARY-*` files.
Qualification on each apparatus is its own competition — its own start list,
day and judging panel — so a World Cup `QUAL` session carries an apparatus,
like `EVENT` (`data-model.md` §3, `Session.apparatus`). Each apparatus's
`SCORES-QUAL-<APP>` therefore maps to its own session, with its own date.

Vault qualification has a **single ranking**: every vaulter performs two
vaults and is ranked on the average, since there's no all-around for a
single counting vault. `Rank` and `Qualification Rank` are both that ranking.

### 10.2 Sources: a mostly scanned book

Of the book's 69 pages, only the entry lists (pp. 2–7) and the finals judges
lists (pp. 34, 61) have a text layer. Every results page, start list, medal
list and qualification judges list is a **scanned image**: `pdftotext` returns
nothing, and no OCR tool is installed. Render pages with `pdftoppm` (≈110 dpi
is readable) and transcribe them; to find pages, crop each page's title band
and stack them into one image (a pure-stdlib PGM→PNG stacker was enough).

The book is also **incomplete**: MAG vault qualification has 22 vaulters but
only its first results page (ranks 1–11) is included. A separate text PDF of
the qualification results (`...-MAG-QUAL.pdf`, from USA Gymnastics) covers all
MAG qualification and WAG floor/beam, and is used instead of the book for
those. Everything only available as an image is transcribed under
`tools/results-parsing/transcriptions/2026-APPARATUS-WORLD-CUP-COTTBUS/`
(format in its README).

### 10.3 Layout gotchas

- **Names print `Given SURNAME`** on entry lists and results (the surname is the
  trailing run of upper-case words: `Kilan VAN DER AA`, `Husam Moqbel Ali
  AL-JABERI`), and **`Given, SURNAME`** on judges lists (split on the comma;
  `Melanie, Jenkins` has a mixed-case surname).
- **The entry list prints NOC only on each country's first row.**
- **Neutral athletes and judges are `AIN1` / `AIN2`** — 4 characters, written as
  printed (`Participant.noc` accepts 3–4).
- **Q/R markers are missing from five qualification tables** (MAG FX, PH, SR;
  WAG VT, UB — the ones printed on the first day). The finals start lists name
  the 8 finalists and 3 ordered reserves, so the markers come from there
  (`FINALS-START-LISTS.txt`); the parser checks that the Q set equals the
  final's athletes.
- **WAG vault's +0.2 combined bonus is printed on the 2nd-vault line**, but it
  isn't part of that vault's score — it's added to the average (→ `Combined
  Bonus`). MAG prints a per-vault `0.1` bonus inside each vault score (→ `B1`/`B2`).
- **Bonus prints as `+0.1`** on non-vault tables; the `+` is dropped.
- **No Competition Schedule page.** Session dates are inferred from the results
  pages' print timestamps, which split cleanly: qualification MAG FX/PH/SR and
  WAG VT/UB on 19 Feb, the rest on 20 Feb; finals in the same split on 21/22 Feb.
  This is the §5.2a exception used as the only source, so confirm against the
  organizer's schedule if one turns up.
- **Judges**: `DS1` heads every panel — the Difficulty Supervisor (World Cup
  panels are 2 D judges, 5 E judges and 1 supervisor, per the 2025–2028 Apparatus
  World Cup rules), kept as `JudgeFunction.DS1`. WAG beam also prints a `pen5`:
  beam requires two time judges and the panel's other one is `Time 1`, so it's
  written as `T2`. `Line n` / `Time n` → `L1`/`L2`/`T1` (numbered even when
  there's only one). Superior Jury members aren't listed in this book.

### 10.4 Checks (all pass)

1. D + E + Penalty + Bonus = Total on every routine; each vault adds up, and the
   published vault total = the average (truncated to 3 decimals) + combined bonus.
2. Ranks are in order, totals never increase down the table, and tied ranks have
   equal totals.
3. Every transcribed qualification page that also exists in the text PDF (8
   pages, 227 rows) matches it field by field.
4. Every row's bib, name, birth year and NOC match the entry list; qualification
   row counts match the start lists' participant counts.
5. Each final's athletes are exactly the qualification's Q set.

Verified by planting a wrong digit in a transcription (caught by check 3) and in
a final (caught by check 1).

**Source inconsistency — the medal list is wrong:** the WAG vault final results
page (printed 21 Feb) has Teja BELAK 3rd (13.516, including the +0.2 combined
bonus) and Kohane USHIOKU 4th (13.333), but the book's medal list (printed 26
Feb) gives the bronze to USHIOKU. Other published results confirm BELAK's
bronze, so the CSV follows the results page. The general lesson: a book's
summary pages (medal lists, standings) can disagree with its own results
tables — treat the results table as the source and the summary as a
cross-check only.
