"""Parse the 2026 World Championships results book into the CSV standard.

Usage (needs poppler's pdftotext on PATH):

    python3 -I parse_2026_world_championships.py \\
        data/2026/2026-WORLD-CHAMPIONSHIPS/2026-WORLD-CHAMPIONSHIPS-ALL-RESULTS.pdf

Writes every PARTICIPANTS / JUDGES / SCORES-* / SUMMARY-* CSV and an IMPORT.json per
discipline next to the PDF, reading event metadata from the EVENT.csv already there.
Exits non-zero if any self-check fails (see docs/instructions-results-parsing.md §8.3).

Written against the FIG/Swiss Timing results-book layout (footer codes like
GARM1APFX----...QUAL..._73I). Session dates come from that book's Competition Schedule
and are set below; reusing this for another FIG results book means re-checking those,
the report footer codes, and the header layouts per §8.
"""
import csv, json, os, re, subprocess, sys
from collections import defaultdict
from decimal import Decimal

PDF = sys.argv[1]
OUT = os.path.dirname(os.path.abspath(PDF))
PAGES = subprocess.run(['pdftotext', '-layout', PDF, '-'], check=True, capture_output=True,
                       text=True).stdout.split('\f')

PREFIX = {'MAG': '2026-WORLD-CHAMPIONSHIPS-MAG-SR', 'WAG': '2026-WORLD-CHAMPIONSHIPS-WAG-SR'}
with open(os.path.join(OUT, '2026-WORLD-CHAMPIONSHIPS-EVENT.csv'), encoding='utf-8') as f:
    EVENTS = {r['Prefix']: r for r in csv.DictReader(f)}
if len({EVENTS[p]['Title'] for p in PREFIX.values()}) != 1 or \
        len({EVENTS[p]['Start Date'] for p in PREFIX.values()}) != 1:
    raise SystemExit('MAG and WAG rows in EVENT.csv are expected to share a title and start date')
EVENT_NAME = EVENTS[PREFIX['MAG']]['Title']
EVENT_START = EVENTS[PREFIX['MAG']]['Start Date']
G = {'MAG': 'M', 'WAG': 'W'}
APPS = {'MAG': ['FX', 'PH', 'SR', 'VT', 'PB', 'HB'], 'WAG': ['VT', 'UB', 'BB', 'FX']}
SUMMARY_APPS = {'MAG': ['FX', 'PH', 'SR', 'VT', 'PB', 'HB'], 'WAG': ['FX', 'BB', 'UB', 'VT']}
# From the Competition Schedule page (never from report footers, §5.2b).
SESSION_DATE = {
    'MAG': {'QUAL': '2026-10-17', 'TEAM': '2026-10-20', 'AA': '2026-10-22',
            'EVENT': {'FX': '2026-10-24', 'PH': '2026-10-24', 'SR': '2026-10-24',
                      'VT': '2026-10-25', 'PB': '2026-10-25', 'HB': '2026-10-25'}},
    'WAG': {'QUAL': '2026-10-18', 'TEAM': '2026-10-21', 'AA': '2026-10-23',
            'EVENT': {'VT': '2026-10-24', 'UB': '2026-10-24', 'BB': '2026-10-25', 'FX': '2026-10-25'}},
}

errors = []
def err(msg):
    errors.append(msg)

# ---------------------------------------------------------------- page lookup
def footer(i):
    for l in reversed(PAGES[i].split('\n')):
        if re.search(r'Page \d+\s*/\s*\d+', l):
            return l.split()[0]
    return None

def find(code, title=None):
    """0-based page indexes whose footer code matches exactly and whose title block contains `title`."""
    out = [i for i in range(len(PAGES)) if footer(i) == code]
    if title:
        out = [i for i in out if any(l.strip() == title for l in PAGES[i].split('\n')[:8])]
    if not out:
        raise SystemExit(f'no pages for {code} / {title}')
    return out

def code(g, kind, rnd, suffix):
    # e.g. GARM1APFX-------------QUAL--------_73I
    head = f'GAR{g}{kind}'
    return head + '-' * (22 - len(head)) + rnd + '-' * (34 - 22 - len(rnd)) + '_' + suffix

def lines(idx):
    return [l for i in idx for l in PAGES[i].split('\n')]

# ---------------------------------------------------------------- csv helpers
def write(name, header, rows):
    with open(os.path.join(OUT, name), 'w', newline='', encoding='utf-8') as f:
        w = csv.writer(f, lineterminator='\n')
        w.writerow(header)
        for r in rows:
            missing = [h for h in header if h not in r]
            if missing:
                raise SystemExit(f'{name}: row missing {missing}: {r}')
            w.writerow([r[h] for h in header])
    print(f'{name}: {len(rows)} rows')

def D(s):
    return Decimal(s) if s else Decimal(0)

