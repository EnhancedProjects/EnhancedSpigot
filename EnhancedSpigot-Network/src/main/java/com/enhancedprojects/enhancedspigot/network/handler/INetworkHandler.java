package com.enhancedprojects.enhancedspigot.network.handler;

import com.enhancedprojects.enhancedspigot.network.packet.AbstractPacket;

public interface INetworkHandler {
	boolean init();

	void shutdown();

	void sendPacket(AbstractPacket packet);
}
