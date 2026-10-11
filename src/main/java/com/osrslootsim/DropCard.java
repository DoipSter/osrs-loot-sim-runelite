package com.osrslootsim;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.ui.FontManager;

/**
 * Draws the pop-up card, in the look of osrslootsim.com's own celebration: charcoal with a brass frame, a title in
 * orange capitals, the item and its name. An insane drop in a simulated run gets one, and so can a new collection
 * log item in the game. The burst of light and the loot beam are Burst's.
 */
final class DropCard
{
	// The card's size before the player resizes it, and how far it can be shrunk and grown from that. A card with a
	// line over the item's name is that line taller.
	static final int WIDTH = 190;
	static final int HEIGHT = 128;
	private static final int LEAD_HEIGHT = 12;
	static final float SMALLEST = 0.6f;
	static final float LARGEST = 3f;

	private static final Color TIER = new Color(0xFF, 0x8A, 0x1F);
	private static final Color BODY = new Color(27, 26, 23, 240);
	private static final Color FRAME = new Color(206, 122, 44);
	private static final Color BRASS = new Color(164, 138, 91, 102);
	private static final Color UNLOCKED = new Color(0, 230, 255);
	private static final int TITLE_SIZE = 19;
	private static final int SMALLEST_TITLE = 12;
	private static final Font NAME = FontManager.getRunescapeBoldFont();
	private static final Font SMALL = FontManager.getRunescapeSmallFont();
	// The title's font for each kind of card, made the first time it is drawn: the largest that fits the card
	private static final Map<Kind, Font> TITLE_FONTS = new EnumMap<>(Kind.class);

	/**
	 * What a card is for, which words its title and the line over the item's name.
	 */
	enum Kind
	{
		INSANE("INSANE DROP!", null),
		// As the game's own pop-up words it
		COLLECTION_LOG("COLLECTION LOG", "New item:");

		private final String title;
		private final String lead;
		private final int height;

		Kind(String title, String lead)
		{
			this.title = title;
			this.lead = lead;
			this.height = HEIGHT + (lead == null ? 0 : LEAD_HEIGHT);
		}
	}

	/**
	 * What a card shows. The picture may arrive after the card is up.
	 */
	static final class Drop
	{
		final Kind kind;
		final String name;
		volatile BufferedImage picture;

		Drop(Kind kind, String name)
		{
			this.kind = kind;
			this.name = name;
		}
	}

	private DropCard()
	{
	}

	/**
	 * The size of the card that fits in a space, keeping its shape: 1 is its own size.
	 */
	static float scale(int width, int height)
	{
		return Math.max(SMALLEST, Math.min(LARGEST, Math.min(width / (float) WIDTH, height / (float) HEIGHT)));
	}

	/**
	 * How much of the screen a kind of card covers at a size.
	 */
	static Dimension size(Kind kind, float scale)
	{
		return new Dimension(Math.round(WIDTH * scale), Math.round(kind.height * scale));
	}

	/**
	 * The card, its top left corner at x, y. scale is its size (1 is its own); opacity runs from 0 to 1, for fading
	 * in and out.
	 */
	static void paintCard(Graphics2D graphics, int x, int y, float scale, Drop drop, float opacity)
	{
		Graphics2D g = begin(graphics, opacity);
		g.translate(x, y);
		g.scale(scale, scale);
		int w = WIDTH;
		int h = drop.kind.height;
		int centre = w / 2;
		for (int i = 1; i <= 8; i++)
		{
			g.setColor(new Color(255, 138, 31, 27 - 3 * i));
			g.drawRect(-i, -i, w + 2 * i - 1, h + 2 * i - 1);
		}
		g.setColor(BODY);
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(15, 14, 12));
		g.drawRect(-1, -1, w + 1, h + 1);
		g.setColor(FRAME);
		g.drawRect(0, 0, w - 1, h - 1);
		g.setColor(new Color(17, 15, 13));
		g.drawRect(1, 1, w - 3, h - 3);
		g.setColor(BRASS);
		g.drawRect(2, 2, w - 5, h - 5);

		String title = drop.kind.title;
		g.setFont(titleFont(g, drop.kind));
		int left = centre - g.getFontMetrics().stringWidth(title) / 2;
		int baseline = 8 + 18;
		g.setColor(new Color(255, 138, 31, 60));
		for (int[] off : new int[][]{{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}})
		{
			g.drawString(title, left + off[0], baseline + off[1]);
		}
		g.setColor(Color.BLACK);
		g.drawString(title, left + 2, baseline + 2);
		g.setColor(TIER);
		g.drawString(title, left, baseline);

