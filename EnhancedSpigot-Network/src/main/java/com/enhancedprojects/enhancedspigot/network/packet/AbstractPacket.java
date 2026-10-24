package com.enhancedprojects.enhancedspigot.network.packet;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter public abstract class AbstractPacket {
	private Long id;
	private String source;
	private List<String> targets;

	public AbstractPacket() {
		this.id = null;
		this.source = null;
		this.targets = null;
	}
}
