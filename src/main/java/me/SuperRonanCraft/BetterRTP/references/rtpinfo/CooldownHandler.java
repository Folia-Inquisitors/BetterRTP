package me.SuperRonanCraft.BetterRTP.references.rtpinfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import lombok.Getter;
import me.SuperRonanCraft.BetterRTP.BetterRTP;
import me.SuperRonanCraft.BetterRTP.references.database.DatabaseCooldowns;
import me.SuperRonanCraft.BetterRTP.references.database.DatabaseHandler;
import me.SuperRonanCraft.BetterRTP.references.file.FileOther;
import me.SuperRonanCraft.BetterRTP.references.player.HelperPlayer;
import me.SuperRonanCraft.BetterRTP.references.player.playerdata.PlayerData;
import me.SuperRonanCraft.BetterRTP.references.rtpinfo.worlds.WorldPlayer;
import me.SuperRonanCraft.BetterRTP.versions.AsyncHandler;

public class CooldownHandler {

    @Getter boolean enabled, loaded, cooldownByWorld;
    @Getter private int defaultCooldownTime; //Global Cooldown timer
    private int lockedAfter; //Rtp's before being locked
    private final List<Player> downloading = new ArrayList<>();
    //private final DatabaseCooldownsGlobal globalCooldown = new DatabaseCooldownsGlobal();

    public void load() {
        //configfile = new File(BetterRTP.getInstance().getDataFolder(), "data/cooldowns.yml");
        FileOther.FILETYPE config = FileOther.FILETYPE.CONFIG;
        enabled = config.getBoolean("Settings.Cooldown.Enabled");
        downloading.clear();
        loaded = false;
        if (enabled) {
            defaultCooldownTime = config.getInt("Settings.Cooldown.Time");
            BetterRTP.debug("Cooldown = " + defaultCooldownTime);
            lockedAfter = config.getInt("Settings.Cooldown.LockAfter");
            cooldownByWorld = config.getBoolean("Settings.Cooldown.PerWorld");
        }
        queueDownload();
    }

    private void queueDownload() {
        AsyncHandler.asyncLater(() -> {
            if (cooldownByWorld && !DatabaseHandler.getCooldowns().isLoaded()) {
               queueDownload();
               return;
            }
            if (!DatabaseHandler.getPlayers().isLoaded()) {
               queueDownload();
               return;
            }
            //OldCooldownConverter.loadOldCooldowns();
            //Load any online players cooldowns (mostly after a reload)
            for (Player p : Bukkit.getOnlinePlayers())
                loadPlayer(p);
            loaded = true;
        }, 10L);
    }

    public void add(Player player, World world) {
        if (!enabled) return;
        PlayerData playerData = getData(player);
        if (cooldownByWorld) {
            HashMap<World, CooldownData> cooldowns = playerData.getCooldowns();
            CooldownData data = cooldowns.getOrDefault(world, new CooldownData(player.getUniqueId(), 0L));
            playerData.setRtpCount(playerData.getRtpCount() + 1);
            data.setTime(System.currentTimeMillis());
            playerData.setGlobalCooldown(data.getTime());
            cooldowns.put(world, data);
            savePlayer(player, world, data, false);
        } else
            add(player);
    }

    private void add(Player player) {
        if (!enabled) return;
        PlayerData playerData = getData(player);
        playerData.setRtpCount(playerData.getRtpCount() + 1);
        playerData.setGlobalCooldown(System.currentTimeMillis());
        savePlayer(player, null, null, false);
    }

    @Nullable
    public CooldownData get(Player p, World world) {
        PlayerData data = getData(p);
        if (cooldownByWorld) {
            HashMap<World, CooldownData> cooldownData = getData(p).getCooldowns();
            if (data != null)
                return cooldownData.getOrDefault(world, null);
        } else if (data.getGlobalCooldown() > 0) {
            return new CooldownData(p.getUniqueId(), data.getGlobalCooldown());
        }
        return null;
    }

    public long timeLeft(CommandSender sendi, CooldownData data, WorldPlayer pWorld) {
        long cooldown = data.getTime();
        long timeLeft = ((cooldown / 1000) + pWorld.getCooldown()) - (System.currentTimeMillis() / 1000);
        //if (BetterRTP.getInstance().getSettings().isDelayEnabled() && !PermissionNode.BYPASS_DELAY.check(sendi))
        //    timeLeft = timeLeft + BetterRTP.getInstance().getSettings().getDelayTime();
        return timeLeft * 1000L;
    }

