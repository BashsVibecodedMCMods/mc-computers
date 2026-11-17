import re, sys, os
root = sys.argv[1]
src = os.path.join(root, 'src/main/java/dev/computah')
files = [
 ('computer', 'machine/Machine.java', 'The computer itself (its own address).'),
 ('eeprom', 'component/EepromComponent.java', 'The BIOS chip. See EEPROM.md.'),
 ('filesystem', 'component/FilesystemComponent.java', 'Hard drives, floppies, the OS disk and the tmpfs. Paths use "/" and are relative to the drive.'),
 ('gpu', 'component/GpuComponent.java', 'Graphics card. Coordinates start at 1. See GRAPHICS.md.'),
 ('screen', 'component/ScreenComponent.java', 'A (multiblock) monitor.'),
 ('keyboard', 'component/KeyboardComponent.java', 'No methods; sends key_down, key_up and clipboard signals.'),
 ('modem', 'network/ModemComponent.java', 'Network card (wired) or wireless card. See NETWORKING.md.'),
 ('redstone', 'component/RedstoneComponent.java', 'Redstone card (the case or rack sides) or redstone I/O block. Sides: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east.'),
 ('disk_drive', 'block/DiskDriveBlockEntity.java', 'A disk drive block. Its floppy appears as a separate filesystem component.'),
 ('inventory / tank / energy', 'component/AdapterComponents.java', 'Blocks reached through an adapter or a wireless link. Every method takes a tick.'),
]
out = ['# Component reference', '', 'Generated from the `@Callback` documentation in the source. Regenerate it with `python3 tools/components_doc.py .` after changing a component; a unit test fails when a method is missing here.',
       '', 'Methods marked **(tick)** are indirect: they run on the server thread, and the calling program waits for the next tick. Others are direct and return immediately (some are limited per tick, see GRAPHICS.md for GPU budgets).', '']
def callbacks(text):
    i = 0
    while True:
        i = text.find('@Callback', i)
        if i < 0:
            return
        j = i + len('@Callback')
        args = ''
        if text[j] == '(':
            depth, k, quoted = 0, j, False
            while True:
                c = text[k]
                if quoted:
                    if c == '\\':
                        k += 1
                    elif c == '"':
                        quoted = False
                elif c == '"':
                    quoted = True
                elif c == '(':
                    depth += 1
                elif c == ')':
                    depth -= 1
                    if depth == 0:
                        break
                k += 1
            args = text[j + 1:k]
            j = k + 1
        m = re.match(r'\s*public\s+(?:synchronized\s+)?Object\[\]\s+(\w+)\s*\(', text[j:])
        if m:
            yield args, m.group(1)
        i = j
for name, path, desc in files:
    text = open(os.path.join(src, path)).read()
    out.append('## ' + name)
    out.append('')
    out.append(desc)
    out.append('')
    found = False
    for args, method in callbacks(text):
        val = re.search(r'value\s*=\s*"([^"]*)"', args) or re.match(r'\s*"([^"]*)"', args)
        lua = val.group(1) if val else method
        doc = re.search(r'doc\s*=\s*"((?:[^"\\]|\\.)*)"', args)
        doc = doc.group(1).replace('\\"', '"') if doc else ''
        indirect = 'direct = false' in args
        sig, _, d = doc.partition(' -- ')
        sig = sig.replace('function', lua, 1) if sig.startswith('function') else lua + '()'
        out.append(f'- `{sig}`{" **(tick)**" if indirect else ""} — {d}')
        found = True
    if not found:
        out.append('(no methods)')
    out.append('')
out += ['## Signals', '',
 '| Signal | Values | Sent by |', '|---|---|---|',
 '| `component_added` | address, type | a component became visible |',
 '| `component_removed` | address, type | a component went away |',
 '| `key_down` / `key_up` | keyboard, char, code, player | keyboard (codes are GLFW key codes, see `keyboard.keys`) |',
 '| `clipboard` | keyboard, text, player | Ctrl+Shift+V in the screen GUI |',
 '| `touch` / `drag` / `drop` | screen, x, y, button, player | screen GUI clicks; right-click on tier 2+ screens in the world |',
 '| `scroll` | screen, x, y, direction, player | mouse wheel in the screen GUI |',
 '| `screen_resized` | screen, width, height | gpu.setResolution |',
 '| `modem_message` | receiver, sender, port, distance, values... | modem |',
 '| `redstone_changed` | address, side, old, new | redstone card / redstone I/O |',
 '']
open(os.path.join(root, 'docs/COMPONENTS.md'), 'w').write('\n'.join(out))
print('ok')
