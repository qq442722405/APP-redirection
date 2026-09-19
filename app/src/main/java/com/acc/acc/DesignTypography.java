package com.acc.acc;

import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;
import android.widget.TextView;

/** The HTML editor exports pixel font sizes. At 160 dpi no numeric conversion is needed. */
final class DesignTypography {
    private DesignTypography() {}
    static void setPx(TextView text,float pixels){
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX,pixels);
        text.setIncludeFontPadding(false);
    }
    // Keep Android-supplied dialog/list text at the default font preference as well.
    // This only changes our context; it never changes the vehicle's system settings.
    static Context fixedFonts(Context base){
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.fontScale=1f;
        return base.createConfigurationContext(config);
    }
}
