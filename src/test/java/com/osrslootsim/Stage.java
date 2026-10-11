package com.osrslootsim;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * A stand-in for the game's camera and floor, for drawing and timing the burst without the game: a level floor seen
 * from a camera that looks down at the character from a distance, as the game's does.
 */
final class Stage implements Burst.Floor
{
	// The faces of a box: two triangles a side, corners numbered 0 to 3 round the bottom and 4 to 7 round the top
	static final int[] BOX_FIRST = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 0, 0};
	static final int[] BOX_SECOND = {1, 5, 2, 6, 3, 7, 0, 4, 5, 6, 1, 2};
	static final int[] BOX_THIRD = {5, 4, 6, 5, 7, 6, 4, 7, 6, 7, 2, 3};

	private final double footX;
	private final double footY;
	private final double zoom;
	private final double distance;
	private final double pitchSin;
	private final double pitchCos;
	private final double yawSin;
	private final double yawCos;

	/**
	 * footX, footY: where the character stands on screen. tileWide: how many pixels wide a tile is there. pitch: how
	 * steeply the camera looks down, in degrees (the game's goes from about 10 to 67). yaw: how far it is turned.
	 * distance: how many tiles it is from the character.
	 */
	Stage(double footX, double footY, double tileWide, double pitch, double yaw, double distance)
	{
		this.footX = footX;
		this.footY = footY;
		this.zoom = tileWide * distance;
		this.distance = distance;
		pitchSin = Math.sin(Math.toRadians(pitch));
		pitchCos = Math.cos(Math.toRadians(pitch));
		yawSin = Math.sin(Math.toRadians(yaw));
		yawCos = Math.cos(Math.toRadians(yaw));
	}

	@Override
	public boolean at(double east, double north, double[] screen)
	{
		return at(east, north, 0, screen);
	}

	/**
	 * Where a point lands on screen: so many tiles east and north of the character and up off the floor.
	 */
	boolean at(double east, double north, double up, double[] screen)
	{
		double across = east * yawCos + north * yawSin;
		double along = north * yawCos - east * yawSin;
		double away = distance + along * pitchCos - up * pitchSin;
		if (away < 0.5)
		{
			return false;
		}
		screen[0] = footX + across * zoom / away;
		screen[1] = footY - (along * pitchSin + up * pitchCos) * zoom / away;
		return true;
	}

	/**
	 * A box standing on the floor, as it lands on screen: its middle so many tiles east and north of the character.
	 */
	Box box(double east, double north, double wide, double tall)
	{
		Box box = new Box();
		double[] screen = new double[2];
		for (int corner = 0; corner < 8; corner++)
		{
			double x = east + ((corner & 3) == 1 || (corner & 3) == 2 ? wide / 2 : -wide / 2);
			double y = north + ((corner & 3) >= 2 ? wide / 2 : -wide / 2);
			at(x, y, corner < 4 ? 0 : tall, screen);
			box.xs[corner] = (int) Math.round(screen[0]);
			box.ys[corner] = (int) Math.round(screen[1]);
		}
		return box;
	}

	static final class Box
	{
		final int[] xs = new int[8];
		final int[] ys = new int[8];

		void draw(Graphics2D g, Color colour)
		{
			for (int face = 0; face < BOX_FIRST.length; face++)
			{
				// a shade a side, so the box reads as a box
				int shade = face / 2 * 9;
				g.setColor(new Color(Math.max(0, colour.getRed() - shade), Math.max(0, colour.getGreen() - shade), Math.max(0, colour.getBlue() - shade)));
				g.fillPolygon(new int[]{xs[BOX_FIRST[face]], xs[BOX_SECOND[face]], xs[BOX_THIRD[face]]},
					new int[]{ys[BOX_FIRST[face]], ys[BOX_SECOND[face]], ys[BOX_THIRD[face]]}, 3);
			}
		}

		void cut(Burst.Cut cut, boolean nearer)
		{
			cut.faces(xs, ys, 8, BOX_FIRST, BOX_SECOND, BOX_THIRD, null, BOX_FIRST.length, nearer);
		}
	}
}
