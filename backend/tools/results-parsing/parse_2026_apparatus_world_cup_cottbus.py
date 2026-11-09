"""Parse the 2026 Apparatus World Cup Cottbus results into the CSV standard.

Usage (needs poppler's pdftotext on PATH):

    python3 -I parse_2026_apparatus_world_cup_cottbus.py \\
        data/2026/2026-APPARATUS-WORLD-CUP-COTTBUS/2026-APPARATUS-WORLD-CUP-COTTBUS-ALL-RESULTS.pdf

Writes PARTICIPANTS / JUDGES / SCORES-QUAL-<APP> / SCORES-EVENT-<APP> CSVs and an IMPORT.json
per discipline next to the PDF, reading event metadata from the EVENT.csv already there.
Exits non-zero if any self-check fails (docs/instructions-results-parsing.md §3; this source format is §5.3).

Sources, in order of preference:
- the results book's text pages: entry lists and the finals judges lists;
- `...-MAG-QUAL.pdf` (a text PDF of the qualification results: all MAG apparatus, WAG FX/BB);
- `transcriptions/2026-APPARATUS-WORLD-CUP-COTTBUS/` for everything that only exists as a
  scanned image in the book (see the README there). Transcribed pages that the text PDF also
  covers are compared field by field rather than used.
"""
import os, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common
from common import D, split_given_first

PDF = sys.argv[1]
OUT = os.path.dirname(os.path.abspath(PDF))
EVENT_PREFIX = '2026-APPARATUS-WORLD-CUP-COTTBUS'
QUAL_PDF = os.path.join(OUT, f'{EVENT_PREFIX}-MAG-QUAL.pdf')
TRANSCRIPTIONS = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'transcriptions', EVENT_PREFIX)

BOOK = common.pdf_pages(PDF)
QUAL = common.pdf_pages(QUAL_PDF)
EVENTS = common.read_events(OUT, EVENT_PREFIX)
out_files = common.Output(OUT)
checks = common.Checks()
err = checks.err

APPS = {'MAG': ['FX', 'PH', 'SR', 'VT', 'PB', 'HB'], 'WAG': ['VT', 'UB', 'BB', 'FX']}
APP_NAMES = {'Floor': 'FX', 'Pommel Horse': 'PH', 'Still Rings': 'SR', 'Vault': 'VT', 'Parallel Bars': 'PB',
             'Horizontal Bar': 'HB', 'Uneven Bars': 'UB', 'Balance Beam': 'BB'}
# The book has no Competition Schedule page. Days are inferred from the results pages' print
# timestamps, which cluster cleanly into two qualification days and two finals days (§5.3).
DAY = {'MAG': {'FX': 1, 'PH': 1, 'SR': 1, 'VT': 2, 'PB': 2, 'HB': 2},
       'WAG': {'VT': 1, 'UB': 1, 'BB': 2, 'FX': 2}}
DATES = {'QUAL': {1: '2026-02-19', 2: '2026-02-20'}, 'EVENT': {1: '2026-02-21', 2: '2026-02-22'}}
# Participant counts printed on each qualification start list (book pages 14-58).
START_COUNTS = {'MAG': {'FX': 34, 'PH': 39, 'SR': 23, 'VT': 22, 'PB': 28, 'HB': 32},
                'WAG': {'VT': 23, 'UB': 26, 'BB': 34, 'FX': 26}}

# ---------------------------------------------------------------- participants (book text)
def participants(disc):
    out, noc = [], None
    for page in BOOK:
        if f'Entry List {disc}' not in page:
            continue
        for l in page.split('\n'):
            m = re.match(r'^\s+(\d{3})\s+(\d{4})\s+(\d+)\s+(\S.*?)(?:\s{2,}(.+?)\s+([A-Z]{3}\d?))?\s*$', l)
            if not m:
                continue
            bib, year, lic, name, _, code = m.groups()
            noc = code or noc   # the NOC is printed only on each country's first row
            surname, given = split_given_first(name)
            if not surname or not given:
                err(f'{disc} participants: name split {name!r}')
            out.append(dict(Bib=bib, Year=year, License=lic, Name=name, NOC=noc, Surname=surname, Given=given))
    expected = int(re.search(rf'Entry List {disc}\s+(\d+) Participants', ' '.join(BOOK)).group(1))
    if len(out) != expected:
        err(f'{disc} participants: {len(out)} parsed, entry list says {expected}')
    return out

