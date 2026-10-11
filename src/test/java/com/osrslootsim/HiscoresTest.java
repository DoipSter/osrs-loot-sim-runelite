package com.osrslootsim;

import net.runelite.client.hiscore.HiscoreSkill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
}
