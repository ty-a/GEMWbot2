package com.faceyspacies.GEMWbot;

import java.io.IOException;

import org.wikipedia.Wiki;
import org.wikipedia.Wiki.User;

import jerklib.Channel;
import jerklib.ConnectionManager;
import jerklib.Session;
import jerklib.events.IRCEvent;
import jerklib.events.IRCEvent.Type;
import jerklib.events.MessageEvent;

public class TieBot implements jerklib.listeners.IRCEventListener {
	private ConnectionManager manager;
	private GEMWbot mainIRC;
	private Wiki wiki = new Wiki("runescape.wikia.com");
	
	public TieBot(ConnectionManager manager, GEMWbot main) {
		this.manager = manager;
		this.mainIRC = main;
	}

	@Override
	public void receiveEvent(IRCEvent e) {
		if (e.getType() == Type.CONNECT_COMPLETE)
		{
			// Feed network has no NickServ, so there can be no authentication
			e.getSession().join("feedChannel");
		}
		else if(e.getType() == Type.CHANNEL_MESSAGE) {
			processMessage((MessageEvent) e);
		}
	}
	
	public void processMessage(MessageEvent e) {
		// We are only interested in the feed, not other things
		if(!e.getNick().equals("rcbot"))
			return;
		// regex is from  http://stackoverflow.com/questions/970545/how-to-strip-color-codes-used-by-mirc-users/970723
		String message = e.getMessage().replaceAll("\\x1f|\\x02|\\x12|\\x0f|\\x16|\\x03(?:\\d{1,2}(?:,\\d{1,2})?)?", "");
		String wiki;
		String page;
		String summary;
		String user;
		String fullWikiUrl;
		
		page = message.substring(2, message.indexOf("]]"));
		
		if(!isFollowedPage(page))
			return;
		
		wiki = message.substring(message.indexOf("http://") + 7, message.indexOf(".wikia"));
		
		if(!isFollowedWiki(wiki))
			return;
		
		fullWikiUrl = message.substring(message.indexOf("http://"), message.indexOf(" * "));
		
		int nameStart = message.indexOf(" * ") + 3;
		int nameEnd = message.indexOf(" * ", nameStart);
		user = message.substring(nameStart, nameEnd);
		
		summary = message.substring(nameEnd + 3);
		
		Session mainSession = manager.getSession("irc.freenode.net");
		Channel channel = mainSession.getChannel(mainIRC.ircChannel);
		if(channel == null)
			return;
		channel.say(page + " was edited by " + user + " | " + fullWikiUrl  + " | " + summary);
		
	}
	
	public boolean isFollowedWiki(String wiki) {
		return(wiki.equalsIgnoreCase("runescape"));
	}
	
	public boolean isFollowedPage(String page) {
		// we follow all forum pages
		if(page.startsWith("Forum:"))
			return true;
		else if(page.startsWith("RuneScape:Clan Chat/Requests for CC Rank/"))
			return true;
		else if(page.startsWith("RuneScape:Requests for "))
			return true;
		
		switch(page) {
			case "RuneScape:User help":
			case "RuneScape:Administrator requests":
			case "RuneScape:AutoWikiBrowser/Requests":
			case "RuneScape:Off-site/IRC/Bot requests":
			case "RuneScape:Counter-Vandalism Unit":
				return true;
		}
		
		return false;
	}
	
	// does not currently work, not sure if logic error, a different error of mine, 
	// or error with library so it is removed for now
	public boolean isBotUser(String user) {
		try {
			User target = wiki.getUser(user.trim());
			if(target == null) { // an IP, can't be a bot
				return false;
			}
			
			return target.isA("bot");
		} catch (IOException e) {
			// if we fail to get it due to network error, 
			// I'm willing to just assume it isn't a bot
			return false;
		}
	}

}
