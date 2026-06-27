package me.deadlight.ezchestshop.listeners;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.ShopCommandManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.guis.AdminShopGUI;
import me.deadlight.ezchestshop.guis.NonOwnerShopGUI;
import me.deadlight.ezchestshop.guis.OwnerShopGUI;
import me.deadlight.ezchestshop.guis.ServerShopGUI;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.signs.SignShopDisplay;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import me.deadlight.ezchestshop.utils.worldguard.WorldGuardUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Sign-first PebbleShop UX: signs open shops, are protected, and support /pshop remove. */
public final class SignShopListener implements Listener {

    private final NonOwnerShopGUI nonOwnerShopGUI = new NonOwnerShopGUI();
    private final OwnerShopGUI ownerShopGUI = new OwnerShopGUI();
    private final AdminShopGUI adminShopGUI = new AdminShopGUI();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShopSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Location shopLocation = SignShopDisplay.shopLocationForSign(event.getClickedBlock());
        if (shopLocation == null) return;
        event.setCancelled(true);
        openShop(event.getPlayer(), shopLocation);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShopSignBreak(BlockBreakEvent event) {
        Location shopLocation = SignShopDisplay.shopLocationForSign(event.getBlock());
        if (shopLocation == null) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(org.bukkit.ChatColor.RED + "Use /pshop remove while looking at this sign to remove the shop.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSignRemoveCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null) return;
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        String[] parts = normalized.split("\\s+");
        if (parts.length < 2 || !isShopCommand(parts[0]) || !parts[1].equals("remove")) return;

        Block target = event.getPlayer().getTargetBlockExact(6);
        Location shopLocation = SignShopDisplay.shopLocationForSign(target);
        if (shopLocation == null) return;
        event.setCancelled(true);
        removeShopFromSign(event.getPlayer(), shopLocation);
    }

    private boolean isShopCommand(String command) {
        return command.equals("pshop") || command.equals("ps") || command.equals("pebbleshop")
                || command.equals("pebblestore") || command.equals("ecs") || command.equals("cs")
                || command.equals("cshop") || command.equals("chestshop");
    }

    private void removeShopFromSign(Player player, Location shopLocation) {
        Block shopBlock = shopLocation.getBlock();
        if (!(shopBlock.getState() instanceof TileState)) return;
        PersistentDataContainer data = ((TileState) shopBlock.getState()).getPersistentDataContainer();
        String ownerRaw = data.get(new NamespacedKey(EzChestShop.getPlugin(), "owner"), PersistentDataType.STRING);
        if (ownerRaw == null) return;
        if (!player.hasPermission("ecs.admin") && !player.getUniqueId().toString().equals(ownerRaw)) {
            player.sendMessage(org.bukkit.ChatColor.RED + "You do not own this PebbleShop.");
            return;
        }
        data.remove(new NamespacedKey(EzChestShop.getPlugin(), "owner"));
        data.remove(new NamespacedKey(EzChestShop.getPlugin(), "buy"));
        data.remove(new NamespacedKey(EzChestShop.getPlugin(), "sell"));
        data.remove(new NamespacedKey(EzChestShop.getPlugin(), "item"));
        data.remove(new NamespacedKey(EzChestShop.getPlugin(), "msgtoggle"));
        data.remove(new NamespacedKey(EzChestShop.getPlugin(), "dbuy"));
        data.remove(new NamespacedKey(EzChestShop.getPlugin(), "dsell"));
        data.remove(new NamespacedKey(EzChestShop.getPlugin(), "admins"));
        data.remove(new NamespacedKey(EzChestShop.getPlugin(), "shareincome"));
        data.remove(new NamespacedKey(EzChestShop.getPlugin(), "adminshop"));
        data.remove(new NamespacedKey(EzChestShop.getPlugin(), "rotation"));
        ((TileState) shopBlock.getState()).update();
        ShopContainer.deleteShop(shopLocation);
        player.sendMessage(org.bukkit.ChatColor.GREEN + "PebbleShop removed.");
    }

    private void openShop(Player player, Location shopLocation) {
        Block shopBlock = resolveDoubleChestShopBlock(shopLocation.getBlock());
        if (!(shopBlock.getState() instanceof TileState)) return;
        PersistentDataContainer data = ((TileState) shopBlock.getState()).getPersistentDataContainer();
        if (!data.has(new NamespacedKey(EzChestShop.getPlugin(), "owner"), PersistentDataType.STRING)) return;
        if (!ShopContainer.isShop(shopBlock.getLocation())) {
            ShopContainer.loadShop(shopBlock.getLocation(), data);
        }
        boolean isAdminShop = data.get(new NamespacedKey(EzChestShop.getPlugin(), "adminshop"), PersistentDataType.INTEGER) == 1;
        String ownerUuid = data.get(new NamespacedKey(EzChestShop.getPlugin(), "owner"), PersistentDataType.STRING);
        if (isAdminShop) {
            if (EzChestShop.worldguard && !WorldGuardUtils.queryStateFlag(FlagRegistry.USE_ADMIN_SHOP, player) && !player.isOp()) return;
            Config.shopCommandManager.executeCommands(player, shopBlock.getLocation(), ShopCommandManager.ShopType.ADMINSHOP,
                    ShopCommandManager.ShopAction.OPEN, null);
            new ServerShopGUI().showGUI(player, data, shopBlock);
            return;
        }
        boolean isAdmin = isAdmin(data, player.getUniqueId().toString());
        if (EzChestShop.worldguard && !WorldGuardUtils.queryStateFlag(FlagRegistry.USE_SHOP, player) && !player.isOp()
                && !(isAdmin || player.getUniqueId().toString().equalsIgnoreCase(ownerUuid))) {
            return;
        }
        Config.shopCommandManager.executeCommands(player, shopBlock.getLocation(), ShopCommandManager.ShopType.SHOP,
                ShopCommandManager.ShopAction.OPEN, null);
        if (player.hasPermission("ecs.admin") || player.hasPermission("ecs.admin.view")) {
            adminShopGUI.showGUI(player, data, shopBlock);
        } else if (player.getUniqueId().toString().equalsIgnoreCase(ownerUuid) || isAdmin) {
            ownerShopGUI.showGUI(player, data, shopBlock, isAdmin);
        } else {
            nonOwnerShopGUI.showGUI(player, data, shopBlock);
        }
    }

    private Block resolveDoubleChestShopBlock(Block block) {
        Inventory inventory = Utils.getBlockInventory(block);
        if (inventory instanceof DoubleChestInventory) {
            DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
            Chest left = (Chest) doubleChest.getLeftSide();
            Chest right = (Chest) doubleChest.getRightSide();
            NamespacedKey ownerKey = new NamespacedKey(EzChestShop.getPlugin(), "owner");
            if (left.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING)) return left.getBlock();
            if (right.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING)) return right.getBlock();
        }
        return block;
    }

    private boolean isAdmin(PersistentDataContainer data, String uuid) {
        UUID ownerUuid = UUID.fromString(uuid);
        List<UUID> admins = Utils.getAdminsList(data);
        return admins.contains(ownerUuid);
    }
}
