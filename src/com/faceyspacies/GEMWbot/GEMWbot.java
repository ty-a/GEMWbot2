package com.faceyspacies.GEMWbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import jerklib.Channel;
import jerklib.ConnectionManager;
import jerklib.Profile;
import jerklib.Session;
import jerklib.events.*;
import jerklib.events.IRCEvent.Type;
import jerklib.listeners.IRCEventListener;
 

public class GEMWbot implements IRCEventListener
{
	private ConnectionManager manager;
	
	private String ircNick;
	private String ircServer;
	private ArrayList<String> allowedHosts;
	private List<String> adminHosts;
	private String ircChannel;
	private String nickServUser;
	private String nickServPass;
 
	public GEMWbot()
	{
		loadIRCsettings();

		manager = new ConnectionManager(new Profile(ircNick));
 
		Session session = manager.requestConnection(ircServer);
 
		session.addIRCEventListener(this);
 
	}
 
	private void loadIRCsettings() {
		Properties ircSettings = new Properties();
		InputStream input = null;
		
		try {
			File settingsFileLocation = new File("irc.properties");
			input = new FileInputStream(settingsFileLocation);
			
			ircSettings.load(input);
			
			ircNick = ircSettings.getProperty("ircNick");
			ircServer = ircSettings.getProperty("ircServer");
			allowedHosts = new ArrayList<String>(Arrays.asList(ircSettings.getProperty("allowedHosts").split(",")));
			adminHosts = Arrays.asList(ircSettings.getProperty("adminHosts").split(","));
			ircChannel = ircSettings.getProperty("ircChannel");
			nickServUser = ircSettings.getProperty("nickServUser");
			nickServPass = ircSettings.getProperty("nickServPass");
			
			if(ircNick == null) {
				System.out.println("[ERROR] ircNick is missing from irc.properties; closing");
				System.exit(0);
			}
			
			if(ircServer == null) {
				System.out.println("[ERROR] ircServer is missing from irc.properties; closing");
				System.exit(0);
			}
			
			if(allowedHosts == null) {
				System.out.println("[ERROR] allowedHosts is missing from irc.properties; closing");
				System.exit(0);
			}
			
			if (ircChannel == null) {
				System.out.println("[ERROR] ircChannel is missing from irc.properties; closing");
				System.exit(0);
			}
			
			if (nickServUser == null) {
				System.out.println("[ERROR] nickServUser is missing from irc.properties; closing");
				System.exit(0);
			}
			
			if (nickServPass == null) {
				System.out.println("[ERROR] nickServPass is missing from irc.properties; closing");
				System.exit(0);
			}
			
			if (adminHosts == null) {
				System.out.println("[ERROR] nickServPass is missing from irc.properties; closing");
				System.exit(0);
			}
			
		}
		catch (FileNotFoundException err) {
			System.out.println("[ERROR] Unable to load irc.properties file; closing");
			System.exit(0);
		} catch (IOException e) {
			System.out.println("[ERROR] IO Error loading irc.properties; closing");
			System.exit(0);
		}
		
	}

	public void receiveEvent(IRCEvent e)
	{
		if (e.getType() == Type.CONNECT_COMPLETE)
		{
			loginToNickServ(e.getSession());
 
		}

		else if (e.getType() == Type.CHANNEL_MESSAGE)
		{
			MessageEvent me = (MessageEvent) e;
			System.out.println("<" + me.getNick() + ">"+ ":" + me.getMessage());
			
			if(me.getMessage().charAt(0) == '~') { // we have a command as ~ is our trigger
				commandHandler(me);
			}
			else {
				return; // no command
			}
		}
		else if(e.getType() == Type.DEFAULT){
			System.out.println(e.getType() + " " + e.getRawEventData());
			
			if(e.getRawEventData().substring(0, 4).equalsIgnoreCase("PING")) {
				return;
			}
			
			int eventCode;
			int firstIndex, secondIndex;
			/*  example of raw event data
			 *  server eventCode <varies on code>
			 * :weber.freenode.net 396 TyBot2 wikia/TyBot :is now your hidden host (set by services.)
			 * 396 is auth'd to services on freenode
			 */
			firstIndex = e.getRawEventData().indexOf(" ") + 1;
			secondIndex = e.getRawEventData().indexOf(" ", firstIndex); // .substring automatically -1s from the endIndex
			try {
				eventCode = Integer.parseInt(e.getRawEventData().substring(firstIndex, secondIndex));
			} catch (NumberFormatException err) {
				return; // no need to continue
			}
			if(eventCode == 396) {
				e.getSession().join(ircChannel);
			}
		}
		
		else
		{
			System.out.println(e.getType() + " " + e.getRawEventData());
			
		}
	}
	
