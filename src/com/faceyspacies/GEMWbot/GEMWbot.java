package com.faceyspacies.GEMWbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import jerklib.Channel;
import jerklib.ConnectionManager;
import jerklib.Profile;
import jerklib.Session;
import jerklib.events.IRCEvent;
import jerklib.events.IRCEvent.Type;
import jerklib.events.MessageEvent;
import jerklib.listeners.IRCEventListener;


/**
 * The main class of GEMWbot. It handles the startup and the base of all IRC functions. It starts
 * all subfunctions, such as the GrandExchangeUpdater, TieBot and the UpdateChecker.
 * 
 * @author Ty
 *
 */
public class GEMWbot implements IRCEventListener {
  /**
   * The connection manager that allows us access to the IRC methods.
   */
  private ConnectionManager manager;

  /**
   * GEMWbot's default IRC nick. It is loaded from irc.properties. After initial setup, it is not
   * used.
   */
  private String ircNick;

  /**
   * The IRC Server the bot will connect to. It is loaded from irc.properties. After initial setup,
   * it is not used.
   */
  private String ircServer;

  /**
   * An List which contains the hosts of users who are considered "mods" of the bot. Allows access
   * to privileged commands such as "~update". Users are added to this list either by editing
   * irc.properties or by using the "~allow" command.
   */
  private List<String> allowedHosts;

  /**
   * A List which contains the hosts of users who are considered "admins" of the bot. Allows access
   * to priviledged commands such as "~die". Users are added to this list manually by editing
   * irc.properties.
   */
  private List<String> adminHosts;

  /**
   * The IRC channel that the bot will join after it connects to the IRC network. Loaded from
   * irc.properties.
   */
  protected String ircChannel;

  /**
   * The bot's NickServ username. Used for authentication to NickServ. Set in irc.properties.
   */
  private String nickServUser;

  /**
   * The bot's NickServ password. Used for authentication to NickServ. Set in irc.properties.
   */
  private String nickServPass;

  /**
   * Used for TieBot. The IRC network that the edit feed is on. Set in irc.properties.
   */
  private String feedNetwork;

  /**
   * Used for TieBot. The port to connect to the IRC network on. Set in irc.properties.
   */
  private int feedPort;

  /**
   * Boolean on if TieBot is enabled. If not specified in irc.properties, assumed false.
   */
  protected boolean enableTieBot;

  /**
   * Boolean on if TieBot is reporting new user creations into #cvn-wikia-newusers. If not specified
   * in irc.properties, assumed false.
   */
  protected boolean enableTieBotNewUsers;

  /**
   * Boolean on if UpdateChecker is enabled. If not specified in irc.properties, assumed false. Only
   * used in initialization. To determine if the UpdateChecker is currently running, see if checker
   * != null.
   */
  protected boolean enableChecker;

  /**
   * The IRC session. Mostly used to send private messages to users.
   */
  private Session session;

  /**
   * The IRC session for TieBot. Mostly used to close the session.
   */
  private Session rcSession;

  /**
   * The instance of TieBot that is currently running. Null if not running
   */
  private TieBot tieBotInstance;

  /**
   * The instance of UpdateChecker currently running. Null if not running.
   */
  private UpdateChecker checker;

  /**
   * The instance of GrandExchangeUpdater currently running. Null if not running.
   */
  private GrandExchangeUpdater updateTask;

  /**
   * Constructor.
   * 
   * Loads our settings and opens the IRC connection. Based on settings, enables TieBot and
   * UpdateChecker.
   */
  public GEMWbot() {
    loadIRCsettings();
    updateTask = null;
    tieBotInstance = null;
    rcSession = null;
    checker = null;

    manager = new ConnectionManager(new Profile(ircNick));

    session = manager.requestConnection(ircServer);
    session.addIRCEventListener(this);

    if (enableTieBot) {
      createTieBotInstance();
    }

    // The startChecker() method checks if the checker is enabled, so no need
    // to check here.
    startChecker();

  }

