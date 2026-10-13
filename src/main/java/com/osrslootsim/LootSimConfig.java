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
		name = "In the game",
		description = "Use the insane drop's loot beam and pop-up for things that happen in the game itself",
		position = 20
	)
	String IN_GAME = "inGame";

	// The most the loot beam's value can be set to: 25m
	int MOST_LOOT_VALUE = 25_000_000;

	// The bosses pinned under the boss box until the player pins their own
	String FIRST_PINS = "Vorkath,Zulrah,Chambers of Xeric,Theatre of Blood,Tombs of Amascut";

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
		keyName = "holidayLook",
		name = "Holiday looks and sound",
		description = "Dress the Looting icon, the loot beam and the insane drop's sound for a holiday: whichever is in season, none, or one of them all year",
		position = 2
	)
	default HolidayLook holidayLook()
	{
		return HolidayLook.BY_SEASON;
	}

	@ConfigItem(
		keyName = "beamColour",
		name = "Loot beam colour",
		description = "One colour always, or by holiday: purple at Halloween, autumn orange at Thanksgiving, ice white at Christmas and gold otherwise",
		position = 3
	)
	default BeamColour beamColour()
	{
		return BeamColour.BY_HOLIDAY;
	}

	@ConfigItem(
		keyName = "insaneDropCard",
		name = "Pop-up",
		description = "Show the item on a card. The card starts in the chatbox; untick Lock pop-up to put it somewhere else",
		position = 11,
		section = INSANE_DROPS
	)
	default boolean insaneDropCard()
	{
		return false;
	}

	@ConfigItem(
		keyName = "lockCard",
		name = "Lock pop-up",
		description = "Untick to place the pop-up. It then stays on screen, outlined. Hold Alt (RuneLite's drag key), then drag it to move it, "
			+ "drag one of its edges to resize it, or right-click it to put it back. Tick again when it is where you want it",
		position = 12,
		section = INSANE_DROPS
	)
	default boolean lockCard()
	{
		return true;
	}

	@ConfigItem(
		keyName = "insaneDropBurst",
		name = "Loot beam",
		description = "A burst of light and a golden loot beam under your character",
		position = 13,
		section = INSANE_DROPS
	)
	default boolean insaneDropBurst()
	{
		return false;
	}

	@ConfigItem(
		keyName = "insaneDropSound",
		name = "Sound",
		description = "Play osrslootsim.com's pet fanfare",
		position = 14,
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
		position = 15,
		section = INSANE_DROPS
	)
	default int soundVolume()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "levelUpBeam",
		name = "Beam on level up",
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
		name = "Beam on collection log",
		description = "Show the loot beam under your character when a new item is added to your collection log",
		position = 22,
		section = IN_GAME
	)
	default boolean collectionLogBeam()
	{
		return false;
	}

	@ConfigItem(
		keyName = "collectionLogCard",
		name = "Pop-up on collection log",
		description = "Show the pop-up when a new item is added to your collection log. It says Collection log, New item and the item's name",
		position = 23,
		section = IN_GAME
	)
	default boolean collectionLogCard()
	{
		return false;
	}

	@ConfigItem(
		keyName = "lootBeam",
		name = "Beam on valuable loot",
		description = "Show the loot beam under your character when the loot of one kill or chest is worth the amount below or more",
		position = 24,
		section = IN_GAME
	)
	default boolean lootBeam()
	{
		return false;
	}

	@Range(max = MOST_LOOT_VALUE)
	@Units(" gp")
	@ConfigItem(
		keyName = "lootBeamValue",
		name = "Loot worth at least",
		description = "From 0 to 25,000,000 gp, at Grand Exchange prices, for all the loot of one kill or chest together. 0 is any loot",
		position = 25,
		section = IN_GAME
	)
	default int lootBeamValue()
	{
		return 1_000_000;
	}

	// Kept by the panel, not shown among the settings: the pinned bosses' names with commas between them
	@ConfigItem(
		keyName = "pinnedBosses",
		name = "",
		description = "",
		hidden = true
	)
	default String pinnedBosses()
	{
		return FIRST_PINS;
	}

	@ConfigItem(
		keyName = "pinnedBosses",
		name = "",
		description = ""
	)
	void pinnedBosses(String names);
}
