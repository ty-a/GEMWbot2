package com.faceyspacies.GEMWbot;

import java.io.IOException;
import java.net.MalformedURLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.login.LoginException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.wikipedia.Wiki;

import com.faceyspacies.GEMWbot.Exceptions.PriceIsZeroException;
import com.faceyspacies.GEMWbot.Holders.GEPrice;
import com.faceyspacies.GEMWbot.Holders.UpdateResult;

/**
 * The object that performs Grand Exchange Updates. It extends the BaseWikiTask class. A new object
 * should be created for each Grand Exchange Update.
 * 
 * For each item, the bot makes several requests to the MediaWiki Server. 1 request to get the page
 * text for the Module:Exchange/Item and Module:Exchange/Item/Data pages 2 requests to actually make
 * the edit
 * 
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
   * Boolean on if we have received a price of 0 from the API. If we receive a price of 0, there is
   * an issue with the API. However, we don't want to spam IRC/Ty with the error.
   */
  private boolean haveWarnedOnPriceOfZero;

  /**
   * VolumeHandler which contains the trade volume data available from the RuneScape website. Only
   * 100 items have volume data/day.
   */
  private VolumeHandler volumes;

  /**
   * A String that contains all the GE items/prices that we were able to update. It is used to
   * populate Module:GEPrices/data
   */
  private String GEPricesString;

  private String formattedDate;

  /**
   * Our constructor. Creates the wikiBot parameter, GEPricesString, and haveWarnedOnPriceOfZero.
   * 
   * @param ircInstance The GEMWbot object. Used so we have IRC access inside the
   *        GrandExchangeUpdater.
   * @throws Exception If we encounter any issue that makes it up here, we throw an Exception which
   *         should be caught
   */
  GrandExchangeUpdater(GEMWbot ircInstance) throws Exception {
    super(ircInstance);

    if (wikiBot == null) {
      throw new Exception("Failed to init");
    }

    GEPricesString = "return {\n";

    haveWarnedOnPriceOfZero = false;

  }

  /**
   * The main loop of the GrandExchangeUpdater. It logs in, gets the pages, updates the pages,
   * reports any errors, update Trade Indexes, and updates Module:GEPrices/data.
   * 
   * @see com.faceyspacies.GEMWbot.BaseWikiTask#Start()
   */
  @Override
  protected void Start() {

    System.out.println("[INFO] GE Updater has Started");

    boolean couldLogin = Login();
    if (!couldLogin) {
      return; // abort, message was sent in function
    }

    wikiBot.setAssertionMode(Wiki.ASSERT_BOT);

    // create the volume handler and then actually get the volume data.
    volumes = new VolumeHandler();
    volumes.getVolumes();

    String[] pages;
    System.out.println("getting pages");
    pages = getPages(wikiBot);
    for (int i = 0; i < pages.length; i++) {
      // Pages grabbed are Exchange:itemName, so we
      // want to remove Exchange:
      pages[i] = pages[i].replace("Exchange:", "");
    }

    // TODO: refactor this
    getTodaysEpochTimestamp(); // this sets timestamp in a round-a-bout way by since loadCurPrice
                               // sets the timestamp field probs should change this
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm:ss");
    ZonedDateTime zdt =
        ZonedDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(timestamp)), ZoneId.of("UTC"));
    formattedDate = dateFormatter.format(zdt) + " (UTC)";

    if (timestamp == null) {
      main.sendMessageToTy("timestamp is null");
      System.out.println("timestamp is null");
      System.exit(0);
    }

    numberOfPages = pages.length;
    numberOfPagesUpdated = 0;

    // List<String> itemsRemovedFromGE = getItemsRemovedFromGE();

    for (int i = 0; i < pages.length; i++) {

      // update the pages, once it is done add to log
      if (running) {
        numberOfPagesUpdated++;
        if (pages[i].contains("/Data")) // we update /Data pages as part of updating the main page
          continue;

        if (pages[i].contains("/doc")) // don't care about doc pages
          continue;

        if (pages[i].contains("/test")) // don't care about test pages
          continue;

        // if (itemsRemovedFromGE.contains(pages[i]))
        // continue;

        addToLog(doUpdates(pages[i]), pages[i]);

        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          main.sendMessageToTy("UNABLE TO SLEEP; I AM FREAKING OUT RIGHT NOW");
        }
      } else { // if no longer running, stop loop and end
        return;
      }
    }

    // Add an "TyBot updated" value to the GEPricesString to tell how fresh the data is
    GEPricesString += "  ['TyBot updated'] = " + timestamp + ",\n";
    GEPricesString = GEPricesString.substring(0, GEPricesString.length() - 2);
    GEPricesString += "\n}";

    for (int i = 0; i < 3; i++) {
      try {
        String oldprices = wikiBot.getPageText("Module:GEPrices/data");
        wikiBot.edit("Module:GEPrices/data", GEPricesString, "updating thing");
        wikiBot.edit("Module:LastPrices/data", oldprices, "updating other thing");
      } catch (LoginException | IOException | AssertionError e) {
        System.out.println("Failed to update GEPrices :(");
      }
    }

    if (mode.equals("rs"))
      updateTradeIndexData();

    updateLogPage();

    main.setUpdateTaskToNull();
    main.startChecker();
    main.setLastGEDate(new Date());
    main.sendMessageToWikiChannel("GE Updates complete!"); // if we make it to end, we did it
  }

  /**
   * Try 3 times to update the page.
   * 
   * @param page The name of the item we want to update
   * @return UpdateResult telling of status
   */
  private UpdateResult doUpdates(String page) {
    int failures;
    failures = 0;

    while (failures < 3) {
      try {
        System.out.println("Updating " + page);
        return updateModulePage(page);
      } catch (IOException e) {
        failures++;
        if (failures == 3) {
          return new UpdateResult("network failure; " + e.getClass() + " " + e.getMessage(), false);
        }
        Login(); // If we are logged out, it will give us a IOException with HookAborted

      } catch (LoginException | AssertionError e) {
        failures++;
        if (failures == 3) {
          return new UpdateResult(e.getMessage(), false);
        }
      } catch (PriceIsZeroException e) {
        if (!haveWarnedOnPriceOfZero) {
          main.sendMessageToTy("HAD A ZERO PRICE; FREAKING OUT RIGHT NOW. CHECK LOG.");
          haveWarnedOnPriceOfZero = true;
        }

        return new UpdateResult("Price was Zero", false);
      } catch (Exception e) {
        failures++;
        if (failures == 3) {
          return new UpdateResult("unknown exception; " + e.getClass() + " " + e.getMessage(),
              false);
        }
      }
    }

    // We really should never get here
    return new UpdateResult("unknown error", false);
  }

  /**
   * Gets all the Exchange pages. If we have an error, try again 3 times.
   * 
   * @param wikiBot the Wiki object we are using
   * @return String array with all the pages in it. null in case of error.
   */
  private String[] getPages(Wiki wikiBot) {

    int failures = 0;

    while (failures < 3) {
      try {
        String[] pages;
        pages = wikiBot.listPages("Module:Exchange/", null, 828, -1, -1, false);

        return pages;
      } catch (IOException e1) {
        failures++;
        if (failures == 3) {
          main.sendMessageToTy("I was unable to get the page list.");
          return null;
        }
      } catch (AssertionError e) {
        failures++;
        Login();
      }
    }

    return null; // shouldn't reach here, but if we do assume we have failed.
  }

  /**
   * Helper function to handle the retries to perform the data updates. Tries to perform them 3
   * times if we have an Exception.
   * 
   * @param pageName The full page name we want to update
   * @param pageContent
   * @param price GEPrice object containing the price and timestamp data.
   * @return
   */
  private UpdateResult doDataUpdate(String pageName, String pageContent, GEPrice price) {
    int failures;
    failures = 0;
    pageName += "/Data";

    while (failures < 3) {
      try {
        // all items that do not throw exceptions are not recoverable
        return updateModuleData(pageName, pageContent, price);

      } catch (IOException e) {
        failures++;
        if (failures == 3) {
          return new UpdateResult("network failure; " + e.getClass() + " " + e.getMessage(), false);
        }
        Login();

      } catch (LoginException | AssertionError e) {
        failures++;
        if (failures == 3) {
          return new UpdateResult(e.getMessage(), false);
        }
        Login();
      } catch (Exception e) {
        failures++;
        if (failures == 3) {
          return new UpdateResult("unknown exception; " + e.getClass() + "\n" + e.getStackTrace(),
              false);
        }
      }
    }

    return new UpdateResult("unknown error", false);
  }

  /**
   * Actually updates the price data on the Module:Exchange/item page
   * 
   * @param pageName The name of the item we want to update. Also accepts full page name.
   * @return UpdateResult with success info
   * @throws IOException
   * @throws LoginException
   * @throws PriceIsZeroException
   * @throws Exception
   */
  private UpdateResult updateModulePage(String pageName)
      throws IOException, LoginException, PriceIsZeroException, Exception {
    String itemID = null;
    GEPrice newPrice = null;

    pageName = pageName.replace(" ", "_");

    if (pageName.indexOf("Module:Exchange/") == -1) {
      pageName = "Module:Exchange/" + pageName;
    }

    String[] pagesContents = wikiBot.getPageText(new String[] {pageName, pageName + "/Data"});
    String pageContent = pagesContents[0];
    if (pageContent == null) {
      // this module page does not exist somehow?
      System.out.println("[ERROR] " + pageName + " doesn't exist!");
      return new UpdateResult("item id is invalid", false);
    }

    if (pageContent.length() == 0) {
      return new UpdateResult("page is empty", false);
    }

    Pattern itemIDregex = Pattern.compile("itemId\\s*= (\\d+)");
    Matcher itemIDMatcher = itemIDregex.matcher(pageContent);

    if (itemIDMatcher.find()) {
      itemID = itemIDMatcher.group(1);
    } else {
      System.out.println("[ERROR] unable to load item id");
      return new UpdateResult("unable to load item id", false);
    }

    // try to load the price up to 3 times
    for (int i = 0; i < 3; i++) {
      try {
        if (mode.equals("rs")) {
          newPrice = loadCurPriceRS(itemID);
        } else {
          newPrice = loadCurPriceOS(itemID);
        }

      } catch (MalformedURLException e) {
        System.out.println("[ERROR] Item ID " + itemID + " is invalid!");
        return new UpdateResult("item id is invalid", false);
      }

      // if it is null, we couldn't get the price :(
      if (newPrice == null) {
        try {
          // wait 3 seconds to avoid rate-limiting
          if (mode.equals("os"))
            Thread.sleep(4000);
          else
            Thread.sleep(3000);
        } catch (InterruptedException e) {
        }

        if (mode.equals("rs")) {
          newPrice = loadCurPriceRS(itemID);
        } else {
          newPrice = loadCurPriceOS(itemID);
        }
      } else if (newPrice.getPrice().equalsIgnoreCase("0")) {
        throw new PriceIsZeroException();
      } else {
        break;
      }
    }

    // if after three tries we still cannot get a price, give up
    if (newPrice == null) {
      // we still want to include the item in our giant prices module though
      // so grab it off of the page
      // if we can't get it off the page, I guess yolo since we have nothing anyway
      Pattern priceregex = Pattern.compile("price\\s*= (\\d+)");
      Matcher priceMatcher = priceregex.matcher(pageContent);

      if (priceMatcher.find()) {
        if (!GEPricesString.contains(pageName.substring(16))) {
          GEPricesString += "  ['" + pageName.substring(16).replace("'", "\\'").replace("_", " ")
              + "'] = " + priceMatcher.group(1) + ",\n";
        }
      }
      return new UpdateResult("unable to fetch price", false);
    }

    String volume;
    if (mode.equals("rs")) {
      volume = volumes.getVolumeFor(newPrice.getId());
    } else {
      volume = newPrice.getVolume();
    }

    if (volume != null) { // WE HAVE VOLUME DATA, WOO!
      // since we don't need the data again, it is safe to just remove it
      if (pageContent.indexOf("volumeDate") != -1) {
        pageContent = pageContent.replaceAll("volume\\s*=.*\\n", "volume     = " + volume + ",\n");
        pageContent = pageContent.replaceAll("volumeDate\\s*=.*\\n",
            "volumeDate = '" + formattedDate + "',\n");
      } else {
        // newly has volume
        String newVolText;
        newVolText = "    volume     = " + volume + ",\n";
        newVolText += "    volumeDate = '" + formattedDate + "',\n";
        newVolText += "    icon";
        pageContent = pageContent.replaceAll("    icon", newVolText);
      }
    }

    if (!GEPricesString.contains(pageName.substring(16))) {
      GEPricesString += "  ['" + pageName.substring(16).replace("'", "\\'").replace("_", " ")
          + "'] = " + newPrice.getPrice() + ",\n";
    }

    // remove data that we don't need
    pageContent = pageContent.replaceAll("\\s*last\\s*=.*\n", "\n");
    pageContent = pageContent.replaceAll("\\s*lastDate\\s*=.*\n", "\n");

    // replaces Price/Date with the new date and pushes the old price down to the LastPrice/LastDate
    // param
    // which we removed above.
    pageContent = pageContent.replaceAll("price\\s*=",
        String.format("price      = %d,\n    last       =", Integer.parseInt(newPrice.getPrice())));
    pageContent = pageContent.replaceAll(" date\\s*=",
        " date       = '" + formattedDate + "',\n    lastDate   =");

    wikiBot.edit(pageName, pageContent, "Updating price");

    addToLog(doDataUpdate(pageName, pagesContents[1], newPrice), pageName + "/Data");
    return new UpdateResult("", true);
  }

  /**
   * Actually updates the /data module page.
   * 
   * @param pageName The full page name of the module we are updating
   * @param pageContent
   * @param price The price of the item
   * @return UpdateResult with success information
   * @throws LoginException
   * @throws IOException
   * @throws Exception
   */
  private UpdateResult updateModuleData(String pageName, String pageContent, GEPrice price)
      throws LoginException, IOException, Exception {
    String volume;

    // if page doesn't exist, this is null
    if (pageContent == null) {
      pageContent = "return {\n";
    } else {
      // We already have page content from previous query, making this unnecessary
      // pageContent = wikiBot.getPageText(pageName);

      // Page may have been vandalised, so just add the data that would've been added here so it can
      // be manually added later
      // If there is no volume, it will say null
      if (pageContent.length() == 0) {
        return new UpdateResult("page is empty; " + price.getTimestamp() + ":" + price.getPrice()
            + ((price.getId() != null) ? volumes.getVolumeFor(price.getId()) : ""), false);

      }

      pageContent = pageContent.replaceAll("\\n}", ",\n");

    }

    if (price.getId() == null) {
      volume = null;
    } else {
      if (mode.equals("rs")) {
        volume = volumes.getVolumeFor(price.getId());
      } else {
        volume = price.getVolume();
      }
    }

    if (volume == null) {
      pageContent += "    '" + price.getTimestamp() + ":" + price.getPrice() + "'\n}";
    } else {
      pageContent +=
          "    '" + price.getTimestamp() + ":" + price.getPrice() + ":" + volume + "'\n}";
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

    String[] indexPages = {"GE Common Trade Index", "GE Discontinued Rare Index", "GE Food Index",
        "GE Herb Index", "GE Log Index", "GE Metal Index", "GE Rune Index"};
    for (int i = 0; i < 3; i++) {
      try {
        wikiBot.purge(false, indexPages);
        break;
      } catch (IOException e) {
        System.out.println("[ERROR] Unable to purge IndexPages due to IOException");
      } catch (AssertionError e) {
        System.out.println("Error: Unable to purge IndexPages due to AssertionError");
      }
    }

    for (String page : indexPages) {
      // Only exists in template namespace
      try {
        Map<String, Object> content = new HashMap<>();
        content.put("title", "Template:" + page);
        parsedContent = wikiBot.parse(content, -1, true);
      } catch (IOException e) {
        System.out.println("[ERROR] Unable to update data on " + page);
        continue;
      }

      // We are just interested in the number
      parsedContent =
          parsedContent.substring(parsedContent.indexOf("<p>") + 3, parsedContent.indexOf("</p>"));

      doTradeIndexUpdate("Module:Exchange/" + page.replaceAll("GE ", ""), timestamp, parsedContent);

    }
  }

  /**
   * Actually calls the method that performs the page update. Gives it 3 attempts to succeed.
   * 
   * @param page Module:Exchange/TradeIndexNameNoGE
   * @param currentDayTimestamp The timestamp to use on the page
   * @param parsedContent The current index value
   */
  private void doTradeIndexUpdate(String page, String currentDayTimestamp, String parsedContent) {
    GEPrice price = null;

    // To make things easy and reuseable, we're going to act like it is just updating a price.
    price = new GEPrice(currentDayTimestamp, parsedContent);

    for (int i = 0; i < 3; i++) {
      try {
        addToLog(updateModuleData(page + "/Data", wikiBot.getPageText(page + "/Data"), price),
            page + "/Data");
        break;
      } catch (IOException e) {
        System.out.println("[ERROR] Unable to update data on " + page);
      } catch (LoginException e) {
        System.out.println("[ERROR] Unable to login");
      } catch (Exception e) {
        System.out.println("[ERROR] Unknown exception: " + e.getClass());
      }
    }
  }

  /**
   * Gets today's Epoch Timestamp from Jagex. Might not actually be today's, but it is what is used
   * by their Graph API.
   * 
   * @return Epoch Timestamp string from Jagex's Graph API
   */
  private String getTodaysEpochTimestamp() {

    try {
      GEPrice price;
      // 245 is the itemID for Wine of Zamorak
      // The actual ID isn't important as long as it is a valid one
      // because we just grab the timestamp off of it
      price = loadCurPriceRS("245");
      if (price == null)
        return null;
      else
        return price.getTimestamp();
    } catch (MalformedURLException e) {
      e.printStackTrace();
      return null;
    }

  }

  /**
   * A Getter method which returns the number of pages we are working on
   * 
   * @return Number of pages we're working on
   */
  public int getNumberOfPages() {
    return numberOfPages;
  }

  /**
   * A Getter method which returns the number of pages we have updated.
   * 
   * @return Number of pages updated.
   */
  public int getNumberOfPagesUpdated() {
    return numberOfPagesUpdated;
  }

  private GEPrice loadCurPriceOS(String itemID) {
    String apiurl = "http://services.runescape.com/m=itemdb_oldschool/viewitem?obj=" + itemID;

    try {

      Document top100 = Jsoup.connect(apiurl).get();

      Pattern getData = Pattern.compile(
          "average180\\.push\\(\\[new Date\\(.*\\), (\\d+), (\\d+)\\]\\);\\s*trade180\\.push\\(\\[new Date\\(.*\\), (\\d+)\\]\\);\\s*\\<\\/script\\>");
      for (Element element : top100.getElementsByTag("script")) {
        if (element.toString().contains("var average30 = [['Date',")) {
          Matcher matcher = getData.matcher(element.toString());
          if (!matcher.find()) {
            main.sendMessageToTy("Unable to load item price for item " + itemID);
            return null;
          }

          return new GEPrice(timestamp, matcher.group(1), itemID, matcher.group(3));
        }
      }

    } catch (IOException e) {

    }
    return null;
  }

  /**
   * Fetches the items from User:TyBot/itemsRemovedFromGE. These are items that are not available on
   * the GE, but have an Exchange: page for historical reference. This is to avoid wasting time on
   * items that we know will not work.
   * 
   * @return A List of item names to not worry about.
   */
  private List<String> getItemsRemovedFromGE() {
    try {
      String items = wikiBot.getPageText("User:TyBot/itemsRemovedFromGE");
      ArrayList<String> temp = new ArrayList<String>();
      String[] itemsarray = items.split("\n");
      for (String item : itemsarray) {
        temp.add("Exchange:" + item);
      }
      return temp;
    } catch (IOException | NullPointerException e) {
      return new ArrayList<String>();
    }

  }
}
