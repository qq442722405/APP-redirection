package com.acc.acc;

import android.content.*;
import android.os.*;
import java.util.*;

/** One explicitly requested screen HOME command. Does not read vehicle telemetry. */
final class CapHomeTest {
    private static final String DESCRIPTOR="com.jidu.capservice.aidl.CapServiceAidlInterface";
    private static CapHomeTest active;
    private final Context context;private final int area;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean bound,finished;private volatile boolean expired,submitted,working;
    private CapHomeTest(Context c,int area){context=c.getApplicationContext();this.area=area;}
    static void start(Context c,int area){
        if(area!=1&&area!=2){CloseTestLog.add("CAP","未执行：未知区域");return;}
        if(active!=null){CloseTestLog.add("CAP","已有区域命令等待结果，请勿重复提交");return;}
        active=new CapHomeTest(c,area);active.connect();
    }
    private final Runnable timeout=()->{
        boolean inFlight;synchronized(this){expired=true;inFlight=submitted;}
        CloseTestLog.add("CAP",inFlight?"接口超过 10 秒未返回，效果未知；不自动重试。原调用仍可能完成。":"连接超过 10 秒，已取消本次测试");
        finish(!working);
    };
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            if(finished||expired)return;
            working=true;
            new Thread(()->{
                String message=null;
                try{
                    if(!DESCRIPTOR.equals(binder.getInterfaceDescriptor()))throw new IllegalStateException("CAP 接口标识不匹配");
                    if(expired)return;
                    Parcel request=Parcel.obtain(),reply=Parcel.obtain();
                    try{
                        request.writeInterfaceToken(DESCRIPTOR);request.writeInt(1);
                        request.writeString("SystemUI");request.writeString("Common");request.writeString("externalControl");
                        request.writeStringList(Collections.emptyList());request.writeStringList(Collections.emptyList());
                        PersistableBundle args=new PersistableBundle();args.putString("action","ACTION_HOME");args.putInt("area",area);
                        request.writeInt(1);args.writeToParcel(request,0);
                        synchronized(CapHomeTest.this){if(expired)return;submitted=true;}
                        if(!binder.transact(1,request,reply,0))throw new IllegalStateException("CAP 不支持事务 1");
                        reply.readException();PersistableBundle result=reply.readInt()==0?null:PersistableBundle.CREATOR.createFromParcel(reply);
                        if(result==null)message="空返回，效果未确认";
                        else{result.keySet();message="已收到返回（不代表窗口已收起）："+result.toString();}
                    }finally{reply.recycle();request.recycle();}
                }catch(Exception e){message="失败："+e.getClass().getSimpleName()+" "+e.getMessage();}
                finally{
                    final String output=message;main.post(()->{if(output!=null)CloseTestLog.add("CAP "+(area==1?"中屏":"右屏"),output);finish(true);});
                }
            },"cap-home-test").start();
        }
        @Override public void onServiceDisconnected(ComponentName name){if(!submitted){CloseTestLog.add("CAP","服务断开，未提交命令");finish(true);}}
        @Override public void onNullBinding(ComponentName name){CloseTestLog.add("CAP","服务未提供接口");finish(true);}
    };
    private void connect(){
        try{
            CloseTestLog.add("CAP","正在连接，申请"+(area==1?"中屏":"右屏")+"返回桌面；此操作针对整个区域");
            bound=context.bindService(new Intent("service.intent.action.CAP").setComponent(new ComponentName("com.jidu.capservice","com.jidu.capservice.CapService")),connection,Context.BIND_AUTO_CREATE);
            if(!bound){CloseTestLog.add("CAP","车机拒绝连接，未发送命令");finish(true);}else main.postDelayed(timeout,10000);
        }catch(RuntimeException e){CloseTestLog.add("CAP","连接失败："+e.getClass().getSimpleName());finish(true);}
    }
    private void finish(boolean release){finished=true;main.removeCallbacks(timeout);if(bound){try{context.unbindService(connection);}catch(RuntimeException ignored){}bound=false;}if(release&&active==this)active=null;}
}
