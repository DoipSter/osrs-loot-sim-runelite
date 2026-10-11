package com.osrslootsim;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts RuneLite with the plugin built in, the way RuneLite's plugin template does for a plugin under development.
 */
public class LootSimPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LootSimPlugin.class);
		RuneLite.main(args);
	}
}
