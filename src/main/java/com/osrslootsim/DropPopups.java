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
 * What an insane drop in a simulated run gets: a card in the chatbox with the item, and a burst of light with a loot
 * beam on the player's character. Each has its own switch in the plugin's settings. Both last a few seconds and go by
 * themselves.
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
	private final Overlay card = new ChatCard();
	private final Overlay burst = new CharacterBurst();

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
		overlayManager.add(card);
		overlayManager.add(burst);
	}

	void stop()
	{
		overlayManager.remove(card);
		overlayManager.remove(burst);
		drop = null;
	}

	/**
	 * A run arrived: celebrates its best insane drop, if it has one and either switch is on. The site marks which
	 * drops are insane, by its own rule: a collection log item rarer than 1/2,000, or anything worth 10m or more
	 * apiece.
	 */
	void ran(LootSimApi.Run run)
	{
		if (!config.insaneDropCard() && !config.insaneDropBurst())
		{
			return;
		}
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
		String name = Text.plain(best.name, 40);
		DropCard.Drop shown = new DropCard.Drop(name);
		icons.picture(best.id, name, picture -> shown.picture = picture);
		shownAt = System.currentTimeMillis();
		drop = shown;
	}

	private static long each(LootSimApi.Item item)
	{
		return item.value / Math.max(1, item.qty);
	}

	/**
	 * How solid the celebration is right now, from 0 to 1; 0 once its time is up.
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
			float opacity = opacity();
			if (shown == null || opacity <= 0 || !config.insaneDropCard())
			{
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
	 * The burst of light and the loot beam, on the player's character.
	 */
	private final class CharacterBurst extends Overlay
	{
		CharacterBurst()
		{
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.ABOVE_SCENE);
			setPriority(PRIORITY_HIGH);
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			float opacity = opacity();
			if (drop == null || opacity <= 0 || !config.insaneDropBurst())
			{
				return null;
			}
			Player me = client.getLocalPlayer();
			LocalPoint at = me == null ? null : me.getLocalLocation();
			if (at == null)
			{
				return null;   // no character on screen (the login screen): nothing to burst from
			}
			Point feet = Perspective.localToCanvas(client, at, client.getPlane());
			Point head = Perspective.localToCanvas(client, at, client.getPlane(), me.getLogicalHeight());
			if (feet == null || head == null)
			{
				return null;
			}
			DropCard.paintBurst(graphics, feet.getX(), feet.getY(), head.getY(), client.getViewportYOffset(),
				System.currentTimeMillis() - shownAt, opacity);
			return null;
		}
	}
}
