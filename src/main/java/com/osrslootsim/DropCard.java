package com.osrslootsim;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import net.runelite.client.ui.FontManager;

/**
 * Draws what an insane drop gets, in the look of osrslootsim.com's own celebration. The card: charcoal with a brass
 * frame, the tier in orange capitals, the item and its name. The burst: a flash of light out of the character, a ring
 * racing over the ground, and a golden loot beam on a turning rune disc.
 */
final class DropCard
{
	static final int WIDTH = 190;
	static final int HEIGHT = 128;

	private static final String TITLE = "INSANE DROP!";
	private static final Color TIER = new Color(0xFF, 0x8A, 0x1F);
	private static final Color BODY = new Color(27, 26, 23, 240);
	private static final Color FRAME = new Color(206, 122, 44);
	private static final Color BRASS = new Color(164, 138, 91, 102);
	private static final Font TITLE_FONT = capitals(19, 0.06f);
	private static final Font NAME = FontManager.getRunescapeBoldFont();

	// The loot beam's lights climb it in a pattern that repeats every this many pixels: across (0 to 1), up (pixels)
	private static final int MOTE_TILE = 260;
	private static final float[][] MOTES = {{.18f, 12}, {.40f, 44}, {.66f, 70}, {.84f, 98}, {.70f, 128}, {.44f, 152}, {.22f, 178}, {.12f, 206}, {.34f, 232}, {.58f, 250}};

	/**
	 * What a card shows. The picture may arrive after the card is up.
	 */
	static final class Drop
	{
		final String name;
		volatile BufferedImage picture;

		Drop(String name)
		{
			this.name = name;
		}
	}

	private DropCard()
	{
	}

	/**
	 * The card, its top left corner at x, y. opacity from 0 to 1, for fading in and out.
	 */
	static void paintCard(Graphics2D graphics, int x, int y, Drop drop, float opacity)
	{
		Graphics2D g = begin(graphics, opacity);
		int w = WIDTH;
		int h = HEIGHT;
		int centre = x + w / 2;
		for (int i = 1; i <= 8; i++)
		{
			g.setColor(new Color(255, 138, 31, 27 - 3 * i));
			g.drawRect(x - i, y - i, w + 2 * i - 1, h + 2 * i - 1);
		}
		g.setColor(BODY);
		g.fillRect(x, y, w, h);
		g.setColor(new Color(15, 14, 12));
		g.drawRect(x - 1, y - 1, w + 1, h + 1);
		g.setColor(FRAME);
		g.drawRect(x, y, w - 1, h - 1);
		g.setColor(new Color(17, 15, 13));
		g.drawRect(x + 1, y + 1, w - 3, h - 3);
		g.setColor(BRASS);
		g.drawRect(x + 2, y + 2, w - 5, h - 5);

		g.setFont(TITLE_FONT);
		int left = centre - g.getFontMetrics().stringWidth(TITLE) / 2;
		int baseline = y + 8 + 18;
		g.setColor(new Color(255, 138, 31, 60));
		for (int[] off : new int[][]{{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}})
		{
			g.drawString(TITLE, left + off[0], baseline + off[1]);
		}
		g.setColor(Color.BLACK);
		g.drawString(TITLE, left + 2, baseline + 2);
		g.setColor(TIER);
		g.drawString(TITLE, left, baseline);

		int rule = y + 8 + 22 + 4;
		g.setPaint(new GradientPaint(x + 22, rule, new Color(255, 138, 31, 0), centre, rule, new Color(255, 138, 31, 204), true));
		g.fillRect(x + 22, rule, w - 44, 1);

		int middle = rule + 1 + 3 + 32;
		g.setPaint(new RadialGradientPaint(centre, middle, 44, new float[]{0f, 0.55f, 1f},
			new Color[]{new Color(255, 242, 173, 102), new Color(255, 210, 125, 40), new Color(255, 210, 125, 0)}));
		g.fillOval(centre - 44, middle - 44, 88, 88);
		BufferedImage picture = drop.picture;
		if (picture != null)
		{
			// Inventory sprites are small: drawn at twice their size, pixel for pixel
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(picture, centre - picture.getWidth(), middle - picture.getHeight(), picture.getWidth() * 2, picture.getHeight() * 2, null);
		}

		g.setFont(NAME);
		FontMetrics metrics = g.getFontMetrics();
		String name = fit(metrics, drop.name, w - 20);
		int nameLeft = centre - metrics.stringWidth(name) / 2;
		int nameBase = y + h - 8 - 3;
		g.setColor(Color.BLACK);
		g.drawString(name, nameLeft + 1, nameBase + 1);
		g.setColor(Color.WHITE);
		g.drawString(name, nameLeft, nameBase);
		g.dispose();
	}

