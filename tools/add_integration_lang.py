"""为 en_us / zh_cn 追加本轮集成切片新增的 lang 键（JEI 类别与气体读数）。

用法：python tools/add_integration_lang.py
脚本按"在最后一个 } 之前插入"的方式追加，保留原有顺序与缩进；
重复运行不会重复插入（先剔除已存在的键）。
"""
import io
import json
import os
import re

BASE = os.path.join('clockwork_unoffical', 'src', 'main', 'resources',
                    'assets', 'vs_clockwork', 'lang')

EN = {
    "vs_clockwork.jei.category.gas_crafting": "Gas Crafting",
    "vs_clockwork.jei.category.gas_info": "Gas Data",
    "vs_clockwork.jei.gas.mass": "Amount: %s",
    "vs_clockwork.jei.gas.pressure": "Pressure: %s",
    "vs_clockwork.jei.gas.temperature": "Temperature: %s",
    "vs_clockwork.jei.gas.density": "Density: %s",
    "vs_clockwork.jei.gas.specific_heat": "Specific heat: %s",
    "vs_clockwork.jei.gas_crafting.temperature": "Needs gas temperature >= %s",
    "vs_clockwork.jei.gas_crafting.heated": "Basin must be heated",
    "vs_clockwork.jei.gas_crafting.superheated": "Basin must be super heated",
    "vs_clockwork.jei.gas_crafting.no_heat": "No heating required",
    "vs_clockwork.jei.gas_crafting.absorbs": "Absorbs %s of heat from the gas",
    "vs_clockwork.jei.gas_crafting.releases": "Releases %s of heat into the gas",
    "vs_clockwork.jei.duct.info.1": "Ducts form Clockwork's gas networks.",
    "vs_clockwork.jei.duct.info.2": "Each duct is a gas node: mass + temperature = pressure.",
    "vs_clockwork.jei.duct.info.3": "Ambient pressure is %s; higher pressure makes gas flow faster.",
    "vs_clockwork.ponder.phys_bearing.text_1": "The Physical Bearing turns the structure in front of it into a physics sub-level",
    "vs_clockwork.ponder.phys_bearing.text_2": "Right-click it with an empty hand to assemble or disassemble that structure",
    "vs_clockwork.ponder.phys_bearing.text_3": "Once assembled, the input shaft rotates the sub-structure through a physics joint",
    "vs_clockwork.ponder.phys_bearing.text_4": "Scroll on the value box to switch between ROTATE, LOCK and FREE",
}

ZH = {
    "vs_clockwork.jei.category.gas_crafting": "气体合成",
    "vs_clockwork.jei.category.gas_info": "气体资料",
    "vs_clockwork.jei.gas.mass": "质量：%s",
    "vs_clockwork.jei.gas.pressure": "压力：%s",
    "vs_clockwork.jei.gas.temperature": "温度：%s",
    "vs_clockwork.jei.gas.density": "密度：%s",
    "vs_clockwork.jei.gas.specific_heat": "比热容：%s",
    "vs_clockwork.jei.gas_crafting.temperature": "需要气体温度 >= %s",
    "vs_clockwork.jei.gas_crafting.heated": "工作盆下方需要加热",
    "vs_clockwork.jei.gas_crafting.superheated": "工作盆下方需要超级加热",
    "vs_clockwork.jei.gas_crafting.no_heat": "无需加热",
    "vs_clockwork.jei.gas_crafting.absorbs": "从气体吸收 %s 热量",
    "vs_clockwork.jei.gas_crafting.releases": "向气体释放 %s 热量",
    "vs_clockwork.jei.duct.info.1": "气体管道构成 Clockwork 的气体网络。",
    "vs_clockwork.jei.duct.info.2": "每根管道都是一个气体节点：质量 + 温度 = 压力。",
    "vs_clockwork.jei.duct.info.3": "环境压力为 %s；压力越高气体流动越快。",
    "vs_clockwork.ponder.phys_bearing.text_1": "物理轴承会把朝向那一侧的结构变成物理子空间",
    "vs_clockwork.ponder.phys_bearing.text_2": "空手右键即可装配或解体该结构",
    "vs_clockwork.ponder.phys_bearing.text_3": "装配后，输入轴会通过物理关节驱动子结构旋转",
    "vs_clockwork.ponder.phys_bearing.text_4": "在滚轮框上滚轮可切换：跟随输入轴 / 锁定角度 / 自由铰链",
}


def encode(value):
    return json.dumps(value, ensure_ascii=False)


def add_entries(path, entries):
    with io.open(path, encoding='utf-8') as handle:
        text = handle.read()

    # 先移除本次要写入的键（支持重复运行）
    for key in entries:
        text = re.sub(r'\n[ \t]*' + re.escape(encode(key)) + r'\s*:\s*"(?:[^"\\]|\\.)*"\s*,?', '', text)

    index = text.rstrip().rfind('}')
    head = text[:index].rstrip()
    if not head.endswith(','):
        head += ','
    tail = text[index:]
    lines = []
    items = list(entries.items())
    for position, (key, value) in enumerate(items):
        comma = ',' if position < len(items) - 1 else ''
        lines.append('  %s: %s%s' % (encode(key), encode(value), comma))
    with io.open(path, 'w', encoding='utf-8', newline='\n') as handle:
        handle.write(head + '\n' + '\n'.join(lines) + '\n' + tail)


def main():
    add_entries(os.path.join(BASE, 'en_us.json'), EN)
    add_entries(os.path.join(BASE, 'zh_cn.json'), ZH)
    for name in ('en_us', 'zh_cn'):
        with io.open(os.path.join(BASE, name + '.json'), encoding='utf-8') as handle:
            data = json.load(handle)
        added = sum(1 for key in data if key.startswith('vs_clockwork.jei.')
                    or key.startswith('vs_clockwork.ponder.phys_bearing.'))
        print('%s: %d keys total, %d integration keys' % (name, len(data), added))


if __name__ == '__main__':
    main()
