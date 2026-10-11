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

	DropCard.Glow glow(Season season)
	{
		switch (this)
		{
			case GOLD:
				return DropCard.Glow.GOLD;
			case PURPLE:
				return DropCard.Glow.PURPLE;
			case AUTUMN:
				return DropCard.Glow.AUTUMN;
			case ICE:
				return DropCard.Glow.ICE;
			default:
				return season == Season.HALLOWEEN ? DropCard.Glow.PURPLE
					: season == Season.THANKSGIVING ? DropCard.Glow.AUTUMN
					: season == Season.CHRISTMAS ? DropCard.Glow.ICE
					: DropCard.Glow.GOLD;
		}
	}

	@Override
	public String toString()
	{
		return name;
	}
}
