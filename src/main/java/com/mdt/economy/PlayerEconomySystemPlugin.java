package com.mdt.economy;

import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

public final class PlayerEconomySystemPlugin extends Plugin {
    @Override
    public void init() {
        Log.info("MDT 玩家经济系统 loaded.");
        Log.info("配置目录建议: config/mods/config/mdt-player-economy-system");
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("economy-settle", "立即执行一次经济结算。", args -> {
            Log.info("MDT 玩家经济系统 命令占位已触发: economy-settle");
        });

        handler.register("economy-preview", "<playerOrComid>", "预览某个玩家本局将获得的奖励。", args -> {
            Log.info("MDT 玩家经济系统 命令占位已触发: economy-preview");
        });

        handler.register("economy-reload", "重新加载经济系统配置。", args -> {
            Log.info("MDT 玩家经济系统 命令占位已触发: economy-reload");
        });

    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("economy", "查看自己的经验与货币信息。", (args, player) -> {
            player.sendMessage("[accent]MDT 玩家经济系统[] 命令占位已触发: economy");
        });

    }
}
