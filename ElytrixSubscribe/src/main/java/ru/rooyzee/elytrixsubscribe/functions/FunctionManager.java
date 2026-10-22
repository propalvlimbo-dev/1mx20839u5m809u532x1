package ru.rooyzee.elytrixsubscribe.functions;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import ru.rooyzee.elytrixsubscribe.exceptions.AgainFunctionRegisterException;
import ru.rooyzee.elytrixsubscribe.exceptions.NoneFunctionException;
import ru.rooyzee.elytrixsubscribe.functions.impl.AntiFall;
import ru.rooyzee.elytrixsubscribe.functions.impl.AntiFood;
import ru.rooyzee.elytrixsubscribe.functions.impl.AutoTotem;
import ru.rooyzee.elytrixsubscribe.functions.impl.ParrotFunction;
import ru.rooyzee.elytrixsubscribe.functions.impl.SpeedBoost;
import ru.rooyzee.elytrixsubscribe.sub.PlayerSub;

public class FunctionManager {
    private final List<Function> functionList = new ArrayList<>();

    public FunctionManager(Plugin plugin) {
        try {
            this.registerFunction(new ParrotFunction(), plugin);
            this.registerFunction(new AntiFood(), plugin);
            this.registerFunction(new AutoTotem(), plugin);
            this.registerFunction(new AntiFall(), plugin);
            this.registerFunction(new SpeedBoost(), plugin);
        } catch (AgainFunctionRegisterException var3) {
            throw new RuntimeException(var3);
        }
    }

    public void registerFunction(Function function, Plugin plugin) throws AgainFunctionRegisterException {
        if (this.functionList.stream().anyMatch((f) -> f.getName().equals(function.getName()))) {
            throw new AgainFunctionRegisterException("Duplicate registration: " + function.getName());
        } else {
            this.functionList.add(function);
            Bukkit.getPluginManager().registerEvents(function, plugin);
        }
    }

    public boolean hasFunction(String name) {
        return this.functionList.stream().anyMatch((f) -> f.getName().equals(name));
    }

    public Function getFunction(String name) throws NoneFunctionException {
        return this.functionList.stream().filter((f) -> f.getName().equals(name)).findFirst().orElseThrow(() ->
                new NoneFunctionException("Function " + name + " not found")
        );
    }

    public boolean isFunctionEnable(PlayerSub player, Function function) {
        return player.getEnabledFunctions().contains(function);
    }
}