package com.enhancedprojects.enhancedspigot.network.config;

import com.enhancedprojects.enhancedspigot.config.EnhancedConfig;
import com.enhancedprojects.enhancedspigot.config.annotation.Comment;
import com.enhancedprojects.enhancedspigot.config.annotation.Serializer;
import com.enhancedprojects.enhancedspigot.config.serializer.impl.BaseSerializer;
import lombok.Getter;

import java.util.UUID;

@Serializer(BaseSerializer.class) @Getter public class NetworkSettings extends EnhancedConfig {
	@Comment("The network id this plugin is running on. Only servers within the same network can communicate with each other.") private String networkId = "net";
	@Comment("A unique id for this server. This is used to identify the server when sending packets.") private String serverId = "server" + UUID.randomUUID()
		.toString()
		.split("-")[0];
	@Comment({"The network handler type to use. Supported types:", "DISCARD: Discard all packets. Use only when plugin doesn't need to communicate with other servers", "BUNGEE: Bungee messaging channel which sends packets only to servers with at least 1 player, but doesn't require any setup. Use only for simple synchronisation like announcements"}) private NetworkHandlerType handler = NetworkHandlerType.DISCARD;
	// private RedisSettings redis; ...

	public NetworkSettings(NetworkHandlerType handler) {
		this.handler = handler;
	}
}
