package com.faceyspacies.GEMWbot.Commands;

import java.util.List;

import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;

import com.faceyspacies.GEMWbot.GEMWbot;

public class HelpCommandHandler extends Command {

  HelpCommandHandler(GEMWbot main) {}

  @Override
  public void runCommand(MessageReceivedEvent e, List<String> args) {
    e.getMessage().reply("All of my commands can be read at [[User:TyBot]]");
    e.getMessage().addReaction(ReactionEmoji.of("👌"));

  }

}
