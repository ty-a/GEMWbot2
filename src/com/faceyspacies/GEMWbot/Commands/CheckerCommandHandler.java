package com.faceyspacies.GEMWbot.Commands;

import java.util.List;

import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;

import com.faceyspacies.GEMWbot.GEMWbot;

public class CheckerCommandHandler extends Command {

  private GEMWbot main;

  CheckerCommandHandler(GEMWbot main) {
    this.main = main;
  }

  @Override
  public void runCommand(MessageReceivedEvent e, List<String> args) {

    if (args.size() == 0) {
      e.getMessage().addReaction(ReactionEmoji.of("❌"));
      e.getMessage().reply("Please say if you want to turn it on or off.");
      return;
    }

    if (args.get(0).toLowerCase().equals("on")) {
      if (main.getChecker() != null) {
        e.getMessage()
            .reply(
                "The Update Checker is already running. If there appears to be an issue with it, please ping @Ty#0768");

        e.getMessage().addReaction(ReactionEmoji.of("❌"));
      } else {

        if (isAdmin(e.getGuild(), e.getAuthor())) {
          e.getMessage().reply("Starting the Update Checker!");
          e.getMessage().addReaction(ReactionEmoji.of("👌"));

          main.startChecker();


        } else {
          e.getMessage().reply("Sorry, currently only RS/OSRS Wiki Admins can do that. :(");
        }

      }

    } else if (args.get(0).toLowerCase().equals("off")) {
      if (main.getChecker() == null) {
        e.getMessage()
            .reply(
                "The Update Checker is already off. If there appears to be an issue with it, please ping @Ty#0768");
        e.getMessage().addReaction(ReactionEmoji.of("❌"));
      } else {
        if (isAdmin(e.getGuild(), e.getAuthor())) {
          main.getChecker().stopRunning();
          e.getMessage().reply("The Update Checker has been stopped.");
          e.getMessage().addReaction(ReactionEmoji.of("👌"));
        } else {
          e.getMessage().reply("Sorry, currently only RS/OSRS Wiki Admins can do that. :(");
        }
      }
    } else {
      e.getMessage().reply("Unknown arg " + args.get(0) + ", please use \"on\" or \"off\"");
      e.getMessage().addReaction(ReactionEmoji.of("❌"));
    }


  }

}
