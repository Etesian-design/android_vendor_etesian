"""Turns an N9 (Immersion) .ivt vibration into on/off timings, as Android's simple vibrator
plays them: one line of comma-separated milliseconds (off, on, off, on, ...).

The file: a header (version, effect count, data size, names size) and an offset table, then
the effects. Timeline effects are byte code: F1 starts an effect, E0 n = which one, E2 t = at
t ms (big-endian), D0 d = for d ms, D1 m = at magnitude m (of 127), FF = end. Basic effects
start 0x20 (a single push) or 0x30 (periodic: the next two bytes, little-endian, are its
length in ms, followed by the 0x20 block)."""
import struct, sys

DEFAULT_PUSH = 40  # ms; a single push whose length the timeline doesn't give

def effects(b):
    _, n, size, _ = struct.unpack_from('<HHHH', b, 0)
    offs = struct.unpack_from('<%dH' % n, b, 8)
    data = b[8 + 2 * n: 8 + 2 * n + size]
    return [data[offs[i]:(offs[i + 1] if i + 1 < n else size)] for i in range(n)]

def length(e):
    if e[:1] == b'\x30':
        ms = e[1] | e[2] << 8
        return ms or DEFAULT_PUSH
    return DEFAULT_PUSH

def magnitude(e):
    """The basic effect's own strength (of 127), from its 0x20 block."""
    j = e.find(b'\x20', 8 if e[:1] == b'\x30' else 0)
    return e[j + 4] if 0 <= j and j + 4 < len(e) else 127

def launches(tl):
    out, i, cur = [], 0, None
    while i < len(tl):
        op = tl[i]
        if op == 0xF1:
            if cur: out.append(cur)
            cur = {'t': 0, 'e': 0, 'd': None, 'm': 127}; i += 1
        elif cur is None: i += 1  # loop header and the like, before the first launch
        elif op == 0xE0: cur['e'] = tl[i + 1]; i += 2
        elif op == 0xE2: cur['t'] = tl[i + 1] << 8 | tl[i + 2]; i += 3
        elif op == 0xD0: cur['d'] = tl[i + 1] << 8 | tl[i + 2]; i += 3
        elif op == 0xD1: cur['m'] = tl[i + 1]; i += 2
        elif op == 0xD2: i += 3
        elif op == 0xFF: break
        else: i += 1
    if cur: out.append(cur)
    return out

def pattern(path, timeline=0, min_magnitude=0x50):
    effs = effects(open(path, 'rb').read())
    spans = []
    for l in launches(effs[timeline]):
        if l['e'] >= len(effs) or min(l['m'], magnitude(effs[l['e']])) < min_magnitude: continue
        d = l['d'] if l['d'] is not None else length(effs[l['e']])
        spans.append((l['t'], l['t'] + d))
    spans.sort()
    merged = []
    for s, e in spans:
        if merged and s <= merged[-1][1]: merged[-1][1] = max(merged[-1][1], e)
        else: merged.append([s, e])
    timings, t = [], 0
    for s, e in merged:
        timings += [s - t, e - s]; t = e
    return timings

if __name__ == '__main__':
    print(','.join(map(str, pattern(sys.argv[1]))))
