package com.faceyspacies.GEMWbot;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.login.LoginException;

import org.wikipedia.Wiki;

import com.faceyspacies.GEMWbot.Exceptions.PriceIsZeroException;
import com.faceyspacies.GEMWbot.Holders.GEPrice;
import com.faceyspacies.GEMWbot.Holders.UpdateResult;

public class GrandExchangeUpdater extends BaseWikiTask {
	// This class will be run from GEMWbot to start the GE updates
	// It will run it its own thread and once it is done, it will be destroyed
	// so a new object will be created every GE Update
	
	private int numberOfPages;
	private int numberOfPagesUpdated;

	private boolean haveWarnedOnPriceOfZero;
	
	private VolumeHandler volumes;
	
	private String GEPricesString;
	
	GrandExchangeUpdater(GEMWbot ircInstance) throws Exception {
		super(ircInstance);
		
		if(wikiBot == null) {
			throw new Exception("Failed to init");
		}
		
		GEPricesString = "return {\n";
		
		haveWarnedOnPriceOfZero = false;
		
	}
	
	@Override
	protected void Start() {

		System.out.println("[INFO] GE Updater has Started");
		
		boolean couldLogin = Login();
		if(!couldLogin) {
			return; // abort, message was sent in function
		}
		
		volumes = new VolumeHandler();
		volumes.getVolumes();
		if(volumes.isEmpty()) {
		}
		
		String[] pages;
		System.out.println("getting pages");
		pages = getPages(wikiBot);
		for(int i = 0; i < pages.length; i++ ) {
			pages[i] = pages[i].replace("Exchange:", "");
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
					Thread.sleep(2500);
				} catch (InterruptedException e) {
					ircInstance.getTellBotInstance().addTell("tybot", "wikia/vstf/TyA", "UNABLE TO SLEEP; I AM FREAKING OUT RIGHT NOW", null);
				}
			} else { // if no longer running, stop loop and end
				return;
			}
		}
		GEPricesString += "  ['TyBot updated'] = " + getTodaysEpochTimestamp() + ",\n";
		GEPricesString = GEPricesString.substring(0, GEPricesString.length() - 2);
		GEPricesString += "\n}"; 
		
		try {
			wikiBot.edit("Module:GEPrices/data", GEPricesString, "updating thing");
		} catch (LoginException | IOException e) {
			System.out.println("Failed to update GEPrices :(");
		}
		
		updateTradeIndexData();
		
		updateLogPage();

		ircInstance.setUpdateTaskToNull();
		ircInstance.startChecker();
		ircInstance.sendMessage(ircChannel, "GE Updates complete!"); // if we make it to end, we did it
	}

	private UpdateResult doUpdates(String page) {
		int failures;
		failures = 0;
		
		while(failures < 3) {
			try {
				return updateModulePage(page);
			} catch (IOException e) {
				failures++;
				if(failures == 3) {
					return new UpdateResult("network failure; " + e.getClass() + " " +  e.getMessage(), false);
				}
				Login(); // If we are logged out, it will give us a IOException with HookAborted
				
			} catch (LoginException e) {
				failures++;
				if(failures == 3) {
					return new UpdateResult("failed to stay logged in", false);
				}
				Login();
			} catch (PriceIsZeroException e) {
				if(!haveWarnedOnPriceOfZero) {
					ircInstance.getTellBotInstance().addTell("tybot", "@wikia/vstf/TyA;@Wikipedia/The-Mol-Man", "HAD A ZERO PRICE; FREAKING OUT RIGHT NOW. CHECK LOG.", null);
					haveWarnedOnPriceOfZero = true;
				}
				
				return new UpdateResult("Price was Zero", false);
			} catch (Exception e) {
				failures++;
				if(failures == 3) {
					return new UpdateResult("unknown exception; " + e.getClass() + " " +  e.getMessage(), false);
				}
			}
		}
		
		return new UpdateResult("unknown error", false);
	}
	
	private String[] getPages(Wiki wikiBot) {
		
		int failures = 0;
		
		while(failures < 3) {
			try {
				// test wiki does not have namespace 112, but main wiki does
				String[] pages = wikiBot.getCategoryMembers("Grand Exchange", 112);
				//String[] pages = wikiBot.getCategoryMembers("Grand Exchange");
				return pages;
			} catch (IOException e1) {
				failures++;
				if(failures == 3) {
					ircInstance.getTellBotInstance().addTell("tybot", "wikia/vstf/TyA", "I was unable to get the page list.", null);
					return null;
				}
			}
		} 
		
		return null; // shouldn't reach here, but if we do assume we have failed. 
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
					return new UpdateResult("network failure; " + e.getClass() + " " + e.getMessage(), false);
				}
				Login();
				
			} catch (LoginException e) {
				failures++;
				if(failures == 3) {
					return new UpdateResult("failed to stay logged in", false);
				}
				Login();
			} catch (Exception e) {
				failures++;
				if(failures == 3) {
					return new UpdateResult("unknown exception; " + e.getClass() + "\n" +  e.getStackTrace(), false);
				}
			}
		}
		
		return new UpdateResult("unknown error", false);
	}
	
	private UpdateResult updateData(String pageName, GEPrice price) throws LoginException, IOException {
		String pageContent;
		String volume;
		boolean doesExist;
		boolean hasDocTemplate = false;
		
		doesExist = (boolean) wikiBot.getPageInfo(pageName).get("exists");
		if(!doesExist) {
			pageContent = "{{ExcgData|name={{subst:PAGENAME}}|size={{{size|}}}|\n";
		} else {
			pageContent = wikiBot.getPageText(pageName);
			
			//Page may have been vandalised, so just add the data that would've been added here so it can be manually added later
			// If there is no volume, it will say null
			if(pageContent.length() == 0) {
				return new UpdateResult("page is empty; " + price.getTimestamp() + ":" + price.getPrice() 
						+ ((price.getId() != null) ? volumes.getVolumeFor(price.getId()) : ""), false);
				
			}
			
			if(pageContent.contains("<noinclude>{{/doc}}</noinclude>")) {
				hasDocTemplate = true;
				pageContent = pageContent.replaceAll("\\<noinclude\\>\\{\\{\\/doc\\}\\}\\</noinclude\\>", "");
			}
			
			pageContent = pageContent.replaceAll("\\n}}", ",");
			
		}
		
		if(price.getId() == null) {
			volume = null;
		} else {
			volume = volumes.getVolumeFor(price.getId());
		}
		
		if(volume == null) {
			pageContent += price.getTimestamp() + ":" + price.getPrice() + "\n}}";
		} else {
			pageContent += price.getTimestamp() + ":" + price.getPrice() + ":" + volume  + "\n}}";
		}
		
		if(hasDocTemplate) {
			pageContent += "<noinclude>{{/doc}}</noinclude>";
		}

		wikiBot.edit(pageName, pageContent, "Updating price data");
		
		return new UpdateResult("", true);
	}
	
	@SuppressWarnings("unused")
	private String[] getModulePages(Wiki wiki) {
		int failures = 0;
		
		while(failures < 3) {
			try {
				String[] pages = wiki.listPages("Module:Exchange/", null, 828, -1, -1);
				return pages;
			} catch (IOException e1) {
				failures++;
				if(failures == 3) {
					ircInstance.getTellBotInstance().addTell("tybot", "wikia/vstf/TyA", "I was unable to get the module page list.", null);
					return null;
				}
			}
		}
		
		return null;
	}
	
	private UpdateResult updateModulePage(String pageName) throws IOException, LoginException, PriceIsZeroException, Exception {
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
		
		for(int i = 0; i < 3; i++) {
			try {
				newPrice = loadCurPrice(itemID);
			} catch (MalformedURLException e) {
				System.out.println("[ERROR] Item ID " + itemID + " is invalid!");
				return new UpdateResult("item id is invalid", false);
			}
			
			if(newPrice == null) {
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {}
				
				newPrice = loadCurPrice(itemID);
			} else if(newPrice.getPrice().equalsIgnoreCase("0")) {
				throw new PriceIsZeroException();
			} else {
				break;
			}
		}
		
		// if after three tries we still cannot get a price, give up 
		if(newPrice == null)
			return new UpdateResult("unable to fetch price", false);
		
		String volume = volumes.getVolumeFor(newPrice.getId());
		if(volume != null) { // WE HAVE VOLUME DATA, WOO!
			// since we don't need the data again, it is safe to just remove it
			if(pageContent.indexOf("volumeDate") != -1) {
				pageContent = pageContent.replaceAll("volume\\s*=.*\\n", "volume     = " + volume + ",\n");
				pageContent = pageContent.replaceAll("volumeDate\\s*=.*\\n", "volumeDate = '~~~~~',\n");
			} else {
				// newly has volume
				String newVolText;
				newVolText = "    volume     = " + volume +",\n";
				newVolText += "    volumeDate = '~~~~~',\n";
				newVolText += "    icon";
				pageContent = pageContent.replaceAll("    icon", newVolText);
			}
		}
		
		if(!GEPricesString.contains(pageName.substring(16))) {
			GEPricesString += "  ['" + pageName.substring(16).replace("'", "\\'").replace("_"," ") + "'] = " + newPrice.getPrice() + ",\n";
		}
		
		// remove data that we don't need
		pageContent = pageContent.replaceAll("\\s*last\\s*=.*\n", "\n");
		pageContent = pageContent.replaceAll("\\s*lastDate\\s*=.*\n", "\n");
		
		//replaces Price/Date with the new date and pushes the old price down to the LastPrice/LastDate param 
		// which we removed above.
		pageContent = pageContent.replaceAll("price\\s*=", String.format("price      = %d,\n    last       =", Integer.parseInt(newPrice.getPrice())));
		pageContent = pageContent.replaceAll(" date\\s*=", " date       = '~~~~~',\n    lastDate   =");
		
		wikiBot.edit(pageName, pageContent, "Updating price");
		
		addToLog(doDataUpdate(pageName, newPrice), pageName + "/Data");
		return new UpdateResult("", true);
	}
	
	private UpdateResult updateModuleData(String pageName, GEPrice price) throws LoginException, IOException, Exception {
		String pageContent;
		String volume;
		boolean doesExist;
		
		doesExist = (boolean) wikiBot.getPageInfo(pageName).get("exists");
		if(!doesExist) {
			pageContent = "return {\n";
		} else {
			pageContent = wikiBot.getPageText(pageName);
			
			//Page may have been vandalised, so just add the data that would've been added here so it can be manually added later
			// If there is no volume, it will say null
			if(pageContent.length() == 0) {
				return new UpdateResult("page is empty; " + price.getTimestamp() + ":" + price.getPrice() 
						+ ((price.getId() != null) ? volumes.getVolumeFor(price.getId()) : ""), false);
				
			}
			
			pageContent = pageContent.replaceAll("\\n}", ",");
			
		}
		
		if(price.getId() == null) {
			volume = null;
		} else {
			volume = volumes.getVolumeFor(price.getId());
		}
		
		if(volume == null) {
			pageContent += "    '" + price.getTimestamp() + ":" + price.getPrice() + "'\n}";
		} else {
			pageContent += "    '" + price.getTimestamp() + ":" + price.getPrice() + ":" + volume + "'\n}";
		}

		wikiBot.edit(pageName, pageContent, "Updating price data");
		
		return new UpdateResult("", true);
	}
	
	private void updateTradeIndexData() {
		String parsedContent;

		String[] indexPages = {"GE Common Trade Index", "GE Discontinued Rare Index", 
									"GE Food Index", "GE Herb Index",
									"GE Log Index", "GE Metal Index", 
									"GE Rune Index"};
		for(int i = 0; i < 3; i++) {
			try {
				wikiBot.purge(false, indexPages);
				break;
			} catch (IOException e) {
				System.out.println("[ERROR] Unable to purge IndexPages due to IOException");
			}
		}
		
		String currentDayTimestamp = getTodaysEpochTimestamp();
		
		
		if(currentDayTimestamp == null) {
			return;
		}
		
		for(String page: indexPages) {
			// Only exists in template namespace
			try {
				parsedContent = wikiBot.parsePage("Template:" + page);
			} catch (IOException e) {
				System.out.println("[ERROR] Unable to update data on " + page );
				continue;
			}
			
			// We are just interested in the number
			parsedContent = parsedContent.substring(0, parsedContent.indexOf("<"));
			
			doTradeIndexUpdate("Module:Exchange/" + page.replaceAll("GE ",  ""), currentDayTimestamp, parsedContent);

		}
	}
	
	private void doTradeIndexUpdate(String page, String currentDayTimestamp, String parsedContent) {
		GEPrice price = null;
		System.out.println(page);
		
		price = new GEPrice(currentDayTimestamp, parsedContent);
		
		for(int i = 0; i < 3; i++) {
			try {
				if(page.indexOf("Module:Exchange/") == -1) {
					addToLog(updateData(page + "/Data", price), page + "/Data");
					break;
				} else {
					addToLog(updateModuleData(page + "/Data", price), page + "/Data");
					break;
				}
			} catch (IOException e) {
				System.out.println("[ERROR] Unable to update data on " + page );
			} catch (LoginException e) {
				System.out.println("[ERROR] Unable to login");
			} catch (Exception e) {
				System.out.println("[ERROR] Unknown exception: " + e.getClass());
			}
		}
	}
	
    private String getTodaysEpochTimestamp() {
    	
		try {
			GEPrice price;
			// 245 is the itemID for Wine of Zamorak
			// The actual ID isn't important as long as it is a valid one
			// because we just grab the timestamp off of it
			price = loadCurPrice("245");
			return price.getTimestamp();
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
    	
	}
	
	public int getNumberOfPages() {
		return numberOfPages;
	}
	
	public int getNumberOfPagesUpdated() {
		return numberOfPagesUpdated;
	}
}
