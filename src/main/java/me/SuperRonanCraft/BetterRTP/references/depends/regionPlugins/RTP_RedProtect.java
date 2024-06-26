package me.SuperRonanCraft.BetterRTP.references.depends.regionPlugins;

import org.bukkit.Location;

import br.net.fabiozumbi12.RedProtect.Bukkit.RedProtect;

public class RTP_RedProtect implements RegionPluginCheck {

    // TESTED (2.13.0)
    // RedProtect (v7.7.2)
    // https://www.spigotmc.org/resources/redprotect.15841/
    public boolean check(Location loc) {
        boolean result = true;
        if (REGIONPLUGINS.REDPROTECT.isEnabled())
            try {
                result = RedProtect.get().getAPI().getRegion(loc) == null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        return result;
    }
}
