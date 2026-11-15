package com.techgenius.tvoslauncher;
final class ContinuePolicy {
    static boolean suppressed(long dismissedPosition, long rawPosition, boolean playing, boolean newWatch) {
        return !(playing && (newWatch || (rawPosition >= 0 && rawPosition > dismissedPosition + 30000)));
    }
    static boolean visible(long lastPlayed, long now, int days) {
        return days == 0 || now - lastPlayed <= days * 86400000L;
    }
}
