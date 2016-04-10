package com.github.jikoo.fasttnt;

import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Just for fun!
 * 
 * @author Jikoo
 */
public class FastTNT extends JavaPlugin implements Listener {

	private VaultHandler vaultHandler;
	private String messageSuccess, messageFailure, messageInvalid;
	private short sandType;
	private double costPerTNT;

	@Override
	public void onEnable() {
		sandType = (short) Math.max(Math.min(this.getConfig().getInt("sand-type", -1), 1), -1);
		messageSuccess = this.getConfig().getString("lang.success", "&aCrafted %tnt% tnt!");
		messageSuccess = ChatColor.translateAlternateColorCodes('&', messageSuccess);
		messageFailure = this.getConfig().getString("lang.failure",
				"&cYou do not have enough %cause% to craft any TNT.");
		messageFailure = ChatColor.translateAlternateColorCodes('&', messageFailure);
		messageInvalid = this.getConfig().getString("lang.invalid",
				"&cUse /fasttnt [number] to craft an amount or no arguments for as many as possible.");
		messageInvalid = ChatColor.translateAlternateColorCodes('&', messageInvalid);
		costPerTNT = this.getConfig().getDouble("cost-per-tnt", 0);

		if (costPerTNT > 0) {
			this.getServer().getPluginManager().registerEvents(this, this);
			updateVaultHandler();
		}
	}

	@Override
	public void onDisable() {
		vaultHandler = null;
		HandlerList.unregisterAll((Listener) this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("reload")
				&& sender.hasPermission("fasttnt.reload")) {
			this.reloadConfig();
			this.onDisable();
			this.onEnable();
			sender.sendMessage("FastTNT reloaded!");
			return true;
		}
		if (!(sender instanceof Player)) {
			sender.sendMessage("/fasttnt reload");
			return true;
		}

		int tnt;
		if (args.length > 0) {
			try {
				tnt = Integer.valueOf(args[0]);
				if (tnt < 1) {
					// No need to bother with logic, same result.
					sender.sendMessage(messageSuccess.replace("%tnt%", String.valueOf(tnt)));
					return true;
				}
			} catch (NumberFormatException exception) {
				sender.sendMessage(messageInvalid);
				return true;
			}
		} else {
			tnt = Integer.MAX_VALUE;
		}

		Player player = (Player) sender;

		// Maximum TNT that could be crafted with the amount of sand the player has
		int tntSand = sum(player.getInventory().all(Material.SAND), sandType) / 4;
		if (tntSand < 1) {
			player.sendMessage(messageFailure.replace("%cause%", this.getConfig().getString("lang.sand", "sand")));
			return true;
		}

		// Set total to limiting factor
		tnt = Math.min(tnt, tntSand);

		// Maximum TNT that could be crafted with the amount of gunpowder the player has
		int tntGunpowder = sum(player.getInventory().all(Material.SULPHUR)) / 5;
		if (tntGunpowder < 1) {
			player.sendMessage(messageFailure.replace("%cause%", this.getConfig().getString("lang.gunpowder", "gunpowder")));
			return true;
		}

		// Next potential limiting factor
		tnt = Math.min(tnt, tntGunpowder);

		// Total amount of quickly-crafted TNT that can be paid for with Vault balance
		int tntMoney = -1;
		if (vaultHandler != null && costPerTNT > 0) {
			tntMoney = (int) (vaultHandler.getBalance(player) / costPerTNT);
			if (tntMoney < 1) {
				player.sendMessage(messageFailure.replace("%cause%", this.getConfig().getString("lang.money", "money")));
				return true;
			}
			// Last potential limiting factor
			tnt = Math.min(tnt, tntMoney);
		}

		// Define total quantity that must be removed
		tntSand = tnt * 4;
		tntGunpowder = tnt * 5;

		// Charge player if Vault is present and a cost is set
		if (tntMoney != -1) {
			vaultHandler.withdraw(player, tnt * costPerTNT);
		}

		// Remove sand from Player
		ItemStack is = new ItemStack(Material.SAND, 64, sandType == -1 ? 0 : sandType);
		while (tntSand > 0) {
			if (tntSand <= 64) {
				is.setAmount(tntSand);
				tntSand = 0;
			} else {
				tntSand -= 64;
			}
			int failures = sum(player.getInventory().removeItem(is));
			if (failures > 0) {
				// If the player has no plain sand left, they must have red sand.
				// Because our sum method discounts stacks with meta, this cannot be hit unless sandType is -1
				is = new ItemStack(Material.SAND, failures, (short) 1);
				player.getInventory().removeItem(is);
				is.setAmount(64);
			}
		}

		// Remove sulpher (gunpowder) from Player
		is = new ItemStack(Material.SULPHUR, 64);
		is.setAmount(64);
		while (tntGunpowder > 0) {
			if (tntGunpowder <= 64) {
				is.setAmount(tntGunpowder);
				tntGunpowder = 0;
			} else {
				tntGunpowder -= 64;
			}
			player.getInventory().removeItem(is);
		}

		// Tell 'em like it is.
		player.sendMessage(messageSuccess.replace("%tnt%", String.valueOf(tnt)));

		// Add TNT to Player
		is.setType(Material.TNT);
		is.setAmount(64);
		while (tnt > 0) {
			if (tnt <= 64) {
				is.setAmount(tnt);
				tnt = 0;
			} else {
				// In case of a massive addition failure, reset stack to 64 each round
				is.setAmount(64);
				tnt -= 64;
			}
			int failure = sum(player.getInventory().addItem(is));
			// Hypothetical situation: Player has 7 sand, 6 gunpowder, inv is full of non-tnt items.
			// Drop items at Player location rather than just delete resources.
			is.setAmount(failure);
			if (failure > 0) {
				player.getWorld().dropItem(player.getLocation(), is);
			}
		}

		return true;
	}

	private int sum(HashMap<Integer, ? extends ItemStack> hashMap) {
		return sum(hashMap, (short) -1);
	}

	private int sum(HashMap<Integer, ? extends ItemStack> hashMap, short durability) {
		int sum = 0;
		for (ItemStack is : hashMap.values()) {
			if (!is.hasItemMeta() && (durability == -1 || durability == is.getDurability())) {
				sum += is.getAmount();
			}
		}
		return sum;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPluginEnable(PluginEnableEvent event) {
		if (costPerTNT > 0) {
			updateVaultHandler();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPluginDisable(PluginDisableEvent event) {
		if (costPerTNT > 0) {
			updateVaultHandler();
		}
	}

	private void updateVaultHandler() {
		new BukkitRunnable() {
			@Override
			public void run() {
				if (!FastTNT.this.getServer().getPluginManager().isPluginEnabled("Vault")) {
					return;
				}
				if (vaultHandler == null) {
					vaultHandler = new VaultHandler(FastTNT.this);
				}
				if (!vaultHandler.init()) {
					vaultHandler = null;
				}
			}
		}.runTask(this);
	}

}
