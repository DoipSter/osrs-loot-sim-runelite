package com.osrslootsim;

/**
 * The loot beam's colour: one colour always, or whichever suits the holiday look (purple at Halloween, autumn orange
 * at Thanksgiving, ice white at Christmas, and the site's gold the rest of the year).
 */
public enum BeamColour
{
	BY_HOLIDAY("By holiday"),
	GOLD("Gold"),
	PURPLE("Purple"),
	AUTUMN("Autumn orange"),
	ICE("Ice white");

	private final String name;

	BeamColour(String name)
	{
		this.name = name;
	}

	Burst.Glow glow(Season season)
	{
		switch (this)
		{
			case GOLD:
				return Burst.Glow.GOLD;
			case PURPLE:
				return Burst.Glow.PURPLE;
			case AUTUMN:
				return Burst.Glow.AUTUMN;
			case ICE:
				return Burst.Glow.ICE;
			default:
				return season == Season.HALLOWEEN ? Burst.Glow.PURPLE
					: season == Season.THANKSGIVING ? Burst.Glow.AUTUMN
					: season == Season.CHRISTMAS ? Burst.Glow.ICE
					: Burst.Glow.GOLD;
		}
	}

	@Override
	public String toString()
	{
		return name;
	}
}
