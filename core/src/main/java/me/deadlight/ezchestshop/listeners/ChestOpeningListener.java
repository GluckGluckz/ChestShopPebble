package me.deadlight.ezchestshop.listeners;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.ShopCommandManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.guis.MultiItemShopGUI;
import me.deadlight.ezchestshop.utils.BlockOutline;
import me.deadlight.ezchestshop.utils.ShopItemUtils;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import me.deadlight.ezchestshop.utils.worldguard.WorldGuardUtils;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChestOpeningListener implements Listener {

    private final MultiItemShopGUI shopGUI = new MultiItemShopGUI();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestOpening(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Material clickedType = event.getClickedBlock().getType();
        if (!Utils.isApplicableContainer(clickedType)) {
            return;
        }

        Block containerBlock = event.getClickedBlock();
        if (EzChestShop.slimefun && BlockStorage.hasBlockInfo(containerBlock.getLocation())) {
            ShopContainer.deleteShop(containerBlock.getLocation());
            return;
        }

        ResolvedContainer resolved = resolveContainer(containerBlock);
        if (resolved == null || resolved.data == null) {
            return;
        }

        NamespacedKey ownerKey = new NamespacedKey(EzChestShop.getPlugin(), "owner");
        if (!resolved.data.has(ownerKey, PersistentDataType.STRING)) {
            return;
        }

        event.setCancelled(true);
        containerBlock = resolved.block;
        Location location = containerBlock.getLocation();

        if (!ShopContainer.isShop(location)) {
            ShopContainer.loadShop(location, resolved.data);
        }

        // Legacy one-item shops are migrated lazily the first time they are opened.
        // Load the shop first so the inherited inventory helpers can safely resolve
        // single and double-chest storage during migration.
        ShopItemUtils.getOffers(containerBlock);

        hideActiveOutline(event.getPlayer(), location);

        Player player = event.getPlayer();
        String ownerUuid = resolved.data.get(ownerKey, PersistentDataType.STRING);
        boolean adminShop = intFlag(resolved.data, "adminshop");
        boolean shopStaff = isShopStaff(resolved.data, player.getUniqueId());

        if (adminShop) {
            if (EzChestShop.worldguard
                    && !WorldGuardUtils.queryStateFlag(FlagRegistry.USE_ADMIN_SHOP, player)
                    && !player.isOp()) {
                return;
            }
            Config.shopCommandManager.executeCommands(player, location,
                    ShopCommandManager.ShopType.ADMINSHOP, ShopCommandManager.ShopAction.OPEN, null);
            shopGUI.showGUI(player, containerBlock);
            return;
        }

        boolean owner = ownerUuid != null && ownerUuid.equalsIgnoreCase(player.getUniqueId().toString());
        if (EzChestShop.worldguard
                && !WorldGuardUtils.queryStateFlag(FlagRegistry.USE_SHOP, player)
                && !player.isOp()
                && !owner
                && !shopStaff) {
            return;
        }

        Config.shopCommandManager.executeCommands(player, location,
                ShopCommandManager.ShopType.SHOP, ShopCommandManager.ShopAction.OPEN, null);
        shopGUI.showGUI(player, containerBlock);
    }

    private ResolvedContainer resolveContainer(Block clickedBlock) {
        Material type = clickedBlock.getType();
        if (!(clickedBlock.getState() instanceof TileState)) {
            return null;
        }

        TileState clickedState = (TileState) clickedBlock.getState();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return new ResolvedContainer(clickedBlock, clickedState.getPersistentDataContainer());
        }

        // Do not use Utils#getBlockInventory here: that helper intentionally rejects
        // shops which are not loaded into ShopContainer yet. A direct chest inventory
        // lookup is required so either half of a double chest works after a restart.
        Inventory inventory = ((Chest) clickedState).getInventory();
        if (!(inventory instanceof DoubleChestInventory)) {
            return new ResolvedContainer(clickedBlock, clickedState.getPersistentDataContainer());
        }

        DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
        if (doubleChest == null
                || !(doubleChest.getLeftSide() instanceof Chest)
                || !(doubleChest.getRightSide() instanceof Chest)) {
            return new ResolvedContainer(clickedBlock, clickedState.getPersistentDataContainer());
        }

        Chest left = (Chest) doubleChest.getLeftSide();
        Chest right = (Chest) doubleChest.getRightSide();
        NamespacedKey ownerKey = new NamespacedKey(EzChestShop.getPlugin(), "owner");

        if (left.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING)) {
            return new ResolvedContainer(left.getBlock(), left.getPersistentDataContainer());
        }
        if (right.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING)) {
            return new ResolvedContainer(right.getBlock(), right.getPersistentDataContainer());
        }
        return new ResolvedContainer(left.getBlock(), left.getPersistentDataContainer());
    }

    private void hideActiveOutline(Player player, Location location) {
        List<BlockOutline> outlinedShops = new ArrayList<>(Utils.activeOutlines.values());
        for (BlockOutline outline : outlinedShops) {
            if (outline == null || outline.player == null || outline.block == null) {
                continue;
            }
            if (outline.player.getUniqueId().equals(player.getUniqueId())
                    && outline.block.getLocation().equals(location)) {
                outline.hideOutline();
            }
        }
    }

    private boolean isShopStaff(PersistentDataContainer data, UUID uuid) {
        return Utils.getAdminsList(data).contains(uuid);
    }

    private boolean intFlag(PersistentDataContainer data, String name) {
        Integer value = data.get(new NamespacedKey(EzChestShop.getPlugin(), name), PersistentDataType.INTEGER);
        return value != null && value == 1;
    }

    private static final class ResolvedContainer {
        private final Block block;
        private final PersistentDataContainer data;

        private ResolvedContainer(Block block, PersistentDataContainer data) {
            this.block = block;
            this.data = data;
        }
    }
}
