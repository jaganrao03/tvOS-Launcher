package com.techgenius.tvoslauncher;
final class HermesContract {
    static boolean valid(String state, String request, String text) {
        return state != null && java.util.Arrays.asList("listening","thinking","reply","error","setup").contains(state)
                && request != null && !request.trim().isEmpty() && request.length()<=200
                && text != null && text.length()<=2000;
    }
    static boolean shouldSpeak(String state, boolean speak, String text) {
        return speak && ("reply".equals(state) || "error".equals(state)) && !text.isEmpty();
    }
    /** Process-wide, bounded IDs/state only. Unknown terminal events can never revive an evicted ID. */
    static final class Tracker {
        private final java.util.LinkedHashMap<String,String> states = new java.util.LinkedHashMap<>();
        private String current;
        synchronized boolean accept(String state, String id) {
            if (!valid(state,id,"")) return false;
            if ("setup".equals(state)) return true; // Never establishes a voice request.
            String prior = states.get(id);
            if (prior == null) {
                if (!"listening".equals(state)) return false;
                if (current != null) states.put(current,"dismissed");
                current = id;
            } else {
                if (!id.equals(current) || "dismissed".equals(prior)) return false;
                if ("reply".equals(prior) || "error".equals(prior)) return false;
                if ("thinking".equals(prior) && "listening".equals(state)) return false;
                if (prior.equals(state)) return false;
            }
            states.put(id,state);
            while (states.size()>128) states.remove(states.keySet().iterator().next());
            return true;
        }
        synchronized void dismiss(String id) {
            if (id != null && states.containsKey(id)) states.put(id,"dismissed");
        }
        synchronized int size() { return states.size(); }
    }
    static long timeout(String state) {
        return "setup".equals(state) ? 0 : ("listening".equals(state)||"thinking".equals(state) ? 240000 : 12000);
    }
}
