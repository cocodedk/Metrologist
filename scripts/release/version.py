"""Read the checked-in release version and refuse malformed or existing tags."""
from pathlib import Path
import re
import subprocess


def source_version(root):
    values = {}
    for line in (root / 'gradle.properties').read_text().splitlines():
        if line.startswith(('VERSION_NAME=', 'VERSION_CODE=')):
            key, value = line.split('=', 1)
            if key in values:
                raise ValueError(f'Duplicate {key} in gradle.properties')
            values[key] = value
    name = values.get('VERSION_NAME', '')
    code = values.get('VERSION_CODE', '')
    component = r'(0|[1-9][0-9]*)'
    if not re.fullmatch(rf'{component}\.{component}\.{component}', name):
        raise ValueError('VERSION_NAME must be a literal major.minor.patch version')
    if not re.fullmatch(r'[1-9][0-9]*', code):
        raise ValueError('VERSION_CODE must be a positive integer')
    major, minor, patch = map(int, name.split('.'))
    expected = major * 1_000_000 + minor * 1_000 + patch
    if minor > 999 or patch > 999 or int(code) != expected or int(code) > 2_100_000_000:
        raise ValueError('VERSION_CODE must equal major*1000000 + minor*1000 + patch within Android limits')
    tag = f'v{name}'
    result = subprocess.run(['git', 'show-ref', '--verify', '--quiet', f'refs/tags/{tag}'], cwd=root)
    if result.returncode == 0:
        raise ValueError(f'{tag} already exists; bump both source version values first')
    if result.returncode != 1:
        raise ValueError('Could not check existing Git tags')
    return name, code, tag


if __name__ == '__main__':
    try:
        name, code, tag = source_version(Path(__file__).resolve().parents[2])
        print(f'version={name}\ncode={code}\ntag={tag}')
    except (OSError, ValueError) as error:
        raise SystemExit(str(error))
