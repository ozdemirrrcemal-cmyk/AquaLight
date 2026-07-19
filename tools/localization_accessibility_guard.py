#!/usr/bin/env python3
"""Commercial localization and accessibility gate for AquaLight Stage 11."""
from __future__ import annotations
import collections, json, re, sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'app/src/main/res'
UI=ROOT/'app/src/main/java/com/aqua/aqualight/ui'
ANDROID='{http://schemas.android.com/apk/res/android}'
APP='{http://schemas.android.com/apk/res-auto}'
REGISTRY=ROOT/'app/src/main/java/com/aqua/aqualight/localization/SupportedLocaleRegistry.kt'
CONFIG=RES/'xml/locales_config.xml'
CONTRAST=ROOT/'tools/accessibility_contrast_contract.json'
STAGING={'tr','de','fr','ru','zh'}
UNSUPPORTED={f'language_{lang}{suffix}' for lang in ('turkish','german','french','russian','chinese') for suffix in ('','_flag_desc','_row_desc','_radio_desc')}
RUNTIME_HEADER={("layout_aqua_header.xml","@+id/btnActionOne"),("layout_aqua_header.xml","@+id/btnActionTwo"),("layout_aqua_header.xml","@+id/btnActionThree"),("layout_aqua_header.xml","@+id/btnFilledIconAction")}
PLACEHOLDER=re.compile(r'(?<!%)%(?!%)(?:(?P<index>\d+)\$)?[-+#, 0(<]*\d*(?:\.\d+)?(?P<type>[a-zA-Z])')
DYNAMIC_VIEW_BLOCK=re.compile(r'(?P<type>[A-Za-z0-9_.]+)\([^)]*\)\.apply\s*\{(?P<body>.*?)(?=\n\s*\})',re.S)
DYNAMIC_FIXED_LAYOUT=re.compile(r'LayoutParams\(\s*resources\.getDimensionPixelOffset\(R\.dimen\.aqua_size_(?P<width>\d+)\)\s*,\s*resources\.getDimensionPixelOffset\(R\.dimen\.aqua_size_(?P<height>\d+)\)',re.S)

class Failure(RuntimeError):pass
def fail(msg):raise Failure(msg)
def files(d):return sorted(p for p in d.glob('*.xml') if p.is_file())
def txt(e):return ''.join(e.itertext())

def placeholders(value):
    out=collections.Counter();implicit=0
    for m in PLACEHOLDER.finditer(value):
        kind=m.group('type').lower()
        if kind=='n':continue
        index=m.group('index')
        if index is None:implicit+=1;index=f'implicit:{implicit}'
        out[(index,kind)]+=1
    return out

def resources(directory):
    out={}
    for p in files(directory):
        root=ET.parse(p).getroot()
        if root.tag!='resources':continue
        for child in root:
            name=child.attrib.get('name')
            if not name or child.attrib.get('translatable','true').lower()=='false':continue
            if child.tag=='string':out[f'string:{name}']=placeholders(txt(child))
            elif child.tag=='plurals':
                for item in child.findall('item'):out[f"plurals:{name}:{item.attrib.get('quantity','')}"]=placeholders(txt(item))
    return out

def enabled_locales():
    source=REGISTRY.read_text(encoding='utf-8')
    default=re.search(r'const\s+val\s+DEFAULT_LANGUAGE_TAG\s*=\s*"([^"]+)"',source)
    if not default:fail('SupportedLocaleRegistry default missing')
    tags={default.group(1),*re.findall(r'languageTag\s*=\s*"([^"]+)"',source)}
    root=ET.parse(CONFIG).getroot();configured={e.attrib[f'{ANDROID}name'] for e in root.findall('locale')}
    if tags!=configured:fail(f'Registry/config mismatch: {sorted(tags)} vs {sorted(configured)}')
    return tags

def locale_dirs():
    out={}
    for d in RES.glob('values-*'):
        m=re.fullmatch(r'values-([a-z]{2,3})(?:-r[A-Z]{2})?',d.name)
        if d.is_dir() and m:out[m.group(1)]=d
    return out

