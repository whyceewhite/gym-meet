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
- **An empty `Status` cell means the attempt was completed normally.** Only
  write `Status` explicitly for an exception (`DNS`; see §5.5 for Vault's
  two-status case). This keeps the common case terse; document the
  convention rather than writing `COMPLETED` on every row.
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
| Birth Year | The PDF entry list gives only a birth **year**, not a full date — see §6 for the gap against `Athlete.birthDate`. |

### 5.4 `JUDGES.csv`

One row per judge per apparatus panel per round, all rounds in one file.

| Column | Notes |
|---|---|
| Round | `QUAL` / `EVENT` / `TEAM` / `AA` |
| Apparatus | The two-letter code (`FX`/`PH`/`SR`/`PB`/`HB`/`VT` for MAG, `FX`/`BB`/`UB`/`VT` for WAG), or **blank** for a Superior Jury row (President/Member — not tied to one apparatus). **Not** the spelled-out name the icon stands for (`Floor Exercise`, `Rings`, ...) — identifying the panel's apparatus by icon (§3.4) is a separate step from writing the column value, and the first pass at this file wrote the spelled-out label straight through for all four MAG/WAG `JUDGES.csv` files before it was caught and fixed. Map to the short code explicitly; don't let the icon lookup's own label leak into the output. |
| Function | `SV`, `D1`, `D2`, `E1`–`E7`, `T`, `L1`, `L2`, `RES 1`–`RES 3`, `L`, `President`, `Member` — copy exactly as printed, don't normalize |
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
| Status | Blank = completed. `DNS` if the athlete didn't start this apparatus. |
| D | Blank on PH's absent-Bonus rows is *not* a thing — PH just never has a `B` value; leave `D`/`E` populated normally |
| E | |
| B | Blank if the apparatus/row has no bonus value (e.g. every PH row) |
| Penalty | Blank when no penalty was applied (not `0`) |
| Total | Blank when `Status` is `DNS` |
| Qualification | `Q` / `R1` / `R2` / `R3` / `NR` / blank — matches `QualificationResult` exactly |

**Vault** (`VT`) — built by joining the single- and double-vault listings
(§4):

