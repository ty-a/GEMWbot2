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

import org.json.JSONObject;
import org.wikipedia.Wiki;

public class GrandExchangeUpdater implements Runnable {
	// This class will be run from GEMWbot to start the GE updates
	// It will run it its own thread and once it is done, it will be destroyed
	// so a new object will be created every GE Update
	
	private int numberOfPages;
	private int numberOfPagesUpdated;
	private int runningMode;
	
	private String wikiUserName;
	private String wikiUserPass;
	private String logPage;
	private String wikiURL;
	private String rsGraphAPILink = "http://services.runescape.com/m=itemdb_rs/api/graph/";
	private String ircChannel;
	private String errorLog;
	
	private boolean running;
	
	private Wiki wikiBot;

	private GEMWbot ircInstance;
	
	private VolumeHandler volumes;
	
	GrandExchangeUpdater(GEMWbot ircInstance) {
		
		if(!loadSettings()) {
			return;
		};
		
		ircChannel = ircInstance.getChannel();
		this.ircInstance = ircInstance;
		
		wikiBot = new Wiki(wikiURL, "");
		wikiBot.setMarkBot(true);
		wikiBot.setThrottle(0);
		errorLog = "";
		
		runningMode = 1; // 0 = exchange, 1 = module, 2 = both
		
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
			System.out.println(err.getMessage());
			ircInstance.setUpdateTaskToNull();
		}
	}
	
	private void Start() {

		System.out.println("[INFO] GE Updater has Started");
		
		boolean couldLogin = Login();
		if(!couldLogin) {
			return; // abort, message was sent in function
		}
		
		volumes = new VolumeHandler();
		volumes.getVolumes();
		if(volumes.isEmpty()) {
			errorLog += "# Failed to get volume data due to connection issue; Attempted to continue\n";
		}
		
		String[] pages;
		
		switch(runningMode) {
		case 0: // template
			pages = getPages(wikiBot);
			break;
		case 1: // module
			pages = getModulePages(wikiBot);
			break;
		default: //both
			pages = getPages(wikiBot);
			if(pages == null) 
				return;
			String[] newPages = new String[pages.length];
			for(int i = 0; i < pages.length; i++ ) {
				newPages[i] = pages[i].replace("Exchange:", "");
			}
			pages = newPages;
		}

		if(pages == null) { // abort, message was sent in function
			return;
		}
		numberOfPages = pages.length;
		numberOfPagesUpdated = 0;
		
		for(int i = 0; i < pages.length; i++) {
		
			// update the pages, once it is done add to log
			if(running) {
				numberOfPagesUpdated++;
				if(pages[i].contains("/Data")) // we update /Data pages as part of updating the main page
					continue;
				addToLog(doUpdates(pages[i]), pages[i]);
			
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					ircInstance.sendMessage(ircChannel, "`tell @wikia/vstf/TyA UNABLE TO SLEEP; I AM FREAKING OUT RIGHT NOW");
				}
			} else { // if no longer running, stop loop and end
				return;
			}
		}
		
		updateLogPage();
		
		/* Module page testing
		
		try {
			updateModulePage("Module:Exchange/Iron bar");
		} catch (LoginException | PageIsEmptyException
				| NoItemIDException | IOException e) {
			// TODO Auto-generated catch block
			
			e.printStackTrace();
		}
		*/

		ircInstance.setUpdateTaskToNull();
		ircInstance.sendMessage(ircChannel, "GE Updates complete!"); // if we make it to end, we did it
	}
	
	private boolean Login() {
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
	
	private void updateLogPage() {
		try {
			if(errorLog.equalsIgnoreCase("")) 
				errorLog = "No issues today; yay!";
			wikiBot.newSection(logPage, "{{subst:CURRENTDAY}} {{subst:CURRENTMONTHNAME}} {{subst:CURRENTYEAR}}", errorLog, false, true);
		} catch (LoginException | IOException e) {
			ircInstance.sendMessage(ircChannel, "`tell @wikia/vstf/TyA could not save the log page; that is just great.");
		}
		
	}
	
	private void addToLog(UpdateResult status, String pageName) {
		if(!status.getSuccess()) {
			errorLog += "# [[" + pageName.replace("_", " ") + "]] - " + status.getResult() + "\n"; 
		}
	}

	private UpdateResult doUpdates(String page) {
		int failures;
		failures = 0;
		
		while(failures < 3) {
			try {
				// all items that do not throw exceptions are not recoverable
				switch(runningMode) {
				case 0:
					return updatePage(page);
				case 1:
					return updateModulePage(page);
				case 2:
					addToLog(updatePage(page), page);
					return updateModulePage(page);
				}
			} catch (IOException e) {
				failures++;
				if(failures == 3) {
					return new UpdateResult("network failure", false);
				}
				
			} catch (LoginException e) {
				failures++;
				if(failures == 3) {
					return new UpdateResult("failed to stay logged in", false);
				}
				Login();
			}
		}
		
		return new UpdateResult("unknown error", false);
	}
	
	private String[] getPages(Wiki wikiBot) {
		
		int failures = 0;
		String[] page = new String[1];
		page[0] = "Exchange:Iron_bar";
		return page;
		/*
		
		while(failures < 3) {
			try {
				// test wiki does not have namespace 112, but main wiki does
				//String[] pages = wikiBot.getCategoryMembers("Grand Exchange", 112);
				String[] pages = wikiBot.getCategoryMembers("Grand Exchange");
				return pages;
			} catch (IOException e1) {
				failures++;
				if(failures == 3) {
					ircInstance.sendMessage(ircChannel, "`tell @wikia/vstf/TyA unable to get page list.");
					return null;
				}
			}
		} 
		
		return null; // shouldn't reach here, but if we do assume we have failed. 
		*/
	}
	
	private UpdateResult updatePage(String pageName) throws IOException, LoginException {
		String itemID = null;
		GEPrice newPrice = null;
		
		pageName = pageName.replace(" ", "_");
		
		if(pageName.indexOf("Exchange:") == -1) {
			pageName = "Exchange:" + pageName;
		}
		
		boolean doesExist;
		doesExist = (boolean)wikiBot.getPageInfo(pageName).get("exists");
		if(!doesExist) {
			return new UpdateResult("page does not exist", false);
		}
		
		String pageContent = wikiBot.getPageText(pageName);
		
		if(pageContent.length() == 0) {
			return new UpdateResult("page is empty", false);
		}
		
		Pattern itemIDregex = Pattern.compile("\\|ItemId=(\\d+)");
		Matcher itemIDMatcher = itemIDregex.matcher(pageContent);
		
		if(itemIDMatcher.find()) {
			itemID = itemIDMatcher.group(1);
		} else {
			System.out.println("[ERROR] unable to load item id");
			return new UpdateResult("unable to load item id", false);

		}
		
		try {
			newPrice = loadCurPrice(itemID);
		} catch (MalformedURLException e) {
			System.out.println("[ERROR] Item ID " + itemID + " is invalid!");
			return new UpdateResult("item id is invalid", false);
		}
		
		if(newPrice == null)
			return new UpdateResult("unable to fetch price", false);
		
		String volume = volumes.getVolumeFor(newPrice.getId());
		if(volume != null) { // WE HAVE VOLUME DATA, WOO!
			// since we don't need the data again, it is safe to just remove it
			if(pageContent.indexOf("Volume=") != -1) {
				pageContent = pageContent.replaceAll("\\|Volume=.*\\n", "\\|Volume=" + volume + "\n");
				pageContent = pageContent.replaceAll("\\|VolumeDate=.*\\n", "\\|VolumeDate=~~~~~\n");
			} else { // Does not have volume fields on page, but has data. probs newly on top100
				String newVolText = "";
				newVolText = "|Volume= " + volume + "\n";
				newVolText += "|VolumeData=~~~~~\n";
				newVolText += "|Limit=";
				pageContent = pageContent.replaceAll("\\|Limit=", newVolText);
			}
		}
		
		// remove data that we don't need
		pageContent = pageContent.replaceAll("\\|Last=.*\\n", "");
		pageContent = pageContent.replaceAll("\\|LastDate=.*\\n", "");
		
		//replaces Price/Date with the new date and pushes the old price down to the LastPrice/LastDate param 
		// which we removed above.
		pageContent = pageContent.replaceAll("\\|Price=", String.format("|Price=%,d\n|Last=", newPrice.getPrice()));
		
		pageContent = pageContent.replaceAll("\\|Date=", "|Date=~~~~~\n|LastDate=");
		
		wikiBot.edit(pageName, pageContent, "Updating [[" + pageName.replace("_", " ")  + "]]");
		
		addToLog(doDataUpdate(pageName, newPrice), pageName + "/Data");
		return new UpdateResult("", true);
	}
	
	private UpdateResult doDataUpdate(String pageName, GEPrice price) {
		int failures;
		failures = 0;
		pageName += "/Data";
		
		while(failures < 3) {
			try {
				// all items that do not throw exceptions are not recoverable
				
				if(pageName.indexOf("Exchange:") == -1) { // module
					return updateModuleData(pageName, price);
				} else {
					return updateData(pageName, price);
				}
				
			} catch (IOException e) {
				failures++;
				if(failures == 3) {
					return new UpdateResult("network failure", false);
				}
				
			} catch (LoginException e) {
				failures++;
				if(failures == 3) {
					return new UpdateResult("failed to stay logged in", false);
				}
				Login();
			}
		}
		
		return new UpdateResult("unknown error", false);
	}
	
	private UpdateResult updateData(String pageName, GEPrice price) throws LoginException, IOException {
		String pageContent;		
		boolean doesExist;
		
		doesExist = (boolean) wikiBot.getPageInfo(pageName).get("exists");
		if(!doesExist) {
			return new UpdateResult("page does not exist", false);
		}
		
		pageContent = wikiBot.getPageText(pageName);
		
		if(pageContent.length() == 0) {
			return new UpdateResult("page is empty", false);
			
		}
		
		pageContent = pageContent.replaceAll("\\n}}", ",");
		
		String volume = volumes.getVolumeFor(price.getId());
		if(volume == null) {
			pageContent += price.getTimestamp() + ":" + price.getPrice() + "\n}}";
		} else {
			pageContent += price.getTimestamp() + ":" + price.getPrice() + ":" + volume  + "\n}}";
		}
		
		wikiBot.edit(pageName, pageContent, "Updating [[" + pageName.replace("_", " ") + "]]");
		
		return new UpdateResult("", true);
	}
	
	private String[] getModulePages(Wiki wiki) {
		int failures = 0;
		
		while(failures < 3) {
			try {
				String[] pages = wiki.listPages("Module:Exchange/", null, 828, -1, -1);
				return pages;
			} catch (IOException e1) {
				failures++;
				if(failures == 3) {
					ircInstance.sendMessage(ircChannel, "`tell @wikia/vstf/TyA unable to get page list.");
					return null;
				}
			}
		}
		
		return null;
	}
	
	private UpdateResult updateModulePage(String pageName) throws IOException, LoginException {
		String itemID = null;
		GEPrice newPrice = null;
		
		pageName = pageName.replace(" ", "_");
		
		if(pageName.indexOf("Module:Exchange/") == -1) {
			pageName = "Module:Exchange/" + pageName;
		}
		
		String pageContent = wikiBot.getPageText(pageName);
		
		if(pageContent.length() == 0) {
			return new UpdateResult("page is empty", false);
		}
		
		Pattern itemIDregex = Pattern.compile("itemId\\s*= (\\d+)");
		Matcher itemIDMatcher = itemIDregex.matcher(pageContent);
		
		if(itemIDMatcher.find()) {
			itemID = itemIDMatcher.group(1);
		} else {
			System.out.println("[ERROR] unable to load item id");
			return new UpdateResult("unable to load item id", false);
		}
		
		try {
			newPrice = loadCurPrice(itemID);
		} catch (MalformedURLException e) {
			System.out.println("[ERROR] Item ID " + itemID + " is invalid!");
			return new UpdateResult("item id is invalid", false);
		}
		
		if(newPrice == null)
			return new UpdateResult("unable to fetch price", false);
		
		String volume = volumes.getVolumeFor(newPrice.getId());
		if(volume != null) { // WE HAVE VOLUME DATA, WOO!
			// since we don't need the data again, it is safe to just remove it
			pageContent = pageContent.replaceAll("volume\\s*=.*\\n", "volume     = " + volume + ",\n");
			pageContent = pageContent.replaceAll("volumeDate\\s*=.*\\n", "volumeDate = '~~~~~',\n");
		}
		
		// remove data that we don't need
		pageContent = pageContent.replaceAll("\\s*last\\s*=.*\n", "\n");
		pageContent = pageContent.replaceAll("\\s*lastDate\\s*=.*\n", "\n");
		
		//replaces Price/Date with the new date and pushes the old price down to the LastPrice/LastDate param 
		// which we removed above.
		pageContent = pageContent.replaceAll("price\\s*=", String.format("price      = %,d,\n    last       = ", newPrice.getPrice()));
		pageContent = pageContent.replaceAll(" date\\s*=", " date       = '~~~~~',\n    lastDate   = ");
		
		wikiBot.edit(pageName, pageContent, "Updating [[" + pageName.replace("_", " ")  + "]]");
		
		addToLog(doDataUpdate(pageName, newPrice), pageName + "/Data");
		return new UpdateResult("", true);
	}
	
	private UpdateResult updateModuleData(String pageName, GEPrice price) throws LoginException, IOException {
		String pageContent;

		pageContent = wikiBot.getPageText(pageName);
		
		if(pageContent.length() == 0) {
			return new UpdateResult("page is empty", false);
		}
		
		pageContent = pageContent.replaceAll("\\n}", ",");
		
		String volume = volumes.getVolumeFor(price.getId());
		if(volume == null) {
			pageContent += "    '" + price.getTimestamp() + ":" + price.getPrice() + "'\n}";
		} else {
			pageContent += "    '" + price.getTimestamp() + ":" + price.getPrice() + ":" + volume + "'\n}";
		}

		wikiBot.edit(pageName, pageContent, "Updating [[" + pageName.replace("_", " ") + "]]");
		
		return new UpdateResult("", true);
	}
	
	private GEPrice loadCurPrice(String  id) throws MalformedURLException {

		URL url = new URL(rsGraphAPILink + id + ".json");
		GEPrice gePrice = null;
		HttpURLConnection request;
		int newPrice = -1;
		try {
			
			request = (HttpURLConnection) url.openConnection();
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
		}
		
		return gePrice;
	}
	
	public int getNumberOfPages() {
		return numberOfPages;
	}
	
	public int getNumberOfPagesUpdated() {
		return numberOfPagesUpdated;
	}
	
	protected void stopRunning() {
		running = false;
		
	}
}
