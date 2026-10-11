package com.osrslootsim;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/**
 * A small drawing pin, for pinning a boss to the row under the boss box: solid on a boss that is pinned, an outline
 * on one that is not.
 */
final class Pin implements Icon
{
	private static final int SIZE = 14;
	private static final double LEAN = Math.toRadians(40);
	private static final Shape HEAD = head();

	private final Color colour;
	private final boolean solid;

	Pin(Color colour, boolean solid)
	{
		this.colour = colour;
		this.solid = solid;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2.translate(x + SIZE / 2.0, y + SIZE / 2.0);
		g2.rotate(LEAN);
		g2.setColor(colour);
		g2.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		if (solid)
		{
			g2.fill(HEAD);
		}
		g2.draw(HEAD);
		g2.draw(new Line2D.Double(0, 1.6, 0, 6.2));   // the needle
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

	// The cap, the grip that widens under it and the plate the needle comes out of, standing upright around 0, 0
	private static Shape head()
	{
		Path2D head = new Path2D.Double();
		head.moveTo(-3, -6);
		head.lineTo(3, -6);
		head.lineTo(3, -4.8);
		head.lineTo(2, -4.8);
		head.lineTo(2, -1.6);
		head.quadTo(2.4, 0, 4, 0.4);
		head.lineTo(4, 1.6);
		head.lineTo(-4, 1.6);
		head.lineTo(-4, 0.4);
		head.quadTo(-2.4, 0, -2, -1.6);
		head.lineTo(-2, -4.8);
		head.lineTo(-3, -4.8);
		head.closePath();
		return head;
	}
}
