package com.faceyspacies.GEMWbot.Commands;

import java.util.List;

import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;

import com.faceyspacies.GEMWbot.GEMWbot;

public class UpdateCommandHandler extends Command {

  private GEMWbot main;

  UpdateCommandHandler(GEMWbot main) {
    this.main = main;
  }

  @Override
  public void runCommand(MessageReceivedEvent e, List<String> args) {
    if (main.getGEMWinstance() != null) {
      e.getMessage()
          .reply(
              "The Grand Exchange Updater is already running. If there appears to be an issue with it, please ping @Ty#0768");

      e.getMessage().addReaction(ReactionEmoji.of("❌"));
    } else {

      if (isAdmin(e.getGuild(), e.getAuthor())) {
        e.getMessage().reply("Starting the Grand Exchange Updater!");

        if (main.getChecker() != null) {
          main.getChecker().stopRunning();
        }

        main.startUpdater(false);

        e.getMessage().addReaction(ReactionEmoji.of("👌"));
      } else {
        e.getMessage().reply("Sorry, currently only RS/OSRS Wiki Admins can do that. :(");
      }

    }

  }

}