	private void commandHandler(MessageEvent me) {
		Session session = manager.getSession(ircServer);
		Channel channel = session.getChannel(ircChannel);
		String command = "";
		String fullCommand = me.getMessage();
		boolean isMod;
		boolean isAdmin;
		
		if(fullCommand.indexOf(" ") != -1) {
			/* index 1 is start of command after trigger
			 */
			command = fullCommand.substring(1, fullCommand.indexOf(" "));
			
		} else {
			command = fullCommand.substring(1);
		}
		
		isAdmin = isAdmin(me.getHostName());
		if(isAdmin) 
			isMod = true;
		else 
			isMod = isMod(me.getHostName());
			
		switch(command.toLowerCase()) {
			case "die":
				if(isAdmin) {
					manager.quit("Requested");
				} else {
					session.sayPrivate(me.getNick(), "You are not allowed to use the ~die command.");
				}
				
				break;
			
			case "nick":
				if(isAdmin) {
					try {
						session.changeNick(fullCommand.split(" ")[1]);
					} catch (IndexOutOfBoundsException err) {
						channel.say(me.getNick() + ": You forgot to specify a nick");
					}
				} else {
					session.sayPrivate(me.getNick(), "You are not allowed to use the ~nick command.");
				}
				break;
				
			case "test":
				channel.say(me.getNick() + ": Hello!");
				break;
				
			case "update":
				if(isMod) {
					channel.say(me.getNick() + ": Starting GE Updates!");
					GrandExchangeUpdater newTask = new GrandExchangeUpdater(this);
					newTask.run();
				} else {
					session.sayPrivate(me.getNick(), "You are not allowed to use the ~update command.");
				}
				break;
				
			case "allow":
				if(isMod) {
					try {
						allowedHosts.add(fullCommand.split(" ")[1]);
						saveSettings();
						
						channel.say(me.getNick() + ": " + fullCommand.split(" ")[1] + " is now allowed to use ~update");
					} catch (IndexOutOfBoundsException err) {
						channel.say(me.getNick() + ": You forgot to specify a host");
					}
				} else {
					session.sayPrivate(me.getNick(), "You are not allowed to use the ~allow command.");
				}
				break;
				
			default:
				System.out.println("[INFO] Unknown command " + command + " used by " + me.getNick() + "!");
				
		}
	}
	
	private void saveSettings() {

		
		try {
			Properties ircSettings = new Properties();
			File settingsFileLocation = new File("irc.properties");
			OutputStream output = new FileOutputStream(settingsFileLocation);
			
			ircSettings.setProperty("ircNick", ircNick);
			ircSettings.setProperty("ircServer", ircServer);
			String hosts = "";
			
			for(int i = 0; i < allowedHosts.size(); i++) {
				hosts+= allowedHosts.get(i) + ",";
			}
			
			hosts = hosts.substring(0, hosts.length() -1);
			
			ircSettings.setProperty("allowedHosts", hosts);
			
			hosts = "";
			for(int i = 0; i < adminHosts.size(); i++) {
				hosts+= adminHosts.get(i) + ",";
			}
			hosts = hosts.substring(0, hosts.length() -1);
			
			ircSettings.setProperty("adminHosts", hosts);
			ircSettings.setProperty("ircChannel", ircChannel);
			ircSettings.setProperty("nickServUser", nickServUser);
			ircSettings.setProperty("nickServPass", nickServPass);
			
			ircSettings.store(output, "GEMWbot's IRC Settings");
			
		}
		catch (FileNotFoundException err) {
			System.out.println("[ERROR] Unable to load irc.properties file; closing");
			System.exit(0);
		} catch (IOException e) {
			System.out.println("[ERROR] IO Error loading irc.properties; closing");
			System.exit(0);
		}
	}
	
	private void loginToNickServ(Session session) {
		session.sayPrivate("NickServ", "IDENTIFY " + nickServUser + " " + nickServPass);
	}
	
	private boolean isMod(String host) {
		return allowedHosts.contains(host);
	}
	
	private boolean isAdmin(String host) {
		return adminHosts.contains(host);
	}
 
	public static void main(String[] args)
	{
		new GEMWbot();
	}
	
	protected void sendMessage(String channelName, String message) {
		Session session = manager.getSession(ircServer);
		Channel channel = session.getChannel(channelName);
		
		channel.say(message);
	}
	
	public String getChannel() {
		return ircChannel;
	}
}