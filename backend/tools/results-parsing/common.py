"""Shared helpers for the per-meet results parsers in this folder.

Each meet gets its own `parse_<year>_<event>.py` (source layouts differ too much to share a
parser), but they all write the same CSV standard (docs/import-file-model.md) and run the
same kinds of checks (docs/instructions-results-parsing.md §3). Those parts live here.

Parsers run with `python3 -I`, which leaves the script's folder off sys.path, so import this as:

    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import common
"""
import csv, json, os, re, subprocess, sys
from decimal import Decimal

# ---------------------------------------------------------------- sources
def pdf_pages(path):
    """`pdftotext -layout` output split into physical pages (index 0 = page 1)."""
    return subprocess.run(['pdftotext', '-layout', path, '-'], check=True, capture_output=True,
                          text=True).stdout.split('\f')

def read_events(out_dir, event_prefix):
    """Rows of `<event_prefix>-EVENT.csv`, keyed by their Prefix column."""
    with open(os.path.join(out_dir, f'{event_prefix}-EVENT.csv'), encoding='utf-8') as f:
        return {r['Prefix']: r for r in csv.DictReader(f)}

# ---------------------------------------------------------------- values
def D(s):
    """A printed score as a Decimal; blank counts as zero."""
    return Decimal(s) if s else Decimal(0)

def rank_number(v):
    """Tie markers vary by source (1T, 45=, 2*); the stored rank is the number alone."""
    return re.sub(r'[T=*]$', '', v)

# ---------------------------------------------------------------- names
PARTICLES = {'Mc', 'van', 'der', 'den', 'de', 'le', 'la', 'du', 'da', 'di', 'von', 'dos', 'del'}

def surname_word(w):
    return re.fullmatch(r"(Mc|Mac)?[^a-z]*[A-Z][^a-z]*", w) is not None

def split_surname_first(name):
    """'SURNAME Given' (FIG results books) -> (surname, given). The surname is the leading run of
    upper-case words, including lowercase particles (`van POL`) and `McDONALD`-style prefixes."""
    words = name.split(' ')
    i = 0
    while i < len(words) and (surname_word(words[i]) or words[i] in PARTICLES):
        i += 1
    return ' '.join(words[:i]), ' '.join(words[i:])

def split_given_first(name):
    """'Given SURNAME' (organizer books) -> (surname, given). The surname is the trailing run of
    upper-case words (`Kilan VAN DER AA`)."""
    words = name.split(' ')
    i = len(words)
    while i > 0 and surname_word(words[i - 1]):
        i -= 1
    return ' '.join(words[i:]), ' '.join(words[:i])

# ---------------------------------------------------------------- column sets (docs/import-file-model.md)
SCORE_META = ['Event Name', 'Discipline', 'Age Category', 'Session Start Date', 'Round', 'Part', 'Apparatus',
              'Rank', 'Bib', 'Name', 'NOC Code']
ROUTINE = ['Status', 'D', 'E', 'B', 'Penalty', 'SJ Penalty', 'Total']
VAULT = [f'{c}{n}' for n in (1, 2) for c in ROUTINE] + ['Combined Bonus', 'Total']
QUALIFICATION = ['Qualification', 'Qualification Rank']
PARTICIPANTS = ['Event Name', 'Discipline', 'Age Category', 'Event Start Date', 'Order', 'NOC Code', 'Bib Number',
                'FIG License', 'Name', 'Surname', 'Given Name', 'Birth Year', 'Birth Date']
JUDGES = ['Event Name', 'Discipline', 'Age Category', 'Event Start Date', 'Round', 'Apparatus', 'Function', 'Name',
          'Surname', 'Given Name', 'NOC Code']

def score_header(vault, qual):
    """SCORES-* header: single-routine or two-attempt vault shape, with or without qualification columns."""
    return SCORE_META + (VAULT if vault else ROUTINE) + (QUALIFICATION if qual else [])

# ---------------------------------------------------------------- output
class Output:
    """Writes CSVs and the IMPORT.json manifest into one event folder."""

    def __init__(self, out_dir):
        self.dir = out_dir

    def csv(self, name, header, rows):
        with open(os.path.join(self.dir, name), 'w', newline='', encoding='utf-8') as f:
            w = csv.writer(f, lineterminator='\n')
            w.writerow(header)
            for r in rows:
                missing = [h for h in header if h not in r]
                if missing:
                    raise SystemExit(f'{name}: row missing {missing}: {r}')
                w.writerow([r[h] for h in header])
        print(f'{name}: {len(rows)} rows')

    def manifest(self, prefix, event):
        """`<prefix>-IMPORT.json`: the EVENT.csv row plus every file written for this prefix."""
        files = sorted(os.listdir(self.dir))
        manifest = {
            'event': {'title': event['Title'], 'disciplines': event['Disciplines'],
                      'ageCategory': event['Age Category'], 'level': event['Level'],
                      'startDate': event['Start Date'], 'endDate': event['End Date'], 'city': event['City'],
                      'country': event['Country'], 'figEventNumber': event['FIG Event Number'],
                      'venue': event['Venue'], 'apparatusSuppliers': event['Apparatus Suppliers']},
            'participants': [f for f in files if f.startswith(prefix + '-PARTICIPANTS')],
            'judges': [f for f in files if f.startswith(prefix + '-JUDGES')],
            'scores': [f for f in files if f.startswith(prefix + '-SCORES-')],
            'summaries': [f for f in files if f.startswith(prefix + '-SUMMARY-')],
        }
        with open(os.path.join(self.dir, f'{prefix}-IMPORT.json'), 'w') as f:
            json.dump(manifest, f, indent=4, separators=(',', ' : '))
            f.write('\n')

# ---------------------------------------------------------------- checks
class Checks:
    """Collects check failures; `finish()` prints them and exits non-zero if there were any."""

    def __init__(self):
        self.errors = []

    def err(self, msg):
        self.errors.append(msg)

    def check_sum(self, ctx, total, *parts):
        """Routine components (D, E, bonus, penalty, …) must add up to the printed score."""
        if sum((D(p) for p in parts), Decimal(0)) != D(total):
            self.err(f'{ctx}: {" + ".join(p or "0" for p in parts)} != {total}')

    def check_vault_average(self, ctx, first, second, combined_bonus, published):
        """A two-vault result is the average of the attempts, truncated to 3 decimals, plus any
        combined bonus."""
        avg = (D(first) + D(second)) / 2 + D(combined_bonus)
        if not Decimal(0) <= avg - D(published) < Decimal('0.001'):
            self.err(f'{ctx}: average {avg} != published {published}')

    def check_rank_order(self, ctx, rows):
        """Ranked rows: ranks never decrease, totals never increase, tied ranks have equal totals."""
        ranked = [r for r in rows if r['Rank']]
        for a, b in zip(ranked, ranked[1:]):
            if int(b['Rank']) < int(a['Rank']) or D(b['Total']) > D(a['Total']):
                self.err(f'{ctx}: rank order broken at bib {b["Bib"]}')
            if int(b['Rank']) == int(a['Rank']) and D(b['Total']) != D(a['Total']):
                self.err(f'{ctx}: tied rank with different totals at bib {b["Bib"]}')

    def finish(self):
        print(f'\n{len(self.errors)} errors')
        for e in self.errors:
            print(' ', e)
        sys.exit(1 if self.errors else 0)