	/**
	 * The burst on the character. footX, footY: where it stands on screen. headY: the top of its head. top: the top
	 * edge of the game view, where the beam leaves the screen. age: milliseconds since the burst began; it drives
	 * every movement.
	 */
	static void paintBurst(Graphics2D graphics, int footX, int footY, int headY, int top, long age, float opacity)
	{
		Graphics2D g = begin(graphics, opacity);
		int body = Math.max(40, footY - headY);
		int w = Math.max(34, Math.min(80, Math.round(body * 0.55f)));   // the beam's width follows the character's size
		int middle = footY - body / 2;
		float swell = (float) (0.5 + 0.5 * Math.sin(age / 1200.0 * Math.PI));   // 0 to 1 and back every 2.4 s
		AffineTransform flat = g.getTransform();

		// A warm glow around the character, swelling and settling
		float glowWide = w * 2.4f;
		float glowTall = Math.max(body * 1.5f, 90f);
		g.translate(footX, middle);
		g.scale(1, glowTall / glowWide);
		g.setPaint(new RadialGradientPaint(0, 0, glowWide, new float[]{0f, 0.72f, 1f},
			new Color[]{gold(60 + 30 * swell), gold(0), gold(0)}));
		g.fillOval(Math.round(-glowWide), Math.round(-glowWide), Math.round(2 * glowWide), Math.round(2 * glowWide));
		g.setTransform(flat);

		// The ground: a pool of light, the rune disc turning in it, and the ring at its heart
		float lie = 0.26f;   // how flat a circle on the ground looks from the game's camera
		g.translate(footX, footY);
		g.scale(1, lie);
		g.setPaint(new RadialGradientPaint(0, 0, w * 3f, new float[]{0f, 1f}, new Color[]{gold(107), gold(0)}));
		g.fillOval(-w * 3, -w * 3, w * 6, w * 6);
		g.setTransform(flat);

		float disc = w * 1.4f;
		Color line = new Color(255, 214, 110, 235);
		g.setStroke(new BasicStroke(1.6f));
		g.setColor(line);
		oval(g, footX, footY, disc * 0.94f, lie);
		oval(g, footX, footY, disc * 0.57f, lie);
		double turn = age / 18000.0 * 2 * Math.PI;
		for (int k = 0; k < 24; k++)
		{
			double a = turn + k * Math.PI / 12;
			double near = k % 2 == 0 ? 0.66 : 0.74;
			g.draw(new Line2D.Double(footX + Math.cos(a) * disc * near, footY + Math.sin(a) * disc * near * lie,
				footX + Math.cos(a) * disc * 0.86, footY + Math.sin(a) * disc * 0.86 * lie));
		}
		float ring = w * (0.7f + 0.03f * swell);
		g.setStroke(new BasicStroke(5f));
		g.setColor(new Color(255, 138, 31, 70));
		oval(g, footX, footY, ring, lie);
		g.setStroke(new BasicStroke(2f));
		g.setColor(new Color(254, 222, 122, 230));
		oval(g, footX, footY, ring, lie);

		// One ring racing outward along the ground as the beam lands
		float raced = (age - 120) / 900f;
		if (raced > 0 && raced < 1)
		{
			g.setStroke(new BasicStroke(3f));
			g.setColor(new Color(255, 246, 216, Math.round(230 * (1 - raced))));
			oval(g, footX, footY, w * (1.05f + 3f * ease(raced)), lie);
		}

		// The beam shoots up in the first third of a second, then breathes. Brightest near the ground, fading as it
		// climbs: drawn in bands, each with its own strength.
		float risen = ease(Math.min(1f, age / 320f));
		int length = Math.max(1, footY - top);
		int beamTop = footY - Math.round(length * risen);
		float breath = 0.9f + 0.1f * swell;
		float[] across = {0f, 0.30f, 0.36f, 0.43f, 0.475f, 0.5f, 0.525f, 0.57f, 0.64f, 0.70f, 1f};
		int halo = Math.round(w * 1.3f);
		for (int y = beamTop; y < footY; y += 4)
		{
			float up = (float) (footY - y) / length;   // 0 at the ground, 1 at the top of the view
			float strength = (up < 0.05f ? up / 0.05f : up < 0.24f ? 1f : 1f - 0.45f * (up - 0.24f) / 0.76f) * breath;
			Color none = new Color(255, 170, 30, 0);
			Color amber = new Color(255, 170, 30, Math.round(97 * strength));
			Color yellow = new Color(255, 200, 61, Math.round(209 * strength));
			Color pale = new Color(255, 236, 150, Math.round(245 * strength));
			Color white = new Color(255, 251, 230, Math.round(255 * strength));
			g.setPaint(new LinearGradientPaint(footX - halo, y, footX + halo, y, across,
				new Color[]{none, none, amber, yellow, pale, white, pale, yellow, amber, none, none}));
			g.fillRect(footX - halo, y, 2 * halo, Math.min(4, footY - y));
		}

		// Small lights climbing the beam
		int strip = Math.round(w * 1.4f);
		int slid = (int) (age * MOTE_TILE / 2600 % MOTE_TILE);
		for (float[] mote : MOTES)
		{
			for (int y = footY - (int) ((mote[1] + slid) % MOTE_TILE); y > beamTop; y -= MOTE_TILE)
			{
				float up = (float) (footY - y) / length;
				float seen = up < 0.16f ? Math.max(0f, (up - 0.03f) / 0.13f) : up < 0.55f ? 1f : Math.max(0f, 1f - (up - 0.55f) / 0.41f);
				if (seen <= 0)
				{
					continue;
				}
				int x = footX - strip / 2 + Math.round(mote[0] * strip);
				g.setColor(new Color(255, 242, 173, Math.round(150 * seen)));
				g.fillOval(x - 3, y - 3, 6, 6);
				g.setColor(new Color(255, 255, 255, Math.round(255 * seen)));
				g.fillOval(x - 1, y - 1, 3, 3);
			}
		}

		// The white-hot bulb where the beam meets the ground
		float bulb = w * 0.8f * risen;
		if (bulb > 1)
		{
			g.setPaint(new RadialGradientPaint(footX, footY - bulb * 0.5f, bulb, new float[]{0f, 0.3f, 0.55f, 1f},
				new Color[]{Color.WHITE, Color.WHITE, new Color(255, 238, 160, 235), new Color(255, 196, 64, 0)}));
			g.fillOval(Math.round(footX - bulb), Math.round(footY - bulb * 1.5f), Math.round(2 * bulb), Math.round(2 * bulb));
		}

		// The flash: light bursting out of the character and gone in just over half a second
		float burst = age / 600f;
		if (burst < 1)
		{
			float reach = 30 + (Math.max(w * 4f, 220f) - 30) * ease(burst);
			float bright = (float) Math.pow(1 - burst, 1.5);
			g.setPaint(new RadialGradientPaint(footX, middle, reach, new float[]{0f, 0.28f, 0.76f, 1f},
				new Color[]{new Color(255, 255, 255, Math.round(242 * bright)), new Color(255, 246, 216, Math.round(235 * bright)),
					new Color(255, 210, 125, 0), new Color(255, 210, 125, 0)}));
			g.fillOval(Math.round(footX - reach), Math.round(middle - reach), Math.round(2 * reach), Math.round(2 * reach));
		}
		g.dispose();
	}

