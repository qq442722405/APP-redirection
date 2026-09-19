package com.acc.acc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Local allowlist. Ambiguous spoken names never resolve to an arbitrary APP. */
final class VoiceCommands {
    static final int LIMIT=500;
    static final class App {
        final String pkg,name,alias;
        App(String pkg,String name,String alias){this.pkg=pkg;this.name=name;this.alias=alias;}
    }
    static final class Command {
        final String id,phrase,pkg,name,action;
        final int viewId;
        Command(App app,String phrase){this(app,phrase,"open");}
        Command(App app,String phrase,String action){
            this.pkg=app.pkg;this.name=app.name;this.phrase=phrase;
            this.action=action;
            this.id="acc."+action+"."+digest(pkg+"\n"+phrase);this.viewId=id.hashCode()&0x7fffffff;
        }
    }
    final List<Command> commands;
    final List<String> conflicts;
    final int omitted;
    VoiceCommands(VoiceCommands base,List<Command> extra){
        TreeMap<String,LinkedHashMap<String,Command>> candidates=new TreeMap<>();
        ArrayList<Command> all=new ArrayList<>(base.commands);all.addAll(extra);
        for(Command c:all){if(c.phrase.length()>200)continue;candidates.computeIfAbsent(c.phrase,k->new LinkedHashMap<>()).put(c.id,c);}
        ArrayList<Command> valid=new ArrayList<>();ArrayList<String> ambiguous=new ArrayList<>(base.conflicts);int skipped=base.omitted;
        for(Map.Entry<String,LinkedHashMap<String,Command>> e:candidates.entrySet()){
            if(e.getValue().size()!=1||ambiguous.contains(e.getKey())){if(!ambiguous.contains(e.getKey()))ambiguous.add(e.getKey());continue;}
            if(valid.size()==LIMIT){skipped++;continue;}valid.add(e.getValue().values().iterator().next());
        }
        commands=Collections.unmodifiableList(valid);conflicts=Collections.unmodifiableList(ambiguous);omitted=skipped;
    }
    VoiceCommands(List<App> apps){
        TreeMap<String,LinkedHashMap<String,App>> candidates=new TreeMap<>();
        for(App app:apps){
            if(app.pkg==null||app.pkg.isEmpty()||app.pkg.startsWith("action:"))continue;
            for(String spoken:Arrays.asList(app.name,app.alias)){
                String name=normalize(spoken);if(name.isEmpty()||name.length()>198)continue;
                String phrase="打开"+name;
                candidates.computeIfAbsent(phrase,k->new LinkedHashMap<>()).put(app.pkg,app);
            }
        }
        ArrayList<Command> valid=new ArrayList<>();ArrayList<String> ambiguous=new ArrayList<>();int skipped=0;
        for(Map.Entry<String,LinkedHashMap<String,App>> entry:candidates.entrySet()){
            if(entry.getValue().size()!=1){ambiguous.add(entry.getKey());continue;}
            if(valid.size()==LIMIT){skipped++;continue;}
            valid.add(new Command(entry.getValue().values().iterator().next(),entry.getKey()));
        }
        commands=Collections.unmodifiableList(valid);conflicts=Collections.unmodifiableList(ambiguous);omitted=skipped;
    }
    Command match(String id,String phrase){
        if(id!=null&&!id.isEmpty()){
            for(Command c:commands)if(c.id.equals(id))return c;
            return null; // A stale/foreign ID must not fall through to another command.
        }
        String normalized=normalize(phrase);
        for(Command c:commands)if(c.phrase.equals(normalized))return c;
        return null;
    }
    static String normalize(String value){return value==null?"":value.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    static String digest(String value){
        try{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
}
