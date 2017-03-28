package com.faceyspacies.GEMWbot;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.login.LoginException;

import org.wikipedia.Wiki;

import com.faceyspacies.GEMWbot.Exceptions.PriceIsZeroException;
import com.faceyspacies.GEMWbot.Holders.GEPrice;
import com.faceyspacies.GEMWbot.Holders.UpdateResult;

/**
 * The object that performs Grand Exchange Updates. It extends the BaseWikiTask class.
 * A new object should be created for each Grand Exchange Update.
 * @author Ty
 *
 */
public class GrandExchangeUpdater extends BaseWikiTask {
	
	/**
	 * The number of pages we're going to try to update.
	 */
	private int numberOfPages;
	
	/**
	 * The number of pages we have already updated, or tried to update.
	 */
	private int numberOfPagesUpdated;

	/**
	 * Boolean on if we have received a price of 0 from the API. If we receive a price of 0, there
	 * is an issue with the API. However, we don't want to spam IRC/Ty with the error. 
	 */
	private boolean haveWarnedOnPriceOfZero;
	
	/**
	 * VolumeHandler which contains the trade volume data available from the RuneScape website. Only 100 items have volume data/day.
	 */
	private VolumeHandler volumes;
	
	/**
	 * A String that contains all the GE items/prices that we were able to update. It is used to populate Module:GEPrices/data
	 */
	private String GEPricesString;
	
	/**
	 * Our constructor. Creates the wikiBot parameter, GEPricesString, and haveWarnedOnPriceOfZero.
	 * @param ircInstance The GEMWbot object. Used so we have IRC access inside the GrandExchangeUpdater.
	 * @throws Exception If we encounter any issue that makes it up here, we throw an Exception which 
	 * should be caught 
	 */
	GrandExchangeUpdater(GEMWbot ircInstance) throws Exception {
		super(ircInstance);
		
		if(wikiBot == null) {
			throw new Exception("Failed to init");
		}
		
		GEPricesString = "return {\n";
		
		haveWarnedOnPriceOfZero = false;
		
	}
	
