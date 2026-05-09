package com.mdt.economy.service;

import com.mdt.economy.api.PlayerEconomyApi;
import com.mdt.economy.config.EconomyConfiguration;
import com.mdt.economy.config.EconomyConfiguration.CurrencyDefinition;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class EconomyService implements PlayerEconomyApi {
    private static final String PLAYER_PREFIX = "player.";

    private final EconomyConfiguration configuration;
    private final File storageFile;
    private final Map<String, Map<String, Long>> balances = new LinkedHashMap<String, Map<String, Long>>();

    public EconomyService(EconomyConfiguration configuration, File dataRoot) throws IOException {
        this.configuration = configuration;
        this.storageFile = new File(dataRoot, configuration.getDataFileName());
        load();
    }

    @Override
    public synchronized long getBalance(String playerUuid, String currencyKey) {
        CurrencyDefinition currency = requireCurrency(currencyKey);
        Map<String, Long> playerBalances = balances.get(normalizeUuid(playerUuid));
        if (playerBalances == null) {
            return currency.getDefaultBalance();
        }
        Long value = playerBalances.get(currency.getKey());
        return value == null ? currency.getDefaultBalance() : value.longValue();
    }

    @Override
    public synchronized Map<String, Long> getBalances(String playerUuid) {
        String uuid = normalizeUuid(playerUuid);
        Map<String, Long> snapshot = new LinkedHashMap<String, Long>();
        for (CurrencyDefinition currency : configuration.getCurrencies().values()) {
            snapshot.put(currency.getKey(), Long.valueOf(getBalance(uuid, currency.getKey())));
        }
        return snapshot;
    }

    @Override
    public synchronized long setBalance(String playerUuid, String currencyKey, long amount) {
        CurrencyDefinition currency = requireCurrency(currencyKey);
        Map<String, Long> playerBalances = getOrCreatePlayerBalances(normalizeUuid(playerUuid));
        playerBalances.put(currency.getKey(), Long.valueOf(amount));
        saveQuietly();
        return amount;
    }

    @Override
    public synchronized long addBalance(String playerUuid, String currencyKey, long amount) {
        long result = getBalance(playerUuid, currencyKey) + amount;
        setBalance(playerUuid, currencyKey, result);
        return result;
    }

    @Override
    public synchronized boolean deductBalance(String playerUuid, String currencyKey, long amount) {
        if (!canAfford(playerUuid, currencyKey, amount)) {
            return false;
        }
        setBalance(playerUuid, currencyKey, getBalance(playerUuid, currencyKey) - amount);
        return true;
    }

    @Override
    public synchronized boolean canAfford(String playerUuid, String currencyKey, long amount) {
        return getBalance(playerUuid, currencyKey) >= amount;
    }

    @Override
    public synchronized Set<String> getCurrencyKeys() {
        return Collections.unmodifiableSet(configuration.getCurrencies().keySet());
    }

    public synchronized Set<String> getKnownPlayers() {
        return Collections.unmodifiableSet(balances.keySet());
    }

    public synchronized String describeCurrencies() {
        StringBuilder builder = new StringBuilder();
        for (CurrencyDefinition currency : configuration.getCurrencies().values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(currency.getKey()).append("(").append(currency.getDisplayName()).append(")");
        }
        return builder.length() == 0 ? "<none>" : builder.toString();
    }

    public synchronized String describeBalances(String playerUuid) {
        StringBuilder builder = new StringBuilder();
        Map<String, Long> playerBalances = getBalances(playerUuid);
        for (Map.Entry<String, Long> entry : playerBalances.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue().longValue());
        }
        return builder.length() == 0 ? "<none>" : builder.toString();
    }

    private void load() throws IOException {
        balances.clear();
        if (!storageFile.exists()) {
            saveQuietly();
            return;
        }

        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(storageFile), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith(PLAYER_PREFIX)) {
                continue;
            }
            int splitIndex = name.lastIndexOf('.');
            if (splitIndex <= PLAYER_PREFIX.length()) {
                continue;
            }
            String uuid = name.substring(PLAYER_PREFIX.length(), splitIndex);
            String currency = name.substring(splitIndex + 1);
            if (!configuration.hasCurrency(currency)) {
                continue;
            }
            long value = parseLong(properties.getProperty(name), configuration.getCurrency(currency).getDefaultBalance());
            getOrCreatePlayerBalances(uuid).put(currency, Long.valueOf(value));
        }
    }

    private void saveQuietly() {
        try {
            save();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to save economy storage.", exception);
        }
    }

    private void save() throws IOException {
        Properties properties = new Properties();
        for (Map.Entry<String, Map<String, Long>> playerEntry : balances.entrySet()) {
            for (Map.Entry<String, Long> balanceEntry : playerEntry.getValue().entrySet()) {
                properties.setProperty(
                    PLAYER_PREFIX + playerEntry.getKey() + "." + balanceEntry.getKey(),
                    String.valueOf(balanceEntry.getValue().longValue())
                );
            }
        }
        if (!storageFile.exists()) {
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("Unable to create storage directory: " + parent.getAbsolutePath());
            }
            if (!storageFile.createNewFile()) {
                throw new IOException("Unable to create storage file: " + storageFile.getAbsolutePath());
            }
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(storageFile), StandardCharsets.UTF_8)) {
            properties.store(writer, "MDT player economy balances");
        }
    }

    private Map<String, Long> getOrCreatePlayerBalances(String uuid) {
        Map<String, Long> playerBalances = balances.get(uuid);
        if (playerBalances == null) {
            playerBalances = new LinkedHashMap<String, Long>();
            balances.put(uuid, playerBalances);
        }
        return playerBalances;
    }

    private CurrencyDefinition requireCurrency(String rawKey) {
        String key = rawKey == null ? "" : rawKey.trim();
        CurrencyDefinition currency = configuration.getCurrency(key);
        if (currency == null) {
            throw new IllegalArgumentException("Unknown currency: " + rawKey);
        }
        return currency;
    }

    private String normalizeUuid(String playerUuid) {
        if (playerUuid == null || playerUuid.trim().isEmpty()) {
            throw new IllegalArgumentException("Player UUID cannot be empty.");
        }
        return playerUuid.trim();
    }

    private long parseLong(String raw, long fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
