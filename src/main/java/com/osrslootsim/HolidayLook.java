package com.osrslootsim;

import java.time.LocalDate;

/**
 * Which holiday the Looting icon, the loot beam and the insane drop's sound are dressed for: whichever is in season,
 * none, or one of them all year.
 */
public enum HolidayLook
{
	BY_SEASON("By season"),
	OFF("Off"),
	HALLOWEEN("Halloween"),
	THANKSGIVING("Thanksgiving"),
	CHRISTMAS("Christmas");

	private final String name;

	HolidayLook(String name)
	{
		this.name = name;
	}

	Season season(LocalDate today)
	{
		switch (this)
		{
			case OFF:
				return Season.NONE;
			case HALLOWEEN:
				return Season.HALLOWEEN;
			case THANKSGIVING:
				return Season.THANKSGIVING;
			case CHRISTMAS:
				return Season.CHRISTMAS;
			default:
				return Season.on(today);
		}
	}

	@Override
	public String toString()
	{
		return name;
	}
}