# ---------------------------------------------------------------- results rows
def bp(tokens, ctx):
    """Penalty and bonus share the row; the sign tells them apart (bonus printed +0.1 or 0.1)."""
    pen = bonus = ''
    for t in tokens:
        if t.startswith('-'):
            if pen: err(f'{ctx}: two penalties')
            pen = t
        else:
            if bonus: err(f'{ctx}: two bonuses')
            bonus = t.lstrip('+')
    return pen, bonus

def parse_qual_pdf():
    """Qualification results from the text PDF -> {(disc, app): [rows]}."""
    out = {}
    for page in QUAL:
        m = re.search(r'Qualification (Men|Women)', page)
        if not m:
            continue
        disc = 'MAG' if m.group(1) == 'Men' else 'WAG'
        app = APP_NAMES[re.search(r'Result\s+(.+?)\s{2,}Print', page).group(1).strip()]
        rows = out.setdefault((disc, app), [])
        for l in page.split('\n'):
            ctx = f'{disc} QUAL {app} text PDF'
            m = re.match(r'^\s*(\d+)\.\s+(\d{3})\s+(\d{4})\s+(.+?)\s{2,}(\d\.\d)\s+(\d+\.\d{3})((?:\s+[-+]\d+\.\d)*)'
                         r'\s+(\d+\.\d{3})\s+([A-Z]{3}\d?)(?:\s+(Q|R\d))?\s*$', l)
            if m:
                rank, bib, year, name, d, e, extra, total, noc, q = m.groups()
                pen, bonus = bp(extra.split(), ctx)
                rows.append(dict(Rank=rank, Bib=bib, Year=year, Name=name.strip(), NOC=noc, Q=q or '',
                                 Status='COMPLETED', D=d, E=e, Penalty=pen, B=bonus, Total=total))
                continue
            m = re.match(r'^\s*(\d+)\.\s+(\d{3})\s+(\d{4})\s+(.+?)\s{2,}(\d+\.\d{3})\s+([A-Z]{3}\d?)(?:\s+(Q|R\d))?\s*$', l)
            if m:
                rank, bib, year, name, total, noc, q = m.groups()
                rows.append(dict(Rank=rank, Bib=bib, Year=year, Name=name.strip(), NOC=noc, Q=q or '',
                                 Status='COMPLETED', Total=total, vaults=[], Combined=''))
                continue
            m = re.match(r'^\s+(?:1st|2nd) Vault\s+(\d\.\d)\s+(\d+\.\d{3})((?:\s+[-+]?\d+\.\d)*)\s+(\d+\.\d{3})\s*$', l)
            if m:
                d, e, extra, score = m.groups()
                pen, bonus = bp(extra.split(), ctx)
                rows[-1]['vaults'].append(dict(Status='COMPLETED', D=d, E=e, Penalty=pen, B=bonus, Total=score))
                continue
            m = re.match(r'^\s+(\d{3})\s+(\d{4})\s+(.+?)\s{2,}DNS\s+([A-Z]{3}\d?)\s*$', l)
            if m:
                bib, year, name, noc = m.groups()
                rows.append(dict(Rank='', Bib=bib, Year=year, Name=name.strip(), NOC=noc, Q='', Status='DNS',
                                 D='', E='', Penalty='', B='', Total='', vaults=[], Combined=''))
                continue
            if re.match(r'^\s*\d+\.\s+\d{3}\s', l) or ' DNS ' in l:
                err(f'{ctx}: unparsed {l!r}')
    return out

def parse_transcription(disc, rnd, app):
    path = os.path.join(TRANSCRIPTIONS, f'{disc}-{rnd}-{app}.txt')
    if not os.path.exists(path):
        return None
    rows = []
    for l in open(path, encoding='utf-8'):
        l = l.rstrip('\n')
        if not l or l.startswith('#'):
            continue
        f = l.split('|')
        if f[0] == 'v':
            rows[-1]['vaults'].append(dict(Status='COMPLETED', D=f[1], E=f[2], Penalty=f[3], B=f[4].lstrip('+'),
                                           Total=f[5]))
        elif f[0] == 'c':
            rows[-1]['Combined'] = f[1]
        elif app == 'VT' and len(f) == 7:
            rank, bib, year, name, total, noc, q = f
            dns = total == 'DNS'
            rows.append(dict(Rank=rank, Bib=bib, Year=year, Name=name, NOC=noc, Q=q, Total='' if dns else total,
                             vaults=[], Combined='', Status='DNS' if dns else 'COMPLETED'))
        else:
            rank, bib, year, name, d, e, pen, bonus, total, noc, q = f
            dns = total == 'DNS'
            rows.append(dict(Rank=rank, Bib=bib, Year=year, Name=name, NOC=noc, Q=q, Status='DNS' if dns else 'COMPLETED',
                             D=d, E=e, Penalty=pen, B=bonus.lstrip('+'), Total='' if dns else total))
    return rows

