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

```
<EVENT-PREFIX>-<SUFFIX>.csv
```

`<EVENT-PREFIX>` is exactly the `Prefix` column value in
`data/<year>/<event>/<EVENT>-EVENT.csv` (e.g.
`2026-EUROPEAN-CHAMPIONSHIPS-MAG-SR`) — i.e.
`<YEAR>-<EVENT-NAME>-<DISCIPLINE>-<LEVEL>`, where `DISCIPLINE` ∈ `MAG`/`WAG`
and `LEVEL` ∈ `SR`/`JR` (matching the `Discipline`/`AgeCategory` enums in
`data-model.md`).

`<SUFFIX>` is one of:

| Suffix | Contents | One file covers |
|---|---|---|
| `PARTICIPANTS` | Entry list (one row per athlete) | Every round, all apparatus |
| `JUDGES` | Judges Assignment (one row per judge per panel) | Every round in one file, distinguished by a `Round` column |
| `QUALS-<APP>` | Results By Apparatus, Qualification round | One apparatus, one round |
| `TEAM-<APP>` | Results By Apparatus, Team Final round (per-athlete detail feeding the team score) | One apparatus, one round |
| `FINALS-<APP>` | Results By Apparatus, (individual) Apparatus Final round | One apparatus, one round |
| `AA-<APP>` | Results By Apparatus, All-Around Final round | One apparatus, one round |
| `QUALS-TEAM` | Team Results, top-line team totals only, Qualification round | Every team, one round |
| `FINALS-TEAM` | Team Final Results, top-line team totals only, Team Final round | Every qualified team, one round |

The first segment is not a literal round name — it tracks whether the file
is **plain per-round detail** (`QUALS`, `TEAM`) or a **decisive placement
summary** (`FINALS`, `AA`), and the second segment is always the content: an
apparatus code, or the literal word `TEAM` meaning "team-standings
summary." A team standings summary is always a placement summary, in both
rounds it exists for — hence `QUALS-TEAM` *and* `FINALS-TEAM`, never
`TEAM-TEAM`. `TEAM-<APP>` is the one place `TEAM` names the first (round)
segment instead, and it pairs with an apparatus code precisely because
that file is per-athlete detail, not a summary — see §5.8 for the full
reasoning and the report each of these three actually comes from. This is
the same kind of position-disambiguated collision as the `SR`/`SR` one
described just below, not a naming mistake.

`AA-<APP>` exists because Junior competitions (unlike Senior, in this PDF)
have *both* an Apparatus Final and an All-Around Final round, and both
produce a full six-apparatus results-by-apparatus breakdown — so
`FINALS-<APP>` alone would be ambiguous for Juniors (which final?). `AA`
was picked as the second placement-summary prefix, alongside `FINALS`,
rather than spelling out the round in the filename (`ALLAROUND-<APP>` or
similar) — it's also the short form `data-model.md` already uses for this
round (`AA_FINAL`). See §5.6/§5.7 for the two files' actual column
differences, which turn out to matter more than the naming: Junior's Vault
is the two-attempt shape (§4) in `FINALS-VT` but a single-attempt shape in
`AA-VT` — the reverse of what the file *names* alone would suggest, so
don't assume based on the suffix; check the page.

`<APP>` is the two-letter `Apparatus` enum code from `data-model.md`: `FX`,
`PH`, `SR` (Still Rings), `PB`, `HB`, `VT` (MAG); `UB`, `BB` (WAG, plus
shared `FX`/`VT`).

> **Known ambiguity, accepted deliberately:** `SR` means Still Rings as an
> apparatus code but Senior as a level code, so a filename can contain `SR`
> twice with different meanings, e.g.
> `2026-EUROPEAN-CHAMPIONSHIPS-MAG-SR-QUALS-SR.csv` (Seniors, Rings). This
> mirrors FIG's own source codes (`GARM1APSR...`) rather than inventing a
> non-standard apparatus code — position in the filename disambiguates it
> (4th segment = level, 6th = apparatus).