  /**
   * Loads IRC settings from our irc.properties file.
   * 
   * Some settings are only optional, so if they are not provided, it assigns a default. Other
   * settings are required, and the program will quit without them. It will print a line to the
   * console alerting the operator to it.
   */
  private void loadIRCsettings() {
    Properties ircSettings = new Properties();
    InputStream input = null;

    try {
      File settingsFileLocation = new File("irc.properties");
      input = new FileInputStream(settingsFileLocation);

      ircSettings.load(input);

      ircNick = ircSettings.getProperty("ircNick");
      ircServer = ircSettings.getProperty("ircServer");
      allowedHosts =
          new ArrayList<String>(Arrays.asList(ircSettings.getProperty("allowedHosts").split(",")));
      adminHosts = Arrays.asList(ircSettings.getProperty("adminHosts").split(","));
      ircChannel = ircSettings.getProperty("ircChannel");
      nickServUser = ircSettings.getProperty("nickServUser");
      nickServPass = ircSettings.getProperty("nickServPass");
      feedNetwork = ircSettings.getProperty("feedNetwork");

      try {
        feedPort = Integer.parseInt(ircSettings.getProperty("feedPort"));
      } catch (NumberFormatException e) {
        System.out.println("[ERROR] feedPort is missing from irc.properties or invalid; closing");
        System.exit(0);
      }

      String temp = ircSettings.getProperty("enableTieBot");
      if (temp == null)
        enableTieBot = false;
      else
        enableTieBot = temp.equals("true") ? true : false;

      temp = ircSettings.getProperty("enableTieBotNewUsers");
      if (temp == null)
        enableTieBotNewUsers = false;
      else
        enableTieBotNewUsers = temp.equals("true") ? true : false;

      temp = ircSettings.getProperty("enableChecker");
      if (temp == null)
        enableChecker = false;
      else
        enableChecker = temp.equals("true") ? true : false;

      if (ircNick == null) {
        System.out.println("[ERROR] ircNick is missing from irc.properties; closing");
        System.exit(0);
      }

      if (ircServer == null) {
        System.out.println("[ERROR] ircServer is missing from irc.properties; closing");
        System.exit(0);
      }

      if (allowedHosts == null) {
        System.out.println("[ERROR] allowedHosts is missing from irc.properties; closing");
        System.exit(0);
      }

      if (ircChannel == null) {
        System.out.println("[ERROR] ircChannel is missing from irc.properties; closing");
        System.exit(0);
      }

      if (nickServUser == null) {
        System.out.println("[ERROR] nickServUser is missing from irc.properties; closing");
        System.exit(0);
      }

      if (nickServPass == null) {
        System.out.println("[ERROR] nickServPass is missing from irc.properties; closing");
        System.exit(0);
      }

      if (adminHosts == null) {
        System.out.println("[ERROR] nickServPass is missing from irc.properties; closing");
        System.exit(0);
      }

      if (feedNetwork == null) {
        System.out.println("[ERROR] feedNetwork is missing from irc.properties; closing");
        System.exit(0);
      }

    } catch (FileNotFoundException err) {
      System.out.println("[ERROR] Unable to load irc.properties file; closing");
      System.exit(0);
    } catch (IOException e) {
      System.out.println("[ERROR] IO Error loading irc.properties; closing");
      System.exit(0);
    }

  }

  /*
   * (non-Javadoc)
   * 
   * @see jerklib.listeners.IRCEventListener#receiveEvent(jerklib.events.IRCEvent)
   */
  @Override
  public void receiveEvent(IRCEvent e) {
    if (e.getType() == Type.CONNECT_COMPLETE) {
      loginToNickServ(e.getSession());

    }

    else if (e.getType() == Type.CHANNEL_MESSAGE) {
      MessageEvent me = (MessageEvent) e;

      // If we are not running the UpdateChecker, listen for RuneScript to announce a GE Update
      if (checker == null) {
        if (me.getMessage().contains(
            "The Grand Exchange has been updated. RuneScript last detected an update")
            && me.getNick().contains("RuneScript")) {

          Channel channel = e.getSession().getChannel(ircChannel);
          try {
            // If the GrandExchangeUpdater is not already running, start it.
            if (updateTask == null) {
              channel.say("Starting GE Updates!");
              updateTask = new GrandExchangeUpdater(this);
              Thread thread = new Thread(updateTask);
              thread.start();
            }
          } catch (Exception err) {
            channel.say("Failed to start GE Updater.");
          }
        }
      }

      if (me.getMessage().charAt(0) == '~') { // we have a command as ~ is our trigger
        commandHandler(me);
      } else {
        return; // no command
      }
    } else if (e.getType() == Type.DEFAULT) {

      // Library handles PING, ignore it
      if (e.getRawEventData().substring(0, 4).equalsIgnoreCase("PING")) {
        return;
      }

      int eventCode;
      int firstIndex, secondIndex;
      /*
       * example of raw event data server eventCode <varies on code> :weber.freenode.net 396 TyBot2
       * wikia/TyBot :is now your hidden host (set by services.) 396 is auth'd to services on
       * freenode
       */
      firstIndex = e.getRawEventData().indexOf(" ") + 1;
      secondIndex = e.getRawEventData().indexOf(" ", firstIndex); // .substring automatically -1s
                                                                  // from the endIndex
      try {
        eventCode = Integer.parseInt(e.getRawEventData().substring(firstIndex, secondIndex));
      } catch (NumberFormatException err) {
        return; // no need to continue
      }
      if (eventCode == 396) {
        e.getSession().join(ircChannel);
        if (enableTieBotNewUsers) {
          e.getSession().join("#cvn-wikia-newusers");
        }
      }
    }
  }

