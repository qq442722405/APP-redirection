package com.acc.acc;

import java.util.List;

/** IDs survive edits and reordering. A deleted ID must never resolve by a reused name. */
final class PresetSelection {
    static final int DIRECT=-1, MISSING=-2;
    static int resolve(String id,String legacyName,List<String> ids,List<String> names){
        if(id!=null&&!id.isEmpty()){
            int index=ids.indexOf(id);return index>=0?index:MISSING;
        }
        if(legacyName==null||legacyName.isEmpty())return DIRECT;
        int index=names.indexOf(legacyName);return index>=0?index:MISSING;
    }
}
