/*
 * Copyright (c) 2018, Ethan <https://github.com/shmeeps>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.praydose;

import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Provides;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.Overlay;
import org.apache.commons.lang3.StringUtils;

@PluginDescriptor(
	name = "Prayer Dose"
)
public class PrayerDosePlugin extends Plugin
{
	private static final Set<Integer> SUPER_RESTORE_ITEM_IDS = Sets.newHashSet
	(
		ItemID.SUPER_RESTORE4, ItemID.SUPER_RESTORE3, ItemID.SUPER_RESTORE2, ItemID.SUPER_RESTORE1
	);

	private static final Set<Integer> PRAYER_ITEM_IDS = Sets.newHashSet
	(
		ItemID.PRAYER_POTION4, ItemID.PRAYER_POTION3, ItemID.PRAYER_POTION2, ItemID.PRAYER_POTION1
	);

	private static final Set<Integer> HOLY_EQUIPMENT_IDS = Sets.newHashSet
	(
		ItemID.PRAYER_CAPE, ItemID.PRAYER_CAPET, ItemID.PRAYER_CAPE_10643,
		ItemID.MAX_CAPE, ItemID.MAX_CAPE_13282, ItemID.MAX_CAPE_13342,
		ItemID.HOLY_WRENCH, ItemID.RING_OF_THE_GODS, ItemID.RING_OF_THE_GODS_I
	);

	@Getter
	private boolean holdingPrayerPotion = false;

	@Getter
	private boolean holdingSuperRestore = false;

	@Getter
	private boolean holyWrenchInInventory = false;

	@Getter
	private boolean holyWrenchInEquipment = false;

	@Getter
	private int prayerPotionPointsRestored = 7;

	@Getter
	private int superRestorePointsRestored = 8;

	@Inject
	private PrayerDoseOverlay overlay;

	@Inject
	private PrayerDoseConfig config;

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Provides
	PrayerDoseConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PrayerDoseConfig.class);
	}

	@Override
	public Overlay getOverlay()
	{
		return overlay;
	}

	@Subscribe
	public void onTick(GameTick tick)
	{
		overlay.onTick();
	}

	@Subscribe
	public void onItemContainerChanged(final ItemContainerChanged event)
	{
		if (event.getItemContainer() == client.getItemContainer(InventoryID.INVENTORY))
		{
			this.checkInventory();
			this.calculatePrayerPointsRestored();
		}

		if (event.getItemContainer() == client.getItemContainer(InventoryID.EQUIPMENT))
		{
			this.checkEquipment();
			this.calculatePrayerPointsRestored();
		}
	}

	public void checkInventory()
	{
		this.holdingPrayerPotion = false;
		this.holdingSuperRestore = false;
		this.holyWrenchInInventory = false;

		ItemContainer inventoryContainer = client.getItemContainer(InventoryID.INVENTORY);

		if (inventoryContainer == null)
		{
			return;
		}

		Item[] inventoryItems = inventoryContainer.getItems();

		if (inventoryItems == null)
		{
			return;
		}

		for (Item item : inventoryItems)
		{
			if (item.getId() == -1)
			{
				continue;
			}

			if (PRAYER_ITEM_IDS.contains(item.getId()))
			{
				this.holdingPrayerPotion = true;
				continue;
			}

			if (SUPER_RESTORE_ITEM_IDS.contains(item.getId()))
			{
				this.holdingSuperRestore = true;
				continue;
			}

			if (HOLY_EQUIPMENT_IDS.contains(item.getId()))
			{
				this.holyWrenchInInventory = true;
			}
		}
	}

	public void checkEquipment()
	{
		this.holyWrenchInEquipment = false;

		ItemContainer equipmentContainer = client.getItemContainer(InventoryID.EQUIPMENT);

		if (equipmentContainer == null)
		{
			return;
		}

		Item[] equipmentItems = equipmentContainer.getItems();

		if (equipmentItems == null)
		{
			return;
		}

		for (Item item : equipmentItems)
		{
			if (item.getId() == -1)
			{
				continue;
			}

			if (HOLY_EQUIPMENT_IDS.contains(item.getId()))
			{
				this.holyWrenchInEquipment = true;
			}
		}
	}

	public double getPrayerDrainRate(Client client)
	{
		double drainRate = 0.0;

		for (Prayer prayer : Prayer.values())
		{
			if (client.isPrayerActive(prayer))
			{
				drainRate += prayer.getDrainRate();
			}
		}

		return drainRate;
	}

	public void calculatePrayerPointsRestored()
	{
		double dosePercentage = this.hasHolyWrench() ? .27 : .25;
		int maxPrayer = this.client.getRealSkillLevel(Skill.PRAYER);
		int basePointsRestored = (int)Math.floor(maxPrayer * dosePercentage);

		this.prayerPotionPointsRestored = basePointsRestored + 7;
		this.superRestorePointsRestored = basePointsRestored + 8;
	}

	public int getTotalPrayerBonus()
	{
		int prayerBonus = 0;
		ItemContainer itemContainer = this.client.getItemContainer(InventoryID.EQUIPMENT);

		if (itemContainer == null)
		{
			return 0;
		}

		Item[] items = itemContainer.getItems();

		if (items == null)
		{
			return 0;
		}

		for (Item item : items)
		{
			if (item.getId() == -1)
			{
				continue;
			}

			prayerBonus += PrayerItems.getItemPrayerBonus(item.getId());
		}

		return prayerBonus;
	}

	public String getEstimatedTimeRemaining()
	{
		// Base data
		double drainRate = this.getPrayerDrainRate(this.client);

		if (drainRate == 0)
		{
			return "N/A";
		}

		int prayerBonus = this.getTotalPrayerBonus();
		int currentPrayer = this.client.getBoostedSkillLevel(Skill.PRAYER);

		// Calculate how many seconds each prayer points last so the prayer bonus can be applied
		double secondsPerPoint = (60.0 / drainRate) * (1.0 + (prayerBonus / 30.0));

		// Calculate the number of seconds left
		double secondsLeft = (currentPrayer * secondsPerPoint);
		int minutes = (int)Math.floor(secondsLeft / 60.0);
		int seconds = (int)Math.floor(secondsLeft - (minutes * 60.0));

		// Return the text
		return Integer.toString(minutes) + ":" + StringUtils.leftPad(Integer.toString(seconds), 2, "0");
	}

	public boolean hasHolyWrench()
	{
		return this.holyWrenchInEquipment || this.holyWrenchInInventory;
	}
}
