# Parsing Results PDFs into CSV

How to turn an official results PDF into the import files under
`data/<year>/<event>/`. This is the *how*: tools, techniques, checks, and what
each kind of source document does. The *what* — every file, column and its
database mapping — is [`import-file-model.md`](import-file-model.md); the
entities are in [`data-model.md`](data-model.md).

- The step-by-step procedure is the `parse-results` skill
  (`.claude/skills/parse-results/SKILL.md`), which refers back here.
- Parsers live in [`tools/results-parsing/`](../tools/results-parsing/): one
  `parse_<year>_<event>.py` per meet, sharing
  [`common.py`](../tools/results-parsing/common.py).
- When a new PDF breaks an assumption, add what you learned to the right
  section below (§4 conventions, §5 the source format) rather than appending a
  history.

## 1. Workflow

1. **Survey the PDF** — page count, which pages have text, which report types
   it contains, and that it is the competition, discipline and level you
   expect (§2.1–§2.3).
2. **Identify the source format** (§5) and the **competition structure**
   (§4.9); stop and discuss anything the data model can't represent yet.
3. **Write the meet's parser** on top of `common.py`; transcribe any scanned
   pages (§2.3).
4. **Run every check in §3**, on a scratch copy first; plant a wrong value to
   prove the checks catch it.
5. **Write the files in place**, including `<PREFIX>-IMPORT.json`, and compare
   against any files that already existed.
6. **Report oddities** and update these docs.

## 2. Tools and techniques

### 2.1 Text extraction

- **poppler-utils** (`brew install poppler`): `pdftotext`, `pdftoppm`,
  `pdfinfo`, `pdfimages`.
- **Always `pdftotext -layout`** — column alignment survives as whitespace,
  which every technique below relies on. Split the output on form feed
  (`\f`) to get physical pages; `pdftoppm -f/-l` wants physical page numbers,
  not the "Page X / Y" printed in footers.
- Parsers run with `python3 -I` (isolated mode; it also keeps the script's
  folder off `sys.path`, so they add it explicitly to import `common`).

### 2.2 Surveying a long PDF

Results books are concatenations of many small reports. Before parsing,
list every physical page with its report identity (footer code and title for
FIG books, header lines for others) and collapse runs of the same report.
Check what the book actually is: one "Cottbus" PDF turned out to be a rhythmic
gymnastics book from another competition.

### 2.3 Scanned pages

A page with no text layer gives `pdftotext` nothing (`pdfimages -list` shows a
full-page image). No OCR tool is installed; read the pages as images instead:

- **Map the book** with
  [`contact_sheet.py`](../tools/results-parsing/contact_sheet.py): it stacks
  each page's title band, labelled with the page number, into one image.
- **Read pages at ~110 dpi** (`pdftoppm -r 110 -png`, cropped to the table)
  and transcribe them into pipe-separated files under
  `tools/results-parsing/transcriptions/<EVENT>/` (format: the README there).
  The parser reads those files like any other source.
- **Look for a text copy** of the same results first (a federation or FIG
  website often has one) and use transcription only for what's missing; where
  both exist, compare them (§3).

### 2.4 Read the header row first, every time

Column presence and order differ between sources, disciplines and even
apparatus in the same book (`E` before `D` on some tables, an extra `Bonus`
column on others, Q/R columns on only some pages). Print the literal header
line before writing a parser for a table. Values always go to the fixed CSV
column names regardless of source order.

### 2.5 Tokenizing a fixed-width row

- **Split on two or more spaces** (`re.split(r'\s{2,}', …)`); single spaces
  occur only inside names.
- **Strip a trailing one-character marker first.** A right-aligned `Q` gets
  more leading spaces than `R1`/`NR`, so `13.833 R1` fuses on a naive split:
  match `\s+(Q|R\d|NR)\s*$` before tokenizing.
- **Never slice by fixed character offsets** taken from a header: columns
  drift by a character or two between rows.
