import json, os, sys
from PIL import Image
ROOT = sys.argv[1]
A = os.path.join(ROOT, 'src/main/resources/assets/computah')
D = os.path.join(ROOT, 'src/main/resources/data')
M = 'computah'

def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')

# Composite the running front of the case.
tex = os.path.join(A, 'textures/block')
base = Image.open(os.path.join(tex, 'case_front.png')).convert('RGBA')
over = Image.open(os.path.join(tex, 'overlay/case_front_on.png')).convert('RGBA')
Image.alpha_composite(base, over).save(os.path.join(tex, 'case_front_running.png'))
rb = Image.open(os.path.join(tex, 'rack_front.png')).convert('RGBA')
srv = Image.open(os.path.join(tex, 'rack_server.png')).convert('RGBA')
Image.alpha_composite(rb, srv).save(os.path.join(tex, 'rack_front_full.png'))

def tinted_cube(name, faces, tint=True):
    """faces: dict north/south/east/west/up/down -> texture"""
    textures = {k: f'{M}:block/{v}' for k, v in faces.items()}
    textures['particle'] = textures['north']
    el = {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {}}
    for d in ['north', 'south', 'east', 'west', 'up', 'down']:
        face = {'texture': '#' + d, 'cullface': d}
        if tint: face['tintindex'] = 0
        el['faces'][d] = face
    w(f'{A}/models/block/{name}.json', {'parent': 'block/block', 'textures': textures, 'elements': [el]})

H = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

def horizontal_state(block, model_for):
    variants = {}
    for facing, rot in H.items():
        for extra, model in model_for(facing):
            key = f'facing={facing}' + (',' + extra if extra else '')
            v = {'model': f'{M}:block/{model}'}
            if rot: v['y'] = rot
            variants[key] = v
    w(f'{A}/blockstates/{block}.json', {'variants': variants})

for t in (1, 2, 3):
    pass
tinted_cube('case', {'north': 'case_front', 'south': 'case_back', 'east': 'case_side', 'west': 'case_side', 'up': 'case_top', 'down': 'case_top'})
tinted_cube('case_running', {'north': 'case_front_running', 'south': 'case_back_running', 'east': 'case_side_running', 'west': 'case_side_running', 'up': 'case_top', 'down': 'case_top'})
for t in (1, 2, 3):
    horizontal_state(f'case{t}', lambda f: [('running=false', 'case'), ('running=true', 'case_running')])
    w(f'{A}/models/item/case{t}.json', {'parent': f'{M}:block/case'})

PARTS = ['single', 'first', 'middle', 'last']
def tile(prefix, h, v):
    if h == 'single' and v == 'single':
        return prefix
    if v == 'single':
        return prefix + {'first': 'ht', 'middle': 'hm', 'last': 'hb'}[h]
    if h == 'single':
        return prefix + {'first': 'vt', 'middle': 'vm', 'last': 'vb'}[v]
    return prefix + {'first': 't', 'middle': 'm', 'last': 'b'}[v] + {'first': 'l', 'middle': 'm', 'last': 'r'}[h]
MIRROR = {'single': 'single', 'first': 'last', 'middle': 'middle', 'last': 'first'}
for h in PARTS:
    for v in PARTS:
        # The back is seen from behind, so its left and right are swapped.
        tinted_cube(f'screen_{h}_{v}', {'north': 'screen/' + tile('f', h, v), 'south': 'screen/' + tile('b', MIRROR[h], v),
                                         'east': 'screen/b', 'west': 'screen/b', 'up': 'screen/b', 'down': 'screen/b'})
for t in (1, 2, 3):
    variants = {}
    for facing, rot in H.items():
        for h in PARTS:
            for v in PARTS:
                var = {'model': f'{M}:block/screen_{h}_{v}'}
                if rot: var['y'] = rot
                variants[f'facing={facing},horizontal={h},vertical={v}'] = var
    w(f'{A}/blockstates/screen{t}.json', {'variants': variants})
    w(f'{A}/models/item/screen{t}.json', {'parent': f'{M}:block/screen_single_single'})