def surname_word(w):
    return re.fullmatch(r"(Mc|Mac)?[^a-z]*[A-Z][^a-z]*", w) is not None

PARTICLES = {'Mc', 'van', 'der', 'den', 'de', 'le', 'la', 'du', 'da', 'di', 'von', 'dos', 'del'}

def split_name(name):
    """§5.10's leading-uppercase-words rule, extended so lowercase particles (`van POL`,
    `le ROCH`) and `McDONALD`-style prefixes stay in the surname."""
    words = name.split(' ')
    i = 0
    while i < len(words) and (surname_word(words[i]) or words[i] in PARTICLES):
        i += 1
    return ' '.join(words[:i]), ' '.join(words[i:])

# ---------------------------------------------------------------- participants
MONTHS = {m: i + 1 for i, m in enumerate('JAN FEB MAR APR MAY JUN JUL AUG SEP OCT NOV DEC'.split())}
NAMES = {}

def participants(disc):
    rows = []
    for l in lines(find(code(G[disc], '', '', '32D'), None)):
        m = re.match(r'^\s+(\d{3})\s+(\d+)\s+(.+?)\s{2,}([A-Z]{3})\s+(\d{1,2}) ([A-Z]{3}) (\d{4})\s*$', l)
        if not m:
            if re.match(r'^\s+\d{3}\s', l):
                err(f'participants {disc}: unparsed {l!r}')
            continue
        bib, lic, name, noc, d, mon, y = m.groups()
        sur, giv = split_name(name)
        if not sur or not giv:
            err(f'participants {disc}: name split {name!r} -> {sur!r}/{giv!r}')
        NAMES[bib] = (name, noc)
        rows.append({'Event Name': EVENT_NAME, 'Discipline': disc, 'Age Category': 'SR',
                     'Event Start Date': EVENT_START, 'Order': len(rows) + 1, 'NOC Code': noc,
                     'Bib Number': bib, 'FIG License': lic, 'Name': name, 'Surname': sur,
                     'Given Name': giv, 'Birth Year': y,
                     'Birth Date': f'{y}-{MONTHS[mon]:02d}-{int(d):02d}'})
    write(f'{PREFIX[disc]}-PARTICIPANTS.csv',
          ['Event Name', 'Discipline', 'Age Category', 'Event Start Date', 'Order', 'NOC Code',
           'Bib Number', 'FIG License', 'Name', 'Surname', 'Given Name', 'Birth Year', 'Birth Date'], rows)

def check_name(ctx, bib, name, noc):
    if bib not in NAMES:
        err(f'{ctx}: bib {bib} not in participants'); return name
    full, pnoc = NAMES[bib]
    if pnoc != noc:
        err(f'{ctx}: bib {bib} NOC {noc} != participants {pnoc}')
    if name != full:
        print(f'  name fix {ctx}: bib {bib} {name!r} -> {full!r}')
    return full

# ---------------------------------------------------------------- judges
SUP = {'Floor Exercise': 'FX', 'Pommel Horse': 'PH', 'Rings': 'SR', 'Vault': 'VT',
       'Parallel Bars': 'PB', 'Horizontal Bar': 'HB', 'Uneven Bars': 'UB', 'Balance Beam': 'BB'}
# Panel order in the icon grid, left-to-right then top-to-bottom; confirmed by rendering every page.
GRID = {'MAG': ['FX', 'PH', 'SR', 'VT', 'PB', 'HB'], 'WAG': ['VT', 'UB', 'BB', 'FX']}

def judge_fn(f):
    f = f.strip()
    m = re.fullmatch(r'(Time|Line) Judge(?: (\d))?', f)
    if m:
        return ('T' if m.group(1) == 'Time' else 'L') + (m.group(2) or '')
    return f

