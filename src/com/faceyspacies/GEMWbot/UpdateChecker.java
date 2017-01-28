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

import org.json.JSONException;
import org.json.JSONObject;
import org.wikipedia.Wiki;

import com.faceyspacies.GEMWbot.Holders.GEPrice;

public class UpdateChecker implements Runnable {
	private String wikiUserName;
	private String wikiUserPass;
	private String wikiURL;
	private String rsGraphAPILink = "http://services.runescape.com/m=itemdb_rs/api/graph/";
	
	private boolean running;
	
	private Wiki wikiBot;
	
	private GEMWbot ircInstance;
	
	public UpdateChecker(GEMWbot ircInstance) {
		if(!loadSettings()) {
			return;
		};
		
		this.ircInstance = ircInstance;
		
		wikiBot = new Wiki(wikiURL, "");
		wikiBot.setMarkBot(true);
		wikiBot.setThrottle(0);
		wikiBot.setUserAgent("TyA's TyBot running the Grand Exchange Updater. https://github.com/ty-a/GEMWbot2");
		wikiBot.setUsingCompressedRequests(false);
	}
	
	/**
	 * Loads settings from the gemw.properties file. 
	 * @return boolean based on success
	 */
	private boolean loadSettings() {
		Properties settings = new Properties();
		InputStream input = null;
		
		try {
			File settingsFileLocation = new File("gemw.properties");
			input = new FileInputStream(settingsFileLocation);
			
			settings.load(input);
			
			wikiUserName = settings.getProperty("wikiUserName");
			wikiUserPass = settings.getProperty("wikiUserPass");
			wikiURL = settings.getProperty("wikiURL");
			
			if(wikiUserName == null) {
				System.out.println("[ERROR] wikiUserName is missing from gemw.properties; closing");
				return false;
			}
			
			if(wikiUserPass == null) {
				System.out.println("[ERROR] wikiUserPass is missing from gemw.properties; closing");
				return false;
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
	
	/**
	 * Loads the current price from the Jagex Graph API. 
	 * @param id The item ID that corresponds to the item you want to load the price for. 
	 * Usually obtained by parsing the item's Exchange:Module/itemName page.
	 * @return GEPrice object containing the item's price, ID, and timestamp. 
	 * @throws MalformedURLException only an issue if you provide a bad item id. 
	 */
	private GEPrice loadCurPrice(String  id) throws MalformedURLException {

		URL url = new URL(rsGraphAPILink + id + ".json");
		System.out.println(rsGraphAPILink + id + ".json");
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
			String timestamp = highestSoFar + "";
			String key = highestSoFar + "000";
			
			newPrice = dailyItem.getInt(key);
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
	
	/**
	 * Loads the current price for itemName from the wiki.
	 * @param itemName This is actually the entire page name. So Module:Exchange/itemName should be passed.
	 * @return The item's price as a string. 
	 */
	private String loadPriceFromWiki(String itemName) {
		
		String pageContent;
		String price;
		try {
			pageContent = wikiBot.getPageText(itemName);
			Pattern priceregex = Pattern.compile("price\\s*= (\\d+)");
			Matcher priceMatcher = priceregex.matcher(pageContent);
			
			if(priceMatcher.find()) {
				price = priceMatcher.group(1);
			} else {
				System.out.println("[ERROR] unable to load item price");
				return null;

			}
			
			return price;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}

	@Override
	/**
	 * Called when starting the thread. Set the running global and catch any exceptions we get.
	 */
	public void run() {
		running = true;
		
		try {
			Start();
		} catch (Exception err) {
			System.out.println("[EXCEPTION] " + err.getClass() + ": " + err.getMessage());
			err.printStackTrace();
			ircInstance.setCheckerToNull();
			ircInstance.getTellBotInstance().addTell("UpdateChecker", "wikia/vstf/TyA", "Error: " + err.getClass() + ": " + err.getMessage(), null);
		}
	}
	
	/**
	 * The main loop of the update checker. Wait 10 minutes, then see if the Jagex Graph API has a different price for the Abyssal
	 * whip than currently stored on the wiki. If so, start the GE Updater and stop running the checker. After the GE update is finished
	 * it restarts the update checker.  
	 */
	private void Start() {
		while(running) {
			try {
				Thread.sleep(600000); // 10 minutes
				String jagexPrice = loadCurPrice("4151").getPrice();
				String wikiPrice = loadPriceFromWiki("Module:Exchange/Abyssal_whip");
				
				if(jagexPrice == null || wikiPrice == null) { // try again in 10 mins
					continue;
				}
				
				if(jagexPrice.equalsIgnoreCase(wikiPrice)) {
					continue;
				} else {
					ircInstance.startUpdater();
					running = false;
				}
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch(NullPointerException e) {
				e.printStackTrace();
			}
		}

	}
	
	/**
	 * Stops the update checker. Does not stop a GE update if it has started. 
	 */
	protected void stopRunning() {
		running = false;
	}
}
