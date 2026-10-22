package ru.rooyzee.elytrixsubscribe.database;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SQLite {
    private Connection connection;
    private final File dbFile;

    public SQLite(JavaPlugin plugin) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.dbFile = new File(plugin.getDataFolder(), "database.db");
        this.connect();
    }

    private synchronized void connect() {
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                return;
            }
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.dbFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized Connection getConnection() {
        try {
            if (this.connection == null || this.connection.isClosed()) {
                this.connect();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return this.connection;
    }

    // FIX: Добавлен synchronized для потокобезопасности
    public synchronized void update(String sql, Object... objs) {
        try (PreparedStatement p = this.getConnection().prepareStatement(sql)) {
            this.setArguments(objs, p);
            p.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // FIX: Добавлен synchronized для потокобезопасности
    public synchronized void query(String sql, ResultSetConsumer consumer, Object... objs) {
        try (PreparedStatement p = this.getConnection().prepareStatement(sql)) {
            this.setArguments(objs, p);
            try (ResultSet rs = p.executeQuery()) {
                consumer.accept(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setArguments(Object[] objs, PreparedStatement p) throws SQLException {
        for (int i = 0; i < objs.length; ++i) {
            p.setObject(i + 1, objs[i]);
        }
    }

    public void close() {
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                this.connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FunctionalInterface
    public interface ResultSetConsumer {
        void accept(ResultSet rs) throws SQLException;
    }
}