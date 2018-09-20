package com.faceyspacies.GEMWbot.Commands;

import java.util.List;

import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;

import com.faceyspacies.GEMWbot.GEMWbot;

public class LogCommandHandler extends Command {

  LogCommandHandler(GEMWbot main) {}

  @Override
  public void runCommand(MessageReceivedEvent e, List<String> args) {
    e.getMessage().reply("My log is at User:TyBot/log");
    e.getMessage().addReaction(ReactionEmoji.of("👌"));
  }

}