Files not covered by a results PDF at all (`EVENT.csv`) are out of scope
here — see the existing file for its shape.

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
- `Round` values are **UPPER_SNAKE_CASE**, but the exact vocabulary depends
  on whether the file mixes rounds or is already round-scoped by its own
  filename:
  - `JUDGES.csv` bundles every round into one file (distinguished by this
    column), so it needs one value per round: `QUALIFICATION`,
    `APPARATUS_FINAL`, `TEAM_FINAL`, `ALL_AROUND_FINAL`.
  - A `QUALS-<APP>`/`QUALS-TEAM` file is already scoped to the Qualification
    round by its filename — use `QUALIFICATION`.
  - A `FINALS-<APP>` file is already scoped to one specific apparatus final
    by its filename (the `<APP>` segment) — use the plain `FINAL`, not
    `APPARATUS_FINAL`. Repeating "apparatus" would be redundant with the
    `Apparatus` column and the filename both already saying which one.
  - A `TEAM-<APP>` file is scoped to the Team Final round by its filename —
    use `TEAM_FINAL` (this one *does* match `JUDGES.csv`'s vocabulary,
    unlike `FINAL` above, since "Team Final" isn't apparatus-specific the
    way "Apparatus Final" is — the round has one name regardless of which
    of the six apparatus files you're looking at).
  - This is a *simpler* vocabulary than `data-model.md`'s `Round` enum
    (which has a separate `FX_FINAL`/`PH_FINAL`/etc. per apparatus, and
    `AA_FINAL` instead of `ALL_AROUND_FINAL`) — see §6 for the exact
    translation to use at import time, now covering `FINAL` +
    `Apparatus=<APP>` → `<APP>_FINAL` too.

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
| Round | `QUALIFICATION` / `APPARATUS_FINAL` / `TEAM_FINAL` / `ALL_AROUND_FINAL` |
| Apparatus | One of the six two-letter codes, or **blank** for a Superior Jury row (President/Member — not tied to one apparatus) |
| Function | `SV`, `D1`, `D2`, `E1`–`E7`, `T`, `L1`, `L2`, `RES 1`–`RES 3`, `L`, `President`, `Member` — copy exactly as printed, don't normalize |
| Name | Combined name as printed |
| Surname | **Derived, every time.** There is no separate master file for judges (unlike athletes), so this file derives the split itself using the rule in §5.10. |
| Given Name | Derived, same rule |
| NOC Code | |

### 5.5 Results-by-apparatus, Qualification round (`QUALS-<APP>`)

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

### 5.6 Results-by-apparatus, Apparatus Final round (`FINALS-<APP>`)

The Apparatus Final report is much simpler than Qualification's — one flat
8-row table (the field is already cut down to the 8 finalists), a single
result each, no DNS cases observed, and **no qualification marker at all**
(there's no further round to qualify into). So this file is a plain subset
of the Qualification columns, dropping everything that only existed to
track qualification/DNS status:

**Non-vault apparatus:**

| Column | Notes |
|---|---|
| Round | `FINAL` (§5.2) |
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

### 5.7 Results-by-apparatus, All-Around Final round (`AA-<APP>`)

Only seen so far for Junior Men, which — unlike the Senior data this
standard was first built from — has a separate All-Around Final round in
addition to the Apparatus Final, each with its own full six-apparatus
results-by-apparatus breakdown. See §5.1 for why this gets a distinct
`AA-<APP>` filename rather than colliding with `FINALS-<APP>`.

Columns and conventions are otherwise identical to §5.6 — same trimmed-down
shape (no `Status`, no `Qualification`), same per-apparatus header-order
quirks — with one value changed and one structural surprise:

| Column | Notes |
|---|---|
| Round | `ALL_AROUND_FINAL` (§5.2) — the one column that actually differs from §5.6 |
| Apparatus, Rank, Bib, Name, NOC Code, D, E, B, Penalty, Total | As in §5.6 |

**Vault is the single-attempt shape here** — `D`/`E`/`B`/`Penalty`/`Total`,
not the `D1`/`D2` split. This is the opposite of what the filenames alone
would suggest (`FINALS-VT` is the two-attempt shape, per §5.6; `AA-VT` is
the plain one), and it makes sense once you think about what each round is
actually for: the Apparatus Final's vault specialists are being judged on
vault specifically, so they get two attempts; an All-Around finalist's
vault score is just one contributor to a six-apparatus total, so it's a
single attempt like every other apparatus in their all-around routine. The
takeaway isn't "AA is always single-attempt" so much as **check the actual
page's header before assuming either shape carries over** — the same
caution as §3.1 and the `TEAM-VT` note in §5.8.

### 5.8 Results-by-apparatus, Team Final round (`TEAM-<APP>`)

In the Team Final, the qualified teams' athletes perform each apparatus
again — this is the per-athlete result feeding into the team score (§5.9
covers the team-level summary those add up to). Structurally this report is
just like Qualification (§5.5) with the qualification-tracking columns
removed, same as the individual Apparatus Final (§5.6) and All-Around
Final (§5.7):

| Column | Notes |
|---|---|
| Round | `TEAM_FINAL` (§5.2) |
| Apparatus | Two-letter code. Like Judges Assignment (§3.4) and the `QUALS-TEAM` top-line report (§5.9), the apparatus is an **icon** in the page header, not text — confirmed here by rendering all six pages, same FX/PH/SR/VT/PB/HB order as everywhere else. |
| Rank, Bib, Name, NOC Code | As in §5.5/§5.6/§5.7. Ties are common here (the field size is fixed at "qualified teams × roster count on that apparatus," not a ranked cut, so more ties survive than in Qualification/individual Finals) — copy duplicate ranks as printed. |
| D, E, B, Penalty, Total | As in §5.5/§5.6/§5.7. PH again has no `B` column and prints `E` before `D`. |

No `Status`, `Qualification`, or `Qualification Rank` columns — same
reasoning as §5.6/§5.7 (nothing to put in them; no further round to qualify
into, and no DNS was observed in this data set).

**Vault is a single-attempt table here** — unlike both Qualification's
double-vault list and the individual Vault Final (§5.6, though *not*
Junior's All-Around Final, §5.7 — check the page), a team-final vault
routine is only performed once per athlete, so `TEAM-VT.csv` uses the
same plain `D`/`E`/`B`/`Penalty`/`Total` shape as every other apparatus in
this section, **not** the `D1`/`D2`-style split. Don't assume "Vault" always
means the two-attempt shape — check the actual page, the same way §3.1 says
to check the header row before assuming a column layout carries over.

### 5.9 Team Results, top-line only (`QUALS-TEAM`, `FINALS-TEAM`)

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
it. (That per-athlete detail is a *different* report from `TEAM-<APP>`,
§5.8, too — §5.8 is the official per-apparatus results feeding into
scoring, individually paginated per apparatus; the breakdown embedded in
*this* report is the same underlying scores laid out per-team instead, with
the drop-lowest score marked in italics.)

Juniors, at least in this PDF, have no Team Final at all — `QUALS-TEAM`
exists for them but there's no `FINALS-TEAM` counterpart, and the
Qualification-round summary carries no `Qualification` marker for any team
(confirmed: the letter `Q` doesn't appear anywhere in that report) since
there's no team round to qualify into. Don't treat a missing `FINALS-TEAM`
file as a parsing gap for a level/discipline that turns out not to have
the round at all.

Like Judges Assignment (§3.4), the six apparatus columns are headed by an
**icon**, not text — confirmed by rendering the page. The order was the same
FX, PH, SR, VT, PB, HB used everywhere else in this document, cross-checked
against an athlete's individual apparatus score elsewhere in the same PDF
(e.g. a team's first column total included a `14.833` matching that
athlete's already-parsed `QUALS-FX.csv` row).

| Column | Notes |
|---|---|
| Round | `QUALIFICATION` for `QUALS-TEAM`, `TEAM_FINAL` for `FINALS-TEAM` — see the naming note below for why the file segment doesn't literally match this value for the Final |
| Rank | Team placement. Ties/no ties as printed. |
| NOC Code | The report also prints a full team/federation name (e.g. `World Gymnastics 2`, `Great Britain`) — **not captured**, since `NOC Code` is enough to join back to `PARTICIPANTS.csv`/`Athlete.country` and the standard doesn't otherwise carry a display name column. |
| FX Total, PH Total, SR Total, VT Total, PB Total, HB Total | Each apparatus's counting team total (already the sum/drop-lowest result printed by the source — not recomputed) |
| SJ Penalty | Superior Jury penalty applied to the team, distinct from an individual routine's `Penalty`. Blank when none was applied — every team in both rounds had a blank value here, so this column is unverified against a real example; watch for one in a future PDF and confirm the parsing still holds. |
| Total | Team's grand total |
| Qualification | `Q`/`R1`/`R2`/`R3`/`NR`/blank, same vocabulary as everywhere else. **`FINALS-TEAM` omits this column entirely** — there's no further round to qualify into once the Team Final is done, same reasoning as §5.6/§5.7/§5.8 dropping it. |

**Naming note, correcting an earlier guess in this doc:** a previous
revision of §5.1 speculated a Team-Final top-line file would be named
`TEAM-TEAM.csv`. It's actually `FINALS-TEAM` — the filename's first segment
tracks "is this a plain per-round detail file (`QUALS`/`TEAM`) or a
decisive placement summary (`FINALS`)" rather than literally naming the
round, and a team standings summary is a placement summary either way,
Qualification included. So the two `TEAM`-content files are
`QUALS-TEAM` (Qualification round) and `FINALS-TEAM` (Team Final round) —
`TEAM` never appears as the first segment paired with a `TEAM` second
segment. Contrast this with `TEAM-<APP>` (§5.8), where `TEAM` *is* the first
segment, but paired with an apparatus code — that file is per-athlete
apparatus detail from the Team Final, not a placement summary, which is
exactly the distinction the first segment is encoding.

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

1. **`Round` granularity.** This CSV standard uses a flat
   `QUALIFICATION` / `FINAL` / `APPARATUS_FINAL` / `TEAM_FINAL` /
   `ALL_AROUND_FINAL` vocabulary (§5.2), because a results-by-apparatus file
   is already scoped to one apparatus by its filename and `Apparatus`
   column. `data-model.md`'s `Round` enum is more granular (`FX_FINAL`,
   `PH_FINAL`, ... one per apparatus, and `AA_FINAL` rather than
   `ALL_AROUND_FINAL`). The intended translation at import time:
   - `QUALIFICATION` → `QUALIFICATION`
   - `TEAM_FINAL` → `TEAM_FINAL`
   - `ALL_AROUND_FINAL` → `AA_FINAL`
   - `FINAL` + `Apparatus=<APP>` (a `FINALS-<APP>` file) → `<APP>_FINAL`
     (e.g. `FX_FINAL`)
   - `APPARATUS_FINAL` + `Apparatus=<APP>` (a `JUDGES.csv` row) →
     `<APP>_FINAL` — same target as above, just arriving with a different
     source value because `JUDGES.csv` mixes rounds in one file while a
     `FINALS-<APP>` file doesn't need to
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
