package com.osrslootsim;

import java.time.LocalDate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class SeasonTest
{
	private static Season on(int year, int month, int day)
	{
		return Season.on(LocalDate.of(year, month, day));
	}

	@Test
	public void halloweenRunsFromOctoberIntoEarlyNovember()
	{
		assertEquals(Season.NONE, on(2026, 9, 30));
		assertEquals(Season.HALLOWEEN, on(2026, 10, 1));
		assertEquals(Season.HALLOWEEN, on(2026, 10, 31));
		assertEquals(Season.HALLOWEEN, on(2026, 11, 2));
	}

	@Test
	public void thanksgivingEndsTheDayAfterTheFourthThursday()
	{
		assertEquals(26, Season.thanksgiving(2026));
		assertEquals(25, Season.thanksgiving(2027));
		assertEquals(23, Season.thanksgiving(2028));
		assertEquals(22, Season.thanksgiving(2029));
		assertEquals(28, Season.thanksgiving(2030));
		assertEquals(Season.THANKSGIVING, on(2026, 11, 3));
		assertEquals(Season.THANKSGIVING, on(2026, 11, 27));
		assertEquals(Season.NONE, on(2026, 11, 28));
		assertEquals(Season.THANKSGIVING, on(2030, 11, 29));
		assertEquals(Season.NONE, on(2030, 11, 30));
	}

	@Test
	public void christmasRunsThroughNewYearsDay()
	{
		assertEquals(Season.NONE, on(2026, 11, 30));
		assertEquals(Season.CHRISTMAS, on(2026, 12, 1));
		assertEquals(Season.CHRISTMAS, on(2026, 12, 25));
		assertEquals(Season.CHRISTMAS, on(2027, 1, 1));
		assertEquals(Season.NONE, on(2027, 1, 2));
		assertEquals(Season.NONE, on(2027, 6, 15));
	}

	@Test
	public void everySeasonHasItsPictures()
	{
		for (Season season : Season.values())
		{
			assertNotNull(season.still(), Season.class.getResource(season.still()));
			assertNotNull(season.strip(), Season.class.getResource(season.strip()));
			assertNotNull(season.fanfare(), Season.class.getResource(season.fanfare()));
		}
	}
}
