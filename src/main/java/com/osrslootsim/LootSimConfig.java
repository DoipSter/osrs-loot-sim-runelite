package com.osrslootsim;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(LootSimPlugin.CONFIG_GROUP)
public interface LootSimConfig extends Config
{
	@ConfigItem(
		keyName = "animatedIcon",
		name = "Animated skill icon",
		description = "Play the Looting icon's loot beam while the panel is open",
		position = 1
	)
	default boolean animatedIcon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "holidayIcon",
		name = "Holiday skill icon",
		description = "Dress the Looting icon for Halloween, Thanksgiving and Christmas",
		position = 2
	)
	default boolean holidayIcon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "insaneDropCard",
		name = "Insane drop pop-up",
		description = "Show the item in the chatbox when a simulated run has an insane drop: a collection log item rarer than 1/2,000, or anything worth 10m or more",
		position = 3
	)
	default boolean insaneDropCard()
	{
		return true;
	}

	@ConfigItem(
		keyName = "insaneDropBurst",
		name = "Insane drop loot beam",
		description = "A burst of light and a golden loot beam on your character when a simulated run has an insane drop",
		position = 4
	)
	default boolean insaneDropBurst()
	{
		return true;
	}
}
