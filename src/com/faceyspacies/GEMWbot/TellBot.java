package com.faceyspacies.GEMWbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.faceyspacies.GEMWbot.Holders.Message;

import jerklib.events.IRCEvent;
import jerklib.events.JoinEvent;
import jerklib.events.MessageEvent;
import jerklib.events.IRCEvent.Type;
import jerklib.events.PartEvent;
import jerklib.events.QuitEvent;

/**
 * 
 * @author Ty
 *
 */
public class TellBot implements jerklib.listeners.IRCEventListener {

	/**
	 * Our main Database connection. Used to create our PreparedStatements.
	 */
	private Connection db;
	
	
	/**
	 * PreparedStatement for getting the the number of tells for a user
	 */
	private PreparedStatement getTellCountQuery;
	
	/**
	 * PreparedStatement for getting the number of tells for a user and who sent them.
	 */
	private PreparedStatement getTellCountQueryWithSenders;
	
	/**
	 * PreparedStatment for getting a user's tells
	 */
	private PreparedStatement getTellMessagesQuery;
	
	/**
	 * PreparedStatement for removing a tell
	 */
	private PreparedStatement removeTellQuery;
	
	/**
	 * PreparedStatement for removing a tell from a host or to a host?
	 */
	private PreparedStatement removeTellQueryHost;
	
	/**
	 * PreparedStatement for adding a tell.
	 */
	private PreparedStatement addTellQuery;
	
	/**
	 * Boolean on whether Evilbot is currently in chat. 
	 */
	private boolean isEvilBotHere;
	
	/**
	 * The database server host. Loaded from tellbot.properties
	 */
	private String dbHost;
	
	/**
	 * The name of the tells database. Loaded from tellbot.properties
	 */
	private String dbName;
	
	/**
	 * The database user. Loaded from tellbot.properties
	 */
	private String dbUser;
	
	/**
	 * The datanase user's password. Loaded from tellbot.properties. 
	 */
	private String dbPass;
	
	/**
	 * Our constructor. Loads settings and creates DB stuff. If either fails, prints line to console.
	 * 
	 * It shouldn't affect TyBot much, so if tellbot isn't working, check console. 
	 */
	public TellBot() {
		if(!loadSettings())
			System.out.println("Failed to load settings :(");
		// we will just exception out and die, main tybot will survive 
		
		if(!createDBStuff()) {
			System.out.println("Failed to create DB Stuff :( so DB probs dead.");
			
		}

		isEvilBotHere = false; 
	}
	
	/**
	 * Loads settings from tellbot.properties. 
	 * @return Boolean based on success
	 */
	private boolean loadSettings() {
		Properties settings = new Properties();
		InputStream input = null;
		
		try {
			File settingsFileLocation = new File("tellbot.properties");
			input = new FileInputStream(settingsFileLocation);
			
			settings.load(input);
			
			dbHost = settings.getProperty("dbHost");
			dbName = settings.getProperty("dbName");
			dbUser = settings.getProperty("dbUser");
			dbPass = settings.getProperty("dbPass");

			
			if(dbHost == null) {
				System.out.println("[ERROR] dbHost is missing from tellbot.properties; closing");
				return false;
			}
			
			if(dbName == null) {
				System.out.println("[ERROR] dbName is missing from tellbot.properties; closing");
				return false;
			}
			
			if(dbUser == null) {
				System.out.println("[ERROR] dbUser is missing from tellbot.properties;");
			}
			
			if(dbPass == null) {
				System.out.println("[ERROR] dbPass is missing from tellbot.properties; closing");
				return false;
			}
			
		}
		catch (FileNotFoundException err) {
			System.out.println("[ERROR] Unable to load tellbot.properties file; closing");
			return false;
		} catch (IOException e) {
			System.out.println("[ERROR] IO Error loading tellbot.properties; closing");
			return false;
		}
		
		return true;
		
	}
	
