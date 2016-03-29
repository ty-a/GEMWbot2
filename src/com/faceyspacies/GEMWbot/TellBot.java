package com.faceyspacies.GEMWbot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import jerklib.events.IRCEvent;
import jerklib.events.JoinEvent;
import jerklib.events.MessageEvent;
import jerklib.events.IRCEvent.Type;
import jerklib.events.PartEvent;
import jerklib.events.QuitEvent;

public class TellBot implements jerklib.listeners.IRCEventListener {

	private Connection db;
	private PreparedStatement getTellCountQuery;
	private PreparedStatement getTellCountQueryWithSenders;
	private PreparedStatement getTellMessagesQuery;
	private PreparedStatement removeTellQuery;
	private PreparedStatement removeTellQueryHost;
	private PreparedStatement addTellQuery;
	private boolean isEvilBotHere;
	
	TellBot() {
		try {
			// TODO: REMOVE HARD CODED CREDENTIALS 
			db = DriverManager.getConnection("jdbc:mysql://localhost/tells?useUnicode=true&characterEncoding=UTF-8", "username", "password");
			System.out.println("Created tells db handler");
			getTellCountQuery = db.prepareStatement("SELECT count(*) AS count FROM tells WHERE target = ?;");
			getTellCountQueryWithSenders = db.prepareStatement("SELECT sender FROM tells WHERE target = ?;");
			getTellMessagesQuery = db.prepareStatement("SELECT message,sender,sent FROM tells WHERE target = ? OR target = ?;");
			removeTellQuery = db.prepareStatement("DELETE FROM tells WHERE target = ? AND sender = ?;");
			removeTellQueryHost = db.prepareStatement("DELETE FROM tells WHERE sender = ? AND (target = ? OR target = ?);");
			addTellQuery = db.prepareStatement("INSERT INTO tells(sender,target,message) VALUES(?,?,?);");

			isEvilBotHere = false;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			
		} 
	}
	
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
	
	private void processMessage(MessageEvent me) {
		if(me.getNick().equalsIgnoreCase("evilbot")) {
			isEvilBotHere = true;
		}
		
		Message[] messages = getMessageForUser(me.getNick(), me.getHostName());
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
	
	// Tells are stored by nick, rather than by host
	//     We will want to see if their host matches or if their nick matches
	// returns array of messages for user or null if none
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
	
	// Return the count of tells for user
	// -1 in case of error
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
	
	// Return the count of tells for user
	// -1 in case of error
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
	
	// Add a tell from nick to target with message
	private void addTell(String nick, String target, String message, MessageEvent me) {
		if(target.indexOf(";") != -1) {
			// we have thing in the form of nick;nick;nick
			String[] targets = target.split(";");
			for(String curr: targets) {
				if(getTellCountForUser(curr) >= 8 ) {
					me.getSession().notice(me.getNick(), "Sorry, " + curr + "'s inbox is full :(");
					return;
				}
				actuallyAddTell(nick, curr, message, me);
			}
		} else {
			if(getTellCountForUser(target) >= 8 ) {
				me.getSession().notice(me.getNick(), "Sorry, " + target + "'s inbox is full :(");
				return;
			}
			actuallyAddTell(nick, target, message, me);
		}		
	}
	
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
			me.getSession().notice(me.getNick(), "Error sending tell! Please tell ty including what was sent!");
			System.out.println(e.getMessage());
		}
	}
	
	protected void cleanupBeforeQuit() {
		try {
			if(db != null) {
				db.close();
			}
			
			if(getTellCountQuery != null) {
				getTellCountQuery.close();
			}
				
			if (getTellMessagesQuery != null) {
				getTellMessagesQuery.close();
			}
			
			if (removeTellQuery != null) {
				removeTellQuery.close();
			}
			
			if (addTellQuery != null) {
				addTellQuery.close();
			}
		} catch (SQLException e) {}
	}

}