	private static Color gold(float alpha)
	{
		return new Color(255, 186, 60, Math.max(0, Math.min(255, Math.round(alpha))));
	}

	// A circle lying on the ground: wide as given, flattened by the camera's angle
	private static void oval(Graphics2D g, int centreX, int centreY, float radius, float lie)
	{
		int wide = Math.round(radius);
		int tall = Math.round(radius * lie);
		g.drawOval(centreX - wide, centreY - tall, 2 * wide, 2 * tall);
	}

	// Fast at first, then settling
	private static float ease(float t)
	{
		float left = 1 - Math.max(0f, Math.min(1f, t));
		return 1 - left * left * left;
	}

	private static Graphics2D begin(Graphics2D graphics, float opacity)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Composite now = g.getComposite();
		float held = now instanceof AlphaComposite ? ((AlphaComposite) now).getAlpha() : 1f;
		g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, held * opacity))));
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		return g;
	}

	private static String fit(FontMetrics metrics, String text, int room)
	{
		if (metrics.stringWidth(text) <= room)
		{
			return text;
		}
		String cut = text;
		while (cut.length() > 1 && metrics.stringWidth(cut + "...") > room)
		{
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "...";
	}

	// The site sets the tier in Palatino capitals; any serif stands in where that font is missing
	private static Font capitals(int size, float tracking)
	{
		Font font = new Font("Palatino Linotype", Font.BOLD, size);
		if (!"Palatino Linotype".equalsIgnoreCase(font.getFamily()))
		{
			font = new Font(Font.SERIF, Font.BOLD, size);
		}
		return font.deriveFont(Collections.singletonMap(TextAttribute.TRACKING, tracking));
	}
}
