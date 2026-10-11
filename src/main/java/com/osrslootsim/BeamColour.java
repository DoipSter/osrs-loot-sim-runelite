package com.osrslootsim;

/**
 * The loot beam's colour: the site's gold, the purple of its Looting icon, or whichever suits the holiday look
 * (purple at Halloween, gold otherwise).
 */
public enum BeamColour
{
	BY_HOLIDAY("By holiday"),
	GOLD("Gold"),
	PURPLE("Purple");

	private final String name;

	BeamColour(String name)
	{
		this.name = name;
	}

	DropCard.Glow glow(Season season)
	{
		boolean purple = this == PURPLE || this == BY_HOLIDAY && season == Season.HALLOWEEN;
		return purple ? DropCard.Glow.PURPLE : DropCard.Glow.GOLD;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
