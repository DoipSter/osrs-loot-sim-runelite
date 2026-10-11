package com.osrslootsim;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.time.LocalDate;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The plugin's celebrations. An insane drop in a simulated run gets a card in the chatbox with the item, a burst of
 * light with a loot beam on the floor under the player's character (drawn behind the character), and the site's pet
 * fanfare. The burst can also mark a level gained or a new collection log item in the game itself. Each part has its
 * own switch in the plugin's settings, and everything goes by itself after a few seconds.
 */
final class DropPopups
{
	// As long as osrslootsim.com shows an insane drop
	private static final int SHOWN_MS = 4800;
	private static final int FADE_IN_MS = 150;
	private static final int FADE_OUT_MS = 400;
	// Two reports of one event (a chat line and a pop-up for the same collection log item) are one burst
	private static final int SAME_EVENT_MS = 1500;
	private static final String INSANE = "insane";

	private final Client client;
	private final LootSimConfig config;
	private final Icons icons;
	private final OverlayManager overlayManager;
	private final AudioPlayer audioPlayer;
	private final Overlay card = new ChatCard();
	private final Overlay burst = new CharacterBurst();

	// Set when something happens (a run arriving on the Swing thread, a game event on the game's thread), read on the
	// game's thread every frame
	private volatile DropCard.Drop drop;
	private volatile long cardAt;
	private volatile long burstAt;
	// The holiday the burst is dressed for, settled when it starts
	private volatile Season burstSeason = Season.NONE;
	private volatile DropCard.Glow burstGlow = DropCard.Glow.GOLD;

	DropPopups(Client client, LootSimConfig config, Icons icons, OverlayManager overlayManager, AudioPlayer audioPlayer)
	{
		this.client = client;
		this.config = config;
		this.icons = icons;
		this.overlayManager = overlayManager;
		this.audioPlayer = audioPlayer;
	}

	void start()
	{
		overlayManager.add(card);
		overlayManager.add(burst);
	}

	void stop()
	{
		overlayManager.remove(card);
		overlayManager.remove(burst);
		drop = null;
		burstAt = 0;
	}

	/**
	 * A run arrived: celebrates its best insane drop, if it has one, with whichever of the card, the burst and the
	 * sound are switched on. The site marks which drops are insane, by its own rule: a collection log item rarer
	 * than 1/2,000, or anything worth 10m or more apiece.
	 */
	void ran(LootSimApi.Run run)
	{
		LootSimApi.Item best = null;
		for (LootSimApi.Item item : run.items)
		{
			if (INSANE.equals(item.tier) && (best == null || each(item) > each(best)))
			{
				best = item;
			}
		}
		if (best == null)
		{
			return;
		}
		if (config.insaneDropCard())
		{
			String name = Text.plain(best.name, 40);
			DropCard.Drop shown = new DropCard.Drop(name);
			icons.picture(best.id, name, picture -> shown.picture = picture);
			cardAt = System.currentTimeMillis();
			drop = shown;
		}
		if (config.insaneDropBurst())
		{
			burst();
		}
		if (config.insaneDropSound())
		{
			fanfare();
		}
	}

	/**
	 * Starts the burst on the character. Called for an insane drop, and for a level or a collection log item gained
	 * in the game when those switches are on.
	 */
	void burst()
	{
		long now = System.currentTimeMillis();
		if (now - burstAt > SAME_EVENT_MS)
		{
			burstSeason = config.holidayLook().season(LocalDate.now());
			burstGlow = config.beamColour().glow(burstSeason);
			burstAt = now;
		}
	}

