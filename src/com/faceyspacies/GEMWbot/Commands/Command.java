package com.faceyspacies.GEMWbot.Commands;

import java.util.List;

import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;

abstract class Command {

  abstract void runCommand(MessageReceivedEvent e, List<String> args);

  public boolean isAdmin(IGuild guild, IUser user) {
    for (IRole role : guild.getRolesForUser(user)) {
      if (role.getName().equals("rs wiki admins") || role.getName().equals("osrs wiki admins")) {
        return true;
      }
    }
    return false;

  }

}