		int rule = 8 + 22 + 4;
		g.setPaint(new GradientPaint(22, rule, new Color(255, 138, 31, 0), centre, rule, new Color(255, 138, 31, 204), true));
		g.fillRect(22, rule, w - 44, 1);

		int middle = rule + 1 + 3 + 32;
		g.setPaint(new RadialGradientPaint(centre, middle, 44, new float[]{0f, 0.55f, 1f},
			new Color[]{new Color(255, 242, 173, 102), new Color(255, 210, 125, 40), new Color(255, 210, 125, 0)}));
		g.fillOval(centre - 44, middle - 44, 88, 88);
		BufferedImage picture = drop.picture;
		if (picture != null)
		{
			// Inventory sprites are small pixel pictures: drawn at a whole number of times their size, twice on a card
			// of its own size, so every pixel stays a square however the card is sized
			int times = Math.max(1, Math.round(2 * scale));
			Graphics2D p = begin(graphics, opacity);
			p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			p.drawImage(picture, x + Math.round(centre * scale) - picture.getWidth() * times / 2, y + Math.round(middle * scale) - picture.getHeight() * times / 2,
				picture.getWidth() * times, picture.getHeight() * times, null);
			p.dispose();
		}

		int nameBase = h - 8 - 3;
		if (drop.kind.lead != null)
		{
			g.setFont(SMALL);
			centred(g, drop.kind.lead, centre, nameBase - 14, TIER);
		}
		g.setFont(NAME);
		centred(g, fit(g.getFontMetrics(), drop.name, w - 20), centre, nameBase, Color.WHITE);
		g.dispose();
	}

	/**
	 * Marks a card that is unlocked, over a card of the given size drawn at x, y: a bright edge, and how to move it
	 * in a strip over the card. toLeft and above are how much of the screen there is on those sides of the card: the
	 * strip goes under a card too near the top for it, and stops at the screen's left edge.
	 */
	static void paintUnlocked(Graphics2D graphics, int x, int y, Dimension size, String[] lines, int toLeft, int above)
	{
		Graphics2D g = begin(graphics, 1f);
		g.translate(x, y);
		int w = size.width;
		int h = size.height;
		g.setColor(new Color(UNLOCKED.getRed(), UNLOCKED.getGreen(), UNLOCKED.getBlue(), 26));
		g.fillRect(0, 0, w, h);
		g.setColor(UNLOCKED);
		g.setStroke(new BasicStroke(2f));
		g.drawRect(-2, -2, w + 3, h + 3);

		g.setFont(SMALL);
		FontMetrics metrics = g.getFontMetrics();
		int widest = 0;
		for (String line : lines)
		{
			widest = Math.max(widest, metrics.stringWidth(line));
		}
		int step = metrics.getHeight();
		int stripWidth = widest + 12;
		int stripHeight = step * lines.length + 6;
		int stripX = Math.max(2 - toLeft, (w - stripWidth) / 2);
		int stripY = above < stripHeight + 8 ? h + 8 : -8 - stripHeight;
		g.setColor(new Color(15, 14, 12, 225));
		g.fillRect(stripX, stripY, stripWidth, stripHeight);
		g.setColor(UNLOCKED);
		g.setStroke(new BasicStroke(1f));
		g.drawRect(stripX, stripY, stripWidth - 1, stripHeight - 1);
		for (int i = 0; i < lines.length; i++)
		{
			centred(g, lines[i], stripX + stripWidth / 2, stripY + 3 + metrics.getAscent() + step * i, Color.WHITE);
		}
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

	// A line of text with its middle at centre, over its own shadow
	private static void centred(Graphics2D g, String text, int centre, int baseline, Color colour)
	{
		int left = centre - g.getFontMetrics().stringWidth(text) / 2;
		g.setColor(Color.BLACK);
		g.drawString(text, left + 1, baseline + 1);
		g.setColor(colour);
		g.drawString(text, left, baseline);
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

	// A longer title than "INSANE DROP!" is set smaller, to stay inside the frame
	private static Font titleFont(Graphics2D g, Kind kind)
	{
		Font font = TITLE_FONTS.get(kind);
		if (font == null)
		{
			int size = TITLE_SIZE;
			font = capitals(size, 0.06f);
			while (size > SMALLEST_TITLE && g.getFontMetrics(font).stringWidth(kind.title) > WIDTH - 22)
			{
				font = capitals(--size, 0.06f);
			}
			TITLE_FONTS.put(kind, font);
		}
		return font;
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
