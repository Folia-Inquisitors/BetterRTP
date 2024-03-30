package me.SuperRonanCraft.BetterRTP.references.helpers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.SuperRonanCraft.BetterRTP.BetterRTP;
import me.SuperRonanCraft.BetterRTP.player.rtp.RTP_ERROR_REQUEST_REASON;
import me.SuperRonanCraft.BetterRTP.player.rtp.RTP_PlayerInfo;
import me.SuperRonanCraft.BetterRTP.references.PermissionCheck;
import me.SuperRonanCraft.BetterRTP.references.PermissionNode;
import me.SuperRonanCraft.BetterRTP.references.rtpinfo.CooldownData;
import me.SuperRonanCraft.BetterRTP.references.rtpinfo.CooldownHandler;
import me.SuperRonanCraft.BetterRTP.references.rtpinfo.worlds.WorldPlayer;

public class HelperRTP_Check {

    public static RTP_ERROR_REQUEST_REASON canRTP(Player player, CommandSender sendi, WorldPlayer pWorld, RTP_PlayerInfo rtpInfo) {
        if (isRTPing(player)) { //Is RTP'ing
            return RTP_ERROR_REQUEST_REASON.IS_RTPING;
        }
        // Not forced and has 'betterrtp.world.<world>'
        if (sendi == player && !PermissionCheck.getAWorld(sendi, pWorld.getWorld().getName())) {
            return RTP_ERROR_REQUEST_REASON.NO_PERMISSION;
        }
        // Check disabled worlds
        if (getPl().getRTP().getDisabledWorlds().contains(pWorld.getWorld().getName())) {
            return RTP_ERROR_REQUEST_REASON.WORLD_DISABLED;
        }
        if (rtpInfo.isCheckCooldown() && isCoolingDown(player, pWorld)) { //Is Cooling down
            return RTP_ERROR_REQUEST_REASON.COOLDOWN;
        }
        if (rtpInfo.isTakeMoney() && !getPl().getEco().hasBalance(pWorld))
            return RTP_ERROR_REQUEST_REASON.PRICE_ECONOMY;
        if (rtpInfo.isTakeHunger() && !getPl().getEco().hasHunger(pWorld))
            return RTP_ERROR_REQUEST_REASON.PRICE_HUNGER;
        return null;
    }

    private static boolean isRTPing(Player player) {
        return getPl().getPInfo().getRtping().getOrDefault(player, false);
    }

    public static boolean isCoolingDown(Player player, WorldPlayer pWorld) {
        if (!applyCooldown(player))
            return false;
        return getCooldown(player, pWorld) > 0L || isLocked(player);
    }

    public static boolean isLocked(Player player) {
        return getPl().getCooldowns().locked(player);
    }

    public static long getCooldown(Player player, WorldPlayer pWorld) {
        CooldownHandler cooldownHandler = getPl().getCooldowns();
        if (!cooldownHandler.isLoaded() || !cooldownHandler.loadedPlayer(player)) { //Cooldowns have yet to download
            return 1L;
        }
        //Cooldown Data
        CooldownData cooldownData = getPl().getCooldowns().get(player, pWorld.getWorld());
        if (cooldownData != null) {
            if (cooldownData.getTime() == 0) //Global cooldown with nothing
                return 0;
            else if (isLocked(player)) { //Infinite cooldown (locked)
                return -1L;
            } else { //Normal cooldown
                return cooldownHandler.timeLeft(player, cooldownData, pWorld);
            }
        }
        return 0L;
    }

    public static boolean applyCooldown(Player player) {
        return getPl().getCooldowns().isEnabled()
                && !PermissionNode.BYPASS_COOLDOWN.check(player);
    }

    public static boolean applyDelay(Player player) {
        return getPl().getSettings().isDelayEnabled()
                && getPl().getSettings().getDelayTime() > 0
                && !PermissionNode.BYPASS_DELAY.check(player);
    }

    private static BetterRTP getPl() {
        return BetterRTP.getInstance();
    }
}
