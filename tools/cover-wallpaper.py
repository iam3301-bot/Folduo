#!/usr/bin/env python3
"""仅用于已验证 Fold7 的外屏主屏幕壁纸。省略操作时只检查状态。"""
import argparse
import shlex
import shutil
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['status', 'apply', 'restore-stock'], nargs='?', default='status')
    parser.add_argument('--adb', default=shutil.which('adb'))
    parser.add_argument('--serial', help='多台设备连接时指定目标设备')
    args = parser.parse_args()
    if not args.adb:
        parser.error('请使用 --adb 指定 Android ADB 的路径。')
    if not args.serial:
        result = subprocess.run([args.adb, 'devices'], check=True, capture_output=True, text=True, timeout=15)
        devices = [line.split()[0] for line in result.stdout.splitlines()[1:]
                   if len(line.split()) == 2 and line.split()[1] == 'device']
        if len(devices) != 1:
            parser.error('请只连接一台设备，或使用 --serial 指定。')
        args.serial = devices[0]
    base = [args.adb, '-s', args.serial]
    jar = Path(__file__).with_name('cover-wallpaper-setup.jar')
    if not jar.is_file():
        parser.error('未找到 cover-wallpaper-setup.jar，请先运行 tools/build-wallpaper-helper.py。')
    remote = '/data/local/tmp/foldthrough-cover-wallpaper-setup.jar'
    subprocess.run(base + ['push', str(jar), remote], check=True, timeout=30)
    command = f'CLASSPATH={shlex.quote(remote)} app_process /system/bin CoverWallpaperSetup {shlex.quote(args.action)}'
    subprocess.run(base + ['shell', command], check=True, timeout=30)


if __name__ == '__main__':
    main()