def check_rows(rows, ctx, vault):
    for r in rows:
        c = f'{ctx} bib {r["Bib"]}'
        if r.get('Status') == 'DNS' or (vault and not r.get('vaults') and r['Total'] == ''):
            continue
        if not vault:
            checks.check_sum(c, r['Total'], r['D'], r['E'], r['Penalty'], r['B'])
            continue
        if len(r['vaults']) != 2:
            err(f'{c}: {len(r["vaults"])} vaults'); continue
        for n, v in enumerate(r['vaults'], 1):
            checks.check_sum(f'{c} vault {n}', v['Total'], v['D'], v['E'], v['Penalty'], v['B'])
        checks.check_vault_average(c, r['vaults'][0]['Total'], r['vaults'][1]['Total'], r['Combined'], r['Total'])
    checks.check_rank_order(ctx, rows)

def compare(text_rows, tx_rows, ctx):
    """Field-by-field comparison of a transcribed page against the same page as text."""
    t = {r['Bib']: r for r in text_rows}
    for r in tx_rows:
        o = t.get(r['Bib'])
        if o is None:
            err(f'{ctx}: transcribed bib {r["Bib"]} not in text PDF'); continue
        for k in ('Rank', 'Year', 'Name', 'NOC', 'Q', 'D', 'E', 'Penalty', 'B', 'Total', 'Combined'):
            if k in r and k in o and str(r[k]) != str(o[k]):
                err(f'{ctx} bib {r["Bib"]} {k}: transcribed {r[k]!r}, text {o[k]!r}')
        for i, (v, w) in enumerate(zip(r.get('vaults', []), o.get('vaults', []))):
            if v != w:
                err(f'{ctx} bib {r["Bib"]} vault {i + 1}: transcribed {v}, text {w}')

# ---------------------------------------------------------------- judges
# 'pen5' appears once per beam panel; beam requires two time judges, so it's the second (§5.3).
FUNCTION = {'Line 1': 'L1', 'Line 2': 'L2', 'Time 1': 'T1', 'Time 2': 'T2', 'pen5': 'T2'}

def finals_judges(disc):
    """Two side-by-side panels per block; apparatus names head each block."""
    page = next(p for p in BOOK if f'Judge List Finals {"Men" if disc == "MAG" else "Women"}' in p)
    out, left, right = [], None, None
    for l in page.split('\n'):
        names = [n for n in APP_NAMES if re.search(rf'\b{n}\b', l)]
        if names and 'Position' not in l and not re.search(r'\b(DS1|D\d|E\d)\b', l):
            hits = sorted((l.index(n), APP_NAMES[n]) for n in names)
            left, right = hits[0][1], hits[-1][1]
            continue
        for m in re.finditer(r'(DS1|D\d|E\d|Line \d|Time \d|pen\d)\s+(\S[^,]*?, \S.*?)\s{2,}([A-Z]{3}\d?)', l):
            app = left if m.start() < 60 else right
            out.append((app, m.group(1), m.group(2).strip(), m.group(3)))
    return out

def qual_judges(disc):
    rows = []
    for l in open(os.path.join(TRANSCRIPTIONS, f'{disc}-JUDGES-QUAL.txt'), encoding='utf-8'):
        if l.strip() and not l.startswith('#'):
            rows.append(tuple(l.rstrip('\n').split('|')))
    return rows

# ---------------------------------------------------------------- output
def start_lists():
    out = {}
    for l in open(os.path.join(TRANSCRIPTIONS, 'FINALS-START-LISTS.txt'), encoding='utf-8'):
        if l.strip() and not l.startswith('#'):
            disc, app, fin, res = l.strip().split('|')
            out[(disc, app)] = (fin.split(','), res.split(','))
    return out

