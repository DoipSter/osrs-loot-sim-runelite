package com.osrslootsim;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Draws the burst an insane drop gets, after osrslootsim.com's own celebration: a flash of light across the floor from
 * under the character, a ring racing outward, and a loot beam standing on a turning rune disc. A holiday dresses it:
 * its own colour, a picture on the floor, something of its own climbing the beam, and its own mark spreading over the
 * floor.
 *
 * Everything that lies on the floor is placed through a Floor, which says where a spot on the floor lands on screen,
 * so it follows the floor from any camera angle. The burst is drawn in two layers, each on a sheet of its own before
 * it goes over the game: what lies on the floor, and the beam. Whoever calls is handed a Cut to wipe off them what
 * stands in front: everything standing on the floor comes off the floor layer, and the character with whatever is
 * nearer to the camera comes off the beam.
 */
final class Burst
{
	/**
	 * Where a spot on the floor lands on screen. east, north: tiles from where the character stands. Fills screen
	 * with x and y and answers true, or answers false for a spot behind the camera.
	 */
	interface Floor
	{
		boolean at(double east, double north, double[] screen);
	}

	/**
	 * The colours of the beam and of the light on the floor: the site's gold, the purple of its Looting icon, an autumn
	 * orange and an ice white. From
	 * the beam's soft edge in to its core, then the floor's pool, the rune disc's lines, and the ring with its glow.
	 */
	enum Glow
	{
		GOLD(rgb(255, 170, 30), rgb(255, 200, 61), rgb(255, 236, 150), rgb(255, 251, 230), rgb(255, 186, 60), rgb(255, 214, 110), rgb(254, 222, 122), rgb(255, 138, 31)),
		PURPLE(rgb(120, 44, 226), rgb(155, 61, 255), rgb(214, 180, 255), rgb(246, 238, 255), rgb(155, 61, 255), rgb(201, 168, 255), rgb(233, 210, 255), rgb(120, 44, 226)),
		AUTUMN(rgb(206, 84, 16), rgb(236, 128, 36), rgb(255, 208, 140), rgb(255, 244, 224), rgb(214, 110, 30), rgb(244, 170, 90), rgb(255, 200, 120), rgb(190, 70, 14)),
		ICE(rgb(120, 176, 236), rgb(176, 214, 250), rgb(226, 242, 255), rgb(255, 255, 255), rgb(160, 206, 246), rgb(206, 232, 255), rgb(238, 248, 255), rgb(120, 176, 236));

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

	// How far the flash and the racing ring reach, in tiles; how long they take; and how far anything reaches after
	private static final float REACH = 3.2f;
	private static final int SPREADING_MS = 1050;
	private static final float SETTLED = 2.9f;
	// The rune disc, and the holiday's picture in its place: tiles from the middle to the edge
	private static final double DISC = 0.95;
	// A circle of light on the floor is drawn as this many straight sides
	private static final int SIDES = 48;
	private static final double[] COS = new double[SIDES];
	private static final double[] SIN = new double[SIDES];
	// The lights climbing the beam follow a pattern that repeats every this many pixels: across (0 to 1), up (pixels)
	private static final int MOTE_TILE = 260;
	private static final float[][] MOTES = {{.18f, 12}, {.40f, 44}, {.66f, 70}, {.84f, 98}, {.70f, 128}, {.44f, 152}, {.22f, 178}, {.12f, 206}, {.34f, 232}, {.58f, 250}};
	private static final Path2D BAT = bat();
	private static final Path2D LEAF = leaf();
	private static final Path2D FLAKE = flake();
	// The dark or pale edge drawn round each of them, worked out once rather than every frame
	private static final Shape BAT_EDGE = new BasicStroke(4f).createStrokedShape(BAT);
	private static final Shape LEAF_EDGE = new BasicStroke(3f).createStrokedShape(LEAF);
	private static final Shape FLAKE_EDGE = new BasicStroke(3f).createStrokedShape(FLAKE);
	private static final float[][] LITTER = scatter(18);

	// The two layers and the tool that cuts them. Only the game's own thread draws.
	private static final Layer FLOOR = new Layer();
	private static final Layer BEAM = new Layer();
	private static final Cut CUT = new Cut();
	// The beam's colours from edge to edge, for the colour and the width it was last drawn in
	private static int[] beamAcross = new int[0];
	private static Glow beamGlow;

	static
	{
		for (int k = 0; k < SIDES; k++)
		{
			COS[k] = Math.cos(k * 2 * Math.PI / SIDES);
			SIN[k] = Math.sin(k * 2 * Math.PI / SIDES);
		}
	}

	private Burst()
	{
	}

