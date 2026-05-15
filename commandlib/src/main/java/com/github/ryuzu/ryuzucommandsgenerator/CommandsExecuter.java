package com.github.ryuzu.ryuzucommandsgenerator;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CommandsExecuter implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        CommandData data = new CommandData(sender, command, label, args, false);
        for (CommandComposition constitution : CommandsGenerator.commands) {
            if(!constitution.getLabel().equals(label)) continue;
            if (!constitution.canExecute(data)) continue;
            constitution.process(data);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> commands = new ArrayList<>();
        CommandData data = new CommandData(sender, command, alias, args, true);
        for (CommandComposition constitution : CommandsGenerator.commands) {
            if(!constitution.getLabel().equals(alias)) continue;
            List<String> complete = constitution.getTabComplete(data);
            if (!constitution.canTabComplete(data)) continue;
            commands.addAll(complete);
        }
        return commands;
    }
}
