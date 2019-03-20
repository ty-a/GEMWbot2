package com.faceyspacies.GEMWbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.faceyspacies.GEMWbot.Commands.CommandHandler;

import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.ReadyEvent;


/**
 * The main class of GEMWbot. It handles the startup and the base of all IRC functions. It starts
 * all subfunctions, such as the GrandExchangeUpdater and the UpdateChecker.
 * 
 * @author Ty
 *
 */
public class GEMWbot {

  /**
   * Boolean on if UpdateChecker is enabled. If not specified in irc.properties, assumed false. Only
   * used in initialization. To determine if the UpdateChecker is currently running, see if checker
   * != null.
   */
  protected boolean enableChecker;

  /**
   * The instance of UpdateChecker currently running. Null if not running.
   */
  private UpdateChecker checker;

  /**
   * The instance of GrandExchangeUpdater currently running. Null if not running.
   */
  private GrandExchangeUpdater updateTask;

  /**
   * Decides if Discord has fired off a DiscordReady Event yet.
   */
  private boolean discordReady = false;

  /**
   * Our Discord token that allows the bot to log into Discord
   */
  private String token;

  /**
   * Ty's User ID to allow the bot to PM Ty
   */
  private long TyUserId;

  /**
   * The ID of the #bots channel to allow the bot to send messages there unprovoked
   */
  private long BotsChannelId;

  /**
   * The ID of the #wiki channel to allow the bot to send messages there unprovoked
   */
  private long WikiChannelId;

  private IDiscordClient discord;

  /**
   * The prefix for commands
   */
  private String prefix;

  /**
   * The Date object of the last time we finished a GE Update
   */
  private Date lastGEUpdateDate = null;

  /**
   * Constructor.
   * 
   * Loads our settings and opens the Discord connection. Based on settings, enables UpdateChecker.
   */
  public GEMWbot() {
    loadDiscordSettings();
    updateTask = null;
    checker = null;

    discord = createClient();

    // The startChecker() method checks if the checker is enabled, so no need
    // to check here.
    startChecker();
  }

  /**
   * Loads Discord settings from our discord.properties file.
   * 
   * Some settings are only optional, so if they are not provided, it assigns a default. Other
   * settings are required, and the program will quit without them. It will print a line to the
   * console alerting the operator to it.
   */
  private void loadDiscordSettings() {
    Properties settings = new Properties();
    InputStream input = null;

    try {
      File settingsFileLocation = new File("discord.properties");
      input = new FileInputStream(settingsFileLocation);

      settings.load(input);

      TyUserId = Long.parseLong(settings.getProperty("TyUserId"));
      token = settings.getProperty("token");
      BotsChannelId = Long.parseLong(settings.getProperty("botChannelId"));
      WikiChannelId = Long.parseLong(settings.getProperty("wikiChannelId"));
      prefix = settings.getProperty("prefix");


      String temp;

      temp = settings.getProperty("enableChecker");
      if (temp == null)
        enableChecker = false;
      else
        enableChecker = temp.equals("true") ? true : false;

    } catch (FileNotFoundException err) {
      System.out.println("[ERROR] Unable to load irc.properties file; closing");
      System.exit(0);
    } catch (IOException e) {
      System.out.println("[ERROR] IO Error loading irc.properties; closing");
      System.exit(0);
    }

  }

  /**
   * Our main method which starts the magic. Simply creates a GEMWbot object.
   * 
   * @param args unused
   */
  public static void main(String[] args) {
    new GEMWbot();
  }

  /**
   * A helper method to send a message to Ty. Mostly used for error messages.
   * 
   * @param message The message to send
   */
  protected void sendMessageToTy(String message) {
    discord.fetchUser(TyUserId).getOrCreatePMChannel().sendMessage(message);
  }

  protected void sendMessageToWikiChannel(String message) {
    discord.getChannelByID(WikiChannelId).sendMessage(message);
  }

  /**
   * Sets the updateTask parameter to null, to signify the GrandExchangeUpdater is not currently
   * running. This is used by the GrandExchangeUpdater when it is finished or when it encounters an
   * error.
   */
  protected void setUpdateTaskToNull() {
    updateTask = null;
  }

