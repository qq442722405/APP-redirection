package com.acc.acc;
import java.util.*;

public final class VoiceCommandsTest {
    static int checks;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    static VoiceCommands.App app(String pkg,String name,String alias){return new VoiceCommands.App(pkg,name,alias);}
    public static void main(String[] args){
        VoiceCommands empty=new VoiceCommands(Collections.emptyList());check(empty.commands.isEmpty(),"empty APP range");
        VoiceCommands base=new VoiceCommands(Arrays.asList(app("a.music","音乐播放器","听歌")));
        check(base.commands.size()==2,"label plus alias");
        check(base.match("","打开听歌").pkg.equals("a.music"),"alias routes to package");
        check(base.match("","删除听歌")==null,"only registered open action accepted");
        VoiceCommands.Command one=base.commands.get(0);
        check(base.match(one.id,"untrusted phrase")==one,"known identifier resolves registered command");
        check(base.match("stale-id",one.phrase)==null,"foreign identifier never falls through");
        VoiceCommands duplicates=new VoiceCommands(Arrays.asList(app("a.music","音乐","音乐"),app("a.music","音乐","音乐")));
        check(duplicates.commands.size()==1,"same APP duplicate names collapsed");
        VoiceCommands collisions=new VoiceCommands(Arrays.asList(app("a.music","音乐","甲音乐"),app("b.music","音乐","乙音乐")));
        check(collisions.match("","打开音乐")==null&&collisions.conflicts.size()==1,"ambiguous names rejected");
        check(collisions.match("","打开甲音乐").pkg.equals("a.music"),"unique alias survives shared label");
        VoiceCommands aliasCollision=new VoiceCommands(Arrays.asList(app("a.music","甲","乙"),app("b.music","乙","")));
        check(aliasCollision.match("","打开乙")==null,"alias cannot shadow another APP label");
        VoiceCommands reordered=new VoiceCommands(Arrays.asList(app("z.video","视频",""),app("a.music","音乐播放器","听歌")));
        check(reordered.match(one.id,"").pkg.equals("a.music"),"identifier stable across ordering and additions");
        check(new VoiceCommands(Collections.emptyList()).match(one.id,one.phrase)==null,"removed APP callback rejected");
        VoiceCommands ascii=new VoiceCommands(Arrays.asList(app("a.test"," TEST  App ","test app")));
        check(ascii.commands.size()==1&&ascii.match(""," 打开TEST   APP ")!=null,"case and whitespace normalization");
        check(new VoiceCommands(Arrays.asList(app("action:back","返回",""),app("a.blank"," ",""))).commands.isEmpty(),"system actions and blank names excluded");
        char[] longName=new char[199];Arrays.fill(longName,'a');
        check(new VoiceCommands(Arrays.asList(app("a.long",new String(longName),""))).commands.isEmpty(),"maximum phrase length");
        List<VoiceCommands.App> many=new ArrayList<>();for(int i=0;i<510;i++)many.add(app("a.p"+i,"应用"+i,""));
        VoiceCommands capped=new VoiceCommands(many);check(capped.commands.size()==500&&capped.omitted==10,"bounded VISO registration");
        check(capped.commands.stream().allMatch(c->c.id.length()<=200&&c.viewId>=0),"protocol identifier and view bounds");
        System.out.println("Voice commands: "+checks+" checks passed.");
    }
}
