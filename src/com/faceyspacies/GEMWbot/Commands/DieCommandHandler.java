package com.faceyspacies.GEMWbot.Commands;

import java.util.List;

import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;

import com.faceyspacies.GEMWbot.GEMWbot;

public class DieCommandHandler extends Command {

  private GEMWbot main;

  DieCommandHandler(GEMWbot main) {
    this.main = main;
  }

  @Override
  public void runCommand(MessageReceivedEvent e, List<String> args) {
    e.getMessage().addReaction(ReactionEmoji.of("👌"));

    if (main.getGEMWinstance() != null) {
      main.getGEMWinstance().stopRunning();
    }

    if (main.getChecker() != null) {
      main.getChecker().stopRunning();
    }

    e.getClient().logout();

    System.exit(0);

  }
}
