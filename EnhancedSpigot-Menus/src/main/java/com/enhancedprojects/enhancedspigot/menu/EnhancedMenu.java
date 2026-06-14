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

package com.enhancedprojects.enhancedspigot.menu;

import com.enhancedprojects.enhancedspigot.menu.container.MenuContainer;
import com.enhancedprojects.enhancedspigot.menu.item.MenuItem;
import com.enhancedprojects.enhancedspigot.menu.nms.InventoryHelperUtil;
import com.enhancedprojects.enhancedspigot.util.DependencyProvider;
import com.enhancedprojects.enhancedspigot.util.Pair;
import com.enhancedprojects.enhancedspigot.util.SchedulerUtil;
import com.enhancedprojects.enhancedspigot.util.TryCatchUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * EnhancedMenu handles menu making process in plugin
 */
@Getter @Setter public abstract class EnhancedMenu implements Listener {
	protected final DependencyProvider provider;
	protected final JavaPlugin plugin;

	protected final Long id;
	protected final int rows;
	protected final List<MenuContainer> containers;

	protected final Inventory bukkitInventory;
	private BiConsumer<InventoryClickEvent, MenuItem.ClickLocation> globalClickAction;
	private Consumer<InventoryDragEvent> globalDragAction;
	private Consumer<InventoryCloseEvent> closeAction;
	private boolean updateItems;

	public EnhancedMenu(String title, int rows, JavaPlugin plugin) {
		this.provider = null;
		this.plugin = plugin;

		this.id = System.currentTimeMillis();
		this.rows = rows;
		this.containers = new ArrayList<>();
		this.updateItems = false;

		this.bukkitInventory = this.register(title);
	}

	public EnhancedMenu(String title, int rows, DependencyProvider provider) {
		this.provider = provider;
		this.plugin = provider.provide(JavaPlugin.class);

		this.id = System.currentTimeMillis();
		this.rows = rows;
		this.containers = new ArrayList<>();
		this.updateItems = false;

		this.bukkitInventory = this.register(title);
	}

	protected Inventory register(String title) {
		if (this.rows > 6 || this.rows < 1) {
			throw new IllegalArgumentException("rows must be between 1 and 6");
		}

		Bukkit.getPluginManager()
			.registerEvents(this, this.plugin);

		return new EnhancedMenuHolder(this, (this.rows * 9), title).getInventory();
	}

	/**
	 * Block global click and drag
	 */
	public void blockClick() {
		this.setGlobalClickAction((e, loc) -> e.setCancelled(true));
		this.setGlobalDragAction(e -> e.setCancelled(true));
	}

	/**
	 * Add container to GUI and auto update this GUI
	 *
	 * @param container Container that is a part of this GUI
	 */
	public void addContainer(MenuContainer container) {
		if (container.getMenu() != this) {
			throw new IllegalArgumentException("Container isn't a part of this menu");
		}
		this.containers.add(container);
		this.update();
	}

	/**
	 * Remove container from GUI
	 *
	 * @param container Container that is a part of this GUI
	 */
	public void removeContainer(MenuContainer container) {
		this.containers.remove(container);
		this.update();
	}

	/**
	 * Reset containers
	 */
	public void resetContainers() {
		this.containers.clear();
		this.update();
	}

	/**
	 * Prepare items in gui
	 */
	public abstract void prepareGui();

	/**
	 * Open GUI to player
	 *
	 * @param player Player that should have open GUI
	 */
	public void open(Player player) {
		prepareGui();
		player.openInventory(bukkitInventory);
	}

	/**
	 * Get container that is in gui
	 *
	 * @param slot Slot where is container
	 * @return Container from gui or null
	 */
	public MenuContainer getContainerAt(int slot) {
		MenuContainer container = null;

		int y = Math.floorDiv(slot, 9);
		int x = Math.floorMod(slot, 9);

		for (MenuContainer c : containers) {
			if (x >= c.getX() && x <= ((c.getX() - 1) + c.getWidth())) {
				if (y >= c.getY() && y <= ((c.getY() - 1) + c.getHeight())) {
					container = c;
					break;
				}
			}
		}

		return container;
	}

	/**
	 * Update items in GUI
	 */
	public void update() {
		this.containers.forEach(container -> {
			container.getItems()
				.forEach((location, item) -> {
					if (item == null) {
						return;
					}
					this.bukkitInventory.setItem(container.getMenuLocFromContainerLoc(location), item.getItemStack());
				});
		});
	}

	public void updateTitle(String title) {
		this.bukkitInventory.getViewers()
			.forEach(viewer -> {
				TryCatchUtil.tryRun(() -> InventoryHelperUtil.getInventoryHelper()
					.updateInventoryTitle((Player) viewer, title));
			});
	}

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		Inventory inv = event.getInventory();
		if (!this.isMenu(inv)) return;
		Inventory clickedInv = event.getClickedInventory();

		if (this.getGlobalClickAction() != null) {
			MenuItem.ClickLocation clickLocation = clickedInv == null ?
				MenuItem.ClickLocation.OUTSIDE :
				clickedInv.equals(this.bukkitInventory) ?
				MenuItem.ClickLocation.TOP :
				MenuItem.ClickLocation.BOTTOM;
			this.getGlobalClickAction()
				.accept(event, clickLocation);
		}

		if (!this.isMenu(clickedInv)) return;

		int slot = event.getSlot();
		MenuContainer container = this.getContainerAt(slot);
		if (container == null) return;

		MenuItem item = container.getItem(container.getContainerLocFromMenuLoc(slot));
		if (item == null) return;

		if (item.getClickAction() != null) {
			item.getClickAction()
				.accept(event, MenuItem.ClickLocation.TOP);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onUpdate(InventoryClickEvent event) {
		if (!this.updateItems) {
			return;
		}

		Inventory inv = event.getInventory();
		if (!this.isMenu(inv)) return;

		SchedulerUtil.runTaskLater(
			this.plugin, () -> {
				for (int i = 0; i < inv.getContents().length; i++) {
					ItemStack realIS = inv.getItem(i);
					MenuContainer container = getContainerAt(i);
					if (container == null) {
						continue;
					}
					Pair<Integer, Integer> loc = container.getContainerLocFromMenuLoc(i);
					MenuItem menuItem = container.getItem(loc);

					if (menuItem == null) {
						if (realIS != null && !realIS.getType()
							.equals(Material.AIR)) {
							container.setItem(loc.getFirst(), loc.getSecond(), new MenuItem(realIS));
						}
						continue;
					}

					if (realIS == null || realIS.getType()
						.equals(Material.AIR)) {
						container.removeItem(loc.getFirst(), loc.getSecond());
						continue;
					}

					if (menuItem.getItemStack()
						.isSimilar(realIS)) {
						continue;
					}

					menuItem.setItemStack(realIS);
				}
			}, 3
		);
	}

	@EventHandler
	public void onDrag(InventoryDragEvent event) {
		Inventory inv = event.getInventory();
		if (!this.isMenu(inv)) return;

		if (this.getGlobalDragAction() != null) {
			this.getGlobalDragAction()
				.accept(event);
		}
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		Inventory inv = event.getInventory();
		if (!this.isMenu(inv)) return;

		if (this.getCloseAction() != null) {
			this.getCloseAction()
				.accept(event);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		EnhancedMenu that = (EnhancedMenu) o;
		return Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	public boolean isMenu(Inventory inv) {
		if (inv == null) return false;
		return inv == this.bukkitInventory;
	}
}
