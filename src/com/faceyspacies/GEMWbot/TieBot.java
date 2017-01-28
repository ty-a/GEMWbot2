package com.faceyspacies.GEMWbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import com.faceyspacies.GEMWbot.Holders.WikiChange;

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
	
	private String feedChannel;
	
	private String webhookURL;
	private String ptbrWebhookURL;
	private String osWebhookURL;
	
	public TieBot(ConnectionManager manager, GEMWbot main) {
		this.manager = manager;
		this.mainIRC = main;
		
		isHushed = false;
		
		loadSettings();
		
	}
	
	private boolean loadSettings() {
		Properties settings = new Properties();
		InputStream input = null;
		
		try {
			File settingsFileLocation = new File("tiebot.properties");
			input = new FileInputStream(settingsFileLocation);
			
			settings.load(input);
			
			String temp;
			webhookURL = settings.getProperty("webhookURL");
			ptbrWebhookURL = settings.getProperty("ptbrWebhookURL");
			osWebhookURL = settings.getProperty("osWebhookURL");
			feedChannel = settings.getProperty("feedChannel");
			temp = settings.getProperty("ignoreThreshold");
			
			if(temp == null) {
				ignoreThreshold = 22;
			} else {
				ignoreThreshold = Integer.parseInt(temp);
			}
			
			if(feedChannel == null) {
				System.out.println("[ERROR] feedChannel is missing from tiebot.properties; closing");
				return false;
			}
			
			if(webhookURL == null) {
				System.out.println("[ERROR] webhookURL is missing from tiebot.properties; closing");
				return false;
			}
			
		}
		catch (FileNotFoundException err) {
			System.out.println("[ERROR] Unable to load tiebot.properties file; closing");
			return false;
		} catch (IOException e) {
			System.out.println("[ERROR] IO Error loading tiebot.properties; closing");
			return false;
		}
		
		return true;
		
	}

	@Override
	public void receiveEvent(IRCEvent e) {
		
		if (e.getType() == Type.CONNECT_COMPLETE)
		{
			// Feed network has no NickServ, so there can be no authentication
			e.getSession().join(feedChannel);
		}
		else if(e.getType() == Type.CHANNEL_MESSAGE) {
			processMessage((MessageEvent) e);
		}
	}
	
	private void processMessage(MessageEvent e) {
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
		
		String wiki = message.substring(message.indexOf("http://") + 7, message.indexOf(".wikia"));
		
		if(wikiDiscussionsFeed && isFollowedWiki(wiki)) {
			WikiChange change = formatMessage(message);
			processWikiDiscussions(change);
		}
		
	}
	
	private WikiChange formatMessage(String message) {
		// [[Special:Log/delete]] delete http://ty.wikia.com/wiki/Special:Log/delete * TyA *  deleted "[[Exchange:Pirate's hook]]": housekeeping
		// [[Special:Log/newusers]] create http://es.dofuswiki.wikia.com/wiki/Especial:Log/newusers * Juan157 *  New user account
		// [[Special:Log/wikifeatures]] wikifeatures http://florielsand-school-of.wikia.com/wiki/Special:Log/wikifeatures * Hogwarts888 *  wikifeatures: set extension option: wgEnableAchievementsExt = true
		// [[Special:Log/move]] move http://thomasandfriends.wikia.com/wiki/Special:Log/move * Thomasoldschool *  moved [[File:ThomasPercyandthePostTrain17.png]] to [[File:ThomasPercyandthePostTrain017.png]]
		// [[Special:Log/protect]] protect http://english-voice-over.wikia.com/wiki/Special:Log/protect * Jade Cooper *  protected "[[The Star Wars Holiday Special (1978)]]" ‎[edit=sysop] (indefinite) ‎[move=sysop] (indefinite)
		// [[Special:Log/useravatar]] avatar_chn http://community.wikia.com/wiki/Special:Log/useravatar * KaosuUzu *  User avatar added or updated
		// [[Special:Log/upload]] overwrite http://h2o.wikia.com/wiki/Special:Log/upload * Mundo Comdilies *  uploaded a new version of "[[File:Cleo orange juice.png]]"
		// [[Special:Log/upload]] upload http://marvel.wikia.com/wiki/Special:Log/upload * The Many-Angled One *  uploaded "[[File:Mighty Captain Marvel Vol 1 0 Noto Variant.jpg]]"
		
		// !NMB
		// [[Hedorah vs B.O.B]] !N http://deathbattlefanon.wikia.com/index.php?oldid=581970&rcid=593468 * Vrokorta * (+29) Created page with "You're right, Hedorah stomps."
		
		
		// If page starts with Special:Log, it is a long entry, else it is an edit. 
		// If a log entry, next word is the log type.  If edit, next set is the flags (!(unpatrolled) New Minor Bot) Empty if none set
		
		// old regex which doesn't capture wiki domain
		//String regex = "\\[\\[Special:Log\\/(\\w*)\\]\\] (\\w*) .* \\* (.*) \\* (.*)";
		String regex = "\\[\\[E?[Ss]pecial:Log\\/(\\w*)\\]\\] (\\w*) http:\\/\\/(.*)\\.wikia.* \\* (.*) \\* (.*)";
		
		boolean isLog = message.startsWith("[[Special:Log/") || message.startsWith("[[Especial:Log/");
		boolean isNew = false;
		boolean isMinor = false;
		boolean isBot = false;
		String type = null;
		String target = null;
		String summary = null;
		String performer = null;
		String flags = null;
		String diffNumber = null;
		String diff = null;
		String wiki = null;
		
		if(isLog) {
			Pattern logregex = Pattern.compile(regex);
			Matcher logRegexMatcher = logregex.matcher(message);
			
			if(logRegexMatcher.matches()) {
				type = logRegexMatcher.group(2);
				performer = logRegexMatcher.group(4);
				wiki = logRegexMatcher.group(3);
				int start,end;
				String temp;
				
				switch(type) {
					case "delete":
						temp = logRegexMatcher.group(5);
						start = temp.indexOf("[[") + 2;
						end = temp.indexOf("]]");
						target = temp.substring(start, end);
						
						start = temp.indexOf(":", end);
						if(start == -1) {
							summary = "";
						} else {
							summary = temp.substring(start + 2);
						}
						break;
						
					case "upload":
						isNew = logRegexMatcher.group(2).equals("upload");
						temp = logRegexMatcher.group(5);
						start = temp.indexOf("[[") + 2;
						end = temp.indexOf("]]");
						target = temp.substring(start, end);
						
						start = temp.indexOf(":", end);
						if(start == -1) {
							summary = "";
						} else {
							summary = temp.substring(start+2);
						}
						
						break;
						
					default:
						summary = logRegexMatcher.group(5);		
				}
				
				WikiChange change = new WikiChange(isLog, isMinor, isBot, isNew, type, target, performer, summary, flags, diffNumber, diff, wiki);
				return change;
			}
		} else {
			type = "edit";
			
			// old regex which doesn't capture wiki domain
			//String editRegex = "\\[\\[(.*)\\]\\] ([!NMB]{0,4}) (.*) \\* (.*) \\* \\((.*)\\) (.*)";
			String editRegex = "\\[\\[(.*)\\]\\] ([!NMB]{0,4}) (http:\\/\\/(.*).wikia.*) \\* (.*) \\* \\((.*)\\) (.*)";
			Pattern editregex = Pattern.compile(editRegex);
			Matcher editRegexMatcher = editregex.matcher(message);
			
			if(editRegexMatcher.matches()) {
				target = editRegexMatcher.group(1);
				flags = editRegexMatcher.group(2);
				wiki = editRegexMatcher.group(4);
				
				isBot = flags.contains("B");
				isMinor = flags.contains("M");
				isNew = flags.contains("N");
				
				diff = editRegexMatcher.group(3);
				performer = editRegexMatcher.group(5);
				diffNumber = editRegexMatcher.group(6);
				summary = editRegexMatcher.group(7);
				
				return new WikiChange(isLog, isMinor, isBot, isNew, type, target, performer, summary, flags, diffNumber, diff, wiki);
			}
		}
		
		return null;
	}

	private void processNewUserMessage(String message) {
		// [[Especial:Log/newusers]] create http://es.dofuswiki.wikia.com/wiki/Especial:Log/newusers * Juan157 *  New user account
		
		Session mainSession = manager.getSession("irc.freenode.net");
		Channel channel = mainSession.getChannel("#cvn-wikia-newusers");
		//Channel channel = mainSession.getChannel("#tybot");
		if(channel == null) {
			System.out.println("channel is null");
			return;
		}
		channel.say(message);
	}
	
	private void processWikiDiscussions(WikiChange change) {
		
		String fullWikiUrl;
		if(isHushed) {
			if(System.currentTimeMillis() > hushTime) {
				isHushed = false;
			} else {
				return;
			}
		}

		sendToDiscord(change);
		// only process discussions on the RS Wiki
		if(!change.getWiki().equals("runescape")) {
			return;
		}
		
		if(change.getTarget() == null)
			return;
		
		if(!isFollowedPage(change.getTarget()))
			return;
		
		fullWikiUrl = processWikiUrl(change.getDiff());
		
		// the change has the symbol in front of it
		if(Integer.parseInt(change.getChange().substring(1)) < ignoreThreshold && !change.getTarget().equals("RuneScape:Counter-Vandalism Unit") ) { // always show CVU edits
			return;
		}
		
		if(!change.isBot()) {
			Session mainSession = manager.getSession("irc.freenode.net");
			Channel channel = mainSession.getChannel(mainIRC.ircChannel);
			if(channel == null)
				return;
			channel.say(change.getTarget() + " was edited by " + change.getPerformer() + " | " + fullWikiUrl  + " | " + "(" + change.getChange() + ")" + change.getSummary());
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

	private boolean isFollowedWiki(String wiki) {
		return(wiki.equalsIgnoreCase("pt.runescape") || wiki.equalsIgnoreCase("runescape") || wiki.equalsIgnoreCase("2007.runescape"));
	}
	
	private boolean isFollowedPage(String page) {
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
		} else if (page.startsWith("RuneScape:Featured images/")) {
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
			case "RuneScape:Administrative requests":
			case "RuneScape:AutoWikiBrowser/Requests":
			case "RuneScape:Off-site/IRC/Bot requests":
			case "RuneScape:Counter-Vandalism Unit":
			case "RuneScape:The Wikian":
				return true;
		}
		
		return false;
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
	
	private void sendToDiscord(WikiChange change) {
		String outmessage;
		String formatString;
		String summary;
		String wikiURL = "http://" + change.getWiki() + ".wikia.com/wiki/";
		
		formatString = "[%1$s](<" + wikiURL + "User:%2$s>) ([t](<" + wikiURL + "User_talk:%2$s>)";
		formatString += "|[c](<" + wikiURL + "Special:Contributions/%2$s>)) ";
		String start = String.format(formatString, change.getPerformer(), change.getPerformer().replaceAll(" ", "_"));
		
		switch(change.getType()) {
			case "edit":
				if(change.isBot())
					return;
				
				formatString = start + "edited [%1$s](<" + wikiURL + "%2$s>)";
				formatString += " (%5$s) `%3$s ` ([diff](<%4$s>))";
				
				outmessage = String.format(formatString, change.getTarget(),
						change.getTarget().replace(" ", "_").replace(")", "\\)"), change.getSummary(), change.getDiff(), change.getChange());
				
				break;
				
			case "delete":
				formatString = start + "deleted [%1$s](<" + wikiURL + "%2$s>)";
				formatString += " `%3$s `";
				
				outmessage = String.format(formatString, change.getTarget(),
						change.getTarget().replaceAll(" ", "_").replace(")", "\\)"), change.getSummary());
				break;
				
			case "move":
				//  moved [[Ice Bow]] to [[Ice bow]]: Actual in-game capitalisation.
				summary = change.getSummary();
				int pgstart, pgend;
				pgstart = summary.indexOf("[[") + 2;
				pgend = summary.indexOf("]]");
				String page1 = summary.substring(pgstart, pgend);
				
				pgstart = summary.indexOf("[[", pgstart) + 2;
				pgend = summary.indexOf("]]", pgend + 1);
				String page2 = summary.substring(pgstart, pgend);
				
				int summarystart = summary.indexOf(":", pgend);
				if(summarystart == -1) {
					summary = "";
				} else {
					summary = summary.substring(summarystart + 1);
				}
				
				formatString = start + "moved %1$s to [%2$s](<" + wikiURL + "%3$s>) `%4$s `";
				
				outmessage = String.format(formatString, page1, page2, page2.replaceAll(" ",  "_"), summary);
				break;
				
			case "avatar_chn":
				outmessage = start + " changed their avatar!";
				break;
				
			case "upload":
				formatString = start + "uploaded [%1$s](<" + wikiURL + "%2$s>)";
				formatString += " `%3$s `";
				
				outmessage = String.format(formatString, change.getTarget(),
						change.getTarget().replaceAll(" ", "_").replace(")", "\\)"), change.getSummary());
				
				break;
				
			case "overwrite":
				// uploaded a new version of "[[File:Opal bracelet detail.png]]": Transparency
				summary = change.getSummary();
				pgstart = summary.indexOf("[[") + 2;
				pgend = summary.indexOf("]]");
				String page = summary.substring(pgstart, pgend);
				
				summarystart = summary.indexOf(":", pgend);
				if(summarystart == -1) {
					summary = "";
				} else {
					summary = summary.substring(summarystart + 1);
				}
				
				formatString = start + "uploaded a new version of [%1$s](<" + wikiURL + "%2$s>) `%3$s `";
				outmessage = String.format(formatString, page, page.replaceAll(" ", "_").replace(")", "\\)"), summary);
				break;
				
			case "block":
			case "unblock":
			case "rights":
			case "chatban":
			case "chatbanremove":
				
				outmessage = start + "`" + change.getSummary() + " `";
				break;
				
			default:
				if(change.getTarget() != null) {
					formatString = start + "%4$s [%1$s](<" + wikiURL + "%2$s>)";
					formatString += " `%3$s `";
					
					outmessage = String.format(formatString, change.getTarget(),
							change.getTarget().replaceAll(" ", "_").replace(")", "\\)"), change.getSummary(),
							change.getType());
				} else {
					formatString = start + "%2$s ";
					formatString += "`%1$s `";
					
					outmessage = String.format(formatString, change.getSummary(),
							change.getType());
				}
		}
	
		try {
			String json = new JSONObject()
					.put("content", outmessage)
					.toString();
			// http://stackoverflow.com/a/35013372
			byte[] out = json.getBytes(StandardCharsets.UTF_8);
			int length = out.length;
			
			URL url = null;
			switch(change.getWiki()) {
			case "runescape":
				url = new URL(webhookURL);
				break;
			case "pt.runescape":
				url = new URL(ptbrWebhookURL);
				break;
			default: // only option left is OS
				url = new URL(osWebhookURL);
			}
			
			int tries = 0; 
			while(tries < 3) {
				tries++;
				URLConnection con = url.openConnection();
				HttpURLConnection http = (HttpURLConnection)con;
				http.setRequestMethod("POST");
				http.setDoOutput(true);
				
				http.setFixedLengthStreamingMode(length);
				http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
				http.setRequestProperty("User-Agent", "tybot #1 - http://github.com/ty-a/GEMWbot2 - @ty#0768");
				
				try(OutputStream os = http.getOutputStream()) {
				    os.write(out);
				}
	
				if(http.getResponseCode() == 200 || http.getResponseCode() == 204 || http.getResponseCode() == 201) {
					http.disconnect();
					break;
				} else if(http.getResponseCode() == 400) {
					System.out.println("Invalid payload send to discord!");
					System.out.println(json);
					break;
				} else {
					http.disconnect();
					try {
						System.out.println("failed to send");
						Thread.sleep(500);
					} catch (InterruptedException e) {}
				}
				//System.out.println(http.getResponseCode());
	
				
			}
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
