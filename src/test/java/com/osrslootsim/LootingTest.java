package com.osrslootsim;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LootingTest
{
	@Test
	public void levelsFollowTheGamesCurve()
	{
		assertEquals(1, Looting.level(0));
		assertEquals(1, Looting.level(82));
		assertEquals(2, Looting.level(83));
		assertEquals(41, Looting.level(43_912));
		assertEquals(98, Looting.level(13_034_430));
		assertEquals(99, Looting.level(13_034_431));
	}

	@Test
	public void levelStopsAt99()
	{
		assertEquals(99, Looting.level(Looting.MAX_XP));
		assertEquals(99, Looting.level(Long.MAX_VALUE));
		assertEquals(1, Looting.level(-5));
		assertEquals(1.0, Looting.progress(13_034_431), 0);
		assertEquals(0, Looting.toNext(13_034_431));
	}

	@Test
	public void progressThroughALevel()
	{
		// Level 41 starts at 41,171 xp and level 42 at 45,529
		assertEquals(0.0, Looting.progress(41_171), 1e-9);
		assertEquals(1_617, Looting.toNext(43_912));
		assertEquals((43_912 - 41_171) / 4358.0, Looting.progress(43_912), 1e-9);
	}

	@Test
	public void numbersReadLikeTheSites()
	{
		assertEquals("14,269 gp", Text.coins(14_269));
		assertEquals("681K gp", Text.coins(681_897));
		assertEquals("15.14M gp", Text.coins(15_136_059));
		assertEquals("2.56B gp", Text.coins(2_556_137_538L));
		assertEquals("1/5,000", Text.rate(5000.00000000055));
		assertEquals("1/64", Text.rate(64));
		assertEquals("1/12.5", Text.rate(12.5));
		assertEquals("1/2.25", Text.rate(2.25));
		assertEquals("2.0%", Text.percent(0.0198));
		assertEquals("<0.1%", Text.percent(0.0002));
		assertEquals(">99.9%", Text.percent(0.99999));
		assertEquals("100.0%", Text.percent(1));
		assertEquals("kills", Text.plural("kill"));
		assertEquals("searches", Text.plural("search"));
		assertEquals("kills", Text.plural(null));
	}

	@Test
	public void textFromTheSiteCannotBecomeHtml()
	{
		assertEquals("htmlbVorkath", Text.plain("<html><b>Vorkath", 60));
		assertEquals("Vorkath", Text.plain("  Vorkath\n\u0000", 60));
		assertEquals("Vork", Text.plain("Vorkath", 4));
		assertEquals("", Text.plain(null, 60));
	}
}