	/**
	 * Lays the burst over the game. view: the part of the screen the game world is drawn in. age: milliseconds since
	 * the burst began; it drives every movement. infront: called once both layers are drawn, to wipe off them what
	 * stands in front (see Cut).
	 */
	static void paint(Graphics2D target, Rectangle view, Floor floor, long age, float opacity, Season season, Glow glow, Consumer<Cut> infront)
	{
		double[] feet = new double[2];
		AffineTransform ground = near(floor, 0, 0);
		if (ground == null || !floor.at(0, 0, feet))
		{
			return;
		}
		int footX = (int) Math.round(feet[0]);
		int footY = (int) Math.round(feet[1]);

		// The floor layer: as much of the screen as the burst reaches over the floor right now
		float reach = age < SPREADING_MS ? REACH : SETTLED;
		double left = feet[0];
		double right = feet[0];
		double top = feet[1];
		double bottom = feet[1];
		double[] edge = new double[2];
		boolean whole = true;
		for (int k = 0; k < SIDES && whole; k++)
		{
			whole = floor.at(COS[k] * reach, SIN[k] * reach, edge);
			left = Math.min(left, edge[0]);
			right = Math.max(right, edge[0]);
			top = Math.min(top, edge[1]);
			bottom = Math.max(bottom, edge[1]);
		}
		Rectangle area = whole ? new Rectangle((int) left - 12, (int) top - 12, (int) (right - left) + 24, (int) (bottom - top) + 24).intersection(view) : view;
		Graphics2D g = FLOOR.begin(area);
		if (g != null)
		{
			floorLayer(g, floor, ground, age, season, glow);
			g.dispose();
		}

		// The beam layer: a column from the character's feet to the top of the view
		double tileWide = Math.hypot(ground.getScaleX(), ground.getShearX());
		int w = (int) Math.max(22, Math.min(90, Math.round(tileWide * 0.62)));   // the beam is a little over half a tile wide
		int half = season == Season.NONE ? Math.round(w * 1.3f) + 6 : Math.round(w * 2.2f) + 24;
		g = BEAM.begin(new Rectangle(footX - half, view.y, 2 * half, footY - view.y + 2).intersection(view));
		if (g != null)
		{
			beamLayer(g, footX, footY, view.y, age, w, season, glow);
			g.dispose();
		}

		infront.accept(CUT);
		FLOOR.lay(target, opacity);
		BEAM.lay(target, opacity);
	}

	/**
	 * Lets go of the sheets the layers are drawn on. For when the burst is over: they are as large as the burst was on
	 * screen.
	 */
	static void release()
	{
		FLOOR.release();
		BEAM.release();
	}

	/**
	 * The sheet one layer is drawn on. It covers one part of the screen at a time.
	 */
	private static final class Layer
	{
		// The part of the screen the sheet covers now; empty while it holds nothing
		private final Rectangle area = new Rectangle();
		private BufferedImage image;
		private int[] pixels;
		// How many pixels a row of the image holds
		private int wide;
		// For bands: how far left and right the ring being filled and the ring inside it reach on each row of the sheet,
		// and which rows each crosses (the first, and one past the last)
		private float[] outerLeft = new float[0];
		private float[] outerRight = new float[0];
		private float[] innerLeft = new float[0];
		private float[] innerRight = new float[0];
		private int outerTop;
		private int outerEnd;
		private int innerTop;
		private int innerEnd;

