#!/usr/bin/env python3
"""仅用于 SM-F9760 的内外屏桌面壁纸；prepare 备份，apply 应用，restore 恢复。"""
import argparse
import shlex
import shutil
import subprocess
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['status', 'prepare', 'apply', 'restore'], nargs='?', default='status')
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
    jar = Path(__file__).with_name('fold8-wallpaper-setup.jar')
    if not jar.is_file():
        parser.error('未找到 fold8-wallpaper-setup.jar，请先运行 tools/build-fold8-wallpaper-helper.py。')
    remote = '/data/local/tmp/folduo-fold8-wallpaper.jar'
    subprocess.run(base + ['push', str(jar), remote], check=True, timeout=30)
    command = f'CLASSPATH={shlex.quote(remote)} app_process /system/bin Fold8WallpaperSetup {shlex.quote(args.action)}'
    subprocess.run(base + ['shell', command], check=True, timeout=30)


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError:
        sys.exit('操作未完成，请按上方设备提示处理；已有恢复记录会保留。')
    except subprocess.TimeoutExpired:
        sys.exit('设备响应超时。请保持连接，先运行 status 检查当前状态，勿删除恢复记录。')