def level(disc, qual_text, starts):
    prefix = f'{EVENT_PREFIX}-{disc}-SR'
    ev = EVENTS[prefix]
    people = participants(disc)
    by_bib = {p['Bib']: p for p in people}
    base = {'Event Name': ev['Title'], 'Discipline': disc, 'Age Category': 'SR'}

    def check_identity(rows, ctx):
        for r in rows:
            p = by_bib.get(r['Bib'])
            if p is None:
                err(f'{ctx}: bib {r["Bib"]} not on the entry list'); continue
            for k in ('Name', 'Year', 'NOC'):
                if r[k] != p[k]:
                    err(f'{ctx} bib {r["Bib"]} {k}: {r[k]!r} vs entry list {p[k]!r}')

    for app in APPS[disc]:
        vault = app == 'VT'
        for rnd in ('QUAL', 'EVENT'):
            ctx = f'{disc} {rnd} {app}'
            tx = parse_transcription(disc, rnd, app)
            text = qual_text.get((disc, app)) if rnd == 'QUAL' else None
            if text is not None and tx is not None:
                compare(text, tx, ctx)
            rows = text if text is not None else tx
            if rows is None:
                err(f'{ctx}: no source'); continue
            check_rows(rows, ctx, vault)
            check_identity(rows, ctx)
            if rnd == 'QUAL':
                if len(rows) != START_COUNTS[disc][app]:
                    err(f'{ctx}: {len(rows)} rows, start list has {START_COUNTS[disc][app]}')
                if (disc, app) in starts:
                    fin, res = starts[(disc, app)]
                    if any(r['Q'] for r in rows):
                        err(f'{ctx}: table prints markers and a start list was also supplied')
                    for r in rows:
                        r['Q'] = 'Q' if r['Bib'] in fin else (f'R{res.index(r["Bib"]) + 1}' if r['Bib'] in res else '')
                finalists = sorted(r['Bib'] for r in rows if r['Q'] == 'Q')
                final = parse_transcription(disc, 'EVENT', app)
                if final is not None and finalists != sorted(r['Bib'] for r in final):
                    err(f'{ctx}: Q markers {finalists} != finalists in the final')
            date = DATES[rnd][DAY[disc][app]]
            out = []
            for r in rows:
                row = base | {'Session Start Date': date, 'Round': rnd, 'Part': '', 'Apparatus': app,
                              'Rank': r['Rank'], 'Bib': r['Bib'], 'Name': by_bib[r['Bib']]['Name'],
                              'NOC Code': r['NOC']}
                if vault:
                    vs = r['vaults'] or [None, None]
                    for n, v in zip((1, 2), vs):
                        v = v or dict(Status='DNS', D='', E='', Penalty='', B='', Total='')
                        row |= {f'Status{n}': v['Status'] if r['Status'] != 'DNS' else 'DNS', f'D{n}': v['D'],
                                f'E{n}': v['E'], f'B{n}': v['B'], f'Penalty{n}': v['Penalty'],
                                f'SJ Penalty{n}': '', f'Total{n}': v['Total']}
                    row |= {'Combined Bonus': r['Combined'], 'Total': r['Total']}
                else:
                    row |= {'Status': r['Status'], 'D': r['D'], 'E': r['E'], 'B': r['B'], 'Penalty': r['Penalty'],
                            'SJ Penalty': '', 'Total': r['Total']}
                if rnd == 'QUAL':
                    # a World Cup has one qualification ranking per apparatus, vault included
                    row |= {'Qualification': r['Q'], 'Qualification Rank': r['Rank']}
                out.append(row)
            out_files.csv(f'{prefix}-SCORES-{rnd}-{app}.csv', common.score_header(vault, rnd == 'QUAL'), out)

    # ---- judges
    rows = []
    for rnd, panel in (('QUAL', qual_judges(disc)), ('EVENT', finals_judges(disc))):
        apps = {a for a, *_ in panel}
        if apps != set(APPS[disc]):
            err(f'{disc} {rnd} judges: panels for {sorted(apps)}')
        for app, fn, name, noc in panel:
            given, surname = (x.strip() for x in name.split(',', 1))
            rows.append(base | {'Event Start Date': ev['Start Date'], 'Round': rnd, 'Apparatus': app,
                                'Function': FUNCTION.get(fn, fn), 'Name': name, 'Surname': surname,
                                'Given Name': given, 'NOC Code': noc})
    out_files.csv(f'{prefix}-JUDGES.csv', common.JUDGES, rows)

    # ---- participants
    out_files.csv(f'{prefix}-PARTICIPANTS.csv', common.PARTICIPANTS,
          [base | {'Event Start Date': ev['Start Date'], 'Order': i + 1, 'NOC Code': p['NOC'],
                   'Bib Number': p['Bib'], 'FIG License': p['License'], 'Name': p['Name'], 'Surname': p['Surname'],
                   'Given Name': p['Given'], 'Birth Year': p['Year'], 'Birth Date': ''}
           for i, p in enumerate(people)])

    out_files.manifest(prefix, ev)

qual_text = parse_qual_pdf()
starts = start_lists()
for disc in ('MAG', 'WAG'):
    print(f'===== {disc}')
    level(disc, qual_text, starts)

checks.finish()
