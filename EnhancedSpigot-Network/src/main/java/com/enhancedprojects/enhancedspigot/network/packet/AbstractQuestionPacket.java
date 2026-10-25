package com.enhancedprojects.enhancedspigot.network.packet;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;

@Getter @Setter public abstract class AbstractQuestionPacket<T extends AbstractAnswerPacket> extends AbstractPacket {
	private boolean askYourself;
	private boolean answerAll;
	private long ttl;
	private long expiresAt;
	private transient int answers;
	private transient int maxAnswers;
	private transient T defaultAnswer;

	public AbstractQuestionPacket() {
		this.askYourself = false;
		this.answerAll = true;
		this.ttl = 60_000L;
		this.expiresAt = 0L;
		this.answers = 0;
		this.maxAnswers = Integer.MAX_VALUE;
		this.defaultAnswer = null;
	}

	@Nullable
	public abstract T answer(JavaPlugin plugin);

	public abstract void handleAnswer(JavaPlugin plugin, T answer);
}
