package com.enhancedprojects.enhancedspigot.network.packet;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Network packet which will be handled on the server side
 * handleServer() method will be called on each server (even the sending one)
 */
public interface IServerPacket {
	void handleServer(JavaPlugin plugin);
}
