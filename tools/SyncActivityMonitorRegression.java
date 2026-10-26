package de.tbhelfer.server;

/** Standalone regression of the production Java monitor; no Android/Gradle needed. */
class SyncActivityMonitorRegression {
    private static int checks;
    private static void check(boolean value,String message) {
        checks++;if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        long send=SyncActivityMonitor.begin("Senden",1000);
        long receive=SyncActivityMonitor.begin("Empfang",2000);
        SyncActivityMonitor.phase(send,"BTB 1 von 2",3000);
        String text=SyncActivityMonitor.describe(11000);
        check(text.contains("Senden: BTB 1 von 2"),"outgoing phase visible");
        check(text.contains("Läuft seit 10 s · Schritt seit 8 s"),"run and phase clocks distinct");
        check(text.contains("Empfang:"),"concurrent receive retained");
        check(text.contains("Restzeit: noch nicht berechenbar"),"unknown ETA honest");
        check(!text.contains("%"),"no fake percentage");
        SyncActivityMonitor.phase(send,"BTB 1 von 2",9000);
        check(SyncActivityMonitor.describe(11000).contains("Schritt seit 8 s"),"duplicate phase does not reset clock");
        SyncActivityMonitor.finish(send);
        check(SyncActivityMonitor.snapshot().size()==1,"ending send does not erase receiver");
        SyncActivityMonitor.phase(send,"late callback",12000);
        check(SyncActivityMonitor.snapshot().size()==1,"late callback cannot resurrect completed run");
        SyncActivityMonitor.finish(receive);
        check(SyncActivityMonitor.describe(15000).equals("Kein laufender Abgleich erfasst."),"idle never claims all data synced");
        check(SyncActivityMonitor.duration(-1000).equals("0 s"),"negative age clamped");
        check(SyncActivityMonitor.duration(125000).equals("2 min 05 s"),"minutes formatted");
        check(SyncActivityMonitor.duration(3720000).equals("1 h 02 min"),"hours formatted");
        Thread[] threads=new Thread[8];
        for(int i=0;i<threads.length;i++)threads[i]=new Thread(()->{
            for(int n=0;n<100;n++){
                long id=SyncActivityMonitor.begin("Parallel",n);
                SyncActivityMonitor.phase(id,"Übertragung",n+1);
                SyncActivityMonitor.snapshot();SyncActivityMonitor.finish(id);
            }
        });
        for(Thread t:threads)t.start();for(Thread t:threads)t.join();
        check(SyncActivityMonitor.snapshot().isEmpty(),"parallel runs cleaned without leaked state");
        System.out.println("PASS: "+checks+" monitor checks, including 800 parallel run lifecycles. Not an Android/HTTP test.");
    }
}
