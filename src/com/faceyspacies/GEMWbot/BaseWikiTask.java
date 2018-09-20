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

import com.faceyspacies.GEMWbot.Holders.GEPrice;
import com.faceyspacies.GEMWbot.Holders.UpdateResult;

/**
 * A class that represents the base wiki task that can be added to GEMWbot. The goal of making this
 * was to help reduce duplicated code.
 * 
 * @author Ty
 *
 */
abstract class BaseWikiTask implements Runnable {

  /**
   * The bot's wiki username. Loaded from gemw.properties.
   */
  protected String wikiUserName;

  /**
   * The bot's wiki password. Loaded from gemw.properties.
   */
  protected String wikiUserPass;

  /**
   * The URL for the wiki to edit. Loaded from gemw.properties.
   */
  protected String wikiURL;

  /**
   * The base link for the RuneScape Graph API.
   */
  protected String rsGraphAPILink = "http://services.runescape.com/m=itemdb_rs/api/graph/";

  /**
   * Boolean on whether or not we should still be running. Does not end immediately, but up to 10
   * mins after the fact.
   */
  protected boolean running;

  /**
   * The Wiki object we used to communicate with the wiki
   */
  protected Wiki wikiBot;

  /**
   * The GEMWbot instance that allows us access to IRC and other bot functions
   */
  protected GEMWbot main;

  /**
   * The page on the wiki the bot would log data to, such as errors
   */
  protected String logPage;

  /**
   * The name of the IRC channel the bot stays in
   */
  protected String ircChannel;

  /**
   * The current error log. At the end, it maybe saved to the logPage.
   */
  protected String errorLog;


  /**
   * To cut down on time spent re-determining the current timestamp, it is saved here. If you want
   * to be sure you have the fresh data, set it to null before using loadCurPrice!
   */
  protected String timestamp;

  /**
   * Whether we're running in rs or os mode
   */
  protected String mode;


  /**
   * Our constructor. It loads the settings from gemw.properties, creates the wikiBot object and
   * initalizes a few things too
   * 
   * @param ircInstance GEMWbot main irc instance
   */
  BaseWikiTask(GEMWbot ircInstance) {
    if (!loadSettings()) {
      if (ircInstance != null)
        ircInstance.sendMessageToTy("failed to load config");
    };

    wikiBot = Wiki.createInstance(wikiURL, "", "https://");
    wikiBot.setMarkBot(true);
    wikiBot.setThrottle(0);
    wikiBot
        .setUserAgent("TyA's TyBot running the Grand Exchange Updater. https://github.com/ty-a/GEMWbot2");
    wikiBot.setUsingCompressedRequests(false); // Wikia fails anywhere from 20-50 times a run on
                                               // this
    errorLog = "";

    if (ircInstance != null) {
      this.main = ircInstance;
    }
    timestamp = null;

  }

  /**
   * Loads settings from the gemw.properties file.
   * 
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
      logPage = settings.getProperty("logPage");
      wikiURL = settings.getProperty("wikiURL");
      mode = settings.getProperty("mode");

      if (wikiUserName == null) {
        System.out.println("[ERROR] wikiUserName is missing from gemw.properties; closing");
        return false;
      }

      if (wikiUserPass == null) {
        System.out.println("[ERROR] wikiUserPass is missing from gemw.properties; closing");
        return false;
      }

      if (logPage == null) {
        System.out.println("[ERROR] logPage is missing from gemw.properties; Using User:"
            + wikiUserName + "/log instead");
        logPage = "User:" + wikiUserName + "/log";
      }

      if (wikiURL == null) {
        System.out.println("[ERROR] wikiURL is missing from gemw.properties; closing");
        return false;
      }

      if (mode == null) {
        System.out.println("Mode isn't definied, assuming rs");
      } else {
        mode = mode.toLowerCase();
        if (!(mode.equals("rs") || mode.equals("os"))) {
          System.out.println("Unknown mode " + mode + ". Assuming rs");
          mode = "rs";

        }
      }

      if (mode.equals("os")) {
        rsGraphAPILink = "http://services.runescape.com/m=itemdb_oldschool/api/graph/";
      }

    } catch (FileNotFoundException err) {
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
    } catch (Exception err) {
      System.out.println("[EXCEPTION] " + err.getClass() + ": " + err.getMessage());
      err.printStackTrace();
      main.setUpdateTaskToNull();
    }
  }

  /**
   * Where the implementing classes will start their work.
   */
  abstract void Start();