		// A clean sheet over a part of the screen, set up so screen coordinates can be used as they are. null for no part
		// at all.
		Graphics2D begin(Rectangle part)
		{
			if (part.isEmpty())
			{
				area.setSize(0, 0);
				return null;
			}
			area.setBounds(part);
			if (image == null || image.getWidth() < part.width || image.getHeight() < part.height)
			{
				image = new BufferedImage(Math.max(part.width, image == null ? 0 : image.getWidth()), Math.max(part.height, image == null ? 0 : image.getHeight()),
					BufferedImage.TYPE_INT_ARGB_PRE);
				pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
				wide = image.getWidth();
			}
			else
			{
				for (int row = 0; row < part.height; row++)
				{
					Arrays.fill(pixels, row * wide, row * wide + part.width, 0);
				}
			}
			Graphics2D g = image.createGraphics();
			g.setClip(0, 0, part.width, part.height);
			g.translate(-part.x, -part.y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			return g;
		}

		boolean touches(int left, int top, int right, int bottom)
		{
			return !area.isEmpty() && right >= area.x && left < area.x + area.width && bottom >= area.y && top < area.y + area.height;
		}

		/**
		 * Wipes a triangle off the sheet, row by row straight in its pixels: a model is thousands of small triangles,
		 * and this is many times quicker than handing each to Java's own shape filling. Corners in screen coordinates.
		 */
		void wipe(int ax, int ay, int bx, int by, int cx, int cy)
		{
			int last = area.x + area.width - 1;
			if (Math.max(ax, Math.max(bx, cx)) < area.x || Math.min(ax, Math.min(bx, cx)) > last)
			{
				return;
			}
			// the corners in order down the screen
			int kept;
			if (ay > by)
			{
				kept = ax;
				ax = bx;
				bx = kept;
				kept = ay;
				ay = by;
				by = kept;
			}
			if (by > cy)
			{
				kept = bx;
				bx = cx;
				cx = kept;
				kept = by;
				by = cy;
				cy = kept;
			}
			if (ay > by)
			{
				kept = ax;
				ax = bx;
				bx = kept;
				kept = ay;
				ay = by;
				by = kept;
			}
			for (int y = Math.max(ay, area.y); y <= Math.min(cy, area.y + area.height - 1); y++)
			{
				float one;
				float other;
				if (ay == cy)
				{
					// all three corners on one row
					one = Math.min(ax, Math.min(bx, cx));
					other = Math.max(ax, Math.max(bx, cx));
				}
				else
				{
					// the long side from the top corner to the bottom one, and whichever short side is level with this row
					one = ax + (float) (cx - ax) * (y - ay) / (cy - ay);
					other = y < by ? ax + (float) (bx - ax) * (y - ay) / (by - ay) : by == cy ? bx : bx + (float) (cx - bx) * (y - by) / (cy - by);
				}
				int from = Math.max(area.x, Math.round(Math.min(one, other)));
				int to = Math.min(last, Math.round(Math.max(one, other)));
				if (from <= to)
				{
					int row = (y - area.y) * wide - area.x;
					Arrays.fill(pixels, row + from, row + to + 1, 0);
				}
			}
		}

		/**
		 * Starts a patch of light made of bands, filled from the middle outward with band.
		 */
		void patch()
		{
			if (outerLeft.length < area.height)
			{
				outerLeft = new float[area.height];
				outerRight = new float[area.height];
				innerLeft = new float[area.height];
				innerRight = new float[area.height];
			}
			innerTop = 0;
			innerEnd = 0;
		}

		/**
		 * Fills the band between a ring and the ring given before it (or all of the first ring) with one colour, over
		 * what the sheet already holds. ring: the ring's corners on screen, x then y for each. Filled row by row
		 * straight in the sheet's pixels: the bands of one patch are thousands of short runs, and handing each to
		 * Java's own shape filling took five times as long.
		 */
		void band(float[] ring, Color colour)
		{
			// Which rows the ring crosses, and how far left and right it reaches on each. A row is crossed where the
			// middle of its pixels is inside the ring.
			float top = Float.MAX_VALUE;
			float bottom = -Float.MAX_VALUE;
			for (int i = 1; i < ring.length; i += 2)
			{
				top = Math.min(top, ring[i]);
				bottom = Math.max(bottom, ring[i]);
			}
			outerTop = Math.max(0, (int) Math.ceil(top - 0.5f - area.y));
			outerEnd = Math.min(area.height, (int) Math.ceil(bottom - 0.5f - area.y));
			for (int row = outerTop; row < outerEnd; row++)
			{
				outerLeft[row] = Float.MAX_VALUE;
				outerRight[row] = -Float.MAX_VALUE;
			}
			for (int i = 0; i < ring.length; i += 2)
			{
				int next = (i + 2) % ring.length;
				boolean down = ring[i + 1] < ring[next + 1];
				float x = ring[down ? i : next];
				float y = ring[(down ? i : next) + 1];
				float endY = ring[(down ? next : i) + 1];
				if (y == endY)
				{
					continue;
				}
				float slope = (ring[down ? next : i] - x) / (endY - y);
				int last = Math.min(outerEnd, (int) Math.ceil(endY - 0.5f - area.y));
				for (int row = Math.max(outerTop, (int) Math.ceil(y - 0.5f - area.y)); row < last; row++)
				{
					float crossed = x + (row + 0.5f + area.y - y) * slope;
					outerLeft[row] = Math.min(outerLeft[row], crossed);
					outerRight[row] = Math.max(outerRight[row], crossed);
				}
			}

			int solid = colour.getAlpha();
			if (solid > 0)
			{
				// the sheet keeps each colour already multiplied by how solid it is
				int paint = (solid << 24) | (colour.getRed() * solid / 255 << 16) | (colour.getGreen() * solid / 255 << 8) | (colour.getBlue() * solid / 255);
				int through = 256 - solid - (solid >> 7);   // how much of what is under it shows through, out of 256
				for (int row = outerTop; row < outerEnd; row++)
				{
					int from = column(outerLeft[row]);
					int to = column(outerRight[row]);
					if (row >= innerTop && row < innerEnd && innerLeft[row] <= innerRight[row])
					{
						run(row, from, Math.min(to, column(innerLeft[row])), paint, through);
						run(row, Math.max(from, column(innerRight[row])), to, paint, through);
					}
					else
					{
						run(row, from, to, paint, through);
					}
				}
			}

			// This ring is the inside of the next band
			float[] kept = innerLeft;
			innerLeft = outerLeft;
			outerLeft = kept;
			kept = innerRight;
			innerRight = outerRight;
			outerRight = kept;
			innerTop = outerTop;
			innerEnd = outerEnd;
		}

		// The first pixel of a row whose middle is right of x, counted from the sheet's left edge and kept on the sheet
		private int column(float x)
		{
			return Math.max(0, Math.min(area.width, (int) Math.ceil(x - 0.5f - area.x)));
		}

		// Lays one colour over a run of pixels in a row
		private void run(int row, int from, int to, int paint, int through)
		{
			int start = row * wide;
			for (int i = start + from; i < start + to; i++)
			{
				int under = pixels[i];
				pixels[i] = under == 0 ? paint
					: paint + (((((under >>> 8) & 0x00FF00FF) * through) & 0xFF00FF00) | ((((under & 0x00FF00FF) * through) >>> 8) & 0x00FF00FF));
			}
		}

		void lay(Graphics2D target, float opacity)
		{
			if (area.isEmpty())
			{
				return;
			}
			Graphics2D g = DropCard.begin(target, opacity);
			g.drawImage(image, area.x, area.y, area.x + area.width, area.y + area.height, 0, 0, area.width, area.height, null);
			g.dispose();
		}

		void release()
		{
			area.setSize(0, 0);
			image = null;
			pixels = null;
		}
	}

