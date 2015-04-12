package com.github.jikoo.fasttnt;

import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Just for fun!
 * 
 * @author Jikoo
 */
public class FastTNT extends JavaPlugin {

	private VaultHandler vaultHandler;

	@Override
	public void onEnable() {
		if (getConfig().getInt("sand-type") > 1 || getConfig().getInt("sand-type") < -1) {
			getConfig().set("sand-type", -1);
		}
		if (getConfig().getDouble("cost-per-tnt", 0) > 0) {
			if (getServer().getPluginManager().isPluginEnabled("Vault")) {
				vaultHandler = new VaultHandler(this);
				if (!vaultHandler.init()) {
					vaultHandler = null;
					getLogger().warning("Vault was hooked, but no valid economy is present. Crafting TNT will not cost anything.");
				} else {
					getLogger().info("Vault hooked! Each TNT crafted will cost " + getConfig().getDouble("cost-per-tnt", 0));
				}
			} else {
				getLogger().warning("Cost per TNT crafted is set, but Vault is not present! Crafting TNT will not cost anything.");
			}
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("FastTNT does not offer console support at this time, sorry!");
			return true;
		}
		Player player = (Player) sender;

		// Total tnt that can be made with the materials in inventory first
		short sandDurability = (short) getConfig().getInt("sand-type", -1);
		int tntSand = sum(player.getInventory().all(Material.SAND), sandDurability) / 4;
		int tntGunpowder = sum(player.getInventory().all(Material.SULPHUR)) / 5;

		// Total amount of quickly-crafted TNT that can be paid for with Vault balance
		int tntMoney = -1;
		if (getConfig().getDouble("cost-per-tnt", 0) > 0 && vaultHandler != null) {
			tntMoney = (int) (vaultHandler.getBalance(player) / getConfig().getDouble("cost-per-tnt"));
		}

		// Set total based on limiting factor
		int tnt = Math.min(tntSand, tntGunpowder);
		if (tntMoney != -1) {
			tnt = Math.min(tnt, tntMoney);
		}

		// Define total quantity that must be removed
		tntSand = tnt * 4;
		tntGunpowder = tnt * 5;

		// Charge player if Vault is present and a cost is set
		if (tntMoney != -1) {
			vaultHandler.withdraw(player, tnt * getConfig().getDouble("cost-per-tnt"));
		}

		// Remove sand from Player
		ItemStack is = new ItemStack(Material.SAND, 64, sandDurability == -1 ? 0 : sandDurability);
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
				// This cannot be hit if sandDurability is not -1, so no need for additional logic
				is = new ItemStack(Material.SAND, failures, (short) 1);
				player.getInventory().removeItem(is);
			}
		}

		// Remove sulpher (gunpowder) from Player
		is.setType(Material.SULPHUR);
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
		String message = getConfig().getString("craft-message", "&aCrafted %tnt% tnt!");
		message = ChatColor.translateAlternateColorCodes('&', message);
		message = message.replace("%tnt%", String.valueOf(tnt));
		player.sendMessage(message);

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
			if (durability == -1 || durability == is.getDurability()) {
				sum += is.getAmount();
			}
		}
		return sum;
	}
}