  /**
   * Processes MessageEvent received and performs the command, if any, that the user requested.
   * 
   * @param me MessageEvent that sent a message that started with ~
   */
  private void commandHandler(MessageEvent me) {
    Session session = manager.getSession(ircServer);
    Channel channel = me.getChannel();
    String command = "";
    String fullCommand = me.getMessage();
    boolean isMod;
    boolean isAdmin;

    if (fullCommand.indexOf(" ") != -1) {
      // index 1 is start of command after trigger
      command = fullCommand.substring(1, fullCommand.indexOf(" "));

    } else {
      command = fullCommand.substring(1);
    }

    isAdmin = isAdmin(me.getHostName());
    if (isAdmin)
      isMod = true;
    else
      isMod = isMod(me.getHostName());

    switch (command.toLowerCase()) {
      case "adie":
      case "die":
        if (isAdmin) {
          manager.quit("Requested");

          if (updateTask != null) {
            updateTask.stopRunning();
          }

          if (checker != null) {
            checker.stopRunning();
          }

          System.exit(0);


        } else {
          session.sayPrivate(me.getNick(), "You are not allowed to use the ~die command.");
        }

        break;

      case "nick":
        if (isAdmin) {
          try {
            session.changeNick(fullCommand.split(" ")[1]);
          } catch (IndexOutOfBoundsException err) {
            channel.say(me.getNick() + ": You forgot to specify a nick");
          }
        } else {
          session.sayPrivate(me.getNick(), "You are not allowed to use the ~nick command.");
        }
        break;

      case "test":
        channel.say(me.getNick() + ": Hai <3!");
        break;

      case "log":
        channel.say(me.getNick() + ": http://runescape.wikia.com/wiki/User:TyBot/log");
        break;

      case "update":
        if (updateTask != null) {
          channel.say(me.getNick() + ": GE Updater is already running!");
          break;
        }
        if (isMod) {
          channel.say(me.getNick() + ": Starting GE Updates!");
          try {
            updateTask = new GrandExchangeUpdater(this);
            Thread thread = new Thread(updateTask);
            thread.start();

            if (checker != null)
              checker.stopRunning();
          } catch (Exception err) {
            channel.say("Failed to start GE Updater.");
          }
        } else {
          session.sayPrivate(me.getNick(), "You are not allowed to use the ~update command.");
        }
        break;

      case "allow":
        if (isMod) {
          try {
            if (allowedHosts.contains(fullCommand.split(" ")[1])) {
              channel.say(me.getNick() + ": " + fullCommand.split(" ")[1]
                  + " is already allowed to use ~update");
              return;
            }
            allowedHosts.add(fullCommand.split(" ")[1]);
            saveSettings();

            channel.say(me.getNick() + ": " + fullCommand.split(" ")[1]
                + " is now allowed to use ~update");
          } catch (IndexOutOfBoundsException err) {
            channel.say(me.getNick() + ": You forgot to specify a host");
          }
        } else {
          session.sayPrivate(me.getNick(), "You are not allowed to use the ~allow command.");
        }
        break;

      case "disallow":
        if (isMod) {
          try {
            if (!allowedHosts.contains(fullCommand.split(" ")[1])) {
              channel.say(me.getNick() + ": " + fullCommand.split(" ")[1]
                  + " isn't allowed to use ~update");
              return;
            }
            allowedHosts.remove(fullCommand.split(" ")[1]);
            saveSettings();

            channel.say(me.getNick() + ": " + fullCommand.split(" ")[1]
                + " is no longer allowed to use ~update");
          } catch (IndexOutOfBoundsException err) {
            channel.say(me.getNick() + ": You forgot to specify a host");
          }
        } else {
          session.sayPrivate(me.getNick(), "You are not allowed to use the ~disallow command.");
        }
        break;

      case "tiebot":
        if (isMod) {
          try {
            String mode = fullCommand.split(" ")[1].toLowerCase();
            boolean on;
            if (mode.equalsIgnoreCase("on")) {
              on = true;
              enableTieBot = true;
            } else if (mode.equalsIgnoreCase("off")) {
              on = false;
              enableTieBot = false;
            } else {
              channel.say(me.getNick() + ": Invalid syntax. Use ~tiebot on/off");
              return;
            }

            if (on) {
              // if we have an instance and we already have it set to read wiki discussions
              if (tieBotInstance != null && tieBotInstance.getWikiDiscussionsFeed()) {
                channel.say(me.getNick() + ": TieBot is already running!");
                return;
                // if we have an instance, but the wiki discussions isn't running start it
              } else if (tieBotInstance != null) {
                channel.say(me.getNick() + ": Starting TieBot!");
                tieBotInstance.setWikiDiscussionsFeed(true);
                return;
              }

              // no instance
              createTieBotInstance();
              channel.say(me.getNick() + ": Starting TieBot!");
            } else { // stop running
              if (tieBotInstance == null) {
                channel.say(me.getNick() + ": TieBot isn't running!");
                return;
              }

              // mark as off
              channel.say(me.getNick() + ": Stopping TieBot!");
              tieBotInstance.setWikiDiscussionsFeed(false);

              // If neither mode is running, just quit
              if (!tieBotInstance.getWikiDiscussionsFeed() && !tieBotInstance.getNewUsersFeed()) {
                rcSession.close("Requested by " + me.getNick());
                tieBotInstance = null;
                return;
              }

            }
          } catch (IndexOutOfBoundsException err) {
            channel.say(me.getNick() + ": Invalid syntax. Use ~tiebot on/off!");
            return;
          }
        } else {
          session.sayPrivate(me.getNick(), "You are not allowed to use the ~tiebot command");
        }
        break;

      case "newusers":
        if (isAdmin) {
          try {
            String mode = fullCommand.split(" ")[1].toLowerCase();
            boolean on;
            if (mode.equalsIgnoreCase("on")) {
              on = true;
              enableTieBotNewUsers = true;
            } else if (mode.equalsIgnoreCase("off")) {
              on = false;
              enableTieBotNewUsers = false;
            } else {
              channel.say(me.getNick() + ": Invalid syntax. Use ~newusers on/off");
              return;
            }

            if (on) {
              // if we have an instance and we already have it set to read wiki discussions
              if (tieBotInstance != null && tieBotInstance.getNewUsersFeed()) {
                channel.say(me.getNick() + ": NewUsers is already running!");
                return;
                // if we have an instance, but the new users isn't running; start it
              } else if (tieBotInstance != null) {
                me.getSession().join("#cvn-wikia-newusers");
                channel.say(me.getNick() + ": Starting new users feed!");
                tieBotInstance.setNewUsersFeed(true);
                return;
              }

              // no instance
              me.getSession().join("#cvn-wikia-newusers");
              createTieBotInstance();
              channel.say(me.getNick() + ": Starting new users feed!");
            } else { // stop running
              if (tieBotInstance == null) {
                channel.say(me.getNick() + ": new users feed isn't running!");
                return;
              }

              // mark as off
              channel.say(me.getNick() + ": Stopping new users feed!");
              tieBotInstance.setNewUsersFeed(false);
              me.getSession().getChannel("#cvn-wikia-newusers")
                  .part("Requested by " + me.getNick());

              // If neither mode is running, just quit
              if (!tieBotInstance.getWikiDiscussionsFeed() && !tieBotInstance.getNewUsersFeed()) {
                rcSession.close("Requested by " + me.getNick());
                tieBotInstance = null;
                return;
              }

            }
          } catch (IndexOutOfBoundsException err) {
            channel.say(me.getNick() + ": Invalid syntax. Use ~newusers on/off!");
            return;
          }
        } else {
          session.sayPrivate(me.getNick(), "You are not allowed to use the ~newusers command");
        }
        break;

      case "hush":
        if (tieBotInstance == null) {
          channel.say(me.getNick() + ": TieBot isn't running!");
          return;
        } else {
          String time = fullCommand.split(" ")[1];
          long hushTime = System.currentTimeMillis() + (Integer.parseInt(time) * 60000);
          tieBotInstance.hush(hushTime);

          channel.say(me.getNick() + ": Hushing for " + time + " mins!");
        }
        break;

      case "unhush":
        if (tieBotInstance == null) {
          channel.say(me.getNick() + ": TieBot is not running!");
          return;
        } else {
          tieBotInstance.unhush();
          channel.say(me.getNick() + ": No longer hushed!");
        }
        break;

      case "astatus":
      case "status":
        channel.say(getStatusText(me.getNick()));
        break;

      case "checker":
        if (isMod) {
          try {
            String mode = fullCommand.split(" ")[1].toLowerCase();
            boolean on;
            if (mode.equalsIgnoreCase("on")) {
              on = true;
            } else if (mode.equalsIgnoreCase("off")) {
              on = false;
            } else {
              channel.say(me.getNick() + ": Invalid syntax. Use ~checker on/off");
              return;
            }

            if (on) {
              if (checker != null) {
                channel.say(me.getNick() + ": Update Checker is already running!");
                return;
              }

              startChecker();
              channel.say(me.getNick() + ": Starting Update Checker!");
            } else { // stop running
              if (checker == null) {
                channel.say(me.getNick() + ": Update Checker isn't running!");
                return;
              }

              channel.say(me.getNick() + ": Stopping Update Checker!");
              checker.stopRunning();

            }
          } catch (IndexOutOfBoundsException err) {
            channel.say(me.getNick() + ": Invalid syntax. Use ~checker on/off!");
            return;
          }
        } else {
          session.sayPrivate(me.getNick(), "You are not allowed to use the checker command");
        }
        break;

      case "allowed":
        String hosts = "";

        for (int i = 0; i < allowedHosts.size(); i++) {
          hosts += allowedHosts.get(i) + ",";
        }

        hosts = hosts.substring(0, hosts.length() - 1);

        channel.say(me.getNick() + ": " + hosts + " are allowed to use ~update");
        break;

      case "help":
        channel.say(me.getNick() + ": My commands can be found on my userpage at [[User:TyBot]].");
        break;

      case "source":
        channel.say(me.getNick()
            + ": My source code is available at https://github.com/ty-a/GEMWbot2");
        break;
    }
  }

