package org.mobicents.restcomm.android.sdk.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
 
public class NotifyMessage extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
         
        TextView txt=new TextView(this);
         
        txt.setText("Activity after click on notification");
        setContentView(txt);
    }
}