const potrace = require('potrace'); const fs = require('fs');
potrace.trace('assets/logo_mask.png', { threshold: 128, blackOnWhite: false, turdSize: 50, optTolerance: 0.4 }, (err, svg) => {
  if (err) throw err; fs.writeFileSync('assets/logo.svg', svg);
  const d = [...svg.matchAll(/ d="([^"]+)"/g)].map(m=>m[1]); console.log(svg.slice(0,200)); console.log('paths', d.length, d.map(x=>x.length));
});
