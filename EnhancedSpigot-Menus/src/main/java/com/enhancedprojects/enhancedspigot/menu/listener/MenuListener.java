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

package com.enhancedprojects.enhancedspigot.menu.listener;

import com.enhancedprojects.enhancedspigot.menu.EnhancedMenu;
import com.enhancedprojects.enhancedspigot.menu.container.MenuContainer;
import com.enhancedprojects.enhancedspigot.menu.item.MenuItem;
import com.enhancedprojects.enhancedspigot.util.Pair;
import com.enhancedprojects.enhancedspigot.util.SchedulerUtil;
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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MenuListener implements Listener {
	private static volatile MenuListener INSTANCE;

	private final JavaPlugin plugin;
	private final ConcurrentMap<UUID, EnhancedMenu> openedMenus;

	private MenuListener(JavaPlugin plugin) {
		this.plugin = plugin;
		this.openedMenus = new ConcurrentHashMap<>();

		plugin.getServer()
			.getPluginManager()
			.registerEvents(this, plugin);
	}

	public static MenuListener getInstance(JavaPlugin plugin) {
		if (INSTANCE == null) {
			synchronized (MenuListener.class) {
				if (INSTANCE == null) INSTANCE = new MenuListener(plugin);
			}
		}
		return INSTANCE;
	}

	public void addViewer(Player player, EnhancedMenu menu) {
		this.openedMenus.put(player.getUniqueId(), menu);
	}

	public void removeViewer(Player player, EnhancedMenu menu) {
		this.openedMenus.remove(player.getUniqueId(), menu);
	}

	public void removeViewer(Player player) {
		this.openedMenus.remove(player.getUniqueId());
	}

	public EnhancedMenu getMenu(Player player) {
		return this.openedMenus.get(player.getUniqueId());
	}

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		Player player = (Player) event.getWhoClicked();
		EnhancedMenu menu = this.getMenu(player);
		if (menu == null) return;

		Inventory inv = event.getInventory();
		if (!menu.isMenu(inv)) return;

		Inventory clickedInv = event.getClickedInventory();

		if (menu.getGlobalClickAction() != null) {
			MenuItem.ClickLocation clickLocation = clickedInv == null ?
				MenuItem.ClickLocation.OUTSIDE :
				clickedInv.equals(menu.getBukkitInventory()) ?
				MenuItem.ClickLocation.TOP :
				MenuItem.ClickLocation.BOTTOM;
			menu.getGlobalClickAction()
				.accept(event, clickLocation);
		}

		if (!menu.isMenu(clickedInv)) return;

		int slot = event.getSlot();
		MenuContainer container = menu.getContainerAt(slot);
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
		Player player = (Player) event.getWhoClicked();
		EnhancedMenu menu = this.getMenu(player);
		if (menu == null) return;
		if (!menu.isUpdateItems()) return;

		Inventory inv = event.getInventory();
		if (!menu.isMenu(inv)) return;

		SchedulerUtil.runTaskLater(
			this.plugin, () -> {
				for (int i = 0; i < inv.getContents().length; i++) {
					ItemStack realIS = inv.getItem(i);
					MenuContainer container = menu.getContainerAt(i);
					if (container == null) continue;
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
		Player player = (Player) event.getWhoClicked();
		EnhancedMenu menu = this.getMenu(player);
		if (menu == null) return;

		Inventory inv = event.getInventory();
		if (!menu.isMenu(inv)) return;

		if (menu.getGlobalDragAction() != null) {
			menu.getGlobalDragAction()
				.accept(event);
		}
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		Player player = (Player) event.getPlayer();
		EnhancedMenu menu = this.getMenu(player);
		if (menu == null) return;

		Inventory inv = event.getInventory();
		if (!menu.isMenu(inv)) return;

		this.removeViewer(player, menu);
		if (menu.getCloseAction() != null) {
			menu.getCloseAction()
				.accept(event);
		}
	}
}
