package de.tbhelfer.server;
import java.util.List;
public final class SpeechDiagnosticsRegression {
    public static void main(String[] args) {
        int checks=0;
        for(String reason:List.of("audio","permission","busy","language","interrupted","storage","service")) {
            var entries=ErrorHistory.record(List.of(),ErrorHistory.Operation.SPEECH,ErrorHistory.ContextKind.NONE,0,"speech_"+reason,1234,200089);
            var e=entries.get(0);
            if(!e.code.name().equals("SPEECH_"+reason.toUpperCase(java.util.Locale.ROOT)))throw new AssertionError(reason);
            var restored=ErrorHistory.restore(e.id,e.operation.name(),e.contextKind.name(),e.contextId,e.code.name(),e.firstMs,e.lastMs,e.count,e.build);
            if(restored.operation!=ErrorHistory.Operation.SPEECH || restored.code!=e.code)throw new AssertionError("restore");
            checks+=2;
        }
        String privateText="Geheimer gesprochener Inhalt TOKEN-123";
        var entries=ErrorHistory.record(List.of(),ErrorHistory.Operation.SPEECH,ErrorHistory.ContextKind.NONE,0,privateText,1234,200089);
        if(ErrorHistory.export(entries).contains(privateText) || ErrorHistory.export(entries).contains("TOKEN-123"))throw new AssertionError("privacy");
        System.out.println("SPEECH_DIAGNOSTICS_OK checks="+(checks+1));
    }
}