tinted_cube('disk_drive', {'north': 'diskdrive_front', 'south': 'diskdrive_side', 'east': 'diskdrive_side', 'west': 'diskdrive_side', 'up': 'generic_top', 'down': 'generic_top'}, tint=False)
tinted_cube('rack', {'north': 'rack_front_full', 'south': 'rack_side', 'east': 'rack_side', 'west': 'rack_side', 'up': 'rack_side', 'down': 'rack_side'}, tint=False)
tinted_cube('relay', {'north': 'switch_side', 'south': 'switch_side', 'east': 'switch_side', 'west': 'switch_side', 'up': 'switch_top', 'down': 'switch_top'}, tint=False)
tinted_cube('adapter', {'north': 'adapter_side', 'south': 'adapter_side', 'east': 'adapter_side', 'west': 'adapter_side', 'up': 'adapter_top', 'down': 'adapter_top'}, tint=False)
tinted_cube('redstone_io', {'north': 'redstone_north', 'south': 'redstone_south', 'east': 'redstone_east', 'west': 'redstone_west', 'up': 'redstone_top', 'down': 'redstone_bottom'}, tint=False)
for b in ['disk_drive', 'rack']:
    horizontal_state(b, lambda f, b=b: [('', b)])
for b in ['relay', 'adapter', 'redstone_io']:
    w(f'{A}/blockstates/{b}.json', {'variants': {f'facing={f}': {'model': f'{M}:block/{b}'} for f in H}})
for b in ['disk_drive', 'rack', 'relay', 'adapter', 'redstone_io']:
    w(f'{A}/models/item/{b}.json', {'parent': f'{M}:block/{b}'})

# Keyboard: a plate lying on its attached face. Model faces up (attached below); rotate for other facings.
w(f'{A}/models/block/keyboard.json', {'parent': 'block/block', 'textures': {'top': f'{M}:block/keyboard', 'side': f'{M}:block/generic_side', 'particle': f'{M}:block/keyboard'},
  'elements': [{'from': [1, 0, 3], 'to': [15, 2, 13], 'faces': {
      'up': {'texture': '#top', 'uv': [0, 3, 16, 13]}, 'down': {'texture': '#side', 'cullface': 'down'},
      'north': {'texture': '#side', 'uv': [1, 14, 15, 16]}, 'south': {'texture': '#side', 'uv': [1, 14, 15, 16]},
      'east': {'texture': '#side', 'uv': [3, 14, 13, 16]}, 'west': {'texture': '#side', 'uv': [3, 14, 13, 16]}}}]})
kb = {'up': {}, 'down': {'x': 180}, 'north': {'x': 90}, 'south': {'x': 90, 'y': 180}, 'west': {'x': 90, 'y': 270}, 'east': {'x': 90, 'y': 90}}
w(f'{A}/blockstates/keyboard.json', {'variants': {f'facing={f}': dict({'model': f'{M}:block/keyboard'}, **r) for f, r in kb.items()}})
w(f'{A}/models/item/keyboard.json', {'parent': f'{M}:block/keyboard'})

# Cable: multipart core + arms.
w(f'{A}/models/block/cable_core.json', {'parent': 'block/block', 'textures': {'t': f'{M}:block/cable', 'particle': f'{M}:block/cable'},
  'elements': [{'from': [5, 5, 5], 'to': [11, 11, 11], 'faces': {d: {'texture': '#t'} for d in ['north', 'south', 'east', 'west', 'up', 'down']}}]})
w(f'{A}/models/block/cable_arm.json', {'parent': 'block/block', 'textures': {'t': f'{M}:block/cable', 'particle': f'{M}:block/cable'},
  'elements': [{'from': [5.5, 5.5, 0], 'to': [10.5, 10.5, 5], 'faces': {d: {'texture': '#t'} for d in ['north', 'east', 'west', 'up', 'down']}}]})
