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

package com.enhancedprojects.enhancedspigot.plugin;

import com.enhancedprojects.enhancedspigot.command.CommandController;
import com.enhancedprojects.enhancedspigot.command.CommandLocale;
import com.enhancedprojects.enhancedspigot.config.EnhancedConfig;
import com.enhancedprojects.enhancedspigot.data.DatabaseController;
import com.enhancedprojects.enhancedspigot.data.connection.DatabaseOptions;
import com.enhancedprojects.enhancedspigot.data.migration.MigrationController;
import com.enhancedprojects.enhancedspigot.item.ItemController;
import com.enhancedprojects.enhancedspigot.item.recipe.RecipeController;
import com.enhancedprojects.enhancedspigot.locale.EnhancedLocale;
import com.enhancedprojects.enhancedspigot.network.NetworkService;
import com.enhancedprojects.enhancedspigot.network.config.NetworkSettings;
import com.enhancedprojects.enhancedspigot.util.DependencyProvider;
import com.enhancedprojects.enhancedspigot.util.SchedulerUtil;
import com.enhancedprojects.enhancedspigot.util.SemanticVersion;
import com.enhancedprojects.enhancedspigot.util.TryCatchUtil;
import com.enhancedprojects.enhancedspigot.util.leaderboard.LeaderboardController;
import com.enhancedprojects.enhancedspigot.util.leaderboard.LeaderboardData;
import com.enhancedprojects.enhancedspigot.util.listener.ListenerRegistry;
import com.enhancedprojects.enhancedspigot.util.time.EnhancedTime;
import com.enhancedprojects.enhancedspigot.util.updater.IUpdater;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public abstract class EnhancedPlugin extends JavaPlugin {
	@Getter protected DependencyProvider dependencyProvider;

	public static DependencyProvider providerFromJavaPlugin(JavaPlugin plugin) {
		return ((EnhancedPlugin) plugin).getDependencyProvider();
	}

	@Override
	public final void onEnable() {
		TryCatchUtil.usePluginLogger(this);

		this.dependencyProvider = new DependencyProvider();
		this.dependencyProvider.register(this, getClass(), EnhancedPlugin.class, JavaPlugin.class);

		boolean passRequirements = true;
		for (String requiredPlugin : this.requiredPlugins()) {
			if (Bukkit.getPluginManager()
				.getPlugin(requiredPlugin) == null) {
				passRequirements = false;
				this.getLogger()
					.severe("This plugin requires " + requiredPlugin + " to be enabled.");
			}
		}
		if (!passRequirements) {
			Bukkit.getPluginManager()
				.disablePlugin(this);
			return;
		}

		this.load();
		SchedulerUtil.runTaskLater(this, this::postLoad, 1);
	}

	@Override
	public final void onDisable() {
		unload();
		this.dependencyProvider.close();
	}

	public abstract void load();

	public abstract void postLoad();

	public void reload() {
		this.dependencyProvider.reload();
		this.reloadImpl();

		LeaderboardController leaderboardController = this.dependencyProvider.provide(LeaderboardController.class);
		if (leaderboardController != null) {
			leaderboardController.unregisterAll();
			this.leaderboards()
				.forEach((key, value) -> {
					leaderboardController.register(key, value);
					value.refresh(System.currentTimeMillis() + leaderboardController.getRefreshRate()
						.getMillis());
				});
		}
	}

	protected abstract void reloadImpl();

	public abstract void unload();

	protected abstract List<String> requiredPlugins();

	public Map<String, LeaderboardData<?>> leaderboards() {
		return new HashMap<>();
	}

	protected CommandController enableCommands(CommandLocale locale) {
		CommandController controller = new CommandController(this.dependencyProvider, locale);
		controller.start();
		this.dependencyProvider.register(controller);
		return controller;
	}

	protected DatabaseController enableDatabase(DatabaseOptions options) {
		DatabaseController controller = new DatabaseController(this, options);
		if (this.getLogger()
			.getLevel() == Level.FINE) controller.setDebug(true);
		controller.start();
		this.dependencyProvider.register(controller);
		return controller;
	}

	protected MigrationController enableMigrations(int currentVersion, Map<Integer, BiConsumer<JavaPlugin, DatabaseController>> migrations) {
		MigrationController migrationController = new MigrationController(this.dependencyProvider, this.getTag(), currentVersion);
		migrations.forEach(migrationController::registerMigration);
		migrationController.start();
		this.dependencyProvider.register(migrationController);
		return migrationController;
	}

	protected RecipeController enableRecipes() {
		RecipeController controller = new RecipeController(this.dependencyProvider);
		this.dependencyProvider.register(controller);
		return controller;
	}

	protected ItemController enableCustomItems() {
		ItemController controller = new ItemController(this.dependencyProvider);
		this.dependencyProvider.register(controller);
		return controller;
	}

	protected LeaderboardController enableLeaderboards(EnhancedTime refreshRate) {
		LeaderboardController controller = new LeaderboardController(this.dependencyProvider);
		controller.setRefreshRate(refreshRate);
		this.leaderboards()
			.forEach(controller::register);

		SchedulerUtil.runTaskLater(this, controller::start, 200);
		this.dependencyProvider.register(controller);

		return controller;
	}

	protected NetworkService enableNetwork(NetworkSettings settings) {
		NetworkService service = new NetworkService(this.dependencyProvider, settings);
		service.start();
		this.dependencyProvider.register(service);
		return service;
	}

	protected <T extends EnhancedLocale> T useLocale(T locale) {
		locale.start();
		this.dependencyProvider.register(locale);
		return locale;
	}

	protected <T extends EnhancedConfig> T useConfiguration(T config) {
		config.start();
		this.dependencyProvider.register(config);
		return config;
	}

	protected void registerListeners(String listenerPackage) {
		ListenerRegistry.register(this.dependencyProvider, this, listenerPackage);
	}

	protected Metrics useBStats(int serviceId) {
		return new Metrics(this, serviceId);
	}

	protected void useUpdater(IUpdater updater) {
		if (updater.hasUpdate(new SemanticVersion(this.getDescription()
			.getVersion()))) {
			this.getLogger()
				.warning("Detected new version of " + this.getName() + " plugin. Download it on " + updater.getDownloadLink());
		}
	}

	protected void showDebug() {
		DebugLogHandler.attachDebugLogger(this);
		this.getLogger()
			.setLevel(Level.FINE);
	}

	public String getTag() {
		return this.getName()
			.toLowerCase();
	}
}
