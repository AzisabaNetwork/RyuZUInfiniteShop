package com.github.ryuzu.ryuzucommandsgenerator;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TabCompleter {
    private final List<List<Function<CommandData, List<String>>>> completes = new ArrayList<>();

    public TabCompleter(String command) {
        Arrays.stream(command.split("\\.")).skip(1).forEach(arg -> this.completes.add(new ArrayList<>(Collections.singleton(data -> new ArrayList<>(Collections.singletonList(arg))))));
    }

    public List<List<Function<CommandData, List<String>>>> getCompletes() {
        return completes;
    }

    public void addComplete(int index, Function<CommandData, List<String>> complete) {
        if(this.completes.size() < index + 1)
            for (int i = this.completes.size(); i < index + 1; i++)
                this.completes.add(new ArrayList<>());
        this.completes.get(index).add(complete);
    }

    public void setOnlinePlayerComplete(int index) {
        addComplete(index, data -> Bukkit.getServer().getOnlinePlayers().stream().map(HumanEntity::getName).sorted().collect(Collectors.toList()));
    }
}