arms = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
parts = [{'apply': {'model': f'{M}:block/cable_core'}}]
for d, r in arms.items():
    parts.append({'when': {d: 'true'}, 'apply': dict({'model': f'{M}:block/cable_arm'}, **r)})
w(f'{A}/blockstates/cable.json', {'multipart': parts})
w(f'{A}/models/item/cable.json', {'parent': 'item/generated', 'textures': {'layer0': f'{M}:block/cable'}})

items = {'cpu1': 'cpu1', 'cpu2': 'cpu2', 'cpu3': 'cpu3', 'ram1': 'ram1', 'ram2': 'ram3', 'ram3': 'ram5', 'ram4': 'ram6',
         'hdd1': 'hdd1', 'hdd2': 'hdd2', 'hdd3': 'hdd3', 'eeprom': 'eeprom',
         'gpu1': 'graphicscard1', 'gpu2': 'graphicscard2', 'gpu3': 'graphicscard3', 'network_card': 'lancard',
         'wireless_card1': 'wlancard1', 'wireless_card2': 'wlancard2', 'redstone_card': 'redstonecard1', 'server': 'server3', 'linker': 'analyzer'}
for name, t in items.items():
    w(f'{A}/models/item/{name}.json', {'parent': 'item/generated', 'textures': {'layer0': f'{M}:item/{t}'}})

# Floppies: one item; the OS disk and dyed disks are data, shown through the computah:variant property
# (0 = blank, 0.5 = OS, 1 = dyed). Dyed disks add a mask of the disk body, tinted with the vanilla dye colour.
fl = Image.open(os.path.join(A, 'textures/item/floppy.png')).convert('RGBA')
mask = Image.new('RGBA', fl.size, (0, 0, 0, 0))
body = [fl.getpixel((x, y)) for y in range(fl.size[1]) for x in range(fl.size[0])]
body = [p for p in body if p[3] and p[0] < 0xd0]
top = max(p[0] for p in body)
for y in range(fl.size[1]):
    for x in range(fl.size[0]):
        r, g, b, a = fl.getpixel((x, y))
        if a and r < 0xd0:
            v = min(255, round(r * 255 / top))
            mask.putpixel((x, y), (v, v, v, 255))
mask.save(os.path.join(A, 'textures/item/floppy_tint.png'))
w(f'{A}/models/item/floppy.json', {'parent': 'item/generated', 'textures': {'layer0': f'{M}:item/floppy'}, 'overrides': [
    {'predicate': {f'{M}:variant': 0.5}, 'model': f'{M}:item/floppy_os'},
    {'predicate': {f'{M}:variant': 1.0}, 'model': f'{M}:item/floppy_dyed'}]})
w(f'{A}/models/item/floppy_os.json', {'parent': 'item/generated', 'textures': {'layer0': f'{M}:item/os_floppy'}})
w(f'{A}/models/item/floppy_dyed.json', {'parent': 'item/generated', 'textures': {'layer0': f'{M}:item/floppy', 'layer1': f'{M}:item/floppy_tint'}})

# Sounds.
w(f'{A}/sounds.json', {
    'hdd_access': {'sounds': [f'{M}:hdd_access{i}' for i in range(1, 8)], 'subtitle': 'subtitles.computah.hdd_access'},
    'floppy_access': {'sounds': [f'{M}:floppy_access{i}' for i in range(1, 7)], 'subtitle': 'subtitles.computah.floppy_access'},
    'floppy_insert': {'sounds': [f'{M}:floppy_insert'], 'subtitle': 'subtitles.computah.floppy_insert'},
    'beep': {'sounds': [{'name': f'{M}:floppy_access1', 'stream': True}], 'subtitle': 'subtitles.computah.beep'},
    'floppy_eject': {'sounds': [f'{M}:floppy_eject'], 'subtitle': 'subtitles.computah.floppy_eject'},
    'computer_running': {'sounds': [f'{M}:computer_running']},
})

