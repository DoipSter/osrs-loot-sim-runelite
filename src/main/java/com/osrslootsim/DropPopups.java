package com.osrslootsim;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The pop-up for an insane drop in a simulated run: a small card in the chatbox, or the full card with its loot beam
 * in front of the player's character, as the plugin's settings say (or neither). It shows for a few seconds and goes
 * by itself. The drop is a simulated one and the card says so.
 */
final class DropPopups
{
	// As long as osrslootsim.com shows an insane drop
	private static final int SHOWN_MS = 4800;
	private static final int FADE_IN_MS = 150;
	private static final int FADE_OUT_MS = 400;
	private static final String INSANE = "insane";

	private final Client client;
	private final LootSimConfig config;
	private final Icons icons;
	private final OverlayManager overlayManager;
	private final Overlay inChat = new ChatCard();
	private final Overlay inWorld = new CharacterCard();

	// Set on the Swing thread when a run arrives, read on the game's thread every frame
	private volatile DropCard.Drop drop;
	private volatile long shownAt;

	DropPopups(Client client, LootSimConfig config, Icons icons, OverlayManager overlayManager)
	{
		this.client = client;
		this.config = config;
		this.icons = icons;
		this.overlayManager = overlayManager;
	}

	void start()
	{
		overlayManager.add(inChat);
		overlayManager.add(inWorld);
	}

	void stop()
	{
		overlayManager.remove(inChat);
		overlayManager.remove(inWorld);
		drop = null;
	}

	/**
	 * A run arrived: shows its best insane drop, if it has one and pop-ups are on. The site marks which drops are
	 * insane, by its own rule: a collection log item rarer than 1/2,000, or anything worth 10m or more apiece.
	 */
	void ran(LootSimApi.Run run)
	{
		if (config.insaneDropPopup() == PopupPlace.OFF)
		{
			return;
		}
		LootSimApi.Item best = null;
		int insane = 0;
		for (LootSimApi.Item item : run.items)
		{
			if (INSANE.equals(item.tier))
			{
				insane++;
				if (best == null || each(item) > each(best))
				{
					best = item;
				}
			}
		}
		if (best == null)
		{
			return;
		}
		String name = Text.plain(best.name, 40);
		DropCard.Drop shown = new DropCard.Drop(name, best.rate >= 1 ? Text.rate(best.rate) : "", Text.coins(each(best)), insane - 1);
		icons.picture(best.id, name, picture -> shown.picture = picture);
		shownAt = System.currentTimeMillis();
		drop = shown;
	}

	private static long each(LootSimApi.Item item)
	{
		return item.value / Math.max(1, item.qty);
	}

	/**
	 * How solid the pop-up is right now, from 0 to 1; 0 once its time is up.
	 */
	private float opacity()
	{
		long age = System.currentTimeMillis() - shownAt;
		if (drop == null || age < 0 || age >= SHOWN_MS)
		{
			drop = null;
			return 0;
		}
		if (age < FADE_IN_MS)
		{
			return (float) age / FADE_IN_MS;
		}
		return age > SHOWN_MS - FADE_OUT_MS ? (float) (SHOWN_MS - age) / FADE_OUT_MS : 1f;
	}

	/**
	 * The one-line card, at the top of the chatbox.
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
			float opacity = opacity();
			if (shown == null || opacity <= 0 || config.insaneDropPopup() != PopupPlace.CHAT)
			{
				return null;
			}
			Widget chat = client.getWidget(InterfaceID.Chatbox.CHATAREA);
			Rectangle area = chat == null || chat.isHidden() ? null : chat.getBounds();
			// No chatbox to sit in (the login screen, a chatbox that is hidden): the bottom left corner of the game
			int x = area == null ? 8 : area.x + 6;
			int y = area == null ? client.getCanvasHeight() - DropCard.SMALL_HEIGHT - 40 : area.y + 6;
			DropCard.paintSmall(graphics, x, y, shown, opacity);
			return null;
		}
	}

	/**
	 * The full card over the character's upper body, with the loot beam coming down to its feet.
	 */
	private final class CharacterCard extends Overlay
	{
		CharacterCard()
		{
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.ABOVE_SCENE);
			setPriority(PRIORITY_HIGH);
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			DropCard.Drop shown = drop;
			float opacity = opacity();
			if (shown == null || opacity <= 0 || config.insaneDropPopup() != PopupPlace.CHARACTER)
			{
				return null;
			}
			int left = client.getViewportXOffset();
			int top = client.getViewportYOffset();
			int width = client.getViewportWidth();
			int height = client.getViewportHeight();
			if (width <= 0 || height <= 0)
			{
				left = 0;
				top = 0;
				width = client.getCanvasWidth();
				height = client.getCanvasHeight();
			}
			// Where the character stands on screen; the middle of the view when there is no character to find
			int footX = left + width / 2;
			int footY = top + height * 2 / 3;
			int headY = footY - 90;
			Player me = client.getLocalPlayer();
			LocalPoint at = me == null ? null : me.getLocalLocation();
			if (at != null)
			{
				Point feet = Perspective.localToCanvas(client, at, client.getPlane());
				Point head = Perspective.localToCanvas(client, at, client.getPlane(), me.getLogicalHeight());
				if (feet != null && head != null)
				{
					footX = feet.getX();
					footY = feet.getY();
					headY = head.getY();
				}
			}
			DropCard.paintBeam(graphics, footX, footY, top, opacity);
			int x = Math.max(left + 4, Math.min(left + width - DropCard.LARGE_WIDTH - 4, footX - DropCard.LARGE_WIDTH / 2));
			int y = Math.max(top + 4, (headY + footY) / 2 - DropCard.LARGE_HEIGHT);
			DropCard.paintLarge(graphics, x, y, shown, opacity);
			return null;
		}
	}
}
