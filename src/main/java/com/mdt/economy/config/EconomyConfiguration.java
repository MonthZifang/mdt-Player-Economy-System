package com.mdt.economy.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class EconomyConfiguration {
    private final String dataFileName;
    private final Map<String, CurrencyDefinition> currencies;

    private EconomyConfiguration(String dataFileName, Map<String, CurrencyDefinition> currencies) {
        this.dataFileName = dataFileName;
        this.currencies = currencies;
    }

    public static EconomyConfiguration load(File file) throws IOException {
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        String dataFileName = valueOrDefault(properties.getProperty("storage.file"), "player-economy-storage.properties");
        List<String> keys = splitCsv(properties.getProperty("currency.keys"));
        if (keys.isEmpty()) {
            keys.add("gold");
        }

        Map<String, CurrencyDefinition> currencies = new LinkedHashMap<String, CurrencyDefinition>();
        for (String key : keys) {
            String displayName = valueOrDefault(properties.getProperty("currency." + key + ".displayName"), key);
            long defaultBalance = parseLong(properties.getProperty("currency." + key + ".defaultBalance"), 0L);
            currencies.put(key, new CurrencyDefinition(key, displayName, defaultBalance));
        }

        return new EconomyConfiguration(dataFileName, currencies);
    }

    public String getDataFileName() {
        return dataFileName;
    }

    public Map<String, CurrencyDefinition> getCurrencies() {
        return currencies;
    }

    public boolean hasCurrency(String key) {
        return currencies.containsKey(key);
    }

    public CurrencyDefinition getCurrency(String key) {
        return currencies.get(key);
    }

    private static long parseLong(String raw, long fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<String>();
        }
        String[] parts = raw.split(",");
        List<String> result = new ArrayList<String>();
        for (String part : parts) {
            String value = part == null ? "" : part.trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    public static final class CurrencyDefinition {
        private final String key;
        private final String displayName;
        private final long defaultBalance;

        private CurrencyDefinition(String key, String displayName, long defaultBalance) {
            this.key = key;
            this.displayName = displayName;
            this.defaultBalance = defaultBalance;
        }

        public String getKey() {
            return key;
        }

        public String getDisplayName() {
            return displayName;
        }

        public long getDefaultBalance() {
            return defaultBalance;
        }
    }
}
