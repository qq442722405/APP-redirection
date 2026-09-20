package com.acc.acc;

import java.util.Arrays;
import java.util.Collections;

public class PresetSelectionTest {
    static void check(String label,int actual,int expected){
        if(actual!=expected)throw new AssertionError(label+": expected "+expected+", got "+actual);
    }
    public static void main(String[] args){
        check("direct launch",PresetSelection.resolve("","",Arrays.asList("id1","id2"),Arrays.asList("Left","Right")),PresetSelection.DIRECT);
        check("selected id",PresetSelection.resolve("id2","Right",Arrays.asList("id1","id2"),Arrays.asList("Left","Right")),1);
        check("renamed preset",PresetSelection.resolve("id2","Old name",Arrays.asList("id1","id2"),Arrays.asList("Left","New name")),1);
        check("reordered presets",PresetSelection.resolve("id2","Right",Arrays.asList("id2","id1"),Arrays.asList("Right","Left")),0);
        check("duplicate names",PresetSelection.resolve("id2","Same",Arrays.asList("id1","id2"),Arrays.asList("Same","Same")),1);
        check("deleted preset name reused",PresetSelection.resolve("deleted","Right",Arrays.asList("newId"),Arrays.asList("Right")),PresetSelection.MISSING);
        check("legacy name migration",PresetSelection.resolve("","Right",Arrays.asList("id1","id2"),Arrays.asList("Left","Right")),1);
        check("legacy missing",PresetSelection.resolve("","Removed",Arrays.asList("id1"),Arrays.asList("Left")),PresetSelection.MISSING);
        check("no presets direct",PresetSelection.resolve("","",Collections.emptyList(),Collections.emptyList()),PresetSelection.DIRECT);
        check("no presets selected",PresetSelection.resolve("id1","Left",Collections.emptyList(),Collections.emptyList()),PresetSelection.MISSING);
        System.out.println("Preset selection: 10 checks passed.");
    }
}
