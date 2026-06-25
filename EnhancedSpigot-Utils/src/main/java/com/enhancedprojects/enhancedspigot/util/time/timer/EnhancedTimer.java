/*
 * Copyright 2026 KPG-TB
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.enhancedprojects.enhancedspigot.util.time.timer;

import com.enhancedprojects.enhancedspigot.util.SchedulerUtil;
import com.enhancedprojects.enhancedspigot.util.time.EnhancedTime;
import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Getter @Setter public class EnhancedTimer {
	protected final JavaPlugin plugin;

	private final TimerType type;
	private final Set<Integer> sendInterval;
	private final int seconds;
	private final List<UUID> viewers;

	private String timeFormat;
	private String timeEmptyReplace;

	private String tickMessage;
	private String cancelMessage;
	private String endMessage;

	private Consumer<EnhancedTimer> startAction;
	private Consumer<EnhancedTimer> tickAction;
	private Consumer<EnhancedTimer> cancelAction;
	private Consumer<EnhancedTimer> endAction;

	private int timeLeft;
	private SchedulerUtil.Task task;
	private boolean started;
	private boolean ended;

	private BossBar bossBar;
	private BarColor bossBarColor;
	private BarStyle bossBarStyle;
	private TimerType bossBarFinishOutput;

	public EnhancedTimer(JavaPlugin plugin, TimerType type, Set<Integer> sendInterval, int seconds) {
		this.plugin = plugin;
		this.type = type;
		this.sendInterval = sendInterval;
		this.seconds = seconds;

		this.viewers = new ArrayList<>();
		this.bossBarColor = BarColor.BLUE;
		this.bossBarStyle = BarStyle.SOLID;

		setTickMessage("<time>s");
		setCancelMessage("Cancelled");
		setEndMessage("Ended");

		end();
	}

	/**
	 * Set format of timer
	 *
	 * @param timeFormat       {@link EnhancedTime}#as()
	 * @param timeEmptyReplace {@link EnhancedTime}#as()
	 * @return This timer
	 */
	public EnhancedTimer setTimeFormat(String timeFormat, String timeEmptyReplace) {
		this.timeFormat = timeFormat;
		this.timeEmptyReplace = timeEmptyReplace;
		return this;
	}

	/**
	 * Add viewer to timer
	 *
	 * @param player viewer
	 * @return This timer
	 */
	public EnhancedTimer addViewer(Player player) {
		if (!hasViewer(player)) {
			viewers.add(player.getUniqueId());
		}
		return this;
	}

	/**
	 * Remove viewer from timer
	 *
	 * @param player viewer
	 * @return This timer
	 */
	public EnhancedTimer removeViewer(Player player) {
		viewers.remove(player.getUniqueId());
		return this;
	}

	/**
	 * Clear viewers
	 *
	 * @return This timer
	 */
	public EnhancedTimer clearViewers() {
		viewers.clear();
		return this;
	}

	/**
	 * Check if timer contains this viewer
	 *
	 * @param player viewer
	 * @return true if this player is a viewer of this timer
	 */
	public boolean hasViewer(Player player) {
		return viewers.contains(player.getUniqueId());
	}

	/**
	 * Get all viewers of timer
	 *
	 * @return all viewers
	 */
	public List<Player> getViewers() {
		return viewers.stream()
			.map(Bukkit::getPlayer)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
	}

	/**
	 * Start the timer
	 *
	 * @return This timer
	 */
	public EnhancedTimer start() {
		if (started) {
			return this;
		}

		started = true;
		ended = false;
		if (startAction != null) {
			startAction.accept(this);
		}

		if (this.type == TimerType.BOSSBAR) {
			this.bossBar = Bukkit.createBossBar("", this.bossBarColor, this.bossBarStyle);

			this.getViewers()
				.forEach(p -> {
					this.bossBar.addPlayer(p);
				});
		}
		this.task = SchedulerUtil.runTaskTimer(
			this.plugin, (task) -> {
				if (timeLeft <= 0) {
					if (endAction != null) endAction.accept(this);
					this.end();
					this.sendMessageToViewers(this.endMessage);
					return;
				}
				tick();
			}, 0, 20L
		);
		return this;
	}

	/**
	 * Cancel the timer
	 *
	 * @return This timer
	 */
	public EnhancedTimer cancel() {
		if (!started || ended) {
			return this;
		}

		end();
		if (cancelAction != null) cancelAction.accept(this);
		sendMessageToViewers(this.cancelMessage);
		return this;
	}

	private void tick() {
		if (tickAction != null) tickAction.accept(this);

		if (this.type == TimerType.BOSSBAR) {
			float progress = (float) timeLeft / (float) seconds;
			if (progress > 1f) progress = 1f;
			if (progress < 0f) progress = 0f;
			bossBar.setProgress(progress);
		}

		if (sendInterval.contains(timeLeft) || sendInterval.contains(-1)) {
			EnhancedTime time = new EnhancedTime(timeLeft * 1000L);
			String timeStr = timeFormat == null || timeFormat.isEmpty() ?
				time.getText() :
				time.as(timeFormat, timeEmptyReplace);

			sendMessageToViewers(this.tickMessage.replace("<time>", timeStr));
		}
		timeLeft--;
	}

	private void end() {
		started = false;
		ended = true;
		timeLeft = seconds;
		if (this.bossBar != null) this.bossBar.removeAll();
		bossBar = null;
		if (task != null) {
			task.cancel();
		}
	}

	private void sendMessageToViewers(String message) {
		if (message.isEmpty()) return;

		TimerType output;
		if (this.type == TimerType.BOSSBAR && this.bossBar == null && this.bossBarFinishOutput != null) {
			output = this.bossBarFinishOutput;
		} else {
			output = this.type;
		}

		if (output == TimerType.BOSSBAR && this.bossBar != null) {
			this.bossBar.setTitle(message);
			return;
		}

		this.getViewers()
			.forEach(viewer -> {
				switch (output) {
					case MESSAGE:
						viewer.sendMessage(message);
						break;
					case ACTIONBAR:
						viewer.spigot()
							.sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
						break;
					case TITLE:
						String[] elements = message.split("\n", 2);
						String title = elements[0];
						String subTitle = elements.length == 1 ?
							"" :
							elements[1];
						viewer.sendTitle(title, subTitle);
						break;
				}
			});
	}

	public enum TimerType {
		MESSAGE, ACTIONBAR, BOSSBAR, TITLE
	}
}
