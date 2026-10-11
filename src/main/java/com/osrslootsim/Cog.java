package com.osrslootsim;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/**
 * A small cog, for the button that opens the plugin's settings.
 */
final class Cog implements Icon
{
	private static final int SIZE = 14;
	private static final Shape SHAPE = shape();

	private final Color colour;

	Cog(Color colour)
	{
		this.colour = colour;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.translate(x + SIZE / 2.0, y + SIZE / 2.0);
		g2.setColor(colour);
		g2.fill(SHAPE);
		g2.dispose();
	}

	@Override
	public int getIconWidth()
	{
		return SIZE;
	}

	@Override
	public int getIconHeight()
	{
		return SIZE;
	}

	// Eight teeth round a wheel with a hole through its middle, drawn around 0, 0
	private static Shape shape()
	{
		Path2D cog = new Path2D.Double(Path2D.WIND_EVEN_ODD);
		int teeth = 8;
		for (int tooth = 0; tooth < teeth; tooth++)
		{
			double middle = tooth * 2 * Math.PI / teeth;
			// a tooth's two feet on the wheel and its two tips: an angle and how far out
			double[][] corners = {{middle - 0.28, 4.6}, {middle - 0.17, 6.7}, {middle + 0.17, 6.7}, {middle + 0.28, 4.6}};
			for (double[] corner : corners)
			{
				double x = Math.cos(corner[0]) * corner[1];
				double y = Math.sin(corner[0]) * corner[1];
				if (cog.getCurrentPoint() == null)
				{
					cog.moveTo(x, y);
				}
				else
				{
					cog.lineTo(x, y);
				}
			}
		}
		cog.closePath();
		cog.append(new Ellipse2D.Double(-2.1, -2.1, 4.2, 4.2), false);
		return cog;
	}
}
