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
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
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
	private static final Path2D LEAF = leaf();
	private static final Path2D FLAKE = flake();

	/**
	 * The colours of the beam and of the light on the floor: the site's gold, or the purple of its Looting icon. From
	 * the beam's soft edge in to its core, then the floor's pool, the rune disc's lines, and the ring with its glow.
	 */
	enum Glow
	{
		GOLD(rgb(255, 170, 30), rgb(255, 200, 61), rgb(255, 236, 150), rgb(255, 251, 230), rgb(255, 186, 60), rgb(255, 214, 110), rgb(254, 222, 122), rgb(255, 138, 31)),
		PURPLE(rgb(120, 44, 226), rgb(155, 61, 255), rgb(214, 180, 255), rgb(246, 238, 255), rgb(155, 61, 255), rgb(201, 168, 255), rgb(233, 210, 255), rgb(120, 44, 226));

		final int[] edge;
		final int[] body;
		final int[] pale;
		final int[] core;
		final int[] pool;
		final int[] line;
		final int[] ring;
		final int[] ringGlow;

		Glow(int[] edge, int[] body, int[] pale, int[] core, int[] pool, int[] line, int[] ring, int[] ringGlow)
		{
			this.edge = edge;
			this.body = body;
			this.pale = pale;
			this.core = core;
			this.pool = pool;
			this.line = line;
			this.ring = ring;
			this.ringGlow = ringGlow;
		}

		private static int[] rgb(int red, int green, int blue)
		{
			return new int[]{red, green, blue};
		}
	}
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
		Season season, Glow glow, Consumer<Graphics2D> cut)
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
		paintBurst(s, ground, footX, footY, view.y, age, season, glow);
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
	 * milliseconds since the burst began; it drives every movement. season: the holiday it is dressed for, which puts a
	 * picture on the floor where the rune disc is and sends something of its own up the beam: a jack-o'-lantern and bats
	 * at Halloween, a roast turkey and leaves at Thanksgiving, a snow globe and snowflakes at Christmas. glow: the colours
	 * of the light.
	 */
	static void paintBurst(Graphics2D g, AffineTransform ground, int footX, int footY, int top, long age, Season season, Glow glow)
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
				new Color[]{new Color(255, 255, 255, Math.round(242 * bright)), tint(glow.core, 235 * bright), tint(glow.pale, 0), tint(glow.pale, 0)}));
			floor.fill(new Ellipse2D.Float(-reach, -reach, 2 * reach, 2 * reach));
		}

		// A pool of light on the floor, swelling and settling
		float pool = 1.9f + 0.2f * swell;
		floor.setPaint(new RadialGradientPaint(0, 0, pool, new float[]{0f, 1f}, new Color[]{tint(glow.pool, 110 + 20 * swell), tint(glow.pool, 0)}));
		floor.fill(new Ellipse2D.Float(-pool, -pool, 2 * pool, 2 * pool));
		floor.dispose();

		// What lies on the floor under the character: the rune disc, or the holiday's own picture
		boolean dressed = season != Season.NONE;
		switch (season)
		{
			case HALLOWEEN:
				pumpkin(g, ground, swell);
				break;
			case THANKSGIVING:
				turkey(g, ground);
				break;
			case CHRISTMAS:
				snowGlobe(g, ground, age);
				break;
			default:
				runeDisc(g, ground, age, swell, glow);
		}

		// One ring racing outward along the floor as the beam lands
		float raced = (age - 120) / 900f;
		if (raced > 0 && raced < 1)
		{
			g.setStroke(new BasicStroke(3f));
			g.setColor(tint(glow.core, 230 * (1 - raced)));
			g.draw(circle(ground, DISC * (0.75 + 2.2 * ease(raced))));
		}

		beamAndLights(g, footX, footY, top, age, w, swell, season, glow);

		// The white-hot foot of the beam, lying on the floor under the character. Smaller on a holiday's picture, to
		// leave the picture in view.
		float foot = (dressed ? 0.2f : 0.42f) * ease(Math.min(1f, age / 320f));
		if (foot > 0.02f)
		{
			Graphics2D lying = (Graphics2D) g.create();
			lying.transform(ground);
			lying.setPaint(new RadialGradientPaint(0, 0, foot, new float[]{0f, 0.35f, 0.6f, 1f},
				new Color[]{Color.WHITE, Color.WHITE, tint(glow.pale, 235), tint(glow.body, 0)}));
			lying.fill(new Ellipse2D.Float(-foot, -foot, 2 * foot, 2 * foot));
			lying.dispose();
		}
	}

	// The rune disc turning on the floor, and the ring at its heart
	private static void runeDisc(Graphics2D g, AffineTransform ground, long age, float swell, Glow glow)
	{
		g.setStroke(new BasicStroke(1.6f));
		g.setColor(tint(glow.line, 235));
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
		g.setColor(tint(glow.ringGlow, 70));
		g.draw(circle(ground, ring));
		g.setStroke(new BasicStroke(2f));
		g.setColor(tint(glow.ring, 230));
		g.draw(circle(ground, ring));
	}

	/**
	 * The jack-o'-lantern lying on the floor where the rune disc is, its stem pointing up the screen wherever the
	 * camera looks from, with candlelight in its face.
	 */
	private static void pumpkin(Graphics2D g, AffineTransform ground, float swell)
	{
		Graphics2D floor = facing(g, ground);
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
	 * A sheet to draw a picture on that lies on the floor under the character, measured in tiles, with "up" on the
	 * sheet pointing up the screen wherever the camera looks from. The pictures are the same left and right, so they
	 * read the right way round from any side.
	 */
	private static Graphics2D facing(Graphics2D g, AffineTransform ground)
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
		return floor;
	}

	/**
	 * A roast turkey on a platter, lying on the floor where the rune disc is: drumsticks to the far side, greens and
	 * cranberries along the near rim.
	 */
	private static void turkey(Graphics2D g, AffineTransform ground)
	{
		Graphics2D floor = facing(g, ground);
		float r = (float) DISC;
		Ellipse2D platter = new Ellipse2D.Float(-r, -0.9f * r, 2 * r, 1.8f * r);
		floor.setPaint(new RadialGradientPaint(0, 0, r, new float[]{0f, 0.78f, 1f},
			new Color[]{new Color(250, 244, 230, 240), new Color(236, 226, 204, 240), new Color(196, 182, 154, 240)}));
		floor.fill(platter);
		floor.setStroke(new BasicStroke(0.03f));
		floor.setColor(new Color(150, 136, 108, 220));
		floor.draw(platter);
		floor.draw(new Ellipse2D.Float(-0.8f * r, -0.72f * r, 1.6f * r, 1.44f * r));

		// greens under the bird, and cranberries
		floor.setColor(new Color(86, 150, 60, 240));
		for (float[] leaf : new float[][]{{-0.55f, 0.42f}, {-0.2f, 0.56f}, {0.2f, 0.56f}, {0.55f, 0.42f}, {-0.68f, 0.12f}, {0.68f, 0.12f}})
		{
			floor.fill(new Ellipse2D.Float((leaf[0] - 0.17f) * r, (leaf[1] - 0.11f) * r, 0.34f * r, 0.22f * r));
		}
		floor.setColor(new Color(190, 30, 50, 245));
		for (float[] berry : new float[][]{{-0.4f, 0.6f}, {0f, 0.68f}, {0.4f, 0.6f}})
		{
			floor.fill(new Ellipse2D.Float((berry[0] - 0.05f) * r, (berry[1] - 0.05f) * r, 0.1f * r, 0.1f * r));
		}

		// the drumsticks, each with its bone end, then the body over them
		for (int side = -1; side <= 1; side += 2)
		{
			Graphics2D leg = (Graphics2D) floor.create();
			leg.translate(side * 0.44f * r, -0.3f * r);
			leg.rotate(side * Math.toRadians(32));
			leg.setColor(new Color(250, 246, 236, 250));
			leg.fill(new RoundRectangle2D.Float(-0.045f * r, -0.5f * r, 0.09f * r, 0.26f * r, 0.04f * r, 0.04f * r));
			leg.fill(new Ellipse2D.Float(-0.1f * r, -0.56f * r, 0.11f * r, 0.11f * r));
			leg.fill(new Ellipse2D.Float(-0.01f * r, -0.56f * r, 0.11f * r, 0.11f * r));
			leg.setPaint(new RadialGradientPaint(0, 0, 0.3f * r, new float[]{0f, 1f}, new Color[]{new Color(214, 138, 58, 250), new Color(150, 82, 26, 250)}));
			leg.fill(new Ellipse2D.Float(-0.17f * r, -0.3f * r, 0.34f * r, 0.56f * r));
			leg.dispose();
		}
		floor.setPaint(new RadialGradientPaint(0, -0.02f * r, 0.62f * r, new float[]{0f, 0.6f, 1f},
			new Color[]{new Color(236, 168, 84, 250), new Color(206, 128, 50, 250), new Color(146, 80, 24, 250)}));
		floor.fill(new Ellipse2D.Float(-0.6f * r, -0.36f * r, 1.2f * r, 0.86f * r));
		floor.setColor(new Color(255, 214, 150, 90));
		floor.fill(new Ellipse2D.Float(-0.3f * r, -0.26f * r, 0.6f * r, 0.3f * r));
		floor.dispose();
	}

	/**
	 * A snow globe with a snowman inside, lying on the floor where the rune disc is, its base to the near side. A few
	 * specks of snow drift down inside the glass.
	 */
	private static void snowGlobe(Graphics2D g, AffineTransform ground, long age)
	{
		Graphics2D floor = facing(g, ground);
		float r = (float) DISC;
		// the base, with a gold band
		floor.setColor(new Color(150, 30, 36, 245));
		floor.fill(new RoundRectangle2D.Float(-0.62f * r, 0.5f * r, 1.24f * r, 0.46f * r, 0.12f * r, 0.12f * r));
		floor.setColor(new Color(236, 196, 84, 245));
		floor.fill(new Rectangle2D.Float(-0.62f * r, 0.66f * r, 1.24f * r, 0.08f * r));

		// the glass
		float cy = -0.14f * r;
		float glass = 0.8f * r;
		Ellipse2D ball = new Ellipse2D.Float(-glass, cy - glass, 2 * glass, 2 * glass);
		floor.setPaint(new RadialGradientPaint(-0.25f * r, cy - 0.3f * r, glass * 1.5f, new float[]{0f, 0.6f, 1f},
			new Color[]{new Color(214, 238, 255, 215), new Color(150, 198, 240, 215), new Color(96, 146, 206, 225)}));
		floor.fill(ball);
		Graphics2D inside = (Graphics2D) floor.create();
		inside.clip(ball);
		// snow on the ground, and the snowman: body, head, hat, scarf, eyes, nose and buttons
		inside.setColor(new Color(250, 253, 255, 250));
		inside.fill(new Ellipse2D.Float(-glass, cy + 0.42f * r, 2 * glass, 0.7f * r));
		inside.fill(new Ellipse2D.Float(-0.27f * r, cy + 0.02f * r, 0.54f * r, 0.54f * r));
		inside.fill(new Ellipse2D.Float(-0.19f * r, cy - 0.3f * r, 0.38f * r, 0.38f * r));
		inside.setColor(new Color(196, 36, 44, 250));
		inside.fill(new RoundRectangle2D.Float(-0.2f * r, cy + 0.02f * r, 0.4f * r, 0.08f * r, 0.04f * r, 0.04f * r));
		inside.setColor(new Color(30, 30, 38, 250));
		inside.fill(new Rectangle2D.Float(-0.13f * r, cy - 0.52f * r, 0.26f * r, 0.22f * r));
		inside.fill(new Rectangle2D.Float(-0.21f * r, cy - 0.32f * r, 0.42f * r, 0.05f * r));
		for (float[] dot : new float[][]{{-0.07f, -0.16f}, {0.07f, -0.16f}, {0f, 0.2f}, {0f, 0.34f}})
		{
			inside.fill(new Ellipse2D.Float((dot[0] - 0.025f) * r, cy + (dot[1] - 0.025f) * r, 0.05f * r, 0.05f * r));
		}
		Path2D nose = new Path2D.Float();
		nose.moveTo(0, cy - 0.1f * r);
		nose.lineTo(0, cy - 0.04f * r);
		nose.lineTo(0.16f * r, cy - 0.06f * r);
		nose.closePath();
		inside.setColor(new Color(240, 138, 40, 250));
		inside.fill(nose);
		// specks of snow, falling slowly and coming round again
		inside.setColor(new Color(255, 255, 255, 235));
		for (float[] speck : MOTES)
		{
			float fall = ((speck[1] / MOTE_TILE + age / 6000f) % 1f) * 2 - 1;
			inside.fill(new Ellipse2D.Float(((speck[0] - 0.5f) * 1.5f - 0.02f) * r, cy + fall * glass - 0.02f * r, 0.045f * r, 0.045f * r));
		}
		inside.dispose();
		// the rim, and a streak of light on the glass
		floor.setStroke(new BasicStroke(0.035f));
		floor.setColor(new Color(236, 246, 255, 235));
		floor.draw(ball);
		floor.setStroke(new BasicStroke(0.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		floor.setColor(new Color(255, 255, 255, 150));
		floor.draw(new Arc2D.Float(-glass * 0.78f, cy - glass * 0.78f, glass * 1.56f, glass * 1.56f, 110, 50, Arc2D.OPEN));
		floor.dispose();
	}

	/**
	 * The beam, and what climbs it: small lights, or the holiday's own (bats, leaves, snowflakes), which are fewer than
	 * the lights, spread wider and sway as they rise.
	 */
	private static void beamAndLights(Graphics2D g, int footX, int footY, int top, long age, int w, float swell, Season season, Glow glow)
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
			Color none = tint(glow.edge, 0);
			Color amber = tint(glow.edge, 97 * strength);
			Color yellow = tint(glow.body, 209 * strength);
			Color pale = tint(glow.pale, 245 * strength);
			Color white = tint(glow.core, 255 * strength);
			g.setPaint(new LinearGradientPaint(footX - halo, y, footX + halo, y, across,
				new Color[]{none, none, amber, yellow, pale, white, pale, yellow, amber, none, none}));
			g.fillRect(footX - halo, y, 2 * halo, Math.min(4, footY - y));
		}

		boolean dressed = season != Season.NONE;
		int strip = Math.round(w * (dressed ? 2.6f : 1.4f));
		int slid = (int) (age * MOTE_TILE / 2600 % MOTE_TILE);
		for (int i = 0; i < MOTES.length; i += dressed ? 2 : 1)
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
				if (dressed)
				{
					double sway = Math.sin(age / 400.0 + i) * w * 0.2;
					double size = Math.max(16, w * 0.55);
					switch (season)
					{
						case HALLOWEEN:
							bat(g, x + sway, y, size, 0.5 + 0.5 * Math.sin(age / 70.0 + i * 1.7), seen);
							break;
						case THANKSGIVING:
							leaf(g, x + sway, y, size * 0.8, age / 450.0 + i * 1.3, i / 2, seen);
							break;
						default:
							snowflake(g, x + sway, y, size * 0.75, age / 1100.0 + i, seen);
					}
					continue;
				}
				g.setColor(tint(glow.pale, 150 * seen));
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

	// One maple leaf, turned by so much, in one of three autumn colours
	private static void leaf(Graphics2D g, double x, double y, double wide, double turned, int colour, float seen)
	{
		int[][] autumn = {{245, 165, 58}, {232, 85, 63}, {240, 200, 70}};
		int[] rgb = autumn[Math.floorMod(colour, autumn.length)];
		Graphics2D l = (Graphics2D) g.create();
		l.translate(x, y);
		l.rotate(turned);
		l.scale(wide / 64.0, wide / 64.0);
		l.setStroke(new BasicStroke(3f));
		l.setColor(new Color(60, 28, 8, Math.round(170 * seen)));
		l.draw(LEAF);
		l.setColor(new Color(rgb[0], rgb[1], rgb[2], Math.round(255 * seen)));
		l.fill(LEAF);
		l.dispose();
	}

	// One snowflake, turned by so much
	private static void snowflake(Graphics2D g, double x, double y, double wide, double turned, float seen)
	{
		Graphics2D f = (Graphics2D) g.create();
		f.translate(x, y);
		f.rotate(turned);
		f.scale(wide / 64.0, wide / 64.0);
		f.setStroke(new BasicStroke(3f));
		f.setColor(new Color(70, 120, 190, Math.round(150 * seen)));
		f.draw(FLAKE);
		f.setColor(new Color(240, 250, 255, Math.round(255 * seen)));
		f.fill(FLAKE);
		f.dispose();
	}

	// osrslootsim.com's own maple leaf (img/season/thanksgiving/flyer.svg), 64 by 68, centred on its middle
	private static Path2D leaf()
	{
		Path2D leaf = new Path2D.Float();
		leaf.moveTo(0f, -33f);
		leaf.lineTo(3.5f, -24f);
		leaf.lineTo(8.5f, -26.5f);
		leaf.lineTo(7.5f, -16f);
		leaf.lineTo(13f, -17f);
		leaf.lineTo(6.5f, -3.5f);
		leaf.lineTo(15.5f, -9.5f);
		leaf.lineTo(16.5f, -17.5f);
		leaf.lineTo(20.5f, -12.5f);
		leaf.lineTo(31f, -15.5f);
		leaf.lineTo(26.5f, -7.5f);
		leaf.lineTo(31.5f, -3f);
		leaf.lineTo(21.5f, 1.5f);
		leaf.lineTo(23.5f, 6.5f);
		leaf.lineTo(13.5f, 7f);
		leaf.lineTo(25f, 13.5f);
		leaf.lineTo(15.5f, 14f);
		leaf.lineTo(17f, 19f);
		leaf.lineTo(3f, 16.5f);
		leaf.lineTo(1.4f, 16.6f);
		leaf.quadTo(1.8f, 25f, 5f, 32.4f);
		leaf.lineTo(2.4f, 33.4f);
		leaf.quadTo(-1f, 25.6f, -1.4f, 16.6f);
		leaf.lineTo(-3f, 16.5f);
		leaf.lineTo(-17f, 19f);
		leaf.lineTo(-15.5f, 14f);
		leaf.lineTo(-25f, 13.5f);
		leaf.lineTo(-13.5f, 7f);
		leaf.lineTo(-23.5f, 6.5f);
		leaf.lineTo(-21.5f, 1.5f);
		leaf.lineTo(-31.5f, -3f);
		leaf.lineTo(-26.5f, -7.5f);
		leaf.lineTo(-31f, -15.5f);
		leaf.lineTo(-20.5f, -12.5f);
		leaf.lineTo(-16.5f, -17.5f);
		leaf.lineTo(-15.5f, -9.5f);
		leaf.lineTo(-6.5f, -3.5f);
		leaf.lineTo(-13f, -17f);
		leaf.lineTo(-7.5f, -16f);
		leaf.lineTo(-8.5f, -26.5f);
		leaf.lineTo(-3.5f, -24f);
		leaf.closePath();
		return leaf;
	}

	// osrslootsim.com's own snowflake (img/season/christmas/flyer.svg), 64 by 74, centred on its middle
	private static Path2D flake()
	{
		Path2D flake = new Path2D.Float();
		flake.moveTo(0f, 36f);
		flake.lineTo(-3.1f, 31.7f);
		flake.lineTo(-3.1f, -31.7f);
		flake.lineTo(0f, -36f);
		flake.lineTo(3.1f, -31.7f);
		flake.lineTo(3.1f, 31.7f);
		flake.closePath();
		flake.moveTo(-31.2f, 18f);
		flake.lineTo(-29f, 13.1f);
		flake.lineTo(25.9f, -18.5f);
		flake.lineTo(31.2f, -18f);
		flake.lineTo(29f, -13.1f);
		flake.lineTo(-25.9f, 18.5f);
		flake.closePath();
		flake.moveTo(-31.2f, -18f);
		flake.lineTo(-25.9f, -18.5f);
		flake.lineTo(29f, 13.1f);
		flake.lineTo(31.2f, 18f);
		flake.lineTo(25.9f, 18.5f);
		flake.lineTo(-29f, -13.1f);
		flake.closePath();
		flake.moveTo(0f, 13.6f);
		flake.lineTo(10f, 19.3f);
		flake.lineTo(11.1f, 23f);
		flake.lineTo(7.4f, 23.8f);
		flake.lineTo(-2.6f, 18.1f);
		flake.closePath();
		flake.moveTo(2.6f, 18.1f);
		flake.lineTo(-7.4f, 23.8f);
		flake.lineTo(-11.1f, 23f);
		flake.lineTo(-10f, 19.3f);
		flake.lineTo(0f, 13.6f);
		flake.closePath();
		flake.moveTo(0f, 23.6f);
		flake.lineTo(5.6f, 26.9f);
		flake.lineTo(6.8f, 30.5f);
		flake.lineTo(3f, 31.4f);
		flake.lineTo(-2.6f, 28.1f);
		flake.closePath();
		flake.moveTo(2.6f, 28.1f);
		flake.lineTo(-3f, 31.4f);
		flake.lineTo(-6.8f, 30.5f);
		flake.lineTo(-5.6f, 26.9f);
		flake.lineTo(0f, 23.6f);
		flake.closePath();
		flake.moveTo(-11.7f, 6.8f);
		flake.lineTo(-11.7f, 18.3f);
		flake.lineTo(-14.3f, 21.1f);
		flake.lineTo(-16.9f, 18.3f);
		flake.lineTo(-16.9f, 6.8f);
		flake.closePath();
		flake.moveTo(-14.3f, 11.3f);
		flake.lineTo(-24.3f, 5.5f);
		flake.lineTo(-25.4f, 1.9f);
		flake.lineTo(-21.7f, 1f);
		flake.lineTo(-11.7f, 6.8f);
		flake.closePath();
		flake.moveTo(-20.5f, 11.8f);
		flake.lineTo(-20.5f, 18.3f);
		flake.lineTo(-23.1f, 21.1f);
		flake.lineTo(-25.7f, 18.3f);
		flake.lineTo(-25.7f, 11.8f);
		flake.closePath();
		flake.moveTo(-23.1f, 16.3f);
		flake.lineTo(-28.7f, 13.1f);
		flake.lineTo(-29.8f, 9.4f);
		flake.lineTo(-26.1f, 8.6f);
		flake.lineTo(-20.5f, 11.8f);
		flake.closePath();
		flake.moveTo(-11.7f, -6.8f);
		flake.lineTo(-21.7f, -1f);
		flake.lineTo(-25.4f, -1.9f);
		flake.lineTo(-24.3f, -5.5f);
		flake.lineTo(-14.3f, -11.3f);
		flake.closePath();
		flake.moveTo(-16.9f, -6.8f);
		flake.lineTo(-16.9f, -18.3f);
		flake.lineTo(-14.3f, -21.1f);
		flake.lineTo(-11.7f, -18.3f);
		flake.lineTo(-11.7f, -6.8f);
		flake.closePath();
		flake.moveTo(-20.5f, -11.8f);
		flake.lineTo(-26.1f, -8.6f);
		flake.lineTo(-29.8f, -9.4f);
		flake.lineTo(-28.7f, -13.1f);
		flake.lineTo(-23.1f, -16.3f);
		flake.closePath();
		flake.moveTo(-25.7f, -11.8f);
		flake.lineTo(-25.7f, -18.3f);
		flake.lineTo(-23.1f, -21.1f);
		flake.lineTo(-20.5f, -18.3f);
		flake.lineTo(-20.5f, -11.8f);
		flake.closePath();
		flake.moveTo(0f, -13.6f);
		flake.lineTo(-10f, -19.3f);
		flake.lineTo(-11.1f, -23f);
		flake.lineTo(-7.4f, -23.8f);
		flake.lineTo(2.6f, -18.1f);
		flake.closePath();
		flake.moveTo(-2.6f, -18.1f);
		flake.lineTo(7.4f, -23.8f);
		flake.lineTo(11.1f, -23f);
		flake.lineTo(10f, -19.3f);
		flake.lineTo(0f, -13.6f);
		flake.closePath();
		flake.moveTo(0f, -23.6f);
		flake.lineTo(-5.6f, -26.9f);
		flake.lineTo(-6.8f, -30.5f);
		flake.lineTo(-3f, -31.4f);
		flake.lineTo(2.6f, -28.1f);
		flake.closePath();
		flake.moveTo(-2.6f, -28.1f);
		flake.lineTo(3f, -31.4f);
		flake.lineTo(6.8f, -30.5f);
		flake.lineTo(5.6f, -26.9f);
		flake.lineTo(0f, -23.6f);
		flake.closePath();
		flake.moveTo(11.7f, -6.8f);
		flake.lineTo(11.7f, -18.3f);
		flake.lineTo(14.3f, -21.1f);
		flake.lineTo(16.9f, -18.3f);
		flake.lineTo(16.9f, -6.8f);
		flake.closePath();
		flake.moveTo(14.3f, -11.3f);
		flake.lineTo(24.3f, -5.5f);
		flake.lineTo(25.4f, -1.9f);
		flake.lineTo(21.7f, -1f);
		flake.lineTo(11.7f, -6.8f);
		flake.closePath();
		flake.moveTo(20.5f, -11.8f);
		flake.lineTo(20.5f, -18.3f);
		flake.lineTo(23.1f, -21.1f);
		flake.lineTo(25.7f, -18.3f);
		flake.lineTo(25.7f, -11.8f);
		flake.closePath();
		flake.moveTo(23.1f, -16.3f);
		flake.lineTo(28.7f, -13.1f);
		flake.lineTo(29.8f, -9.4f);
		flake.lineTo(26.1f, -8.6f);
		flake.lineTo(20.5f, -11.8f);
		flake.closePath();
		flake.moveTo(11.7f, 6.8f);
		flake.lineTo(21.7f, 1f);
		flake.lineTo(25.4f, 1.9f);
		flake.lineTo(24.3f, 5.5f);
		flake.lineTo(14.3f, 11.3f);
		flake.closePath();
		flake.moveTo(16.9f, 6.8f);
		flake.lineTo(16.9f, 18.3f);
		flake.lineTo(14.3f, 21.1f);
		flake.lineTo(11.7f, 18.3f);
		flake.lineTo(11.7f, 6.8f);
		flake.closePath();
		flake.moveTo(20.5f, 11.8f);
		flake.lineTo(26.1f, 8.6f);
		flake.lineTo(29.8f, 9.4f);
		flake.lineTo(28.7f, 13.1f);
		flake.lineTo(23.1f, 16.3f);
		flake.closePath();
		flake.moveTo(25.7f, 11.8f);
		flake.lineTo(25.7f, 18.3f);
		flake.lineTo(23.1f, 21.1f);
		flake.lineTo(20.5f, 18.3f);
		flake.lineTo(20.5f, 11.8f);
		flake.closePath();
		return flake;
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

	// A colour at a strength from 0 (clear) to 255 (solid)
	private static Color tint(int[] rgb, float alpha)
	{
		return new Color(rgb[0], rgb[1], rgb[2], Math.max(0, Math.min(255, Math.round(alpha))));
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
