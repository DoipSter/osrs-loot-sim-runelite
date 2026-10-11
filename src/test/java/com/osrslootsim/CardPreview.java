package com.osrslootsim;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the pop-up card and the burst to a PNG without starting the game. On the left the card four ways: for an
 * insane drop, for a collection log item, unlocked at one and a half times its size, and at its smallest. Then the
 * burst at five moments of its life, on plain dark ground. The top row is seen from the game's usual camera angle, the bottom
 * row from a low one turned a little, where the floor runs away from the eye. In each a grey figure stands in for the
 * character, a crate stands on the floor beside it (the burst must lie under it) and a post stands between the
 * character and the camera (it must hide the beam). Run it with "gradlew cardPreview" (see build.gradle). The real
 * floor and the real shapes of things are the overlay's business and need the game to see.
 */
public final class CardPreview
{
	private static final long[] MOMENTS = {80, 250, 500, 1500, 3200};
	private static final int COLUMN = 330;
	private static final int ROW = 380;
	// The stand-in character's head, as corners round a middle (the last corner)
	private static final int HEAD_SIDES = 10;

	public static void main(String[] args) throws Exception
	{
		String out = args.length > 0 ? args[0] : "build/cards.png";
		String sprite = args.length > 1 ? args[1] : "";
		Season season = args.length > 2 && !args[2].isEmpty() ? Season.valueOf(args[2].toUpperCase()) : Season.NONE;
		Burst.Glow glow = args.length > 3 && !args[3].isEmpty() ? Burst.Glow.valueOf(args[3].toUpperCase()) : BeamColour.BY_HOLIDAY.glow(season);

		DropCard.Drop drop = new DropCard.Drop(DropCard.Kind.INSANE, "Avernic defender hilt");
		DropCard.Drop logged = new DropCard.Drop(DropCard.Kind.COLLECTION_LOG, "Giant egg sac(full)");
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
			logged.picture = box;
		}

		int left = Math.round(DropCard.WIDTH * 1.5f) + 40;
		BufferedImage image = new BufferedImage(left + COLUMN * MOMENTS.length, 2 * ROW, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(34, 40, 30));
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		DropCard.paintCard(g, 20, 24, 1f, drop, 1f);
		DropCard.paintCard(g, 20, 180, 1f, logged, 1f);
		String[] how = {"Hold Alt, then", "drag to move, drag an edge to resize,", "right-click to put it back"};
		DropCard.paintCard(g, 20, 400, 1.5f, drop, 1f);
		DropCard.paintUnlocked(g, 20, 400, DropCard.size(drop.kind, 1.5f), how, 20, 400);
		DropCard.paintCard(g, 20, 630, DropCard.SMALLEST, logged, 1f);
		DropCard.paintUnlocked(g, 20, 630, DropCard.size(logged.kind, DropCard.SMALLEST), how, 20, 0);
		for (int row = 0; row < 2; row++)
		{
			for (int i = 0; i < MOMENTS.length; i++)
			{
				int footX = left + COLUMN * i + COLUMN / 2;
				int footY = ROW * row + (row == 0 ? 250 : 270);
				Stage stage = row == 0 ? new Stage(footX, footY, 62, 27, 0, 12) : new Stage(footX, footY, 74, 11, 25, 7);
				Rectangle view = new Rectangle(left + COLUMN * i, ROW * row, COLUMN, ROW - 30);
				scene(g, stage, view, footX, footY, MOMENTS[i], season, glow);
				g.setColor(new Color(200, 200, 200));
				g.drawString(MOMENTS[i] + " ms", footX - 20, ROW * (row + 1) - 10);
			}
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

	private static void scene(Graphics2D target, Stage stage, Rectangle view, int footX, int footY, long age, Season season, Burst.Glow glow)
	{
		Graphics2D g = (Graphics2D) target.create();
		g.setClip(view);
		// What stands in the scene, far to near: the crate, the character, the post
		Stage.Box crate = stage.box(1.5, 0.7, 0.9, 0.8);
		Stage.Box post = stage.box(0.1, -1.7, 0.3, 2.4);
		int headY = footY - 90;
		int[] xs = new int[HEAD_SIDES + 5];
		int[] ys = new int[HEAD_SIDES + 5];
		int[] first = new int[HEAD_SIDES + 2];
		int[] second = new int[HEAD_SIDES + 2];
		int[] third = new int[HEAD_SIDES + 2];
		for (int k = 0; k < HEAD_SIDES; k++)
		{
			xs[k] = footX + (int) Math.round(9 * Math.cos(k * 2 * Math.PI / HEAD_SIDES));
			ys[k] = headY + 9 + (int) Math.round(9 * Math.sin(k * 2 * Math.PI / HEAD_SIDES));
			first[k] = HEAD_SIDES;
			second[k] = k;
			third[k] = (k + 1) % HEAD_SIDES;
		}
		xs[HEAD_SIDES] = footX;
		ys[HEAD_SIDES] = headY + 9;
		int body = HEAD_SIDES + 1;
		int[][] corners = {{-7, headY + 18}, {7, headY + 18}, {7, footY}, {-7, footY}};
		for (int k = 0; k < 4; k++)
		{
			xs[body + k] = footX + corners[k][0];
			ys[body + k] = corners[k][1];
		}
		first[HEAD_SIDES] = body;
		second[HEAD_SIDES] = body + 1;
		third[HEAD_SIDES] = body + 2;
		first[HEAD_SIDES + 1] = body;
		second[HEAD_SIDES + 1] = body + 2;
		third[HEAD_SIDES + 1] = body + 3;

		crate.draw(g, new Color(150, 104, 58));
		g.setColor(new Color(120, 120, 120));
		g.fillOval(footX - 9, headY, 18, 18);
		g.fillRect(footX - 7, headY + 18, 14, 72);
		post.draw(g, new Color(96, 100, 110));
		Burst.paint(g, view, stage, age, 1f, season, glow, cut ->
		{
			crate.cut(cut, false);
			cut.faces(xs, ys, xs.length, first, second, third, null, first.length, true);
			post.cut(cut, true);
		});
		g.dispose();
	}
}
