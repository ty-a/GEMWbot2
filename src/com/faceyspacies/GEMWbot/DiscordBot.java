package com.faceyspacies.GEMWbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.EventSubscriber;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.DiscordDisconnectedEvent;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.HTTP429Exception;
import sx.blah.discord.util.MissingPermissionsException;

public class DiscordBot {
	private static IDiscordClient client;
	private boolean isReady = false;
	private String channelID;
	private String token;
	private GEMWbot main;
	private static int RESPONSELIMIT = 3;
	
	DiscordBot(GEMWbot main) throws DiscordException {
		this.main = main;
		
		if(!loadSettings()) {
			main.getTellBotInstance().addTell("tybot", "wikia/vstf/TyA", "invalid settings in discord.properties", null);
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
	public void onDisconnect(DiscordDisconnectedEvent event) {
		isReady = false;
		main.createDiscordBotInstance();
	}
	
	@EventSubscriber
	public void onMessageReceivedEvent(MessageReceivedEvent event) {
	    if(!isReady)
	    	return;
	    
	    boolean isAdmin = false;
	    
	    IMessage message = event.getMessage();
	    try {
		    if(message.getContent().startsWith("~linklimit ")) {
		    	List<IRole> roles = message.getAuthor().getRolesForGuild(message.getGuild());
		    	for(IRole role: roles) {
		    		if(role.getName().equals("badmins"))
		    			isAdmin = true;
		    	}
		    	if(isAdmin) {
		    		try {
			    		RESPONSELIMIT = Integer.parseInt(message.getContent().split(" ")[1]);
			    		message.getChannel().sendMessage(message.getAuthor().getName() + ": updated link limit!");
			    	} catch (NumberFormatException | IndexOutOfBoundsException e) {
						message.getChannel().sendMessage(message.getAuthor().getName() + ": you have to provide a number!");

			    	}
		    	} else {
		    		message.getChannel().sendMessage(message.getAuthor().getName() + ": you're not allowed to do that!");
		    	}
		    } else if (message.getContent().equalsIgnoreCase("~status")) {
		    	message.getChannel()).sendMessage(
		    			main.getDiscordStatusText(message.getAuthor().getName())
		    		);
		    } else if (message.getContent().equalsIgnoreCase("~addme")) {
		    	List<IRole> roles = message.getAuthor().getRolesForGuild(message.getGuild());
		    	if(roles != null) {
			    	for(IRole role: roles) {
			    		if(role.getName().equals("wiki squids")) {
			    			message.getChannel().sendMessage(message.getAuthor().getName() + ": You are already subscribed to wiki notifications");
			    			return;
			    		}
			    	}
		    	}
		    	
		    	List<IRole> guildRoles = message.getGuild().getRoles();
		    	String roleID = null;
		    	for(IRole role: guildRoles) {
		    		if(role.getName().equalsIgnoreCase("wiki squids"))
		    			roleID = role.getID();
		    	}
		    	if(roleID == null) {
		    		System.out.println("wiki squids is not defined");
		    		return;
		    	}
		    	IGuild currGuild = message.getGuild();
		    	roles.add(currGuild.getRoleByID(roleID));
		    	currGuild.editUserRoles(message.getAuthor(), roles.toArray(new IRole[roles.size()]));
		    	message.getChannel().sendMessage(message.getAuthor().getName() + ": You have subscribed to wiki notifications!");
		    	
		    } else if (message.getContent().equalsIgnoreCase("~removeme")) {
		    	List<IRole> roles = message.getAuthor().getRolesForGuild(message.getGuild());
		    	boolean isSquid = false;
		    	if(roles != null) {
			    	for(IRole role: roles) {
			    		if(role.getName().equals("wiki squids")) {
			    			isSquid = true;
			    		}
			    	}
		    	}
		    	
		    	if(!isSquid) {
		    		message.getChannel().sendMessage(message.getAuthor().getName() + ": You aren't subscribed to wiki notifications");
    				return;
		    	}
		    	
		    	List<IRole> guildRoles = message.getGuild().getRoles();
		    	String roleID = null;
		    	for(IRole role: guildRoles) {
		    		if(role.getName().equalsIgnoreCase("wiki squids"))
		    			roleID = role.getID();
		    	}
		    	if(roleID == null) {
		    		System.out.println("wiki squids is not defined");
		    		return;
		    	}
		    	
		    	IGuild currGuild = message.getGuild();
		    	roles.remove(currGuild.getRoleByID(roleID));
		    	currGuild.editUserRoles(message.getAuthor(), roles.toArray(new IRole[roles.size()]));
		    	message.getChannel().sendMessage(message.getAuthor().getName() + ": You have unsubscribed to wiki notifications!");
		    } else {
		    	String links = wikiLinks(message.getContent());
		    	if(links != null) {
					message.getChannel().sendMessage(links);
		    	}
		    }
	    } catch (MissingPermissionsException | HTTP429Exception | DiscordException e) {
	    		main.getTellBotInstance().addTell("tybot", "wikia/vstf/TyA", "DiscordBot hit " + e.getClass(), null);
	    }
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
			main.getTellBotInstance().addTell("tybot", "wikia/vstf/TyA", "DiscordBot hit HTTP429Exception - too many requests", null);
		} catch (DiscordException e) {
			main.getTellBotInstance().addTell("tybot", "wikia/vstf/TyA", "DiscordBot hit DiscordException - miscellanious error", null);
		} catch (MissingPermissionsException e) {
			main.getTellBotInstance().addTell("tybot", "wikia/vstf/TyA", "DiscordBot hit MissingPermissionsException - for obv reasons", null);
		}
	}
	// begin wikilinks by The Mol Man
	final static String[][] ENCODING = {
			{"%", "%25"},
			{"&", "%26"},
			{"'", "%27"},
			{"\\+", "%2B"},
			{"\\?", "%3F"},
			{" ", "_"}
	};
	
	public static String wikiLinks(String msg) {
	
		String[] links = new String[RESPONSELIMIT];
	
		String responses = "[Wiki links] : ";
		String[] indexParams = { "action", "redirect", "useskin", "section", "offset", "oldid", "direction" };
		String iparams = String.join("|", indexParams);
		
		Pattern linkPattern = Pattern.compile("\\[\\[([^\\]\\|]+)(?:|[^\\]]+)?\\]\\]");
		Pattern pagePattern = Pattern.compile("^(.+?)(\\?(?:" + iparams + ").+|)$");
		Matcher pageMatch = linkPattern.matcher(msg);
	
		int listSize = 0;
		for (int i = 0; i < RESPONSELIMIT && pageMatch.find(); i++) {
			links[i] = pageMatch.group(1);
			listSize++;
		}
		
		if(listSize <= 0)
			return null;
		
		Matcher pageMatcher;
		for (int i = 0; i < listSize; i++) {
			String s = links[i];
			
			if (s == null)
				break;
			
			pageMatcher = pagePattern.matcher(s);
			pageMatcher.find();
			String page = pageMatcher.group(1);
			String params = pageMatcher.group(2);
			
			if (params.length() == 0 && s.indexOf("#") == -1)
				params = "?action=view";
			
			// lazy encoding
			for (int j = 0; j < ENCODING.length; j++) {
				page = page.replaceAll(ENCODING[j][0],ENCODING[j][1]);
			}
			
			responses += "\nhttp://rs.wikia.com/"+page+params;
		}
		return responses;
	}
	// END wiki links by The Mol Man
	
	public void quit() {
		try {
			client.logout();
		} catch (HTTP429Exception | DiscordException e) {
			System.out.println("Failed to log out");
		}
		
	}
}
