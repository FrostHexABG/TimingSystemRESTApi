package com.frosthex.timingsystem.restapi.network;

import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.port;
import static spark.Spark.staticFiles;
import static spark.Spark.stop;

import java.util.Optional;
import java.util.UUID;

import me.makkuusen.timing.system.round.RoundType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import com.frosthex.timingsystem.restapi.TimingSystemRESTApiPlugin;
import com.frosthex.timingsystem.restapi.utils.Messager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import me.makkuusen.timing.system.api.DriverDetails;
import me.makkuusen.timing.system.api.EventResultsAPI;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.api.event.DriverResult;
import me.makkuusen.timing.system.api.event.EventResult;
import me.makkuusen.timing.system.api.event.HeatResult;
import me.makkuusen.timing.system.api.event.LapResult;
import me.makkuusen.timing.system.api.event.RoundResult;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.timetrial.TimeTrialFinish;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.options.TrackOption;
import me.makkuusen.timing.system.track.tags.TrackTag;

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
		
		before("/api/*/readonly/*", (request, response) -> {
			// Set MIME type for responses #16
			response.type("application/json");
			
			// Allow all origins
			response.header("Access-Control-Allow-Origin", "*");
			
			// Authenticate READ ONLY
			String apiKey = request.queryParams("api_key");
			if (apiKey == null) {
				halt(401, "{\"error\":true,\"error_message\":\"Couldn't read api_key. Please provide a valid api_key in your request.\"}");
			}
			
			boolean authenticated = false;
			
			for (String readOnlyKey : TimingSystemRESTApiPlugin.getInstance().getConfig().getStringList("api_keys.read_only")) {
				if (readOnlyKey.equalsIgnoreCase(apiKey)) {
					authenticated = true;
					break;
				}
			}
			
			if (!authenticated) {
				halt(401, "{\"error\":true,\"error_message\":\"Unknown api_key. Please provide a valid api_key in your request.\"}");
			}
			
			// TODO RATE LIMIT HERE
		});
		
		before("/api/*/readwrite/*", (request, response) -> {
			// Allow all origins
			response.header("Access-Control-Allow-Origin", "*");
			
			// Authenticate READ WRITE
		});
		
		// /api/v1/readonly/tracks
		get("/api/v1/readonly/tracks", (request, response) -> {			
			var tracks = TimingSystemAPI.getTracks();
			
			if (tracks == null) {
				halt(401, "{\"error\":true,\"error_message\":\"Something went wrong. TimingSystemAPI.getTracks() is null.\"}");
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
		
		// /api/v3/readonly/tracks
		get("/api/v3/readonly/tracks", (request, response) -> {			
			var tracks = TimingSystemAPI.getTracks();
			
			if (tracks == null) {
				halt(401, "{\"error\":true,\"error_message\":\"Something went wrong. TimingSystemAPI.getTracks() is null.\"}");
			}
			
			JsonObject tracksResponseObject = new JsonObject();
			
			tracksResponseObject.addProperty("number", tracks.size());
			
			JsonArray tracksListObject = new JsonArray();
			
			for (Track track : tracks) {
				if (track.getCommandName() == null) {
					continue;
				}
				JsonObject trackObj = new JsonObject();
				trackObj.addProperty("command_name", track.getCommandName());
				trackObj.addProperty("display_name", track.getDisplayName());
				trackObj.addProperty("type", track.getTypeAsString());
				trackObj.addProperty("open", track.isOpen());
				trackObj.addProperty("date_created", track.getDateCreated());
				trackObj.addProperty("id", track.getId());
				trackObj.addProperty("total_attempts", track.getTimeTrials().getTotalAttempts());
				trackObj.addProperty("total_finishes", track.getTimeTrials().getTotalFinishes());
				trackObj.addProperty("total_time_spent", track.getTotalTimeSpent());
				trackObj.addProperty("weight", track.getWeight());
				ItemStack trackItem = track.getItem();
				if (trackItem == null) {
					Messager.msgConsole("&c[WARN] Track " + track.getCommandName() + " has a TrackItem that is null.");
					trackObj.addProperty("gui_item", "null");
				} else {
					trackObj.addProperty("gui_item", track.getItem().toString());
				}
				
				JsonArray optionsArray = new JsonArray();
				for (TrackOption option : track.getTrackOptions().getTrackOptions()) {
					optionsArray.add(option.toString());
				}
				trackObj.add("options", optionsArray);		
				trackObj.addProperty("owner", track.getOwner().getUniqueId().toString());
				trackObj.add("spawn_location", serializeLocation(track.getSpawnLocation()));
				JsonArray tagsArray = new JsonArray();
				for (TrackTag trackTag : track.getTrackTags().get()) {
					tagsArray.add(trackTag.getValue());
				}
				trackObj.add("tags", tagsArray);
				
				tracksListObject.add(trackObj);
			}
			
			tracksResponseObject.add("tracks", tracksListObject);
			
			response.status(200);
			return tracksResponseObject.toString();
		});		
		
		// /api/v2/readonly/tracks/:trackname
		get("/api/v2/readonly/tracks/:trackname", (request, response) -> {
			String trackInternalName = request.params("trackname");
			
			if (trackInternalName == null) {
				halt(401, "{\"error\":true,\"error_message\":\"Something went wrong. The track name provided is null.\"}");
			}
			
			Optional<Track> optionalTrack = TimingSystemAPI.getTrack(trackInternalName);
			if (optionalTrack.isEmpty()) {
				halt(401, "{\"error\":true,\"error_message\":\"Something went wrong. Could find a track with that name.\"}");
			}
			
			Track track = optionalTrack.get();
			
			JsonObject responseObject = new JsonObject();
			responseObject.addProperty("command_name", track.getCommandName());
			responseObject.addProperty("display_name", track.getDisplayName());
			responseObject.addProperty("type", track.getTypeAsString());
			responseObject.addProperty("open", track.isOpen());
			responseObject.addProperty("date_created", track.getDateCreated());
			responseObject.addProperty("id", track.getId());
			responseObject.addProperty("total_attempts", track.getTimeTrials().getTotalAttempts());
			responseObject.addProperty("total_finishes", track.getTimeTrials().getTotalFinishes());
			responseObject.addProperty("total_time_spent", track.getTotalTimeSpent());
			responseObject.addProperty("weight", track.getWeight());
			ItemStack trackItem = track.getItem();
			if (trackItem == null) {
				Messager.msgConsole("&c[WARN] Track " + track.getCommandName() + " has a TrackItem that is null.");
				responseObject.addProperty("gui_item", "null");
			} else {
				responseObject.addProperty("gui_item", track.getItem().toString());
			}
			JsonArray optionsArray = new JsonArray();
			for (TrackOption option : track.getTrackOptions().getTrackOptions()) {
				optionsArray.add(option.toString());
			}
			responseObject.add("options", optionsArray);		
			responseObject.addProperty("owner", track.getOwner().getUniqueId().toString());
			responseObject.add("spawn_location", serializeLocation(track.getSpawnLocation()));
			JsonArray tagsArray = new JsonArray();
			for (TrackTag trackTag : track.getTrackTags().get()) {
				tagsArray.add(trackTag.getValue());
			}
			responseObject.add("tags", tagsArray);
			JsonArray topListArray = new JsonArray();
			for (TimeTrialFinish finish : track.getTimeTrials().getTopList()) {
				JsonObject timeTrialFinishObject = new JsonObject();
				timeTrialFinishObject.addProperty("date", finish.getDate());
				timeTrialFinishObject.addProperty("id", finish.getId());
				timeTrialFinishObject.addProperty("time", finish.getTime());
				timeTrialFinishObject.addProperty("player_uuid", finish.getPlayer().getUniqueId().toString());
				topListArray.add(timeTrialFinishObject);
			}
			responseObject.add("top_list", topListArray);
		
			response.status(200);
			return responseObject.toString();
		});
		
		// /api/v2/readonly/tracks/:trackname/withusernames
		get("/api/v2/readonly/tracks/:trackname/withusernames", (request, response) -> {
			String trackInternalName = request.params("trackname");
			
			if (trackInternalName == null) {
				halt(401, "{\"error\":true,\"error_message\":\"Something went wrong. The track name provided is null.\"}");
			}
			
			Optional<Track> optionalTrack = TimingSystemAPI.getTrack(trackInternalName);
			if (optionalTrack.isEmpty()) {
				halt(401, "{\"error\":true,\"error_message\":\"Something went wrong. Could find a track with that name.\"}");
			}
			
			Track track = optionalTrack.get();
			
			JsonObject responseObject = new JsonObject();
			responseObject.addProperty("command_name", track.getCommandName());
			responseObject.addProperty("display_name", track.getDisplayName());
			responseObject.addProperty("type", track.getTypeAsString());
			responseObject.addProperty("open", track.isOpen());
			responseObject.addProperty("date_created", track.getDateCreated());
			responseObject.addProperty("id", track.getId());
			responseObject.addProperty("total_attempts", track.getTimeTrials().getTotalAttempts());
			responseObject.addProperty("total_finishes", track.getTimeTrials().getTotalFinishes());
			responseObject.addProperty("total_time_spent", track.getTotalTimeSpent());
			responseObject.addProperty("weight", track.getWeight());
			ItemStack trackItem = track.getItem();
			if (trackItem == null) {
				Messager.msgConsole("&c[WARN] Track " + track.getCommandName() + " has a TrackItem that is null.");
				responseObject.addProperty("gui_item", "null");
			} else {
				responseObject.addProperty("gui_item", track.getItem().toString());
			}
			JsonArray optionsArray = new JsonArray();
			for (TrackOption option : track.getTrackOptions().getTrackOptions()) {
				optionsArray.add(option.toString());
			}
			responseObject.add("options", optionsArray);		
			responseObject.addProperty("owner", track.getOwner().getUniqueId().toString());
			responseObject.add("spawn_location", serializeLocation(track.getSpawnLocation()));
			JsonArray tagsArray = new JsonArray();
			for (TrackTag trackTag : track.getTrackTags().get()) {
				tagsArray.add(trackTag.getValue());
			}
			responseObject.add("tags", tagsArray);
			JsonArray topListArray = new JsonArray();
			for (TimeTrialFinish finish : track.getTimeTrials().getTopList()) {
				JsonObject timeTrialFinishObject = new JsonObject();
				timeTrialFinishObject.addProperty("date", finish.getDate());
				timeTrialFinishObject.addProperty("id", finish.getId());
				timeTrialFinishObject.addProperty("time", finish.getTime());
				timeTrialFinishObject.addProperty("player_uuid", finish.getPlayer().getUniqueId().toString());
				timeTrialFinishObject.addProperty("username", finish.getPlayer().getName());
				topListArray.add(timeTrialFinishObject);
			}
			responseObject.add("top_list", topListArray);		
			
			response.status(200);
			return responseObject.toString();
		});
		
		// /api/v1/readonly/players/:uuid OR /api/v1/readonly/players/:username
		get("/api/v1/readonly/players/:uuidorusername", (request, response) -> {			
			String uuidOrUsernameString = request.params("uuidorusername");
			
			if (uuidOrUsernameString == null) {
				halt(401, "{\"error\":true,\"error_message\":\"Something went wrong. UUID or username argument was null\"}");
			}			
			
			UUID uuid = UUID.randomUUID();
			TPlayer tPlayer = null;
			
			try {
				uuid = UUID.fromString(uuidOrUsernameString);
			} catch (Exception e) {
				OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(uuidOrUsernameString);
				
				if (offline == null) {
					halt(401, "{\"error\":true,\"error_message\":\"Something went wrong. UUID or username couldn't be parsed from input.\"}");
				} else {
					uuid = offline.getUniqueId();
				}				
			}
			
			tPlayer = TimingSystemAPI.getTPlayer(uuid);
			
			if (tPlayer == null) {
				halt(401, "{\"error\":true,\"error_message\":\"Something went wrong. That player couldn't be found.\"}");
			}
			
			JsonObject responseObject = new JsonObject();
			responseObject.addProperty("uuid", preventNull(tPlayer.getUniqueId().toString()));
			responseObject.addProperty("name", preventNull(tPlayer.getName()));
			responseObject.addProperty("display_name", preventNull(tPlayer.getNameDisplay()));
			responseObject.addProperty("color_code", preventNull(tPlayer.getSettings().getColor()));
			responseObject.addProperty("hex_color", preventNull(tPlayer.getSettings().getHexColor()));
			responseObject.addProperty("boat_type", preventNull(tPlayer.getSettings().getBoat().toString()));
			responseObject.addProperty("boat_material", preventNull(tPlayer.getSettings().getBoatMaterial().toString()));
			responseObject.addProperty("bukkit_color", preventNull(tPlayer.getSettings().getBukkitColor().toString()));
			
			response.status(200);
			return responseObject.toString();
		});
		
		// /api/v1/readonly/events/running-heats
		get("/api/v1/readonly/events/running-heats", (request, response) -> {
			var heats = TimingSystemAPI.getRunningHeats();
			
			if (heats == null) {
				halt(401, "{\"error\":true,\"error_message\":\"Something went wrong TimingSystem.getRunningHeats() is null.\"}");
			}
			
			JsonArray arrayObj = new JsonArray();
			
			for (Heat heat : heats) {
				JsonObject heatObj = new JsonObject();
				heatObj.addProperty("name", heat.getName());
				heatObj.addProperty("event_name", heat.getEvent().getDisplayName());
				heatObj.addProperty("id", heat.getId());
				heatObj.addProperty("qualifying", (heat.getRound().getType() == RoundType.QUALIFICATION)); // Issue #19
				
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
					driverObj.addProperty("best_lap", dd.getBestLap());
					
					driverPositionsArray.add(driverObj);
				}
				heatObj.add("driver_details", driverPositionsArray);
				arrayObj.add(heatObj);
			}
			
			response.status(200);
			return arrayObj.toString();
		});
		
		// /api/v1/readonly/events/results/:eventname
		get("/api/v1/readonly/events/results/:eventname", (request, response) -> {
			String eventName = request.params("eventname");
			
			if (eventName == null) {
				halt(401, "{\"error\":true,\"error_message\":\"Something went wrong. The event name provided is null.\"}");
			}
			
			EventResult eventResult = EventResultsAPI.getEventResult(eventName);
			if (eventResult == null) {
				halt(401, "{\"error\":true,\"error_message\":\"Something went wrong. Could find an event with that name.\"}");
			}

			JsonObject eventResultObject = serializeEventResult(eventResult);
			
			response.status(200);
			return eventResultObject.toString();
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
		if (loc == null) { // ISSUE #15
			obj.addProperty("error", true);
			obj.addProperty("error_message", "location received internally was null.");
		} else {
			obj.addProperty("x",loc.getX());
			obj.addProperty("y",loc.getY());
			obj.addProperty("z",loc.getZ());
			obj.addProperty("pitch",loc.getPitch());
			obj.addProperty("yaw",loc.getYaw());
			obj.addProperty("world_name", loc.isWorldLoaded() ? loc.getWorld().getName() : "null");
		}	
		
		return obj;
	}
	
	private static JsonObject serializeEventResult(EventResult eventResult) {
		JsonObject eventResultObject = new JsonObject();
		eventResultObject.addProperty("name", eventResult.getName());
		eventResultObject.addProperty("date", eventResult.getDate());
		eventResultObject.addProperty("track_name", eventResult.getTrackName());
		eventResultObject.addProperty("participant_count", eventResult.getParticipants());

		JsonArray roundResultArray = new JsonArray();
		for (RoundResult roundResult : eventResult.getRounds()) {
			roundResultArray.add(serializeRoundResult(roundResult));
		}
		eventResultObject.add("rounds", roundResultArray);
		return eventResultObject;
	}
	
	private static JsonObject serializeRoundResult(RoundResult roundResult) {
		JsonObject roundResultObject = new JsonObject();
		roundResultObject.addProperty("name", roundResult.getName());
		roundResultObject.addProperty("type", roundResult.getType());

		JsonArray heatResultArray = new JsonArray();
		for (HeatResult heatResult : roundResult.getHeatResults()) {
			heatResultArray.add(serializeHeatResult(heatResult));
		}
		roundResultObject.add("heats", heatResultArray);
		return roundResultObject;
	}

	private static JsonObject serializeHeatResult(HeatResult heatResult) {
		JsonObject heatResultObject = new JsonObject();
		heatResultObject.addProperty("name", heatResult.getName());
		if (heatResult.getTotalLaps() != null) { // null for qualification heats
			heatResultObject.addProperty("total_laps", heatResult.getTotalLaps());
		}

		JsonArray driverResultArray = new JsonArray();
		for (DriverResult driverResult : heatResult.getDriverResultList()) {
			driverResultArray.add(serializeDriverResult(driverResult));
		}
		heatResultObject.add("driver_results", driverResultArray);
		return heatResultObject;
	}
	
	private static JsonObject serializeDriverResult(DriverResult driverResult) {
		JsonObject driverResultObject = new JsonObject();
		driverResultObject.addProperty("position", driverResult.getPosition());
		driverResultObject.addProperty("start_position", driverResult.getStartPosition());
		driverResultObject.addProperty("name", driverResult.getName());
		driverResultObject.addProperty("uuid", driverResult.getUuid());
		try {
			driverResultObject.addProperty("finish_time", driverResult.getFinishTimeInMs());
		} catch (NoSuchMethodError e) {
			// for TimingSystem versions before commit 3d876f3, which added finishTimeInMs to the driverResult api
		}

		JsonArray lapResultArray = new JsonArray();
		for (LapResult lapResult : driverResult.getLaps()) {
			lapResultArray.add(serializeLapResult(lapResult));
		}
		driverResultObject.add("laps", lapResultArray);
		return driverResultObject;
	}
	
	private static JsonObject serializeLapResult(LapResult lapResult) {
		JsonObject lapResultObject = new JsonObject();
		lapResultObject.addProperty("time", lapResult.getTimeInMs());
		lapResultObject.addProperty("pitstop", lapResult.isPitstop());
		lapResultObject.addProperty("fastest", lapResult.isFastest());
		return lapResultObject;
	}
	
	private static String preventNull(String possibleNull) {
		if (possibleNull == null) {
			return "null";
		}
		return possibleNull;
	}
	
}