	/**
	 * Plays the site's pet fanfare, the holiday's own in season, at the volume the settings give.
	 */
	private void fanfare()
	{
		int volume = Math.max(0, Math.min(100, config.soundVolume()));
		if (volume == 0)
		{
			return;
		}
		Season season = config.holidayLook().season(LocalDate.now());
		// The player takes a gain in decibels: every halving of the volume is 6 less
		float gain = (float) (20 * Math.log10(volume / 100.0));
		try
		{
			audioPlayer.play(DropPopups.class, season.fanfare(), gain);
		}
		catch (IOException | UnsupportedAudioFileException | LineUnavailableException | IllegalArgumentException e)
		{
			// no sound device, or one that will not take the format: the card and the burst still show
		}
	}

	private static long each(LootSimApi.Item item)
	{
		return item.value / Math.max(1, item.qty);
	}

	/**
	 * How solid something that began at the given time is right now, from 0 to 1; 0 once its time is up.
	 */
	private static float opacity(long began)
	{
		long age = System.currentTimeMillis() - began;
		if (age < 0 || age >= SHOWN_MS)
		{
			return 0;
		}
		if (age < FADE_IN_MS)
		{
			return (float) age / FADE_IN_MS;
		}
		return age > SHOWN_MS - FADE_OUT_MS ? (float) (SHOWN_MS - age) / FADE_OUT_MS : 1f;
	}

