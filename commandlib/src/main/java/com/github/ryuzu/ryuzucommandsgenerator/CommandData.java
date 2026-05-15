package com.github.ryuzu.ryuzucommandsgenerator;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class CommandData {
    private final CommandSender sender;
    private final Command command;
    private final String label;
    private final String[] args;
    private final boolean isTabComplete;

    public CommandData(CommandSender sender, Command command, String label, String[] args, boolean isTabComplete) {
        this.sender = sender;
        this.command = command;
        this.label = label;
        this.args = args;
        this.isTabComplete = isTabComplete;
    }

    public CommandSender getSender() {
        return sender;
    }

    public Command getCommand() {
        return command;
    }

    public String getLabel() {
        return label;
    }

    public String[] getArgs() {
        return args;
    }

    public boolean isTabComplete() {
        return isTabComplete;
    }

    public void sendMessage(String msg) {
        if (isTabComplete) return;
        sender.sendMessage(msg);
    }
}
