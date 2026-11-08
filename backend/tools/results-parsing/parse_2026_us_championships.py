"""Parse the 2026 U.S. Championships results PDF into the CSV standard.

Usage (needs poppler's pdftotext on PATH):

    python3 -I parse_2026_us_championships.py \\
        data/2026/2026-US-CHAMPIONSHIPS/2026-US-CHAMPIONSHIPS-ALL-RESULTS.pdf

Writes PARTICIPANTS / SCORES-AA-<APP> / SUMMARY-FINAL-AA / SUMMARY-FINAL-<APP> CSVs and an
IMPORT.json per level next to the PDF, reading event metadata from the EVENT.csv already
there. Exits non-zero if any self-check fails (see docs/instructions-results-parsing.md §9).

Written against the USA Gymnastics ProScore layout: a two-day All-Around (Day 1 = Part 1,
Day 2 = Part 2) whose titles are decided by two-day combined totals. Athlete names, clubs
and the Surname/Given Name split come from the existing PARTICIPANTS files, keyed by bib.
"""
import csv, json, os, re, subprocess, sys
from collections import defaultdict
from decimal import Decimal

PDF = sys.argv[1]
OUT = os.path.dirname(os.path.abspath(PDF))
PAGES = subprocess.run(['pdftotext', '-layout', PDF, '-'], check=True, capture_output=True,
                       text=True).stdout.split('\f')

EVENT_PREFIX = '2026-US-CHAMPIONSHIPS'
with open(os.path.join(OUT, f'{EVENT_PREFIX}-EVENT.csv'), encoding='utf-8') as f:
    EVENTS = {r['Prefix']: r for r in csv.DictReader(f)}

LEVELS = {'J6': ('MAG', 'JR16'), 'J7': ('MAG', 'JR17'), ('Men', 'SR'): ('MAG', 'SR'),
          ('Women', 'JR'): ('WAG', 'JR'), ('Women', 'SR'): ('WAG', 'SR')}
APPS = {'MAG': ['FX', 'PH', 'SR', 'VT', 'PB', 'HB'], 'WAG': ['VT', 'UB', 'BB', 'FX']}
MULTI_APP = {'Floor': 'FX', 'Pommel': 'PH', 'Rings': 'SR', 'Vault': 'VT', 'P Bars': 'PB', 'H Bar': 'HB',
             'Bars': 'UB', 'Beam': 'BB'}
MONTHS = {m: i + 1 for i, m in enumerate('Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec'.split())}

errors = []
def err(msg):
    errors.append(msg)

def D(s):
    return Decimal(s) if s not in ('', None) else Decimal(0)

def blank(v):
    """ProScore prints an empty value as underscores (_._, _.__, __.___)."""
    return '' if v is None or v.startswith('_') or v == '' else v

def rank_num(v):
    """Ties are printed with a marker (1T, 45=, 2*); the stored rank is the number."""
    return re.sub(r'[T=*]$', '', v)

# ---------------------------------------------------------------- page classification
def classify(page):
    lines = [l for l in page.split('\n') if l.strip()]
    if len(lines) < 5 or 'U.S. Championships' not in lines[0]:
        return None
    m = re.search(r'- (Men|Women) Day (\d)', lines[0])
    gender, day = m.group(1), int(m.group(2))
    lv = re.search(r'(Men|Women) / (\w+) /', lines[2]).group(2)
    disc, age = LEVELS.get(lv) or LEVELS[(gender, lv)]
    report = re.split(r'\s{2,}', lines[3].strip())[0]
    date = re.match(r'(\w{3}) (\d+), (\d{4})', lines[4].strip())
    date = f'{date.group(3)}-{MONTHS[date.group(1)]:02d}-{int(date.group(2)):02d}'
    return dict(day=day, disc=disc, age=age, report=report, date=date, lines=lines)

REPORTS = defaultdict(list)     # (disc, age, report) -> [lines of every page]
SESSION_DATE = {}               # (disc, age, part) -> date
for page in PAGES:
    c = classify(page)
    if c is None:
        continue
    REPORTS[(c['disc'], c['age'], c['report'])].extend(c['lines'])
    if c['report'] in ('Meet Results', 'Meet Results - Multi'):
        SESSION_DATE[(c['disc'], c['age'], c['day'])] = c['date']

