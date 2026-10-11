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
import java.awt.image.BufferedImage;
import java.util.Collections;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Draws the pop-up for an insane drop, in the look of osrslootsim.com's own: a charcoal card with a brass frame and
 * the tier in orange capitals, and for the large one a golden loot beam behind it. Two sizes: a one-line card for the
 * chatbox, and the full card that stands in front of the character.
 */
final class DropCard
{
	static final int LARGE_WIDTH = 200;
	static final int LARGE_HEIGHT = 195;
	static final int SMALL_WIDTH = 262;
	static final int SMALL_HEIGHT = 44;

	private static final String TITLE = "INSANE DROP!";
	private static final Color TIER = new Color(0xFF, 0x8A, 0x1F);
	private static final Color BODY = new Color(27, 26, 23, 240);
	private static final Color FRAME = new Color(206, 122, 44);
	private static final Color BRASS = new Color(164, 138, 91, 102);
	private static final Color GOLD = new Color(255, 186, 60);
	private static final Color PALE = new Color(255, 242, 173);
	private static final Font LARGE_TITLE = capitals(20, 0.06f);
	private static final Font SMALL_TITLE = capitals(12, 0.08f);
	private static final Font NAME = FontManager.getRunescapeBoldFont();
	private static final Font SMALL = FontManager.getRunescapeSmallFont();
	private static final String DOT = SMALL.canDisplay('·') ? " · " : "  ";

	/**
	 * What a pop-up says. The picture may arrive after the pop-up is up.
	 */
	static final class Drop
	{
		final String name;
		final String rate;
		final String value;
		final int more;
		volatile BufferedImage picture;

		/**
		 * rate: "1/5,000", or nothing when the site gave none. more: how many other insane drops the run had.
		 */
		Drop(String name, String rate, String value, int more)
		{
			this.name = name;
			this.rate = rate;
			this.value = value;
			this.more = more;
		}
	}

	private DropCard()
	{
	}

	/**
	 * The full card, its top left corner at x, y. opacity from 0 to 1, for fading in and out.
	 */
	static void paintLarge(Graphics2D graphics, int x, int y, Drop drop, float opacity)
	{
		Graphics2D g = begin(graphics, opacity);
		int w = LARGE_WIDTH;
		int h = LARGE_HEIGHT;
		int centre = x + w / 2;
		for (int i = 1; i <= 10; i++)
		{
			g.setColor(new Color(255, 138, 31, 22 - 2 * i));
			g.drawRect(x - i, y - i, w + 2 * i - 1, h + 2 * i - 1);
		}
		frame(g, x, y, w, h);

		int line = y + 12;
		title(g, LARGE_TITLE, TITLE, centre, line + 19, true);
		line += 24 + 5;
		g.setPaint(new GradientPaint(x + 24, line, new Color(255, 138, 31, 0), centre, line, new Color(255, 138, 31, 204), true));
		g.fillRect(x + 24, line, w - 48, 1);
		line += 1 + 5;

		int pictureMiddle = line + 39;
		g.setPaint(new RadialGradientPaint(centre, pictureMiddle, 50, new float[]{0f, 0.55f, 1f},
			new Color[]{new Color(255, 242, 173, 102), new Color(255, 210, 125, 40), new Color(255, 210, 125, 0)}));
		g.fillOval(centre - 50, pictureMiddle - 50, 100, 100);
		BufferedImage picture = drop.picture;
		if (picture != null)
		{
			// Inventory sprites are small: drawn at twice their size, pixel for pixel
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(picture, centre - picture.getWidth(), pictureMiddle - picture.getHeight(), picture.getWidth() * 2, picture.getHeight() * 2, null);
		}
		line += 78 + 4;

		centred(g, NAME, drop.name, Color.WHITE, centre, line + 13, w - 24);
		line += 16 + 4;
		String worth = drop.rate.isEmpty() ? drop.value : drop.rate + DOT + drop.value;
		centred(g, SMALL, worth, ColorScheme.TEXT_COLOR, centre, line + 12, w - 24);
		line += 16 + 4;
		centred(g, SMALL, drop.more > 0 ? "Simulated drop" + DOT + "+" + drop.more + " more" : "Simulated drop", ColorScheme.LIGHT_GRAY_COLOR, centre, line + 12, w - 24);
		g.dispose();
	}

