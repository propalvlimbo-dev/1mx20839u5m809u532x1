package ru.rooyzee.elytrixsubscribe.functions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.exceptions.AgainFunctionRegisterException;
import ru.rooyzee.elytrixsubscribe.exceptions.NoneFunctionException;
import ru.rooyzee.elytrixsubscribe.functions.impl.AntiFall;
import ru.rooyzee.elytrixsubscribe.functions.impl.AntiFood;
import ru.rooyzee.elytrixsubscribe.functions.impl.AutoTotem;
import ru.rooyzee.elytrixsubscribe.functions.impl.SpeedBoost;

/** Функции подписки. Питомцы живут отдельно — в PetManager. */
public class FunctionManager {
    private final List<Function> functions = new ArrayList<Function>();

    public FunctionManager(Main plugin) {
        try {
            this.register(new AntiFood(), plugin);
            this.register(new AutoTotem(), plugin);
            this.register(new AntiFall(), plugin);
            this.register(new SpeedBoost(), plugin);
        } catch (AgainFunctionRegisterException error) {
            throw new RuntimeException(error);
        }
    }

    public void register(Function function, Main plugin) throws AgainFunctionRegisterException {
        for (Function other : this.functions) {
            if (other.getName().equalsIgnoreCase(function.getName())) {
                throw new AgainFunctionRegisterException("Duplicate: " + function.getName());
            }
        }
        this.functions.add(function);
        Bukkit.getPluginManager().registerEvents(function, plugin);
    }

    public boolean has(String name) {
        return this.find(name) != null;
    }

    public Function find(String name) {
        if (name == null) return null;
        for (Function function : this.functions) {
            if (function.getName().equalsIgnoreCase(name)) return function;
        }
        return null;
    }

    public Function getFunction(String name) throws NoneFunctionException {
        Function function = this.find(name);
        if (function == null) throw new NoneFunctionException("Function " + name + " not found");
        return function;
    }

    public List<Function> all() {
        return Collections.unmodifiableList(this.functions);
    }
}