    public boolean locked(Player player) {
        return lockedAfter > 0 && getData(player).getRtpCount() >= lockedAfter;
    }

    public void removeCooldown(Player player, World world) {
        if (!enabled) return;
        PlayerData playerData = getData(player);
        CooldownData cooldownData = playerData.getCooldowns().getOrDefault(world, null);
        if (cooldownData != null) {
            if (lockedAfter > 0) {
                //uses.put(id, uses.getOrDefault(id, 1) - 1);
                if (playerData.getRtpCount() <= 0) { //Remove from file as well
                    savePlayer(player, world, cooldownData, true);
                    getData(player).getCooldowns().put(world, null);
                } else { //Keep the player cached
                    savePlayer(player, world, cooldownData, false);
                }
            } else { //Remove completely
                getData(player).getCooldowns().remove(world);
                savePlayer(player, world, cooldownData, true);
            }
        } else if (!cooldownByWorld) {
            getData(player).setGlobalCooldown(0);
            savePlayer(player, null, null, true);
        }
    }

    private void savePlayer(Player player, @Nullable World world, @Nullable CooldownData data, boolean remove) {
        AsyncHandler.async(() -> {
                if (world != null && data != null && getDatabaseWorlds() != null) { //Per World enabled?
                    if (!remove)
                        getDatabaseWorlds().setCooldown(world, data);
                    else
                        getDatabaseWorlds().removePlayer(data.getUuid(), world);
                }
                DatabaseHandler.getPlayers().setData(getData(player));
            });
    }

    public void loadPlayer(Player player) {
        if (!isEnabled()) return;
        downloading.add(player);
        PlayerData playerData = getData(player);
        if (getDatabaseWorlds() != null) //Per World enabled?
            for (World world : Bukkit.getWorlds()) {
                //Cooldowns
                CooldownData cooldown = getDatabaseWorlds().getCooldown(player.getUniqueId(), world);
                if (cooldown != null)
                    playerData.getCooldowns().put(world, cooldown);
            }
        //Player Data
        DatabaseHandler.getPlayers().setupData(playerData);
        downloading.remove(player);
    }

    public boolean loadedPlayer(Player player) {
        return !downloading.contains(player);
    }

    @Nullable
    private DatabaseCooldowns getDatabaseWorlds() {
        if (cooldownByWorld)
            return DatabaseHandler.getCooldowns();
        return null;
    }

    private PlayerData getData(Player p) {
        return HelperPlayer.getData(p);
    }
}

//Old yaml file based system, no longer useful as of 3.3.1
/*@Deprecated
    static class OldCooldownConverter {

        static void loadOldCooldowns() {
            File file = new File(BetterRTP.getInstance().getDataFolder(), "data/cooldowns.yml");
            YamlConfiguration config = getFile(file);
            if (config == null) return;
            if (config.getBoolean("Converted")) return;
            List<CooldownData> cooldownData = new ArrayList<>();
            for (String id : config.getConfigurationSection("").getKeys(false)) {
                try {
                    Long time = config.getLong(id + ".Time");
                    UUID uuid = UUID.fromString(id);
                    int uses = config.getInt(id + ".Attempts");
                    cooldownData.add(new CooldownData(uuid, time, uses));
                } catch (IllegalArgumentException e) {
                    //Invalid UUID
                }
            }
            config.set("Converted", true);
            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
            BetterRTP.getInstance().getLogger().info("Cooldowns converting to new database...");
            Bukkit.getScheduler().runTaskAsynchronously(BetterRTP.getInstance(), () -> {
                BetterRTP.getInstance().getDatabaseCooldowns().setCooldown(cooldownData);
                BetterRTP.getInstance().getLogger().info("Cooldowns have been converted to the new database!");
            });
        }

        private static YamlConfiguration getFile(File configfile) {
            if (!configfile.exists()) {
                return null;
            }
            try {
                YamlConfiguration config = new YamlConfiguration();
                config.load(configfile);
                return config;
            } catch (IOException | InvalidConfigurationException e) {
                e.printStackTrace();
            }
            return null;
        }
    }*/