- **Short rows are DNS/DNF rows** — detect them by token count, not by forcing
  the normal mapping. A placeholder `0.000` on a DNS row is not a score.
- **Penalty and bonus can share columns** and either may be empty; the sign
  separates them (bonus positive, sometimes printed `+0.1`; penalty negative).
  3-decimal tokens are scores, 1-decimal tokens are penalty/bonus.
- **pdftotext occasionally moves a value to the next line** (a NOC and total
  pushed below the name) or joins a NOC to the name with one space; identity
  regexes must tolerate both.

### 2.6 Positional parsing

When a line omits values for skipped apparatus, order alone can't tell which
value belongs where — match by column position:

- Places printed under a Final line: assign each to the Final value whose
  right edge it sits under, and fail if none is within a few columns.
- Values right-aligned under a total (team-report `DNS`): match on right edge,
  not start position.
- Lines that print either every apparatus (with placeholders) or only the
  performed ones: if the count equals the apparatus count, assign in order;
  if it equals the performed count, assign to those; otherwise fail.

### 2.7 Icons instead of text

Some column or panel labels are icons (FIG Judges Assignments, team and
all-around result columns). Render the page and confirm the order from the
picture; a printed legend can list the apparatus in a *different* order than
the columns. Then verify the mapping with data — e.g. an athlete's score in an
icon-headed column matches the same score in a text-labelled table.

## 3. Checks

Every parser runs these and exits non-zero on any failure (`common.Checks`).
A parser that silently drops or misplaces values is worse than one that stops.

1. **Arithmetic** — D + E + bonus + penalty (+ any itemized bonuses) = the
   printed score, for every routine.
2. **Aggregates** — all-around totals = sum of apparatus scores; team
   apparatus totals = best counting scores; a two-vault result = the average
   truncated to 3 decimals + any combined bonus; multi-part totals = the sum of
   the parts.
3. **Ranks** — never decrease down a table, totals never increase, tied ranks
   have equal totals.
4. **Counts** — rows parsed = rows counted by an independent regex (ranked
   rows + DNS markers); entries = the source's own totals ("Number of Entries",
   "34 Participants" on a start list).
5. **Cross-source agreement** — wherever two reports show the same thing,
   compare them: single vs double vault lists, team/all-around reports vs
   per-apparatus tables, a two-day report's Day 1 block vs Day 1's results,
   transcriptions vs a text copy, each final's athletes vs the qualifiers.
6. **Identity** — every bib's name, NOC (and birth year where printed) matches
   the entry list; results tables truncate long names (§4.2).
7. **Prove the checks** — plant one wrong digit in a source and one in a
   transcription, and confirm both are caught.
8. **Summary pages are a cross-check, not a source** — medal lists and
   standings can disagree with the results tables (Cottbus WAG vault bronze,
   §5.3). When they do, the results table wins; flag it.

Run on a scratch copy of the event folder first, diff against any existing
files, and only then write in place. A rerun should reproduce identical files.

## 4. Conventions when producing the files

The column-level standard (empty cells, explicit `Status`, preserving printed
formats, the metadata prefix) is in `import-file-model.md`. These are the
decisions that come up while parsing.

### 4.1 Names

- **`PARTICIPANTS` is the master name record.** Surname/Given Name are derived
  there once; results files carry `Bib` + `Name` only, and every results row's
  name is replaced by the participant's full name by bib.
- **Truncation happens anywhere a name is long** — narrow vault columns, but
  also normal tables (`BORDELAIS-MAISONNABLE` without his given name, initials
  like `KUBON CV` in all-around tables). Always diff against `PARTICIPANTS`.
- **Splitting** (`common.split_surname_first` / `split_given_first`):
  - FIG books print `SURNAME Given`: the surname is the leading run of
    upper-case words, keeping lowercase particles (`van POL`, `LIDE de LEON`)
    and `Mc` prefixes (`McDONALD`, `Mc CLENAGHAN`). Don't use "up to the last
    upper-case word" — a trailing initial (`SOTO RODRIGUEZ Jensuel J`) breaks it.
  - Organizer books may print `Given SURNAME` (Cottbus): the surname is the
    trailing upper-case run (`Kilan VAN DER AA`).
  - Judges lists may print `Given, SURNAME` (Cottbus): split on the comma
    (`Melanie, Jenkins` has a mixed-case surname).
  - US Championships prints `Given Surname` in mixed case; its Surname/Given
    split was curated by hand and is read from the existing `PARTICIPANTS`.
