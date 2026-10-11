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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.function.Consumer;
import net.runelite.client.ui.FontManager;

/**
 * Draws what an insane drop gets, in the look of osrslootsim.com's own celebration. The card: charcoal with a brass
 * frame, the tier in orange capitals, the item and its name. The burst: a flash of light across the floor from under
 * the character, a ring racing outward, and a golden loot beam on a turning rune disc, all of it behind the character.
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

	// Sizes on the floor, in tiles from where the character stands: the rune disc, and how far the flash spreads
	private static final double DISC = 0.95;
	private static final float FLASH_REACH = 3.2f;
	// The sheet the burst is drawn on before it goes over the game. Only the game's own thread draws.
	private static BufferedImage sheet;

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
	 * Lays the burst over the game behind whatever stands in it. The burst is drawn onto a sheet of its own, cut then
	 * wipes off the parts the character covers (it is handed the sheet, in the game's own coordinates, set up so
	 * anything it fills is removed), and the sheet goes onto the game. view: the part of the screen the game world is
	 * drawn in. ground: where one tile around the character lands on screen (see paintBurst).
	 */
	static void paintBurstBehind(Graphics2D target, Rectangle view, AffineTransform ground, int footX, int footY, long age, float opacity,
		Consumer<Graphics2D> cut)
	{
		double tileWide = Math.hypot(ground.getScaleX(), ground.getShearX());
		double tileTall = Math.hypot(ground.getShearY(), ground.getScaleY());
		int reachX = (int) Math.ceil(FLASH_REACH * tileWide) + 8;
		int reachY = (int) Math.ceil(FLASH_REACH * tileTall) + 8;
		Rectangle area = new Rectangle(footX - reachX, view.y, 2 * reachX, footY + reachY - view.y).intersection(view);
		if (area.isEmpty())
		{
			return;
		}
		if (sheet == null || sheet.getWidth() < area.width || sheet.getHeight() < area.height)
		{
			sheet = new BufferedImage(Math.max(area.width, sheet == null ? 0 : sheet.getWidth()), Math.max(area.height, sheet == null ? 0 : sheet.getHeight()),
				BufferedImage.TYPE_INT_ARGB_PRE);
		}
		Graphics2D s = sheet.createGraphics();
		s.setComposite(AlphaComposite.Clear);
		s.fillRect(0, 0, area.width, area.height);
		s.setComposite(AlphaComposite.SrcOver);
		s.setClip(0, 0, area.width, area.height);
		s.translate(-area.x, -area.y);
		s.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		paintBurst(s, ground, footX, footY, view.y, age);
		s.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		s.setComposite(AlphaComposite.DstOut);
		s.setColor(Color.BLACK);
		cut.accept(s);
		s.dispose();

		Graphics2D g = begin(target, opacity);
		g.drawImage(sheet, area.x, area.y, area.x + area.width, area.y + area.height, 0, 0, area.width, area.height, null);
		g.dispose();
	}

	/**
	 * The burst, on the floor under the character. ground turns a place on the floor, measured in tiles from where the
	 * character stands, into its place on screen, so everything that lies on the floor (the flash, the pool of light,
	 * the rune disc, the rings) lies as flat as the game's own floor does at this camera angle. footX, footY: where
	 * the character stands on screen. top: the top edge of the game view, where the beam leaves the screen. age:
	 * milliseconds since the burst began; it drives every movement.
	 */
	static void paintBurst(Graphics2D g, AffineTransform ground, int footX, int footY, int top, long age)
	{
		double tileWide = Math.hypot(ground.getScaleX(), ground.getShearX());
		int w = (int) Math.max(22, Math.min(90, Math.round(tileWide * 0.62)));   // the beam is a little over half a tile wide
		float swell = (float) (0.5 + 0.5 * Math.sin(age / 1200.0 * Math.PI));   // 0 to 1 and back every 2.4 s
		Graphics2D floor = (Graphics2D) g.create();
		floor.transform(ground);

		// The flash: light bursting out across the floor from under the character, gone in just over half a second
		float burst = age / 600f;
		if (burst < 1)
		{
			float reach = 0.4f + (FLASH_REACH - 0.4f) * ease(burst);
			float bright = (float) Math.pow(1 - burst, 1.5);
			floor.setPaint(new RadialGradientPaint(0, 0, reach, new float[]{0f, 0.28f, 0.76f, 1f},
				new Color[]{new Color(255, 255, 255, Math.round(242 * bright)), new Color(255, 246, 216, Math.round(235 * bright)),
					new Color(255, 210, 125, 0), new Color(255, 210, 125, 0)}));
			floor.fill(new Ellipse2D.Float(-reach, -reach, 2 * reach, 2 * reach));
		}

		// A pool of light on the floor, swelling and settling
		float pool = 1.9f + 0.2f * swell;
		floor.setPaint(new RadialGradientPaint(0, 0, pool, new float[]{0f, 1f}, new Color[]{gold(110 + 20 * swell), gold(0)}));
		floor.fill(new Ellipse2D.Float(-pool, -pool, 2 * pool, 2 * pool));
		floor.dispose();

		// The rune disc turning on the floor, and the ring at its heart
		g.setStroke(new BasicStroke(1.6f));
		g.setColor(new Color(255, 214, 110, 235));
		g.draw(circle(ground, DISC * 0.94));
		g.draw(circle(ground, DISC * 0.57));
		double turn = age / 18000.0 * 2 * Math.PI;
		for (int k = 0; k < 24; k++)
		{
			double a = turn + k * Math.PI / 12;
			double near = DISC * (k % 2 == 0 ? 0.66 : 0.74);
			double far = DISC * 0.86;
			g.draw(ground.createTransformedShape(new Line2D.Double(Math.cos(a) * near, Math.sin(a) * near, Math.cos(a) * far, Math.sin(a) * far)));
		}
		double ring = DISC * (0.44 + 0.02 * swell);
		g.setStroke(new BasicStroke(5f));
		g.setColor(new Color(255, 138, 31, 70));
		g.draw(circle(ground, ring));
		g.setStroke(new BasicStroke(2f));
		g.setColor(new Color(254, 222, 122, 230));
		g.draw(circle(ground, ring));

		// One ring racing outward along the floor as the beam lands
		float raced = (age - 120) / 900f;
		if (raced > 0 && raced < 1)
		{
			g.setStroke(new BasicStroke(3f));
			g.setColor(new Color(255, 246, 216, Math.round(230 * (1 - raced))));
			g.draw(circle(ground, DISC * (0.75 + 2.2 * ease(raced))));
		}

		// The beam shoots up from the floor in the first third of a second, then breathes. Brightest near the floor,
		// fading as it climbs: drawn in bands, each with its own strength.
		float risen = ease(Math.min(1f, age / 320f));
		int length = Math.max(1, footY - top);
		int beamTop = footY - Math.round(length * risen);
		float breath = 0.9f + 0.1f * swell;
		float[] across = {0f, 0.30f, 0.36f, 0.43f, 0.475f, 0.5f, 0.525f, 0.57f, 0.64f, 0.70f, 1f};
		int halo = Math.round(w * 1.3f);
		for (int y = beamTop; y < footY; y += 4)
		{
			float up = (float) (footY - y) / length;   // 0 at the floor, 1 at the top of the view
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

		// The white-hot foot of the beam, lying on the floor under the character
		float foot = 0.42f * risen;
		if (foot > 0.02f)
		{
			Graphics2D lying = (Graphics2D) g.create();
			lying.transform(ground);
			lying.setPaint(new RadialGradientPaint(0, 0, foot, new float[]{0f, 0.35f, 0.6f, 1f},
				new Color[]{Color.WHITE, Color.WHITE, new Color(255, 238, 160, 235), new Color(255, 196, 64, 0)}));
			lying.fill(new Ellipse2D.Float(-foot, -foot, 2 * foot, 2 * foot));
			lying.dispose();
		}
	}

	private static Color gold(float alpha)
	{
		return new Color(255, 186, 60, Math.max(0, Math.min(255, Math.round(alpha))));
	}

	// A circle on the floor around the character, so many tiles in radius, as it looks on screen
	private static Shape circle(AffineTransform ground, double radius)
	{
		return ground.createTransformedShape(new Ellipse2D.Double(-radius, -radius, 2 * radius, 2 * radius));
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
