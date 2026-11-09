"""Stack a horizontal band of every page of a PDF into one PNG, each strip labelled with its page
number, so a long or scanned results book can be mapped in a single image view.

Usage (needs poppler's pdftoppm on PATH):

    python3 -I contact_sheet.py <pdf> <out.png> [--pages 8-69] [--top 70] [--height 50] [--dpi 60]

`--top`/`--height` select the band, in pixels at `--dpi` (defaults catch the title block of an A4
page). Pure standard library: pdftoppm writes PGM, which is stacked and encoded as PNG here.
"""
import argparse, os, re, struct, subprocess, sys, tempfile, zlib

# 3x5 bitmap digits for the page-number labels
FONT = {'0': '111101101101111', '1': '010110010010111', '2': '111001111100111', '3': '111001111001111',
        '4': '101101111001001', '5': '111100111001111', '6': '111100111101111', '7': '111001001001001',
        '8': '111101111101111', '9': '111101111001111'}

def read_pgm(path):
    data = open(path, 'rb').read()
    fields, i = [], 0
    while len(fields) < 4:
        while data[i:i + 1].isspace():
            i += 1
        j = i
        while not data[j:j + 1].isspace():
            j += 1
        fields.append(data[i:j]); i = j
    w, h = int(fields[1]), int(fields[2])
    return w, h, data[i + 1:i + 1 + w * h]

def label(text, h, scale=3):
    w = len(text) * 4 * scale + 4
    px = bytearray([255]) * (w * h)
    for ci, ch in enumerate(text):
        for r in range(5):
            for c in range(3):
                if FONT[ch][r * 3 + c] == '1':
                    for dy in range(scale):
                        for dx in range(scale):
                            y, x = 4 + r * scale + dy, 2 + (ci * 4 + c) * scale + dx
                            if y < h:
                                px[y * w + x] = 0
    return w, px

def png(width, height, raw):
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 0, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b''))

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('pdf')
    ap.add_argument('out')
    ap.add_argument('--pages', help='first-last physical pages (default: all)')
    ap.add_argument('--top', type=int, default=70)
    ap.add_argument('--height', type=int, default=50)
    ap.add_argument('--dpi', type=int, default=60)
    a = ap.parse_args()

    if a.pages:
        first, last = (int(x) for x in a.pages.split('-'))
    else:
        info = subprocess.run(['pdfinfo', a.pdf], check=True, capture_output=True, text=True).stdout
        first, last = 1, int(re.search(r'Pages:\s+(\d+)', info).group(1))
    width = round(8.27 * a.dpi)   # A4 width
    rows = []
    with tempfile.TemporaryDirectory() as tmp:
        for n in range(first, last + 1):
            prefix = os.path.join(tmp, f'p{n}')
            subprocess.run(['pdftoppm', '-f', str(n), '-l', str(n), '-r', str(a.dpi), '-gray', '-x', '0',
                            '-y', str(a.top), '-W', str(width), '-H', str(a.height), a.pdf, prefix], check=True)
            path = next(os.path.join(tmp, f) for f in os.listdir(tmp) if f.startswith(f'p{n}-'))
            w, h, px = read_pgm(path)
            os.remove(path)
            lw, lpx = label(str(n), h)
            rows.append((lw, w, h, lpx, px))
    total_w = max(lw + w for lw, w, _, _, _ in rows)
    raw = bytearray()
    for lw, w, h, lpx, px in rows:
        for y in range(h):
            line = bytes(lpx[y * lw:(y + 1) * lw]) + bytes(px[y * w:(y + 1) * w])
            raw += b'\x00' + line + b'\xff' * (total_w - len(line))
        raw += b'\x00' + b'\x80' * total_w   # separator line
    with open(a.out, 'wb') as f:
        f.write(png(total_w, sum(h + 1 for _, _, h, _, _ in rows), raw))
    print(f'{a.out}: pages {first}-{last}')

if __name__ == '__main__':
    sys.exit(main())
