package com.osrslootsim;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(LootSimPlugin.CONFIG_GROUP)
public interface LootSimConfig extends Config
{
	@ConfigSection(
		name = "Insane drops",
		description = "What a simulated run shows when it has an insane drop: a collection log item rarer than 1/2,000, or anything worth 10m or more",
		position = 10
	)
	String INSANE_DROPS = "insaneDrops";

	@ConfigSection(
		name = "Loot beam in the game",
		description = "Use the insane drop's loot beam for things that happen in the game itself",
		position = 20
	)
	String IN_GAME = "inGame";

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
		name = "Holiday looks and sound",
		description = "Dress the Looting icon, the loot beam and the insane drop's sound for Halloween, Thanksgiving and Christmas",
		position = 2
	)
	default boolean holidayIcon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "insaneDropCard",
		name = "Pop-up in chat",
		description = "Show the item in the chatbox",
		position = 11,
		section = INSANE_DROPS
	)
	default boolean insaneDropCard()
	{
		return true;
	}

	@ConfigItem(
		keyName = "insaneDropBurst",
		name = "Loot beam",
		description = "A burst of light and a golden loot beam under your character",
		position = 12,
		section = INSANE_DROPS
	)
	default boolean insaneDropBurst()
	{
		return true;
	}

	@ConfigItem(
		keyName = "insaneDropSound",
		name = "Sound",
		description = "Play osrslootsim.com's pet fanfare",
		position = 13,
		section = INSANE_DROPS
	)
	default boolean insaneDropSound()
	{
		return true;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "soundVolume",
		name = "Sound volume",
		description = "How loud the sound is. 0 is silent",
		position = 14,
		section = INSANE_DROPS
	)
	default int soundVolume()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "levelUpBeam",
		name = "On level up",
		description = "Show the loot beam under your character when you gain a level, a Looting level included",
		position = 21,
		section = IN_GAME
	)
	default boolean levelUpBeam()
	{
		return false;
	}

	@ConfigItem(
		keyName = "collectionLogBeam",
		name = "On collection log",
		description = "Show the loot beam under your character when a new item is added to your collection log",
		position = 22,
		section = IN_GAME
	)
	default boolean collectionLogBeam()
	{
		return false;
	}
}
