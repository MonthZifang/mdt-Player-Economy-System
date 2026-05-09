package com.mdt.economy.util;

import arc.util.Strings;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import mindustry.gen.Groups;
import mindustry.gen.Player;

public final class PlayerUuidResolver {
    private PlayerUuidResolver() {
    }

    public static String resolveIdentity(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Player identity cannot be empty.");
        }
        String normalized = value.trim();
        Player player = Groups.player.find(candidate ->
            normalized.equalsIgnoreCase(candidate.name)
                || normalized.equalsIgnoreCase(Strings.stripColors(candidate.name))
                || normalized.equalsIgnoreCase(candidate.plainName())
                || normalized.equalsIgnoreCase(resolvePlayerUuid(candidate))
        );
        return player == null ? normalized : resolvePlayerUuid(player);
    }

    public static String resolvePlayerUuid(Player player) {
        try {
            Method method = player.getClass().getMethod("uuid");
            Object value = method.invoke(player);
            if (value != null) {
                return value.toString();
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to field lookup.
        }

        try {
            Field field = player.getClass().getField("uuid");
            Object value = field.get(player);
            if (value != null) {
                return value.toString();
            }
        } catch (ReflectiveOperationException ignored) {
            // Throw below.
        }

        throw new IllegalStateException("Unable to resolve UUID from player object.");
    }
}
