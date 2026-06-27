package me.deadlight.ezchestshop.utils.signs;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.objects.ShopSettings;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Replaces PebbleShop's floating hologram display with physical, protected signs.
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
        Block signBlock = findOrCreateSign(shop);
        if (signBlock == null) return;
        writeSign(signBlock, shop);
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

    private static Block findOrCreateSign(EzShop shop) {
        Location shopLocation = shop.getLocation();
        for (Block block : candidateBlocks(shopLocation)) {
            if (isShopSignFor(block, shopLocation)) return block;
        }

        Block preferred = preferredWallBlock(shop);
        if (preferred != null && preferred.getType() == Material.AIR) {
            BlockFace face = faceFromShopToSign(shopLocation.getBlock(), preferred);
            if (placeWallSign(preferred, face)) return preferred;
        }

        for (BlockFace face : orderedFaces(shop)) {
            Block candidate = shopLocation.getBlock().getRelative(face);
            if (candidate.getType() == Material.AIR && placeWallSign(candidate, face)) return candidate;
        }

        Block top = shopLocation.getBlock().getRelative(BlockFace.UP);
        if (top.getType() == Material.AIR && placeStandingSign(top)) return top;
        return null;
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

    private static Block preferredWallBlock(EzShop shop) {
        String rotation = shop.getSettings() == null ? null : shop.getSettings().getRotation();
        BlockFace face = faceForRotation(rotation);
        return face == null ? null : shop.getLocation().getBlock().getRelative(face);
    }

    private static BlockFace[] orderedFaces(EzShop shop) {
        String rotation = shop.getSettings() == null ? null : shop.getSettings().getRotation();
        BlockFace preferred = faceForRotation(rotation);
        if (preferred == null) return WALL_FACES;
        List<BlockFace> faces = new ArrayList<BlockFace>();
        faces.add(preferred);
        for (BlockFace face : WALL_FACES) {
            if (face != preferred) faces.add(face);
        }
        return faces.toArray(new BlockFace[faces.size()]);
    }

    private static BlockFace faceForRotation(String rotation) {
        if (rotation == null) return null;
        String value = rotation.toLowerCase();
        if (value.equals("north")) return BlockFace.NORTH;
        if (value.equals("east")) return BlockFace.EAST;
        if (value.equals("south")) return BlockFace.SOUTH;
        if (value.equals("west")) return BlockFace.WEST;
        return null;
    }

    private static BlockFace faceFromShopToSign(Block shopBlock, Block signBlock) {
        if (signBlock.getX() < shopBlock.getX()) return BlockFace.WEST;
        if (signBlock.getX() > shopBlock.getX()) return BlockFace.EAST;
        if (signBlock.getZ() < shopBlock.getZ()) return BlockFace.NORTH;
        if (signBlock.getZ() > shopBlock.getZ()) return BlockFace.SOUTH;
        return BlockFace.NORTH;
    }

    private static boolean placeWallSign(Block block, BlockFace face) {
        try {
            block.setType(Material.OAK_WALL_SIGN, false);
            BlockData data = block.getBlockData();
            if (data instanceof Directional) {
                ((Directional) data).setFacing(face);
                block.setBlockData(data, false);
            }
            return true;
        } catch (Throwable throwable) {
            EzChestShop.getPlugin().getLogger().log(Level.WARNING, "Could not place PebbleShop wall sign at " + block.getLocation(), throwable);
            return false;
        }
    }

    private static boolean placeStandingSign(Block block) {
        try {
            block.setType(Material.OAK_SIGN, false);
            BlockData data = block.getBlockData();
            if (data instanceof Rotatable) {
                ((Rotatable) data).setRotation(BlockFace.SOUTH);
                block.setBlockData(data, false);
            }
            return true;
        } catch (Throwable throwable) {
            EzChestShop.getPlugin().getLogger().log(Level.WARNING, "Could not place PebbleShop standing sign at " + block.getLocation(), throwable);
            return false;
        }
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
        boolean buyDisabled = settings != null && settings.isDbuy();
        boolean sellDisabled = settings != null && settings.isDsell();
        String item = compactItemName(shop.getShopItem());
        String buy = buyDisabled ? "OFF" : Config.currency + Utils.formatNumber(shop.getBuyPrice(), Utils.FormatType.CHAT);
        String sell = sellDisabled ? "OFF" : Config.currency + Utils.formatNumber(shop.getSellPrice(), Utils.FormatType.CHAT);

        return new String[] {
                color(admin ? "&d✦ ADMIN SHOP ✦" : "&b✦ PEBBLE SHOP ✦"),
                color("&f" + item),
                color("&aBUY &8» &2" + buy),
                color("&cSELL &8» &4" + sell)
        };
    }

    private static String compactItemName(ItemStack item) {
        String name = item == null ? "Unknown Item" : ChatColor.stripColor(Utils.getFinalItemName(item));
        if (name == null || name.trim().isEmpty()) name = "Unknown Item";
        name = name.trim();
        return name.length() > 20 ? name.substring(0, 20) : name;
    }

    private static String color(String raw) {
        return Utils.colorify(raw);
    }
}
