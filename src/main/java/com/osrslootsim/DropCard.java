package com.osrslootsim;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.Collections;
import net.runelite.client.ui.FontManager;

/**
 * Draws the card an insane drop gets, in the look of osrslootsim.com's own celebration: charcoal with a brass frame,
 * the tier in orange capitals, the item and its name. The burst of light and the loot beam are Burst's.
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
	 * A copy of a drawing surface to draw something on at an opacity from 0 to 1, with smooth edges switched on.
	 */
	static Graphics2D begin(Graphics2D graphics, float opacity)
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