- **Strip decoration**: a leading `*` on US names.
- **Judges have no master file**, so `JUDGES` derives the split itself.

### 4.2 Ranks and markers

- Copy ranks as published, including duplicate ranks for ties and tie-breaks
  that order equal totals — never recompute (tie-break rules change by quad).
- Strip tie markers: `1T`, `45=`, `2*` → the number (`common.rank_number`).
- Leave `Rank` blank where the source publishes none (a DNS row, US Day 2,
  a Team Final with no per-apparatus ranks).
- The two-per-country rule never changes a rank; it shows as `NR`.

### 4.3 Status

- `COMPLETED`, `DNS` or `INVALID_VAULT`, always written.
- **Not entered on an apparatus** (a specialist's other apparatus, printed as
  underscores or simply absent): no row at all.
- **Entered but didn't perform**: `DNS`, blank scores and rank — including a
  US routine printed `0.00 / 0.000` (scratched).
- **Invalid vault**: a completed attempt scored `0.000` (Europeans WAG bib
  111 on vault 1; Worlds MAG ZAPATA's vault 2, whose average was halved).
- **Vault DNF** (vault 1 done, vault 2 not): `Status2=DNS`, blank `Total`.
- **All-around/team DNF** rows are kept with blank `Rank`/`Total`; a team
  missing a whole apparatus prints a blank column and `DNF` total.
- US all-around totals for partial all-arounders are printed and kept.

### 4.4 Session dates

- **From the Competition Schedule pages, never from a report footer's
  timestamp.** Footers are print times: Europeans WAG qualification was
  stamped 13 Aug but competed 12 Aug.
- **Read the schedule's time blocks, not just its labels.** A line naming
  "Junior Team Final" with the same time block as the qualification
  subdivisions is a relabel, not a separate session; a round only gets files
  if the PDF has its own results report.
- **A round over several days** (Worlds qualification) uses its first day.
- **Exception — splitting known days:** when the schedule gives two days for
  a set of finals but not which apparatus is on which, the per-apparatus print
  timestamps (tightly clustered within one of the known days) decide it
  (Europeans MAG SR finals: FX/PH/SR one day, VT/PB/HB the next).
- **No schedule at all** (Cottbus): infer from print timestamps, say so in the
  parser, and confirm with the user.

### 4.5 Vault

- **Two FIG listings in qualification**: a single-vault list (everyone, first
  vault, whole-field rank — the vault that counts for all-around and team) and
  a double-vault list (vault-final hopefuls: both vaults, average, its own
  rank, Q/R). Join them by bib; a double-list bib missing from the single list
  is an error. Take names from the single list (the double list's column
  truncates). Vault 1 must be identical in both.
- **Per-attempt vs combined bonus**: MAG prints a per-vault bonus inside each
  vault score (`B1`/`B2`). WAG's +0.2 applies to the combined result
  (`Combined Bonus`) — printed as a `*` on the total (Europeans), its own
  `Vault Bonus` column (Worlds), on the second vault's line but excluded from
  that vault's score (Cottbus), or as `Bns` (US).
- **Which shape a file uses** depends on the round and source — check the page:
  Team and AA finals are single-attempt at FIG events; US AA vault is
  double-attempt where a vault event is held; a World Cup's vault
  qualification has every vaulter do two vaults and a single ranking.

### 4.6 Qualification markers

- `Q`, `R1`–`R4`, `NR` as printed (Worlds' AA qualification has four
  reserves).
- `Qualification Rank` on every `QUAL` row: the double-vault rank for vault,
  equal to `Rank` elsewhere.
- **Markers not printed on the results table** but named on the final's start
  list (8 finalists + ordered reserves): take them from the start list, record
  it as a source file, and check the Q set equals the final's athletes.

### 4.7 Judges

| Printed | Written |
|---|---|
| `D1`, `D2`, `E1`–`E7` | as printed |
| `Time Judge`, `Time Judge 1`, `Time 1` | `T`, `T1`, `T1` |
| `Line Judge`, `Line Judge 1`, `Line 1` | `L`, `L1`, `L1` |
| `<Apparatus> Supervisor` (Superior Jury) | `SV`, with that apparatus |
| `DS1` (World Cup panel) | `DS1` — Difficulty Supervisor |
| `pen5` (Cottbus beam panel) | `T2` — beam has two time judges |
| `President`, `Vice-President`, `Member` | as printed, apparatus blank |
| Reserves `R1`… / one unnumbered reserve | `R1`… / `R`, apparatus blank unless the source assigns one |

Every value must resolve through `JudgeFunction.find`. The `Apparatus` column
is the two-letter code, never the spelled-out name. Superior Jury rows may
have no NOC (blank).

### 4.8 Countries

- IOC codes, as World Gymnastics uses them (`GER`, `NED`), for NOCs and the
  host country — not ISO.
- Neutral athletes keep the code printed: `AIN1`/`AIN2` (Cottbus), `WG2`
  (Europeans); the same athletes appear as `RUS`/`BLR` at Worlds.
- Some entry lists print the NOC only on each country's first row.
- The US Championships prints no NOC; participants are written as `USA`.

### 4.9 Competition structures

Map the meet onto `Session`s (`data-model.md` §3) before parsing:

| Structure | Sessions | Example |
|---|---|---|
| Championship | `QUAL`, `TEAM`, `AA`, `EVENT` per apparatus | Worlds, Europeans |
| Juniors without a team final | no `TEAM`; team summary has no Q markers | Europeans juniors |
| All-around decided in qualification | no `AA` session; `SUMMARY-QUAL-AA` only | Europeans WAG |
| Round split into parts, decided on the combined total | one session per part + a whole-round session (part blank) holding the combined standings | US Championships |
| Apparatus competition | `QUAL` and `EVENT` per apparatus; no team or AA | Apparatus World Cups |

If a meet doesn't fit any of these, stop and discuss the model change first.

## 5. Source formats

### 5.1 FIG / Swiss Timing results books (Europeans, Worlds)

A concatenation of reports, each stamped with a footer like
`GARM1APFX-------------QUAL--------_73I 1.0  <timestamp>  Page 1/5`.
Parsers: [`parse_2026_world_championships.py`](../tools/results-parsing/parse_2026_world_championships.py)
(the 2026 Europeans files predate the parsers and were built by hand).

**Finding reports**

- The footer code identifies a report template, `GAR` + `M`/`W` + report
  (`1APFX` apparatus, `1AA` all-around, `TEAM`, `GEN`) + round (`QUAL`, `FNL`)
  + suffix (`_73I` apparatus results, `_73J` vault double list, `_73C`/`_73D`
  team results, `_73G`/`_73H` all-around results, `_57A`/`_57B` judges, `_32D`
  entry list by bib, `_51x` start lists, `_77x` tie-break reports, `_92x`
  medallists).
- A page's footer describes the content above it.
- **Codes are reused for different reports** — e.g. Junior Men's apparatus
  final and all-around final pages share codes, and Worlds' "Men's
  Qualifications" (every athlete) and "Men's All-Around Qualification" share
  `_73G`. Always confirm by the printed title (and timestamp), never by the
  code alone.
- Entry list titles vary in case ("Entry List By BIB" for WAG Europeans).

**Apparatus tables**

- Europeans: `D, E, B` columns; MAG PH and every WAG apparatus print `E`
  before `D`; PH has no bonus. A `!` before a D score marks an inquiry — kept
  as part of the value.
- Worlds: `D` before `E` everywhere; separate `Pen.` and `Bonus` columns; no
  `SJ Pen.` column; finals add trailing `Qualification Rank` and `Start Order`
  columns (not captured — join to the `QUAL` file).
- Vault: `_73I` "Qualifications - Vault 1" is the single list, `_73J` the
  double list; a DNF shows as `DNF` in the total with `DNS` for vault 2.

**Team and all-around reports**

- Team results: a team summary line (`(rank) total` per apparatus) followed
  by each athlete's `D` line and `E` line (and `BP` bonus/penalty line for
  MAG, `EP`/`E P` for WAG), apparatus identified by column position (§2.6).
