package com.frosthex.timingsystem.restapi.network;

import static spark.Spark.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

import com.frosthex.timingsystem.restapi.TimingSystemRESTApiPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import me.makkuusen.timing.system.TPlayer;
import me.makkuusen.timing.system.api.DriverDetails;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.event.EventDatabase;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.SpectatorScoreboard;
import me.makkuusen.timing.system.timetrial.TimeTrialFinish;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.TrackTag;

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
			// Allow all origins
			response.header("Access-Control-Allow-Origin", "*");
			
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
			
			// TODO RATE LIMIT HERE
		});
		
		before("/api/v1/readwrite/*", (request, response) -> {
			// Allow all origins
			response.header("Access-Control-Allow-Origin", "*");
			
			// Authenticate READ WRITE
		});
		
		// /api/v1/readonly/events/heats/scoreboards
		get("/api/v1/readonly/events/heats/scoreboards", (request, response) -> {			
			var heats = EventDatabase.getHeats();
			
			if (heats == null) {
				halt(401, "{\"error\":true,\"errorMessage\":\"Something went wrong EventDatabase.getHeats() is null.\"}");
			}
						
			JsonArray responseArray = new JsonArray();			
			
			for (Heat heat : heats) {
				JsonObject heatObject = new JsonObject();
				heatObject.addProperty("name", heat.getName());
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
				responseArray.add(heatObject);
			}
			
			response.status(200);
			return responseArray.toString();
		});
		
		// /api/v1/readonly/tracks/
		get("/api/v1/readonly/tracks/", (request, response) -> {			
			var tracks = TimingSystemAPI.getTracks();
			
			if (tracks == null) {
				halt(401, "{\"error\":true,\"errorMessage\":\"Something went wrong TimingSystemAPI.getTracks() is null.\"}");
			}
			
			JsonObject tracksResponseObject = new JsonObject();
			
			tracksResponseObject.addProperty("number", tracks.size());
			
			JsonArray tracksNamesArray = new JsonArray();
			
			for (Track track : tracks) {
				String commandName = track.getCommandName();
				if (commandName == null) {
					continue;
				}
				tracksNamesArray.add(commandName);
			}			
			tracksResponseObject.add("track_command_names", tracksNamesArray);
			
			response.status(200);
			return tracksResponseObject.toString();
		});
		
		// /api/v1/readonly/track/:trackname
		get("/api/v1/readonly/track/:trackname", (request, response) -> {
			String trackInternalName = request.params("trackname");
			
			if (trackInternalName == null) {
				halt(401, "{\"error\":true,\"errorMessage\":\"Something went wrong. The track name provided is null.\"}");
			}
			
			Optional<Track> optionalTrack = TimingSystemAPI.getTrack(trackInternalName);
			if (optionalTrack.isEmpty()) {
				halt(401, "{\"error\":true,\"errorMessage\":\"Something went wrong. Could find a track with that name.\"}");
			}
			
			Track track = optionalTrack.get();
			
			JsonObject responseObject = new JsonObject();
			responseObject.addProperty("command_name", track.getCommandName());
			responseObject.addProperty("display_name", track.getDisplayName());
			responseObject.addProperty("mode", track.getModeAsString());
			responseObject.addProperty("type", track.getTypeAsString());
			responseObject.addProperty("date_created", track.getDateCreated());
			responseObject.addProperty("id", track.getId());
			responseObject.addProperty("total_attempts", track.getTotalAttempts());
			responseObject.addProperty("total_finishes", track.getTotalFinishes());
			responseObject.addProperty("total_time_spent", track.getTotalTimeSpent());
			responseObject.addProperty("weight", track.getWeight());
			responseObject.addProperty("gui_item", track.getGuiItem().toString());
			JsonArray optionsArray = new JsonArray();
			for (char c : track.getOptions()) {
				optionsArray.add(c);
			}
			responseObject.add("options", optionsArray);		
			responseObject.addProperty("owner", track.getOwner().getUniqueId().toString());
			responseObject.add("spawn_location", serializeLocation(track.getSpawnLocation()));
			JsonArray tagsArray = new JsonArray();
			for (TrackTag trackTag : track.getTags()) {
				tagsArray.add(trackTag.getValue());
			}
			responseObject.add("tags", tagsArray);
			JsonArray topListArray = new JsonArray();
			for (TimeTrialFinish finish : track.getTopList()) {
				JsonObject timeTrialFinishObject = new JsonObject();
				timeTrialFinishObject.addProperty("date", finish.getDate());
				timeTrialFinishObject.addProperty("id", finish.getId());
				timeTrialFinishObject.addProperty("time", finish.getTime());
				timeTrialFinishObject.addProperty("track_id", finish.getTrack());
				timeTrialFinishObject.addProperty("player_uuid", finish.getPlayer().toString());
				topListArray.add(timeTrialFinishObject);
			}
			responseObject.add("top_list", topListArray);
		
			response.status(200);
			return responseObject.toString();
		});
		
		// /api/v1/readonly/player/:uuid
		get("/api/v1/readonly/player/:uuid", (request, response) -> {			
			String uuidString = request.params("uuid");
			
			if (uuidString == null) {
				halt(401, "{\"error\":true,\"errorMessage\":\"Something went wrong. The uuid provided is null.\"}");
			}
			
			UUID uuid = UUID.randomUUID();
			
			try {
				uuid = UUID.fromString(uuidString);
			} catch (Exception e) {
				halt(401, "{\"error\":true,\"errorMessage\":\"Something went wrong. The UUID couldn't be parsed.\"}");
			}
			
			TPlayer tPlayer = TimingSystemAPI.getTPlayer(uuid);
			
			// TODO serialize whole tPlayer object
			
			return "";
		});
		
		// /api/v1/readonly/events/heats/scoreboards
		get("/api/v1/readonly/events/runningheats/", (request, response) -> {
			var heats = TimingSystemAPI.getRunningHeats();
			
			if (heats == null) {
				halt(401, "{\"error\":true,\"errorMessage\":\"Something went wrong TimingSystem.getRunningHeats() is null.\"}");
			}
			
			JsonArray arrayObj = new JsonArray();
			
			for (Heat heat : heats) {
				JsonObject heatObj = new JsonObject();
				heatObj.addProperty("name", heat.getName());
				heatObj.addProperty("event_name", heat.getEvent().getDisplayName());
				heatObj.addProperty("id", heat.getId());
				
				JsonArray driverPositionsArray = new JsonArray();
				var driverDetailsList = TimingSystemAPI.getAllDriverDetailsFromHeat(heat);
								
				for (DriverDetails dd : driverDetailsList) {
					JsonObject driverObj = new JsonObject();
					driverObj.addProperty("name", dd.getName());
					driverObj.addProperty("team_color", dd.getTeamColor());
					driverObj.addProperty("uuid", dd.getUuid());
					driverObj.addProperty("gap", dd.getGap());
					driverObj.addProperty("gap_to_leader", dd.getGapFromLeader());
					driverObj.addProperty("laps", dd.getLaps());
					driverObj.addProperty("pits", dd.getPits());
					driverObj.addProperty("position", dd.getPosition());
					driverObj.addProperty("start_position", dd.getStartPosition());
					driverObj.addProperty("is_in_pit", dd.isInpit());
					driverObj.addProperty("is_offline", dd.isOffline());
					
					driverPositionsArray.add(driverObj);
				}
				heatObj.add("driver_details", driverPositionsArray);
				arrayObj.add(heatObj);
			}
			
			response.status(200);
			return arrayObj.toString();
		});
		
		get("/api/v1/readonly/tracks/example/dontuse", (request, response) -> {
			return "";
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
	
	private static JsonObject serializeLocation(Location loc) {
		JsonObject obj = new JsonObject();
		obj.addProperty("x",loc.getX());
		obj.addProperty("y",loc.getY());
		obj.addProperty("z",loc.getZ());
		obj.addProperty("pitch",loc.getPitch());
		obj.addProperty("yaw",loc.getYaw());
		obj.addProperty("world_name",loc.getWorld().getName());
		
		return obj;
	}
	
}
