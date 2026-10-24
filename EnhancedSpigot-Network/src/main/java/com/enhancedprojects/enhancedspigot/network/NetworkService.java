package com.enhancedprojects.enhancedspigot.network;

import com.enhancedprojects.enhancedspigot.common.IDependencyProvider;
import com.enhancedprojects.enhancedspigot.common.stereotype.Service;
import com.enhancedprojects.enhancedspigot.network.config.NetworkHandlerType;
import com.enhancedprojects.enhancedspigot.network.config.NetworkSettings;
import com.enhancedprojects.enhancedspigot.network.handler.BungeeHandler;
import com.enhancedprojects.enhancedspigot.network.handler.DiscardHandler;
import com.enhancedprojects.enhancedspigot.network.handler.INetworkHandler;
import com.enhancedprojects.enhancedspigot.network.packet.AbstractAnswerPacket;
import com.enhancedprojects.enhancedspigot.network.packet.AbstractPacket;
import com.enhancedprojects.enhancedspigot.network.packet.AbstractQuestionPacket;
import com.enhancedprojects.enhancedspigot.network.packet.AbstractRawBungeePacket;
import com.enhancedprojects.enhancedspigot.network.packet.IServerPacket;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

public class NetworkService extends Service {
	private final NetworkSettings settings;
	private final NetworkQuestionsController questionsController;
	private BungeeHandler bungeeHandler;
	private INetworkHandler packetHandler;

	public NetworkService(IDependencyProvider dependencyProvider, NetworkSettings settings) {
		super(dependencyProvider);
		this.settings = settings;
		this.questionsController = new NetworkQuestionsController(dependencyProvider, this);
	}

	public NetworkService(JavaPlugin plugin, NetworkSettings settings) {
		super(plugin);
		this.settings = settings;
		this.questionsController = new NetworkQuestionsController(plugin, this);
	}

	public void sendPacket(AbstractPacket packet) {
		if (packet.getId() != null) throw new IllegalStateException("Packet has been already sent before");

		packet.setId(System.currentTimeMillis());
		packet.setSource(this.settings.getServerId());
		if (packet instanceof AbstractQuestionPacket<?>) {
			AbstractQuestionPacket<?> question = (AbstractQuestionPacket<?>) packet;

			question.setExpiresAt(System.currentTimeMillis() + question.getTtl());
			if (question.getDefaultAnswer() != null) question.getDefaultAnswer()
				.setAnswerId(packet.getId());
			this.questionsController.watchQuestion(question);
		}

		this.receivePacket(packet, true);
		this.packetHandler.sendPacket(packet);
	}

	public void sendRawBungeePacket(AbstractRawBungeePacket rawBungeePacket) {
		this.bungeeHandler.sendPacket(rawBungeePacket);
	}

	public void receivePacket(AbstractPacket packet, boolean internal) {
		if (packet.getId() == null || packet.getSource() == null) return;
		if (packet.getTargets() != null && !packet.getTargets()
			.contains(this.getServerId())) return;

		if (packet instanceof IServerPacket) {
			((IServerPacket) packet).handleServer(this.plugin);
		}

		if (!internal && packet instanceof AbstractQuestionPacket<?>) {
			AbstractQuestionPacket<?> question = (AbstractQuestionPacket<?>) packet;

			AbstractAnswerPacket answer = question.answer(this.plugin);
			if (answer != null) {
				answer.setAnswerId(packet.getId());
				if (!question.isAnswerAll()) answer.setTargets(Arrays.asList(packet.getSource()));

				this.sendPacket(answer);
			}
		}

		if (packet instanceof AbstractAnswerPacket) {
			this.questionsController.handleAnswer((AbstractAnswerPacket) packet);
		}
	}

	public String getNetworkId() {
		return this.settings.getNetworkId();
	}

	public String getServerId() {
		return this.settings.getServerId();
	}

	@Override
	public void close() {
		this.bungeeHandler.shutdown();
		if (this.settings.getHandler() != NetworkHandlerType.BUNGEE) this.packetHandler.shutdown();
	}

	@Override
	public void reload() {

	}

	@Override
	public void start() {
		this.bungeeHandler = new BungeeHandler(this.plugin, this);
		this.bungeeHandler.init();

		switch (this.settings.getHandler()) {
			case DISCARD:
				this.packetHandler = new DiscardHandler();
				break;
			case BUNGEE:
				this.packetHandler = this.bungeeHandler;
				break;
		}

		if (this.settings.getHandler() != NetworkHandlerType.BUNGEE) this.packetHandler.init();
	}
}
