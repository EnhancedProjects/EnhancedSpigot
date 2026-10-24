package com.enhancedprojects.enhancedspigot.network.handler;

import com.enhancedprojects.enhancedspigot.network.NetworkService;
import com.enhancedprojects.enhancedspigot.network.packet.AbstractPacket;
import com.enhancedprojects.enhancedspigot.network.packet.AbstractRawBungeePacket;
import com.enhancedprojects.enhancedspigot.util.gson.GsonAdapterRegistry;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class BungeeHandler implements INetworkHandler, PluginMessageListener {
	private final static String CHANNEL_NAME = "BungeeCord";

	private final JavaPlugin plugin;
	private final NetworkService networkService;
	private final String subchannel;

	public BungeeHandler(JavaPlugin plugin, NetworkService networkService) {
		this.plugin = plugin;
		this.networkService = networkService;
		this.subchannel = "EnhancedSpigot:" + this.networkService.getNetworkId() + ":" + plugin.getName();
	}

	@Override
	public boolean init() {
		Messenger messenger = this.plugin.getServer()
			.getMessenger();
		messenger.registerIncomingPluginChannel(this.plugin, CHANNEL_NAME, this);
		messenger.registerOutgoingPluginChannel(this.plugin, CHANNEL_NAME);
		return true;
	}

	@Override
	public void shutdown() {
		Messenger messenger = this.plugin.getServer()
			.getMessenger();
		messenger.unregisterIncomingPluginChannel(this.plugin, CHANNEL_NAME);
		messenger.unregisterOutgoingPluginChannel(this.plugin, CHANNEL_NAME);
	}

	@Override
	public void sendPacket(AbstractPacket packet) {
		if (packet instanceof AbstractRawBungeePacket) {
			this.plugin.getServer()
				.sendPluginMessage(
					this.plugin, CHANNEL_NAME, ((AbstractRawBungeePacket) packet).constructBungeeMessage()
						.toByteArray()
				);
			return;
		}

		String json = GsonAdapterRegistry.getInstance()
			.getGson()
			.toJson(packet);

		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		out.writeUTF("Forward");
		out.writeUTF("ALL");
		out.writeUTF(this.subchannel);
		out.writeUTF(packet.getClass()
			.getName());
		out.writeUTF(json);
		this.plugin.getServer()
			.sendPluginMessage(this.plugin, CHANNEL_NAME, out.toByteArray());

		this.plugin.getLogger()
			.fine("Sent bungee packet (" + packet.getClass()
				.getName() + ") in " + this.subchannel + " with data (" + json + ")");
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!channel.equalsIgnoreCase("BungeeCord")) return;
		try {
			ByteArrayDataInput in = ByteStreams.newDataInput(message);
			String subchannel = in.readUTF();
			if (!subchannel.equalsIgnoreCase(this.subchannel)) return;

			String className = in.readUTF();
			String json = in.readUTF();

			this.plugin.getLogger()
				.fine("Received bungee packet (" + className + ") in " + this.subchannel + " with data (" + json + ")");

			Class<?> clazz = Class.forName(className);
			AbstractPacket packet = (AbstractPacket) GsonAdapterRegistry.getInstance()
				.getGson()
				.fromJson(json, clazz);
			this.networkService.receivePacket(packet, false);

		} catch (Exception e) {
			this.plugin.getLogger()
				.severe("Failed to handle incoming bungee packet: " + e.getMessage());
		}
	}
}
