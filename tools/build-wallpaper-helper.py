#!/usr/bin/env python3
"""使用 Android SDK 和 Java 17 构建外屏壁纸辅助工具，不操作设备。"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    args = parser.parse_args()
    if not args.sdk:
        parser.error('请通过 ANDROID_HOME 或 --sdk 指定 Android SDK。')
    sdk = Path(args.sdk).expanduser().resolve()
    android = sdk / 'platforms/android-37.0/android.jar'
    if not android.is_file():
        android = sdk / 'platforms/android-37/android.jar'
    d8 = sdk / 'build-tools/36.0.0' / ('d8.bat' if os.name == 'nt' else 'd8')
    tools = Path(__file__).resolve().parent
    suffix = '.exe' if os.name == 'nt' else ''
    java_home = os.environ.get('JAVA_HOME')
    javac = str(Path(java_home) / 'bin' / ('javac' + suffix)) if java_home else shutil.which('javac')
    if not javac or not android.is_file() or not d8.is_file():
        parser.error('需要 Java 17、Android SDK platform 37 和 build-tools 36.0.0。')
    build = tools / 'build'
    build.mkdir(exist_ok=True)
    output = tools / 'cover-wallpaper-setup.jar'
    with tempfile.TemporaryDirectory(prefix='wallpaper-', dir=build) as temporary:
        temp = Path(temporary)
        classes = temp / 'classes'
        classes.mkdir()
        subprocess.run([javac, '--release', '17', '-encoding', 'UTF-8', '-cp', str(android),
                        '-d', str(classes), str(tools / 'CoverWallpaperSetup.java')], check=True)
        class_jar = temp / 'classes.jar'
        with zipfile.ZipFile(class_jar, 'w') as archive:
            for file in sorted(classes.rglob('*.class')):
                archive.write(file, file.relative_to(classes).as_posix())
        dex_jar = temp / 'cover-wallpaper-setup.jar'
        subprocess.run([str(d8), '--release', '--min-api', '33', '--lib', str(android),
                        '--output', str(dex_jar), str(class_jar)], check=True)
        with zipfile.ZipFile(dex_jar, 'a') as archive:
            if 'classes.dex' not in archive.namelist():
                raise RuntimeError('D8 输出中缺少 classes.dex。')
            archive.write(tools.parent / 'LICENSE', 'META-INF/LICENSE')
        shutil.copy2(dex_jar, output)
    print('已生成：', output)


if __name__ == '__main__':
    main()
