package com.osrslootsim;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the insane-drop pop-ups to a PNG without starting the game: the full card with its loot beam on the left, the
 * chatbox card on the right, on plain dark ground. Run it with "gradlew cardPreview" (see build.gradle). Where they
 * sit in the game is the overlays' business and needs the game to see.
 */
public final class CardPreview
{
	public static void main(String[] args) throws Exception
	{
		String out = args.length > 0 ? args[0] : "build/cards.png";
		String sprite = args.length > 1 ? args[1] : "";

		DropCard.Drop drop = new DropCard.Drop("Draconic visage", "1/5,000", "3.22M gp", 0);
		DropCard.Drop several = new DropCard.Drop("Twisted bow", "1/868", "1.49B gp", 3);
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
			several.picture = box;
		}

		BufferedImage image = new BufferedImage(620, 340, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(34, 40, 30));
		g.fillRect(0, 0, 620, 340);
		DropCard.paintBeam(g, 150, 290, 0, 1f);
		DropCard.paintLarge(g, 50, 40, drop, 1f);
		DropCard.paintSmall(g, 330, 60, drop, 1f);
		DropCard.paintSmall(g, 330, 130, several, 1f);
		DropCard.paintSmall(g, 330, 200, new DropCard.Drop("Tumeken's shadow (uncharged)", "1/3,472", "1.21B gp", 12), 0.5f);
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
