package com.acc.acc;

import android.content.SharedPreferences;
import java.util.*;

/** Dialog-local overlay: only touched keys are committed, never overwrite service position updates. */
final class PreferenceDraft implements SharedPreferences {
    private final SharedPreferences source;
    private final Map<String,Object> values=new HashMap<>();
    private final Set<String> removed=new HashSet<>();
    PreferenceDraft(SharedPreferences source){this.source=source;}
    boolean save(){
        Editor out=source.edit();
        for(String k:removed)out.remove(k);
        for(Map.Entry<String,Object> e:values.entrySet()){
            String k=e.getKey(); Object v=e.getValue();
            if(v instanceof Boolean)out.putBoolean(k,(Boolean)v);
            else if(v instanceof Integer)out.putInt(k,(Integer)v);
            else if(v instanceof Long)out.putLong(k,(Long)v);
            else if(v instanceof Float)out.putFloat(k,(Float)v);
            else if(v instanceof String)out.putString(k,(String)v);
            else if(v instanceof Set)out.putStringSet(k,new HashSet<>((Set<String>)v));
        }
        boolean ok=out.commit(); if(ok){values.clear();removed.clear();} return ok;
    }
    private Object value(String k,Object fallback){
        if(removed.contains(k))return fallback;
        if(values.containsKey(k))return values.get(k);
        Map<String,?> stored=source.getAll();
        return stored.containsKey(k)?stored.get(k):fallback;
    }
    public Map<String,?> getAll(){Map<String,Object> all=new HashMap<>(source.getAll());for(String k:removed)all.remove(k);all.putAll(values);return all;}
    public String getString(String k,String d){return (String)value(k,d);}
    public Set<String> getStringSet(String k,Set<String>d){Set<String> s=(Set<String>)value(k,d);return s==null?null:new HashSet<>(s);}
    public int getInt(String k,int d){return ((Number)value(k,d)).intValue();}
    public long getLong(String k,long d){return ((Number)value(k,d)).longValue();}
    public float getFloat(String k,float d){return ((Number)value(k,d)).floatValue();}
    public boolean getBoolean(String k,boolean d){return (Boolean)value(k,d);}
    public boolean contains(String k){return !removed.contains(k)&&(values.containsKey(k)||source.contains(k));}
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
    public Editor edit(){return new Editor(){
        private final Map<String,Object> pending=new HashMap<>();
        private final Set<String> deletes=new HashSet<>();
        private boolean clear;
        private Editor put(String k,Object v){if(v==null)return remove(k);pending.put(k,v);deletes.remove(k);return this;}
        public Editor putString(String k,String v){return put(k,v);}
        public Editor putStringSet(String k,Set<String> v){return put(k,v==null?null:new HashSet<>(v));}
        public Editor putInt(String k,int v){return put(k,v);}
        public Editor putLong(String k,long v){return put(k,v);}
        public Editor putFloat(String k,float v){return put(k,v);}
        public Editor putBoolean(String k,boolean v){return put(k,v);}
        public Editor remove(String k){pending.remove(k);deletes.add(k);return this;}
        public Editor clear(){clear=true;return this;}
        public boolean commit(){apply();return true;}
        public void apply(){if(clear){removed.addAll(getAll().keySet());values.clear();}for(String k:deletes){values.remove(k);removed.add(k);}for(String k:pending.keySet())removed.remove(k);values.putAll(pending);}
    };}
}
