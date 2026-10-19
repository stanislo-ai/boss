const {chromium}=require('/opt/node-tools/node_modules/playwright');
(async()=>{const b=await chromium.launch();const dir=process.argv[2];
for(const [f,w,h,o] of [['icon.html',512,512,'icon_512.png'],['thumb.html',1920,1080,'thumbnail_1920x1080.png']]){
 const p=await b.newPage({viewport:{width:w,height:h}});await p.goto('file://'+dir+'/'+f);await p.waitForTimeout(500);
 await p.locator('#c').screenshot({path:'/home/user/boss/roblox-assets/'+o});}
await b.close();})();