def judges(disc):
    g = G[disc]
    sfx = '57A' if disc == 'MAG' else '57B'
    specs = [('QUAL', code(g, 'GEN', 'QUAL', sfx)), ('TEAM', code(g, 'TEAM', 'FNL', sfx)),
             ('AA', code(g, '1AA', 'FNL', sfx)), ('EVENT', code(g, '1AP', 'FNL', sfx))]
    rows = []
    def add(rnd, app, fn, name, noc):
        sur, giv = split_name(name)
        rows.append({'Event Name': EVENT_NAME, 'Discipline': disc, 'Age Category': 'SR',
                     'Event Start Date': EVENT_START, 'Round': rnd, 'Apparatus': app, 'Function': fn,
                     'Name': name, 'Surname': sur, 'Given Name': giv, 'NOC Code': noc})
    for rnd, c in specs:
        ls = lines(find(c))
        block, split_at, in_res, n_rnd = -1, None, False, len(rows)
        for l in ls:
            m = re.match(r'^\s+(?:Superior Jury:)?\s+([A-Z].+?)\s{2,}(President|Vice-President|Member|(.+) Supervisor)\s*$', l)
            if m and block < 0:
                role = m.group(2)
                if m.group(3):
                    add(rnd, SUP[m.group(3)], 'SV', m.group(1), '')
                else:
                    add(rnd, '', role, m.group(1), '')
                continue
            if re.match(r'^\s+Function\s+Name\s+Function\s+Name', l):
                block += 1
                split_at = l.index('Function', l.index('Name'))
                continue
            if l.strip() == 'Reserves':
                in_res = True
                continue
            if in_res:
                # the last reserve can share its line with the 'Legend:' label
                m = re.match(r'^(?:Legend:)?\s+R(\d)\s+(.+?)\s{2,}([A-Z]{3})\s*$', l)
                if m:
                    add(rnd, '', f'R{m.group(1)}', m.group(2), m.group(3))
                continue
            if block >= 0 and split_at:
                for side, part in ((0, l[:split_at]), (1, l[split_at:])):
                    m = re.match(r'^\s*(D\d|E\d|(?:Time|Line) Judge(?: \d)?)\s+(.+?)\s{2,}([A-Z]{3})\s*$', part)
                    if m:
                        add(rnd, GRID[disc][block * 2 + side], judge_fn(m.group(1)), m.group(2), m.group(3))
                    elif part.strip() and not part.strip().startswith(('NOC', 'Code')):
                        err(f'judges {disc} {rnd}: unparsed {part!r}')
        if block + 1 != len(GRID[disc]) // 2:
            err(f'judges {disc} {rnd}: {block + 1} panel rows')
        print(f'  judges {disc} {rnd}: {len(rows) - n_rnd}')
    # Superior Jury members carry no NOC on these pages.
    write(f'{PREFIX[disc]}-JUDGES.csv',
          ['Event Name', 'Discipline', 'Age Category', 'Event Start Date', 'Round', 'Apparatus',
           'Function', 'Name', 'Surname', 'Given Name', 'NOC Code'], rows)

# ---------------------------------------------------------------- apparatus tables
SCORE_HDR = ['Event Name', 'Discipline', 'Age Category', 'Session Start Date', 'Round', 'Apparatus',
             'Rank', 'Bib', 'Name', 'NOC Code']
MARK = r'(?:\s+(Q|R\d|NR))?'

def meta(disc, rnd, app):
    date = SESSION_DATE[disc][rnd]
    if isinstance(date, dict):
        date = date[app]
    return {'Event Name': EVENT_NAME, 'Discipline': disc, 'Age Category': 'SR',
            'Session Start Date': date, 'Round': rnd, 'Apparatus': app}

def split_bp(vals, ctx):
    """Pen./Bonus share a column pair; sign tells them apart (bonus > 0, penalty < 0)."""
    b = p = ''
    for v in vals:
        if not re.fullmatch(r'-?\d+\.\d', v):
            err(f'{ctx}: unexpected token {v!r}'); continue
        if v.startswith('-'):
            if p: err(f'{ctx}: two penalties')
            p = v
        else:
            if b: err(f'{ctx}: two bonuses')
            b = v
    return b, p

def check_sum(ctx, d, e, b, p, total):
    if D(d) + D(e) + D(b) + D(p) != D(total):
        err(f'{ctx}: {d}+{e}+{b}+{p} != {total}')

ROW_RE = re.compile(r'^\s*(\d+)\s+(\d{3})\s+(.+?)\s{2,}([A-Z]{3})\s+(\d+\.\d{3})\s+(\d+\.\d{3})((?:\s+-?\d+\.\d)*)\s+(\d+\.\d{3})' + MARK + r'(?:\s+\d+\s+\d+)?\s*$')
DNS_RE = re.compile(r'^\s*(\d{3})\s+(.+?)\s{2,}([A-Z]{3})\s+DNS\s*$')

def single_table(idx, ctx):
    """Rows of a one-attempt table, keyed by order of appearance."""
    out = []
    for l in lines(idx):
        m = ROW_RE.match(l)
        if m:
            rank, bib, name, noc, d, e, bp, tot, q = m.groups()
            b, p = split_bp(bp.split(), f'{ctx} bib {bib}')
            check_sum(f'{ctx} bib {bib}', d, e, b, p, tot)
            out.append(dict(Rank=rank, Bib=bib, Name=name, NOC=noc, Status='COMPLETED', D=d, E=e,
                            B=b, Penalty=p, Total=tot, Qualification=q or ''))
            continue
        m = DNS_RE.match(l)
        if m:
            bib, name, noc = m.groups()
            out.append(dict(Rank='', Bib=bib, Name=name, NOC=noc, Status='DNS', D='', E='', B='',
                            Penalty='', Total='', Qualification=''))
            continue
        if re.match(r'^\s*\d+\s+\d{3}\s', l) or re.search(r'\bDN[SF]\s*$', l):
            err(f'{ctx}: unparsed {l!r}')
    # independent count check
    n_rank = sum(1 for l in lines(idx) if re.match(r'^\s*\d+\s+\d{3}\s+\S', l))
    n_dns = sum(1 for l in lines(idx) if re.search(r'\sDNS\s*$', l))
    if n_rank + n_dns != len(out):
        err(f'{ctx}: parsed {len(out)} rows, expected {n_rank}+{n_dns}')
    return out

