package com.faceyspacies.GEMWbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.EventSubscriber;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.HTTP429Exception;
import sx.blah.discord.util.MissingPermissionsException;

public class DiscordBot {
	private static IDiscordClient client;
	private boolean isReady = false;
	private TellBot tellbot;
	private String token;
	
	DiscordBot(TellBot tellbot) throws DiscordException {
		this.tellbot = tellbot;
		
		if(!loadSettings()) {
			tellbot.addTell("tybot", "wikia/vstf/TyA", "invalid settings in discord.properties", null);
		}
		
		client = new ClientBuilder().withToken(token).login();
		client.getDispatcher().registerListener(this);
	}
	
	private boolean loadSettings() {
		Properties settings = new Properties();
		InputStream input = null;
		
		try {
			File settingsFileLocation = new File("discord.properties");
			input = new FileInputStream(settingsFileLocation);
			
			settings.load(input);
			
			token = settings.getProperty("token");
			channelID = settings.getProperty("channelID");
			
			if(token == null) {
				System.out.println("[ERROR] token is missing from discord.properties; closing");
				return false;
			}
			
			if(channelID == null) {
				System.out.println("[ERROR] channelID is missing from discord.properties; closing");
				return false;
			}
			
		}
		catch (FileNotFoundException err) {
			System.out.println("[ERROR] Unable to load discord.properties file; closing");
			return false;
		} catch (IOException e) {
			System.out.println("[ERROR] IO Error loading discord.properties; closing");
			return false;
		}
		
		return true;
		
	}
	
	@EventSubscriber
	public void onReady(ReadyEvent event) {
		isReady = true;
		client.updatePresence(false, Optional.of("Spamming edits to be #1 bot on Wikia"));
	}
	
	protected void sendMessage(String message) {
		try {
			if(isReady)
				client.getChannelByID(channelID).sendMessage(message);
			
		} catch (HTTP429Exception e) {
			tellbot.addTell("tybot", "wikia/vstf/TyA", "DiscordBot hit HTTP429Exception - too many requests", null);
		} catch (DiscordException e) {
			tellbot.addTell("tybot", "wikia/vstf/TyA", "DiscordBot hit DiscordException - miscellanious error", null);
		} catch (MissingPermissionsException e) {
			tellbot.addTell("tybot", "wikia/vstf/TyA", "DiscordBot hit MissingPermissionsException - for obv reasons", null);
		}
	}

	public void quit() {
		try {
			client.logout();
		} catch (HTTP429Exception | DiscordException e) {
			System.out.println("Failed to log out");
		}
		
	}
}
