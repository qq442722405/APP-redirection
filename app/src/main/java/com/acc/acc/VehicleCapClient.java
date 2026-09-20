package com.acc.acc;

import android.content.*;
import android.os.*;
import java.util.*;

/** Read-only CAP queries reconstructed from the supplied APK protocol. */
final class VehicleCapClient {
    static final String PACKAGE="com.jidu.capservice",DESCRIPTOR="com.jidu.capservice.aidl.CapServiceAidlInterface";
    private final Context context;
    volatile IBinder remote;volatile String status="尚未连接";
    private boolean bound,closed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){if(closed)return;try{if(!DESCRIPTOR.equals(binder.getInterfaceDescriptor())){status="CAP 接口版本不匹配";return;}remote=binder;status="车辆数据服务已连接";}catch(Exception e){status="连接失败："+e.getClass().getSimpleName();}}
        public void onServiceDisconnected(ComponentName n){remote=null;status="车辆数据连接中断";retry();}
        public void onBindingDied(ComponentName n){remote=null;status="车辆数据服务重启";retry();}
        public void onNullBinding(ComponentName n){status="车辆数据服务拒绝连接";retry();}
    };
    private final Runnable reconnect=()->{unbind();connect();};
    private final Runnable timeout=()->{if(remote==null&&!closed){status="连接车辆数据超时";retry();}};
    VehicleCapClient(Context c){context=c.getApplicationContext();}
    void connect(){if(closed||bound)return;try{Intent intent=new Intent("service.intent.action.CAP").setComponent(new ComponentName(PACKAGE,PACKAGE+".CapService"));bound=context.bindService(intent,connection,Context.BIND_AUTO_CREATE);status=bound?"正在连接车辆数据…":"车机不支持或拒绝 CAP 服务";main.postDelayed(timeout,8000);}catch(RuntimeException e){status="CAP 连接失败："+e.getClass().getSimpleName();}}
    private void retry(){if(closed)return;main.removeCallbacks(reconnect);main.postDelayed(reconnect,10000);}
    private void unbind(){if(bound){try{context.unbindService(connection);}catch(IllegalArgumentException ignored){}bound=false;}remote=null;}
    Map<String,Object> read(VehicleData.Field field)throws Exception{
        IBinder binder=remote;if(binder==null)throw new IllegalStateException(status);
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{
            data.writeInterfaceToken(DESCRIPTOR);data.writeInt(1);
            data.writeString("carsettings");data.writeString(field.group);data.writeString(field.method);data.writeStringList(Collections.emptyList());data.writeStringList(Collections.emptyList());
            data.writeInt(1);new PersistableBundle().writeToParcel(data,0);
            if(!binder.transact(1,data,reply,0))throw new IllegalStateException("CAP 不支持查询事务");reply.readException();
            if(reply.readInt()==0)throw new IllegalStateException("CAP 没有返回数据");PersistableBundle bundle=PersistableBundle.CREATOR.createFromParcel(reply);
            Map<String,Object> values=new LinkedHashMap<>();for(String key:bundle.keySet())values.put(key,bundle.get(key));return values;
        }finally{reply.recycle();data.recycle();}
    }
    void close(){closed=true;main.removeCallbacksAndMessages(null);unbind();}
}
