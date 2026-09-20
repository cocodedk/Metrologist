"""Create a fresh local AVD configuration without copying another emulator's live disk."""
import os
from pathlib import Path


def properties(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines() if '=' in line)


def from_template(sdk, destination, name, template):
    source_home = Path(os.environ.get('ANDROID_AVD_HOME', str(Path.home() / '.android/avd')))
    source = source_home / (template + '.avd')
    pointer = source_home / (template + '.ini')
    if pointer.exists():
        source = Path(properties(pointer).get('path', str(source)))
    original = properties(source / 'config.ini')
    image = (sdk / original['image.sysdir.1']).resolve()
    if not image.is_dir():
        raise ValueError(f'Template system image is missing: {image}')
    # Hardware settings and tags describe the virtual device, not its user data.
    config = {key: value for key, value in original.items()
              if key.startswith(('hw.', 'tag.', 'runtime.network.'))
              and not any(word in key.lower() for word in ('path', 'file'))}
    config.update({
        'AvdId': name, 'avd.ini.displayname': name, 'avd.ini.encoding': 'UTF-8',
        'image.sysdir.1': str(image) + '/', 'target': original['target'],
        'PlayStore.enabled': original.get('PlayStore.enabled', 'false'),
        'disk.dataPartition.size': '6G', 'hw.sdCard': 'no',
        'hw.camera.back': 'virtualscene', 'hw.camera.front': 'emulated',
        'fastboot.forceColdBoot': 'yes', 'fastboot.forceFastBoot': 'no',
    })
    avd = destination / (name + '.avd')
    avd.mkdir(parents=True, exist_ok=False)
    (avd / 'config.ini').write_text(''.join(f'{key}={value}\n' for key, value in sorted(config.items())))
    (destination / (name + '.ini')).write_text(
        f'avd.ini.encoding=UTF-8\npath={avd}\ntarget={original["target"]}\n')
    return avd