# Loot tables and tags.
blocks = ['case1', 'case2', 'case3', 'screen1', 'screen2', 'screen3', 'keyboard', 'disk_drive', 'cable', 'relay', 'rack', 'adapter', 'redstone_io']
for b in blocks:
    w(f'{D}/{M}/loot_table/blocks/{b}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{M}:{b}'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
w(f'{D}/minecraft/tags/block/mineable/pickaxe.json', {'values': [f'{M}:{b}' for b in blocks]})

# Recipes: cheap, CraftingTable-only, vanilla materials.
def shaped(name, pattern, key, count=1):
    w(f'{D}/{M}/recipe/{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'redstone', 'pattern': pattern,
        'key': {k: ({'tag': v[1:]} if v.startswith('#') else {'item': v}) for k, v in key.items()},
        'result': {'id': f'{M}:{name}', 'count': count}})
def shapeless(name, ingredients, count=1):
    w(f'{D}/{M}/recipe/{name}.json', {'type': 'minecraft:crafting_shapeless', 'category': 'redstone',
        'ingredients': [({'tag': v[1:]} if v.startswith('#') else {'item': v}) for v in ingredients], 'result': {'id': f'{M}:{name}', 'count': count}})

shaped('case1', ['SSS', 'SRS', 'SGS'], {'S': 'minecraft:stone', 'R': 'minecraft:redstone', 'G': 'minecraft:glass_pane'})
shaped('case2', ['SSS', 'SRS', 'SGS'], {'S': 'minecraft:stone', 'R': 'minecraft:gold_ingot', 'G': 'minecraft:glass_pane'})
shaped('case3', ['SSS', 'SRS', 'SGS'], {'S': 'minecraft:stone', 'R': 'minecraft:diamond', 'G': 'minecraft:glass_pane'})
shaped('screen1', ['SSS', 'SGS', 'SRS'], {'S': 'minecraft:stone', 'G': 'minecraft:glass_pane', 'R': 'minecraft:redstone'})
shaped('screen2', ['SSS', 'SGS', 'SRS'], {'S': 'minecraft:stone', 'G': 'minecraft:glass_pane', 'R': 'minecraft:gold_ingot'})
shaped('screen3', ['SSS', 'SGS', 'SRS'], {'S': 'minecraft:stone', 'G': 'minecraft:glass_pane', 'R': 'minecraft:diamond'})
shaped('keyboard', ['BBB', 'SSS'], {'B': 'minecraft:stone_button', 'S': 'minecraft:stone'})
shaped('disk_drive', ['SSS', 'SRS', 'SRS'], {'S': 'minecraft:stone', 'R': 'minecraft:redstone'})
shaped('cable', ['NRN'], {'N': 'minecraft:iron_nugget', 'R': 'minecraft:redstone'}, 8)
shaped('relay', ['NCN', 'CRC', 'NCN'], {'N': 'minecraft:iron_nugget', 'C': f'{M}:cable', 'R': 'minecraft:redstone'})
shaped('rack', ['III', 'ICI', 'III'], {'I': 'minecraft:iron_ingot', 'C': f'{M}:case1'})
shaped('adapter', ['ICI', 'CHC', 'ICI'], {'I': 'minecraft:iron_ingot', 'C': f'{M}:cable', 'H': 'minecraft:hopper'})
shaped('redstone_io', ['IRI', 'RCR', 'IRI'], {'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone', 'C': f'{M}:cable'})
shaped('cpu1', ['IRI', 'RNR', 'IRI'], {'I': 'minecraft:iron_nugget', 'R': 'minecraft:redstone', 'N': 'minecraft:gold_nugget'})
shaped('cpu2', ['IRI', 'RNR', 'IRI'], {'I': 'minecraft:iron_nugget', 'R': 'minecraft:redstone', 'N': 'minecraft:gold_ingot'})
shaped('cpu3', ['IRI', 'RNR', 'IRI'], {'I': 'minecraft:iron_nugget', 'R': 'minecraft:redstone', 'N': 'minecraft:diamond'})
shaped('ram1', ['RRR', 'III'], {'R': 'minecraft:redstone', 'I': 'minecraft:iron_nugget'})
shaped('ram2', ['RGR', 'III'], {'R': 'minecraft:redstone', 'G': 'minecraft:gold_nugget', 'I': 'minecraft:iron_nugget'})
shaped('ram3', ['RGR', 'III'], {'R': 'minecraft:redstone', 'G': 'minecraft:gold_ingot', 'I': 'minecraft:iron_nugget'})
shaped('ram4', ['RDR', 'III'], {'R': 'minecraft:redstone', 'D': 'minecraft:diamond', 'I': 'minecraft:iron_nugget'})
shaped('hdd1', ['III', 'RSR', 'III'], {'I': 'minecraft:iron_nugget', 'R': 'minecraft:redstone', 'S': 'minecraft:smooth_stone'})
shaped('hdd2', ['III', 'RSR', 'IGI'], {'I': 'minecraft:iron_nugget', 'R': 'minecraft:redstone', 'S': 'minecraft:smooth_stone', 'G': 'minecraft:gold_ingot'})
shaped('hdd3', ['III', 'RSR', 'IDI'], {'I': 'minecraft:iron_nugget', 'R': 'minecraft:redstone', 'S': 'minecraft:smooth_stone', 'D': 'minecraft:diamond'})
shapeless('floppy', ['minecraft:paper', 'minecraft:redstone'])
def data_recipe(file, kind, ingredients, result):
    w(f'{D}/{M}/recipe/{file}.json', {'type': f'{M}:{kind}', 'category': 'redstone',
        'ingredients': [({'tag': v[1:]} if v.startswith('#') else {'item': v}) for v in ingredients], 'result': result})