def single_row_out(disc, rnd, app, r, ctx, with_q):
    row = meta(disc, rnd, app) | {'Rank': r['Rank'], 'Bib': r['Bib'],
                                  'Name': check_name(ctx, r['Bib'], r['Name'], r['NOC']),
                                  'NOC Code': r['NOC'], 'Status': r['Status'], 'D': r['D'], 'E': r['E'],
                                  'B': r['B'], 'Penalty': r['Penalty'], 'SJ Penalty': '', 'Total': r['Total']}
    if with_q:
        row['Qualification'] = r['Qualification']
    return row

SINGLE_HDR = SCORE_HDR + ['Status', 'D', 'E', 'B', 'Penalty', 'SJ Penalty', 'Total']
QUAL_SCORES = defaultdict(dict)   # disc -> (app, bib) -> total, for cross-checking team/AA reports

def single_app(disc, rnd, app):
    g = G[disc]
    rc = 'QUAL' if rnd == 'QUAL' else 'FNL'
    idx = find(code(g, '1AP' + app, rc, '73I'))
    if app == 'VT':  # the single-attempt list is the 'Vault 1' report
        idx = [i for i in idx if 'Vault 1' in PAGES[i][:600]]
    ctx = f'{disc} {rnd} {app}'
    rows = single_table(idx, ctx)
    out = [single_row_out(disc, rnd, app, r, ctx, rnd == 'QUAL') for r in rows]
    if rnd == 'QUAL':
        for r in rows:
            QUAL_SCORES[disc][(app, r['Bib'])] = r['Total'] or 'DNS'
    if app != 'VT':
        write(f'{PREFIX[disc]}-SCORES-{rnd}-{app}.csv', SINGLE_HDR + (['Qualification'] if rnd == 'QUAL' else []), out)
    return rows

def vault_attempt(tokens, ctx):
    """tokens after the vault number: D E [pen/bonus...] VaultScore ... -> dict, remaining tokens."""
    if tokens[0] == 'DNS':
        return dict(Status='DNS', D='', E='', B='', Penalty='', Total=''), tokens[1:]
    d, e = tokens[0], tokens[1]
    i = 2
    extra = []
    while not re.fullmatch(r'\d+\.\d{3}', tokens[i]):
        extra.append(tokens[i]); i += 1
    b, p = split_bp(extra, ctx)
    tot = tokens[i]
    check_sum(ctx, d, e, b, p, tot)
    status = 'INVALID_VAULT' if D(tot) == 0 else 'COMPLETED'
    return dict(Status=status, D=d, E=e, B=b, Penalty=p, Total=tot), tokens[i + 1:]

def double_table(disc, rnd):
    rc = 'QUAL' if rnd == 'QUAL' else 'FNL'
    ctx = f'{disc} {rnd} VT double'
    ls = lines(find(code(G[disc], '1APVT', rc, '73J')))
    out, cur = [], None
    for l in ls:
        m = re.match(r'^\s*(\d+)?\s+(\d{3})\s+(.+?)\s{2,}([A-Z]{3})\s+1\s+(.*)$', l)
        if m:
            rank, bib, name, noc, rest = m.groups()
            t = rest.split()
            if rnd == 'EVENT':
                t = t[:-2]   # Qualification Rank, Start Order
            q = t.pop() if t[-1] in ('Q', 'R1', 'R2', 'R3', 'NR') else ''
            v1, t = vault_attempt(t, f'{ctx} bib {bib} v1')
            bonus = ''
            if len(t) == 2:
                bonus = t.pop(0)
                if not re.fullmatch(r'\d\.\d', bonus): err(f'{ctx} bib {bib}: bad bonus {bonus}')
            if len(t) != 1: err(f'{ctx} bib {bib}: leftover {t}')
            cur = dict(Rank=rank or '', Bib=bib, Name=name, NOC=noc, v1=v1, Bonus=bonus, Total=t[0], Q=q)
            out.append(cur)
            continue
        m = re.match(r'^\s+2\s+(.*)$', l)
        if m and cur is not None and 'v2' not in cur:
            v2, t = vault_attempt(m.group(1).split(), f'{ctx} bib {cur["Bib"]} v2')
            if t: err(f'{ctx}: leftover v2 {t}')
            cur['v2'] = v2
            if cur['Total'] not in ('DNF', 'DNS'):
                avg = (D(cur['v1']['Total']) + D(v2['Total'])) / 2 + D(cur['Bonus'])
                if abs(avg - D(cur['Total'])) > Decimal('0.0015'):
                    err(f'{ctx} bib {cur["Bib"]}: average {avg} != {cur["Total"]}')
            continue
        if re.match(r'^\s*\d+\s+\d{3}\s', l):
            err(f'{ctx}: unparsed {l!r}')
    if any('v2' not in r for r in out):
        err(f'{ctx}: missing vault 2 line')
    return out