  /**
   * Saves settings that may have been modified back into irc.properties. <br />
   * IMPORTANT: If you add a new setting to the irc.properties file, make sure it is saved here.
   * Otherwise calling this will REMOVE that setting.
   */
  private void saveSettings() {


    try {
      Properties ircSettings = new Properties();
      File settingsFileLocation = new File("irc.properties");
      OutputStream output = new FileOutputStream(settingsFileLocation);

      ircSettings.setProperty("ircNick", ircNick);
      ircSettings.setProperty("ircServer", ircServer);
      String hosts = "";

      for (int i = 0; i < allowedHosts.size(); i++) {
        hosts += allowedHosts.get(i) + ",";
      }

      hosts = hosts.substring(0, hosts.length() - 1);

      ircSettings.setProperty("allowedHosts", hosts);

      hosts = "";
      for (int i = 0; i < adminHosts.size(); i++) {
        hosts += adminHosts.get(i) + ",";
      }
      hosts = hosts.substring(0, hosts.length() - 1);

      ircSettings.setProperty("adminHosts", hosts);
      ircSettings.setProperty("ircChannel", ircChannel);
      ircSettings.setProperty("nickServUser", nickServUser);
      ircSettings.setProperty("nickServPass", nickServPass);
      ircSettings.setProperty("enableTieBot", "" + enableTieBot);
      ircSettings.setProperty("enableTieBotNewUsers", "" + enableTieBotNewUsers);
      ircSettings.setProperty("feedNetwork", feedNetwork);
      ircSettings.setProperty("feedPort", "" + feedPort);
      ircSettings.setProperty("enableChecker", "" + enableChecker);

      ircSettings.store(output, "GEMWbot's IRC Settings");

    } catch (FileNotFoundException err) {
      System.out.println("[ERROR] Unable to load irc.properties file; closing");
      System.exit(0);
    } catch (IOException e) {
      System.out.println("[ERROR] IO Error loading irc.properties; closing");
      System.exit(0);
    }
  }

