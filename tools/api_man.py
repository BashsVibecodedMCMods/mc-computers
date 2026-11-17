"""Generates the in-game manual pages (os/usr/man/<library>) from docs/API.md.

Run after changing docs/API.md:  python3 tools/api_man.py .
"""
import os
import re
import sys
import textwrap

root = sys.argv[1] if len(sys.argv) > 1 else "."
src = open(os.path.join(root, "docs/API.md"), encoding="utf-8").read()
out_dir = os.path.join(root, "src/main/resources/data/computah/lua/os/usr/man")
WIDTH = 50


def plain(s):
    s = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", s)
    s = s.replace("**", "").replace("`", "")
    return s


def render(name, body):
    lines = [f"{name.upper()} - Computah OS library", "(generated from docs/API.md)", ""]
    in_code = False
    para = []

    def flush():
        if para:
            lines.extend(textwrap.wrap(plain(" ".join(para)), WIDTH))
            para.clear()

    for raw in body.splitlines():
        if raw.startswith("```"):
            flush()
            in_code = not in_code
            continue
        if in_code:
            lines.append("  " + raw)
            continue
        line = raw.rstrip()
        if line.startswith("### "):
            flush()
            if lines and lines[-1] != "":
                lines.append("")
            lines.append(plain(line[4:]))
            continue
        if line.startswith("|"):
            flush()
            cells = [plain(c.strip()) for c in line.strip("|").split("|")]
            if all(set(c) <= set("-: ") for c in cells):
                continue
            lines.extend(textwrap.wrap(" - ".join(c for c in cells if c), WIDTH, subsequent_indent="    "))
            continue
        if line.startswith("- "):
            flush()
            lines.extend(textwrap.wrap(plain(line), WIDTH, subsequent_indent="  "))
            continue
        if not line.strip():
            flush()
            if lines and lines[-1] != "":
                lines.append("")
            continue
        para.append(line.strip())
    flush()
    return "\n".join(l.rstrip() for l in lines).strip() + "\n"


sections = re.split(r"^## ", src, flags=re.M)[1:]
count = 0
for sec in sections:
    title, _, body = sec.partition("\n")
    name = title.strip()
    if not re.fullmatch(r"[a-z]+", name):
        continue
    body = body.split("\n---", 1)[0]
    with open(os.path.join(out_dir, name), "w", encoding="utf-8") as f:
        f.write(render(name, body))
    count += 1
print(f"wrote {count} manual pages")
