package me.deadlight.ezchestshop.integrations;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Reflective bridge into PebbleCore's Cash economy.
 *
 * <p>PebbleShop is built and shipped independently, so this class does not compile against
 * PebbleCore. At runtime it resolves either the public PebbleQuest API or, as a compatibility
 * fallback, PebbleCore's legacy getCashService() methods. All player-keyed operations affect
 * PebbleCore's active-profile Cash balance.</p>
 */
public final class PebbleEconomyBridge {

    private static final String PLUGIN_NAME = "PebbleCore";
    private static final String REASON = "pebbleshop";

    private static Plugin pebble;
    private static Object economyService;
    private static Method depositMethod;
    private static Method withdrawMethod;
    private static Method getCashMethod;
    private static Method formatMethod;
    private static Method txSuccessMethod;
    private static Method txErrorMethod;
    private static String lastError;
    private static boolean warned;

    private PebbleEconomyBridge() {}

    private static synchronized void ensure() {
        if (pebble != null && economyService != null && depositMethod != null && withdrawMethod != null && getCashMethod != null) {
            return;
        }

        Plugin candidate = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (candidate == null || !candidate.isEnabled()) {
            return;
        }

        try {
            Object service = resolvePublicApi(candidate);
            if (service == null) {
                service = resolveLegacyCashService(candidate);
            }
            if (service == null) {
                warnOnce("PebbleCore is enabled, but no compatible Cash economy service was found.", null);
                return;
            }

            bind(candidate, service);
        } catch (Throwable t) {
            warnOnce("PebbleCore economy bridge unavailable: " + t.getMessage(), t);
        }
    }

    private static Object resolvePublicApi(Plugin candidate) throws Exception {
        ClassLoader loader = candidate.getClass().getClassLoader();
        Class<?> apiClass = Class.forName("com.gluckz.pebblequest.api.PebbleQuestAPI", true, loader);
        Object root = apiClass.getMethod("get").invoke(null);
        if (root == null) {
            return null;
        }
        return root.getClass().getMethod("economy").invoke(root);
    }

    private static Object resolveLegacyCashService(Plugin candidate) throws Exception {
        return candidate.getClass().getMethod("getCashService").invoke(candidate);
    }

    private static void bind(Plugin candidate, Object service) throws Exception {
        Method deposit = service.getClass().getMethod("deposit", UUID.class, double.class, String.class);
        Method withdraw = service.getClass().getMethod("withdraw", UUID.class, double.class, String.class);
        Method getCash = service.getClass().getMethod("getCash", UUID.class);

        Method format = null;
        try {
            format = service.getClass().getMethod("format", double.class);
        } catch (NoSuchMethodException ignored) {
            // Older PebbleCore builds may not expose format(double). PebbleShop can fall back locally.
        }

        Method success = null;
        Method error = null;
        try {
            success = deposit.getReturnType().getMethod("success");
        } catch (NoSuchMethodException ignored) {
            // Older return types are treated as success if no success() accessor exists.
        }
        try {
            error = deposit.getReturnType().getMethod("error");
        } catch (NoSuchMethodException ignored) {
            // Error text is optional.
        }

        pebble = candidate;
        economyService = service;
        depositMethod = deposit;
        withdrawMethod = withdraw;
        getCashMethod = getCash;
        formatMethod = format;
        txSuccessMethod = success;
        txErrorMethod = error;
        lastError = null;
    }

    public static boolean economyAvailable() {
        ensure();
        return pebble != null && economyService != null && depositMethod != null && withdrawMethod != null && getCashMethod != null;
    }

    public static double getBalance(OfflinePlayer player) {
        ensure();
        if (player == null || getCashMethod == null) {
            return 0.0;
        }
        try {
            Object result = getCashMethod.invoke(economyService, player.getUniqueId());
            if (result instanceof Number) {
                return ((Number) result).doubleValue();
            }
        } catch (Throwable t) {
            warn("balance", t);
        }
        return 0.0;
    }

    public static boolean has(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        return getBalance(player) + 1.0E-6 >= amount;
    }

    public static boolean deposit(OfflinePlayer player, double amount) {
        ensure();
        if (player == null || depositMethod == null) {
            lastError = "PebbleCore Cash API is not available.";
            return false;
        }
        if (amount < 0) {
            lastError = "Amount must be non-negative.";
            return false;
        }
        if (amount == 0) {
            lastError = null;
            return true;
        }
        try {
            Object result = depositMethod.invoke(economyService, player.getUniqueId(), amount, REASON);
            return transactionSuccess(result);
        } catch (Throwable t) {
            lastError = t.getMessage();
            warn("deposit", t);
            return false;
        }
    }

    public static boolean withdraw(OfflinePlayer player, double amount) {
        ensure();
        if (player == null || withdrawMethod == null) {
            lastError = "PebbleCore Cash API is not available.";
            return false;
        }
        if (amount < 0) {
            lastError = "Amount must be non-negative.";
            return false;
        }
        if (amount == 0) {
            lastError = null;
            return true;
        }
        try {
            Object result = withdrawMethod.invoke(economyService, player.getUniqueId(), amount, REASON);
            return transactionSuccess(result);
        } catch (Throwable t) {
            lastError = t.getMessage();
            warn("withdraw", t);
            return false;
        }
    }

    public static String format(double amount) {
        ensure();
        if (formatMethod != null) {
            try {
                Object result = formatMethod.invoke(economyService, amount);
                if (result != null) {
                    return result.toString();
                }
            } catch (Throwable t) {
                warn("format", t);
            }
        }
        return String.format("%,.2f", amount);
    }

    public static String lastError() {
        return lastError;
    }

    private static boolean transactionSuccess(Object result) {
        if (result == null) {
            lastError = null;
            return true;
        }
        if (txSuccessMethod == null) {
            lastError = null;
            return true;
        }
        try {
            Object ok = txSuccessMethod.invoke(result);
            boolean success = !(ok instanceof Boolean) || (Boolean) ok;
            if (!success) {
                lastError = readError(result);
            } else {
                lastError = null;
            }
            return success;
        } catch (Throwable t) {
            lastError = null;
            return true;
        }
    }

    private static String readError(Object result) {
        if (txErrorMethod == null) {
            return "PebbleCore transaction failed.";
        }
        try {
            Object error = txErrorMethod.invoke(result);
            return error == null ? "PebbleCore transaction failed." : error.toString();
        } catch (Throwable ignored) {
            return "PebbleCore transaction failed.";
        }
    }

    private static void warn(String operation, Throwable t) {
        Bukkit.getLogger().log(Level.WARNING, "[PebbleShop] PebbleCore Cash " + operation + " failed: " + t.getMessage());
    }

    private static void warnOnce(String message, Throwable t) {
        if (warned) {
            return;
        }
        warned = true;
        if (t == null) {
            Bukkit.getLogger().warning("[PebbleShop] " + message);
        } else {
            Bukkit.getLogger().log(Level.WARNING, "[PebbleShop] " + message, t);
        }
    }
}
