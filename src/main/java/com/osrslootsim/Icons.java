package com.osrslootsim;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.JLabel;

/**
 * Where the panel gets its pictures. In the game they come from the game itself; the panel preview has stand-ins.
 */
interface Icons
{
	/**
	 * Puts an item's inventory picture on the label once it is ready, with its stack count when there is more than
	 * one. The simulator sends an item ID where it knows one (0 otherwise); the name finds the rest.
	 */
	void item(JLabel label, int id, String name, long quantity);

	/**
	 * Hands over the boss's hiscores picture on the Swing thread, if it has one.
	 */
	void boss(String name, Consumer<BufferedImage> use);
}