def tokens(line, label, pattern):
    """(value, end column) for every match of `pattern` after `label` on the line."""
    i = line.index(label) + len(label)
    return [(m.groups() if m.groups() else m.group(), i + m.end()) for m in re.finditer(pattern, line[i:])]

PAIR_DE = r'(_\.__|\d+\.\d+)/\s*(_\.___|\d+\.\d+)'
PAIR_BONUS = r'(_\.___|\d+\.\d+)\s*/\s*(_\._|\d+\.\d+)'
PAIR_SND = r'(_\._|-?\d+\.\d+)\s*/\s*(_\._|-?\d+\.\d+)'
SINGLE = r'_\._|-?\d+\.\d+'
FINAL = r'__\.___|\d+\.\d+'

def spread(values, finals, n, ctx):
    """Assign a line's values to apparatus. Lines print either every apparatus (placeholders
    for the skipped ones) or only the apparatus actually performed."""
    performed = [i for i in range(n) if blank(finals[i]) != '']
    if len(values) == n:
        return {i: values[i] for i in range(n)}
    if len(values) == len(performed):
        return dict(zip(performed, values))
    err(f'{ctx}: {len(values)} values for {n} apparatus ({len(performed)} performed)')
    return {}

def routine_block(disc, block, ctx):
    """Routines from one athlete's Final / D/E / D+/E+ / S+/ND (MAG) or Final / D/E / ND (WAG)
    lines. Returns (per-apparatus dict, AA total)."""
    n = len(APPS[disc])
    finals = [v for v, _ in tokens(block['Final:'], 'Final:', FINAL)]
    if len(finals) != n + 1:
        err(f'{ctx}: {len(finals)} Final values'); return {}, ''
    de = spread([v for v, _ in tokens(block['D/E:'], 'D/E:', PAIR_DE)], finals, n, ctx + ' D/E')
    if disc == 'MAG':
        bonus = spread([v for v, _ in tokens(block['D+/E+:'], 'D+/E+:', PAIR_BONUS)], finals, n, ctx + ' D+/E+')
        snd = spread([v for v, _ in tokens(block['S+/ND:'], 'S+/ND:', PAIR_SND)], finals, n, ctx + ' S+/ND')
    else:
        nd = spread([v for v, _ in tokens(block['ND:'], 'ND:', SINGLE)], finals, n, ctx + ' ND')
    out = {}
    for i, app in enumerate(APPS[disc]):
        fin = blank(finals[i])
        if fin == '':
            continue   # not entered on this apparatus: no row at all
        d, e = (blank(x) for x in de.get(i, ('', '')))
        if disc == 'MAG':
            dp, ep = (blank(x) for x in bonus.get(i, ('', '')))
            sp, pen = (blank(x) for x in snd.get(i, ('', '')))
        else:
            dp = ep = sp = ''
            pen = blank(nd.get(i, ''))
        r = dict(D=d, E=e, Dp=dp, Ep=ep, Sp=sp, Penalty=pen, Total=fin)
        if D(d) == 0 and D(e) == 0 and D(fin) == 0:
            out[app] = dict(Status='DNS', D='', E='', Dp='', Ep='', Sp='', Penalty='', Total='')
            continue
        r['Status'] = 'COMPLETED'
        if D(d) + D(e) + D(dp) + D(ep) + D(sp) + D(pen) != D(fin):
            err(f'{ctx} {app}: components {r} != {fin}')
        out[app] = r
    aa = blank(finals[n])
    total = sum((D(r['Total']) for r in out.values()), Decimal(0))
    if aa and total != D(aa):
        err(f'{ctx}: AA {aa} != apparatus sum {total}')
    return out, aa

def blocks(lines, start_re):
    """Split report lines into per-athlete blocks starting at lines matching start_re."""
    out, cur = [], None
    for l in lines:
        m = start_re.match(l)
        if m:
            cur = dict(head=m, lines=[l]); out.append(cur)
        elif cur is not None:
            cur['lines'].append(l)
    return out

