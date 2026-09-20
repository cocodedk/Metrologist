"""Verify OpenCV keep rules and native bytes in an unsigned optimized APK."""
import argparse
import hashlib
import json
from pathlib import Path
import tomllib
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def verify(apk, mapping, cache):
    root = Path(__file__).resolve().parents[2]
    versions = tomllib.loads((root / 'gradle/libs.versions.toml').read_text())['versions']
    artifacts = [('org.opencv', 'opencv', versions['opencv']),
                 ('androidx.camera', 'camera-core', versions['camerax'])]
    originals = {}
    for group, name, version in artifacts:
        aars = list((cache / group / name / version).glob('*/*.aar'))
        if len(aars) != 1:
            raise ValueError(f'Expected one cached {group}:{name}:{version} AAR')
        with zipfile.ZipFile(aars[0]) as archive:
            for entry in archive.namelist():
                if entry.startswith('jni/') and entry.endswith('.so'):
                    originals[entry.replace('jni/', 'lib/', 1)] = (sha(archive.read(entry)), f'{group}:{name}:{version}')
    # Transitive AndroidX libraries may also carry native code. For these, find
    # an exact byte match and record the source coordinate rather than guessing
    # which cached version Gradle resolved. OpenCV/CameraX stay version-pinned above.
    with zipfile.ZipFile(apk) as archive:
        extra = {entry: sha(archive.read(entry)) for entry in archive.namelist()
                 if entry.startswith('lib/') and entry.endswith('.so') and entry not in originals}
    for aar in sorted(cache.glob('androidx.*/*/*/*/*.aar')):
        if not extra:
            break
        with zipfile.ZipFile(aar) as archive:
            for entry in archive.namelist():
                target = entry.replace('jni/', 'lib/', 1)
                if target in extra and sha(archive.read(entry)) == extra[target]:
                    coordinate = ':'.join(aar.relative_to(cache).parts[:3])
                    originals[target] = (extra.pop(target), coordinate)
    checked = []
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.namelist():
            if entry.startswith('lib/') and entry.endswith('.so'):
                digest = sha(archive.read(entry))
                if entry not in originals or digest != originals[entry][0]:
                    raise ValueError(f'Native library missing from source AARs or changed: {entry}')
                checked.append(dict(entry=entry, sha256=digest, source=originals[entry][1]))
    if not checked:
        raise ValueError('No native libraries found')
    text = (mapping / 'mapping.txt').read_text()
    classes = ['org.opencv.core.Mat', 'org.opencv.core.Core', 'org.opencv.imgproc.Imgproc',
               'org.opencv.android.OpenCVLoader', 'org.opencv.android.Utils']
    for name in classes:
        if f'{name} -> {name}:' not in text:
            raise ValueError(f'JNI class removed or renamed: {name}')
    if (mapping / 'missing_rules.txt').exists():
        raise ValueError('R8 generated missing_rules.txt; inspect before releasing')
    return dict(apk_sha256=sha(apk.read_bytes()), native_libraries=checked,
                unrenamed_jni_classes=classes, missing_rules=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('mapping', type=Path)
    parser.add_argument('--cache', type=Path, default=Path.home() / '.gradle/caches/modules-2/files-2.1')
    args = parser.parse_args()
    print(json.dumps(verify(args.apk, args.mapping, args.cache), indent=2))
