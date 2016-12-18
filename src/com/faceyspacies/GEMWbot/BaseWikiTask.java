package com.faceyspacies.GEMWbot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;

import org.json.JSONException;
import org.json.JSONObject;
import org.wikipedia.Wiki;

abstract class BaseWikiTask implements Runnable {
	
	protected String wikiUserName;
	protected String wikiUserPass;
	protected String logPage;
	protected String wikiURL;
	protected String ircChannel;
	protected String errorLog;
	private String rsGraphAPILink = "http://services.runescape.com/m=itemdb_rs/api/graph/";
	
	protected Wiki wikiBot;

	protected GEMWbot ircInstance;
	
	protected boolean running;
	
	protected String timestamp;
	
	
	BaseWikiTask(GEMWbot ircInstance) {
		if(!loadSettings()) {
			if(ircInstance != null)
				ircInstance.getTellBotInstance().addTell("gemwbot", "wikia/vstf/TyA", "failed to load config", null);
		};
		
		wikiBot = new Wiki(wikiURL, "");
		wikiBot.setMarkBot(true);
		wikiBot.setThrottle(0);
		wikiBot.setUserAgent("TyA's TyBot running the Grand Exchange Updater. https://github.com/ty-a/GEMWbot2");
		wikiBot.setUsingCompressedRequests(false); // Wikia fails anywhere from 20-50 times a run on this
		errorLog = "";
		
		if(ircInstance != null) {
			ircChannel = ircInstance.getChannel();
			this.ircInstance = ircInstance;
		}
		timestamp = null;
	
	}
	
	private boolean loadSettings() {
		Properties settings = new Properties();
		InputStream input = null;
		
		try {
			File settingsFileLocation = new File("gemw.properties");
			input = new FileInputStream(settingsFileLocation);
			
			settings.load(input);
			
			wikiUserName = settings.getProperty("wikiUserName");
			wikiUserPass = settings.getProperty("wikiUserPass");
			logPage = settings.getProperty("logPage");
			wikiURL = settings.getProperty("wikiURL");
			
			if(wikiUserName == null) {
				System.out.println("[ERROR] wikiUserName is missing from gemw.properties; closing");
				return false;
			}
			
			if(wikiUserPass == null) {
				System.out.println("[ERROR] wikiUserPass is missing from gemw.properties; closing");
				return false;
			}
			
			if(logPage == null) {
				System.out.println("[ERROR] logPage is missing from gemw.properties; Using User:" + wikiUserName + "/log instead");
				logPage = "User:" + wikiUserName + "/log";
			}
			
			if(wikiURL == null) {
				System.out.println("[ERROR] wikiURL is missing from gemw.properties; closing");
				return false;
			}
			
		}
		catch (FileNotFoundException err) {
			System.out.println("[ERROR] Unable to load gemw.properties file; closing");
			return false;
		} catch (IOException e) {
			System.out.println("[ERROR] IO Error loading gemw.properties; closing");
			return false;
		}
		
		return true;
		
	}
	
	@Override
	public void run() {

		running = true;
		
		try {
			Start();
		}
		catch(Exception err) {
			System.out.println("[EXCEPTION] " + err.getClass() + ": " + err.getMessage());
			err.printStackTrace();
			ircInstance.setUpdateTaskToNull();
		}
	}
	
	abstract void Start();
	
	protected boolean Login() {
		int failures = 0;
		while(failures < 3) {
			// Try to login three times. If we fail three times, send a message to the channel 
			try {
				wikiBot.login(wikiUserName, wikiUserPass.toCharArray());
				return true;
				
			}	catch (FailedLoginException e) {
				failures++;
				if(failures == 3) {
					addToLog(new UpdateResult("unable to login - check username/pass", false), "[[Special:UserLogin]]");
					return false;
				}
				
				
			} catch (IOException e) {
				failures++;
				if(failures == 3) {
					addToLog(new UpdateResult("unable to login - network", false), "[[Special:UserLogin]]");
					return false;
				}
			}
		}
		
		return false;
	}
	
	protected void updateLogPage() {
		try {
			if(errorLog.equalsIgnoreCase("")) 
				errorLog = "No issues today; yay!";
			wikiBot.newSection(logPage, "{{subst:CURRENTDAY}} {{subst:CURRENTMONTHNAME}} {{subst:CURRENTYEAR}}", errorLog, false, true);
		} catch (LoginException | IOException e) {
			ircInstance.sendMessage(ircChannel, "`tell @wikia/vstf/TyA could not save the log page; that is just great.");
		}
		
	}
	
	protected void addToLog(UpdateResult status, String pageName) {
		if(!status.getSuccess()) {
			errorLog += "# [{{fullurl:" + pageName.replace("_", " ") + "}} " + pageName.replace("_", " ") + "] - " + status.getResult() + "\n"; 
		}
	}
	
	protected GEPrice loadCurPrice(String  id) throws MalformedURLException {

		URL url = new URL(rsGraphAPILink + id + ".json");
		GEPrice gePrice = null;
		HttpURLConnection request;
		int newPrice = -1;
		try {
				
			request = (HttpURLConnection) url.openConnection();
			request.setRequestProperty("User-Agent", "GEMWBot2 - The RuneScape Wiki's Grand Exchange Price Database Updater. https://github.com/ty-a/GEMWbot2");
			request.connect();
			
			BufferedReader response = new BufferedReader(new InputStreamReader(request.getInputStream()));
			
			String data = response.readLine(); // entire thing is given as one line
			response.close();
			
			JSONObject baseItem = new JSONObject(data);
			JSONObject dailyItem = baseItem.getJSONObject("daily");
			
			if(timestamp == null) {
				Iterator<?> keys = dailyItem.keys();
				int highestSoFar = 0;
				int currNum;
				Pattern getNumber = Pattern.compile("(\\d+)\\d\\d\\d");
				
				while(keys.hasNext()) {
					String currKey = (String)keys.next();
					Matcher verifyNum = getNumber.matcher(currKey);
					if(verifyNum.find()) {
						currKey = verifyNum.group(1);
					} else {
						System.out.println("[ERROR] Failed to find price in json");
					}
					
					currNum = Integer.parseInt(currKey);
					
					if(currNum > highestSoFar) {
						highestSoFar = currNum;
						
					} else
						continue; 
				}
				timestamp = highestSoFar + "";
			}
			newPrice = dailyItem.getInt(timestamp + "000");
			gePrice = new GEPrice(timestamp, newPrice, id);
			
		} catch (IOException e) {
			System.out.println("[ERROR] Unable to connect to GEMW API.");
			return null;
		} catch (JSONException e)  {
			System.out.println("[ERROR] Unable to process JSON");
			return null;
		}
		
		return gePrice;
	}
	
	protected void stopRunning() {
		running = false;
	}

}