	/** Check to see if Evilbot joins or leaves chat.
	 * Also processes all messages
	 * @see jerklib.listeners.IRCEventListener#receiveEvent(jerklib.events.IRCEvent)
	 */
	@Override
	public void receiveEvent(IRCEvent e) {
		if(e.getType() == Type.CHANNEL_MESSAGE) {
			processMessage((MessageEvent) e);
		} else if (e.getType() == Type.JOIN) {
			JoinEvent je = (JoinEvent)e;
			if(je.getNick().equalsIgnoreCase("evilbot")) {
				isEvilBotHere = true;
			}
		} else if (e.getType() == Type.PART) {
			PartEvent pe = (PartEvent)e;
			if(pe.getWho().equalsIgnoreCase("evilbot")) {
				isEvilBotHere = false;
			}
		} else if (e.getType() == Type.QUIT) {
			QuitEvent qe = (QuitEvent)e;
			if(qe.getNick().equalsIgnoreCase("evilbot")) {
				isEvilBotHere = false;
			}
		}
	}
	
	/**
	 * Processes all messages. First determines if evilbot is here. 
	 * After that, we check if the user has any messages. If they do, we deliver the messages.
	 * <br />
	 * Then we determine if the user is trying to use a command, and if so act on it. 
	 * @param me
	 */
	private void processMessage(MessageEvent me) {
		if(me.getNick().equalsIgnoreCase("evilbot")) {
			isEvilBotHere = true;
		}
		
		Message[] messages = getMessageForUser(me.getNick(), me.getHostName());
		
		// if we get an SQL Error in GetMessageForUser(), it returns null
		// If it is null, there is likely an issue with it so try to recreate it 3 times. 
		if(messages == null) {
			int count = 0;
			while(count < 3) {
				cleanupBeforeQuit();
				createDBStuff();
				
				messages = getMessageForUser(me.getNick(), me.getHostName());
				if(messages != null) {
					break;
				}
				
				count++;
			}
		}
		
		if (messages != null) {
			for(Message message: messages) {
				me.getChannel().say(me.getNick() + ": [" + message.getTimestamp() + "] <" + message.getSender() + "> " + message.getMessage());
				removeTell(message.getSender(), me.getNick(), me.getHostName());
			}
		}
		
		// now we need to see if they are trying to give us a command
		// commands are ~tell user_with_underscores message 
		//              ~untell user_with_underscores
		//              ~told user_with_underscores
		if(me.getMessage().charAt(0) != '~' && me.getMessage().charAt(0) != '`') { // we have a command as ~ is our trigger
			return;
		}
		
		String command = "";
		String fullCommand = me.getMessage();
		if(fullCommand.indexOf(" ") != -1) {
			/* index 1 is start of command after trigger
			 */
			command = fullCommand.substring(0, fullCommand.indexOf(" "));
			
		} else {
			command = fullCommand;
		}
		
		switch(command) {
			case "`told":
				if(isEvilBotHere && getTellCountForUser(fullCommand.split(" ")[1]) == 0) {
					break;
				}
			case "~told":
				try {
					int tells = getTellCountForUser(fullCommand.split(" ")[1]);
					if(tells > 0) {
						me.getChannel().say(me.getNick() + ": " + fullCommand.split(" ")[1] + " has not yet received their messages!");
					} else if(tells == -1) {
						me.getChannel().say(me.getNick() + ": Internal error when getting tellcount. Pls tell ty");
					} else {
						me.getChannel().say(me.getNick() + ": " + fullCommand.split(" ")[1] + " has received their messages!");
					}
					
				} catch (IndexOutOfBoundsException e) {
					me.getSession().notice(me.getNick(), "Invalid usage! Please use ~told user");
					return;
				}
				break;
				
			case "`tell":
				if(isEvilBotHere) {
					break;
				}
			case "~tell":
				try {
					addTell(me.getNick(), fullCommand.split(" ")[1], fullCommand.substring(fullCommand.indexOf(" ", 
							fullCommand.indexOf(" ") + 1) + 1), me);
					
					me.getSession().notice(me.getNick(), "Your message to " + fullCommand.split(" ")[1] 
							+ " will be sent next time I see them!");

				} catch (IndexOutOfBoundsException e) {
					me.getSession().notice(me.getNick(), "Invalid usage! Please use ~tell user/host message");
					return;
				}
				
				break;
				
			case "`untell":
				if(isEvilBotHere && getTellCountForUser(fullCommand.split(" ")[1]) == 0) {
					break;
				}
			case "~untell":
				try {
					if(removeTell(me.getNick(), fullCommand.split(" ")[1], null) >= 1) {
						me.getChannel().say(me.getNick() + ": Removed message to " + fullCommand.split(" ")[1] + "!");
					} else {
						me.getChannel().say(me.getNick() + ": Failed to remove message to " + fullCommand.split(" ")[1] + 
								"! Did they not have one?");
					}
				} catch (IndexOutOfBoundsException e) {
					me.getSession().notice(me.getNick(), "Invalid usage! Please use ~untell user/host");
					return;
				}
				
				break;
				
			case "`tellcheck":
				if(isEvilBotHere && getTellCountForUser(fullCommand.split(" ")[1]) == 0) {
					break;
				}
			case "~tellcheck":
				me.getChannel().say(me.getNick() + ": " + getTellCountForUserPlusSenders(fullCommand.split(" ")[1]));
				break;
				
		}
	}
	
