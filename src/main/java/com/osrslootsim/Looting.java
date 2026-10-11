package com.osrslootsim;

import net.runelite.api.Experience;

/**
 * The plugin's Looting level: 1 to 99 on the game's own experience curve, like the level on osrslootsim.com. The two
 * are separate: the site keeps its level in the visitor's browser, the plugin keeps this one per RuneScape account.
 */
final class Looting
{
	static final long MAX_XP = Experience.MAX_SKILL_XP;

	private Looting()
	{
	}

	static int level(long xp)
	{
		int capped = (int) Math.max(0, Math.min(xp, MAX_XP));
		return Math.min(Experience.MAX_REAL_LEVEL, Experience.getLevelForXp(capped));
	}

	/**
	 * How far through the current level the xp is, from 0 to 1. Always 1 at level 99.
	 */
	static double progress(long xp)
	{
		int level = level(xp);
		if (level >= Experience.MAX_REAL_LEVEL)
		{
			return 1;
		}
		long from = Experience.getXpForLevel(level);
		long to = Experience.getXpForLevel(level + 1);
		return Math.max(0, Math.min(1, (double) (xp - from) / (to - from)));
	}

	/**
	 * The xp still needed for the next level, or 0 at level 99.
	 */
	static long toNext(long xp)
	{
		int level = level(xp);
		return level >= Experience.MAX_REAL_LEVEL ? 0 : Experience.getXpForLevel(level + 1) - xp;
	}
}
