package com.faceyspacies.GEMWbot;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The UpdateChecker checks to see if the price on the wiki for an item is the same
 * as the price in the API for an item. If it isn't, begin GrandExchangeUpdater. 
 * @author Ty
 *
 */
public class UpdateChecker extends BaseWikiTask {
	
	/**
	 * Our constructor.
	 * @see com.faceyspacies.GEMWbot.BaseWikiTask#BaseWikiTask(GEMWbot ircInstance)
	 * @param ircInstance
	 */
	public UpdateChecker(GEMWbot ircInstance) {
		super(ircInstance);
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
			System.out.println("[INFO] Starting update checker");
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
	protected void Start() {
		while(running) {
			try {
				Thread.sleep(600000); // 10 minutes
				
				// ensure we're getting the freshest of prices
				timestamp = null;
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
	
	@Override
	/**
	 * Stops the update checker. Does not stop a GE update if it has started. 
	 */
	protected void stopRunning() {
		running = false;
		ircInstance.setCheckerToNull();
	}
}
