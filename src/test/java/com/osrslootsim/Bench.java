package com.osrslootsim;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import javax.swing.JLabel;

/**
 * Times the parts of the plugin that run every frame or every keystroke, without the game: the loot beam at two
 * screen sizes for the regular look and each holiday, the chat card, and a search of the boss box. Run it with
 * "gradlew bench". The game draws 50 frames a second, 20 ms each, and a plugin should take a small part of that.
 */
public final class Bench
{
	private static final int WARM_UP = 150;
	private static final int FRAMES = 300;
	private static final int SHOWN_MS = 4800;

	public static void main(String[] args)
	{
		System.out.println("loot beam, milliseconds a frame over its 4.8 s (median / 95th percentile / worst), with a 3,000-face character and 6,000 faces of scenery cut out");
		// the game at its fixed size, the camera at an ordinary distance; and a big window zoomed right in
		time("fixed 512x334, tile 44 px ", new Rectangle(4, 4, 512, 334), 44, 260, 210, 40, 90);
		time("large 1640x1000, tile 120 px", new Rectangle(0, 0, 1640, 1000), 120, 820, 640, 110, 250);

		BufferedImage canvas = new BufferedImage(600, 300, BufferedImage.TYPE_INT_ARGB_PRE);
		Graphics2D g = canvas.createGraphics();
		DropCard.Drop drop = new DropCard.Drop("Tumeken's shadow (uncharged)");
		drop.picture = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
		long[] card = new long[FRAMES];
		for (int i = -WARM_UP; i < FRAMES; i++)
		{
			long began = System.nanoTime();
			DropCard.paintCard(g, 20, 20, drop, 0.9f);
			if (i >= 0)
			{
				card[i] = System.nanoTime() - began;
			}
		}
		g.dispose();
		System.out.println("chat card: " + report(card));

		// the boss box: every keystroke filters the full list
		List<String> names = new ArrayList<>();
		Random random = new Random(7);
		for (int i = 0; i < 1708; i++)
		{
			StringBuilder name = new StringBuilder();
			for (int j = 0; j < 6 + random.nextInt(14); j++)
			{
				name.append((char) (j > 0 && random.nextInt(7) == 0 ? ' ' : 'a' + random.nextInt(26)));
			}
			names.add(name.toString());
		}
		Icons none = new Icons()
		{
			@Override
			public void item(JLabel label, int id, String name, long quantity)
			{
			}

			@Override
			public void picture(int id, String name, Consumer<BufferedImage> use)
			{
			}

			@Override
			public void boss(String name, Consumer<BufferedImage> use)
			{
			}
		};
		BossPicker picker = new BossPicker(none, names.subList(0, 177), names.get(0));
		picker.setBosses(names.subList(0, 177), names, Collections.emptyMap());
		String[] typed = {"a", "ab", "abc", "e", "th", "s", "zzzz", "o", "an"};
		long[] search = new long[FRAMES];
		for (int i = -WARM_UP; i < FRAMES; i++)
		{
			long began = System.nanoTime();
			picker.type(typed[Math.floorMod(i, typed.length)]);
			if (i >= 0)
			{
				search[i] = System.nanoTime() - began;
			}
		}
		System.out.println("boss search, one keystroke over 1,708 names: " + report(search));
	}

	private static void time(String label, Rectangle view, int tileWide, int footX, int footY, int bodyWide, int bodyTall)
	{
		BufferedImage canvas = new BufferedImage(view.x + view.width, view.y + view.height, BufferedImage.TYPE_INT_ARGB_PRE);
		Stage stage = new Stage(footX, footY, tileWide, 27, 0, 12);
		// A stand-in for the character's model: small faces scattered over the space a character fills. And for the
		// scenery around it: twice as many, scattered over the floor the burst covers and a little above it.
		Random random = new Random(3);
		int[][] body = faces(random, 3000, footX - bodyWide / 2, footY - bodyTall, bodyWide, bodyTall);
		int[][] scenery = faces(random, 6000, footX - 3 * tileWide, footY - 2 * tileWide, 6 * tileWide, 3 * tileWide);
		Consumer<Burst.Cut> infront = cut ->
		{
			cut.faces(body[0], body[1], body[0].length, body[2], body[3], body[4], null, body[2].length, true);
			cut.faces(scenery[0], scenery[1], scenery[0].length, scenery[2], scenery[3], scenery[4], null, scenery[2].length, false);
		};
		for (Season season : Season.values())
		{
			Burst.Glow glow = BeamColour.BY_HOLIDAY.glow(season);
			long[] took = new long[FRAMES];
			Graphics2D g = canvas.createGraphics();
			for (int i = -WARM_UP; i < FRAMES; i++)
			{
				long age = (long) Math.floorMod(i, FRAMES) * SHOWN_MS / FRAMES;
				long began = System.nanoTime();
				Burst.paint(g, view, stage, age, 1f, season, glow, infront);
				if (i >= 0)
				{
					took[i] = System.nanoTime() - began;
				}
			}
			g.dispose();
			System.out.println("  " + label + "  " + String.format("%-12s", season) + report(took));
		}
		Burst.release();
	}

	// So many small triangles scattered over a box on screen: the corners' x and y, then each face's three corners
	private static int[][] faces(Random random, int count, int left, int top, int wide, int tall)
	{
		int[][] model = {new int[3 * count], new int[3 * count], new int[count], new int[count], new int[count]};
		for (int face = 0; face < count; face++)
		{
			int x = left + random.nextInt(wide);
			int y = top + random.nextInt(tall);
			for (int corner = 0; corner < 3; corner++)
			{
				model[0][3 * face + corner] = x + random.nextInt(9) - 4;
				model[1][3 * face + corner] = y + random.nextInt(9) - 4;
				model[2 + corner][face] = 3 * face + corner;
			}
		}
		return model;
	}

	private static String report(long[] nanos)
	{
		long[] sorted = nanos.clone();
		Arrays.sort(sorted);
		return String.format("%.2f / %.2f / %.2f ms", sorted[sorted.length / 2] / 1e6, sorted[sorted.length * 95 / 100] / 1e6, sorted[sorted.length - 1] / 1e6);
	}
}