	/**
	 * Wipes off the burst what stands in front of it. It is given things as they land on screen, face by face of their
	 * models. Everything it is given comes off the floor layer, since whatever stands on the floor hides the floor
	 * behind it; what is marked nearer (the character, and whatever stands between it and the camera) comes off the
	 * beam as well.
	 */
	static final class Cut
	{
		private Cut()
		{
		}

		/**
		 * Whether something on screen around x, y, reaching no further than reach pixels from there, could hide any of
		 * the burst. For skipping what is nowhere near it before working out its shape.
		 */
		boolean reaches(double x, double y, double reach, boolean nearer)
		{
			int left = (int) Math.floor(x - reach);
			int top = (int) Math.floor(y - reach);
			int right = (int) Math.ceil(x + reach);
			int bottom = (int) Math.ceil(y + reach);
			return FLOOR.touches(left, top, right, bottom) || nearer && BEAM.touches(left, top, right, bottom);
		}

		/**
		 * Wipes a model off. xs, ys: where its corners land on screen, with Integer.MIN_VALUE in xs for a corner behind
		 * the camera. first, second, third: the three corners of each face. clear: how see-through each face is, or
		 * null for a model that is solid all over.
		 */
		void faces(int[] xs, int[] ys, int corners, int[] first, int[] second, int[] third, byte[] clear, int faces, boolean nearer)
		{
			int left = Integer.MAX_VALUE;
			int top = Integer.MAX_VALUE;
			int right = Integer.MIN_VALUE;
			int bottom = Integer.MIN_VALUE;
			for (int i = 0; i < corners; i++)
			{
				int x = xs[i];
				if (x == Integer.MIN_VALUE)
				{
					continue;
				}
				int y = ys[i];
				left = Math.min(left, x);
				right = Math.max(right, x);
				top = Math.min(top, y);
				bottom = Math.max(bottom, y);
			}
			boolean floor = left <= right && FLOOR.touches(left, top, right, bottom);
			boolean beam = nearer && left <= right && BEAM.touches(left, top, right, bottom);
			if (!floor && !beam)
			{
				return;
			}
			for (int face = 0; face < faces; face++)
			{
				int a = first[face];
				int b = second[face];
				int c = third[face];
				// Skipped: a face that is see-through, or has a corner behind the camera
				if (a < 0 || b < 0 || c < 0 || a >= corners || b >= corners || c >= corners || clear != null && (clear[face] & 0xFF) >= 254
					|| xs[a] == Integer.MIN_VALUE || xs[b] == Integer.MIN_VALUE || xs[c] == Integer.MIN_VALUE)
				{
					continue;
				}
				if (floor)
				{
					FLOOR.wipe(xs[a], ys[a], xs[b], ys[b], xs[c], ys[c]);
				}
				if (beam)
				{
					BEAM.wipe(xs[a], ys[a], xs[b], ys[b], xs[c], ys[c]);
				}
			}
		}
	}

	/**
	 * Everything that lies on the floor, bottom to top: the flash, the pool of light, the holiday's spread, the rune
	 * disc or the holiday's picture, the ring racing outward, and the white-hot foot of the beam.
	 */
	private static void floorLayer(Graphics2D g, Floor floor, AffineTransform ground, long age, Season season, Glow glow)
	{
		float swell = (float) (0.5 + 0.5 * Math.sin(age / 1200.0 * Math.PI));   // 0 to 1 and back every 2.4 s

		// The flash: light bursting out across the floor from under the character, gone in just over half a second
		float burst = age / 600f;
		if (burst < 1)
		{
			float reach = 0.4f + (REACH - 0.4f) * ease(burst);
			float bright = (float) Math.pow(1 - burst, 1.5);
			disc(floor, 0, 0, reach, 32, new float[]{0f, 0.28f, 0.76f, 1f},
				new Color[]{new Color(255, 255, 255, Math.round(242 * bright)), tint(glow.core, 235 * bright), tint(glow.pale, 0), tint(glow.pale, 0)});
		}

		// A pool of light, swelling and settling
		disc(floor, 0, 0, 1.9f + 0.2f * swell, 32, new float[]{0f, 1f}, new Color[]{tint(glow.pool, 110 + 20 * swell), tint(glow.pool, 0)});

		boolean dressed = season != Season.NONE;
		switch (season)
		{
			case HALLOWEEN:
				cobweb(g, floor, age);
				pumpkin(g, ground, swell);
				break;
			case THANKSGIVING:
				leafLitter(g, floor, age);
				turkey(g, ground);
				break;
			case CHRISTMAS:
				frost(g, floor, age);
				snowGlobe(g, ground, age);
				break;
			default:
				runeDisc(g, floor, age, swell, glow);
		}
		shockRing(g, floor, age, season, glow);

		// The white-hot foot of the beam. Smaller on a holiday's picture, to leave the picture in view.
		float foot = (dressed ? 0.2f : 0.42f) * ease(Math.min(1f, age / 320f));
		if (foot > 0.02f)
		{
			disc(floor, 0, 0, foot, 12, new float[]{0f, 0.35f, 0.6f, 1f}, new Color[]{Color.WHITE, Color.WHITE, tint(glow.pale, 235), tint(glow.body, 0)});
		}
	}

