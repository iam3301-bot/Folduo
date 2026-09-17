#!/usr/bin/env python3
"""只读检查 Fold8 Ultra 的系统、屏幕状态和角度传感器；不设置壁纸或切换屏幕。"""
import argparse
import json
import re
import shutil
import subprocess
from pathlib import Path

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',default=shutil.which('adb'),help='ADB 程序路径')
    parser.add_argument('--serial',help='多台设备连接时指定设备')
    parser.add_argument('--output',type=Path,help='保存精简报告，不包含序列号或屏幕内容')
    args=parser.parse_args()
    if not args.adb: parser.error('请安装 Android platform-tools，或用 --adb 指定路径。')
    def run(parts):
        return subprocess.run(parts,check=True,capture_output=True,text=True,timeout=30).stdout.strip()
    if not args.serial:
        devices=[row.split()[0] for row in run([args.adb,'devices']).splitlines()[1:] if len(row.split())==2 and row.split()[1]=='device']
        if len(devices)!=1:parser.error('请连接一台已授权 USB 调试的手机，或用 --serial 指定设备。')
        args.serial=devices[0]
    base=[args.adb,'-s',args.serial,'shell']
    report={label:run(base+['getprop',key]) for label,key in {
        '型号':'ro.product.model','系统':'ro.build.version.release','API':'ro.build.version.sdk','One UI 标识':'ro.build.version.oneui','版本':'ro.build.display.id'}.items()}
    states=run(base+['dumpsys','device_state'])
    report['系统状态摘要']=[line.strip() for line in states.splitlines() if re.search(r'Supported states|DeviceState\{|CONCURRENT_',line)][:24]
    sensor=run(base+['dumpsys','sensorservice'])
    report['角度传感器摘要']=[line.strip() for line in sensor.splitlines() if re.search(r'hinge|fold|angle',line,re.I) and not re.search(r'uid|package|connection|client',line,re.I)][:24]
    report['型号匹配']=report['型号']=='SM-F9760'
    report['双屏状态名称齐全']=all(name in states for name in ('CONCURRENT_INNER_DEFAULT','CONCURRENT_OUTER_DEFAULT'))
    report['说明']='这是只读能力报告；状态名称存在不等于折叠动画已验证。精细角度仍需在手机开合时检测。'
    content=json.dumps(report,ensure_ascii=False,indent=2)+'\n'
    print(content,end='')
    if args.output:args.output.write_text(content,encoding='utf-8')

if __name__=='__main__': main()
