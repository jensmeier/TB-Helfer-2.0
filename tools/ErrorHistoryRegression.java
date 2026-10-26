package de.tbhelfer.server;

import java.util.ArrayList;
import java.util.List;

class ErrorHistoryRegression {
    private static int checks;
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private static List<ErrorHistory.Entry> add(List<ErrorHistory.Entry> old,long site,String raw,long time,int build){
        return ErrorHistory.record(old,ErrorHistory.Operation.SEND,ErrorHistory.ContextKind.SITE,site,raw,time,build);
    }
    public static void main(String[] args){
        List<ErrorHistory.Entry> empty=new ArrayList<>();
        List<ErrorHistory.Entry> list=add(empty,5,"PERMISSION_REQUIRED:files.view Bearer TOP_SECRET https://secret.invalid",1000,200084);
        check(empty.isEmpty(),"model never mutates old snapshot");
        ErrorHistory.Entry first=list.get(0);
        check(first.code==ErrorHistory.Code.FILES_VIEW,"permission classified");
        list=add(list,5,"PERMISSION_REQUIRED:files.view different raw text",2000,200084);
        check(list.size()==1&&list.get(0).count==2,"repeats coalesced independent of raw/private text");
        check(list.get(0).id.equals(first.id),"deep link stable across repeats");
        check(list.get(0).firstMs==1000&&list.get(0).lastMs==2000,"first and latest preserved");
        list=add(list,6,"PERMISSION_REQUIRED:files.view",3000,200084);
        check(list.size()==2,"different worksites stay separate");
        list=add(list,5,"PERMISSION_REQUIRED:files.view",4000,200085);
        check(list.size()==3,"different builds stay separate");
        ErrorHistory.Entry restored=ErrorHistory.restore(first.id,first.operation.name(),first.contextKind.name(),first.contextId,first.code.name(),first.firstMs,first.lastMs,first.count,first.build);
        check(restored.id.equals(first.id)&&restored.code==first.code&&restored.contextId==5,"safe fields reconstruct after persistence");
        String export=ErrorHistory.export(list);
        check(!export.contains("TOP_SECRET")&&!export.contains("https://")&&!export.contains("Bearer"),"export never contains credential/raw URL");
        List<ErrorHistory.Entry> privateError=add(empty,0,"PRIVATE_BTB_TEXT password=secret someone@example.test /private/photo.jpg",1,1);
        String neutral=ErrorHistory.export(privateError);
        check(!neutral.contains("PRIVATE_BTB_TEXT")&&!neutral.contains("password")&&!neutral.contains("example.test")&&!neutral.contains("photo.jpg"),"unknown messages cannot leak contents");
        check(ErrorHistory.classify("Unable to resolve host 'secret-host'")==ErrorHistory.Code.DNS,"DNS");
        check(ErrorHistory.classify("Servername konnte nicht aufgelöst werden")==ErrorHistory.Code.DNS,"German DNS");
        check(ErrorHistory.classify("SocketTimeoutException")==ErrorHistory.Code.TIMEOUT,"timeout");
        check(ErrorHistory.classify("Handy offline · kein Netzwerk")==ErrorHistory.Code.OFFLINE,"offline");
        check(ErrorHistory.classify("HTTP 503 secret response")==ErrorHistory.Code.HTTP5XX,"server HTTP error");
        check(ErrorHistory.classify("Serverfehler (403)")==ErrorHistory.Code.HTTP403,"permission HTTP");
        check(ErrorHistory.classify("VERSION_CONFLICT")==ErrorHistory.Code.CONFLICT,"version conflict");
        check(ErrorHistory.classify("Gerätefreigabe ist nicht mehr gültig")==ErrorHistory.Code.AUTH,"device authorization");
        check(ErrorHistory.classify("Serverprüfung fehlgeschlagen")==ErrorHistory.Code.CONNECTION,"probe failure area");
        List<ErrorHistory.Entry> bounded=empty;
        for(int n=1;n<=250;n++)bounded=add(bounded,n,"new failure",n,1);
        check(bounded.size()==200,"hard 200-group limit");
        check(bounded.get(0).contextId==250&&bounded.get(199).contextId==51,"newest groups retained");
        bounded=add(bounded,51,"new failure",1000,1);
        check(bounded.size()==200&&bounded.get(0).contextId==51&&bounded.get(0).count==2,"repeat refreshes recency without extra group");
        List<ErrorHistory.Entry> reset=add(new ArrayList<>(),5,"PERMISSION_REQUIRED:files.view",9999,200084);
        check(reset.get(0).count==1&&!reset.get(0).id.equals(first.id),"new history starts fresh after clearing");
        boolean rejected=false;
        try{ErrorHistory.restore("private-id","SEND","SITE",0,"GENERIC",1,1,1,1);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"invalid stored ID rejected without raw export");
        rejected=false;
        try{ErrorHistory.restore(first.id,"SEND","SITE",0,"TOKEN_secret",1,1,1,1);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"only enumerated stored codes accepted");
        List<ErrorHistory.Entry> backwards=add(add(empty,1,"new failure",5000,1),1,"new failure",1000,1);
        check(backwards.get(0).lastMs==5000&&backwards.get(0).count==2,"clock rollback retains monotone latest observation");
        System.out.println("PASS: "+checks+" error-history checks (classification, privacy, bounds, repeats, restoration). Not Android persistence/UI E2E.");
    }
}