	/**
	 * Get the messages for the user. This checks if the user has messages on their nick or host. 
	 * @param user the user's nick we're checking
	 * @param host the user's host we're checking
	 * @return Message array of messages, or null if none
	 */
	private Message[] getMessageForUser(String user, String host) {
		try {
			ArrayList<Message> list = new ArrayList<Message>();
			getTellMessagesQuery.setString(1, user);
			getTellMessagesQuery.setString(2, host);
			
			ResultSet rs = getTellMessagesQuery.executeQuery();
			while(rs.next()) {
				list.add(new Message(rs.getString("sender"), rs.getString("message"), 
						new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(rs.getTimestamp("sent"))));
			}
			
			return list.toArray(new Message[list.size()]);
		} catch (SQLException e) {
			return null;
		}
	}
	
	/**
	 * Gets the number of tells user has. 
	 * @param user User to get tell count for
	 * @return int number of messages, -1 for error
	 */
	private int getTellCountForUser(String user) {
		try {
			getTellCountQuery.setString(1, user);
			
			ResultSet rs = getTellCountQuery.executeQuery();
			rs.next();
			int count = rs.getInt("count");
			rs.close();
			return count;
			
		} catch (SQLException e) {
			return -1;
		}
	}
	
	/**
	 * Gets the tell count for the user plus who has sent them messages
	 * @param user User to get tell count for
	 * @return <ul>
	 * <li>"user has number message(s) waiting for him/her, from: source, source, ..."</li>
	 * <li>"user has no unread messages!"</li>
	 * <li>Null in case of error</li>
	 * </ul>
	 */
	private String getTellCountForUserPlusSenders(String user) {
		try {
			getTellCountQueryWithSenders.setString(1, user);
			
			ResultSet rs = getTellCountQueryWithSenders.executeQuery();
			int count = 0;
			String out = "";
			String users = "";
			//"target has number message(s) waiting for him/her, from: source, source, ..."
			List<String> displayedNicks = new ArrayList<String>();
			while(rs.next()) {
				 count++;
				 if(!displayedNicks.contains(rs.getString("sender"))) {
					 users += rs.getString("sender") + ", ";
					 displayedNicks.add(rs.getString("sender"));
				 }
			}
			rs.close();
			if(count == 0) {
				return user + " has no unread messages!";
			}
			
			users = users.substring(0, users.length() - 2);
			out = user + " has " + count + " unread message(s) from " + users;
			
			return out;
			
		} catch (SQLException e) {
			return null;
		}
	}
	
	// Remove the tell from nick to target
	// int row changes
	// -1 in case of error.
	/**
	 * Removes tells from nick/host to target. Target can be a ; delimited list.
	 * @param nick Nick of user wanting to remove tells
	 * @param target Nick/Host of user to remove tell from, or ; delimited list of Nicks/Hosts
	 * @param host
	 * @return Number of rows changed, so number of tells removed. -1 in case of error.
	 */
	private int removeTell(String nick, String target, String host) {
		int rowsRemoved = 0;
		if(target.indexOf(";") > -1) {
			String[] targets = target.split(";");
			for(String curr: targets) {
				rowsRemoved += actuallyRemoveTell(nick, curr, host);
			}
		} else {
			rowsRemoved += actuallyRemoveTell(nick, target, host);
		}
		
		return rowsRemoved;
	}
	