  /**
   * Logs the bot into NickServ. It does it by sending a Private Message to NickServ with the
   * nickServUser and nickServPass loaded in from irc.properties.
   * 
   * @param session The IRC session we are connected to.
   */
  private void loginToNickServ(Session session) {
    session.sayPrivate("NickServ", "IDENTIFY " + nickServUser + " " + nickServPass);
  }

  /**
   * Determines if the host is a mod of the bot, being in the allowedHosts field.
   * 
   * @param host The host of the user.
   * @return boolean on if user is a mod
   */
  private boolean isMod(String host) {
    return allowedHosts.contains(host);
  }

  /**
   * Determines if the host is an admin of the bot, being in the adminHosts field.
   * 
   * @param host The host of the user
   * @return boolean on if user is an admin
   */
  private boolean isAdmin(String host) {
    return adminHosts.contains(host);
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
   * A helper method to send a message to channelName.
   * 
   * @param channelName The channel to send the message to
   * @param message The message to send
   */
  protected void sendMessage(String channelName, String message) {
    Session session = manager.getSession(ircServer);
    Channel channel = session.getChannel(channelName);

    channel.say(message);
  }

  /**
   * A Getter method which returns what IRC channel the bot is in
   * 
   * @return The channel we are in
   */
  public String getChannel() {
    return ircChannel;
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
  public void startUpdater() {
    if (updateTask == null) {
      try {
        updateTask = new GrandExchangeUpdater(this);
        Thread updateThread = new Thread(updateTask);
        updateThread.start();
        manager.getSession(ircServer).getChannel(ircChannel)
            .say("GE Update detected! Starting updates.... ");
        checker = null;
      } catch (Exception e) {
        manager.getSession(ircServer).getChannel(ircChannel).say("Failed to start GE Updater!");
      }

    }
  }

  /**
   * Creates our TieBot object and adds the event listener for it.
   */
  private void createTieBotInstance() {
    rcSession = manager.requestConnection(feedNetwork, feedPort);
    tieBotInstance = new TieBot(manager, this);
    rcSession.addIRCEventListener(tieBotInstance);

    tieBotInstance.setNewUsersFeed(enableTieBotNewUsers);
    tieBotInstance.setWikiDiscussionsFeed(enableTieBot);
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
   * Returns the instance of TieBot that is currently running.
   * 
   * @return TieBot instance or null if not running
   */
  public TieBot getTieBotinstance() {
    return tieBotInstance;
  }

  /**
   * Creates the Status text returned when using the ~status command.
   * 
   * @param nick The user who performed the ~status command
   * @return The status text
   */
  public String getStatusText(String nick) {
    String out = "";
    if (updateTask == null) {
      out = nick + ": The GE Updater is not running! ";

    } else {
      out =
          nick + ": Updating page " + updateTask.getNumberOfPagesUpdated() + " out of "
              + updateTask.getNumberOfPages() + "! ";
    }

    out +=
        "Uptime: " + getUptime() + " TieBot: " + (enableTieBot ? "on" : "off") + " NewUsersFeed: "
            + (enableTieBotNewUsers ? "on" : "off") + " Update Checker: "
            + ((checker != null) ? "on" : "off");

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
    long minute =
        TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.DAYS.toMinutes(day)
            - TimeUnit.HOURS.toMinutes(hour);
    long second =
        TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.DAYS.toSeconds(day)
            - TimeUnit.HOURS.toSeconds(hour) - TimeUnit.MINUTES.toSeconds(minute);
    String uptime =
        String.format("%02d days %02d hours %02d minutes %02d seconds", day, hour, minute, second);
    return uptime;
  }
}
