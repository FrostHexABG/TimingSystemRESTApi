package com.frosthex.timingsystem.restapi.network;

import static spark.Spark.*;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.frosthex.timingsystem.restapi.TimingSystemRESTApiPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import me.makkuusen.timing.system.event.EventDatabase;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.SpectatorScoreboard;

/**
 * TimingSystemRESTApi - Provides a basic JSON REST API for the TimingSystem plugin.
 * Copyright (C) 2023 Justin "JustBru00" Brubaker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * @author Justin Brubaker
 *
 */
public class SparkManager {
	
	private static int port;
	private static String pathToPublicHtmlFolder;

	public static void initSpark() {
		// For full list of REST API routes see: https://github.com/JustBru00/TimingSystemRESTApi/wiki/REST-API-Documentation
		
		port(port);
		staticFiles.externalLocation(pathToPublicHtmlFolder);
		
		before("/api/v1/readonly/*", (request, response) -> {
			// Authenticate READ ONLY
			String apiKey = request.queryParams("api_key");
			if (apiKey == null) {
				halt(401, "{\"error\":true,\"errorMessage\":\"Couldn't read api_key. Please provide a valid api_key in your request.\"}");
			}
			
			boolean authenticated = false;
			
			for (String readOnlyKey : TimingSystemRESTApiPlugin.getInstance().getConfig().getStringList("api_keys.read_only")) {
				if (readOnlyKey.equalsIgnoreCase(apiKey)) {
					authenticated = true;
					break;
				}
			}
			
			if (!authenticated) {
				halt(401, "{\"error\":true,\"errorMessage\":\"Unknown api_key. Please provide a valid api_key in your request.\"}");
			}
		});
		
		before("/api/v1/readwrite/*", (request, response) -> {
			// Authenticate READ WRITE
		});
		
		// /api/v1/readonly/events/heats/scoreboards
		get("/api/v1/readonly/events/heats/scoreboards", (request, response) -> {
			
			var heats = EventDatabase.getHeats();
			
			if (heats == null) {
				halt(401, "{\"error\":true,\"errorMessage\":\"Something went wrong EventDatabase.getHeats() is null.\"}");
			}
						
			JsonObject responseObject = new JsonObject();
			
			
			for (Heat heat : heats) {
				JsonObject heatObject = new JsonObject();
				heatObject.addProperty("id", heat.getId());
				
				SpectatorScoreboard scoreboard = heat.getScoreboard();
				if (scoreboard == null) {
					continue;
				}				
				
				List<String> scoreBoardLines = scoreboard.normalScoreboard();
				
				if (scoreBoardLines == null) {
					continue;
				}
				
				JsonArray scoreboardLinesArray = new JsonArray();
				
				for (String scoreLine : scoreBoardLines) {
					scoreboardLinesArray.add(scoreLine);
				}
				
				heatObject.add("scoreboard", scoreboardLinesArray);				
				if (heat.getName() == null) {
					responseObject.add("null-name-" + ThreadLocalRandom.current().nextInt(1, 1000), heatObject);
				} else {
					responseObject.add(heat.getName(), heatObject);
				}				
			}
			
			response.status(200);
			return responseObject.toString();
		});
		
	}
	
	public static void stopSpark() {
		stop();
	}

	public static int getPort() {
		return port;
	}

	public static void setPort(int port) {
		SparkManager.port = port;
	}

	public static String getPathToPublicHtmlFolder() {
		return pathToPublicHtmlFolder;
	}

	public static void setPathToPublicHtmlFolder(String pathToPublicHtmlFolder) {
		SparkManager.pathToPublicHtmlFolder = pathToPublicHtmlFolder;
	}
	
}
