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
	
	private boolean newUsersFeed;
	private boolean wikiDiscussionsFeed;
	
	private boolean isHushed;
	
	private long hushTime;
	
	private int ignoreThreshold;
	
	private Wiki wiki = new Wiki("runescape.wikia.com", "");
	
	public TieBot(ConnectionManager manager, GEMWbot main, int ignoreThreshold) {
		this.manager = manager;
		this.mainIRC = main;
		this.ignoreThreshold = ignoreThreshold;
		
		isHushed = false;
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
		
		if(newUsersFeed) {
			if(message.indexOf("Log/newusers]] create http://") != -1) {
				processNewUserMessage(e.getMessage());
				return; // if a new user, it isn't a discussion
				
			}
		}
		
		if(wikiDiscussionsFeed) {
			processWikiDiscussions(message);
		}
		
	}
	
	private void processNewUserMessage(String message) {
		
		Session mainSession = manager.getSession("irc.freenode.net");
		Channel channel = mainSession.getChannel("#cvn-wikia-newusers");
		//Channel channel = mainSession.getChannel("#tybot");
		if(channel == null) {
			System.out.println("channel is null");
			return;
		}
		channel.say(message);
	}
	
	private void processWikiDiscussions(String message) {
		
		String wiki;
		String page;
		String summary;
		String user;
		String fullWikiUrl;
		String count;
		
		if(isHushed) {
			if(System.currentTimeMillis() > hushTime) {
				isHushed = false;
			} else {
				return;
			}
		}
		
		
		
		page = message.substring(2, message.indexOf("]]"));
		
		if(!isFollowedPage(page))
			return;
		
		wiki = message.substring(message.indexOf("http://") + 7, message.indexOf(".wikia"));
		
		if(!isFollowedWiki(wiki))
			return;
		
		fullWikiUrl = message.substring(message.indexOf("http://"), message.indexOf(" * "));
		fullWikiUrl = processWikiUrl(fullWikiUrl);
		
		int nameStart = message.indexOf(" * ") + 3;
		int nameEnd = message.indexOf(" * ", nameStart);
		user = message.substring(nameStart, nameEnd);
		summary = message.substring(nameEnd + 3);
		
		count = summary.substring(2, summary.indexOf(")"));
		
		if(Integer.parseInt(count) < ignoreThreshold ) {
			return;
		}
		
		if(!isBotUser(user)) {
			Session mainSession = manager.getSession("irc.freenode.net");
			Channel channel = mainSession.getChannel(mainIRC.ircChannel);
			if(channel == null)
				return;
			channel.say(page + " was edited by " + user + " | " + fullWikiUrl  + " | " + summary);
		}
	}
	
	private String processWikiUrl(String fullWikiUrl) {
		fullWikiUrl = fullWikiUrl.replace("runescape", "rs");
		fullWikiUrl = fullWikiUrl.replace("index.php", "");
		if(fullWikiUrl.indexOf("rcid") != -1) { // page is probs new
			fullWikiUrl = fullWikiUrl.replace("oldid", "diff");
			fullWikiUrl = fullWikiUrl.substring(0, fullWikiUrl.indexOf("&rcid"));
			return fullWikiUrl;
		}
		if(fullWikiUrl.indexOf("oldid") != -1) 
			fullWikiUrl = fullWikiUrl.substring(0, fullWikiUrl.indexOf("&oldid"));
		return fullWikiUrl;
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
		else if(page.startsWith("RuneScape:Requests for ")) {
			
			if(page.indexOf("/") == -1) {
				// If there is no /, it is most likely the base page.
				// We don't need to know about edits to that as the discussion happens on
				// sub pages
				return false;
			}
			
			if(page.toLowerCase().indexOf("/archive") != -1) {
				// This page is most likely an archive. 
				// archives usually take the form of /Archive int 
				// where int is the current archive number
				return false;
			}
				
			
			return true;
		}

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
	
	protected void setNewUsersFeed(boolean newMode) {
		newUsersFeed = newMode;
	}
	
	protected boolean getNewUsersFeed() {
		return newUsersFeed;
	}
	
	protected void setWikiDiscussionsFeed(boolean newMode) {
		wikiDiscussionsFeed = newMode;
	}
	
	protected boolean getWikiDiscussionsFeed() {
		return wikiDiscussionsFeed;
	}
	
	protected void hush(long hushTime) {
		isHushed = true;
		this.hushTime = hushTime;
	}
	
	protected void unhush() {
		isHushed = false;
	}
	
	protected void setThreshold(int newThreshold) {
		ignoreThreshold = newThreshold;
	}
}
