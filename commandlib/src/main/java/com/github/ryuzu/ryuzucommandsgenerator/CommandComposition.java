package com.github.ryuzu.ryuzucommandsgenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CommandComposition {
    private final String command;
    private final String label;
    private final String[] args;
    private final Consumer<CommandData> process;
    private List<String> permissions = new ArrayList<>();
    private Predicate<CommandData> condition = (data) -> true;
    private Predicate<CommandData> tabcompleteconditon = (data) -> true;
    private TabCompleter completer;

    public CommandComposition(String command, Consumer<CommandData> process) {
        this.command = command;
        this.label = command.split("\\.")[0];
        this.args = Arrays.stream(command.split("\\.")).skip(1).toArray(String[]::new);
        this.process = process;
        this.completer = new TabCompleter(command);
    }

    String getLabel() {
        return label;
    }

    void setPermissions(List<String> permissions) {
        this.permissions = new ArrayList<>(permissions);
    }

    void setCondition(Predicate<CommandData> condition) {
        this.condition = condition;
    }

    void setTabcompleteConditon(Predicate<CommandData> tabcompleteconditon) {
        this.tabcompleteconditon = tabcompleteconditon;
    }

    boolean hasPermission(CommandData data) {
        boolean has = permissions.stream().anyMatch(perm -> data.getSender().hasPermission(perm));
        if (!has) data.sendMessage(RyuZUCommandsGenerator.resistPermissionMessage);
        return has;
    }

    boolean canTabComplete(CommandData data) {
        return data.getArgs().length >= args.length && getTabComplete(data) != null && tabcompleteconditon.test(data) && hasPermission(data);
    }

    boolean canExecute(CommandData data) {
        if (!canTabComplete(data)) return false;
        return condition.test(data);
    }

    void process(CommandData data) {
        process.accept(data);
    }

    List<String> getTabComplete(CommandData data) {
        int length = Math.min(data.getArgs().length, args.length);
        if (length == 0) return new ArrayList<>();
        for (int i = 0; i < length; i++) {
            if (length - 1 == i) {
                if (data.isTabComplete()) {
                    if (!args[i].toUpperCase().startsWith(data.getArgs()[i].toUpperCase())) return null;
                } else if (!args[i].equalsIgnoreCase(data.getArgs()[i])) return null;
            } else {
                if (args[i].equalsIgnoreCase("#object#")) continue;
                if (!args[i].equalsIgnoreCase(data.getArgs()[i])) return null;
            }
        }
        if (args.length > data.getArgs().length) return null;
        if (completer.getCompletes().size() < data.getArgs().length) return new ArrayList<>();
        return completer.getCompletes().get(data.getArgs().length - 1).stream()
                .map(complete -> complete.apply(data))
                .flatMap(List::stream)
                .filter(complete -> complete.startsWith(data.getArgs()[data.getArgs().length - 1]))
                .collect(Collectors.toList());
    }

    public CommandComposition permissions(String... permissions) {
        setPermissions(Arrays.asList(permissions));
        return this;
    }

    public CommandComposition permissions(List<String> permissions) {
        setPermissions(permissions);
        return this;
    }

    public CommandComposition condition(Predicate<CommandData> condition) {
        setCondition(condition);
        return this;
    }

    public CommandComposition tabCompleteConditon(Predicate<CommandData> tabcompleteconditon) {
        setTabcompleteConditon(tabcompleteconditon);
        return this;
    }

    public CommandComposition complete(int index, List<String> complete) {
        completer.addComplete(index, data -> complete);
        return this;
    }

    public CommandComposition complete(int index, String... complete) {
        completer.addComplete(index, data -> Arrays.asList(complete));
        return this;
    }

    public CommandComposition complete(int index, Function<CommandData, List<String>> complete) {
        completer.addComplete(index, complete);
        return this;
    }

    public CommandComposition completePlayer(int index) {
        completer.setOnlinePlayerComplete(index);
        return this;
    }
}
