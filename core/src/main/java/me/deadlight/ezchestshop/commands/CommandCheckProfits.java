package me.deadlight.ezchestshop.commands;

import com.google.common.collect.Lists;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.PlayerContainer;
import me.deadlight.ezchestshop.utils.objects.CheckProfitEntry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CommandCheckProfits implements CommandExecutor, Listener, TabCompleter {

    public static LanguageManager lm = new LanguageManager();

    @SuppressWarnings("deprecation")
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(lm.consoleNotAllowed());
            return true;
        }

        Player p = (Player) sender;
        if (!p.hasPermission("ecs.checkprofits")) {
            return true;
        }

        PlayerContainer pc = PlayerContainer.get(p);
        List<CheckProfitEntry> checkprofits = profitEntries(pc);

        if (args.length == 0) {
            int buyAmount = sumBuyAmount(checkprofits);
            double buyCost = sumBuyPrice(checkprofits);
            int sellAmount = sumSellAmount(checkprofits);
            double sellCost = sumSellPrice(checkprofits);
            p.spigot().sendMessage(lm.checkProfitsLandingpage(p, buyCost, buyAmount, sellCost, sellAmount));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            p.spigot().sendMessage(lm.confirmProfitClear());
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("clear") && args[1].equalsIgnoreCase("-confirm")) {
            pc.clearProfits();
            p.sendMessage(lm.confirmProfitClearSuccess());
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("p")) {
            int page = parsePage(args[1]);
            Collections.sort(checkprofits, Comparator.comparingDouble(CommandCheckProfits::netProfit).reversed());

            int linesPerPage = linesPerPage();
            int pages = Math.max(1, (int) Math.ceil(checkprofits.size() / (double) linesPerPage));
            if (page > pages || page < 1) {
                p.sendMessage(lm.wrongInput());
                return true;
            }

            p.spigot().sendMessage(lm.checkProfitsDetailpage(p, checkprofits, page, pages));
            return true;
        }

        p.sendMessage(lm.wrongInput());
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent evt) {
        Player p = evt.getPlayer();
        if (!p.hasPermission("ecs.checkprofits")) {
            return;
        }
        PlayerContainer pc = PlayerContainer.get(p);
        List<CheckProfitEntry> checkprofits = profitEntries(pc);
        if (checkprofits.isEmpty()) {
            return;
        }
        EzChestShop.getScheduler().runTaskLater(() -> p.spigot().sendMessage(lm.joinProfitNotification()), 4L);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> s1 = Arrays.asList("clear", "p");
        List<String> fList = Lists.newArrayList();
        if (sender instanceof Player) {
            Player p = (Player) sender;

            if (args.length == 1) {
                for (String s : s1) {
                    if (s.startsWith(args[0].toLowerCase())) {
                        fList.add(s);
                    }
                }
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("clear")) {
                    if ("-confirm".startsWith(args[1].toLowerCase())) {
                        fList.add("-confirm");
                    }
                } else if (args[0].equalsIgnoreCase("p")) {
                    PlayerContainer pc = PlayerContainer.get(p);
                    List<CheckProfitEntry> checkprofits = profitEntries(pc);
                    int pages = Math.max(1, (int) Math.ceil(checkprofits.size() / (double) linesPerPage()));
                    List<String> range = IntStream.rangeClosed(1, pages).boxed().map(Object::toString)
                            .collect(Collectors.toList());
                    for (String s : range) {
                        if (s.startsWith(args[1])) {
                            fList.add(s);
                        }
                    }
                }
            }
        }
        return fList;
    }

    private static List<CheckProfitEntry> profitEntries(PlayerContainer pc) {
        return pc.getProfits().values().stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.getItem() != null)
                .collect(Collectors.toList());
    }

    private static int parsePage(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static int linesPerPage() {
        return Math.max(1, Config.command_checkprofit_lines_pp);
    }

    private static int sumBuyAmount(List<CheckProfitEntry> entries) {
        return entries.stream().collect(Collectors.summingInt(entry -> safeInt(entry.getBuyAmount())));
    }

    private static double sumBuyPrice(List<CheckProfitEntry> entries) {
        return entries.stream().collect(Collectors.summingDouble(entry -> safeDouble(entry.getBuyPrice())));
    }

    private static int sumSellAmount(List<CheckProfitEntry> entries) {
        return entries.stream().collect(Collectors.summingInt(entry -> safeInt(entry.getSellAmount())));
    }

    private static double sumSellPrice(List<CheckProfitEntry> entries) {
        return entries.stream().collect(Collectors.summingDouble(entry -> safeDouble(entry.getSellPrice())));
    }

    private static double netProfit(CheckProfitEntry entry) {
        return safeDouble(entry.getBuyPrice()) - safeDouble(entry.getSellPrice());
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static double safeDouble(Double value) {
        return value == null ? 0.0D : value;
    }
}