	/**
	 * The card, in the chatbox.
	 */
	private final class ChatCard extends Overlay
	{
		ChatCard()
		{
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.ABOVE_WIDGETS);
			setPriority(PRIORITY_HIGH);
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			DropCard.Drop shown = drop;
			float opacity = opacity(cardAt);
			if (shown == null || opacity <= 0)
			{
				drop = null;
				return null;
			}
			Widget chat = client.getWidget(InterfaceID.Chatbox.CHATAREA);
			Rectangle area = chat == null || chat.isHidden() ? null : chat.getBounds();
			// Left side of the chatbox, its bottom edge just inside the chatbox's own. No chatbox to sit in (the login
			// screen, a chatbox that is hidden): the bottom left corner of the game.
			int x = area == null ? 8 : area.x + 6;
			int y = (area == null ? client.getCanvasHeight() - 30 : area.y + area.height - 6) - DropCard.HEIGHT;
			DropCard.paintCard(graphics, x, Math.max(2, y), shown, opacity);
			return null;
		}
	}

	/**
	 * The burst of light and the loot beam, on the floor under the player's character and behind it.
	 */
	private final class CharacterBurst extends Overlay
	{
		// Where the character's model lands on screen, corner by corner; kept between frames to spare the garbage
		private int[] cornerX = new int[0];
		private int[] cornerY = new int[0];

		CharacterBurst()
		{
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.ABOVE_SCENE);
			setPriority(PRIORITY_HIGH);
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			long began = burstAt;
			float opacity = opacity(began);
			if (opacity <= 0)
			{
				return null;
			}
			Player me = client.getLocalPlayer();
			LocalPoint at = me == null ? null : me.getLocalLocation();
			WorldView world = me == null ? null : me.getWorldView();
			if (at == null || world == null)
			{
				return null;   // no character on screen (the login screen): nothing to burst from
			}
			// The floor under the character: where it stands, and one tile east and one tile north of it at the same
			// height, give the slant every circle on the floor is drawn with
			int height = Perspective.getTileHeight(client, at, world.getPlane());
			double[] feet = onScreen(at.getX(), at.getY(), height);
			double[] east = onScreen(at.getX() + Perspective.LOCAL_TILE_SIZE, at.getY(), height);
			double[] north = onScreen(at.getX(), at.getY() + Perspective.LOCAL_TILE_SIZE, height);
			if (feet == null || east == null || north == null)
			{
				return null;
			}
			AffineTransform ground = new AffineTransform(east[0] - feet[0], east[1] - feet[1], north[0] - feet[0], north[1] - feet[1], feet[0], feet[1]);
			Rectangle view = new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(), client.getViewportWidth(), client.getViewportHeight());
			DropCard.paintBurstBehind(graphics, view, ground, (int) Math.round(feet[0]), (int) Math.round(feet[1]), System.currentTimeMillis() - began, opacity,
				burstSeason, burstGlow, sheet -> outline(sheet, me, at, world));
			return null;
		}

		/**
		 * Where a point of the game world lands on screen, as RuneLite's own Perspective.localToCanvas works it out but
		 * without rounding to whole pixels. A tile is only a few dozen pixels wide, so whole pixels made the circles on
		 * the floor jump by a few percent from frame to frame while the camera turned. null for a point behind the
		 * camera. With the GPU renderer the camera has fractional values, which is what the picture is drawn with;
		 * without it the camera moves in the game's own steps (2048 to a full turn).
		 */
		private double[] onScreen(double x, double y, double z)
		{
			double pitch;
			double yaw;
			if (client.isGpu())
			{
				pitch = client.getCameraFpPitch();
				yaw = client.getCameraFpYaw();
				x -= client.getCameraFpX();
				y -= client.getCameraFpY();
				z -= client.getCameraFpZ();
			}
			else
			{
				pitch = client.getCameraPitch() * Perspective.UNIT;
				yaw = client.getCameraYaw() * Perspective.UNIT;
				x -= client.getCameraX();
				y -= client.getCameraY();
				z -= client.getCameraZ();
			}
			double across = x * Math.cos(yaw) + y * Math.sin(yaw);
			double along = y * Math.cos(yaw) - x * Math.sin(yaw);
			double down = z * Math.cos(pitch) - along * Math.sin(pitch);
			double away = along * Math.cos(pitch) + z * Math.sin(pitch);
			if (away < 50)
			{
				return null;
			}
			double scale = client.getScale();
			return new double[]{
				client.getViewportWidth() / 2.0 + across * scale / away + client.getViewportXOffset(),
				client.getViewportHeight() / 2.0 + down * scale / away + client.getViewportYOffset()};
		}

		/**
		 * Fills the character's shape on screen, face by face of its model, placed the way RuneLite's own outline
		 * drawing places an actor's model. What it fills is wiped off the burst, which leaves the character in front.
		 */
		private void outline(Graphics2D sheet, Player me, LocalPoint at, WorldView world)
		{
			Model model = me.getModel();
			if (model == null)
			{
				return;
			}
			int corners = model.getVerticesCount();
			if (cornerX.length < corners)
			{
				cornerX = new int[corners];
				cornerY = new int[corners];
			}
			int height = Perspective.getFootprintTileHeight(client, at, world.getPlane(), me.getFootprintSize()) - me.getAnimationHeightOffset();
			Perspective.modelToCanvas(client, world, corners, at.getX(), at.getY(), height, me.getCurrentOrientation(),
				model.getVerticesX(), model.getVerticesZ(), model.getVerticesY(), cornerX, cornerY);
			int[] first = model.getFaceIndices1();
			int[] second = model.getFaceIndices2();
			int[] third = model.getFaceIndices3();
			byte[] clear = model.getFaceTransparencies();
			int[] xs = new int[3];
			int[] ys = new int[3];
			for (int face = 0; face < model.getFaceCount(); face++)
			{
				int a = first[face];
				int b = second[face];
				int c = third[face];
				// Skipped: a face that is see-through, or has a corner behind the camera
				if (a < 0 || b < 0 || c < 0 || a >= corners || b >= corners || c >= corners || clear != null && (clear[face] & 0xFF) >= 254
					|| cornerX[a] == Integer.MIN_VALUE || cornerX[b] == Integer.MIN_VALUE || cornerX[c] == Integer.MIN_VALUE)
				{
					continue;
				}
				xs[0] = cornerX[a];
				xs[1] = cornerX[b];
				xs[2] = cornerX[c];
				ys[0] = cornerY[a];
				ys[1] = cornerY[b];
				ys[2] = cornerY[c];
				sheet.fillPolygon(xs, ys, 3);
			}
		}
	}
}
