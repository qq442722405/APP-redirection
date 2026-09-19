const fs=require('fs'),path=require('path');
const directory=path.join(__dirname,'../app/src/main/assets/layouts');
const pages={'主界面':32,'新建预设窗口':46,'添加APP':26,'设置':14,'界面选项':19,'悬浮窗口设置':35,'自动启动项目':11,'权限与诊断':7};
for(const [name,count]of Object.entries(pages)){
 const data=JSON.parse(fs.readFileSync(path.join(directory,name+'.json'),'utf8'));
 const map=new Map(data.elements.map(e=>[e.id,e]));
 if(map.size!==data.elements.length)throw Error(name+'：控件 ID 重复');
 for(let i=0;i<count;i++)if(!map.has('e'+i))throw Error(name+'：缺少必要控件 e'+i+'，请保留控件并只调整位置/尺寸/文字');
 const root=map.get('e0');
 for(const e of data.elements){
  for(const key of ['left','top','width','height'])if(!Number.isFinite(e[key]))throw Error(name+' '+e.id+'：无效 '+key);
  if(e.width<=0||e.height<=0||e.left<0||e.top<0||e.left+e.width>root.width||e.top+e.height>root.height)throw Error(name+' '+e.id+'：超出根窗口或尺寸无效');
  if(e.fontSize!==undefined&&(!Number.isFinite(e.fontSize)||e.fontSize<=0))throw Error(name+' '+e.id+'：字号无效');
 }
 console.log(name+': '+map.size+' 个控件通过检查');
}
