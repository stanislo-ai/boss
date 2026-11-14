// node tools/dbg.cjs page.html t out.jpg "js to eval before frame"
const { chromium } = require('playwright'); const http=require('http'),fs=require('fs'),path=require('path');
const [,,pg,t,out,js='']=process.argv; const root=path.resolve(__dirname,'..');
const types={'.html':'text/html','.js':'text/javascript','.css':'text/css','.woff2':'font/woff2','.png':'image/png','.svg':'image/svg+xml','.jpg':'image/jpeg','.json':'application/json'};
const srv=http.createServer((q,r)=>{const p=path.join(root,decodeURIComponent(q.url.split('?')[0]));fs.readFile(p,(e,b)=>{if(e){r.writeHead(404);r.end();return;}r.writeHead(200,{'Content-Type':types[path.extname(p)]||'application/octet-stream'});r.end(b);});});
srv.listen(0,async()=>{const b=await chromium.launch({args:['--use-angle=swiftshader','--enable-unsafe-swiftshader']});const p=await b.newPage({viewport:{width:1080,height:1920}});
p.on('pageerror',e=>console.log('[err]',e.message)); p.on('console',m=>console.log('[page]',m.text()));
await p.goto(`http://localhost:${srv.address().port}/${pg}?render=1`);await p.waitForFunction(()=>window.__ready===true,null,{timeout:120000});
if(js) await p.evaluate(js); for (const tt of t.split(',')){ const d=await p.evaluate(x=>window.__frame(x),+tt); fs.writeFileSync(out.replace('.jpg',`_${tt}.jpg`),Buffer.from(d,'base64'));}
await b.close();srv.close();});
