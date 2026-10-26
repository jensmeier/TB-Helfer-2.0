package de.tbhelfer.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Observational only: separate runs, monotonic time, no transport or retry decisions. */
public final class SyncActivityMonitor {
    private SyncActivityMonitor() {}
    private static final Map<Long, Run> RUNS = new LinkedHashMap<>();
    private static long sequence;
    public static final class Run {
        public final long id, startedMs, phaseStartedMs;
        public final String label, phase;
        private Run(long id, String label, String phase, long startedMs, long phaseStartedMs) {
            this.id=id; this.label=label; this.phase=phase; this.startedMs=startedMs; this.phaseStartedMs=phaseStartedMs;
        }
    }
    public static long nowMs() { return System.nanoTime()/1_000_000L; }
    public static long begin(String label) { return begin(label, nowMs()); }
    public static synchronized long begin(String label, long now) {
        long id=++sequence;
        RUNS.put(id,new Run(id,label,"Wird vorbereitet",now,now));
        return id;
    }
    public static void phase(long id, String phase) { phase(id,phase,nowMs()); }
    public static synchronized void phase(long id, String phase, long now) {
        Run before=RUNS.get(id);
        if(before!=null && !before.phase.equals(phase))
            RUNS.put(id,new Run(id,before.label,phase,before.startedMs,now));
    }
    public static synchronized void finish(long id) { RUNS.remove(id); }
    public static synchronized List<Run> snapshot() { return new ArrayList<>(RUNS.values()); }
    public static String duration(long ms) {
        long seconds=Math.max(0,ms)/1000;
        if(seconds<60)return seconds+" s";
        if(seconds<3600)return String.format(Locale.GERMAN,"%d min %02d s",seconds/60,seconds%60);
        return String.format(Locale.GERMAN,"%d h %02d min",seconds/3600,(seconds%3600)/60);
    }
    public static String describe(long now) {
        List<Run> runs=snapshot();
        if(runs.isEmpty())return "Kein laufender Abgleich erfasst.";
        StringBuilder text=new StringBuilder();
        for(Run run:runs) {
            if(text.length()>0)text.append("\n\n");
            text.append(run.label).append(": ").append(run.phase)
                .append("\nLäuft seit ").append(duration(now-run.startedMs))
                .append(" · Schritt seit ").append(duration(now-run.phaseStartedMs));
        }
        return text.append("\nRestzeit: noch nicht berechenbar.").toString();
    }
}