def vault_cols(n, v):
    return {f'Status{n}': v['Status'], f'D{n}': v['D'], f'E{n}': v['E'], f'B{n}': v['B'],
            f'Penalty{n}': v['Penalty'], f'SJ Penalty{n}': '', f'Total{n}': v['Total']}

BLANK_V = dict(Status='', D='', E='', B='', Penalty='', Total='')

def vault(disc, rnd):
    ctx = f'{disc} {rnd} VT'
    hdr = SCORE_HDR + [f'{c}{n}' for n in (1, 2) for c in ('Status', 'D', 'E', 'B', 'Penalty', 'SJ Penalty', 'Total')]
    hdr += (['Bonus'] if disc == 'WAG' else []) + ['Total']
    double = double_table(disc, rnd)
    rows = []
    if rnd == 'QUAL':
        hdr += ['Qualification', 'Qualification Rank']
        single = single_app(disc, 'QUAL', 'VT')
        dbl = {r['Bib']: r for r in double}
        if len(dbl) != len(double): err(f'{ctx}: duplicate bib in double list')
        for s in single:
            v1 = dict(Status=s['Status'], D=s['D'], E=s['E'], B=s['B'], Penalty=s['Penalty'], Total=s['Total'])
            if v1['Status'] == 'COMPLETED' and D(v1['Total']) == 0:
                v1['Status'] = 'INVALID_VAULT'
            r = dbl.pop(s['Bib'], None)
            if r is None:
                v2, bonus, total, q, qrank = BLANK_V, '', s['Total'], '', ''
            else:
                if r['v1'] != v1:
                    err(f'{ctx} bib {s["Bib"]}: single/double vault 1 differ {v1} vs {r["v1"]}')
                v2 = r['v2']
                if v2['Status'] == 'DNS':
                    v2 = dict(BLANK_V, Status='DNS')
                bonus, q, qrank = r['Bonus'], r['Q'], r['Rank']
                total = '' if r['Total'] in ('DNF', 'DNS') else r['Total']
            row = meta(disc, rnd, 'VT') | {'Rank': s['Rank'], 'Bib': s['Bib'],
                   'Name': check_name(ctx, s['Bib'], s['Name'], s['NOC']), 'NOC Code': s['NOC']}
            row |= vault_cols(1, v1) | vault_cols(2, v2) | {'Total': total, 'Qualification': q,
                                                             'Qualification Rank': qrank}
            if disc == 'WAG':
                row['Bonus'] = bonus
            rows.append(row)
        if dbl:
            err(f'{ctx}: double-list bibs not on single list: {list(dbl)}')
    else:
        for r in double:
            row = meta(disc, rnd, 'VT') | {'Rank': r['Rank'], 'Bib': r['Bib'],
                   'Name': check_name(ctx, r['Bib'], r['Name'], r['NOC']), 'NOC Code': r['NOC']}
            row |= vault_cols(1, r['v1']) | vault_cols(2, r['v2']) | {'Total': r['Total']}
            if disc == 'WAG':
                row['Bonus'] = r['Bonus']
            rows.append(row)
    write(f'{PREFIX[disc]}-SCORES-{rnd}-VT.csv', hdr, rows)

# ---------------------------------------------------------------- team / all-around reports
NUM = re.compile(r'-?\d+\.\d+')