data_recipe('os_floppy', 'os_floppy', [f'{M}:floppy', 'minecraft:book'], {'id': f'{M}:floppy', 'count': 1})
data_recipe('bios_eeprom', 'bios_eeprom', [f'{M}:eeprom', 'minecraft:book'], {'id': f'{M}:eeprom', 'count': 1})
DYES = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple',
        'blue', 'brown', 'green', 'red', 'black']
for c in DYES:
    data_recipe(f'floppy_{c}', 'floppy_dye', [f'{M}:floppy', f'#c:dyes/{c}'],
                {'id': f'{M}:floppy', 'count': 1, 'components': {f'{M}:floppy_color': c}})
shaped('eeprom', ['NRN', 'NPN'], {'N': 'minecraft:gold_nugget', 'R': 'minecraft:redstone', 'P': 'minecraft:paper'})
shaped('gpu1', ['RIR', 'PPP'], {'R': 'minecraft:redstone', 'I': 'minecraft:iron_ingot', 'P': 'minecraft:paper'})
shaped('gpu2', ['RGR', 'PPP'], {'R': 'minecraft:redstone', 'G': 'minecraft:gold_ingot', 'P': 'minecraft:paper'})
shaped('gpu3', ['RDR', 'PPP'], {'R': 'minecraft:redstone', 'D': 'minecraft:diamond', 'P': 'minecraft:paper'})
shaped('network_card', ['RSR', 'PPP'], {'R': 'minecraft:redstone', 'S': 'minecraft:string', 'P': 'minecraft:paper'})
shaped('wireless_card1', ['RER', 'PPP'], {'R': 'minecraft:redstone', 'E': 'minecraft:ender_pearl', 'P': 'minecraft:paper'})
shaped('wireless_card2', ['RER', 'PPP'], {'R': 'minecraft:redstone', 'E': 'minecraft:ender_eye', 'P': 'minecraft:paper'})
shaped('redstone_card', ['RTR', 'PPP'], {'R': 'minecraft:redstone', 'T': 'minecraft:redstone_torch', 'P': 'minecraft:paper'})
shaped('server', ['III', 'IRI', 'IGI'], {'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone', 'G': 'minecraft:glass_pane'})
shaped('linker', [' E', 'I '], {'E': 'minecraft:ender_pearl', 'I': 'minecraft:iron_ingot'})
print('ok')