def labelled(lines, labels):
    """First line containing each label, in order, from a block's lines."""
    found, i = {}, 0
    for lab in labels:
        while i < len(lines) and lab not in lines[i]:
            i += 1
        if i == len(lines):
            return None
        found[lab] = lines[i]; i += 1
    return found

# ---------------------------------------------------------------- Day 1: Meet Results
DAY1_START = re.compile(r'^\s*(\d+[T=*]?)\s+(\d+)\s+\*?(\S.*?)\s+D/E:')

def day1(disc, age):
    labels = ['D/E:', 'D+/E+:', 'S+/ND:', 'Final:', 'Place:'] if disc == 'MAG' else ['D/E:', 'ND:', 'Final:', 'Place:']
    out = {}
    for b in blocks(REPORTS[(disc, age, 'Meet Results')], DAY1_START):
        rank, bib, name = b['head'].groups()
        ctx = f'{disc} {age} day 1 bib {bib}'
        lab = labelled(b['lines'], labels)
        if lab is None:
            err(f'{ctx}: incomplete block'); continue
        routines, aa = routine_block(disc, lab, ctx)
        finals = tokens(lab['Final:'], 'Final:', FINAL)
        places = tokens(lab['Place:'], 'Place:', r'\d+[T=*]?')
        # places are right-aligned under the Final values; some (vault, for most levels) are blank
        for p, end in places:
            k = min(range(len(finals)), key=lambda j: abs(finals[j][1] - end))
            if abs(finals[k][1] - end) > 4:
                err(f'{ctx}: place {p} at {end} not under a Final value')
            if k == len(finals) - 1:
                if rank_num(p) != rank_num(rank): err(f'{ctx}: AA place {p} != rank {rank}')
            elif APPS[disc][k] in routines and routines[APPS[disc][k]]['Status'] == 'COMPLETED':
                routines[APPS[disc][k]]['Rank'] = rank_num(p)
        out[bib] = dict(Name=name.strip(), Rank=rank_num(rank), AA=aa, routines=routines)
    return out

# ---------------------------------------------------------------- Day 2: Meet Results - Multi
DAY2_START = re.compile(r'^\s*(\d+[T=*]?)\s+(\d+)\s+\*?(\S.*?)\s+Prelims\s+Final:')

def day2(disc, age, d1):
    out = {}
    per = ['D/E:', 'D+/E+:', 'S+/ND:'] if disc == 'MAG' else ['D/E:', 'ND:']
    for b in blocks(REPORTS[(disc, age, 'Meet Results - Multi')], DAY2_START):
        rank, bib, name = b['head'].groups()
        ctx = f'{disc} {age} day 2 bib {bib}'
        ls = b['lines']
        fi = next(i for i, l in enumerate(ls) if re.search(r'Finals\s+Final:', l))
        pre = labelled(ls[:fi], per)
        fin = labelled(ls[fi + 1:], per)
        if pre is None or fin is None:
            err(f'{ctx}: incomplete block'); continue
        pre['Final:'] = ls[0][ls[0].index('Prelims'):].replace('Prelims', '', 1)
        fin['Final:'] = ls[fi][ls[fi].index('Finals'):].replace('Finals', '', 1)
        r1, aa1 = routine_block(disc, pre, ctx + ' prelims')
        r2, aa2 = routine_block(disc, fin, ctx)
        if bib not in d1:
            err(f'{ctx}: not in Day 1 results')
        else:
            if aa1 != d1[bib]['AA']: err(f'{ctx}: prelims AA {aa1} != day 1 {d1[bib]["AA"]}')
            for app, r in r1.items():
                x = d1[bib]['routines'].get(app)
                if x is None or any(D(r[k]) != D(x[k]) for k in ('D', 'E', 'Dp', 'Ep', 'Sp', 'Penalty', 'Total')):
                    err(f'{ctx} {app}: prelims routine differs from day 1')
        ct = next((l for l in ls if 'Combined Total:' in l), None)
        combined = ct.split('Combined Total:')[1].split()[0]
        if D(aa1) + D(aa2) != D(combined):
            err(f'{ctx}: combined {combined} != {aa1} + {aa2}')
        out[bib] = dict(Name=name.strip(), CombinedRank=rank_num(rank), Combined=combined, AA=aa2, routines=r2)
    return out