def detail_lines(ls, i, disc, items, ctx):
    """Read the E (and BP) lines following a D line. `items` = list of (app_index, start_col, D, Score)
    for apps with a routine; returns per-app dict with E/B/Penalty, assigned by column position."""
    res = {a: {'E': '', 'B': '', 'Penalty': ''} for a, _, _, _ in items if _ is not None}
    starts = sorted((col, a) for a, col, d, s in items if d is not None)
    def owner_nearest(pos):
        return min(starts, key=lambda t: abs(t[0] - pos))[1]
    def owner_left(pos):
        left = [a for col, a in starts if col <= pos + 1]
        return left[-1] if left else starts[0][1]
    j = i + 1
    e_line = ls[j]
    m = re.match(r'^(\s+)(E P|EP|E)(?:\s|$)', e_line)
    if not m:
        err(f'{ctx}: expected E line, got {e_line!r}'); return res, j
    e_tag = m.group(2)
    for v in NUM.finditer(e_line[m.end():]):
        pos, val = v.start() + m.end(), v.group()
        if val.startswith('-') and e_tag != 'E':
            res[owner_left(pos)]['Penalty'] = val
        else:
            a = owner_nearest(pos)
            if res[a]['E']: err(f'{ctx}: two E values for app {a}')
            res[a]['E'] = val
    j += 1
    if disc == 'MAG':
        m = re.match(r'^\s+BP(\s|$)', ls[j])
        if not m:
            err(f'{ctx}: expected BP line, got {ls[j]!r}')
        else:
            for v in NUM.finditer(ls[j][m.end():]):
                pos, val = v.start() + m.end(), v.group()
                key = 'Penalty' if val.startswith('-') else 'B'
                a = owner_left(pos)
                if res[a][key]: err(f'{ctx}: two {key} for app {a}')
                res[a][key] = val
            j += 1
    return res, j

APP_ITEM = re.compile(r'(\d+\.\d{3})\s+(\d+\.\d{3})(?:\s+\((\d+)\))?|DNS')

def aa_report(disc, rnd):
    """All-Around Qualification / All-Around Final ranking report."""
    sfx = '73G' if disc == 'MAG' else '73H'
    rc = 'QUAL' if rnd == 'QUAL' else 'FNL'
    title = {'MAG': "Men's All-Around", 'WAG': "Women's All-Around"}[disc] + (' Qualification' if rnd == 'QUAL' else ' Final')
    ls = lines(find(code(G[disc], '1AA', rc, sfx), title))
    n = len(APPS[disc])
    ctx0 = f'{disc} {rnd} AA'
    athletes = []
    i = 0
    while i < len(ls):
        m = re.match(r'^\s*(\d+)?\s+(\d{3})\s+(.+?)\s+([A-Z]{3})\s+D\s(.*)$', ls[i])
        spill = None
        if not m:
            m = re.match(r'^\s*(\d+)?\s+(\d{3})\s+(.+?)()\s+D\s(.*)$', ls[i])
            spill = re.match(r'^\s+([A-Z]{3})\s+(\S+)(?:\s+(Q|R\d|NR))?\s*$', ls[i + 1]) if m else None
            if not spill:
                m = None
        if not m:
            if re.match(r'^\s*\d+\s+\d{3}\s', ls[i]): err(f'{ctx0}: unparsed {ls[i]!r}')
            i += 1; continue
        rank, bib, name, noc, _ = m.groups()
        if spill:
            noc = spill.group(1)
            print(f'  {ctx0} bib {bib}: NOC/total taken from following line')
        ctx = f'{ctx0} bib {bib}'
        base = m.start(5)
        rest = ls[i][base:]
        items = []
        end = 0
        for it in APP_ITEM.finditer(rest):
            if len(items) == n: break
            end = it.end()
            if it.group(0) == 'DNS':
                items.append((len(items), base + it.start(), None, None, None))
            else:
                items.append((len(items), base + it.start(), it.group(1), it.group(2), it.group(3)))
        tail = rest[end:].split()
        if spill:
            if tail: err(f'{ctx}: unexpected tail with spilled NOC')
            tail = [spill.group(2)] + ([spill.group(3)] if spill.group(3) else [])
            ls[i + 1] = ''  # consumed
        if len(items) != n or not tail:
            err(f'{ctx}: {len(items)} apparatus items, tail {tail}'); i += 1; continue
        total = tail[0]
        q = tail[1] if len(tail) > 1 else ''
        det, i = detail_lines(ls, i + (1 if spill else 0), disc, [(a, c, d, s) for a, c, d, s, _ in items], ctx)
        apps = {}
        for a, col, d, s, rk in items:
            app = APPS[disc][a]
            if d is None:
                apps[app] = dict(Status='DNS', D='', E='', B='', Penalty='', Total='', Rank='')
            else:
                x = det[a]
                check_sum(f'{ctx} {app}', d, x['E'], x['B'], x['Penalty'], s)
                apps[app] = dict(Status='COMPLETED', D=d, E=x['E'], B=x['B'], Penalty=x['Penalty'],
                                 Total=s, Rank=rk or '')
        if total not in ('DNF', 'DNS'):
            ssum = sum(D(v['Total']) for v in apps.values())
            if ssum != D(total): err(f'{ctx}: apparatus sum {ssum} != {total}')
        athletes.append(dict(Rank=rank or '', Bib=bib, Name=name, NOC=noc, apps=apps,
                             Total='' if total in ('DNF', 'DNS') else total, Q=q))
    n_id = sum(1 for l in ls if re.match(r'^\s*(\d+)?\s+\d{3}\s+\S.*?\s+D\s', l))
    if n_id != len(athletes): err(f'{ctx0}: parsed {len(athletes)} of {n_id}')
    return athletes

