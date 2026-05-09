# MDT 玩家经济系统

管理玩家多币种余额，并把余额同步到 `player_profile` 列表数据，方便排行榜、资料页和其他插件直接读取。

## 依赖

- `mdt-list-data-system`
- 可选联动：`go---mdt---Jump-Plugin`，用于把 UUID 映射到 COMID
- 可选联动：`mdt-player-level-system`，用于按等级发奖励

## 配置文件

首次启动后会生成：

```text
config/mods/config/mdt-player-economy-system/player-economy-system.properties
```

关键配置项：

- `storage.file`：余额存储文件名
- `currency.keys`：启用的币种列表
- `currency.<key>.displayName`：币种显示名
- `currency.<key>.defaultBalance`：默认余额

## 写入字段

插件会把每个币种同步到 `player_profile`：

- `gold_balance`
- `rare_crystal_balance`
- 以及其他 `currency.keys` 中声明的 `<currency>_balance`

同时会维护：

- `uuid`
- `comid`
- `lastName`
- `updatedAt`

## 命令

- `economy-currencies`：查看已配置币种
- `economy-balance <playerOrUuid> [currency]`：查看余额
- `economy-set <playerOrUuid> <currency> <amount>`：设置余额
- `economy-add <playerOrUuid> <currency> <amount>`：增加余额
- `economy-take <playerOrUuid> <currency> <amount>`：扣减余额
- `economy-transfer <from> <to> <currency> <amount>`：转账
- `economy-reward-level <playerOrUuid> <currency> <baseAmount> [perLevel]`：按等级发奖励
- `economy-preview <playerOrUuid>`：预览全部余额
- `economy-reload`：重载配置
- `/economy [currency]`：客户端查看自己的余额

## 插件入口

```text
com.mdt.economy.PlayerEconomySystemPlugin
```
