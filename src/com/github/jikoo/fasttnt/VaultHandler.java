package com.github.jikoo.fasttnt;

import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

/**
 * Helper class for Vault compatibility.
 * 
 * @author Jikoo
 */
public class VaultHandler {

	private final FastTNT plugin;
	private Economy economy;

	public VaultHandler(FastTNT plugin) {
		this.plugin = plugin;
	}

	public boolean init() {
		RegisteredServiceProvider<Economy> economyProvider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
		if (economyProvider == null) {
			return false;
		}
		economy = economyProvider.getProvider();
		return true;
	}

	public double getBalance(Player player) {
		if (economy == null) {
			return 0;
		}
		return economy.getBalance(player, player.getWorld().getName());
	}

	public boolean withdraw(Player player, double amount) {
		if (economy == null) {
			return false;
		}
		EconomyResponse response = economy.withdrawPlayer(player, player.getWorld().getName(), amount);
		return response.transactionSuccess();
	}
}
