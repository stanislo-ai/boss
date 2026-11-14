// usage: node tools/render.cjs <scene.html> <outDir> [fps] [duration] [from] [to]
const { chromium } = require('playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const [,, page_, outDir, fpsS='30', durS='15.2', fromS, toS] = process.argv;
const fps = +fpsS, dur = +durS, N = Math.round(fps*dur);
const root = path.resolve(__dirname, '..');
const types = {'.html':'text/html','.js':'text/javascript','.mjs':'text/javascript','.css':'text/css','.woff2':'font/woff2','.woff':'font/woff','.png':'image/png','.svg':'image/svg+xml','.jpg':'image/jpeg'};
const srv = http.createServer((q,r)=>{ const p = path.join(root, decodeURIComponent(q.url.split('?')[0]));
  fs.readFile(p,(e,b)=>{ if(e){r.writeHead(404);r.end();return;} r.writeHead(200,{'Content-Type':types[path.extname(p)]||'application/octet-stream'}); r.end(b); }); });
srv.listen(0, async () => {
  const port = srv.address().port; fs.mkdirSync(outDir,{recursive:true});
  const browser = await chromium.launch({ args:['--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport:{width:1080,height:1920}, deviceScaleFactor:1 });
  page.on('console', m=>console.log('[page]', m.text())); page.on('pageerror', e=>console.log('[err]', e.message));
  await page.goto(`http://localhost:${port}/${page_}?render=1`);
  await page.waitForFunction(()=>window.__ready===true, null, {timeout:120000});
  const from = fromS? +fromS : 0, to = toS? +toS : N;
  const t0 = Date.now();
  for (let i=from;i<to;i++){
    const b64 = await page.evaluate(t=>window.__frame(t), i/fps);
    fs.writeFileSync(path.join(outDir, `f_${String(i).padStart(4,'0')}.jpg`), Buffer.from(b64,'base64'));
    if (i%30===0) console.log(`frame ${i}/${to} ${((Date.now()-t0)/(i-from+1)).toFixed(0)}ms/f`);
  }
  await browser.close(); srv.close();
});