  /**
   * Attempts to login to the wiki up to 3 times.
   * 
   * @return UpdateResult with success info.
   */
  protected boolean Login() {
    int failures = 0;
    while (failures < 3) {
      // Try to login three times. If we fail three times, send a message to the channel
      try {
        wikiBot.login(wikiUserName, wikiUserPass.toCharArray());
        return true;

      } catch (FailedLoginException e) {
        failures++;
        if (failures == 3) {
          addToLog(new UpdateResult("unable to login - check username/pass", false),
              "[[Special:UserLogin]]");
          return false;
        }


      } catch (IOException e) {
        failures++;
        if (failures == 3) {
          addToLog(new UpdateResult("unable to login - network", false), "[[Special:UserLogin]]");
          return false;
        }
      }
    }

    return false;
  }

  /**
   * Updates the log page on the wiki.
   */
  protected void updateLogPage() {
    try {
      if (errorLog.equalsIgnoreCase(""))
        errorLog = "No issues today; yay!";
      wikiBot.newSection(logPage,
          "{{subst:CURRENTDAY}} {{subst:CURRENTMONTHNAME}} {{subst:CURRENTYEAR}}", errorLog, false,
          true);
    } catch (LoginException | IOException e) {
      main.sendMessageToTy("Failed to save the log page. It is probably too long. "
          + e.getClass() + ": " + e.getMessage());
    }

  }

  /**
   * A Helper method to add items to the log using the UpdateResult class. Only logs items that were
   * unsuccessful.
   * 
   * @param status UpdateResult with success info
   * @param pageName The page that corresponds to the UpdateResult
   */
  protected void addToLog(UpdateResult status, String pageName) {
    if (!status.getSuccess()) {
      errorLog +=
          "# [{{fullurl:" + pageName.replace("_", " ") + "}} " + pageName.replace("_", " ")
              + "] - " + status.getResult() + "\n";
    }
  }

  /**
   * Loads the current price from the Jagex Graph API.
   * 
   * @param id The item ID that corresponds to the item you want to load the price for. Usually
   *        obtained by parsing the item's Exchange:Module/itemName page.
   * @return GEPrice object containing the item's price, ID, and timestamp.
   * @throws MalformedURLException only an issue if you provide a bad item id.
   */
  protected GEPrice loadCurPrice(String id) throws MalformedURLException {

    URL url = new URL(rsGraphAPILink + id + ".json");
    GEPrice gePrice = null;
    HttpURLConnection request;
    int newPrice = -1;
    try {

      request = (HttpURLConnection) url.openConnection();
      request
          .setRequestProperty(
              "User-Agent",
              "GEMWBot2 - The RuneScape Wiki's Grand Exchange Price Database Updater. https://github.com/ty-a/GEMWbot2");
      request.connect();

      BufferedReader response = new BufferedReader(new InputStreamReader(request.getInputStream()));

      String data = response.readLine(); // entire thing is given as one line
      response.close();

      JSONObject baseItem = new JSONObject(data);
      JSONObject dailyItem = baseItem.getJSONObject("daily");

      if (timestamp == null) {
        Iterator<?> keys = dailyItem.keys();
        int highestSoFar = 0;
        int currNum;
        Pattern getNumber = Pattern.compile("(\\d+)\\d\\d\\d");

        while (keys.hasNext()) {
          String currKey = (String) keys.next();
          Matcher verifyNum = getNumber.matcher(currKey);
          if (verifyNum.find()) {
            currKey = verifyNum.group(1);
          } else {
            System.out.println("[ERROR] Failed to find price in json");
          }

          currNum = Integer.parseInt(currKey);

          if (currNum > highestSoFar) {
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
    } catch (JSONException e) {
      System.out.println("[ERROR] Unable to process JSON");
      return null;
    }

    return gePrice;
  }

  /**
   * Sets the running parameter to false, to cause the main loop to end. Does not guarantee an
   * immediate stop.
   */
  public void stopRunning() {
    running = false;
  }

}
