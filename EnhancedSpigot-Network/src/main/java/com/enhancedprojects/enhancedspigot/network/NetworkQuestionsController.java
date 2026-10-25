package com.enhancedprojects.enhancedspigot.network;

import com.enhancedprojects.enhancedspigot.common.IDependencyProvider;
import com.enhancedprojects.enhancedspigot.common.stereotype.Controller;
import com.enhancedprojects.enhancedspigot.network.packet.AbstractAnswerPacket;
import com.enhancedprojects.enhancedspigot.network.packet.AbstractQuestionPacket;
import com.enhancedprojects.enhancedspigot.util.SchedulerUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class NetworkQuestionsController extends Controller {
	private final NetworkService networkService;
	private final ConcurrentMap<Long, AbstractQuestionPacket<?>> awaitingQuestions;

	public NetworkQuestionsController(JavaPlugin plugin, NetworkService networkService) {
		super(plugin);
		this.networkService = networkService;
		this.awaitingQuestions = new ConcurrentHashMap<>();
	}

	public NetworkQuestionsController(IDependencyProvider dependencyProvider, NetworkService networkService) {
		super(dependencyProvider);
		this.networkService = networkService;
		this.awaitingQuestions = new ConcurrentHashMap<>();
	}

	public void watchQuestion(AbstractQuestionPacket<?> packet) {
		this.awaitingQuestions.put(packet.getId(), packet);
	}

	@SuppressWarnings("unchecked")
	public <T extends AbstractAnswerPacket> void handleAnswer(T packet) {
		if (packet.getAnswerId() == null) return;
		if (!this.awaitingQuestions.containsKey(packet.getAnswerId())) return;

		AbstractQuestionPacket<T> question = (AbstractQuestionPacket<T>) this.awaitingQuestions.get(packet.getAnswerId());
		if (question.getExpiresAt() <= System.currentTimeMillis() || question.getAnswers() >= question.getMaxAnswers()) {
			if (question.getDefaultAnswer() == null) return;
			if (!Objects.equals(
				question.getDefaultAnswer()
					.getId(), packet.getId()
			)) return;
			this.awaitingQuestions.remove(question.getId());
		}

		question.setAnswers(question.getAnswers() + 1);
		question.handleAnswer(this.plugin, packet);
	}

	@Override
	public void close() {

	}

	@Override
	public void reload() {

	}

	@Override
	public void start() {
		SchedulerUtil.runTaskTimerAsynchronously(
			this.plugin, () -> {
				this.awaitingQuestions.forEach((id, packet) -> {
					if (packet.getExpiresAt() <= System.currentTimeMillis() || packet.getAnswers() >= packet.getMaxAnswers()) {
						if (packet.getDefaultAnswer() != null && packet.getAnswers() == 0) {
							this.networkService.sendPacket(packet.getDefaultAnswer());
						} else {
							this.awaitingQuestions.remove(id);
						}
					}
				});
			}, 20L, 20L
		);
	}
}