	/**
	 * A round patch of light on the floor layer, its colour changing from the middle outward. Drawn as bands of one
	 * colour each between rings that follow the floor exactly, so it lies as the floor does from any camera angle.
	 */
	private static void disc(Floor floor, double east, double north, double radius, int rings, float[] stops, Color[] colours)
	{
		double[] xy = new double[2];
		float[] ring = new float[2 * SIDES];
		FLOOR.patch();
		for (int band = 1; band <= rings; band++)
		{
			double r = radius * band / rings;
			for (int k = 0; k < SIDES; k++)
			{
				if (!floor.at(east + COS[k] * r, north + SIN[k] * r, xy))
				{
					return;   // from here out the floor runs behind the camera
				}
				ring[2 * k] = (float) xy[0];
				ring[2 * k + 1] = (float) xy[1];
			}
			FLOOR.band(ring, blend(stops, colours, (band - 0.5f) / rings));
		}
	}

	// The colour a run of colours has at a point from 0 to 1 along it
	private static Color blend(float[] stops, Color[] colours, float at)
	{
		int i = 1;
		while (i < stops.length - 1 && at > stops[i])
		{
			i++;
		}
		float t = Math.max(0f, Math.min(1f, (at - stops[i - 1]) / Math.max(0.0001f, stops[i] - stops[i - 1])));
		Color a = colours[i - 1];
		Color b = colours[i];
		return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
			Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t), Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
	}

	/**
	 * How the floor lies on screen right around a spot: a tile east and a tile north of it as steps on screen. Small
	 * things (a leaf, the picture under the character) are drawn through this; null where the floor is behind the
	 * camera.
	 */
	private static AffineTransform near(Floor floor, double east, double north)
	{
		double[] c = new double[2];
		double[] e = new double[2];
		double[] n = new double[2];
		if (!floor.at(east, north, c) || !floor.at(east + 0.25, north, e) || !floor.at(east, north + 0.25, n))
		{
			return null;
		}
		return new AffineTransform((e[0] - c[0]) * 4, (e[1] - c[1]) * 4, (n[0] - c[0]) * 4, (n[1] - c[1]) * 4, c[0], c[1]);
	}

	/**
	 * A shape drawn on the floor, in tiles from the character, as it looks on screen. Curves are cut into short
	 * straight pieces, and a long straight line is laid in steps of half a tile, so it follows a floor that is not
	 * level.
	 */
	private static Path2D on(Floor floor, Shape shape)
	{
		Path2D out = new Path2D.Float();
		double[] at = new double[6];
		double[] xy = new double[2];
		double fromX = 0;
		double fromY = 0;
		double startX = 0;
		double startY = 0;
		boolean open = false;
		for (PathIterator it = shape.getPathIterator(null, 0.004); !it.isDone(); it.next())
		{
			int kind = it.currentSegment(at);
			if (kind == PathIterator.SEG_CLOSE)
			{
				if (open)
				{
					out.closePath();
				}
				fromX = startX;
				fromY = startY;
				continue;
			}
			if (kind == PathIterator.SEG_MOVETO)
			{
				startX = at[0];
				startY = at[1];
				open = floor.at(at[0], at[1], xy);
				if (open)
				{
					out.moveTo(xy[0], xy[1]);
				}
			}
			else
			{
				int steps = Math.max(1, (int) Math.ceil(Math.hypot(at[0] - fromX, at[1] - fromY) / 0.5));
				for (int step = 1; step <= steps; step++)
				{
					if (!floor.at(fromX + (at[0] - fromX) * step / steps, fromY + (at[1] - fromY) * step / steps, xy))
					{
						open = false;
					}
					else if (open)
					{
						out.lineTo(xy[0], xy[1]);
					}
					else
					{
						out.moveTo(xy[0], xy[1]);
						open = true;
					}
				}
			}
			fromX = at[0];
			fromY = at[1];
		}
		return out;
	}

	private static Shape circle(Floor floor, double radius)
	{
		return on(floor, new Ellipse2D.Double(-radius, -radius, 2 * radius, 2 * radius));
	}

	// The rune disc turning on the floor, and the ring at its heart
	private static void runeDisc(Graphics2D g, Floor floor, long age, float swell, Glow glow)
	{
		Path2D marks = new Path2D.Double();
		double turn = age / 18000.0 * 2 * Math.PI;
		for (int k = 0; k < 24; k++)
		{
			double a = turn + k * Math.PI / 12;
			double from = DISC * (k % 2 == 0 ? 0.66 : 0.74);
			double to = DISC * 0.86;
			marks.moveTo(Math.cos(a) * from, Math.sin(a) * from);
			marks.lineTo(Math.cos(a) * to, Math.sin(a) * to);
		}
		g.setStroke(new BasicStroke(1.6f));
		g.setColor(tint(glow.line, 235));
		g.draw(circle(floor, DISC * 0.94));
		g.draw(circle(floor, DISC * 0.57));
		g.draw(on(floor, marks));
		Shape ring = circle(floor, DISC * (0.44 + 0.02 * swell));
		g.setStroke(new BasicStroke(5f));
		g.setColor(tint(glow.ringGlow, 70));
		g.draw(ring);
		g.setStroke(new BasicStroke(2f));
		g.setColor(tint(glow.ring, 230));
		g.draw(ring);
	}

