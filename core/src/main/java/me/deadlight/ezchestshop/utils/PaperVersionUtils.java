package me.deadlight.ezchestshop.utils;

import me.deadlight.ezchestshop.EzChestShop;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Paper/Bukkit API implementation used by PebbleShop on Pebble Quest Paper 26.1.2.
 *
 * <p>The original upstream plugin used version-specific NMS packet classes. Those classes are
 * fragile across custom Paper builds, so this implementation intentionally avoids direct NMS and
 * CraftBukkit references. Visual helpers are backed by normal Bukkit entities where possible and
 * unsupported packet-only features degrade safely instead of disabling the whole shop plugin.</p>
 */
public class PaperVersionUtils extends VersionUtils {

    private static final Map<Integer, Entity> ENTITIES = new ConcurrentHashMap<>();
    private static final Map<UUID, ChatInputSession> CHAT_SIGN_INPUTS = new ConcurrentHashMap<>();
    private static final AtomicBoolean CHAT_LISTENER_REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean SIGN_WARNING_SENT = new AtomicBoolean(false);
    private static final long INPUT_TIMEOUT_TICKS = 20L * 90L;

    @Override
    String ItemToTextCompoundString(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == null) {
            return "{}";
        }
        try {
            String key = itemStack.getType().getKey().toString();
            int amount = Math.max(1, itemStack.getAmount());
            return "{id:\"" + key + "\",Count:" + amount + "b}";
        } catch (Throwable ignored) {
            return "{}";
        }
    }

    @Override
    int getItemIndex() {
        return 0;
    }

    @Override
    int getArmorStandIndex() {
        return 0;
    }

    @Override
    void destroyEntity(Player player, int entityID) {
        Entity entity = ENTITIES.remove(entityID);
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }

    @Override
    void spawnHologram(Player player, Location location, String line, int ID) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        Entity entity = location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        if (entity instanceof ArmorStand) {
            ArmorStand armorStand = (ArmorStand) entity;
            armorStand.setVisible(false);
            armorStand.setMarker(true);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setCustomName(line);
            armorStand.setCustomNameVisible(true);
        }
        ENTITIES.put(ID, entity);
    }

    @Override
    void spawnFloatingItem(Player player, Location location, ItemStack itemStack, int ID) {
        if (location == null || location.getWorld() == null || itemStack == null) {
            return;
        }

        Item item = location.getWorld().dropItem(location, itemStack.clone());
        item.setGravity(false);
        item.setVelocity(new Vector(0, 0, 0));
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setInvulnerable(true);
        ENTITIES.put(ID, item);
    }

    @Override
    void renameEntity(Player player, int entityID, String name) {
        Entity entity = ENTITIES.get(entityID);
        if (entity != null) {
            entity.setCustomName(name);
        }
    }

    @Override
    void teleportEntity(Player player, int entityID, Location location) {
        Entity entity = ENTITIES.get(entityID);
        if (entity != null && location != null) {
            entity.teleport(location);
        }
    }

    @Override
    void signFactoryListen(SignMenuFactory signMenuFactory) {
        // The legacy sign editor used NMS packet listeners that do not exist on Pebble Quest's
        // Paper 26.1.2 build. Register one chat-based fallback listener instead; each opened sign
        // prompt stores its active Menu in CHAT_SIGN_INPUTS.
        if (CHAT_LISTENER_REGISTERED.compareAndSet(false, true)) {
            Bukkit.getPluginManager().registerEvents(new ChatSignInputListener(), EzChestShop.getPlugin());
            Bukkit.getLogger().log(Level.INFO,
                    "[PebbleShop] Legacy sign menus will use polished chat input on this server build.");
        }
    }

    @Override
    void removeSignMenuFactoryListen(SignMenuFactory signMenuFactory) {
        // Global listener is plugin-owned and is automatically unregistered by Bukkit on disable.
    }

    @Override
    void openMenu(SignMenuFactory.Menu menu, Player player) {
        if (player == null || menu == null) {
            return;
        }
        if (SIGN_WARNING_SENT.compareAndSet(false, true)) {
            Bukkit.getLogger().log(Level.INFO,
                    "[PebbleShop] Packet-based sign menus are unavailable here; using PebbleShop chat prompts instead.");
        }

        ChatInputSession session = new ChatInputSession(menu, inferPrompt(menu), System.currentTimeMillis());
        ChatInputSession oldSession = CHAT_SIGN_INPUTS.put(player.getUniqueId(), session);
        if (oldSession != null) {
            oldSession.menu.getFactory().getInputs().remove(player);
        }
        menu.getFactory().getInputs().put(player, menu);

        player.closeInventory();
        sendPrompt(player, session);
        scheduleExpiry(player.getUniqueId(), session);
    }

    @Override
    public void injectConnection(Player player) {
        // No packet injection is required for the Paper-safe implementation.
    }

    @Override
    public void ejectConnection(Player player) {
        // No packet injection is required for the Paper-safe implementation.
    }

    @Override
    void showOutline(Player player, Block block, int eID) {
        // The upstream outline was packet/NMS based. Skip it safely on 26.1.2 instead of crashing.
        if (EzChestShop.getPlugin() != null) {
            EzChestShop.logDebug("Skipping packet-based block outline on Paper-safe version utilities.");
        }
    }

    private static void sendPrompt(Player player, ChatInputSession session) {
        PromptContext prompt = session.prompt;
        player.sendMessage(Utils.colorify(""));
        player.sendMessage(Utils.colorify("&d&m----------------&r &b" + prompt.title + " &d&m----------------"));
        player.sendMessage(Utils.colorify(prompt.instruction));

        TextComponent cancelLine = new TextComponent(Utils.colorify("&8Type &c/cancel &8or click "));
        TextComponent cancelButton = new TextComponent(Utils.colorify("&c&l[CANCEL]"));
        cancelButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pshop cancelinput"));
        cancelLine.addExtra(cancelButton);
        cancelLine.addExtra(new TextComponent(Utils.colorify(" &8to stop.")));
        player.spigot().sendMessage(cancelLine);

        List<String> visibleLines = cleanPromptLines(session.menu.getText(), prompt.numeric);
        if (!visibleLines.isEmpty()) {
            player.sendMessage(Utils.colorify(prompt.multiline ? "&7Current lines / placeholders:" : "&7Prompt:"));
            for (String line : visibleLines) {
                player.sendMessage(Utils.colorify("&8» &f" + line));
            }
        }

        Utils.sendActionBar(player, "&bPebbleShop input active &8• &7Your next chat message is private");
    }

    private static void sendInvalid(Player player, ChatInputSession session, String reason) {
        player.sendMessage(Utils.colorify("&d[&bPebbleShop&d] &c" + reason));
        player.sendMessage(Utils.colorify("&7Try again, or type &c/cancel &7to stop."));
        Utils.sendActionBar(player, "&cInvalid PebbleShop input &8• &7Try again or /cancel");
        sendMiniReminder(player, session);
    }

    private static void sendMiniReminder(Player player, ChatInputSession session) {
        PromptContext prompt = session.prompt;
        player.sendMessage(Utils.colorify("&8» &7" + stripColors(prompt.instruction)));
    }

    private static void scheduleExpiry(final UUID playerId, final ChatInputSession session) {
        EzChestShop.getScheduler().scheduleSyncDelayedTask(new Runnable() {
            @Override
            public void run() {
                ChatInputSession active = CHAT_SIGN_INPUTS.get(playerId);
                if (active != session) {
                    return;
                }
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    removeInput(player, session);
                    player.sendMessage(Utils.colorify("&d[&bPebbleShop&d] &7Input timed out. Reopen the shop menu when you're ready."));
                    Utils.sendActionBar(player, "&7PebbleShop input timed out");
                } else {
                    CHAT_SIGN_INPUTS.remove(playerId);
                }
            }
        }, INPUT_TIMEOUT_TICKS);
    }

    private static PromptContext inferPrompt(SignMenuFactory.Menu menu) {
        String joined = normalizePrompt(menu.getText());
        boolean numeric = joined.contains("price")
                || joined.contains("amount")
                || joined.contains("buy")
                || joined.contains("sell")
                || joined.contains("max")
                || joined.contains("number");

        if (joined.contains("buy") && joined.contains("price")) {
            return PromptContext.numeric("Update Buy Price", "&eType the new &fbuy price &ein chat. Example: &f1000&e.");
        }
        if (joined.contains("sell") && joined.contains("price")) {
            return PromptContext.numeric("Update Sell Price", "&eType the new &fsell price &ein chat. Example: &f1000&e.");
        }
        if (joined.contains("price")) {
            return PromptContext.numeric("Update Price", "&eType the new price in chat. Example: &f1000&e.");
        }
        if (numeric) {
            return PromptContext.numeric("Enter Amount", "&eType the amount in chat. Example: &f64&e.");
        }

        boolean multiline = menu.getText() != null && menu.getText().size() > 1;
        if (multiline) {
            return new PromptContext("Custom Hologram Message",
                    "&eType your message in chat. Use &f| &eto split lines. Example: &fLine 1 | Line 2&e.",
                    false, true);
        }
        return new PromptContext("PebbleShop Input",
                "&eType your input in chat. Your message will not be shown publicly.", false, false);
    }

    private static String normalizePrompt(List<String> rawLines) {
        if (rawLines == null || rawLines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : rawLines) {
            if (line == null) {
                continue;
            }
            builder.append(' ').append(stripColors(line).toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    private static List<String> cleanPromptLines(List<String> rawLines, boolean numericPrompt) {
        List<String> lines = new ArrayList<>();
        if (rawLines == null) {
            return lines;
        }
        for (String line : rawLines) {
            if (line == null) {
                continue;
            }
            String clean = stripColors(line).trim();
            if (clean.isEmpty()) {
                continue;
            }
            String strippedDecoration = clean.replace("^", "").replace("-", "").replace("_", "").trim();
            if (strippedDecoration.isEmpty()) {
                continue;
            }
            if (numericPrompt && lines.size() >= 2) {
                break;
            }
            lines.add(clean);
        }
        return lines;
    }

    private static String[] chatToSignLines(String message, boolean multiline) {
        String[] lines = new String[SignMenuFactory.SIGN_LINES];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = "";
        }

        if (message == null) {
            return lines;
        }

        if (!multiline) {
            lines[0] = primaryInput(message);
            return lines;
        }

        String[] split = message.split("\\|", SignMenuFactory.SIGN_LINES);
        for (int i = 0; i < split.length && i < lines.length; i++) {
            lines[i] = split[i].trim();
        }
        return lines;
    }

    private static String primaryInput(String message) {
        if (message == null) {
            return "";
        }
        String[] split = message.split("\\|", 2);
        return split.length == 0 ? "" : split[0].trim();
    }

    private static boolean isCancelInput(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("cancel") || normalized.equals("/cancel");
    }

    private static boolean isCancelCommand(String command) {
        if (command == null) {
            return false;
        }
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("/cancel")
                || normalized.equals("/ps cancel")
                || normalized.equals("/pshop cancel")
                || normalized.equals("/pshop cancelinput")
                || normalized.equals("/pebbleshop cancel")
                || normalized.equals("/chestshop cancel")
                || normalized.equals("/cshop cancel");
    }

    private static void removeInput(Player player, ChatInputSession session) {
        if (player == null) {
            return;
        }
        CHAT_SIGN_INPUTS.remove(player.getUniqueId());
        if (session != null && session.menu != null) {
            session.menu.getFactory().getInputs().remove(player);
        }
    }

    private static String stripColors(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.stripColor(Utils.colorify(input));
    }

    private static final class ChatInputSession {
        private final SignMenuFactory.Menu menu;
        private final PromptContext prompt;
        private final long openedAt;

        private ChatInputSession(SignMenuFactory.Menu menu, PromptContext prompt, long openedAt) {
            this.menu = menu;
            this.prompt = prompt;
            this.openedAt = openedAt;
        }
    }

    private static final class PromptContext {
        private final String title;
        private final String instruction;
        private final boolean numeric;
        private final boolean multiline;

        private PromptContext(String title, String instruction, boolean numeric, boolean multiline) {
            this.title = title;
            this.instruction = instruction;
            this.numeric = numeric;
            this.multiline = multiline;
        }

        private static PromptContext numeric(String title, String instruction) {
            return new PromptContext(title, instruction, true, false);
        }
    }

    private static final class ChatSignInputListener implements Listener {

        @EventHandler
        public void onChat(AsyncPlayerChatEvent event) {
            final Player player = event.getPlayer();
            final ChatInputSession session = CHAT_SIGN_INPUTS.get(player.getUniqueId());
            if (session == null) {
                return;
            }

            event.setCancelled(true);
            final String message = event.getMessage();
            EzChestShop.getScheduler().scheduleSyncDelayedTask(new Runnable() {
                @Override
                public void run() {
                    handleInput(player, session, message);
                }
            }, 0);
        }

        @EventHandler
        public void onCommand(PlayerCommandPreprocessEvent event) {
            Player player = event.getPlayer();
            ChatInputSession session = CHAT_SIGN_INPUTS.get(player.getUniqueId());
            if (session == null || !isCancelCommand(event.getMessage())) {
                return;
            }
            event.setCancelled(true);
            cancelInput(player, session);
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            Player player = event.getPlayer();
            ChatInputSession session = CHAT_SIGN_INPUTS.remove(player.getUniqueId());
            if (session != null && session.menu != null) {
                session.menu.getFactory().getInputs().remove(player);
            }
        }

        private void handleInput(Player player, ChatInputSession session, String message) {
            if (player == null || session == null || !player.isOnline()) {
                return;
            }
            if (CHAT_SIGN_INPUTS.get(player.getUniqueId()) != session) {
                return;
            }
            if (isCancelInput(message)) {
                cancelInput(player, session);
                return;
            }

            if (session.prompt.numeric) {
                String input = primaryInput(message);
                if (input.isEmpty() || !Utils.isNumeric(input)) {
                    sendInvalid(player, session, "Please enter a valid number.");
                    return;
                }
            }

            boolean accepted = false;
            try {
                accepted = session.menu.getResponse() == null
                        || session.menu.getResponse().test(player, chatToSignLines(message, session.prompt.multiline));
            } catch (Throwable throwable) {
                Bukkit.getLogger().log(Level.WARNING,
                        "[PebbleShop] Chat input failed for " + player.getName() + ".", throwable);
            }

            if (accepted || session.menu.isForceClose()) {
                removeInput(player, session);
                Utils.sendActionBar(player, "&aPebbleShop input saved");
                return;
            }

            sendInvalid(player, session, "That value was not accepted.");
        }

        private void cancelInput(Player player, ChatInputSession session) {
            removeInput(player, session);
            player.sendMessage(Utils.colorify("&d[&bPebbleShop&d] &cInput cancelled."));
            Utils.sendActionBar(player, "&cPebbleShop input cancelled");
        }
    }
}
