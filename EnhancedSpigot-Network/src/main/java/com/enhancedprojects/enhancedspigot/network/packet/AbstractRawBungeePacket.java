package com.enhancedprojects.enhancedspigot.network.packet;

import com.google.common.io.ByteArrayDataOutput;

public abstract class AbstractRawBungeePacket extends AbstractPacket {
	public abstract ByteArrayDataOutput constructBungeeMessage();
}
