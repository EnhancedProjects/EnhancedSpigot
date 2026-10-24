package com.enhancedprojects.enhancedspigot.network.handler;

import com.enhancedprojects.enhancedspigot.network.packet.AbstractPacket;

public class DiscardHandler implements INetworkHandler {
	@Override
	public boolean init() {return true;}

	@Override
	public void shutdown() {}

	@Override
	public void sendPacket(AbstractPacket packet) {}
}
