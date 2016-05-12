package com.faceyspacies.GEMWbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import sx.blah.discord.util.DiscordException;
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
	protected String ircChannel;
	private String nickServUser;
	private String nickServPass;
	private String feedNetwork;
	private int feedPort;
	protected boolean enableTieBot;
	protected boolean enableTieBotNewUsers;
	protected boolean enableTellBot;
	protected boolean enableDiscordBot;
	private Session session;
	private Session rcSession;
	private TieBot tieBotInstance;
	private TellBot tellBotInstance;
	private UpdateChecker checker;
	private DiscordBot discordBotInstance;
	
	private GrandExchangeUpdater updateTask;
 
	public GEMWbot()
	{
		loadIRCsettings();
		updateTask = null;
		tieBotInstance = null;
		rcSession = null;

		manager = new ConnectionManager(new Profile(ircNick));
 
		session = manager.requestConnection(ircServer); 
		session.addIRCEventListener(this);
		
		if(enableTellBot) {
			addTellBotCommands();
		}
		
		if(enableTieBot) {
			createTieBotInstance();
		}
		
		if(enableDiscordBot) {
			createDiscordBotInstance();
		}
		
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
			feedNetwork = ircSettings.getProperty("feedNetwork");
			
			try {
				feedPort = Integer.parseInt(ircSettings.getProperty("feedPort"));
			} catch (NumberFormatException e) {
				System.out.println("[ERROR] feedPort is missing from irc.properties or invalid; closing");
				System.exit(0);
			}
			
			String temp = ircSettings.getProperty("enableTieBot");
			if(temp == null) 
				enableTieBot = false;
			else
				enableTieBot = temp.equals("true") ? true : false;
			
			temp = ircSettings.getProperty("enableTieBotNewUsers");
			if(temp == null) 
				enableTieBotNewUsers = false;
			else
				enableTieBotNewUsers = temp.equals("true") ? true : false;
			
			temp = ircSettings.getProperty("enableTellBot");
			if(temp == null) 
				enableTellBot = false;
			else
				enableTellBot = temp.equals("true") ? true : false;
			
			temp = ircSettings.getProperty("enableDiscordBot");
			if(temp == null) 
				enableDiscordBot = false;
			else
				enableDiscordBot = temp.equals("true") ? true : false;
			
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
			
			if(feedNetwork == null) {
				System.out.println("[ERROR] feedNetwork is missing from irc.properties; closing");
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
			if(me.getMessage().contains("The Grand Exchange has been updated. RuneScript last detected an update") && 
					me.getNick().contains("RuneScript")) {

				Channel channel = e.getSession().getChannel(ircChannel);
				channel.say("Starting GE Updates!");
				try {
					if(updateTask == null) {
						updateTask = new GrandExchangeUpdater(this);
						Thread thread = new Thread(updateTask);
						thread.start();
					} else {
						channel.say("HALP! RUNESCRIPT DETECTED AN UPDATE WHILE I WAS ALREADY UPDATING; FREAKING OUT MAN");
					}
				} catch (Exception err) {
					channel.say("Failed to start GE Updater.");
				}
			}
			
			if(me.getMessage().charAt(0) == '~') { // we have a command as ~ is our trigger
				commandHandler(me);
			}
			else {
				return; // no command
			}
		}
		else if(e.getType() == Type.DEFAULT){
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
				if(enableTieBotNewUsers) {
					e.getSession().join("#cvn-wikia-newusers");
				}
			}
		}
	}
	
	private void commandHandler(MessageEvent me) {
		Session session = manager.getSession(ircServer);
		Channel channel = me.getChannel();
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
			case "adie":
			case "die":
				if(isAdmin) {
					manager.quit("Requested");
					
					if(updateTask != null) {
						updateTask.stopRunning();
					}
					
					if(discordBotInstance != null) {
						discordBotInstance.quit();
					}
					
					System.exit(0);
					
					
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
				channel.say(me.getNick() + ": Hai <3!");
				break;
				
			case "log":
				channel.say(me.getNick() + ": http://runescape.wikia.com/wiki/User:TyBot/log");
				break;
				
			case "update":
				if(updateTask != null) {
					channel.say(me.getNick() + ": GE Updater is already running!");
					break;
				}
				if(isMod) {
					channel.say(me.getNick() + ": Starting GE Updates!");
					try {
						updateTask = new GrandExchangeUpdater(this);
						Thread thread = new Thread(updateTask);
						thread.start();
					} catch (Exception err) {
						channel.say("Failed to start GE Updater.");
					}
				} else {
					session.sayPrivate(me.getNick(), "You are not allowed to use the ~update command.");
				}
				break;
				
			case "allow":
				if(isMod) {
					try {
						if(allowedHosts.contains(fullCommand.split(" ")[1])) {
							channel.say(me.getNick() + ": " + fullCommand.split(" ")[1] + " is already allowed to use ~update");
							return;
						}
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
				
			case "disallow":
				if(isMod) {
					try {
						if(!allowedHosts.contains(fullCommand.split(" ")[1])) {
							channel.say(me.getNick() + ": " + fullCommand.split(" ")[1] + " isn't allowed to use ~update");
							return;
						}
						allowedHosts.remove(fullCommand.split(" ")[1]);
						saveSettings();
						
						channel.say(me.getNick() + ": " + fullCommand.split(" ")[1] + " is no longer allowed to use ~update");
					} catch (IndexOutOfBoundsException err) {
						channel.say(me.getNick() + ": You forgot to specify a host");
					}
				} else {
					session.sayPrivate(me.getNick(), "You are not allowed to use the ~disallow command.");
				}
				break;
				
			case "tiebot":
				if(isMod) {
					try {
						String mode = fullCommand.split(" ")[1].toLowerCase();
						boolean on;
						if(mode.equalsIgnoreCase("on")) {
							on = true;
							enableTieBot = true;
						} else if(mode.equalsIgnoreCase("off")) {
							on = false;
							enableTieBot = false;
						} else {
							channel.say(me.getNick() + ": Invalid syntax. Use ~tiebot on/off");
							return;
						}
						
						if(on) {
							// if we have an instance and we already have it set to read wiki discussions
							if( tieBotInstance != null && tieBotInstance.getWikiDiscussionsFeed() ){
								channel.say(me.getNick() + ": TieBot is already running!");
								return;
							// if we have an instance, but the wiki discussions isn't running start it
							} else if(tieBotInstance != null) {
								channel.say(me.getNick() + ": Starting TieBot!");
								tieBotInstance.setWikiDiscussionsFeed(true);
								return;
							}
							
							// no instance
							createTieBotInstance();
							channel.say(me.getNick() + ": Starting TieBot!");
						} else { // stop running
							if(tieBotInstance == null) {
								channel.say(me.getNick() + ": TieBot isn't running!");
								return;
							}
							
							// mark as off
							channel.say(me.getNick() + ": Stopping TieBot!");
							tieBotInstance.setWikiDiscussionsFeed(false);
							
							// If neither mode is running, just quit
							if(!tieBotInstance.getWikiDiscussionsFeed() && !tieBotInstance.getNewUsersFeed()) {
								rcSession.close("Requested by " + me.getNick());
								tieBotInstance.cleanupBeforeQuit();
								tieBotInstance = null;
								return;
							}

						}
					} catch (IndexOutOfBoundsException err) {
						channel.say(me.getNick() + ": Invalid syntax. Use ~tiebot on/off!");
						return;
					}
				} else {
					session.sayPrivate(me.getNick(),  "You are not allowed to use the ~tiebot command");
				}
				break;
				
			case "newusers":
				if(isAdmin) {
					try {
						String mode = fullCommand.split(" ")[1].toLowerCase();
						boolean on;
						if(mode.equalsIgnoreCase("on")) {
							on = true;
							enableTieBotNewUsers = true;
						} else if(mode.equalsIgnoreCase("off")) {
							on = false;
							enableTieBotNewUsers = false;
						} else {
							channel.say(me.getNick() + ": Invalid syntax. Use ~newusers on/off");
							return;
						}
						
						if(on) {
							// if we have an instance and we already have it set to read wiki discussions
							if( tieBotInstance != null && tieBotInstance.getNewUsersFeed() ){
								channel.say(me.getNick() + ": NewUsers is already running!");
								return;
							// if we have an instance, but the new users isn't running; start it
							} else if(tieBotInstance != null) {
								me.getSession().join("#cvn-wikia-newusers");
								channel.say(me.getNick() + ": Starting new users feed!");
								tieBotInstance.setNewUsersFeed(true);
								return;
							}
							
							// no instance
							me.getSession().join("#cvn-wikia-newusers");
							createTieBotInstance();
							channel.say(me.getNick() + ": Starting new users feed!");
						} else { // stop running
							if(tieBotInstance == null) {
								channel.say(me.getNick() + ": new users feed isn't running!");
								return;
							}
							
							// mark as off
							channel.say(me.getNick() + ": Stopping new users feed!");
							tieBotInstance.setNewUsersFeed(false);
							me.getSession().getChannel("#cvn-wikia-newusers").part("Requested by " + me.getNick());
							
							// If neither mode is running, just quit
							if(!tieBotInstance.getWikiDiscussionsFeed() && !tieBotInstance.getNewUsersFeed()) {
								rcSession.close("Requested by " + me.getNick());
								tieBotInstance.cleanupBeforeQuit();
								tieBotInstance = null;
								return;
							}

						}
					} catch (IndexOutOfBoundsException err) {
						channel.say(me.getNick() + ": Invalid syntax. Use ~newusers on/off!");
						return;
					}
				} else {
					session.sayPrivate(me.getNick(),  "You are not allowed to use the ~newusers command");
				}
				break;
				
			case "hush":
				if(tieBotInstance == null) {
					channel.say(me.getNick() + ": TieBot isn't running!");
					return;
				} else {
					String time = fullCommand.split(" ")[1];
					long hushTime = System.currentTimeMillis() + (Integer.parseInt(time) * 60000);
					tieBotInstance.hush(hushTime);
					
					channel.say(me.getNick() + ": Hushing for " + time + " mins!");
				}
				break;
				
			case "unhush":
					if(tieBotInstance == null) {
						channel.say(me.getNick() + ": TieBot is not running!");
						return;
					} else {
						tieBotInstance.unhush();
						channel.say(me.getNick() + ": No longer hushed!");
					}
					break;
			
			case "astatus":
			case "status":
				channel.say(getStatusText(me.getNick()));
				break;
				
			case "tellbot":
				if(isMod) {
					try {
						String mode = fullCommand.split(" ")[1].toLowerCase();
						boolean on;
						if(mode.equalsIgnoreCase("on")) {
							on = true;
							enableTellBot = true;
						} else if(mode.equalsIgnoreCase("off")) {
							on = false;
							enableTellBot = false;
						} else {
							channel.say(me.getNick() + ": Invalid syntax. Use ~tellbot on/off");
							return;
						}
						
						if(on) {
							if(tellBotInstance != null) {
								channel.say(me.getNick() + ": TellBot is already running!");
							} else {
								addTellBotCommands();
							}
						} else {
							if(tellBotInstance == null) {
								channel.say(me.getNick() + ": TellBot isn't running!");
							} else {
								channel.say(me.getNick() + ": Stopping TellBot!");
								tellBotInstance.cleanupBeforeQuit();
								session.removeIRCEventListener(tellBotInstance);
								tellBotInstance = null;
							}
						}
					} catch (IndexOutOfBoundsException e) {
						channel.say(me.getNick() + ": Invalid syntax. Use ~tellbot on/off!");
					}
				} else {
					channel.say(me.getNick() + ": You're not allowed to use the ~tellbot command");
				}
				break;
			
			case "allowed":
				String hosts = "";
				
				for(int i = 0; i < allowedHosts.size(); i++) {
					hosts+= allowedHosts.get(i) + ",";
				}
				
				hosts = hosts.substring(0, hosts.length() -1);
				
				channel.say(me.getNick() + ": " + hosts + " are allowed to use ~update");
				break;
				
			case "help":
				channel.say(me.getNick() +": My commands can be found on my userpage at [[User:TyBot]].");
				break;
				
			case "source":
				channel.say(me.getNick() + ": My source code is available at https://github.com/ty-a/GEMWbot2");
				break;				
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
			ircSettings.setProperty("enableTieBot", "" + enableTieBot);
			ircSettings.setProperty("enableTieBotNewUsers", "" + enableTieBotNewUsers);
			ircSettings.setProperty("enableTellBot", "" + enableTellBot);
			ircSettings.setProperty("enableDiscordBot", "" + enableDiscordBot);
			ircSettings.setProperty("feedNetwork", feedNetwork);
			ircSettings.setProperty("feedPort", "" + feedPort);
			
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
	
	protected void setUpdateTaskToNull() {
		updateTask = null;
	}
	
	protected void setCheckerToNull() {
		checker = null;
	}
	
	public void startChecker() {
		if(checker == null) {
			checker = new UpdateChecker(this);
			Thread checkerThread = new Thread(checker);
			checkerThread.start();
		}
	}
	
	public void startUpdater() {
		if(updateTask == null) {
			try {
				updateTask = new GrandExchangeUpdater(this);
				Thread updateThread = new Thread(updateTask);
				updateThread.start();
				manager.getSession(ircServer).getChannel(ircChannel).say("GE Update detected! Starting updates.... ");
				checker = null;
			} catch (Exception e) {
				manager.getSession(ircServer).getChannel(ircChannel).say("Failed to start GE Updater!");
			}
			
		}
	}
	
	private void createTieBotInstance() {
		rcSession = manager.requestConnection(feedNetwork, feedPort);
		tieBotInstance = new TieBot(manager, this);
		rcSession.addIRCEventListener(tieBotInstance);
		
		tieBotInstance.setNewUsersFeed(enableTieBotNewUsers);
		tieBotInstance.setWikiDiscussionsFeed(enableTieBot);
	}
	
	protected void createDiscordBotInstance() {
		try {
			discordBotInstance = new DiscordBot(this);
		} catch (DiscordException e) {
			tellBotInstance.addTell("tybot", "wikia/vstf/TyA", "Discord error when starting discordBot", null);
		}
	}
	
	public GrandExchangeUpdater getGEMWinstance() {
		return updateTask;
	}
	
	public TellBot getTellBotInstance() {
		return tellBotInstance;
	}
	
	public DiscordBot getDiscordBotInstance() {
		return discordBotInstance;
	}
	
	public TieBot getTieBotinstance() {
		return tieBotInstance;
	}
	
	private void addTellBotCommands() {
		tellBotInstance = new TellBot();
		session.addIRCEventListener(tellBotInstance);
	}
	
	public String getStatusText(String nick) {
		RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
		long millis = rb.getUptime();
		long second = TimeUnit.MILLISECONDS.toSeconds(millis);
		long minute = TimeUnit.MILLISECONDS.toMinutes(millis);
		long hour = TimeUnit.MILLISECONDS.toHours(millis);
		long day = TimeUnit.MICROSECONDS.toDays(millis);
		String uptime = String.format("%02d days %02d hours %02d minutes %02d seconds", day, hour, minute, second);
		String out = "";
		if(updateTask == null) {
			out = nick + ": The GE Updater is not running! ";
			
		} else {
			out = nick + ": Updating page " + updateTask.getNumberOfPagesUpdated() + " out of " + updateTask.getNumberOfPages() + "! ";
		}
		
		out += "Uptime: " + uptime + " TieBot: " + (enableTieBot? "on": "off") 
				+ " NewUsersFeed: " + (enableTieBotNewUsers? "on": "off") + " TellBot: " + (enableTellBot? "on": "off") + " Discord: "
				+ (enableDiscordBot? "on": "off");
		
		return out;
		
	}
	
	public String getDiscordStatusText(String nick) {
		RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
		long millis = rb.getUptime();
		long second = TimeUnit.MILLISECONDS.toSeconds(millis);
		long minute = TimeUnit.MILLISECONDS.toMinutes(millis);
		long hour = TimeUnit.MILLISECONDS.toHours(millis);
		long day = TimeUnit.MICROSECONDS.toDays(millis);
		String uptime = String.format("%02d days %02d hours %02d minutes %02d seconds", day, hour, minute, second);
		String out = "";
		if(updateTask == null) {
			out = nick + ": The GE Updater is not running!\n";
			
		} else {
			out = nick + ": Updating page " + updateTask.getNumberOfPagesUpdated() + " out of " + updateTask.getNumberOfPages() + "!\n";
		}
		
		out += "Uptime: " + uptime + "\nTieBot: " + (enableTieBot? "on": "off") 
				+ " NewUsersFeed: " + (enableTieBotNewUsers? "on": "off") + " TellBot: " + (enableTellBot? "on": "off") + " Discord: "
				+ (enableDiscordBot? "on": "off");
		
		return out;
	}
}