	/**
	 * Halloween creeping out over the floor from under the character: a purple mist with paler puffs drifting round in
	 * it, and a cobweb spinning itself outward, thread by thread and ring by ring.
	 */
	private static void cobweb(Graphics2D g, Floor floor, long age)
	{
		float grown = ease(Math.min(1f, age / 900f));
		float reach = 0.3f + 2.4f * grown;
		disc(floor, 0, 0, reach, 64, new float[]{0f, 0.7f, 1f}, new Color[]{new Color(126, 74, 196, 135), new Color(74, 42, 126, 105), new Color(74, 42, 126, 0)});
		for (int k = 0; k < LITTER.length; k += 2)
		{
			float[] spot = LITTER[k];
			if (spot[1] > reach)
			{
				continue;
			}
			double round = spot[0] + age / 9000.0;   // the puffs drift slowly round the character
			float puff = 0.42f + 0.1f * (float) Math.sin(age / 700.0 + k);
			disc(floor, Math.cos(round) * spot[1], Math.sin(round) * spot[1], puff, 8, new float[]{0f, 1f}, new Color[]{new Color(196, 176, 236, 78), new Color(196, 176, 236, 0)});
		}

		// The web: ten threads out from the middle, joined by rings that sag toward it between each pair of threads
		int threads = 10;
		Path2D web = new Path2D.Double();
		for (int k = 0; k < threads; k++)
		{
			double a = k * 2 * Math.PI / threads + 0.1;
			web.moveTo(Math.cos(a) * 0.3, Math.sin(a) * 0.3);
			web.lineTo(Math.cos(a) * reach * 0.97, Math.sin(a) * reach * 0.97);
		}
		for (double ring : new double[]{0.3, 0.5, 0.72, 0.92})
		{
			double out = reach * ring;
			for (int k = 0; k < threads; k++)
			{
				double a = k * 2 * Math.PI / threads + 0.1;
				double b = a + 2 * Math.PI / threads;
				double mid = (a + b) / 2;
				web.moveTo(Math.cos(a) * out, Math.sin(a) * out);
				web.quadTo(Math.cos(mid) * out * 0.84, Math.sin(mid) * out * 0.84, Math.cos(b) * out, Math.sin(b) * out);
			}
		}
		g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
		g.setColor(new Color(238, 232, 248, 190));
		g.draw(on(floor, web));
	}

	/**
	 * Frost creeping out over the floor from under the character: a pale sheet of ice that spreads in under a second,
	 * crystals growing through it like the arms of a snowflake, and glints that come and go.
	 */
	private static void frost(Graphics2D g, Floor floor, long age)
	{
		float grown = ease(Math.min(1f, age / 900f));
		float reach = 0.3f + 2.4f * grown;
		disc(floor, 0, 0, reach, 64, new float[]{0f, 0.72f, 1f}, new Color[]{new Color(238, 248, 255, 165), new Color(190, 226, 252, 125), new Color(190, 226, 252, 0)});

		// Twelve crystals, long and short by turns, each with twigs branching off both sides
		Path2D crystals = new Path2D.Double();
		for (int k = 0; k < 12; k++)
		{
			double a = k * Math.PI / 6 + (k % 2) * 0.13;
			double length = reach * (k % 2 == 0 ? 0.94 : 0.68);
			crystals.moveTo(Math.cos(a) * 0.3, Math.sin(a) * 0.3);
			crystals.lineTo(Math.cos(a) * length, Math.sin(a) * length);
			for (double along : new double[]{0.42, 0.63, 0.82})
			{
				double x = Math.cos(a) * length * along;
				double y = Math.sin(a) * length * along;
				double twig = length * 0.17 * (1.2 - along);
				for (int side = -1; side <= 1; side += 2)
				{
					double b = a + side * Math.toRadians(55);
					crystals.moveTo(x, y);
					crystals.lineTo(x + Math.cos(b) * twig, y + Math.sin(b) * twig);
				}
			}
		}
		g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
		g.setColor(new Color(242, 250, 255, 215));
		g.draw(on(floor, crystals));

		// Glints on the ice, each twinkling in its own time
		double[] at = new double[2];
		for (int k = 0; k < LITTER.length; k++)
		{
			float[] spot = LITTER[k];
			float bright = (float) Math.sin(age / 280.0 + k * 2.1);
			if (spot[1] > reach || bright <= 0 || !floor.at(Math.cos(spot[0]) * spot[1], Math.sin(spot[0]) * spot[1], at))
			{
				continue;
			}
			double arm = 1.5 + 2.5 * bright;
			g.setColor(new Color(255, 255, 255, Math.round(235 * bright)));
			g.draw(new Line2D.Double(at[0] - arm, at[1], at[0] + arm, at[1]));
			g.draw(new Line2D.Double(at[0], at[1] - arm, at[0], at[1] + arm));
		}
	}

