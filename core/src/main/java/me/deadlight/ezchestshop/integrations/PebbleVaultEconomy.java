package me.deadlight.ezchestshop.integrations;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;

/**
 * Vault-compatible adapter backed by PebbleCore Cash.
 *
 * <p>This lets PebbleShop's existing transaction code keep using the Vault Economy interface while
 * all money movement is applied to PebbleCore's active-profile Cash wallet.</p>
 */
public final class PebbleVaultEconomy implements Economy {

    @Override
    public boolean isEnabled() {
        return PebbleEconomyBridge.economyAvailable();
    }

    @Override
    public String getName() {
        return "PebbleCore";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return PebbleEconomyBridge.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return "Cash";
    }

    @Override
    public String currencyNameSingular() {
        return "Cash";
    }

    @Override
    public boolean hasAccount(String playerName) {
        return hasAccount(player(playerName));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null && PebbleEconomyBridge.economyAvailable();
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(player(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return PebbleEconomyBridge.getBalance(player);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return has(player(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return PebbleEconomyBridge.has(player, amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(player(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        boolean success = PebbleEconomyBridge.withdraw(player, amount);
        return response(player, amount, success);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(player(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        boolean success = PebbleEconomyBridge.deposit(player, amount);
        return response(player, amount, success);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return bankUnsupported();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    private EconomyResponse response(OfflinePlayer player, double amount, boolean success) {
        double balance = PebbleEconomyBridge.getBalance(player);
        String error = success ? null : PebbleEconomyBridge.lastError();
        if (error == null && !success) {
            error = "PebbleCore Cash transaction failed.";
        }
        return new EconomyResponse(
                amount,
                balance,
                success ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                error
        );
    }

    private EconomyResponse bankUnsupported() {
        return new EconomyResponse(
                0.0,
                0.0,
                EconomyResponse.ResponseType.NOT_IMPLEMENTED,
                "PebbleCore Cash does not expose Vault bank accounts."
        );
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer player(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return null;
        }
        return Bukkit.getOfflinePlayer(playerName);
    }
}
