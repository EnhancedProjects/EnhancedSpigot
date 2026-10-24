package com.enhancedprojects.enhancedspigot.network.packet;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter public abstract class AbstractAnswerPacket extends AbstractPacket {
	private Long answerId;

	public AbstractAnswerPacket() {
		this.answerId = null;
	}
}