	/**
	 * Actually removes the tell from the database
	 * @param nick 
	 * @param target
	 * @param host
	 * @return Number of rows changed, -1 in case of error.
	 */
	private int actuallyRemoveTell(String nick, String target, String host) {
		try {
			if(host != null) {
				removeTellQueryHost.setString(1, nick);
				removeTellQueryHost.setString(2, target);
				removeTellQueryHost.setString(3, host);
			
				return removeTellQueryHost.executeUpdate();

			} else {
				removeTellQuery.setString(1, target);
				removeTellQuery.setString(2, nick);
			
				return removeTellQuery.executeUpdate();

			}
			
		} catch (SQLException e) {
			return -1;
		}
	}
	
	/**
	 * processes a tell before adding it to the db
	 * @param nick - who sent it
	 * @param target - who is getting it
	 * @param message - what is being sent
	 * @param me - a MessageEvent object to respond back. null if sent message internally
	 */
	protected void addTell(String nick, String target, String message, MessageEvent me) {
		if(target.indexOf(";") != -1) {
			// we have thing in the form of nick;nick;nick
			String[] targets = target.split(";");
			for(String curr: targets) {
				if(getTellCountForUser(curr) >= 8 ) {
					if(me != null)
						me.getSession().notice(me.getNick(), "Sorry, " + curr + "'s inbox is full :(");
					return;
				}
				actuallyAddTell(nick, curr, message, me);
			}
		} else {
			if(getTellCountForUser(target) >= 8 ) {
				if(me != null)
					me.getSession().notice(me.getNick(), "Sorry, " + target + "'s inbox is full :(");
				return;
			}
			actuallyAddTell(nick, target, message, me);
		}		
	}
	
	/**
	 * adds a tell to the db
	 * @param nick - who sent it
	 * @param target - who is getting it
	 * @param message - what is being sent
	 * @param me - a MessageEvent object to respond back. null if sent message internally
	 */
	private void actuallyAddTell(String nick, String target, String message, MessageEvent me) {
		try {
			if(target.charAt(0) == '@') {
				target = target.substring(1);
			}
			//sender,target,message
			addTellQuery.setString(1, nick);
			addTellQuery.setString(2, target);
			addTellQuery.setString(3, message);
			
			addTellQuery.executeUpdate();
			
		} catch (SQLException e) {
			if(me == null)
				return;
			me.getSession().notice(me.getNick(), "Error sending tell! Please tell ty including what was sent!");
			System.out.println(e.getMessage());
		}
	}
	
	/**
	 * Closes all database objects before quitting. 
	 */
	protected void cleanupBeforeQuit() {
		try {
			if(db != null) {
				db.close();
			}
		} catch (SQLException e) {}
		
		try {
			if(getTellCountQuery != null) {
				getTellCountQuery.close();
			}
		} catch (SQLException e) {}
		
		try{
			if (getTellMessagesQuery != null) {
				getTellMessagesQuery.close();
			}
		} catch (SQLException e) {}
		
		try {
			if (removeTellQuery != null) {
				removeTellQuery.close();
			}
		} catch (SQLException e) {}
		
		try {
			if (addTellQuery != null) {
				addTellQuery.close();
			}
		} catch (SQLException e) {}
	}
	
	/**
	 * Creates the database objects/prepared statements. 
	 * @return boolean on success. 
	 */
	private boolean createDBStuff() {
		try {
			db = DriverManager.getConnection("jdbc:mysql://" + dbHost + "/" + dbName + "?useUnicode=true&characterEncoding=UTF-8", dbUser, dbPass);
			System.out.println("Created tells db handler");
			getTellCountQuery = db.prepareStatement("SELECT count(*) AS count FROM tells WHERE target = ?;");
			getTellCountQueryWithSenders = db.prepareStatement("SELECT sender FROM tells WHERE target = ?;");
			getTellMessagesQuery = db.prepareStatement("SELECT message,sender,sent FROM tells WHERE target = ? OR target = ?;");
			removeTellQuery = db.prepareStatement("DELETE FROM tells WHERE target = ? AND sender = ?;");
			removeTellQueryHost = db.prepareStatement("DELETE FROM tells WHERE sender = ? AND (target = ? OR target = ?);");
			addTellQuery = db.prepareStatement("INSERT INTO tells(sender,target,message) VALUES(?,?,?);");
			return true;
		} catch (SQLException e) {
			return false;
		}
		
	}

}
