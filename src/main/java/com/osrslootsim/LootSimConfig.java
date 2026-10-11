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
}
