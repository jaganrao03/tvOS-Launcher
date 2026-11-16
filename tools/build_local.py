#!/usr/bin/env python3
"""Build local source with installed SDK tools; never fetch, install, or clean up.

Every build uses a new staging directory. No dependency on git remotes/releases.
The signing key is read from its original location and never copied or generated.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-root', type=Path, default=Path(os.environ.get('TMPDIR', str(Path.home() / '.cache'))) / 'tvos-local-builds')
    parser.add_argument('--version-code', required=True, type=int)
    parser.add_argument('--version-name', required=True)
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    if not args.output_root.is_absolute() or args.version_code < 1 or not args.version_name.strip():
        parser.error('Require absolute output root, positive version code, nonempty version name')
    sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '/opt/android-sdk'))).resolve()
    bt = sdk / 'build-tools/34.0.0'
    jar = sdk / 'platforms/android-34/android.jar'
    key = Path(os.environ.get('RELEASE_KEYSTORE', str(ROOT / 'debug.keystore'))).resolve()
    release = bool(os.environ.get('RELEASE_KEYSTORE'))
    required = [jar, key] + [bt / n for n in ('aapt2', 'd8', 'zipalign', 'apksigner')]
    for path in required:
        if not path.is_file():
            parser.error(f'Missing local prerequisite: {path}; no downloads or key generation performed')
    for name in ('javac', 'java'):
        if not shutil.which(name):
            parser.error(f'Missing {name}; provide JDK 17+ on PATH')
    if release and not os.environ.get('RELEASE_KEYSTORE_PASS'):
        parser.error('RELEASE_KEYSTORE_PASS must be set for release signing')
    plan: dict[str, object] = dict(source=str(ROOT), output_root=str(args.output_root.resolve()),
                version_code=args.version_code, version_name=args.version_name,
                network='none', device_actions='none',
                cleanup='none; fresh directory each build', signing_key=str(key))
    if args.dry_run:
        print(json.dumps(plan, indent=2))
        return
    args.output_root.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix='tvos-', dir=args.output_root)).resolve()
    assert stage.is_relative_to(args.output_root.resolve()) and stage != args.output_root.resolve()
    src = stage / 'source'
    for name in ('src', 'res', 'assets'):
        shutil.copytree(ROOT / name, src / name)
    shutil.copy2(ROOT / 'AndroidManifest.xml', src / 'AndroidManifest.xml')
    hashes = {str(p.relative_to(src)): hashlib.sha256(p.read_bytes()).hexdigest()
              for p in sorted(src.rglob('*')) if p.is_file()}
    out = stage / 'build'
    out.mkdir()
    for name in ('gen', 'classes', 'dex'):
        (out / name).mkdir()
    def run(*cmd):
        subprocess.run([str(x) for x in cmd], check=True, cwd=stage)
    run(bt / 'aapt2', 'compile', '--dir', src / 'res', '-o', out / 'res.zip')
    run(bt / 'aapt2', 'link', '-o', out / 'unsigned.apk', '-I', jar,
        '--manifest', src / 'AndroidManifest.xml', '-A', src / 'assets',
        '--java', out / 'gen', '--min-sdk-version', '24', '--target-sdk-version', '34',
        '--version-code', args.version_code, '--version-name', args.version_name, out / 'res.zip')
    javafiles = sorted((src / 'src').rglob('*.java')) + sorted((out / 'gen').rglob('*.java'))
    run('javac', '-encoding', 'UTF-8', '--release', '11', '-classpath', jar, '-d', out / 'classes', *javafiles)
    run(bt / 'd8', '--release', '--min-api', '24', '--lib', jar, '--output', out / 'dex',
        *sorted((out / 'classes').rglob('*.class')))
    with zipfile.ZipFile(out / 'unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as archive:
        for dex in sorted((out / 'dex').glob('*.dex')):
            archive.write(dex, dex.name)
    run(bt / 'zipalign', '4', out / 'unsigned.apk', out / 'aligned.apk')
    sign = [bt / 'apksigner', 'sign', '--ks', key]
    if release:
        sign += ['--ks-pass', 'env:RELEASE_KEYSTORE_PASS', '--ks-key-alias', os.environ.get('RELEASE_KEY_ALIAS', 'tvos-release')]
    else:
        sign += ['--ks-pass', 'pass:android', '--key-pass', 'pass:android']
    apk = out / 'tvos-home.apk'
    run(*sign, '--out', apk, out / 'aligned.apk')
    run(bt / 'apksigner', 'verify', '--verbose', '--print-certs', apk)
    plan.update(apk=str(apk), apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), source_sha256=hashes)
    (stage / 'build-manifest.json').write_text(json.dumps(plan, indent=2) + '\n')
    print(f'Built {apk}', flush=True)
    print(f'Manifest {stage / "build-manifest.json"}', flush=True)


if __name__ == '__main__':
    main()
