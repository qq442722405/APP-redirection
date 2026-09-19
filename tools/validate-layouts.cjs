const fs=require('fs'),path=require('path');
const directory=path.join(__dirname,'../app/src/main/assets/layouts');
const pages={'主界面':40,'新建预设窗口':46,'添加APP':26,'设置':16,'界面选项':23,'悬浮窗口设置':35,'自动启动项目':11,'权限与诊断':7,'添加悬浮按钮':26,'单图标操作':9,'手势APP选择':26,'SIMO语音启动':16,'功能测试':16,'关闭APP测试':14,'窗口屏幕测试':16,'SIMO功能测试':12,'测试日志':11};
const removed={'主界面':[2,11],'界面选项':[12,13,18],'手势APP选择':[22,23]};
for(const [name,count]of Object.entries(pages)){
 const data=JSON.parse(fs.readFileSync(path.join(directory,name+'.json'),'utf8'));
 const map=new Map(data.elements.map(e=>[e.id,e]));
 if(map.size!==data.elements.length)throw Error(name+'：控件 ID 重复');
 for(let i=0;i<count;i++)if(!(removed[name]||[]).includes(i)&&!map.has('e'+i))throw Error(name+'：缺少必要控件 e'+i+'，请保留控件并只调整位置/尺寸/文字');
 for(const i of removed[name]||[])if(map.has('e'+i))throw Error(name+'：已移除的旧控件 e'+i+' 不应保留');
 const root=map.get('e0');
 for(const e of data.elements){
  for(const key of ['left','top','width','height'])if(!Number.isFinite(e[key]))throw Error(name+' '+e.id+'：无效 '+key);
  if(e.width<=0||e.height<=0||e.left<0||e.top<0||e.left+e.width>root.width||e.top+e.height>root.height)throw Error(name+' '+e.id+'：超出根窗口或尺寸无效');
  if(e.fontSize!==undefined&&(!Number.isFinite(e.fontSize)||e.fontSize<=0))throw Error(name+' '+e.id+'：字号无效');
 }
 console.log(name+': '+map.size+' 个控件通过检查');
}