| Column | Notes |
|---|---|
| Round, Apparatus, Rank, Bib, Name, NOC Code | As above. `Rank` always comes from the **single-vault** list. |
| Status1 | Status of vault attempt 1. Blank = completed; `DNS` = never vaulted at all. |
| D1, E1, B1, Penalty1, Total1 | Vault 1 figures, from the single-vault list |
| Status2 | Status of vault attempt 2, **tri-state**: blank = no second attempt was made (this athlete isn't on the double-vault list at all); `COMPLETED` = a real second vault happened; `DNS` = a second vault was intended (athlete is on the double-vault list) but not completed. This distinction matters — don't collapse "opted out" and "started but didn't finish" into the same blank value. |
| D2, E2, B2, Penalty2, Total2 | Vault 2 figures, from the double-vault list. Blank whenever `Status2` is blank or `DNS`. |
| Total | The athlete's official counting result: `Total1` alone for a single-vault athlete, the published average for a completed two-vault athlete, blank when there's no valid combined result (`Status1=DNS` or `Status2=DNS`) |
| Qualification | `Q`/`R1`/`R2`/`R3`/`NR`/blank — only ever set from the double-vault list; always blank for single-vault-only athletes |
| Qualification Rank | This athlete's rank **within the double-vault list specifically** (i.e. among just the Vault-final hopefuls) — distinct from `Rank`. Blank for single-vault-only athletes. See §6 for the open question of how this maps to `data-model.md`. |

### 5.6 Results-by-apparatus, Apparatus Final round (`SCORES-EVENT-<APP>`)

The Apparatus Final report is much simpler than Qualification's — one flat
8-row table (the field is already cut down to the 8 finalists), a single
result each, no DNS cases observed, and **no qualification marker at all**
(there's no further round to qualify into). So this file is a plain subset
of the Qualification columns, dropping everything that only existed to
track qualification/DNS status:

**Non-vault apparatus:**

| Column | Notes |
|---|---|
| Round | `EVENT` (§5.2) |
| Apparatus | Two-letter code |
| Rank, Bib, Name, NOC Code | As in §5.5, minus any DNS handling — none seen yet in a Final; if one ever shows up, extend this file the same way §5.5/§3.3 do rather than force it into a full row |
| D, E, B, Penalty, Total | As in §5.5. PH again has no `B` column and prints `E` before `D` in the source — same header-order check from §3.1 applies |

No `Status` or `Qualification` column — there was nothing to put in them
for this round, so they're omitted rather than carried along empty. If a
future Final does have a withdrawal, add `Status` back in following the
same blank-means-completed convention as §5.5, rather than reusing Total's
old DNS-as-text trick from before that convention existed.

**Vault** (`VT`) — every finalist performs two vaults in the actual final
(unlike Qualification, where a second vault was optional), so there is only
one listing here, not two, and every row is fully populated:

| Column | Notes |
|---|---|
| Round, Apparatus, Rank, Bib, Name, NOC Code | As above |
| D1, E1, B1, Penalty1, Total1 | Vault 1 |
| D2, E2, B2, Penalty2, Total2 | Vault 2 |
| Total | The published average of the two vaults |

No `Status1`/`Status2` — both attempts are expected to be complete for
every finalist. `Qualification`/`Qualification Rank` don't apply either
(same reasoning as the non-vault Finals table above).

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
shape (no `Status`, no `Qualification`), same per-apparatus header-order
quirks — with one value changed and one structural surprise:

| Column | Notes |
|---|---|
| Round | `AA` (§5.2) — the one column that actually differs from §5.6 |
| Apparatus, Rank, Bib, Name, NOC Code, D, E, B, Penalty, Total | As in §5.6 |

**Vault is the single-attempt shape here** — `D`/`E`/`B`/`Penalty`/`Total`,
not the `D1`/`D2` split. This is the opposite of what the filenames alone
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
| D, E, B, Penalty, Total | As in §5.5/§5.6/§5.7. PH again has no `B` column and prints `E` before `D`. |

No `Status`, `Qualification`, or `Qualification Rank` columns — same
reasoning as §5.6/§5.7 (nothing to put in them; no further round to qualify
into, and no DNS was observed in this data set).

**Vault is a single-attempt table here** — unlike both Qualification's
double-vault list and the individual Vault Final (§5.6, though *not*
Junior's All-Around Final, §5.7 — check the page), a team-final vault
routine is only performed once per athlete, so `SCORES-TEAM-VT.csv` uses
the same plain `D`/`E`/`B`/`Penalty`/`Total` shape as every other apparatus in
this section, **not** the `D1`/`D2`-style split. Don't assume "Vault" always
means the two-attempt shape — check the actual page, the same way §3.1 says
to check the header row before assuming a column layout carries over.

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
| Qualification | `Q`/`R1`/`R2`/`R3`/`NR`/blank, same vocabulary as everywhere else. **`SUMMARY-FINAL-TEAM` omits this column entirely** — there's no further round to qualify into once the Team Final is done, same reasoning as §5.6/§5.7/§5.8 dropping it. |

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
   directly onto `Score.status`/`Score.secondStatus` without needing a new
   enum value. Confirm this is the intended reading of `secondStatus`'s
   "null means no second attempt occurred at all" note before importing.
3. **`INVALID_VAULT` not yet observed.** `data-model.md` has a status for a
   ruled-invalid vault attempt (board/landing-zone violation). This hasn't
   shown up in a source PDF yet — when it does, expect a distinct symbol or
   footnote marker in the results table (the NOTE sections in this PDF
   mention an `!` prefix on a D score for "under inquiry," which is a
   different thing — a score still stands but is flagged, not invalidated).
   Watch for it and update this doc with what it looks like on the page.
4. **Two ranks for Vault, one `rank` field on `Score`.** §5.5 keeps both
   `Rank` (whole-field, single-vault-based) and `Qualification Rank`
   (double-vault-list-only) in the CSV for full provenance. `data-model.md`'s
   `Score.rank` doesn't distinguish these — for every other apparatus the two
   concepts are identical, so this only bites Vault. Recommendation: import
   `Rank` into `Score.rank` (consistent with every other apparatus) and treat
   `Qualification Rank` as reference data only, not requiring its own column
   — but confirm this is acceptable before dropping it at import time.
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

### 7.2 No `B`/Bonus column on non-vault apparatus

Like MAG's PH, none of WAG's `FX`/`BB`/`UB` tables have a bonus column at
all — but for WAG this is true of every non-vault apparatus, not one
exception. Their `SCORES-QUAL-<APP>`/`SCORES-EVENT-<APP>`/`SCORES-TEAM-<APP>`
column set is `Round,Apparatus,Rank,Bib,Name,NOC Code,Status,D,E,Penalty,Total[,Qualification]`
— §5.5/§5.6/§5.8's tables, minus the `B` column entirely (not just left
blank).

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
  `Bonus` column (`0.2` or blank) sitting just before `Total`, with `Total`
  itself holding the clean number (asterisk stripped) — chosen over
  preserving the `*` inline, unlike the `!` inquiry marker (§5.2), because
  this value is immediately needed as a number downstream and the
  legacy WAG files already established that shape as reasonable.

Full `SCORES-QUAL-VT` column set:

```
Round,Apparatus,Rank,Bib,Name,NOC Code,
Status1,D1,E1,Penalty1,SJ Penalty1,Total1,
Status2,D2,E2,Penalty2,SJ Penalty2,Total2,
Bonus,Total,Qualification,Qualification Rank
```

`SCORES-EVENT-VT` is the same shape minus `Status1`/`Status2`/
`Qualification`/`Qualification Rank` (no DNS observed in a Final, no
further round to qualify into — same reasoning as §5.6). Both are built by
joining a single-vault list and a double-vault list exactly per §4 — WAG
has the identical two-listing structure, DNF/DNS conventions included.

**`SCORES-TEAM-VT` has neither column.** Confirmed by rendering the actual
page: Team Final vault is single-attempt or WAG (matching MAG, §5.8), with
plain `D,E,Penalty,Total` and no `SJ Pen.`/`*` at all. Don't assume Vault
always carries these two extra concepts — they showed up on the two
double-attempt reports and nowhere else.

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

### 7.10 What the schedule says exists and what the results PDF actually contains can disagree

The competition schedule pages (front of the PDF) list a **"Women's Junior
Team Final"** and both a **"Women's Senior/Junior Individual All-Around
Final"** as scheduled sessions. Neither has any corresponding results in
this PDF:

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

The general lesson: the schedule describes what was *planned*, not what
the results document *contains*. Confirm a round's results actually exist
as their own report (a real footer code, a real page title) before
building a file for it — don't infer a file should exist just because the
schedule names the round.

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
