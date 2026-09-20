package com.acc.acc;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.util.*;

/** VISO wire format observed in the supplied APK; no third-party implementation is bundled. */
final class SimoVoiceProtocol {
    static final String PACKAGE="com.jidu.visoservice",SERVICE=PACKAGE+".VisoService";
    static final String DESCRIPTOR=PACKAGE+".aidl.IServiceInterface",CALLBACK=PACKAGE+".aidl.IAppAsyncCallback";
    static final String PANEL="com.acc.acc.voice.apps";
    static byte[] page(Context context,VoiceCommands registry){
        Parcel p=Parcel.obtain();
        try{
            p.writeString(context.getPackageName());p.writeString(PANEL);p.writeInt(1);p.writeInt(0);p.writeInt(registry.commands.size());
            for(VoiceCommands.Command c:registry.commands){
                p.writeInt(1);p.writeInt(c.viewId);p.writeString(c.id);p.writeString("Button");p.writeString("visoClick");p.writeString(c.phrase);p.writeStringList(Collections.singletonList(""));
            }
            return p.marshall();
        }finally{p.recycle();}
    }
    static void sendPage(Context context,IBinder service,byte[] bytes,IBinder callback,boolean remove)throws Exception{
        File file=File.createTempFile("acc-voice-",".parcel",context.getCacheDir());
        try{
            try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);out.getFD().sync();}
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)){
                Parcel p=Parcel.obtain();
                try{
                    p.writeInterfaceToken(DESCRIPTOR);p.writeInt(1);fd.writeToParcel(p,0);if(!remove)p.writeStrongBinder(callback);
                    if(!service.transact(remove?2:1,p,null,IBinder.FLAG_ONEWAY))throw new IOException("VISO 不支持页面登记事务");
                }finally{p.recycle();}
            }
        }finally{file.delete();}
    }
    static void result(IBinder service,Bundle original,int code)throws Exception{
        Bundle response=new Bundle(original);response.putInt("viso_result_code",code);
        Parcel p=Parcel.obtain(),reply=Parcel.obtain();
        try{p.writeInterfaceToken(DESCRIPTOR);p.writeInt(1);response.writeToParcel(p,0);if(!service.transact(6,p,reply,0))throw new IOException("VISO 不支持结果回报事务");reply.readException();}
        finally{reply.recycle();p.recycle();}
    }
}