  /**
   * Sets the checker parameter to null, to signify the UpdateChecker is not currently running. This
   * is used by the UpdateChecker when it detects an update or when it encounters an error.
   */
  protected void setCheckerToNull() {
    checker = null;
  }

  /**
   * If the enableChecker parameter is true and the UpdateChecker isn't already running, it starts
   * the UpdateChecker.
   */
  public void startChecker() {
    if (enableChecker) {
      if (checker == null) {
        checker = new UpdateChecker(this);
        Thread checkerThread = new Thread(checker);
        checkerThread.start();
      }
    }
  }

  /**
   * If the Grand Exchange Updater isn't already running, starts it.
   */
  public void startUpdater(boolean makeAnnouncement) {
    if (updateTask == null) {
      try {

        if (makeAnnouncement) {
          sendMessageToWikiChannel(
              "Detected a Grand Exchange Update, starting to update prices on the wiki.");
        }
        updateTask = new GrandExchangeUpdater(this);
        Thread updateThread = new Thread(updateTask);
        updateThread.start();

        checker = null;
      } catch (Exception e) {
        sendMessageToTy("Failed to start GE Updater :(");
      }

    }
  }

  /**
   * Returns the instance of GrandExchangeUpdater that is currently running.
   * 
   * @return GrandExchangeUpdater instance or null if not running
   */
  public GrandExchangeUpdater getGEMWinstance() {
    return updateTask;
  }

  /**
   * Returns the instance of UpdateChecker that is currently running.
   * 
   * @return UpdateChecker instance or null if not running
   */
  public UpdateChecker getChecker() {
    return checker;
  }

  /**
   * Creates the Status text returned when using the ~status command.
   * 
   * @param nick The user who performed the ~status command
   * @return The status text
   */
  public String getStatusText() {
    String out = "";
    if (updateTask == null) {
      out = "The GE Updater is not running! ";

    } else {
      out = "Updating page (not item!) " + updateTask.getNumberOfPagesUpdated() + " out of "
          + updateTask.getNumberOfPages() + "! ";
    }

    out += "Uptime: " + getUptime() + " Update Checker: " + ((checker != null) ? "on" : "off");

    out += " Last Update was at "
        + ((lastGEUpdateDate != null) ? lastGEUpdateDate.toString() : "Unknown");

    return out;

  }

  /**
   * Gets the current uptime of the program
   * 
   * @return The uptime as a string
   */
  public String getUptime() {
    RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
    long millis = rb.getUptime();
    long day = TimeUnit.MILLISECONDS.toDays(millis);
    long hour = TimeUnit.MILLISECONDS.toHours(millis) - TimeUnit.DAYS.toHours(day);
    long minute = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.DAYS.toMinutes(day)
        - TimeUnit.HOURS.toMinutes(hour);
    long second = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.DAYS.toSeconds(day)
        - TimeUnit.HOURS.toSeconds(hour) - TimeUnit.MINUTES.toSeconds(minute);
    String uptime =
        String.format("%02d days %02d hours %02d minutes %02d seconds", day, hour, minute, second);
    return uptime;
  }

  public IDiscordClient createClient() {
    ClientBuilder cb = new ClientBuilder();
    cb.withToken(token);
    cb.registerListener(new CommandHandler(this, BotsChannelId));
    IDiscordClient client = cb.login();
    return client;
  }

  @EventSubscriber
  public void onReadyEvent(ReadyEvent event) {
    discordReady = true;
  }

  /**
   * Returns our command prefix
   * 
   * @return String prefix
   */
  public String getCommandPrefix() {
    return prefix;
  }

  /**
   * Returns the last Date object we have for completing GE updates
   * 
   * @return Date object of last GE update. Null if no updates have been ran
   */
  public Date getLastGEDate() {
    return lastGEUpdateDate;
  }

  /**
   * Allows us to set the last Date for completing the last GE Update
   * 
   * @param date
   */
  public void setLastGEDate(Date date) {
    this.lastGEUpdateDate = date;
  }
}
