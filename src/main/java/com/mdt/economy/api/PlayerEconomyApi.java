package com.mdt.economy.api;

import java.util.Map;
import java.util.Set;

public interface PlayerEconomyApi {
    long getBalance(String playerUuid, String currencyKey);

    Map<String, Long> getBalances(String playerUuid);

    long setBalance(String playerUuid, String currencyKey, long amount);

    long addBalance(String playerUuid, String currencyKey, long amount);

    boolean deductBalance(String playerUuid, String currencyKey, long amount);

    boolean canAfford(String playerUuid, String currencyKey, long amount);

    Set<String> getCurrencyKeys();
}
