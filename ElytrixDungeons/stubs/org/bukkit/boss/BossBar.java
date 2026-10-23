package org.bukkit.boss;
import java.util.List;
import org.bukkit.entity.Player;
public interface BossBar {
    String getTitle();
    void setTitle(String title);
    BarColor getColor();
    void setColor(BarColor color);
    BarStyle getStyle();
    void setStyle(BarStyle style);
    double getProgress();
    void setProgress(double progress);
    void addPlayer(Player player);
    void removePlayer(Player player);
    void removeAll();
    List<Player> getPlayers();
    void setVisible(boolean visible);
    boolean isVisible();
}
