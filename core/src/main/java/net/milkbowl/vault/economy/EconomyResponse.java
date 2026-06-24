package net.milkbowl.vault.economy;

/**
 * Minimal Vault EconomyResponse compatibility class used by legacy PebbleShop call sites.
 */
public class EconomyResponse {

    public enum ResponseType {
        SUCCESS,
        FAILURE,
        NOT_IMPLEMENTED
    }

    public final double amount;
    public final double balance;
    public final ResponseType type;
    public final String errorMessage;

    public EconomyResponse(double amount, double balance, ResponseType type, String errorMessage) {
        this.amount = amount;
        this.balance = balance;
        this.type = type;
        this.errorMessage = errorMessage;
    }

    public boolean transactionSuccess() {
        return type == ResponseType.SUCCESS;
    }
}
