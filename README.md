<div align="center">
  <a href="https://github.com/MonthZifang/YUEYUEDAO-TECH">
    <img src="./md/logo.png" alt="YUEYUEDAO TECH Logo" width="720" />
  </a>

  <p><strong>YUEYUEDAO TECH 维护 MDT 玩家经济系统</strong></p>

  <p>
    <a href="https://github.com/MonthZifang/YUEYUEDAO-TECH"><strong>查看月月岛科技详情</strong></a>
  </p>
</div>

# MDT 玩家经济系统

按在线时长和建造数量结算玩家每局收益，只对已绑定玩家生效，按可配置概率计算经验、普通货币和稀有货币，并把结果写回列表数据系统。

## 市场固定识别文件

仓库根目录固定提供以下文件，供插件市场识别：

```text
market.plugin.json
plugin.json
```

## 依赖

- `mdt-list-data-system`
- 可选依赖：`mdt-bound-unbound`

## 配置文件

首次启动后建议维护以下配置文件：

```text
config/mods/config/mdt-player-economy-system/player-economy-system.properties
```

- 只对已绑定玩家结算，可直接读取列表数据系统中的绑定字段。
- 支持在线时长权重与建造数量权重组合结算。
- 支持多个货币项，内部键固定，显示名可自定义但不建议改内部键。
- 支持显示每局结算前三名，并展示经验、普通货币、稀有货币结果。

## 功能说明

- 支持每局结束自动结算。
- 支持经验、普通货币、稀有货币的独立概率与数量配置。
- 概率支持小数，例如 `0.01` 或 `0.0001`。
- 统一把结算结果写入列表数据系统中的玩家对象。

## 数据与写入说明

- 建议经验字段使用 `experience`。
- 建议货币写入 `currency.gold`、`currency.rare_crystal` 之类的固定键。
- 排行榜面板只显示前三名，但完整结算仍可写入全部玩家数据。

## 命令

- `economy-settle`：立即执行一次经济结算。
- `economy-preview <playerOrComid>`：预览某个玩家本局将获得的奖励。
- `economy-reload`：重新加载经济系统配置。
- `/economy`：查看自己的经验与货币信息。

## Help 注册备注

- `help mdt-player-economy-system`：查看 MDT 玩家经济系统 的独立命令说明。
- 中文备注建议写为“单局结算、收益预览、概率配置重载”。

## 插件入口

```text
com.mdt.economy.PlayerEconomySystemPlugin
```

## 版本规则

- 当前插件版本：`v1`
- 当前需求市场版本：`v1`
