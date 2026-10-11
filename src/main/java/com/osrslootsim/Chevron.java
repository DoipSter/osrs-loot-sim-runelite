package com.osrslootsim;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;
import net.runelite.client.ui.ColorScheme;

/**
 * A small arrow: pointing down on something that is open or opens a list, pointing right on something closed.
 */
final class Chevron implements Icon
{
	private final boolean down;

	Chevron(boolean down)
	{
		this.down = down;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		if (down)
		{
			g2.drawPolyline(new int[]{x + 1, x + 5, x + 9}, new int[]{y + 2, y + 6, y + 2}, 3);
		}
		else
		{
			g2.drawPolyline(new int[]{x + 3, x + 7, x + 3}, new int[]{y, y + 4, y + 8}, 3);
		}
		g2.dispose();
	}

	@Override
	public int getIconWidth()
	{
		return 10;
	}

	@Override
	public int getIconHeight()
	{
		return 9;
	}
}