# ---------------------------------------------------------------- Day 2: Event Results - Multi - <apparatus>
MULTI_START = re.compile(r'^\s*(\d+[T=*]?)\s+(\d+)\s+\*?(\S.*?)\s{2,}.*?Fin:')

def day_columns(lines):
    """End columns of the '#2' (Day 1) and '#1' (Day 2) headings — note the reversed numbering."""
    h = next(l for l in lines if re.search(r'#2\s+#1\s+Score', l))
    return {1: h.index('#2') + 2, 2: h.index('#1') + 2, 'Score': h.index('Score') + 5}

def by_day(line, label, pattern, cols):
    out = {}
    for v, end in tokens(line, label, pattern):
        k = min((1, 2, 'Score'), key=lambda c: abs(cols[c] - end))
        out[k] = v
    return out

def multi_event(disc, age, app_name):
    report = f'Event Results - Multi - {app_name}'
    ls = REPORTS[(disc, age, report)]
    if not ls:
        err(f'{disc} {age}: no {report}'); return {}
    cols = day_columns(ls)
    out = {}
    for b in blocks(ls, MULTI_START):
        place, bib, _ = b['head'].groups()
        fin = by_day(b['lines'][0], 'Fin:', r'\d+\.\d+', cols)
        r = dict(Rank=rank_num(place), Day={1: fin.get(1, ''), 2: fin.get(2, '')}, Score=fin.get('Score', ''))
        if D(r['Day'][1]) + D(r['Day'][2]) != D(r['Score']):
            err(f'{disc} {age} {app_name} bib {bib}: {r["Day"]} != {r["Score"]}')
        if app_name == 'Vault':
            r['vaults'] = vault_details(disc, b['lines'], cols, f'{disc} {age} vault bib {bib}')
        out[bib] = r
    return out

def vault_details(disc, lines, cols, ctx):
    """Both attempts for each day from a two-day vault block (D/E/D+/S+/ND pairs 'a/b', WAG Bns)."""
    labels = ['D:', 'E:', 'D+:', 'S+:', 'ND:'] if disc == 'MAG' else ['D:', 'E:', 'ND:', 'Bns:']
    lab = labelled(lines[1:], labels)
    if lab is None:
        err(f'{ctx}: incomplete vault block'); return {}
    pairs = {k: by_day(lab[k], k, r'(-?[\d.]*)/(-?[\d.]*)', cols) for k in labels if k != 'Bns:'}
    bns = by_day(lab['Bns:'], 'Bns:', r'\d+\.\d+', cols) if 'Bns:' in lab else {}
    out = {}
    for day in (1, 2):
        if day not in pairs['D:']:
            continue
        att = []
        for a in (0, 1):
            v = {k: pairs[k].get(day, ('', ''))[a] for k in pairs}
            att.append(dict(D=v['D:'], E=v['E:'], Dp=v.get('D+:', ''), Sp=v.get('S+:', ''), Penalty=v['ND:']))
        out[day] = dict(attempts=att, Bonus=bns.get(day, ''))
    return out

# ---------------------------------------------------------------- Day 1: Event Results - Vault
VALUE = r'_\._|-?\d+\.\d+'

def day1_vault_event(disc, age):
    """Day 1's two-vault list: rank and both attempts. It also covers athletes who didn't
    compete on Day 2, so they're absent from the two-day vault report."""
    out, cur = {}, None
    for l in REPORTS.get((disc, age, 'Event Results - Vault'), []):
        m = re.match(r'^\s*(\d+[T=*]?)\s+(\d+)\s+\*?\S', l)
        t = re.findall(VALUE, l)
        if m:
            # MAG: Diff Exec D+ S+ ND Score Out; WAG: Diff Exec ND Score Out
            t = t[-7:] if disc == 'MAG' else t[-5:]
            if disc == 'MAG':
                v1 = dict(D=t[0], E=t[1], Dp=blank(t[2]), Sp=blank(t[3]), Penalty=blank(t[4]))
            else:
                v1 = dict(D=t[0], E=t[1], Dp='', Sp='', Penalty=blank(t[2]))
            cur = dict(Rank=rank_num(m.group(1)), Score=t[-2], attempts=[v1], Bonus='')
            out[m.group(2)] = cur
        elif cur is not None and len(cur['attempts']) == 1 and t and re.match(r'^\s+\d\.\d{3}\s', l):
            if disc == 'MAG':
                v2 = dict(D=t[0], E=t[1], Dp=blank(t[2]), Sp=blank(t[3]), Penalty=blank(t[4]))
            else:
                v2 = dict(D=t[0], E=t[1], Dp='', Sp='', Penalty=blank(t[2]))
                cur['Bonus'] = t[3] if len(t) > 3 else ''
            cur['attempts'].append(v2)
    return out

