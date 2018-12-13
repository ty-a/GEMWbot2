package com.faceyspacies.GEMWbot.Commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

import com.faceyspacies.GEMWbot.GEMWbot;

public class CommandHandler {

  private static Map<String, Command> commands = new HashMap<>();

  private GEMWbot main;
  private long botChannelId;

  public CommandHandler(GEMWbot main, long botChannelId) {
    this.main = main;
    this.botChannelId = botChannelId;

    commands.put("checker", new CheckerCommandHandler(main));
    commands.put("die", new DieCommandHandler(main));
    commands.put("help", new HelpCommandHandler(main));
    commands.put("log", new LogCommandHandler(main));
    commands.put("status", new StatusCommandHandler(main));
    commands.put("update", new UpdateCommandHandler(main));
  }

  @EventSubscriber
  public void onMessageReceived(MessageReceivedEvent event) {

    if (event.getAuthor().isBot()) {
      return;
    }

    if (event.getChannel().getLongID() != botChannelId) {
      return; // only listen in #bots
    }


    String[] args = event.getMessage().getContent().split(" ");

    if (args.length == 0) {
      return;
    }

    if (!args[0].startsWith(main.getCommandPrefix())) {
      return;
    }

    String command = args[0].substring(main.getCommandPrefix().length());

    List<String> argsList = new ArrayList<>(Arrays.asList(args));
    argsList.remove(0);

    if (commands.containsKey(command)) {
      commands.get(command).runCommand(event, argsList);
    }
  }
}
