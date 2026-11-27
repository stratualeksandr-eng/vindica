#!/usr/bin/env python3
# The centre of the first element of a uiautomator dump whose text or description matches the pattern: "x y".
import re, sys
xml, pat = sys.argv[1], re.compile(sys.argv[2], re.I)
try:
    s = open(xml, encoding='utf-8', errors='replace').read()
except OSError:
    sys.exit(0)
for m in re.finditer(r'<node [^>]*>', s):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n)
    d = re.search(r' content-desc="([^"]*)"', n)
    b = re.search(r' bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b:
        continue
    if any(x and pat.fullmatch(x.group(1).strip()) for x in (t, d)):
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
