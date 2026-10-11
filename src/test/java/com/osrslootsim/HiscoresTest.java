package com.osrslootsim;

import net.runelite.client.hiscore.HiscoreSkill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class HiscoresTest
{
	@Test
	public void bossesAndRaidsCountOnTheirOwnRows()
	{
		assertEquals(HiscoreSkill.VORKATH, Hiscores.count("Vorkath"));
		assertEquals(HiscoreSkill.ZULRAH, Hiscores.count("zulrah"));
		assertEquals(HiscoreSkill.CHAMBERS_OF_XERIC, Hiscores.count("Chambers of Xeric"));
		assertEquals("Kree'Arra", Hiscores.count("Kree'arra").getName());
		assertEquals("Thermonuclear Smoke Devil", Hiscores.count("Thermonuclear smoke devil").getName());
	}

	@Test
	public void sourcesTheHiscoresSpellAnotherWay()
	{
		assertEquals(HiscoreSkill.BARROWS_CHESTS, Hiscores.count("Barrows"));
		assertEquals("Nightmare", Hiscores.count("The Nightmare").getName());
		assertEquals("Lunar Chests", Hiscores.count("Moons of Peril").getName());
		assertEquals("Sol Heredit", Hiscores.count("Fortis Colosseum").getName());
		assertEquals(HiscoreSkill.CLUE_SCROLL_HARD, Hiscores.count("Clue scroll (hard)"));
	}

	@Test
	public void noCountWhereTheUnitsDiffer()
	{
		assertNull(Hiscores.count("Wintertodt"));
		assertNull(Hiscores.count("Tempoross"));
		assertNull(Hiscores.count("Guardians of the Rift"));
		assertNull(Hiscores.count("Abyssal demon"));
		assertNull(Hiscores.count("Crystal chest"));
	}

	@Test
	public void picturesComeFromRowsThatHaveOne()
	{
		assertEquals(HiscoreSkill.VORKATH, Hiscores.picture("Vorkath"));
		assertEquals("Clue Scrolls (all)", Hiscores.picture("Clue scroll (hard)").getName());
		assertEquals("Rifts closed", Hiscores.picture("Guardians of the Rift").getName());
		assertEquals(HiscoreSkill.WINTERTODT, Hiscores.picture("Wintertodt"));
		assertNull(Hiscores.picture("Abyssal demon"));
	}

	@Test
	public void onlyBossesAndRaidsAreBosses()
	{
		assertTrue(Hiscores.boss("Vorkath"));
		assertTrue(Hiscores.boss("Chambers of Xeric"));
		assertTrue(Hiscores.boss("Barrows"));
		assertTrue(Hiscores.boss("Moons of Peril"));
		assertFalse(Hiscores.boss("Clue scroll (hard)"));
		assertFalse(Hiscores.boss("Guardians of the Rift"));
		assertFalse(Hiscores.boss("Abyssal demon"));
		assertFalse(Hiscores.boss("Crystal chest"));
		assertFalse(Hiscores.boss(""));
	}

	@Test
	public void theFirstPinsAreBosses()
	{
		for (String name : LootSimConfig.FIRST_PINS.split(","))
		{
			assertTrue(name, Hiscores.boss(name));
		}
	}
}
