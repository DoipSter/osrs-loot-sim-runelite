package com.osrslootsim;

import java.util.function.Consumer;

/**
 * What the panel needs to know about the RuneScape account RuneLite is on.
 */
interface Account
{
	/**
	 * True once RuneLite knows which RuneScape account is playing. Looting xp is kept per account, so there is none to
	 * show or earn before that.
	 */
	boolean known();

	long xp();

	void addXp(long gained);

	/**
	 * Finds the account's kill count for a boss: the larger of the count RuneLite last read from chat and the one on the
	 * official hiscores. Called on the Swing thread, it answers there too, with null when neither has a count.
	 */
	void killCount(String boss, Consumer<Integer> answer);
}