	/**
	 * Autumn spreading out over the floor from under the character: the ground dries to a warm brown, and fallen
	 * leaves are left lying where the spread has passed.
	 */
	private static void leafLitter(Graphics2D g, Floor floor, long age)
	{
		float spread = ease(Math.min(1f, age / 900f));
		float reach = 0.3f + 2.4f * spread;
		disc(floor, 0, 0, reach, 64, new float[]{0f, 0.7f, 1f}, new Color[]{new Color(168, 104, 44, 140), new Color(128, 78, 30, 110), new Color(128, 78, 30, 0)});
		int[][] autumn = {{245, 165, 58}, {232, 85, 63}, {240, 200, 70}, {166, 98, 40}};
		for (int k = 0; k < LITTER.length; k++)
		{
			float[] spot = LITTER[k];
			AffineTransform here = spot[1] > reach ? null : near(floor, Math.cos(spot[0]) * spot[1], Math.sin(spot[0]) * spot[1]);
			if (here == null)
			{
				continue;
			}
			float landed = Math.min(1f, (reach - spot[1]) / 0.35f);   // comes in as the spread passes over its place
			int[] rgb = autumn[k % autumn.length];
			Graphics2D leaf = (Graphics2D) g.create();
			leaf.transform(here);
			leaf.rotate(spot[2]);
			leaf.scale(spot[3] / 64.0, spot[3] / 64.0);
			leaf.setColor(new Color(60, 28, 8, Math.round(150 * landed)));
			leaf.fill(LEAF_EDGE);
			leaf.setColor(new Color(rgb[0], rgb[1], rgb[2], Math.round(240 * landed)));
			leaf.fill(LEAF);
			leaf.dispose();
		}
	}

	/**
	 * One ring racing outward along the floor as the beam lands, fading as it goes. A plain ring of light, or the
	 * holiday's own: a jagged pumpkin grin at Halloween, a gust of leaves at Thanksgiving, a ring of ice spikes at
	 * Christmas.
	 */
	private static void shockRing(Graphics2D g, Floor floor, long age, Season season, Glow glow)
	{
		float raced = (age - 120) / 900f;
		if (raced <= 0 || raced >= 1)
		{
			return;
		}
		double radius = DISC * (0.75 + 2.2 * ease(raced));
		float fade = 1 - raced;
		switch (season)
		{
			case HALLOWEEN:
			{
				// teeth all the way round, like the lantern's grin
				int teeth = 30;
				Path2D grin = new Path2D.Double();
				for (int k = 0; k < 2 * teeth; k++)
				{
					double a = k * Math.PI / teeth;
					double out = radius * (k % 2 == 0 ? 1.0 : 1.1);
					if (k == 0)
					{
						grin.moveTo(Math.cos(a) * out, Math.sin(a) * out);
					}
					else
					{
						grin.lineTo(Math.cos(a) * out, Math.sin(a) * out);
					}
				}
				grin.closePath();
				Shape ring = on(floor, grin);
				g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.setColor(new Color(155, 61, 255, Math.round(95 * fade)));
				g.draw(ring);
				g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.setColor(new Color(255, 156, 46, Math.round(240 * fade)));
				g.draw(ring);
				break;
			}
			case THANKSGIVING:
			{
				// a gust: leaves swept round and outward, tumbling
				g.setStroke(new BasicStroke(1.5f));
				g.setColor(tint(glow.pale, 110 * fade));
				g.draw(circle(floor, radius));
				int[][] autumn = {{245, 165, 58}, {232, 85, 63}, {240, 200, 70}, {166, 98, 40}};
				int count = 16;
				for (int k = 0; k < count; k++)
				{
					double a = k * 2 * Math.PI / count + raced * 1.1;
					AffineTransform here = near(floor, Math.cos(a) * radius, Math.sin(a) * radius);
					if (here == null)
					{
						continue;
					}
					int[] rgb = autumn[k % autumn.length];
					Graphics2D leaf = (Graphics2D) g.create();
					leaf.transform(here);
					leaf.rotate(a + raced * 7 + k);
					leaf.scale(0.3 / 64.0, 0.3 / 64.0);
					leaf.setColor(new Color(rgb[0], rgb[1], rgb[2], Math.round(245 * fade)));
					leaf.fill(LEAF);
					leaf.dispose();
				}
				break;
			}
			case CHRISTMAS:
			{
				// a ring of ice with spikes thrown outward, long and short by turns
				int spikes = 28;
				Path2D ice = new Path2D.Double();
				for (int k = 0; k < spikes; k++)
				{
					double a = k * 2 * Math.PI / spikes;
					double tip = radius * (k % 2 == 0 ? 1.14 : 1.07);
					double half = 0.045;
					ice.moveTo(Math.cos(a - half) * radius, Math.sin(a - half) * radius);
					ice.lineTo(Math.cos(a) * tip, Math.sin(a) * tip);
					ice.lineTo(Math.cos(a + half) * radius, Math.sin(a + half) * radius);
					ice.closePath();
				}
				Shape round = circle(floor, radius);
				g.setStroke(new BasicStroke(6f));
				g.setColor(new Color(120, 176, 236, Math.round(90 * fade)));
				g.draw(round);
				g.setColor(new Color(232, 245, 255, Math.round(225 * fade)));
				g.fill(on(floor, ice));
				g.setStroke(new BasicStroke(2f));
				g.setColor(new Color(255, 255, 255, Math.round(240 * fade)));
				g.draw(round);
				break;
			}
			default:
				g.setStroke(new BasicStroke(3f));
				g.setColor(tint(glow.core, 230 * fade));
				g.draw(circle(floor, radius));
		}
	}