def check_locales(enabled):
    base=resources(RES/'values');dirs=locale_dirs()
    missing=STAGING-dirs.keys()
    if missing:fail(f'Missing staging directories: {sorted(missing)}')
    language_layout=(RES/'layout/fragment_language_settings.xml').read_text(encoding='utf-8')
    if 'translationStagingResources' in language_layout:fail('Hidden translation staging view is forbidden')
    base_names={key.split(':',1)[1] for key in base if key.startswith('string:')}
    leaked=UNSUPPORTED&base_names
    if leaked:fail(f'Unsupported language resources packaged: {sorted(leaked)}')
    assets=[p.relative_to(ROOT).as_posix() for pattern in ('flag_tr.*','flag_de.*','flag_fr.*','flag_ru.*','flag_cn.*') for p in RES.glob('drawable*/'+pattern)]
    if assets:fail(f'Unsupported flag assets packaged: {assets}')
    for lang,d in dirs.items():
        localized=resources(d)
        unknown=localized.keys()-base.keys()
        if unknown:fail(f'{d.name} has unknown keys: {sorted(unknown)[:10]}')
        for key,tokens in localized.items():
            if tokens!=base[key]:fail(f'Placeholder mismatch {d.name}/{key}: {base[key]} != {tokens}')
        if lang in enabled and lang!='en':
            missing_keys=base.keys()-localized.keys()
            if missing_keys:fail(f'Enabled locale {lang} incomplete: {len(missing_keys)} missing')
        elif lang in STAGING and localized:
            fail(f'Disabled locale {lang} must remain empty until complete catalog lands atomically')

def dimen_catalog():
    values={};aliases={}
    for p in files(RES/'values'):
        for item in ET.parse(p).getroot().findall('dimen'):
            name=item.attrib.get('name');raw=txt(item).strip()
            if not name:continue
            direct=re.fullmatch(r'([0-9]+(?:\.[0-9]+)?)dp',raw);alias=re.fullmatch(r'@dimen/(\w+)',raw)
            if direct:values[name]=float(direct.group(1))
            elif alias:aliases[name]=alias.group(1)
    changed=True
    while changed:
        changed=False
        for name,target in list(aliases.items()):
            if target in values:values[name]=values[target];del aliases[name];changed=True
    return values

def dp(raw,catalog):
    if not raw:return None
    m=re.fullmatch(r'([0-9]+(?:\.[0-9]+)?)dp',raw.strip())
    if m:return float(m.group(1))
    m=re.fullmatch(r'@dimen/(\w+)',raw.strip())
    return catalog.get(m.group(1)) if m else None

def check_header_runtime():
    source=(ROOT/'app/src/main/java/com/aqua/aqualight/ui/common/header/AquaHeaderBindingExt.kt').read_text(encoding='utf-8')
    needed=('button.contentDescription =','action.contentDescription','btnFilledIconAction.contentDescription =','filledIconAction.contentDescription')
    missing=[x for x in needed if x not in source]
    if missing:fail(f'Header runtime descriptions missing: {missing}')

def check_xml_controls():
    check_header_runtime();dims=dimen_catalog();errors=[]
    interactive_classes={'Button','ImageButton','CheckBox','RadioButton','Switch','SwitchMaterial','MaterialButton','MaterialRadioButton','MaterialCheckBox'}
    for p in sorted((RES/'layout').glob('*.xml')):
        root=ET.parse(p).getroot()
        for view in root.iter():
            kind=view.tag.rsplit('.',1)[-1]
            clickable=view.attrib.get(f'{ANDROID}clickable')=='true'
            focusable=view.attrib.get(f'{ANDROID}focusable')=='true'
            interactive=clickable or focusable or kind in interactive_classes
            has_icon=any(k in view.attrib for k in (f'{ANDROID}src',f'{APP}srcCompat',f'{APP}icon'))
            has_text=bool(view.attrib.get(f'{ANDROID}text','').strip())
            icon_only=kind=='ImageButton' or (kind=='ImageView' and interactive) or (interactive and has_icon and not has_text)
            vid=view.attrib.get(f'{ANDROID}id',kind);where=f'{p.relative_to(ROOT)}:{vid}'
            ignored=view.attrib.get(f'{ANDROID}importantForAccessibility') in {'no','noHideDescendants'}
            desc=view.attrib.get(f'{ANDROID}contentDescription','').strip()
            if icon_only and (p.name,vid) not in RUNTIME_HEADER and not ignored and (not desc or desc=='@null'):
                errors.append(where+' has no contentDescription')
            if interactive:
                width=dp(view.attrib.get(f'{ANDROID}layout_width'),dims);height=dp(view.attrib.get(f'{ANDROID}layout_height'),dims)
                minw=dp(view.attrib.get(f'{ANDROID}minWidth'),dims) or 0;minh=dp(view.attrib.get(f'{ANDROID}minHeight'),dims) or 0
                if width is not None and width>0 and max(width,minw)<48:errors.append(where+' fixed width below 48dp')
                if height is not None and height>0 and max(height,minh)<48:errors.append(where+' fixed height below 48dp')
    if errors:fail('XML accessibility violations:\n- '+'\n- '.join(errors[:100]))

