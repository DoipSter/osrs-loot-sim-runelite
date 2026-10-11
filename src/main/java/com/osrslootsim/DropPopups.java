package com.osrslootsim;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.IOException;
import java.time.LocalDate;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemLayer;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The plugin's celebrations. An insane drop in a simulated run gets a card in the chatbox with the item, a burst of
 * light with a loot beam on the floor under the player's character, and the site's pet fanfare. The burst can also
 * mark a level gained, a new collection log item or valuable loot in the game itself. Each part has its own switch in
 * the plugin's settings, and everything goes by itself after a few seconds.
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
	// How far around the character to look for things standing in front of the burst, in tiles
	private static final int AROUND = 6;
	// How far from where it stands a thing is taken to reach at most, in the game's units (128 to a tile)
	private static final int THING_REACH = 448;
	// A model that rises less than this off the floor is part of the floor (a rug, a marking), and the burst lies on it
	private static final int FLOOR_THICK = 24;
	// How much nearer to the camera than the character something must stand to hide the beam
	private static final int NEARER_BY = 40;

	private final Client client;
	private final ClientThread clientThread;
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
	private volatile Burst.Glow burstGlow = Burst.Glow.GOLD;

	DropPopups(Client client, ClientThread clientThread, LootSimConfig config, Icons icons, OverlayManager overlayManager, AudioPlayer audioPlayer)
	{
		this.client = client;
		this.clientThread = clientThread;
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
		// The burst's sheets belong to the game's thread, which may be drawing on them right now
		clientThread.invokeLater(Burst::release);
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
	 * Starts the burst on the character. Called for an insane drop, and for a level, a collection log item or valuable
	 * loot gained in the game when those switches are on.
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
	 * The burst of light and the loot beam, in the game world: on the floor under the player's character, under
	 * everything that stands on that floor, with the beam behind the character.
	 */
	private final class CharacterBurst extends Overlay
	{
		// Where a model lands on screen, corner by corner; kept between frames to spare the garbage
		private int[] cornerX = new int[0];
		private int[] cornerY = new int[0];
		private final double[] spot = new double[2];
		// True while the burst's sheets are in use
		private boolean drawn;

		// The frame being drawn: the camera, the floor, and where the character stands on it
		private double cameraX;
		private double cameraY;
		private double cameraZ;
		private double yawSin;
		private double yawCos;
		private double pitchSin;
		private double pitchCos;
		private double scale;
		private double middleX;
		private double middleY;
		private WorldView world;
		private int plane;
		private int[][][] heights;
		private byte[][][] settings;
		private double standsX;
		private double standsY;
		private double standsZ;
		// False on a boat: its floor moves through the world, and only RuneLite's own way of placing things knows how
		private boolean plain;
		// How far in front of the camera the character is
		private double mine;

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
				if (drawn)
				{
					drawn = false;
					Burst.release();
				}
				return null;
			}
			Player me = client.getLocalPlayer();
			LocalPoint at = me == null ? null : me.getLocalLocation();
			WorldView around = me == null ? null : me.getWorldView();
			if (at == null || around == null)
			{
				return null;   // no character on screen (the login screen): nothing to burst from
			}
			look(around, at);
			Rectangle view = new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(), client.getViewportWidth(), client.getViewportHeight());
			drawn = true;
			Burst.paint(graphics, view, this::floor, System.currentTimeMillis() - began, opacity, burstSeason, burstGlow, cut -> infront(cut, me));
			world = null;
			heights = null;
			settings = null;
			return null;
		}

		/**
		 * Takes down what this frame is drawn with. With the GPU renderer the camera has fractional values, which is
		 * what the picture is drawn with; without it the camera moves in the game's own steps (2048 to a full turn).
		 */
		private void look(WorldView around, LocalPoint at)
		{
			double pitch;
			double yaw;
			if (client.isGpu())
			{
				pitch = client.getCameraFpPitch();
				yaw = client.getCameraFpYaw();
				cameraX = client.getCameraFpX();
				cameraY = client.getCameraFpY();
				cameraZ = client.getCameraFpZ();
			}
			else
			{
				pitch = client.getCameraPitch() * Perspective.UNIT;
				yaw = client.getCameraYaw() * Perspective.UNIT;
				cameraX = client.getCameraX();
				cameraY = client.getCameraY();
				cameraZ = client.getCameraZ();
			}
			yawSin = Math.sin(yaw);
			yawCos = Math.cos(yaw);
			pitchSin = Math.sin(pitch);
			pitchCos = Math.cos(pitch);
			scale = client.getScale();
			middleX = client.getViewportWidth() / 2.0 + client.getViewportXOffset();
			middleY = client.getViewportHeight() / 2.0 + client.getViewportYOffset();
			world = around;
			plane = around.getPlane();
			heights = around.getTileHeights();
			settings = around.getTileSettings();
			plain = around.isTopLevel() && heights != null && settings != null;
			standsX = at.getX();
			standsY = at.getY();
			standsZ = Perspective.getTileHeight(client, at, plane);
			mine = plain ? onScreen(standsX, standsY, standsZ, spot) : -1;
		}

		/**
		 * Where a spot on the floor lands on screen, for Burst: so many tiles east and north of the character, at the
		 * height the floor has there.
		 */
		private boolean floor(double east, double north, double[] screen)
		{
			double x = standsX + east * Perspective.LOCAL_TILE_SIZE;
			double y = standsY + north * Perspective.LOCAL_TILE_SIZE;
			if (plain)
			{
				return onScreen(x, y, height(x, y), screen) > 0;
			}
			Point point = Perspective.localToCanvas(client, new LocalPoint((int) Math.round(x), (int) Math.round(y), world), plane, 0);
			if (point == null)
			{
				return false;
			}
			screen[0] = point.getX();
			screen[1] = point.getY();
			return true;
		}

		/**
		 * How high the floor is at a spot: between the heights of the four corners of its tile, as RuneLite's own
		 * Perspective.getTileHeight works it out, but without rounding. Off the map, the height under the character.
		 */
		private double height(double x, double y)
		{
			int tileX = (int) Math.floor(x / Perspective.LOCAL_TILE_SIZE);
			int tileY = (int) Math.floor(y / Perspective.LOCAL_TILE_SIZE);
			int level = plane;
			if (tileX < 0 || tileY < 0 || level < 0 || level >= heights.length || tileX + 1 >= heights[level].length || tileY + 1 >= heights[level][tileX + 1].length)
			{
				return standsZ;
			}
			// A tile on a bridge has its floor a level up
			if (level < Constants.MAX_Z - 1 && level + 1 < heights.length && settings.length > 1 && tileX < settings[1].length && tileY < settings[1][tileX].length
				&& (settings[1][tileX][tileY] & Constants.TILE_FLAG_BRIDGE) == Constants.TILE_FLAG_BRIDGE)
			{
				level++;
			}
			int[][] corner = heights[level];
			double across = x / Perspective.LOCAL_TILE_SIZE - tileX;
			double up = y / Perspective.LOCAL_TILE_SIZE - tileY;
			double south = corner[tileX][tileY] * (1 - across) + corner[tileX + 1][tileY] * across;
			double north = corner[tileX][tileY + 1] * (1 - across) + corner[tileX + 1][tileY + 1] * across;
			return south * (1 - up) + north * up;
		}

		/**
		 * Where a point of the game world lands on screen, as RuneLite's own Perspective.localToCanvas works it out but
		 * without rounding to whole pixels: a tile is only a few dozen pixels wide, so whole pixels made the circles on
		 * the floor jump from frame to frame while the camera turned. Fills screen and answers how far in front of the
		 * camera the point is; answers -1, leaving screen alone, for a point behind the camera.
		 */
		private double onScreen(double x, double y, double z, double[] screen)
		{
			x -= cameraX;
			y -= cameraY;
			z -= cameraZ;
			double across = x * yawCos + y * yawSin;
			double along = y * yawCos - x * yawSin;
			double down = z * pitchCos - along * pitchSin;
			double away = along * pitchCos + z * pitchSin;
			if (away < 50)
			{
				return -1;
			}
			screen[0] = middleX + across * scale / away;
			screen[1] = middleY + down * scale / away;
			return away;
		}

		// Whether something standing at a point is between the character and the camera
		private boolean nearer(double x, double y, double z)
		{
			if (!plain || mine < 0)
			{
				return false;
			}
			double away = onScreen(x, y, z, spot);
			return away > 0 && away < mine - NEARER_BY;
		}

		/**
		 * Wipes off the burst everything that stands in front of it: the character, other players and monsters, and on
		 * the tiles around, scenery, walls, what hangs on them, and items lying on the floor.
		 */
		private void infront(Burst.Cut cut, Player me)
		{
			actor(cut, me, true);
			for (NPC npc : world.npcs())
			{
				actor(cut, npc, false);
			}
			for (Player player : world.players())
			{
				if (player != me)
				{
					actor(cut, player, false);
				}
			}

			Tile[][][] levels = world.getScene().getTiles();
			if (levels == null || plane < 0 || plane >= levels.length || levels[plane] == null)
			{
				return;
			}
			Tile[][] tiles = levels[plane];
			int middleTileX = (int) Math.floor(standsX / Perspective.LOCAL_TILE_SIZE);
			int middleTileY = (int) Math.floor(standsY / Perspective.LOCAL_TILE_SIZE);
			int fromX = Math.max(0, middleTileX - AROUND);
			int fromY = Math.max(0, middleTileY - AROUND);
			for (int tileX = fromX; tileX <= middleTileX + AROUND && tileX < tiles.length; tileX++)
			{
				for (int tileY = fromY; tileY <= middleTileY + AROUND && tileY < tiles[tileX].length; tileY++)
				{
					Tile tile = tiles[tileX][tileY];
					if (tile != null)
					{
						tile(cut, tile, tileX, tileY, fromX, fromY);
					}
				}
			}
		}

		private void tile(Burst.Cut cut, Tile tile, int tileX, int tileY, int fromX, int fromY)
		{
			GameObject[] objects = tile.getGameObjects();
			if (objects != null)
			{
				for (GameObject object : objects)
				{
					// A player or a monster is listed among the scenery of its tile too, and has its turn by itself
					if (object == null || object.getRenderable() instanceof Actor)
					{
						continue;
					}
					// One that covers several tiles is listed on each of them: taken on the first it has among those looked at
					Point corner = object.getSceneMinLocation();
					if (corner != null && (Math.max(corner.getX(), fromX) != tileX || Math.max(corner.getY(), fromY) != tileY))
					{
						continue;
					}
					int z = object.getZ();
					thing(cut, object.getRenderable(), object.getX(), object.getY(), z, object.getModelOrientation(),
						nearer(object.getX(), object.getY(), z), false, THING_REACH + Math.max(object.sizeX(), object.sizeY()) * Perspective.LOCAL_TILE_SIZE / 2);
				}
			}

			WallObject wall = tile.getWallObject();
			if (wall != null)
			{
				// A wall stands on an edge of its tile: that edge, not the middle of the tile, says which side of the
				// character it is on
				int side = wall.getOrientationA();
				int edgeX = wall.getX() + ((side & 4) != 0 ? 60 : (side & 1) != 0 ? -60 : 0);
				int edgeY = wall.getY() + ((side & 2) != 0 ? 60 : (side & 8) != 0 ? -60 : 0);
				boolean nearer = nearer(edgeX, edgeY, wall.getZ());
				thing(cut, wall.getRenderable1(), wall.getX(), wall.getY(), wall.getZ(), 0, nearer, true, THING_REACH);
				thing(cut, wall.getRenderable2(), wall.getX(), wall.getY(), wall.getZ(), 0, nearer, true, THING_REACH);
			}

			DecorativeObject hung = tile.getDecorativeObject();
			if (hung != null)
			{
				int z = hung.getZ();
				boolean nearer = nearer(hung.getX() + hung.getXOffset(), hung.getY() + hung.getYOffset(), z);
				thing(cut, hung.getRenderable(), hung.getX() + hung.getXOffset(), hung.getY() + hung.getYOffset(), z, 0, nearer, true, THING_REACH);
				thing(cut, hung.getRenderable2(), hung.getX() + hung.getXOffset2(), hung.getY() + hung.getYOffset2(), z, 0, nearer, true, THING_REACH);
			}

			GroundObject low = tile.getGroundObject();
			if (low != null)
			{
				thing(cut, low.getRenderable(), low.getX(), low.getY(), low.getZ(), 0, nearer(low.getX(), low.getY(), low.getZ()), false, THING_REACH);
			}

			ItemLayer pile = tile.getItemLayer();
			if (pile != null)
			{
				int z = pile.getZ() - pile.getHeight();
				boolean nearer = nearer(pile.getX(), pile.getY(), z);
				thing(cut, pile.getBottom(), pile.getX(), pile.getY(), z, 0, nearer, true, THING_REACH);
				thing(cut, pile.getMiddle(), pile.getX(), pile.getY(), z, 0, nearer, true, THING_REACH);
				thing(cut, pile.getTop(), pile.getX(), pile.getY(), z, 0, nearer, true, THING_REACH);
			}
		}

		/**
		 * Wipes a player or a monster off the burst. self: the player's own character, which is always in front of the
		 * beam.
		 */
		private void actor(Burst.Cut cut, Actor actor, boolean self)
		{
			LocalPoint where = actor.getLocalLocation();
			if (where == null || Math.abs(where.getX() - standsX) > (AROUND + 2) * Perspective.LOCAL_TILE_SIZE
				|| Math.abs(where.getY() - standsY) > (AROUND + 2) * Perspective.LOCAL_TILE_SIZE)
			{
				return;
			}
			int z = Perspective.getFootprintTileHeight(client, where, plane, actor.getFootprintSize());
			thing(cut, actor, where.getX(), where.getY(), z, actor.getCurrentOrientation(), self || nearer(where.getX(), where.getY(), z), true,
				THING_REACH + actor.getFootprintSize() * Perspective.LOCAL_TILE_SIZE / 2);
		}

		/**
		 * Wipes one thing off the burst, face by face of its model, placed the way RuneLite's own outline drawing
		 * places it. x, y, z: where it stands. nearer: whether it is between the character and the camera. lowToo:
		 * false for scenery, where something that barely rises off the floor is taken as part of the floor. reach: how
		 * far from where it stands it is taken to reach at most, in the game's units.
		 */
		private void thing(Burst.Cut cut, Renderable renderable, int x, int y, int z, int orientation, boolean nearer, boolean lowToo, int reach)
		{
			if (renderable == null)
			{
				return;
			}
			z -= renderable.getAnimationHeightOffset();
			if (plain)
			{
				// Not worth working out the shape of something that is nowhere near the burst on screen
				double away = onScreen(x, y, z, spot);
				if (away < 0 || !cut.reaches(spot[0], spot[1], reach * scale / away + 4, nearer))
				{
					return;
				}
			}
			Model model = renderable instanceof Model ? (Model) renderable : renderable.getModel();
			if (model == null)
			{
				return;
			}
			int corners = model.getVerticesCount();
			float[] heightOf = model.getVerticesY();
			if (!lowToo)
			{
				// up is the smaller number
				float highest = 0;
				for (int i = 0; i < corners; i++)
				{
					highest = Math.min(highest, heightOf[i]);
				}
				if (-highest < FLOOR_THICK)
				{
					return;
				}
			}
			if (cornerX.length < corners)
			{
				cornerX = new int[corners];
				cornerY = new int[corners];
			}
			Perspective.modelToCanvas(client, world, corners, x, y, z, orientation, model.getVerticesX(), model.getVerticesZ(), heightOf, cornerX, cornerY);
			cut.faces(cornerX, cornerY, corners, model.getFaceIndices1(), model.getFaceIndices2(), model.getFaceIndices3(), model.getFaceTransparencies(),
				model.getFaceCount(), nearer);
		}
	}
}