	/**
	 * The main loop of the GrandExchangeUpdater. It logs in, gets the pages,
	 * updates the pages, reports any errors, update Trade Indexes, and
	 * updates Module:GEPrices/data.
	 * 
	 * @see com.faceyspacies.GEMWbot.BaseWikiTask#Start()
	 */
	@Override
	protected void Start() {

		System.out.println("[INFO] GE Updater has Started");
		
		boolean couldLogin = Login();
		if(!couldLogin) {
			return; // abort, message was sent in function
		}
		
		// create the volume handler and then actually get the volume data.
		volumes = new VolumeHandler();
		volumes.getVolumes();
		
		String[] pages;
		System.out.println("getting pages");
		pages = getPages(wikiBot);
		for(int i = 0; i < pages.length; i++ ) {
			// Pages grabbed are Exchange:itemName, so we
			// want to remove Exchange:
			pages[i] = pages[i].replace("Exchange:", "");
		}
		
		numberOfPages = pages.length;
		numberOfPagesUpdated = 0;
		
		List<String> itemsRemovedFromGE = getItemsRemovedFromGE();
		
		for(int i = 0; i < pages.length; i++) {
		
			// update the pages, once it is done add to log
			if(running) {
				numberOfPagesUpdated++;
				if(pages[i].contains("/Data")) // we update /Data pages as part of updating the main page
					continue;
				
				if(itemsRemovedFromGE.contains(pages[i]))
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
		
		// Add an "TyBot updated" value to the GEPricesString to tell how fresh the data is
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

	/**
	 * Try 3 times to update the page. 
	 * @param page The name of the item we want to update
	 * @return UpdateResult telling of status
	 */
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
					ircInstance.getTellBotInstance().addTell("tybot", "@wikia/vstf/TyA", "HAD A ZERO PRICE; FREAKING OUT RIGHT NOW. CHECK LOG.", null);
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
		
		// We really should never get here
		return new UpdateResult("unknown error", false);
	}
	
	/**
	 * Gets all the Exchange pages. If we have an error, try again 3 times. 
	 * @param wikiBot the Wiki object we are using
	 * @return String array with all the pages in it. null in case of error. 
	 */
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
	
	/**
	 * Helper function to handle the retries to perform the data updates. Tries to perform them 3 times if we 
	 * have an Exception. 
	 * @param pageName The full page name we want to update
	 * @param price GEPrice object containing the price and timestamp data.
	 * @return
	 */
	private UpdateResult doDataUpdate(String pageName, GEPrice price) {
		int failures;
		failures = 0;
		pageName += "/Data";
		
		while(failures < 3) {
			try {
				// all items that do not throw exceptions are not recoverable
				return updateModuleData(pageName, price);
				
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
	
	/**
	 * Actually updates the price data on the Module:Exchange/item page
	 * @param pageName The name of the item we want to update. Also accepts full page name.
	 * @return UpdateResult with success info
	 * @throws IOException
	 * @throws LoginException
	 * @throws PriceIsZeroException
	 * @throws Exception
	 */
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
		
		// try to load the price up to 3 times
		for(int i = 0; i < 3; i++) {
			try {
				newPrice = loadCurPrice(itemID);
			} catch (MalformedURLException e) {
				System.out.println("[ERROR] Item ID " + itemID + " is invalid!");
				return new UpdateResult("item id is invalid", false);
			}
			
			// if it is null, we couldn't get the price :(
			if(newPrice == null) {
				try {
					// wait 3 seconds to avoid rate-limiting
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
	
	/**
	 * Actually updates the /data module page. 
	 * @param pageName The full page name of the module we are updating
	 * @param price The price of the item
	 * @return UpdateResult with success information
	 * @throws LoginException
	 * @throws IOException
	 * @throws Exception
	 */
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
	
	/**
	 * Purges the index pages and for each page, gets their current index value. Calls
	 * doTradeIndexUpdate to actually perform the update.
	 */
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
	
	/**
	 * Actually calls the method that performs the page update. Gives it 3 attempts to succeed. 
	 * @param page Module:Exchange/TradeIndexNameNoGE
	 * @param currentDayTimestamp The timestamp to use on the page
	 * @param parsedContent The current index value
	 */
	private void doTradeIndexUpdate(String page, String currentDayTimestamp, String parsedContent) {
		GEPrice price = null;
		
		// To make things easy and reuseable, we're going to act like it is just updating a price. 
		price = new GEPrice(currentDayTimestamp, parsedContent);
		
		for(int i = 0; i < 3; i++) {
			try {
				addToLog(updateModuleData(page + "/Data", price), page + "/Data");
			} catch (IOException e) {
				System.out.println("[ERROR] Unable to update data on " + page );
			} catch (LoginException e) {
				System.out.println("[ERROR] Unable to login");
			} catch (Exception e) {
				System.out.println("[ERROR] Unknown exception: " + e.getClass());
			}
		}
	}
	
    /**
     * Gets today's Epoch Timestamp from Jagex. Might not actually be today's, but it is what
     * is used by their Graph API. 
     * @return Epoch Timestamp string from Jagex's Graph API
     */
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
	
	/**
	 * A Getter method which returns the number of pages we are working on
	 * @return Number of pages we're working on
	 */
	public int getNumberOfPages() {
		return numberOfPages;
	}
	
	/**
	 * A Getter method which returns the number of pages we have updated.
	 * @return Number of pages updated.
	 */
	public int getNumberOfPagesUpdated() {
		return numberOfPagesUpdated;
	}
	
	/**
	 * Fetches the items from User:TyBot/itemsRemovedFromGE. These are items that are not available on the GE, but have
	 * an Exchange: page for historical reference. This is to avoid wasting time on items that we know will not work. 
	 * @return A List of item names to not worry about. 
	 */
	private List<String> getItemsRemovedFromGE() {
		try {
			String items = wikiBot.getPageText("User:TyBot/itemsRemovedFromGE");
			ArrayList<String> temp = new ArrayList<String>();
			String[] itemsarray = items.split("\n");
			for(String item: itemsarray) {
				temp.add("Exchange:" + item);
			}
			return temp;
		} catch (IOException e) {
			return new ArrayList<String>();
		}
		
	}
}
