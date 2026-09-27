#!/usr/bin/env python3
"""Keep the water engine pure and its UI behind the application boundary."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
BASE = Path('app/src/main/java/com/aqua/aqualight')
RULES = (
    ('application/aquarium/health', ('android.', 'androidx.', 'com.google.',
                                   'com.aqua.aqualight.data.', 'com.aqua.aqualight.ui.')),
    ('application/aquarium/catalog', ('android.', 'androidx.', 'com.google.',
                                     'com.aqua.aqualight.data.', 'com.aqua.aqualight.ui.')),
    ('data/aquarium/health', ('com.aqua.aqualight.ui.',)),
    ('data/aquarium/catalog/plant', ('com.aqua.aqualight.ui.',)),
    ('ui/tabs/aquarium/detail/health', ('com.aqua.aqualight.data.', 'com.google.firebase.')),
)


def violations(root: Path) -> list[str]:
    errors = []
    for folder, forbidden in RULES:
        for path in sorted((root / BASE / folder).rglob('*.kt')):
            text = path.read_text(encoding='utf-8')
            for imported in re.findall(r'^import\s+([\w.]+)', text, re.MULTILINE):
                if imported.startswith(forbidden):
                    errors.append(f'{path.relative_to(root)} imports {imported}')
    health = root / BASE / 'application/aquarium/health'
    engines = ('water', 'observation', 'algae', 'plant', 'livestock')
    paths = (path for engine in engines for path in (health / engine).rglob('*.kt'))
    for path in sorted(paths):
        text = path.read_text(encoding='utf-8')
        for token in ('System.currentTimeMillis(', 'Clock.system', 'Firebase', 'Dispatchers.',
                      'DataStore', 'JSONObject(', 'JsonParser.', 'java.io.', 'java.net.'):
            if token in text:
                errors.append(f'{path.relative_to(root)} performs ambient engine work: {token}')
    return errors


def main() -> int:
    errors = violations(ROOT)
    if errors:
        print('Water Analysis architecture guard failed:')
        print('\n'.join(f'- {error}' for error in errors))
        return 1
    print('Water Analysis architecture guard passed.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
