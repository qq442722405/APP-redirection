const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'../..');
const pages=fs.readdirSync(path.join(root,'app/src/main/assets/layouts'));
for(const file of pages){
 const data=JSON.parse(fs.readFileSync(path.join(root,'app/src/main/assets/layouts',file),'utf8'));
 assert.equal(data.fontUnit,'px');assert.equal(data.targetDpi,160);assert.equal(data.globalDpi,160);
}
const main=JSON.parse(fs.readFileSync(path.join(root,'app/src/main/assets/layouts/主界面.json'),'utf8'));
const elements=new Map(main.elements.map(e=>[e.id,e]));
const cards=[9,16,17,18,19,20,21,22];
cards.forEach((id,i)=>{
 const card=elements.get('e'+id),selector=elements.get('e'+(32+i));
 assert.equal(card.left,selector.left);assert.equal(card.width,selector.width);
 assert(selector.top>=card.top+card.height,'preset box overlaps APP card');
 assert(selector.top+selector.height<elements.get('e12').top,'preset box overlaps footer');
 assert(selector.height>=selector.fontSize*1.2,'preset text cannot fit');
});
const java=path.join(root,'app/src/main/java/com/acc/acc');
for(const file of fs.readdirSync(java).filter(f=>f.endsWith('.java'))){
 const text=fs.readFileSync(path.join(java,file),'utf8');
 if(file!=='DesignTypography.java')assert(!text.includes('.setTextSize('),file+' has a font size outside the px helper');
 assert(!text.includes('COMPLEX_UNIT_SP'),file+' uses sp');
}
const html=fs.readFileSync(path.join(root,'design/程序界面设计.html'),'utf8');
for(const match of html.matchAll(/<script>([\s\S]*?)<\/script>/g))new Function(match[1]);
const embedded=JSON.parse(html.match(/const builtinLayouts=(.*);\n/)[1]);
for(const file of pages)assert.deepStrictEqual(embedded[file],JSON.parse(fs.readFileSync(path.join(root,'app/src/main/assets/layouts',file),'utf8')),'editor defaults differ: '+file);
assert(!elements.has('e2')&&!elements.has('e11'),'main spacer frames must be removed');
const options=embedded['界面选项.json'].elements;
assert(!options.some(e=>(e.content||'').includes('壁纸')));
assert.deepStrictEqual([20,21,22].map(i=>options.find(e=>e.id==='e'+i).text),['紫色','黑色','灰色']);
console.log('8 preset boxes, '+pages.length+' DPI/font configs, centralized native fonts, themes and editor defaults: passed.');
