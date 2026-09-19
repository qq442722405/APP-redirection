package com.acc.acc;

final class ThemePalette {
    static final String[] IDS={"purple","black","gray"};
    static final String[] NAMES={"紫色","黑色","灰色"};
    final String id;
    final int panel,panelEnd,button,buttonEnd,selected,border,accent;
    private ThemePalette(String id,int panel,int panelEnd,int button,int buttonEnd,int selected,int border,int accent){
        this.id=id;this.panel=panel;this.panelEnd=panelEnd;this.button=button;this.buttonEnd=buttonEnd;this.selected=selected;this.border=border;this.accent=accent;
    }
    static ThemePalette forId(String id){
        if("black".equals(id))return new ThemePalette("black",0xff121212,0xff080808,0xff303030,0xff202020,0xff515151,0xff555555,0xffeeeeee);
        if("gray".equals(id))return new ThemePalette("gray",0xff383c43,0xff2d3036,0xff565c66,0xff444a54,0xff707884,0xff7e8794,0xffedf2f7);
        return new ThemePalette("purple",0xff251b36,0xff1a1228,0xff7044a2,0xff533079,0xff8352b6,0xff705785,0xffe1c4ff);
    }
}
