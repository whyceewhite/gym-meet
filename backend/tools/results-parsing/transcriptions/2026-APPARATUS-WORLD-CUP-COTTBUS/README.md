# Cottbus 2026 transcriptions

Most results pages in `2026-APPARATUS-WORLD-CUP-COTTBUS-ALL-RESULTS.pdf` are scanned
images with no text layer, so they were read by eye and transcribed here. The parser
(`../../parse_2026_apparatus_world_cup_cottbus.py`) reads these files, checks every row
arithmetically, and compares every transcribed qualification page that also exists in
the text PDF (`...-MAG-QUAL.pdf`) field by field.

Pipe-separated, one file per discipline/round/apparatus (`<DISC>-<ROUND>-<APP>.txt`):

- Non-vault rows: `rank|bib|year|name|D|E|pen|bonus|total|noc|q` — blank `rank` and
  `total=DNS` for a DNS row; `q` blank where the table prints no marker.
- Vault: an athlete line `rank|bib|year|name|total|noc|q`, then two attempt lines
  `v|D|E|pen|bonus|score`, then an optional `c|bonus` line for a bonus applied to the
  combined result (WAG's +0.2, printed on the 2nd-vault line in the source).
- Judges (`<DISC>-JUDGES-QUAL.txt`): `apparatus|function|Given, SURNAME|noc`, as printed.
- `FINALS-START-LISTS.txt`: finalists and ordered reserves per final.

Values are copied exactly as printed (`+0.1` keeps its sign).
