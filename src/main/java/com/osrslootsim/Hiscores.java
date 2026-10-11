package com.osrslootsim;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

/**
 * Matches the simulator's names for bosses, raids and clues to their rows on the official hiscores.
 */
final class Hiscores
{
	// The simulator's names for sources the hiscores spell another way
	private static final Map<String, String> NAMES = new HashMap<>();

	// Sources the simulator counts in something other than the hiscores do (reward rolls, not games), so a hiscores
	// count would be the wrong number to simulate
	private static final Set<String> OTHER_UNITS = new HashSet<>(Arrays.asList("wintertodt", "tempoross", "guardians of the rift"));

	static
	{
		NAMES.put("barrows", "barrows chests");
		NAMES.put("the nightmare", "nightmare");
		NAMES.put("moons of peril", "lunar chests");
		NAMES.put("fortis colosseum", "sol heredit");
		NAMES.put("guardians of the rift", "rifts closed");
	}

	private Hiscores()
	{
	}

	/**
	 * The hiscores row whose picture stands for a source, or null. Every clue tier shares the one clue picture: the
	 * tiers' own rows have none.
	 */
	static HiscoreSkill picture(String source)
	{
		String name = source.toLowerCase(Locale.ENGLISH);
		HiscoreSkill row = find(name.startsWith("clue scroll (") ? "clue scrolls (all)" : NAMES.getOrDefault(name, name));
		return row != null && row.getSpriteId() > 0 ? row : null;
	}

	/**
	 * True for a source with a boss's row on the hiscores, raids among them: what the panel lets a player pin. Clues,
	 * minigames, chests and ordinary monsters have none.
	 */
	static boolean boss(String source)
	{
		HiscoreSkill row = picture(source);
		return row != null && row.getType() == HiscoreSkillType.BOSS;
	}

	/**
	 * The hiscores row that counts a source's kills, completions or caskets, or null when the hiscores have none that
	 * counts the same thing.
	 */
	static HiscoreSkill count(String source)
	{
		String name = source.toLowerCase(Locale.ENGLISH);
		if (otherUnit(source))
		{
			return null;
		}
		return find(name.startsWith("clue scroll (") ? name.replace("clue scroll (", "clue scrolls (") : NAMES.getOrDefault(name, name));
	}

	/**
	 * True for a source the simulator counts in reward rolls where the game counts games: no kill count fits it.
	 */
	static boolean otherUnit(String source)
	{
		return OTHER_UNITS.contains(source.toLowerCase(Locale.ENGLISH));
	}

	private static HiscoreSkill find(String name)
	{
		for (HiscoreSkill row : HiscoreSkill.values())
		{
			if (row.getName().toLowerCase(Locale.ENGLISH).equals(name))
			{
				return row;
			}
		}
		return null;
	}
}