def summary_aa(disc, rnd, athletes):
    hdr = ['Event Name', 'Discipline', 'Age Category', 'Event Start Date', 'Round', 'Rank', 'Bib', 'Name',
           'NOC Code'] + [f'{a} Total' for a in SUMMARY_APPS[disc]] + ['SJ Penalty', 'Total']
    if rnd == 'QUAL':
        hdr.append('Qualification')
    rows = []
    for a in athletes:
        r = {'Event Name': EVENT_NAME, 'Discipline': disc, 'Age Category': 'SR', 'Event Start Date': EVENT_START,
             'Round': rnd, 'Rank': a['Rank'], 'Bib': a['Bib'],
             'Name': check_name(f'{disc} {rnd} AA summary', a['Bib'], a['Name'], a['NOC']),
             'NOC Code': a['NOC'], 'SJ Penalty': '', 'Total': a['Total'], 'Qualification': a['Q']}
        for app in SUMMARY_APPS[disc]:
            r[f'{app} Total'] = a['apps'][app]['Total']
        rows.append(r)
    write(f'{PREFIX[disc]}-SUMMARY-{"QUAL" if rnd == "QUAL" else "FINAL"}-AA.csv', hdr, rows)

def team_report(disc, rnd):
    sfx = '73C' if disc == 'MAG' else '73D'
    rc = 'QUAL' if rnd == 'QUAL' else 'FNL'
    ls = lines(find(code(G[disc], 'TEAM', rc, sfx)))
    n = len(APPS[disc])
    teams, team = [], None
    i = 0
    while i < len(ls):
        l = ls[i]
        m = re.match(r'^\s*(\d+)?\s+([A-Z]{3}) - (.+?)\s{2,}(\(.*)$', l)
        if m:
            rank, noc, _, rest = m.groups()
            base = m.start(4)
            cols = [(base + x.end(), x.group(2)) for x in re.finditer(r'\((\d+)\)\s+(\d+\.\d{3})', rest)]
            tail = rest[list(re.finditer(r'\(\d+\)\s+\d+\.\d{3}', rest))[-1].end():].split()
            if len(cols) != n: err(f'{disc} {rnd} team {noc}: {len(cols)} apparatus totals')
            team = dict(Rank=rank or '', NOC=noc, cols=[c for c, _ in cols], totals=[t for _, t in cols],
                        Total=tail[0], Q=tail[1] if len(tail) > 1 else '', athletes=[])
            teams.append(team)
            i += 1; continue
        m = re.match(r'^\s+(\d{3}) (.+?)\s{2,}D(\s.*)?$', l)
        if m and team is not None:
            bib, name = m.group(1), m.group(2)
            ctx = f'{disc} {rnd} team {team["NOC"]} bib {bib}'
            base = m.start(3) if m.group(3) else len(l)
            items = []
            for it in APP_ITEM.finditer(m.group(3) or ''):
                pos = base + it.start()
                a = min(range(n), key=lambda k: abs(team['cols'][k] - (base + it.end())))
                if any(x[0] == a for x in items): err(f'{ctx}: two items in app {a}')
                if it.group(0) == 'DNS':
                    items.append((a, pos, None, None))
                else:
                    items.append((a, pos, it.group(1), it.group(2)))
            apps = {}
            if items:
                det, i = detail_lines(ls, i, disc, items, ctx)
            else:
                i += 2 if disc == 'MAG' else 1
            for a, pos, d, s in items:
                app = APPS[disc][a]
                if d is None:
                    apps[app] = dict(Status='DNS', D='', E='', B='', Penalty='', Total='')
                else:
                    x = det[a]
                    check_sum(f'{ctx} {app}', d, x['E'], x['B'], x['Penalty'], s)
                    apps[app] = dict(Status='COMPLETED', D=d, E=x['E'], B=x['B'], Penalty=x['Penalty'], Total=s)
            team['athletes'].append(dict(Bib=bib, Name=name, NOC=team['NOC'], apps=apps))
            continue
        i += 1
    # team apparatus total = best three counting scores
    for t in teams:
        for a, app in enumerate(APPS[disc]):
            sc = sorted((D(x['apps'][app]['Total']) for x in t['athletes']
                         if app in x['apps'] and x['apps'][app]['Total']), reverse=True)
            if sum(sc[:3]) != D(t['totals'][a]):
                err(f'{disc} {rnd} team {t["NOC"]} {app}: top3 {sum(sc[:3])} != {t["totals"][a]}')
        if t['Total'] != 'DNF' and sum(D(x) for x in t['totals']) != D(t['Total']):
            err(f'{disc} {rnd} team {t["NOC"]}: total mismatch')
    return teams

