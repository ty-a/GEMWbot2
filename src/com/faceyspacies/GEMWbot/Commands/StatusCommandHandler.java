package com.faceyspacies.GEMWbot.Commands;

import java.util.List;

import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;

import com.faceyspacies.GEMWbot.GEMWbot;

public class StatusCommandHandler extends Command {

  private GEMWbot main;

  StatusCommandHandler(GEMWbot main) {
    this.main = main;
  }

  @Override
  public void runCommand(MessageReceivedEvent e, List<String> args) {
    e.getMessage().reply(main.getStatusText());
    e.getMessage().addReaction(ReactionEmoji.of("👌"));

  }

}
