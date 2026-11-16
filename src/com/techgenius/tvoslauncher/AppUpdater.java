package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.pm.PackageInfo;

/**
 * Installed-version metadata only for the custom source build.
 * No release checks, cached release values, downloads or installation actions.
 * Keeping this class name avoids unrelated changes to version-info consumers.
 */
public final class AppUpdater {
    private AppUpdater() { }

    public static String getCurrentVersion(Context c) {
        if (c != null) {
            try {
                PackageInfo info = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
                if (info != null && info.versionName != null && !info.versionName.isEmpty()) {
                    return info.versionName;
                }
            } catch (Exception ignored) {
                // Package metadata unavailable: do not invent a release version.
            }
        }
        return "unknown-custom";
    }
}
