package com.osrslootsim;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.Timer;
import net.runelite.client.util.ImageUtil;

/**
 * The Looting skill icon beside the level, as on osrslootsim.com: a loot beam over a stack of coins that fires every
 * three seconds, dressed for the holiday in season. It moves only while the panel is open. With the movement switched
 * off it is the still icon at the same scale.
 */
final class SkillIcon extends JComponent
{
	// Every strip holds the same three-second loop: this many frames side by side, at this many a second
	private static final int FRAMES = 45;
	private static final int FRAMES_PER_SECOND = 15;
	// The strips are drawn at twice the 16-pixel icon's size, so the still icon is doubled to match
	private static final int STILL_SCALE = 2;

	private final Timer timer = new Timer(1000 / FRAMES_PER_SECOND, e -> step());
	private BufferedImage strip;
	private BufferedImage still;
	private Season season;
	private boolean moving;
	private boolean playing;
	private int frame;

	SkillIcon()
	{
		set(Season.NONE, false);
	}

	/**
	 * Which icon to show, and whether it moves.
	 */
	void set(Season season, boolean moving)
	{
		if (season == this.season && moving == this.moving)
		{
			return;
		}
		this.season = season;
		this.moving = moving;
		BufferedImage small = ImageUtil.loadImageResource(SkillIcon.class, season.still());
		still = ImageUtil.resizeImage(small, small.getWidth() * STILL_SCALE, small.getHeight() * STILL_SCALE, false);
		strip = moving ? ImageUtil.loadImageResource(SkillIcon.class, season.strip()) : null;
		frame = 0;
		setPreferredSize(strip != null ? new Dimension(strip.getWidth() / FRAMES, strip.getHeight())
			: new Dimension(still.getWidth(), still.getHeight()));
		run();
		revalidate();
		repaint();
	}

	/**
	 * The panel opening (true) or closing (false): the icon only moves while someone can see it.
	 */
	void play(boolean playing)
	{
		this.playing = playing;
		run();
	}

	/**
	 * Shows one frame of the loop and holds it. For the panel preview.
	 */
	void hold(int frame)
	{
		timer.stop();
		this.frame = Math.floorMod(frame, FRAMES);
		repaint();
	}

	private void run()
	{
		if (strip != null && playing)
		{
			timer.start();
		}
		else
		{
			timer.stop();
		}
	}

	private void step()
	{
		frame = (frame + 1) % FRAMES;
		if (isShowing())
		{
			repaint();
		}
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		if (strip == null)
		{
			g.drawImage(still, (getWidth() - still.getWidth()) / 2, getHeight() - still.getHeight(), null);
			return;
		}
		int width = strip.getWidth() / FRAMES;
		int x = (getWidth() - width) / 2;
		int y = getHeight() - strip.getHeight();
		g.drawImage(strip, x, y, x + width, y + strip.getHeight(), frame * width, 0, (frame + 1) * width, strip.getHeight(), null);
	}
}
