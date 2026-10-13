package org.bordenj.hoyopark;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;


public final class Main extends JavaPlugin{

    private Events events;
    @Override
    public void onEnable() {
        // Plugin startup logic
        events = new Events(this);
        getServer().getPluginManager().registerEvents(events, this);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Objects.requireNonNull(p.getAttribute(Attribute.MAX_ABSORPTION)).setBaseValue(20.0);
            events.addStates(p);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        events.cleanup();
    }


}