def check_dynamic_controls():
    errors=[]
    for p in sorted(UI.rglob('*.kt')):
        source=p.read_text(encoding='utf-8')
        for match in DYNAMIC_VIEW_BLOCK.finditer(source):
            view_type=match.group('type').rsplit('.',1)[-1]
            body=match.group('body')
            interactive='setOnClickListener' in body or 'isClickable = true' in body
            if not interactive:continue
            if view_type in {'ImageView','ImageButton'} and 'contentDescription' not in body and 'importantForAccessibility' not in body:
                errors.append(f'{p.relative_to(ROOT)} clickable programmatic image lacks description')
            for size in DYNAMIC_FIXED_LAYOUT.finditer(body):
                width=int(size.group('width'));height=int(size.group('height'))
                if width<48 or height<48:
                    errors.append(f'{p.relative_to(ROOT)} programmatic clickable target is {width}x{height}dp')
    if errors:fail('Dynamic accessibility violations:\n- '+'\n- '.join(sorted(set(errors))))

def check_locale_boundary():
    errors=[]
    for p in sorted(UI.rglob('*.kt')):
        source=p.read_text(encoding='utf-8')
        for token in ('SimpleDateFormat','DecimalFormat','Locale.US'):
            if token in source:errors.append(f'{p.relative_to(ROOT)} uses {token}')
    if 'setupDateLocale' in (UI/'tabs/aquarium/common/AquariumDatePolicy.kt').read_text(encoding='utf-8'):
        errors.append('AquariumDatePolicy.setupDateLocale remains')
    if errors:fail('Locale boundary violations:\n- '+'\n- '.join(errors))

def color_catalog(directory,base=None):
    out=dict(base or {})
    for p in files(directory):
        for item in ET.parse(p).getroot().findall('color'):
            name=item.attrib.get('name');value=txt(item).strip()
            if name and value:out[name]=value
    return out

def resolve(name,catalog,stack=()):
    if name in stack:fail('Circular color reference: '+' -> '.join(stack+(name,)))
    raw=catalog.get(name)
    if raw is None:fail(f'Missing color: {name}')
    if raw.startswith('@color/'):return resolve(raw[7:],catalog,stack+(name,))
    if not re.fullmatch(r'#[0-9a-fA-F]{6}|#[0-9a-fA-F]{8}',raw):fail(f'Unsupported color {name}={raw}')
    if len(raw)==9:
        if raw[1:3].lower()!='ff':fail(f'Contrast pair must be opaque: {name}={raw}')
        raw='#'+raw[3:]
    return raw

def luminance(color):
    values=[int(color[i:i+2],16)/255 for i in (1,3,5)]
    linear=[v/12.92 if v<=.03928 else ((v+.055)/1.055)**2.4 for v in values]
    return .2126*linear[0]+.7152*linear[1]+.0722*linear[2]
def ratio(a,b):
    x,y=luminance(a),luminance(b);return (max(x,y)+.05)/(min(x,y)+.05)

def check_contrast():
    contract=json.loads(CONTRAST.read_text(encoding='utf-8'));pairs=contract['pairs'];required=set(contract['required_foregrounds'])
    covered={p['foreground'] for p in pairs}
    if required-covered:fail(f'Uncovered semantic foregrounds: {sorted(required-covered)}')
    labels=[p['label'] for p in pairs]
    if len(labels)!=len(set(labels)):fail('Duplicate contrast labels')
    base=color_catalog(RES/'values');themes={'base':base,'night':color_catalog(RES/'values-night',base)};errors=[]
    for theme,catalog in themes.items():
        for pair in pairs:
            actual=ratio(resolve(pair['foreground'],catalog),resolve(pair['background'],catalog));minimum=float(pair['minimum'])
            if actual+1e-9<minimum:errors.append(f"{theme}/{pair['label']}: {actual:.2f}:1 < {minimum:.1f}:1")
    if errors:fail('WCAG contrast violations:\n- '+'\n- '.join(errors))

def check_status():
    for p in (UI/'common/devicecard/DeviceCompactCardBinder.kt',UI/'tabs/settings/device/DeviceStatusAdapter.kt'):
        source=p.read_text(encoding='utf-8');missing=[x for x in ('R.string.device_online','R.string.device_offline','contentDescription') if x not in source]
        if missing:fail(f'{p.relative_to(ROOT)} dynamic status incomplete: {missing}')

def main():
    try:
        enabled=enabled_locales();check_locales(enabled);check_locale_boundary();check_xml_controls();check_dynamic_controls();check_status();check_contrast()
    except (Failure,ET.ParseError,json.JSONDecodeError) as e:
        print('LOCALIZATION_ACCESSIBILITY_GUARD_FAILED:',e,file=sys.stderr);return 1
    print('LOCALIZATION_ACCESSIBILITY_GUARD_PASS',f"enabled_locales={','.join(sorted(enabled))}",f"staging_locales={','.join(sorted(STAGING))}");return 0
if __name__=='__main__':raise SystemExit(main())
