package de.tbhelfer.server;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** No raw messages are retained. Persistence/export use only allowlisted classifications. */
public final class ErrorHistory {
    private ErrorHistory() {}
    public static final int LIMIT=200;
    public enum Operation {
        FULL_SYNC("Vollabgleich"), SEND("BTB senden"), STRUCTURE("Baustellenstruktur"),
        RECEIVE("Änderungen empfangen"), CATALOG("Katalog"), CONNECTION("Serverprüfung"), UPDATE("App-Update");
        public final String label; Operation(String label){this.label=label;}
    }
    public enum ContextKind { NONE, ROOT, SITE }
    public enum Code {
        DNS("Verbindung","DNS_RESOLUTION_FAILED","Servername konnte nicht aufgelöst werden."),
        TIMEOUT("Verbindung","NETWORK_TIMEOUT","Serverantwort blieb innerhalb des Zeitlimits aus."),
        OFFLINE("Verbindung","NETWORK_UNAVAILABLE","Keine nutzbare Netzwerkverbindung."),
        FILES_VIEW("Berechtigungen","PERMISSION_REQUIRED:files.view","Dateizugriff für diese Baustelle wurde verweigert."),
        PERMISSION("Berechtigungen","PERMISSION_REQUIRED","Eine erforderliche Berechtigung fehlt."),
        AUTH("Berechtigungen","AUTH_REQUIRED","Gerätefreigabe oder Anmeldung muss geprüft werden."),
        CONFLICT("Synchronisation","VERSION_CONFLICT","Versionskonflikt: Änderungen müssen geprüft werden."),
        HTTP403("Berechtigungen","HTTP_403","Server hat den Zugriff verweigert."),
        HTTP404("Synchronisation","HTTP_404","Angefragte Serverfunktion oder Daten nicht gefunden."),
        HTTP429("Verbindung","HTTP_429","Server begrenzt vorübergehend die Anfragen."),
        HTTP5XX("Verbindung","HTTP_5XX","Server meldet einen internen Fehler."),
        FILES("Bilder/Dateien","FILE_SYNC_FAILED","Bilder oder Dateien konnten nicht abgeglichen werden."),
        CONNECTION("Verbindung","CONNECTION_CHECK_FAILED","Serverprüfung ist fehlgeschlagen."),
        API("Verbindung","INVALID_API_RESPONSE","Serverantwort entspricht nicht dem erwarteten API-Format."),
        GENERIC("Synchronisation","OPERATION_FAILED","Der Vorgang konnte nicht abgeschlossen werden.");
        public final String area,technical,message;
        Code(String area,String technical,String message){this.area=area;this.technical=technical;this.message=message;}
    }
    public static Code classify(String raw) {
        String s=raw==null?"":raw.substring(0,Math.min(raw.length(),8192)).toLowerCase(Locale.ROOT);
        if(s.contains("permission_required:files.view"))return Code.FILES_VIEW;
        if(s.contains("permission_required"))return Code.PERMISSION;
        if(s.contains("unknownhost")||s.contains("unable to resolve")||s.contains("nicht aufgelöst")||s.contains("dns"))return Code.DNS;
        if(s.contains("timeout")||s.contains("timed out")||s.contains("zeitlimit")||s.contains("prüftimeout"))return Code.TIMEOUT;
        if(s.contains("kein netzwerk")||s.contains("offline")||s.contains("network is unreachable"))return Code.OFFLINE;
        if(s.contains("version_conflict")||s.contains("versionskonflikt")||s.contains("http 409"))return Code.CONFLICT;
        if(s.contains("device_revoked")||s.contains("user_disabled")||s.contains("user_expired")||s.contains("nicht aktiviert")||s.contains("nicht für den server aktiviert")||s.contains("http 401")||s.contains("gerätefreigabe"))return Code.AUTH;
        if(s.matches("(?s).*http\\s*403.*")||s.contains("serverfehler (403)"))return Code.HTTP403;
        if(s.matches("(?s).*http\\s*404.*")||s.contains("serverfehler (404)"))return Code.HTTP404;
        if(s.matches("(?s).*http\\s*429.*")||s.contains("serverfehler (429)"))return Code.HTTP429;
        if(s.matches("(?s).*(http\\s*5[0-9]{2}|serverfehler \\(5[0-9]{2}\\)).*"))return Code.HTTP5XX;
        if(s.contains("api-antwort unerwartet"))return Code.API;
        if(s.contains("serverprüfung fehlgeschlagen"))return Code.CONNECTION;
        if(s.contains("bild")||s.contains("datei")||s.contains("foto"))return Code.FILES;
        return Code.GENERIC;
    }
    public static final class Entry {
        public final String id;
        public final Operation operation;
        public final ContextKind contextKind;
        public final long contextId,firstMs,lastMs,count;
        public final int build;
        public final Code code;
        private Entry(String id,Operation operation,ContextKind contextKind,long contextId,Code code,
                      long firstMs,long lastMs,long count,int build){
            this.id=id;this.operation=operation;this.contextKind=contextKind;
            this.contextId=contextKind==ContextKind.NONE?0:Math.max(0,contextId);this.code=code;
            this.firstMs=Math.max(0,firstMs);this.lastMs=Math.max(this.firstMs,lastMs);
            this.count=Math.max(1,count);this.build=Math.max(0,build);
        }
    }
    public static Entry restore(String id,String operation,String kind,long contextId,String code,long first,long last,long count,int build){
        if(id==null||!id.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException("Invalid diagnostic ID");
        return new Entry(UUID.fromString(id).toString(),Operation.valueOf(operation),ContextKind.valueOf(kind),contextId,Code.valueOf(code),first,last,count,build);
    }
    public static List<Entry> record(List<Entry> old,Operation op,ContextKind kind,long contextId,String raw,long now,int build){
        Code code=classify(raw);List<Entry> result=new ArrayList<>(old);Entry previous=null;
        long context=kind==ContextKind.NONE?0:Math.max(0,contextId);
        for(Entry e:old)if(e.operation==op&&e.contextKind==kind&&e.contextId==context&&e.code==code&&e.build==build){previous=e;break;}
        if(previous!=null)result.remove(previous);
        result.add(new Entry(previous==null?UUID.randomUUID().toString():previous.id,op,kind,context,code,
            previous==null?now:previous.firstMs,Math.max(now,previous==null?0:previous.lastMs),
            previous==null?1:(previous.count==Long.MAX_VALUE?Long.MAX_VALUE:previous.count+1),build));
        result.sort(Comparator.comparingLong((Entry e)->e.lastMs).reversed());
        return new ArrayList<>(result.subList(0,Math.min(LIMIT,result.size())));
    }
    private static String date(long time){return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.GERMAN).format(new Date(time));}
    public static String summary(Entry e){return date(e.lastMs)+" · "+e.code.area+" · "+e.count+" Meldung(en)\n"+e.code.message;}
    public static String describe(Entry e){
        return e.code.message+"\n\nTechnische Details\nBereich: "+e.code.area+"\nVorgang: "+e.operation.label+
            "\nCode: "+e.code.technical+"\nErstmals: "+date(e.firstMs)+"\nZuletzt: "+date(e.lastMs)+
            "\nMeldungen: "+e.count+"\nBuild: "+e.build+
            (e.contextKind==ContextKind.NONE?"\nBaustelle: nicht zugeordnet":"\nLokaler Baustellenbezug: "+e.contextKind.name()+" "+e.contextId);
    }
    public static String export(List<Entry> entries){
        StringBuilder s=new StringBuilder("TB-Helfer – lokales Fehlerprotokoll\nKeine Rohantworten, Tokens oder Fachinhalte enthalten.\n");
        for(Entry e:entries)s.append("\n---\n").append(describe(e)).append('\n');return s.toString();
    }
}
