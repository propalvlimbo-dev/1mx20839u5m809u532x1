package ru.rooyzee.elytrixsubscribe.task;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Consumer;
import ru.rooyzee.elytrixsubscribe.Main;

public class TimerTask extends BukkitRunnable {
    private int secondsLeft;
    private final Runnable endRunnable;
    private final Consumer<Integer> onDurationRunnable;

    public TimerTask(int timeInSecond, Runnable endRunnable, Consumer<Integer> onDurationRunnable) {
        this.secondsLeft = timeInSecond;
        this.endRunnable = endRunnable;
        this.onDurationRunnable = onDurationRunnable;
        this.runTaskTimer(Main.getInstance(), 0L, 20L);
    }

    public void run() {
        if (this.secondsLeft <= 0) {
            this.endRunnable.run();
            this.cancel();
        } else {
            this.onDurationRunnable.accept(this.secondsLeft);
            --this.secondsLeft;
        }
    }

    public int getSecondsLeft() {
        return this.secondsLeft;
    }
}