# ---------------------------------------------------------------- output
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

def bonus_sum(r):
    parts = [r[k] for k in ('Dp', 'Ep', 'Sp') if r.get(k)]
    return f'{sum(D(p) for p in parts):.3f}' if parts else ''

SCORE_HDR = ['Event Name', 'Discipline', 'Age Category', 'Session Start Date', 'Round', 'Part', 'Apparatus',
             'Rank', 'Bib', 'Name', 'NOC Code']
SINGLE_COLS = ['Status', 'D', 'E', 'B', 'Penalty', 'SJ Penalty', 'Total']
BREAKDOWN = ['D+', 'E+', 'S+']

def single_cols(r, sfx=''):
    return {f'Status{sfx}': r['Status'], f'D{sfx}': r['D'], f'E{sfx}': r['E'], f'B{sfx}': bonus_sum(r),
            f'Penalty{sfx}': r['Penalty'], f'SJ Penalty{sfx}': '', f'D+{sfx}': r['Dp'],
            f'E+{sfx}': r['Ep'], f'S+{sfx}': r['Sp']} | ({'Total': r['Total']} if not sfx else {f'Total{sfx}': r['Total']})

def level(disc, age):
    prefix = f'{EVENT_PREFIX}-{disc}-{age}'
    ev = EVENTS[prefix]
    with open(os.path.join(OUT, f'{prefix}-PARTICIPANTS.csv'), encoding='utf-8') as f:
        people = {r['Bib Number']: r for r in csv.DictReader(f)}
    d1 = day1(disc, age)
    d2 = day2(disc, age, d1)
    for bib, a in list(d1.items()) + list(d2.items()):
        if bib not in people:
            err(f'{prefix}: bib {bib} ({a["Name"]}) missing from PARTICIPANTS')
        elif people[bib]['Name'] != a['Name']:
            err(f'{prefix}: bib {bib} printed {a["Name"]!r}, PARTICIPANTS has {people[bib]["Name"]!r}')
    multi = {app: multi_event(disc, age, name) for name, app in MULTI_APP.items() if app in APPS[disc]}
    vault_event = day1_vault_event(disc, age)
    two_vault = bool(vault_event) or any(D(v['vaults'][d]['attempts'][1]['D']) > 0
                                         for v in multi['VT'].values() for d in v['vaults'])

    def vault_day(part, bib):
        """Both attempts and the published day result for a vault entry, or None."""
        m = multi['VT'].get(bib)
        v = m['vaults'].get(part) if m else None
        if part == 1 and bib in vault_event:
            e = vault_event[bib]
            if v and any(D(a[k]) != D(b[k]) for a, b in zip(e['attempts'], v['attempts'])
                         for k in ('D', 'E', 'Dp', 'Sp', 'Penalty')):
                err(f'{prefix} vault bib {bib}: Day 1 vault list and two-day vault report disagree')
            if v and D(e['Score']) != D(m['Day'][1]):
                err(f'{prefix} vault bib {bib}: Day 1 score {e["Score"]} != two-day report {m["Day"][1]}')
            return dict(attempts=e['attempts'], Bonus=e['Bonus'], Total=e['Score'], Rank=e['Rank'])
        if v:
            return dict(attempts=v['attempts'], Bonus=v['Bonus'], Total=m['Day'][part], Rank='')
        return None

    def check_two_day(ctx, bib, part, app, total):
        """A routine's day score must match the two-day report — which only lists athletes
        who competed on this apparatus on both days (for vault: who contested the vault title,
        which isn't decidable from the AA results alone, so only listed athletes are checked)."""
        m = multi[app].get(bib)
        both_days = bib in d2 and app in d1.get(bib, {}).get('routines', {}) and app in d2[bib]['routines']
        if m is None:
            if both_days and app != 'VT':
                err(f'{ctx}: missing from the two-day report')
        elif D(m['Day'][part]) != D(total):
            err(f'{ctx}: two-day report has {m["Day"][part]} for {total}')

    def meta(part, app):
        return {'Event Name': ev['Title'], 'Discipline': disc, 'Age Category': age,
                'Session Start Date': SESSION_DATE[(disc, age, part)], 'Round': 'AA', 'Part': part, 'Apparatus': app}

    # ---- SCORES-AA-<APP>
    for app in APPS[disc]:
        rows = []
        for part, data in ((1, d1), (2, d2)):
            for bib, a in data.items():
                r = a['routines'].get(app)
                if r is None:
                    continue
                ctx = f'{prefix} part {part} {app} bib {bib}'
                row = meta(part, app) | {'Rank': r.get('Rank', ''), 'Bib': bib, 'Name': people[bib]['Name'],
                                         'NOC Code': 'USA'}
                if app != 'VT' or not two_vault:
                    row |= single_cols(r)
                    if r['Status'] == 'COMPLETED':
                        check_two_day(ctx, bib, part, app, r['Total'])
                    rows.append(row); continue
                v = vault_day(part, bib) if r['Status'] == 'COMPLETED' else None
                second = v is not None and len(v['attempts']) == 2 and D(v['attempts'][1]['D']) > 0
                if v and any(D(v['attempts'][0][k]) != D(r[k]) for k in ('D', 'E', 'Dp', 'Sp', 'Penalty')):
                    err(f'{ctx}: vault 1 differs between the AA results and the vault list')
                row |= single_cols(r, '1')
                if second:
                    s = dict(v['attempts'][1], Ep='', Status='COMPLETED')
                    s['Total'] = f'{D(s["D"]) + D(s["E"]) + D(s["Dp"]) + D(s["Sp"]) + D(s["Penalty"]):.3f}'
                    avg = (D(r['Total']) + D(s['Total'])) / 2 + D(v['Bonus'])
                    if abs(avg - D(v['Total'])) > Decimal('0.0015'):
                        err(f'{ctx}: average {avg} != published {v["Total"]}')
                    row |= single_cols(s, '2') | {'Combined Bonus': v['Bonus'], 'Total': v['Total']}
                    if not row['Rank']:
                        row['Rank'] = v['Rank']
                    check_two_day(ctx, bib, part, app, v['Total'])
                else:
                    # single vaulters don't contest the vault title, so aren't in the two-day vault report
                    row |= {f'{c}2': '' for c in SINGLE_COLS + BREAKDOWN if c != 'Total'} | {'Total2': '',
                            'Combined Bonus': '', 'Total': r['Total']}
                    if bib in multi['VT']:
                        err(f'{ctx}: in the two-day vault report but no second vault found')
                rows.append(row)
        rows.sort(key=lambda r: (r['Part'], int(r['Bib'])))
        if app == 'VT' and two_vault:
            hdr = SCORE_HDR + [f'{c}{n}' for n in (1, 2) for c in SINGLE_COLS] + ['Combined Bonus', 'Total'] + \
                  [f'{c}{n}' for n in (1, 2) for c in BREAKDOWN]
        else:
            hdr = SCORE_HDR + SINGLE_COLS + BREAKDOWN
        write(f'{prefix}-SCORES-AA-{app}.csv', hdr, rows)

    # ---- SUMMARY-FINAL-AA: per part, then the two-day combined standings (Part blank)
    order = ['FX', 'PH', 'SR', 'VT', 'PB', 'HB'] if disc == 'MAG' else ['FX', 'BB', 'UB', 'VT']
    hdr = ['Event Name', 'Discipline', 'Age Category', 'Event Start Date', 'Round', 'Part', 'Rank', 'Bib', 'Name',
           'NOC Code'] + [f'{a} Total' for a in order] + ['SJ Penalty', 'Total']
    rows = []
    base = {'Event Name': ev['Title'], 'Discipline': disc, 'Age Category': age, 'Event Start Date': ev['Start Date'],
            'Round': 'AA', 'NOC Code': 'USA', 'SJ Penalty': ''}
    for part, data in ((1, d1), (2, d2)):
        for bib, a in sorted(data.items(), key=lambda x: int(x[0])):
            rows.append(base | {'Part': part, 'Rank': a['Rank'] if part == 1 else '', 'Bib': bib,
                                'Name': people[bib]['Name'], 'Total': a['AA']} |
                        {f'{x} Total': a['routines'].get(x, {}).get('Total', '') for x in order})
    for bib, a in sorted(d2.items(), key=lambda x: int(x[1]['CombinedRank'])):
        rows.append(base | {'Part': '', 'Rank': a['CombinedRank'], 'Bib': bib, 'Name': people[bib]['Name'],
                            'Total': a['Combined']} | {f'{x} Total': '' for x in order})
    write(f'{prefix}-SUMMARY-FINAL-AA.csv', hdr, rows)

    # ---- SUMMARY-FINAL-<APP>: two-day combined apparatus standings
    for app in APPS[disc]:
        hdr = ['Event Name', 'Discipline', 'Age Category', 'Event Start Date', 'Round', 'Part', 'Apparatus', 'Rank',
               'Bib', 'Name', 'NOC Code', 'Part 1 Total', 'Part 2 Total', 'Total']
        rows = [base | {'Part': '', 'Apparatus': app, 'Rank': m['Rank'], 'Bib': bib, 'Name': people[bib]['Name'],
                        'Part 1 Total': m['Day'][1], 'Part 2 Total': m['Day'][2], 'Total': m['Score']}
                for bib, m in sorted(multi[app].items(), key=lambda x: int(x[1]['Rank']))]
        for r in rows:
            r.pop('SJ Penalty')
        write(f'{prefix}-SUMMARY-FINAL-{app}.csv', hdr, rows)

    # ---- PARTICIPANTS in the standard shape (Club kept as a trailing reference column)
    hdr = ['Event Name', 'Discipline', 'Age Category', 'Event Start Date', 'Order', 'NOC Code', 'Bib Number',
           'FIG License', 'Name', 'Surname', 'Given Name', 'Birth Year', 'Birth Date', 'Club']
    rows = [p | {'Order': i + 1, 'NOC Code': 'USA', 'Birth Date': p.get('Birth Date', '')}
            for i, p in enumerate(sorted(people.values(), key=lambda p: int(p['Bib Number'])))]
    write(f'{prefix}-PARTICIPANTS.csv', hdr, rows)

    files = sorted(os.listdir(OUT))
    manifest = {
        'event': {'title': ev['Title'], 'disciplines': ev['Disciplines'], 'ageCategory': ev['Age Category'],
                  'level': ev['Level'], 'startDate': ev['Start Date'], 'endDate': ev['End Date'], 'city': ev['City'],
                  'country': ev['Country'], 'figEventNumber': ev['FIG Event Number'], 'venue': ev['Venue'],
                  'apparatusSuppliers': ev['Apparatus Suppliers']},
        'participants': [f for f in files if f.startswith(prefix + '-PARTICIPANTS')],
        'judges': [f for f in files if f.startswith(prefix + '-JUDGES')],
        'scores': [f for f in files if f.startswith(prefix + '-SCORES-')],
        'summaries': [f for f in files if f.startswith(prefix + '-SUMMARY-')],
    }
    with open(os.path.join(OUT, f'{prefix}-IMPORT.json'), 'w') as f:
        json.dump(manifest, f, indent=4, separators=(',', ' : '))
        f.write('\n')

for disc, age in [('MAG', 'SR'), ('MAG', 'JR16'), ('MAG', 'JR17'), ('WAG', 'SR'), ('WAG', 'JR')]:
    print(f'===== {disc} {age}')
    level(disc, age)

print(f'\n{len(errors)} errors')
for e in errors:
    print(' ', e)
sys.exit(1 if errors else 0)
