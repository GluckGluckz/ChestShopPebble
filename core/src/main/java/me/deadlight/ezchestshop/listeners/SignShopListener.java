package me.deadlight.ezchestshop.listeners;

import com.palmergames.bukkit.towny.utils.ShopPlotUtil;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopCommandManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.guis.AdminShopGUI;
import me.deadlight.ezchestshop.guis.NonOwnerShopGUI;
import me.deadlight.ezchestshop.guis.OwnerShopGUI;
import me.deadlight.ezchestshop.guis.ServerShopGUI;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.signs.SignShopDisplay;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import me.deadlight.ezchestshop.utils.worldguard.WorldGuardUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Sign-first PebbleShop UX: [sign] creates/links signs, right-click opens shops, signs are protected. */
public final class SignShopListener implements Listener {

    private final LanguageManager lm = new LanguageManager();
    private final NonOwnerShopGUI nonOwnerShopGUI = new NonOwnerShopGUI();
    private final OwnerShopGUI ownerShopGUI = new OwnerShopGUI();
    private final AdminShopGUI adminShopGUI = new AdminShopGUI();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShopSignCreate(SignChangeEvent event) {
        if (!containsSignTrigger(event)) return;

        Player player = event.getPlayer();
        Block signBlock = event.getBlock();
        Block containerBlock = findContainerForSign(signBlock);
        if (containerBlock == null) {
            paintInvalidSign(event, "No container");
            player.sendMessage(ChatColor.RED + "Place the [sign] sign on or next to a chest, barrel, or shulker shop container.");
            return;
        }

        Location shopLocation = shopLocationFromContainer(containerBlock);
        boolean createdShop = false;
        if (shopLocation == null) {
            shopLocation = createShopFromSign(player, containerBlock);
            createdShop = shopLocation != null;
        } else if (!canManageShopSign(player, shopLocation)) {
            paintInvalidSign(event, "Not owner");
            player.sendMessage(ChatColor.RED + "You do not own this PebbleShop sign target.");
            return;
        }

        if (shopLocation == null) {
            paintInvalidSign(event, "Create failed");
            return;
        }

        paintShopSign(event, shopLocation);

        final Block finalSignBlock = signBlock;
        final Location finalShopLocation = shopLocation;
        final boolean openAfterCreate = createdShop;
        EzChestShop.getScheduler().runTaskLater(EzChestShop.getPlugin(), new Runnable() {
            @Override
            public void run() {
                SignShopDisplay.link(finalSignBlock, finalShopLocation);
                if (openAfterCreate) {
                    openShop(player, finalShopLocation);
                }
            }
        }, 1L);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.35f);
        player.sendMessage(ChatColor.GREEN + "PebbleShop sign ready. " + ChatColor.AQUA + "Right-click it to open the shop.");
    }

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
        event.getPlayer().sendMessage(ChatColor.RED + "Use /pshop remove while looking at this sign to remove the shop.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onShopCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null) return;
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        String[] parts = normalized.split("\\s+");
        if (parts.length < 1 || !isShopCommand(parts[0])) return;

        String sub = parts.length >= 2 ? parts[1] : "";
        Block target = event.getPlayer().getTargetBlockExact(6);
        Location signShopLocation = SignShopDisplay.shopLocationForSign(target);
        if (signShopLocation != null && sub.equals("remove")) {
            event.setCancelled(true);
            removeShopFromSign(event.getPlayer(), signShopLocation);
            return;
        }

        final Location targetShopLocation = signShopLocation != null ? signShopLocation : shopLocationFromContainer(target);
        EzChestShop.getScheduler().runTaskLater(EzChestShop.getPlugin(), new Runnable() {
            @Override
            public void run() {
                if (sub.equals("remove") && targetShopLocation != null && !ShopContainer.isShop(targetShopLocation)) {
                    SignShopDisplay.remove(targetShopLocation);
                } else {
                    SignShopDisplay.syncAll();
                }
            }
        }, 2L);
    }

    private boolean isShopCommand(String command) {
        return command.equals("pshop") || command.equals("ps") || command.equals("pebbleshop")
                || command.equals("peblestore") || command.equals("pebblestore") || command.equals("ecs") || command.equals("cs")
                || command.equals("cshop") || command.equals("chestshop");
    }

    private boolean containsSignTrigger(SignChangeEvent event) {
        for (int i = 0; i < 4; i++) {
            String line = ChatColor.stripColor(event.getLine(i));
            if (line != null && line.trim().equalsIgnoreCase("[sign]")) return true;
        }
        return false;
    }

    private Block findContainerForSign(Block signBlock) {
        if (signBlock == null || !(signBlock.getState() instanceof Sign)) return null;
        BlockData data = signBlock.getBlockData();
        if (data instanceof Directional) {
            Block attached = signBlock.getRelative(((Directional) data).getFacing().getOppositeFace());
            if (Utils.isApplicableContainer(attached)) return attached;
        }

        Block below = signBlock.getRelative(BlockFace.DOWN);
        if (Utils.isApplicableContainer(below)) return below;

        for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block nearby = signBlock.getRelative(face);
            if (Utils.isApplicableContainer(nearby)) return nearby;
        }
        return null;
    }

    private Location shopLocationFromContainer(Block block) {
        Block shopBlock = resolveExistingShopBlock(block);
        if (shopBlock == null) return null;
        if (!ShopContainer.isShop(shopBlock.getLocation()) && shopBlock.getState() instanceof TileState) {
            ShopContainer.loadShop(shopBlock.getLocation(), ((TileState) shopBlock.getState()).getPersistentDataContainer());
        }
        return shopBlock.getLocation();
    }

    private Block resolveExistingShopBlock(Block block) {
        if (block == null) return null;
        if (ShopContainer.isShop(block.getLocation()) || hasShopData(block)) return block;

        Block doubleChestShop = doubleChestShopBlock(block);
        if (doubleChestShop != null) return doubleChestShop;

        EzShop partOfShop = Utils.isPartOfTheChestShop(block.getLocation());
        return partOfShop == null ? null : partOfShop.getLocation().getBlock();
    }

    private Block doubleChestShopBlock(Block block) {
        if (block == null || !(block.getState() instanceof Chest)) return null;
        Inventory inventory = ((Chest) block.getState()).getInventory();
        if (!(inventory instanceof DoubleChestInventory)) return null;
        DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
        Chest left = (Chest) doubleChest.getLeftSide();
        Chest right = (Chest) doubleChest.getRightSide();
        if (hasShopData(left.getBlock()) || ShopContainer.isShop(left.getLocation())) return left.getBlock();
        if (hasShopData(right.getBlock()) || ShopContainer.isShop(right.getLocation())) return right.getBlock();
        return null;
    }

    private boolean hasShopData(Block block) {
        if (block == null || !(block.getState() instanceof TileState)) return false;
        PersistentDataContainer data = ((TileState) block.getState()).getPersistentDataContainer();
        return data.has(new NamespacedKey(EzChestShop.getPlugin(), "owner"), PersistentDataType.STRING);
    }

    private Location createShopFromSign(Player player, Block target) {
        if (target == null || target.getType() == Material.AIR || !(target.getState() instanceof TileState)) {
            player.sendMessage(lm.lookAtChest());
            return null;
        }
        if (!Utils.isApplicableContainer(target)) {
            player.sendMessage(lm.noChest());
            return null;
        }
        if (!checkShopLimit(player, target.getWorld())) return null;
        if (EzChestShop.worldguard && !WorldGuardUtils.queryStateFlag(FlagRegistry.CREATE_SHOP, player)) {
            player.spigot().sendMessage(lm.notAllowedToCreateOrRemove(player));
            return null;
        }
        if (!checkIfLocation(target, player)) {
            player.spigot().sendMessage(lm.notAllowedToCreateOrRemove(player));
            return null;
        }
        if (EzChestShop.towny && Config.towny_integration_shops_only_in_shop_plots) {
            if (!ShopPlotUtil.isShopPlot(target.getLocation())
                    || !(ShopPlotUtil.doesPlayerOwnShopPlot(player, target.getLocation())
                    || ShopPlotUtil.doesPlayerHaveAbilityToEditShopPlot(player, target.getLocation()))) {
                player.spigot().sendMessage(lm.notAllowedToCreateOrRemove(player));
                return null;
            }
        }

        TileState state = (TileState) target.getState();
        PersistentDataContainer container = state.getPersistentDataContainer();
        if (container.has(new NamespacedKey(EzChestShop.getPlugin(), "owner"), PersistentDataType.STRING)
                || doubleChestShopBlock(target) != null) {
            player.sendMessage(lm.alreadyAShop());
            return null;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            player.sendMessage(lm.holdSomething());
            return null;
        }

        ItemStack shopItem = heldItem.clone();
        shopItem.setAmount(1);
        if (Utils.isShulkerBox(shopItem.getType()) && Utils.isShulkerBox(target)) {
            player.sendMessage(lm.invalidShopItem());
            return null;
        }

        double buyPrice = 0D;
        double sellPrice = 0D;
        int isDbuy = 1;
        int isDSell = 1;

        container.set(new NamespacedKey(EzChestShop.getPlugin(), "owner"), PersistentDataType.STRING, player.getUniqueId().toString());
        container.set(new NamespacedKey(EzChestShop.getPlugin(), "buy"), PersistentDataType.DOUBLE, buyPrice);
        container.set(new NamespacedKey(EzChestShop.getPlugin(), "sell"), PersistentDataType.DOUBLE, sellPrice);
        container.set(new NamespacedKey(EzChestShop.getPlugin(), "msgtoggle"), PersistentDataType.INTEGER, Config.settings_defaults_transactions ? 1 : 0);
        container.set(new NamespacedKey(EzChestShop.getPlugin(), "dbuy"), PersistentDataType.INTEGER, isDbuy);
        container.set(new NamespacedKey(EzChestShop.getPlugin(), "dsell"), PersistentDataType.INTEGER, isDSell);
        container.set(new NamespacedKey(EzChestShop.getPlugin(), "admins"), PersistentDataType.STRING, "none");
        container.set(new NamespacedKey(EzChestShop.getPlugin(), "shareincome"), PersistentDataType.INTEGER, Config.settings_defaults_shareprofits ? 1 : 0);
        container.set(new NamespacedKey(EzChestShop.getPlugin(), "adminshop"), PersistentDataType.INTEGER, 0);
        container.set(new NamespacedKey(EzChestShop.getPlugin(), "rotation"), PersistentDataType.STRING, Config.settings_defaults_rotation);

        try {
            Utils.storeItem(shopItem, container);
        } catch (IOException exception) {
            exception.printStackTrace();
            player.sendMessage(ChatColor.RED + "PebbleShop could not save this shop item.");
            return null;
        }

        state.update();
        ShopContainer.createShop(target.getLocation(), player, shopItem, buyPrice, sellPrice, false,
                true, true, "none", true, false, Config.settings_defaults_rotation);
        player.sendMessage(lm.shopCreated());
        player.sendMessage(ChatColor.AQUA + "Buying and selling start disabled. Set prices from the owner GUI before opening this shop to players.");
        return target.getLocation();
    }

    private boolean checkShopLimit(Player player, World world) {
        if (!Config.permissions_create_shop_enabled) return true;
        int maxShopsWorld = Utils.getMaxPermission(player, "ecs.shops.limit." + world.getName() + ".", -2);
        if (maxShopsWorld == -2) {
            int maxShops = Utils.getMaxPermission(player, "ecs.shops.limit.");
            maxShops = maxShops == -1 ? 10000 : maxShops;
            int shops = ShopContainer.getShopCount(player);
            if (shops >= maxShops) {
                player.sendMessage(lm.maxShopLimitReached(maxShops));
                return false;
            }
        } else {
            maxShopsWorld = maxShopsWorld == -1 ? 10000 : maxShopsWorld;
            int shops = ShopContainer.getShopCount(player, world);
            if (shops >= maxShopsWorld) {
                player.sendMessage(lm.maxShopLimitReached(maxShopsWorld));
                return false;
            }
        }
        return true;
    }

    private boolean checkIfLocation(Block target, Player player) {
        BlockBreakEvent newevent = new BlockBreakEvent(target, player);
        Utils.blockBreakMap.put(player.getName(), target);
        Bukkit.getServer().getPluginManager().callEvent(newevent);

        boolean result = true;
        if (!Utils.blockBreakMap.containsKey(player.getName()) || Utils.blockBreakMap.get(player.getName()) != target) {
            result = false;
        }
        if (player.hasPermission("ecs.admin")) {
            result = true;
        }
        Utils.blockBreakMap.remove(player.getName());
        return result;
    }

    private boolean canManageShopSign(Player player, Location shopLocation) {
        Block shopBlock = resolveDoubleChestShopBlock(shopLocation.getBlock());
        if (!(shopBlock.getState() instanceof TileState)) return false;
        PersistentDataContainer data = ((TileState) shopBlock.getState()).getPersistentDataContainer();
        String ownerRaw = data.get(new NamespacedKey(EzChestShop.getPlugin(), "owner"), PersistentDataType.STRING);
        if (ownerRaw == null) return false;
        if (player.hasPermission("ecs.admin") || player.getUniqueId().toString().equalsIgnoreCase(ownerRaw)) return true;
        try {
            return Utils.getAdminsList(data).contains(player.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void paintShopSign(SignChangeEvent event, Location shopLocation) {
        String[] lines = SignShopDisplay.lines(shopLocation);
        for (int i = 0; i < 4; i++) event.setLine(i, lines[i]);
    }

    private void paintInvalidSign(SignChangeEvent event, String reason) {
        event.setLine(0, ChatColor.RED + "PebbleShop");
        event.setLine(1, ChatColor.DARK_RED + "Invalid Sign");
        event.setLine(2, ChatColor.GRAY + reason);
        event.setLine(3, ChatColor.GRAY + "Use [sign]");
    }

    private void removeShopFromSign(Player player, Location shopLocation) {
        Block shopBlock = shopLocation.getBlock();
        if (!(shopBlock.getState() instanceof TileState)) return;
        TileState state = (TileState) shopBlock.getState();
        PersistentDataContainer data = state.getPersistentDataContainer();
        String ownerRaw = data.get(new NamespacedKey(EzChestShop.getPlugin(), "owner"), PersistentDataType.STRING);
        if (ownerRaw == null) return;
        if (!player.hasPermission("ecs.admin") && !player.getUniqueId().toString().equals(ownerRaw)) {
            player.sendMessage(ChatColor.RED + "You do not own this PebbleShop.");
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
        state.update();
        SignShopDisplay.remove(shopLocation);
        ShopContainer.deleteShop(shopLocation);
        player.sendMessage(ChatColor.GREEN + "PebbleShop removed.");
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
