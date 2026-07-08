package com.darkfactions.commands;

// Handles power/elixir/shop economy subcommands.

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;

import org.bukkit.entity.Player;

public class FactionEconomyCommands extends AbstractFactionSubcommand {

    public FactionEconomyCommands(DarkFactions plugin) {
        super(plugin);
    }

    // ==========================================
    // POWER - /f power
    // Shows the faction's current power
    // ==========================================
    boolean handlePower(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        double totalPower = plugin.getPowerManager().getEffectiveFactionPower(faction.getFactionId());
        double playerPower = plugin.getPowerManager().getPlayerPower(player.getUniqueId());

        player.sendMessage(msg.header("Faction Power"));
        player.sendMessage(msg.info("Total Power: " + String.format("%.1f", totalPower)));
        player.sendMessage(msg.info("Your Power: " + String.format("%.1f", playerPower)));
        player.sendMessage(msg.info("Max Power: " + String.format("%.1f",
                plugin.getPowerManager().getFactionMaxPower(faction.getFactionId()))));

        if (plugin.getPowerManager().isFactionRaidable(faction.getFactionId())) {
            player.sendMessage(msg.warning("Your faction is raidable! Power is too low!"));
        } else {
            player.sendMessage(msg.success("Your faction is protected!"));
        }

        return true;
    }

    // ==========================================
    // ELIXIR - /f elixir
    // Shows the faction's elixir balance
    // ==========================================
    boolean handleElixir(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        double elixir = plugin.getElixirManager().getFactionElixir(faction.getFactionId());

        player.sendMessage(msg.header("Faction Elixir"));
        player.sendMessage(msg.info("Balance: " + String.format("%.0f", elixir) + " Elixir"));

        if (plugin.getElixirManager().claimPendingElixir(player.getUniqueId())) {
            player.sendMessage(msg.success("Daily elixir bonus claimed!"));
        }

        return true;
    }

    // ==========================================
    // ELIXIR BAL - /f bal <faction>
    // Check another faction's elixir
    // ==========================================
    boolean handleElixirBal(Player player, String[] args) {

        Faction faction;

        if (args.length >= 2) {
            faction = plugin.getFactionManager().getFactionByName(args[1]);
        } else {
            faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        }

        if (faction == null) {
            player.sendMessage(msg.error("Faction not found!"));
            return true;
        }

        double elixir = plugin.getElixirManager().getFactionElixir(faction.getFactionId());
        player.sendMessage(msg.info(faction.getName() + " has " + String.format("%.0f", elixir) + " Elixir."));

        return true;
    }

    // ==========================================
    // SHOP - /f shop
    // Buy faction upgrades with elixir
    // ==========================================
    boolean handleShop(Player player, String[] args) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only the leader and officers can use the shop!")) return true;

        if (args.length >= 3) {
            String item = args[1].toLowerCase();
            String amount = args[2];
            switch (item) {
                case "power":
                    handleShopPower(player, faction, amount);
                    return true;
                case "maxpower":
                    handleShopMaxPower(player, faction, amount);
                    return true;
                default:
                    player.sendMessage(msg.error("Unknown shop item! Use /f shop to see items."));
                    return true;
            }
        }

        double elixir = faction.getElixir();
        double powerCost = plugin.getConfigManager().getShopPowerCost();
        double maxPowerCost = plugin.getConfigManager().getShopMaxPowerCost();
        double maxPowerAmount = plugin.getConfigManager().getShopMaxPowerAmount();
        player.sendMessage(msg.header("Faction Shop"));
        player.sendMessage(msg.info("Balance: " + String.format("%.0f", elixir) + " Elixir"));
        player.sendMessage(msg.help("/f shop power [amount]",
                "Boost faction power (" + String.format("%.0f", powerCost) + " elixir per 1 power)"));
        player.sendMessage(msg.help("/f shop maxpower [amount]",
                "Increase max power (" + String.format("%.0f", maxPowerCost) + " elixir per "
                        + String.format("%.0f", maxPowerAmount) + " max power)"));

        return true;
    }

    void handleShopPower(Player player, Faction faction, String amountStr) {
        try {
            int amount = Integer.parseInt(amountStr);
            if (amount <= 0) { player.sendMessage(msg.error("Amount must be positive!")); return; }
            double cost = amount * plugin.getConfigManager().getShopPowerCost();
            if (plugin.getElixirManager().boostFactionPower(faction.getFactionId(), cost, amount)) {
                player.sendMessage(msg.success("Boosted faction power by " + amount + " for " + String.format("%.0f", cost) + " elixir!"));
            } else {
                player.sendMessage(msg.error("Not enough elixir! Need " + String.format("%.0f", cost)));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(msg.error("Invalid amount!"));
        }
    }

    void handleShopMaxPower(Player player, Faction faction, String amountStr) {
        try {
            int amount = Integer.parseInt(amountStr);
            if (amount <= 0) { player.sendMessage(msg.error("Amount must be positive!")); return; }
            double cost = amount * plugin.getConfigManager().getShopMaxPowerCost();
            double powerBoost = amount * plugin.getConfigManager().getShopMaxPowerAmount();
            if (plugin.getElixirManager().increaseMaxPower(faction.getFactionId(), cost, powerBoost)) {
                player.sendMessage(msg.success("Increased max power by " + String.format("%.0f", powerBoost) + " for " + String.format("%.0f", cost) + " elixir!"));
            } else {
                player.sendMessage(msg.error("Not enough elixir! Need " + String.format("%.0f", cost)));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(msg.error("Invalid amount!"));
        }
    }

    // ==========================================
    // ELIXIR TRANSFER - /f transfer <target> <amount>
    // Send elixir to another faction
    // ==========================================
    boolean handleElixirTransfer(Player player, String[] args) {

        if (!requireArgs(player, args, 3, "/f transfer <faction> <amount>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireLeader(player, faction, "Only the leader can transfer elixir!")) return true;

        Faction target = requireFactionByName(player, args[1]);
        if (target == null) return true;

        if (target.getFactionId().equals(faction.getFactionId())) {
            player.sendMessage(msg.error("You can't transfer elixir to yourself!"));
            return true;
        }

        try {
            double amount = Double.parseDouble(args[2]);
            if (amount <= 0) { player.sendMessage(msg.error("Amount must be positive!")); return true; }
            if (plugin.getElixirManager().transferElixir(faction.getFactionId(), target.getFactionId(), amount)) {
                player.sendMessage(msg.success("Transferred " + String.format("%.0f", amount) + " elixir to " + target.getName() + "!"));
            } else {
                player.sendMessage(msg.error("Transfer failed! Not enough elixir or transfers are disabled."));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(msg.error("Invalid amount!"));
        }

        return true;
    }
}