	/**
	 * The one-line card for the chatbox, its top left corner at x, y.
	 */
	static void paintSmall(Graphics2D graphics, int x, int y, Drop drop, float opacity)
	{
		Graphics2D g = begin(graphics, opacity);
		int w = SMALL_WIDTH;
		int h = SMALL_HEIGHT;
		for (int i = 1; i <= 4; i++)
		{
			g.setColor(new Color(255, 138, 31, 40 - 9 * i));
			g.drawRect(x - i, y - i, w + 2 * i - 1, h + 2 * i - 1);
		}
		frame(g, x, y, w, h);
		BufferedImage picture = drop.picture;
		if (picture != null)
		{
			g.drawImage(picture, x + 6 + (36 - picture.getWidth()) / 2, y + (h - picture.getHeight()) / 2, null);
		}
		int left = x + 48;
		int room = w - 48 - 8;
		g.setFont(SMALL_TITLE);
		g.setColor(Color.BLACK);
		g.drawString(TITLE, left + 1, y + 18);
		g.setColor(TIER);
		g.drawString(TITLE, left, y + 17);
		int titleWidth = g.getFontMetrics().stringWidth(TITLE);

		g.setFont(SMALL);
		FontMetrics small = g.getFontMetrics();
		// Beside the title: that the drop is a simulated one, and how many other insane drops the run had. The
		// shorter wording is for a count too long to fit.
		String[] notes = drop.more > 0 ? new String[]{"simulated, +" + drop.more + " more", "sim, +" + drop.more} : new String[]{"simulated"};
		for (String note : notes)
		{
			if (titleWidth + 8 + small.stringWidth(note) <= room)
			{
				g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
				g.drawString(note, left + room - small.stringWidth(note), y + 17);
				break;
			}
		}
		String line = fit(small, drop.rate.isEmpty() ? drop.name : drop.name + DOT + drop.rate, room);
		g.setColor(Color.BLACK);
		g.drawString(line, left + 1, y + 35);
		g.setColor(Color.WHITE);
		g.drawString(line, left, y + 34);
		g.dispose();
	}

	/**
	 * The loot beam: a golden column from the top of the view down to the character's feet, with a ring and a pool of
	 * light on the ground.
	 */
	static void paintBeam(Graphics2D graphics, int footX, int footY, int top, float opacity)
	{
		Graphics2D g = begin(graphics, opacity);
		AffineTransform flat = g.getTransform();
		g.translate(footX, footY);
		g.scale(1, 0.24);
		g.setPaint(new RadialGradientPaint(0, 0, 110, new float[]{0f, 1f}, new Color[]{new Color(255, 186, 60, 107), new Color(255, 186, 60, 0)}));
		g.fillOval(-110, -110, 220, 220);
		g.setTransform(flat);

		// The column, brightest at the ground and fading as it climbs: drawn in bands, each a little fainter
		int half = 45;
		int length = Math.max(1, footY - top);
		float[] across = {0f, 0.3f, 0.5f, 0.7f, 1f};
		for (int y = top; y < footY; y += 4)
		{
			float climbed = 1f - (float) (y - top) / length;
			float strength = 1f - 0.7f * Math.min(1f, climbed / 0.65f);
			Color gold = new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), Math.round(128 * strength));
			Color pale = new Color(PALE.getRed(), PALE.getGreen(), PALE.getBlue(), Math.round(242 * strength));
			Color none = new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 0);
			g.setPaint(new LinearGradientPaint(footX - half, y, footX + half, y, across, new Color[]{none, gold, pale, gold, none}));
			g.fillRect(footX - half, y, 2 * half, Math.min(4, footY - y));
		}

		g.setStroke(new BasicStroke(5f));
		g.setColor(new Color(255, 138, 31, 70));
		g.drawOval(footX - 61, footY - 17, 122, 34);
		g.setStroke(new BasicStroke(2f));
		g.setColor(new Color(254, 222, 122, 230));
		g.drawOval(footX - 60, footY - 16, 120, 32);
		g.dispose();
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

	// The card's body and its frame: a dark line, the tier's orange, a dark line and a thin brass one inside
	private static void frame(Graphics2D g, int x, int y, int w, int h)
	{
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
	}

	private static void title(Graphics2D g, Font font, String text, int centre, int baseline, boolean glow)
	{
		g.setFont(font);
		int left = centre - g.getFontMetrics().stringWidth(text) / 2;
		if (glow)
		{
			g.setColor(new Color(255, 138, 31, 60));
			for (int[] off : new int[][]{{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}})
			{
				g.drawString(text, left + off[0], baseline + off[1]);
			}
		}
		g.setColor(Color.BLACK);
		g.drawString(text, left + 2, baseline + 2);
		g.setColor(TIER);
		g.drawString(text, left, baseline);
	}

	private static void centred(Graphics2D g, Font font, String text, Color color, int centre, int baseline, int room)
	{
		g.setFont(font);
		FontMetrics metrics = g.getFontMetrics();
		String shown = fit(metrics, text, room);
		int left = centre - metrics.stringWidth(shown) / 2;
		g.setColor(Color.BLACK);
		g.drawString(shown, left + 1, baseline + 1);
		g.setColor(color);
		g.drawString(shown, left, baseline);
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
