package com.frosthex.timingsystem.restapi.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import com.frosthex.timingsystem.restapi.TimingSystemRESTApiPlugin;
import com.frosthex.timingsystem.restapi.utils.Messager;

public class TimingSystemRestApiCommand implements CommandExecutor {

	@SuppressWarnings("deprecation")
	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
			@NotNull String[] args) {
	
		if (command.getName().equalsIgnoreCase("timingsystemrestapi")) {
			if (!sender.hasPermission("timingsystemrestapi.timingsystemrestapi")) {
				Messager.msgSender("&cSorry you don't have permission to use that command.", sender);
				return true;
			}
			
			Messager.msgSender("&6TimingSystemRESTApi version " + TimingSystemRESTApiPlugin.getInstance().getDescription().getVersion() + ".", sender);
			return true;
		}
		
		return false;
	}

}
