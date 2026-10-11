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
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
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
	private static final Path2D BAT = bat();
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
		Season season, Consumer<Graphics2D> cut)
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
		paintBurst(s, ground, footX, footY, view.y, age, season);
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
	 * milliseconds since the burst began; it drives every movement. season: the holiday it is dressed for. At Halloween
	 * the rune disc is a jack-o'-lantern and the lights climbing the beam are bats.
	 */
	static void paintBurst(Graphics2D g, AffineTransform ground, int footX, int footY, int top, long age, Season season)
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

		boolean halloween = season == Season.HALLOWEEN;
		if (halloween)
		{
			pumpkin(g, ground, swell);
		}
		else
		{
			runeDisc(g, ground, age, swell);
		}

		// One ring racing outward along the floor as the beam lands
		float raced = (age - 120) / 900f;
		if (raced > 0 && raced < 1)
		{
			g.setStroke(new BasicStroke(3f));
			g.setColor(new Color(255, 246, 216, Math.round(230 * (1 - raced))));
			g.draw(circle(ground, DISC * (0.75 + 2.2 * ease(raced))));
		}

		beamAndLights(g, footX, footY, top, age, w, swell, halloween);

		// The white-hot foot of the beam, lying on the floor under the character. Smaller on the pumpkin, to leave its
		// face in view.
		float foot = (halloween ? 0.2f : 0.42f) * ease(Math.min(1f, age / 320f));
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

	// The rune disc turning on the floor, and the ring at its heart
	private static void runeDisc(Graphics2D g, AffineTransform ground, long age, float swell)
	{
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
	}

	/**
	 * The jack-o'-lantern lying on the floor where the rune disc is, its stem pointing up the screen wherever the
	 * camera looks from, with candlelight in its face.
	 */
	private static void pumpkin(Graphics2D g, AffineTransform ground, float swell)
	{
		Graphics2D floor = (Graphics2D) g.create();
		floor.transform(ground);
		try
		{
			Point2D up = ground.createInverse().deltaTransform(new Point2D.Double(0, -1), null);
			floor.rotate(Math.atan2(up.getX(), -up.getY()));
		}
		catch (NoninvertibleTransformException e)
		{
			// a floor seen exactly edge on: left as it lies
		}
		float r = (float) DISC;
		Ellipse2D body = new Ellipse2D.Float(-r, -0.9f * r, 2 * r, 1.8f * r);
		floor.setPaint(new RadialGradientPaint(0, -0.1f * r, r * 1.05f, new float[]{0f, 0.7f, 1f},
			new Color[]{new Color(255, 160, 50, 235), new Color(236, 110, 20, 235), new Color(168, 70, 8, 235)}));
		floor.fill(body);
		floor.setStroke(new BasicStroke(0.035f));
		floor.setColor(new Color(150, 62, 6, 210));
		for (float rib : new float[]{0.36f, 0.7f})
		{
			floor.draw(new Ellipse2D.Float(-rib * r, -0.9f * r, 2 * rib * r, 1.8f * r));
		}
		floor.draw(body);
		floor.setColor(new Color(92, 122, 46, 240));
		floor.fill(new RoundRectangle2D.Float(-0.09f * r, -1.12f * r, 0.18f * r, 0.3f * r, 0.06f * r, 0.06f * r));

		// The face: two eyes, a nose and a jagged grin, lit from inside
		float[][][] cuts = {
			{{-0.52f, -0.18f}, {-0.22f, -0.18f}, {-0.37f, -0.46f}},
			{{0.52f, -0.18f}, {0.22f, -0.18f}, {0.37f, -0.46f}},
			{{-0.08f, 0.08f}, {0.08f, 0.08f}, {0f, -0.08f}},
			{{-0.62f, 0.2f}, {-0.4f, 0.32f}, {-0.28f, 0.2f}, {-0.14f, 0.34f}, {0f, 0.2f}, {0.14f, 0.34f}, {0.28f, 0.2f}, {0.4f, 0.32f}, {0.62f, 0.2f},
				{0.46f, 0.56f}, {0.3f, 0.5f}, {0.18f, 0.66f}, {0f, 0.56f}, {-0.18f, 0.66f}, {-0.3f, 0.5f}, {-0.46f, 0.56f}},
		};
		Path2D face = new Path2D.Float();
		for (float[][] cut : cuts)
		{
			face.moveTo(cut[0][0] * r, cut[0][1] * r);
			for (int i = 1; i < cut.length; i++)
			{
				face.lineTo(cut[i][0] * r, cut[i][1] * r);
			}
			face.closePath();
		}
		floor.setColor(new Color(255, 240, 150, Math.round(205 + 50 * swell)));
		floor.fill(face);
		floor.dispose();
	}

	/**
	 * The beam, and what climbs it: small lights, or bats at Halloween.
	 */
	private static void beamAndLights(Graphics2D g, int footX, int footY, int top, long age, int w, float swell, boolean bats)
	{
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

		// What climbs the beam. Bats are fewer than the lights and spread wider, sway as they rise and beat their wings.
		int strip = Math.round(w * (bats ? 2.6f : 1.4f));
		int slid = (int) (age * MOTE_TILE / 2600 % MOTE_TILE);
		for (int i = 0; i < MOTES.length; i += bats ? 2 : 1)
		{
			float[] mote = MOTES[i];
			for (int y = footY - (int) ((mote[1] + slid) % MOTE_TILE); y > beamTop; y -= MOTE_TILE)
			{
				float up = (float) (footY - y) / length;
				float seen = up < 0.16f ? Math.max(0f, (up - 0.03f) / 0.13f) : up < 0.55f ? 1f : Math.max(0f, 1f - (up - 0.55f) / 0.41f);
				if (seen <= 0)
				{
					continue;
				}
				int x = footX - strip / 2 + Math.round(mote[0] * strip);
				if (bats)
				{
					double sway = Math.sin(age / 400.0 + i) * w * 0.2;
					double beat = 0.5 + 0.5 * Math.sin(age / 70.0 + i * 1.7);
					bat(g, x + sway, y, Math.max(16, w * 0.55), beat, seen);
					continue;
				}
				g.setColor(new Color(255, 242, 173, Math.round(150 * seen)));
				g.fillOval(x - 3, y - 3, 6, 6);
				g.setColor(new Color(255, 255, 255, Math.round(255 * seen)));
				g.fillOval(x - 1, y - 1, 3, 3);
			}
		}
	}

	// One bat, so many pixels across with its wings spread. beat: 0 wings folded in, 1 spread. A pale purple edge
	// keeps it readable where it flies off the beam onto dark ground.
	private static void bat(Graphics2D g, double x, double y, double wide, double beat, float seen)
	{
		Graphics2D b = (Graphics2D) g.create();
		b.translate(x, y);
		double size = wide / 64.0;
		b.scale(size * (0.55 + 0.45 * beat), size);
		b.setStroke(new BasicStroke(4f));
		b.setColor(new Color(201, 168, 255, Math.round(140 * seen)));
		b.draw(BAT);
		b.setColor(new Color(21, 13, 31, Math.round(255 * seen)));
		b.fill(BAT);
		b.dispose();
	}

	// osrslootsim.com's own bat drawing (img/season/halloween/bat.svg), 64 by 34, centred on its middle
	private static Path2D bat()
	{
		Path2D bat = new Path2D.Float();
		bat.moveTo(0f, -8f);
		bat.lineTo(-2.6f, -14.5f);
		bat.lineTo(-4.2f, -8.2f);
		bat.curveTo(-5.9f, -7.2f, -6.9f, -5.5f, -7.1f, -3.6f);
		bat.curveTo(-11.4f, -8.8f, -17.6f, -11.3f, -24.7f, -10.5f);
		bat.curveTo(-27.5f, -10.1f, -30.1f, -8.8f, -32f, -6.8f);
		bat.curveTo(-27.2f, -6.5f, -24.1f, -3.6f, -23.3f, 0.7f);
		bat.curveTo(-21.2f, -1.3f, -18.3f, -1.3f, -16.2f, 0.8f);
		bat.curveTo(-14.9f, -1.4f, -12f, -2.1f, -10f, -0.2f);
		bat.curveTo(-8.8f, 4.2f, -5.7f, 10.1f, 0f, 16f);
		bat.curveTo(5.7f, 10.1f, 8.8f, 4.2f, 10f, -0.2f);
		bat.curveTo(12f, -2.1f, 14.9f, -1.4f, 16.2f, 0.8f);
		bat.curveTo(18.3f, -1.3f, 21.2f, -1.3f, 23.3f, 0.7f);
		bat.curveTo(24.1f, -3.6f, 27.2f, -6.5f, 32f, -6.8f);
		bat.curveTo(30.1f, -8.8f, 27.5f, -10.1f, 24.7f, -10.5f);
		bat.curveTo(17.6f, -11.3f, 11.4f, -8.8f, 7.1f, -3.6f);
		bat.curveTo(6.9f, -5.5f, 5.9f, -7.2f, 4.2f, -8.2f);
		bat.lineTo(2.6f, -14.5f);
		bat.closePath();
		return bat;
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