- All-around results: an identity line with `D Score (Rk)` per apparatus,
  then E and BP lines; the overall total can sit on its own line. A regex over
  those lines must allow `-` (a penalty) — a digits-and-dots class silently
  failed on penalized rows.
- At Worlds the Team Final and All-Around Final have **no per-apparatus
  report**; `SCORES-TEAM-<APP>`/`SCORES-AA-<APP>` are built from these blocks
  (no per-apparatus rank in the Team Final; the AA report's `(Rk)` is used).
- Column order follows the icons, e.g. MAG FX PH SR VT PB HB, WAG VT UB BB FX
  at Worlds; the WAG legend text lists them differently.

**Judges Assignments**

- Superior Jury block (President, Vice-President, Members, apparatus
  supervisors), then panels in an icon grid — MAG FX/PH, SR/VT, PB/HB; WAG
  VT/UB, BB/FX — split into left/right halves at the second `Function`
  heading, then reserves.
- The last reserve can share its text line with `Legend:` (a first pass
  dropped Worlds' R6).
- Europeans assign reserves to panels and use one unnumbered reserve on WAG
  finals (`R`); Worlds list reserves for the whole round.

**Other**

- Entry lists: Europeans print birth year; Worlds print full date of birth
  (`Birth Date`).
- Junior events may have no Team Final and no AA Final (Europeans).
- Checks run at Worlds: all of §3; team/AA blocks against the per-apparatus
  qualification tables proved the icon column order.

### 5.2 USA Gymnastics ProScore (US Championships)

A ProScore printout: no footer codes, no NOCs or FIG licences, names
`Given Surname`, values printed with underscores when empty (`_._`, `_.__`,
`__.___`). Parser:
[`parse_2026_us_championships.py`](../tools/results-parsing/parse_2026_us_championships.py)
(reads names, clubs and the Surname/Given split from the existing
`PARTICIPANTS` files).

**Structure.** Each level competes the all-around on two days (Day 1 =
`Part 1`, Day 2 = `Part 2`); titles are decided on two-day combined totals —
AA on the combined total, each apparatus on the combined apparatus total.
Combined standings go on the whole-round session: `SUMMARY-FINAL-AA` rows with
`Part` blank, and `SUMMARY-FINAL-<APP>`. Day 2 has no per-day places, so
`Rank` is blank on `Part 2` rows. Day-1-only athletes have no combined rows.

| Report | Gives |
|---|---|
| Day 1 "Meet Results" | Day 1 routines (D/E, D+/E+, S+/ND, Final), apparatus places, AA total and place |
| Day 1 "Event Results - Vault" (MAG SR, WAG SR, WAG JR) | the Day 1 two-vault list: both vaults, day result, rank |
| Day 2 "Meet Results - Multi" | Day 2 routines ("Finals" block), combined AA total and rank; its "Prelims" block repeats Day 1 (cross-checked) |
| Day 2 "Event Results - Multi - <apparatus>" | combined apparatus standings; for vault, both days' attempts |
| "USAG 10-Point Results" | national-team selection points — not parsed |

**Gotchas**

- **The two-day reports number the days backwards**: `#1` is Day 2, `#2` is
  Day 1 (printed first). Map by heading.
- Two-day Meet Results omit D/E pairs for skipped apparatus but print
  placeholder D+/E+ and S+/ND pairs (§2.6).
- Day 1 apparatus places are positional; the vault place is blank for levels
  with a vault event.
- **Bonuses**: MAG prints D+ (difficulty bonus), E+ (execution bonus, `0.0`
  on every 2026 routine), S+ (stick bonus) and ND (neutral deduction). `B` =
  D+ + E+ + S+, `Penalty` = ND, and D+/E+/S+ are kept as breakdown columns. WAG
  prints only ND (and the vault `Bns`).
- **Vault**: MAG juniors vault once each day (the two-day report prints an
  empty `x/0.000` second slot). MAG SR, WAG SR and WAG JR hold a two-vault
  event each day, so their `SCORES-AA-VT` is double-attempt: vault 1 is the AA
  vault, vault 2 and the day's result come from the vault lists. Day 1 second
  vaults come from Day 1's list — the two-day report omits anyone who didn't
  contest the vault title on both days. Single vaulters aren't in the vault
  title reports. The Day 1 vault rank is the two-vault list's rank.
- The two-day apparatus reports list only athletes who competed on that
  apparatus on both days.

### 5.3 Organizer results book, mostly scanned (Cottbus Apparatus World Cup)

The organizer's (CABI-Berlin) media book: entry lists and finals judges lists
have text; every results page, start list, medal list and qualification
judges list is a scanned image. The book also omits MAG vault qualification's
second results page. Parser:
[`parse_2026_apparatus_world_cup_cottbus.py`](../tools/results-parsing/parse_2026_apparatus_world_cup_cottbus.py),
which uses, in order: the book's text pages; a text PDF of the qualification
results from USA Gymnastics (`...-MAG-QUAL.pdf`: all MAG apparatus, WAG FX/BB);
transcriptions of everything else. The 227 transcribed rows that the text PDF
also covers matched it on every field.

**Structure.** An apparatus competition: qualification and finals per
apparatus, no team or all-around round, so no `SUMMARY-*` files. Each
apparatus's qualification is its own session (its own day and panel). Vault
qualification has a single ranking — every vaulter does two vaults, ranked on
the average.

**Gotchas**

- Names: `Given SURNAME` on entry lists and results, `Given, SURNAME` on
  judges lists; the entry list prints the NOC only on each country's first row.
- `AIN1`/`AIN2` neutral codes for athletes and judges.
- Q/R markers are missing from five qualification tables (MAG FX, PH, SR; WAG
  VT, UB — the first day's prints); they come from the finals start lists
  (`FINALS-START-LISTS.txt`).
- MAG prints bonuses as `+0.1`; WAG vault's +0.2 sits on the second vault's
  line but is a combined bonus.
- No Competition Schedule page: qualification MAG FX/PH/SR and WAG VT/UB on
  19 Feb, the rest on 20 Feb; finals in the same split on 21/22 Feb — inferred
  from print timestamps and confirmed with the user.
- Judges: `DS1` heads every panel; WAG beam has `pen5` (→ `T2`).
- **The book's medal list is wrong** for the WAG vault bronze: it names
  USHIOKU, but the results page — confirmed by other published results — has
  BELAK 3rd (13.516 with the combined bonus). The CSV follows the results page.

## 6. Open items

1. **Invalid vault appearance.** Only two cases seen, both a plain `0.000`
   with no marker; watch for a source that marks it differently.
2. **Europeans MAG all-around summaries** (`SUMMARY-QUAL-AA`,
   `SUMMARY-FINAL-AA`) were never built; the reports exist in that PDF.
3. **Europeans files were built by hand** before the parsers existed and lack
   the newer `Birth Date` column; there is no parser to regenerate them.
4. **`SV` vs `DS1`** may be the same role (an apparatus supervisor) seen in
   different contexts; kept separate for now.
5. **No Flyway migrations yet** (the schema exists only through Hibernate in
   tests). When they're written: the duplicate-session protection for null
   `apparatus`/`part` lives in `SessionService` and could become a Postgres
   partial unique index.
6. **US club names** (`Club` column) have no entity field.
