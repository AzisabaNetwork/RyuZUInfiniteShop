package com.github.ryuzu.ryuzucommandsgenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class CommandsGenerator {
    public static List<CommandComposition> commands = new ArrayList<>();

    public static CommandComposition registerCommand(String command, Consumer<CommandData> process) {
        return registerCommand(command, process, Collections.emptyList());
    }

    public static CommandComposition registerCommand(String command, Consumer<CommandData> process, String permission) {
        return registerCommand(command, process, Collections.singletonList(permission));
    }

    public static CommandComposition registerCommand(String command, Consumer<CommandData> process, List<String> permissions) {
        return registerCommand(command, process, permissions, data -> true);
    }

    public static CommandComposition registerCommand(String command, Consumer<CommandData> process, String permission, Predicate<CommandData> condition) {
        return registerCommand(command, process, Collections.singletonList(permission), condition, data -> true);
    }

    public static CommandComposition registerCommand(String command, Consumer<CommandData> process, List<String> permissions, Predicate<CommandData> condition) {
        return registerCommand(command, process, permissions, condition, data -> true);
    }


    public static CommandComposition registerCommand(String command, Consumer<CommandData> process, String permission, Predicate<CommandData> condition, Predicate<CommandData> tabcompletecondition) {
        return registerCommand(command, process, Collections.singletonList(permission), condition, tabcompletecondition);
    }

    public static CommandComposition registerCommand(String command, Consumer<CommandData> process, List<String> permissions, Predicate<CommandData> condition, Predicate<CommandData> tabcompletecondition) {
        CommandComposition constitution = new CommandComposition(command, process)
                .permissions(permissions)
                .condition(condition)
                .tabCompleteConditon(tabcompletecondition);
        CommandsGenerator.commands.add(constitution);
        return constitution;
    }
}
