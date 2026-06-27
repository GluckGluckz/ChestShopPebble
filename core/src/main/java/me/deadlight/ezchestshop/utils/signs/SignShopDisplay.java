package me.deadlight.ezchestshop.utils.signs;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.objects.ShopSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Sign-backed PebbleShop display. Signs are player-placed with [shop] or [sign]
 * and then linked to the shop container through persistent data.
 */
public final class SignShopDisplay {

    private static final BlockFace[] WALL_FACES = new BlockFace[] {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private SignShopDisplay() {}

    public static NamespacedKey signKey() {
        return new NamespacedKey(EzChestShop.getPlugin(), "shop_sign_for");
    }

    public static void syncAll() {
        for (EzShop shop : ShopContainer.getShops()) {
            sync(shop.getLocation());
        }
    }

    public static void sync(Location shopLocation) {
        if (shopLocation == null || shopLocation.getWorld() == null) return;
        EzShop shop = ShopContainer.getShop(shopLocation);
        if (shop == null) return;
        for (Block block : candidateBlocks(shopLocation)) {
            if (isShopSignFor(block, shopLocation)) {
                writeSign(block, shop);
                return;
            }
        }
    }

    public static void link(Block signBlock, Location shopLocation) {
        if (signBlock == null || shopLocation == null || shopLocation.getWorld() == null) return;
        EzShop shop = ShopContainer.getShop(shopLocation);
        if (shop == null) return;
        writeSign(signBlock, shop);
    }

    public static String[] lines(Location shopLocation) {
        if (shopLocation == null) {
            return fallbackLines();
        }
        EzShop shop = ShopContainer.getShop(shopLocation);
        return shop == null ? fallbackLines() : lines(shop);
    }

    public static void remove(Location shopLocation) {
        if (shopLocation == null || shopLocation.getWorld() == null) return;
        for (Block block : candidateBlocks(shopLocation)) {
            if (isShopSignFor(block, shopLocation)) {
                block.setType(Material.AIR, false);
            }
        }
    }

    public static boolean isShopSign(Block block) {
        return shopLocationForSign(block) != null;
    }

    public static Location shopLocationForSign(Block block) {
        if (block == null || !(block.getState() instanceof Sign)) return null;
        BlockState state = block.getState();
        if (!(state instanceof TileState)) return null;
        String raw = ((TileState) state).getPersistentDataContainer().get(signKey(), PersistentDataType.STRING);
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return Utils.StringtoLocation(raw);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<Block> candidateBlocks(Location shopLocation) {
        List<Block> blocks = new ArrayList<Block>();
        if (shopLocation == null || shopLocation.getWorld() == null) return blocks;
        Block shop = shopLocation.getBlock();
        for (BlockFace face : WALL_FACES) blocks.add(shop.getRelative(face));
        blocks.add(shop.getRelative(BlockFace.UP));
        return blocks;
    }

    private static boolean isShopSignFor(Block block, Location shopLocation) {
        Location linked = shopLocationForSign(block);
        return linked != null && sameBlock(linked, shopLocation);
    }

    private static boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && b.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private static void writeSign(Block signBlock, EzShop shop) {
        BlockState state = signBlock.getState();
        if (!(state instanceof Sign)) return;
        Sign sign = (Sign) state;
        String[] lines = lines(shop);
        for (int i = 0; i < 4; i++) sign.setLine(i, lines[i]);
        if (state instanceof TileState) {
            ((TileState) state).getPersistentDataContainer().set(signKey(), PersistentDataType.STRING,
                    Utils.LocationtoString(shop.getLocation()));
        }
        state.update(true, false);
    }

    private static String[] lines(EzShop shop) {
        ShopSettings settings = shop.getSettings();
        boolean admin = settings != null && settings.isAdminshop();

        return new String[] {
                color(admin ? "&d&lAdmin Shop" : "&b&lPebbleShop"),
                color("&a&lRight Click"),
                color("&fOwner:"),
                color("&b" + compactText(ownerName(shop), 20))
        };
    }

    private static String[] fallbackLines() {
        return new String[] {
                color("&b&lPebbleShop"),
                color("&a&lRight Click"),
                color("&fOwner:"),
                color("&bUnknown")
        };
    }

    private static String ownerName(EzShop shop) {
        if (shop == null || shop.getOwnerID() == null) return "Unknown";
        OfflinePlayer owner = Bukkit.getOfflinePlayer(shop.getOwnerID());
        String name = owner.getName();
        return name == null || name.trim().isEmpty() ? "Unknown" : name.trim();
    }

    private static String compactText(String text, int maxLength) {
        if (text == null) return "Unknown";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private static String color(String raw) {
        return Utils.colorify(raw);
    }
}
