#!/usr/bin/env python3
"""检查中文资源覆盖、格式参数和换行，防止升级后出现漏译或格式异常。"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

res = Path(__file__).resolve().parents[1] / "app/src/main/res"
def strings(folder):
    return {e.attrib["name"]: e.text or "" for e in ET.parse(res / folder / "strings.xml").getroot()}

default, chinese, english = strings("values"), strings("values-zh"), strings("values-en")
assert default == chinese, "中文资源与默认资源不一致"
assert english.keys() <= chinese.keys(), "存在未覆盖的上游文案"
for name, value in chinese.items():
    assert re.search(r"[\u3400-\u9fff]", value), f"缺少中文：{name}"
    assert r"\\n" not in value, f"换行被重复转义：{name}"
    if name in english:
        assert sorted(re.findall(r"%\d+\$[sdf]", value)) == sorted(re.findall(r"%\d+\$[sdf]", english[name])), f"格式参数不同：{name}"
print(f"通过：{len(chinese)} 条中文资源，对照资源 {len(english)} 条文案全部覆盖；格式参数与换行正确。")
