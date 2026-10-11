package com.osrslootsim;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the insane-drop card and burst to a PNG without starting the game: the card on the left, then the burst at
 * five moments of its life under a stand-in character, on plain dark ground. Run it with "gradlew cardPreview" (see
 * build.gradle). Where they sit in the game, and the real character's outline, are the overlays' business and need
 * the game to see.
 */
public final class CardPreview
{
	private static final long[] MOMENTS = {80, 250, 500, 1500, 3200};

	public static void main(String[] args) throws Exception
	{
		String out = args.length > 0 ? args[0] : "build/cards.png";
		String sprite = args.length > 1 ? args[1] : "";
		Season season = args.length > 2 && !args[2].isEmpty() ? Season.valueOf(args[2].toUpperCase()) : Season.NONE;

		DropCard.Drop drop = new DropCard.Drop("Avernic defender hilt");
		File file = new File(sprite);
		if (file.isFile())
		{
			// As the game hands an item's picture over: the sprite somewhere inside a 36 by 32 box
			BufferedImage item = ImageIO.read(file);
			BufferedImage box = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
			Graphics2D b = box.createGraphics();
			b.drawImage(item, (36 - item.getWidth()) / 2, (32 - item.getHeight()) / 2, null);
			b.dispose();
			drop.picture = box;
		}

		int column = 300;
		int height = 380;
		BufferedImage image = new BufferedImage(DropCard.WIDTH + 40 + column * MOMENTS.length, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(34, 40, 30));
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		DropCard.paintCard(g, 20, 120, drop, 1f);
		for (int i = 0; i < MOMENTS.length; i++)
		{
			int footX = DropCard.WIDTH + 40 + column * i + column / 2;
			int footY = 270;
			int headY = 180;
			// The stand-in character: a grey figure 90 pixels tall, standing on a floor seen from the game's usual
			// angle (a tile 70 pixels wide and 30 deep)
			g.setColor(new Color(120, 120, 120));
			g.fillOval(footX - 9, headY, 18, 18);
			g.fillRect(footX - 7, headY + 18, 14, 72);
			AffineTransform ground = new AffineTransform(70, 0, 0, -30, footX, footY);
			Rectangle view = new Rectangle(DropCard.WIDTH + 40 + column * i, 0, column, height - 30);
			DropCard.paintBurstBehind(g, view, ground, footX, footY, MOMENTS[i], 1f, season, cut ->
			{
				cut.fillOval(footX - 9, headY, 18, 18);
				cut.fillRect(footX - 7, headY + 18, 14, 72);
			});
			g.setColor(new Color(200, 200, 200));
			g.drawString(MOMENTS[i] + " ms", footX - 20, height - 10);
		}
		g.dispose();
		File target = new File(out);
		if (target.getParentFile() != null)
		{
			target.getParentFile().mkdirs();
		}
		ImageIO.write(image, "png", target);
		System.out.println("wrote " + target.getAbsolutePath());
	}
}
