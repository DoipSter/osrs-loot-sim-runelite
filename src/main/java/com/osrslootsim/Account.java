package com.osrslootsim;

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
	 * The account's kill count for a boss as RuneLite last saw it in chat, or null when it has none.
	 */
	Integer killCount(String boss);
}