def summary_team(disc, rnd, teams):
    hdr = ['Event Name', 'Discipline', 'Age Category', 'Event Start Date', 'Round', 'Rank', 'NOC Code'] + \
          [f'{a} Total' for a in SUMMARY_APPS[disc]] + ['SJ Penalty', 'Total']
    if rnd == 'QUAL':
        hdr.append('Qualification')
    rows = []
    for t in teams:
        r = {'Event Name': EVENT_NAME, 'Discipline': disc, 'Age Category': 'SR', 'Event Start Date': EVENT_START,
             'Round': rnd, 'Rank': t['Rank'], 'NOC Code': t['NOC'], 'SJ Penalty': '',
             'Total': '' if t['Total'] == 'DNF' else t['Total'], 'Qualification': t['Q']}
        for a, app in enumerate(APPS[disc]):
            r[f'{app} Total'] = t['totals'][a]
        rows.append(r)
    write(f'{PREFIX[disc]}-SUMMARY-{"QUAL" if rnd == "QUAL" else "FINAL"}-TEAM.csv', hdr, rows)

def scores_from_report(disc, rnd, athletes):
    """Per-apparatus SCORES-<rnd>-<APP> files built from a team/AA report (no separate per-apparatus report)."""
    for app in APPS[disc]:
        rows = []
        for a in athletes:
            x = a['apps'].get(app)
            if x is None:
                continue
            ctx = f'{disc} {rnd} {app}'
            rows.append(meta(disc, rnd, app) | {'Rank': x.get('Rank', ''), 'Bib': a['Bib'],
                        'Name': check_name(ctx, a['Bib'], a['Name'], a['NOC']), 'NOC Code': a['NOC'],
                        'Status': x['Status'], 'D': x['D'], 'E': x['E'], 'B': x['B'], 'Penalty': x['Penalty'],
                        'SJ Penalty': '', 'Total': x['Total']})
        if rnd == 'AA':
            rows.sort(key=lambda r: (r['Rank'] == '', int(r['Rank'] or 0)))
        write(f'{PREFIX[disc]}-SCORES-{rnd}-{app}.csv', SINGLE_HDR, rows)

def cross_check_qual(disc, athletes, ctx):
    for a in athletes:
        for app, x in a['apps'].items():
            want = QUAL_SCORES[disc].get((app, a['Bib']))
            got = x['Total'] or 'DNS'
            if want != got:
                err(f'{ctx} bib {a["Bib"]} {app}: {got} vs per-apparatus qual {want}')

# ---------------------------------------------------------------- run
for disc in ('MAG', 'WAG'):
    print(f'===== {disc}')
    participants(disc)
    judges(disc)
    for app in APPS[disc]:
        if app == 'VT':
            vault(disc, 'QUAL'); vault(disc, 'EVENT')
        else:
            single_app(disc, 'QUAL', app); single_app(disc, 'EVENT', app)

    tq = team_report(disc, 'QUAL')
    cross_check_qual(disc, [x for t in tq for x in t['athletes']], f'{disc} team qual')
    summary_team(disc, 'QUAL', tq)

    aaq = aa_report(disc, 'QUAL')
    cross_check_qual(disc, aaq, f'{disc} AA qual')
    summary_aa(disc, 'QUAL', aaq)

    tf = team_report(disc, 'TEAM')
    summary_team(disc, 'TEAM', tf)
    scores_from_report(disc, 'TEAM', [x for t in tf for x in t['athletes']])

    aaf = aa_report(disc, 'AA')
    summary_aa(disc, 'AA', aaf)
    scores_from_report(disc, 'AA', aaf)

    NAMES.clear()

    files = sorted(os.listdir(OUT))
    p = PREFIX[disc]
    e = EVENTS[p]
    manifest = {
        'event': {'title': e['Title'], 'disciplines': e['Disciplines'], 'ageCategory': e['Age Category'],
                  'level': e['Level'], 'startDate': e['Start Date'], 'endDate': e['End Date'], 'city': e['City'],
                  'country': e['Country'], 'figEventNumber': e['FIG Event Number'], 'venue': e['Venue'],
                  'apparatusSuppliers': e['Apparatus Suppliers']},
        'participants': [f for f in files if f.startswith(p + '-PARTICIPANTS')],
        'judges': [f for f in files if f.startswith(p + '-JUDGES')],
        'scores': [f for f in files if f.startswith(p + '-SCORES-')],
        'summaries': [f for f in files if f.startswith(p + '-SUMMARY-')],
    }
    with open(os.path.join(OUT, f'{p}-IMPORT.json'), 'w') as f:
        json.dump(manifest, f, indent=4, separators=(',', ' : '))
        f.write('\n')

print(f'\n{len(errors)} errors')
for e in errors:
    print(' ', e)
sys.exit(1 if errors else 0)
