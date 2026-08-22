/* ============================================================
   MONIKA SZEWCZYK MAKE-UP — skrypt wspolny dla wszystkich podstron
   Priorytet: plynnosc na telefonie. Ciezkie efekty tylko na
   komputerze (myszka + szeroki ekran).
   ============================================================ */
(function(){
  'use strict';

  var reduce  = matchMedia('(prefers-reduced-motion: reduce)').matches;
  var desktop = matchMedia('(min-width: 960px)').matches &&
                matchMedia('(hover:hover) and (pointer:fine)').matches;

  /* ---------- rok w stopce ---------- */
  var yr = document.getElementById('yr');
  if (yr) yr.textContent = new Date().getFullYear();

  /* ============================================================
     1. NAWIGACJA — chowa sie przy przewijaniu w dol
     ============================================================ */
  var nav = document.getElementById('nav');
  var lastY = window.scrollY;
  var navTick = false;

  function onScrollNav(){
    var y = window.scrollY;
    if (nav){
      nav.classList.toggle('solid', y > 16);
      var down = y > lastY && y > 260;
      if (!document.body.classList.contains('menu-open')){
        nav.classList.toggle('hide', down);
      }
    }
    if (dock) dock.classList.toggle('show', y > 320);
    lastY = y;
    navTick = false;
  }

  /* ============================================================
     2. PASEK AKCJI NA DOLE (telefon)
     ============================================================ */
  var dock = document.getElementById('dock');

  /* ============================================================
     3. MENU PELNOEKRANOWE
     ============================================================ */
  var burger = document.getElementById('burger');
  var drawer = document.getElementById('drawer');

  function setMenu(open){
    if (!drawer || !burger) return;
    drawer.classList.toggle('open', open);
    burger.setAttribute('aria-expanded', open ? 'true' : 'false');
    document.body.classList.toggle('menu-open', open);
    document.documentElement.style.overflow = open ? 'hidden' : '';
    if (open){
      nav && nav.classList.remove('hide');
      /* schodkowe pojawianie sie pozycji menu */
      drawer.querySelectorAll('.dnav a').forEach(function(a, i){
        a.style.animationDelay = (0.05 + i * 0.045) + 's';
      });
      var first = drawer.querySelector('.dnav a');
      first && first.focus({ preventScroll: true });
    } else {
      burger.focus({ preventScroll: true });
    }
  }

  if (burger && drawer){
    burger.addEventListener('click', function(){
      setMenu(!drawer.classList.contains('open'));
    });
    drawer.querySelectorAll('a').forEach(function(a){
      a.addEventListener('click', function(){ setMenu(false); });
    });
    addEventListener('keydown', function(e){
      if (e.key === 'Escape' && drawer.classList.contains('open')) setMenu(false);
    });
    /* zamknij menu po przejsciu na szeroki ekran */
    matchMedia('(min-width: 1180px)').addEventListener('change', function(e){
      if (e.matches) setMenu(false);
    });
  }

  addEventListener('scroll', function(){
    if (!navTick){ navTick = true; requestAnimationFrame(onScrollNav); }
  }, { passive: true });
  onScrollNav();

  /* ============================================================
     4. NAGLOWEK — wjazd slowo po slowie
     ============================================================ */
  if (!reduce){
    document.querySelectorAll('[data-words]').forEach(function(el){
      var i = 0;
      el.querySelectorAll('.line').forEach(function(line){
        var txt = line.textContent.trim();
        line.textContent = '';
        var frag = document.createDocumentFragment();
        txt.split(' ').forEach(function(word, k, arr){
          var w = document.createElement('span');
          w.className = 'w';
          var inner = document.createElement('span');
          inner.textContent = word;
          inner.style.animationDelay = (0.15 + i * 0.08) + 's';
          w.appendChild(inner);
          frag.appendChild(w);
          if (k < arr.length - 1) frag.appendChild(document.createTextNode(' '));
          i++;
        });
        line.appendChild(frag);
      });
    });
  }

  /* ============================================================
     5. UJAWNIANIE TRESCI PRZY PRZEWIJANIU
     Klase dodaje skrypt — bez JS nic nie zostanie ukryte.
     ============================================================ */
  if (!reduce){
    var sel = 'section > .wrap > *, .card, .step, .q, .prow, details, .hr, .pkgbody';
    var zoomSel = '.shot, .pkgimg, .cardimg';
    var items = [];

    document.querySelectorAll(zoomSel).forEach(function(el){
      if (el.dataset.rv) return;
      el.dataset.rv = '1';
      el.classList.add('reveal-zoom');
      items.push(el);
    });
    document.querySelectorAll(sel).forEach(function(el){
      if (el.dataset.rv) return;
      el.dataset.rv = '1';
      el.classList.add('reveal');
      items.push(el);
    });

    /* Sprawdzanie przy przewijaniu zamiast IntersectionObserver:
       przy szybkim machnieciu palcem obserwator potrafi pominac
       element i tresc zostalaby niewidoczna. Tu nic nie zniknie. */
    var rvTick = false;
    function revealPass(){
      rvTick = false;
      var limit = innerHeight * 0.94;
      var left = [];
      for (var i = 0; i < items.length; i++){
        var el = items[i];
        var top = el.getBoundingClientRect().top;
        if (top < limit){
          var sibs = el.parentElement ? [].slice.call(el.parentElement.children) : [];
          var idx = Math.max(0, sibs.indexOf(el));
          el.style.transitionDelay = Math.min(idx * 0.07, 0.35) + 's';
          el.classList.add('in');
        } else {
          left.push(el);
        }
      }
      items = left;
    }
    function queueReveal(){
      if (!rvTick){ rvTick = true; requestAnimationFrame(revealPass); }
    }
    addEventListener('scroll', queueReveal, { passive: true });
    addEventListener('resize', queueReveal, { passive: true });
    addEventListener('load', queueReveal);
    revealPass();
  }

  /* ============================================================
     6. LIGHTBOX GALERII
     ============================================================ */
  var gal = document.getElementById('gal');
  var lb  = document.getElementById('lb');

  if (gal && lb){
    var lbi   = document.getElementById('lbi');
    var lbc   = document.getElementById('lbc');
    var shots = [].slice.call(gal.querySelectorAll('img'));
    var cur = 0, lastFocus = null;

    function show(i){
      cur = (i + shots.length) % shots.length;
      lbi.src = shots[cur].currentSrc || shots[cur].src;
      lbi.alt = shots[cur].alt;
      lbc.textContent = (cur + 1) + ' / ' + shots.length;
    }
    function openLb(i){
      lastFocus = document.activeElement;
      show(i);
      lb.classList.add('on');
      document.documentElement.style.overflow = 'hidden';
      document.getElementById('lbx').focus();
    }
    function closeLb(){
      lb.classList.remove('on');
      document.documentElement.style.overflow = '';
      if (lastFocus) lastFocus.focus();
    }

    gal.querySelectorAll('button').forEach(function(b, i){
      b.addEventListener('click', function(){ openLb(i); });
    });
    document.getElementById('lbx').addEventListener('click', closeLb);
    document.getElementById('lbp').addEventListener('click', function(e){ e.stopPropagation(); show(cur - 1); });
    document.getElementById('lbn').addEventListener('click', function(e){ e.stopPropagation(); show(cur + 1); });
    lb.addEventListener('click', function(e){ if (e.target === lb) closeLb(); });
    addEventListener('keydown', function(e){
      if (!lb.classList.contains('on')) return;
      if (e.key === 'Escape')     closeLb();
      if (e.key === 'ArrowLeft')  show(cur - 1);
      if (e.key === 'ArrowRight') show(cur + 1);
    });
    /* przesuniecie palcem */
    var sx = 0;
    lb.addEventListener('touchstart', function(e){ sx = e.touches[0].clientX; }, { passive: true });
    lb.addEventListener('touchend', function(e){
      var d = e.changedTouches[0].clientX - sx;
      if (Math.abs(d) > 45) show(d > 0 ? cur - 1 : cur + 1);
    }, { passive: true });
  }

  if (reduce) return;   /* dalej tylko efekty ozdobne */

  /* ============================================================
     7. LICZNIKI CEN
     ============================================================ */
  if ('IntersectionObserver' in window){
    var counted = new WeakSet();
    var nio = new IntersectionObserver(function(es){
      es.forEach(function(e){
        if (!e.isIntersecting) return;
        nio.unobserve(e.target);
        var el = e.target;
        if (counted.has(el)) return;
        counted.add(el);
        var raw = el.textContent;
        var m = raw.match(/(\d+(?:[\s ]\d{3})*)/);
        if (!m) return;
        var target = parseInt(m[1].replace(/\s| /g, ''), 10);
        if (!target || target < 100) return;
        var t0 = performance.now(), dur = 850;
        el.style.fontVariantNumeric = 'tabular-nums';
        (function step(t){
          var k = Math.min(1, (t - t0) / dur);
          var val = Math.round(target * (1 - Math.pow(1 - k, 3)));
          el.textContent = raw.replace(m[1], String(val).replace(/\B(?=(\d{3})+(?!\d))/g, ' '));
          if (k < 1) requestAnimationFrame(step);
          else { el.textContent = raw; el.style.fontVariantNumeric = ''; }
        })(performance.now());
      });
    }, { threshold: .6 });
    document.querySelectorAll('.bigprice').forEach(function(el){ nio.observe(el); });
  }

  /* ============================================================
     8. AURY SWIATLA (tanie, wszedzie)
     ============================================================ */
  ['aura-1', 'aura-2'].forEach(function(c){
    var d = document.createElement('div');
    d.className = 'aura ' + c;
    d.setAttribute('aria-hidden', 'true');
    document.body.insertBefore(d, document.body.firstChild);
  });

  if (!desktop) return;   /* na telefonie konczymy — plynnosc wazniejsza */

  /* ============================================================
     9. PASEK POSTEPU CZYTANIA (komputer)
     ============================================================ */
  var bar = document.createElement('div');
  bar.className = 'progress';
  document.body.appendChild(bar);
  function prog(){
    var h = document.documentElement.scrollHeight - innerHeight;
    bar.style.width = (h > 0 ? (scrollY / h) * 100 : 0) + '%';
  }
  addEventListener('scroll', prog, { passive: true });
  addEventListener('resize', prog, { passive: true });
  prog();

  /* ============================================================
     10. SWIATLO ZA KURSOREM (komputer)
     ============================================================ */
  var spot = document.createElement('div');
  spot.className = 'spot';
  spot.setAttribute('aria-hidden', 'true');
  document.body.appendChild(spot);
  var mx = 0, my = 0, cx = 0, cy = 0, sraf = null;
  addEventListener('mousemove', function(e){
    mx = e.clientX; my = e.clientY;
    spot.style.opacity = '1';
    if (!sraf) sraf = requestAnimationFrame(follow);
  }, { passive: true });
  addEventListener('mouseleave', function(){ spot.style.opacity = '0'; });
  function follow(){
    cx += (mx - cx) * .12; cy += (my - cy) * .12;
    spot.style.transform = 'translate(' + cx + 'px,' + cy + 'px) translate(-50%,-50%)';
    sraf = (Math.abs(mx - cx) > .4 || Math.abs(my - cy) > .4)
      ? requestAnimationFrame(follow) : null;
  }

  /* ============================================================
     11. PARALAKSA ZDJEC (komputer)
     ============================================================ */
  var paras = [].slice.call(document.querySelectorAll('.hero-media img, .pkgimg img, .shot img'));
  if (paras.length){
    var pTick = false;
    function parallax(){
      for (var i = 0; i < paras.length; i++){
        var el = paras[i], r = el.getBoundingClientRect();
        if (r.bottom < -120 || r.top > innerHeight + 120) continue;
        var mid = r.top + r.height / 2 - innerHeight / 2;
        var shift = Math.max(-13, Math.min(13, -mid * .02));
        el.style.transform = 'translate3d(0,' + shift.toFixed(2) + 'px,0) scale(1.04)';
      }
      pTick = false;
    }
    addEventListener('scroll', function(){
      if (!pTick){ pTick = true; requestAnimationFrame(parallax); }
    }, { passive: true });
    parallax();
  }

  /* ============================================================
     12. PYLEK NA PLOTNIE (komputer)
     ============================================================ */
  var cv = document.createElement('canvas');
  cv.id = 'shimmer';
  cv.setAttribute('aria-hidden', 'true');
  document.body.insertBefore(cv, document.body.firstChild);
  var ctx = cv.getContext('2d'), W = 0, H = 0, dpr = 1, parts = [], praf = null;

  function sizeCanvas(){
    dpr = Math.min(devicePixelRatio || 1, 2);
    W = cv.width  = innerWidth  * dpr;
    H = cv.height = innerHeight * dpr;
    cv.style.width  = innerWidth  + 'px';
    cv.style.height = innerHeight + 'px';
  }
  function seed(){
    var n = innerWidth < 1400 ? 46 : 64;
    parts = [];
    for (var i = 0; i < n; i++){
      parts.push({
        x: Math.random() * W, y: Math.random() * H,
        r: (Math.random() * 1.4 + .4) * dpr,
        vy: -(Math.random() * .15 + .03),
        vx: (Math.random() - .5) * .1,
        a: Math.random() * .38 + .1,
        tw: Math.random() * 6.283,
        ts: Math.random() * .018 + .005
      });
    }
  }
  function draw(){
    ctx.clearRect(0, 0, W, H);
    for (var i = 0; i < parts.length; i++){
      var p = parts[i];
      p.y += p.vy; p.x += p.vx; p.tw += p.ts;
      if (p.y < -10){ p.y = H + 10; p.x = Math.random() * W; }
      if (p.x < -10) p.x = W + 10;
      if (p.x > W + 10) p.x = -10;
      var tw = (Math.sin(p.tw) + 1) / 2;
      var al = p.a * (.3 + tw * .7) * (.45 + .55 * (1 - p.y / H));
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.r, 0, 6.2832);
      ctx.fillStyle = 'rgba(170,128,96,' + al.toFixed(3) + ')';
      ctx.fill();
    }
    praf = requestAnimationFrame(draw);
  }
  sizeCanvas(); seed(); draw();
  addEventListener('resize', function(){ sizeCanvas(); seed(); }, { passive: true });
  document.addEventListener('visibilitychange', function(){
    cancelAnimationFrame(praf);
    if (!document.hidden) draw();
  });
})();
