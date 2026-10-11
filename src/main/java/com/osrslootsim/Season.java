package com.osrslootsim;

import java.time.LocalDate;

/**
 * The holiday the Looting icon dresses for, by the player's own date, on the same calendar as osrslootsim.com:
 * Halloween from 1 October to 2 November, Thanksgiving from 3 November to the day after US Thanksgiving, Christmas
 * from 1 December to New Year's Day. A few plain days lie between Thanksgiving and Christmas.
 */
enum Season
{
	NONE(""),
	HALLOWEEN("-halloween"),
	THANKSGIVING("-thanksgiving"),
	CHRISTMAS("-christmas");

	private final String suffix;

	Season(String suffix)
	{
		this.suffix = suffix;
	}

	/**
	 * The still Looting icon for this season, 16 by 16, next to the plugin's classes.
	 */
	String still()
	{
		return "looting" + suffix + ".png";
	}

	/**
	 * The moving Looting icon for this season: its frames side by side in one picture.
	 */
	String strip()
	{
		return "looting-anim" + suffix + ".png";
	}

	/**
	 * The site's pet fanfare for this season, as a sound file next to the plugin's classes.
	 */
	String fanfare()
	{
		return "pet" + suffix + ".wav";
	}

	static Season on(LocalDate day)
	{
		int date = day.getMonthValue() * 100 + day.getDayOfMonth();
		if (date >= 1001 && date <= 1102)
		{
			return HALLOWEEN;
		}
		if (date >= 1103 && date <= 1100 + thanksgiving(day.getYear()) + 1)
		{
			return THANKSGIVING;
		}
		if (date >= 1201 || date == 101)
		{
			return CHRISTMAS;
		}
		return NONE;
	}

	/**
	 * The day of November that US Thanksgiving falls on: its fourth Thursday.
	 */
	static int thanksgiving(int year)
	{
		int firstDay = LocalDate.of(year, 11, 1).getDayOfWeek().getValue() % 7;   // Sunday 0 to Saturday 6
		return 22 + (11 - firstDay) % 7;
	}
}