	/**
	 * The beam, and what climbs it: small lights, or the holiday's own (bats, leaves, snowflakes), which are fewer than
	 * the lights, spread wider and sway as they rise.
	 */
	private static void beamLayer(Graphics2D g, int footX, int footY, int top, long age, int w, Season season, Glow glow)
	{
		float swell = (float) (0.5 + 0.5 * Math.sin(age / 1200.0 * Math.PI));
		// The beam shoots up from the floor in the first third of a second, then breathes
		float risen = ease(Math.min(1f, age / 320f));
		int length = Math.max(1, footY - top);
		int beamTop = footY - Math.round(length * risen);
		beam(footX, footY, beamTop, length, Math.round(w * 1.3f), 0.9f + 0.1f * swell, glow);

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

	/**
	 * The beam itself: soft at its edges, white at its core, brightest near the floor and fading as it climbs. Written
	 * straight into the pixels of its sheet, which is clean, so there is nothing under it to blend with. half: pixels
	 * from the middle of the beam to the end of its soft edge. breath: its strength right now, up to 1.
	 */
	private static void beam(int footX, int footY, int beamTop, int length, int half, float breath, Glow glow)
	{
		if (beamGlow != glow || beamAcross.length != 2 * half)
		{
			float[] stops = {0f, 0.30f, 0.36f, 0.43f, 0.475f, 0.5f, 0.525f, 0.57f, 0.64f, 0.70f, 1f};
			Color none = tint(glow.edge, 0);
			Color[] colours = {none, none, tint(glow.edge, 97), tint(glow.body, 209), tint(glow.pale, 245), tint(glow.core, 255),
				tint(glow.pale, 245), tint(glow.body, 209), tint(glow.edge, 97), none, none};
			beamAcross = new int[2 * half];
			for (int x = 0; x < beamAcross.length; x++)
			{
				Color c = blend(stops, colours, (x + 0.5f) / beamAcross.length);
				int solid = c.getAlpha();
				// the sheet keeps each colour already multiplied by how solid it is
				beamAcross[x] = (solid << 24) | (c.getRed() * solid / 255 << 16) | (c.getGreen() * solid / 255 << 8) | (c.getBlue() * solid / 255);
			}
			beamGlow = glow;
		}
		Rectangle area = BEAM.area;
		int from = Math.max(footX - half, area.x);
		int to = Math.min(footX + half, area.x + area.width);
		for (int y = Math.max(beamTop, area.y); y < Math.min(footY, area.y + area.height); y++)
		{
			float up = (float) (footY - y) / length;   // 0 at the floor, 1 at the top of the view
			int strength = Math.round((up < 0.05f ? up / 0.05f : up < 0.24f ? 1f : 1f - 0.45f * (up - 0.24f) / 0.76f) * breath * 256);
			int row = (y - area.y) * BEAM.wide - area.x;
			for (int x = from; x < to; x++)
			{
				// all four parts of the colour weakened at once, two at a time
				int colour = beamAcross[x - footX + half];
				BEAM.pixels[row + x] = ((((colour >>> 8) & 0x00FF00FF) * strength) & 0xFF00FF00) | ((((colour & 0x00FF00FF) * strength) >>> 8) & 0x00FF00FF);
			}
		}
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

	// One bat, so many pixels across with its wings spread. beat: 0 wings folded in, 1 spread. A pale purple edge
	// keeps it readable where it flies off the beam onto dark ground.
	private static void bat(Graphics2D g, double x, double y, double wide, double beat, float seen)
	{
		Graphics2D b = (Graphics2D) g.create();
		b.translate(x, y);
		double size = wide / 64.0;
		b.scale(size * (0.55 + 0.45 * beat), size);
		b.setColor(new Color(201, 168, 255, Math.round(140 * seen)));
		b.fill(BAT_EDGE);
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
		l.setColor(new Color(60, 28, 8, Math.round(170 * seen)));
		l.fill(LEAF_EDGE);
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
		f.setColor(new Color(70, 120, 190, Math.round(150 * seen)));
		f.fill(FLAKE_EDGE);
		f.setColor(new Color(240, 250, 255, Math.round(255 * seen)));
		f.fill(FLAKE);
		f.dispose();
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

	// Places scattered over the floor around the character, the same every time: the way round (radians), how far out
	// (tiles), a turn (radians) and a size (tiles). Fallen leaves lie on them, and glints of frost twinkle on them.
	private static float[][] scatter(int count)
	{
		float[][] spots = new float[count][];
		long seed = 20261011L;
		for (int i = 0; i < count; i++)
		{
			float[] spot = new float[4];
			for (int j = 0; j < 4; j++)
			{
				seed = (seed * 6364136223846793005L + 1442695040888963407L);
				spot[j] = (seed >>> 40) / (float) (1 << 24);
			}
			spots[i] = new float[]{(float) (2 * Math.PI * (i + spot[0] * 0.8f) / count), 0.85f + 1.75f * spot[1], (float) (2 * Math.PI * spot[2]), 0.3f + 0.16f * spot[3]};
		}
		return spots;
	}

	// Fast at first, then settling
	private static float ease(float t)
	{
		float left = 1 - Math.max(0f, Math.min(1f, t));
		return 1 - left * left * left;
	}

	// A colour at a strength from 0 (clear) to 255 (solid)
	private static Color tint(int[] rgb, float alpha)
	{
		return new Color(rgb[0], rgb[1], rgb[2], Math.max(0, Math.min(255, Math.round(alpha))));
	}
}
