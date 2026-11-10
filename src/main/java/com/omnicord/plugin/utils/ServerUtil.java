package com.omnicord.plugin.utils;

import org.bukkit.Bukkit;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.text.DecimalFormat;

/**
 * Utility for retrieving server metrics: TPS, MSPT, JVM Memory, and Uptime.
 * Uses reflection to support Spigot, Paper, and Purpur across 1.16 - 1.21+.
 */
public final class ServerUtil {

    private static final DecimalFormat DF = new DecimalFormat("0.00");

    private ServerUtil() {}

    /**
     * Retrieves the 1-minute, 5-minute, and 15-minute TPS array.
     */
    public static double[] getTPS() {
        try {
            // Paper / Purpur / Modern Spigot
            Method spigotMethod = Bukkit.getServer().getClass().getMethod("spigot");
            Object spigot = spigotMethod.invoke(Bukkit.getServer());
            Method getTPSMethod = spigot.getClass().getMethod("getTPS");
            return (double[]) getTPSMethod.invoke(spigot);
        } catch (Throwable t1) {
            try {
                Method getTPSMethod = Bukkit.class.getMethod("getTPS");
                return (double[]) getTPSMethod.invoke(null);
            } catch (Throwable t2) {
                return new double[]{20.0, 20.0, 20.0};
            }
        }
    }

    /**
     * Format primary TPS as a string with color indicator.
     */
    public static String getFormattedTPS() {
        double[] tps = getTPS();
        double currentTps = Math.min(20.0, tps[0]);
        return DF.format(currentTps);
    }

    /**
     * Retrieve average tick time (MSPT) if running Paper/Purpur.
     */
    public static double getMSPT() {
        try {
            Method method = Bukkit.class.getMethod("getAverageTickTime");
            return (double) method.invoke(null);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /**
     * Formatted uptime string (e.g. 3d 4h 12m 30s).
     */
    public static String getUptime() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds = uptimeMs / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(secs).append("s");

        return sb.toString().trim();
    }

    /**
     * Formatted memory usage string.
     */
    public static String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long allocatedMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = allocatedMemory - freeMemory;

        return usedMemory + "MB / " + maxMemory + "MB";
    }

    /**
     * Calculate memory percentage used.
     */
    public static int getMemoryPercentage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        if (maxMemory <= 0) return 0;
        return (int) ((usedMemory * 100) / maxMemory);
    }
}
