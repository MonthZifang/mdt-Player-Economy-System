package com.mdt.economy;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import com.mdt.economy.api.PlayerEconomyApi;
import com.mdt.economy.config.EconomyConfiguration;
import com.mdt.economy.service.EconomyService;
import com.mdt.economy.util.PlayerUuidResolver;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import mindustry.Vars;
import mindustry.game.EventType.PlayerJoin;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

public final class PlayerEconomySystemPlugin extends Plugin {
    private static final String CONFIG_DIR_NAME = "mdt-player-economy-system";
    private static final String CONFIG_FILE_NAME = "player-economy-system.properties";

    private static volatile PlayerEconomyApi api;

    private File dataRoot;
    private EconomyConfiguration configuration;
    private EconomyService service;

    public static PlayerEconomyApi getApi() {
        return api;
    }

    @Override
    public void init() {
        try {
            dataRoot = resolveDataRoot();
            ensureDefaultResources(dataRoot);
            reloadInternal();
            api = service;
            Events.on(PlayerJoin.class, event -> syncProfile(event.player));
            Log.info("MDT Player Economy System loaded.");
            Log.info("Config file: @", new File(dataRoot, CONFIG_FILE_NAME).getAbsolutePath());
            Log.info("Currencies: @", service.describeCurrencies());
        } catch (IOException exception) {
            throw new RuntimeException("Failed to initialize MDT Player Economy System.", exception);
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("economy-reload", "Reload economy configuration and storage.", args -> {
            try {
                reloadInternal();
                api = service;
                Log.info("Economy reloaded. Currencies: @", service.describeCurrencies());
            } catch (IOException exception) {
                Log.err("Failed to reload economy config: @", exception.getMessage());
            }
        });

        handler.register("economy-currencies", "List configured currencies.", args -> {
            Log.info("Configured currencies: @", service.describeCurrencies());
        });

        handler.register("economy-balance", "<playerOrUuid> [currency]", "Show one balance or all balances.", args -> {
            runEconomyCommand(() -> {
                String identity = PlayerUuidResolver.resolveIdentity(args[0]);
                String storageIdentity = resolveStorageIdentity(identity);
                if (args.length > 1) {
                    long balance = service.getBalance(storageIdentity, args[1]);
                    Log.info("@ @ = @", identity, args[1], balance);
                } else {
                    Log.info("@ balances: @", identity, service.describeBalances(storageIdentity));
                }
                syncProfile(identity);
            });
        });

        handler.register("economy-set", "<playerOrUuid> <currency> <amount>", "Set a player's currency balance.", args -> {
            Long amount = parseLong(args[2]);
            if (amount == null) {
                Log.err("Amount must be an integer.");
                return;
            }
            runEconomyCommand(() -> {
                String identity = PlayerUuidResolver.resolveIdentity(args[0]);
                String storageIdentity = resolveStorageIdentity(identity);
                service.setBalance(storageIdentity, args[1], amount.longValue());
                syncProfile(identity);
                Log.info("Set @ @ -> @", identity, args[1], amount.longValue());
            });
        });

        handler.register("economy-add", "<playerOrUuid> <currency> <amount>", "Add currency to a player.", args -> {
            Long amount = parseLong(args[2]);
            if (amount == null) {
                Log.err("Amount must be an integer.");
                return;
            }
            runEconomyCommand(() -> {
                String identity = PlayerUuidResolver.resolveIdentity(args[0]);
                String storageIdentity = resolveStorageIdentity(identity);
                long result = service.addBalance(storageIdentity, args[1], amount.longValue());
                syncProfile(identity);
                Log.info("Added @ @ to @, new balance=@", amount.longValue(), args[1], identity, result);
            });
        });

        handler.register("economy-take", "<playerOrUuid> <currency> <amount>", "Take currency from a player.", args -> {
            Long amount = parseLong(args[2]);
            if (amount == null) {
                Log.err("Amount must be an integer.");
                return;
            }
            if (amount.longValue() < 0L) {
                Log.err("Amount must be a non-negative integer.");
                return;
            }
            runEconomyCommand(() -> {
                String identity = PlayerUuidResolver.resolveIdentity(args[0]);
                String storageIdentity = resolveStorageIdentity(identity);
                boolean success = service.deductBalance(storageIdentity, args[1], amount.longValue());
                if (!success) {
                    Log.info("Not enough @ for @, current=@ needed=@", args[1], identity, service.getBalance(storageIdentity, args[1]), amount.longValue());
                    return;
                }
                syncProfile(identity);
                Log.info("Took @ @ from @, new balance=@", amount.longValue(), args[1], identity, service.getBalance(storageIdentity, args[1]));
            });
        });

        handler.register("economy-transfer", "<from> <to> <currency> <amount>", "Transfer currency between players.", args -> {
            Long amount = parseLong(args[3]);
            if (amount == null || amount.longValue() < 0L) {
                Log.err("Amount must be a non-negative integer.");
                return;
            }
            runEconomyCommand(() -> {
                String fromIdentity = PlayerUuidResolver.resolveIdentity(args[0]);
                String toIdentity = PlayerUuidResolver.resolveIdentity(args[1]);
                String fromStorage = resolveStorageIdentity(fromIdentity);
                String toStorage = resolveStorageIdentity(toIdentity);
                if (!service.deductBalance(fromStorage, args[2], amount.longValue())) {
                    Log.info("Transfer failed. @ does not have enough @.", fromIdentity, args[2]);
                    return;
                }
                service.addBalance(toStorage, args[2], amount.longValue());
                syncProfile(fromIdentity);
                syncProfile(toIdentity);
                Log.info("Transferred @ @ from @ to @", amount.longValue(), args[2], fromIdentity, toIdentity);
            });
        });

        handler.register("economy-reward-level", "<playerOrUuid> <currency> <baseAmount> [perLevel]", "Reward a player using their level.", args -> {
            Long baseAmount = parseLong(args[2]);
            Long perLevel = args.length >= 4 ? parseLong(args[3]) : Long.valueOf(0L);
            if (baseAmount == null || perLevel == null) {
                Log.err("Reward values must be integers.");
                return;
            }
            runEconomyCommand(() -> {
                String identity = PlayerUuidResolver.resolveIdentity(args[0]);
                String storageIdentity = resolveStorageIdentity(identity);
                int level = readLevel(identity);
                long reward = baseAmount.longValue() + (perLevel.longValue() * Math.max(0, level));
                long result = service.addBalance(storageIdentity, args[1], reward);
                syncProfile(identity);
                Log.info("Rewarded @ with @ @ using level @, new balance=@", identity, reward, args[1], level, result);
            });
        });

        handler.register("economy-preview", "<playerOrUuid>", "Preview all balances for a player.", args -> {
            runEconomyCommand(() -> {
                String identity = PlayerUuidResolver.resolveIdentity(args[0]);
                String storageIdentity = resolveStorageIdentity(identity);
                Log.info("@ balances: @", identity, service.describeBalances(storageIdentity));
                syncProfile(identity);
            });
        });

        handler.register("economy-sync", "[playerOrUuid]", "Sync one or all economy profiles into player_profile.", args -> {
            if (args.length == 0) {
                syncAllProfiles();
                Log.info("Synced all economy profiles.");
                return;
            }
            String identity = PlayerUuidResolver.resolveIdentity(args[0]);
            syncProfile(identity);
            Log.info("Synced economy profile for @", identity);
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("economy", "[currency]", "Show your currencies.", (args, player) -> {
            String uuid = PlayerUuidResolver.resolvePlayerUuid(player);
            syncProfile(player);
            if (args.length > 0) {
                long balance = service.getBalance(uuid, args[0]);
                player.sendMessage("[accent]" + args[0] + "[]: " + balance);
            } else {
                player.sendMessage("[accent]Economy[] " + service.describeBalances(uuid));
            }
        });
    }

    private void reloadInternal() throws IOException {
        configuration = EconomyConfiguration.load(new File(dataRoot, CONFIG_FILE_NAME));
        service = new EconomyService(configuration, dataRoot);
    }

    private void syncProfile(Player player) {
        if (player == null) {
            return;
        }
        syncProfile(PlayerUuidResolver.resolvePlayerUuid(player));
    }

    private void syncProfile(String uuid) {
        try {
            String normalizedIdentity = resolveStorageIdentity(uuid);
            boolean comidIdentity = looksLikeComId(normalizedIdentity);
            String comid = comidIdentity ? normalizedIdentity.trim().toUpperCase() : resolveComId(normalizedIdentity);
            String key = comid == null || comid.trim().isEmpty() ? normalizedIdentity : comid;
            Map<String, String> current = listDataObject("player_profile", key);
            LinkedHashMap<String, String> updated = new LinkedHashMap<String, String>(current);
            updated.put("uuid", comidIdentity ? current.getOrDefault("uuid", "-") : normalizedIdentity);
            if (comid != null && !comid.trim().isEmpty()) {
                updated.put("comid", comid);
            }
            Player online = findOnlinePlayer(normalizedIdentity);
            if (online != null) {
                updated.put("lastName", online.plainName());
            }
            for (Map.Entry<String, Long> entry : service.getBalances(normalizedIdentity).entrySet()) {
                updated.put(entry.getKey() + "_balance", String.valueOf(entry.getValue().longValue()));
            }
            updated.put("updatedAt", nowText());
            listDataPutObject("player_profile", key, updated);
        } catch (Exception exception) {
            Log.err("Failed to sync economy profile: @", exception.getMessage());
        }
    }

    private Player findOnlinePlayer(String uuid) {
        return mindustry.gen.Groups.player.find(player -> uuid.equalsIgnoreCase(PlayerUuidResolver.resolvePlayerUuid(player)));
    }

    private int readLevel(String uuid) {
        String identity = resolveStorageIdentity(uuid);
        if (looksLikeComId(identity)) {
            Map<String, String> direct = listDataObject("player_profile", identity.trim().toUpperCase());
            return parseInt(direct.get("level"));
        }
        String comid = resolveComId(identity);
        String key = comid == null || comid.trim().isEmpty() ? identity : comid;
        Map<String, String> object = listDataObject("player_profile", key);
        if (object.isEmpty()) {
            object = listDataObject("player_profile", identity);
        }
        return parseInt(object.get("level"));
    }

    private void syncAllProfiles() {
        for (String identity : service.getKnownPlayers()) {
            syncProfile(identity);
        }
    }

    private String resolveStorageIdentity(String identity) {
        String normalized = identity == null ? "" : identity.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        if (!looksLikeComId(normalized)) {
            return normalized;
        }
        Map<String, String> profile = listDataObject("player_profile", normalized.toUpperCase());
        String uuid = profile.get("uuid");
        return uuid == null || uuid.trim().isEmpty() || "-".equals(uuid.trim()) ? normalized.toUpperCase() : uuid.trim();
    }

    private void runEconomyCommand(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            Log.err(exception.getMessage());
        } catch (RuntimeException exception) {
            Log.err("Economy command failed: @", exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> listDataObject(String listName, String key) {
        try {
            Class<?> listDataClass = Class.forName("com.mdt.listdata.ListDataSystemPlugin");
            Object result = listDataClass.getMethod("getObject", String.class, String.class).invoke(null, listName, key);
            return result == null ? new LinkedHashMap<String, String>() : (Map<String, String>)result;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to read list-data object.", exception);
        }
    }

    private void listDataPutObject(String listName, String key, Map<String, String> values) {
        try {
            Class<?> listDataClass = Class.forName("com.mdt.listdata.ListDataSystemPlugin");
            Method method = listDataClass.getMethod("putObject", String.class, String.class, Map.class);
            method.invoke(null, listName, key, values);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to write list-data object.", exception);
        }
    }

    private String resolveComId(String uuid) {
        try {
            Class<?> jumpPluginClass = Class.forName("com.mdt.jump.JumpComIdPlugin");
            Object apiObject = jumpPluginClass.getMethod("getApi").invoke(null);
            if (apiObject == null) {
                return "";
            }
            Object record = apiObject.getClass().getMethod("getOrCreate", String.class).invoke(apiObject, uuid);
            if (record == null) {
                return "";
            }
            Object value = record.getClass().getMethod("getComId").invoke(record);
            return value == null ? "" : value.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private File resolveDataRoot() {
        File modsRoot = new File(Vars.dataDirectory.absolutePath(), "mods");
        return new File(new File(modsRoot, "config"), CONFIG_DIR_NAME);
    }

    private void ensureDefaultResources(File root) throws IOException {
        if (!root.exists() && !root.mkdirs() && !root.isDirectory()) {
            throw new IOException("Unable to create config directory: " + root.getAbsolutePath());
        }
        copyIfMissing(root, CONFIG_FILE_NAME);
    }

    private void copyIfMissing(File root, String resourceName) throws IOException {
        File target = new File(root, resourceName);
        if (target.exists()) {
            return;
        }
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IOException("Missing bundled resource: " + resourceName);
            }
            Files.copy(inputStream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Long parseLong(String raw) {
        try {
            return Long.valueOf(Long.parseLong(raw.trim()));
        } catch (Exception exception) {
            return null;
        }
    }

    private int parseInt(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private boolean looksLikeComId(String value) {
        return value != null && value.trim().length() <= 8 && value.trim().matches("[A-Za-z0-9]+");
    }

    private String nowText() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
    }
}
