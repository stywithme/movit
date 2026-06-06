/* WayToFix prototypes — shared interactions (theme, selects, state switcher) */
(function(){
  var root = document.documentElement;

  /* ---- theme toggle (persisted) ---- */
  try{ var saved = localStorage.getItem('wtf-theme'); if(saved) root.setAttribute('data-theme', saved); }catch(e){}
  var t = document.getElementById('themeToggle');
  if(t){
    var lbl = t.querySelector('.tlabel');
    var sync = function(){ if(lbl) lbl.textContent = root.getAttribute('data-theme')==='dark' ? 'Dark' : 'Light'; };
    sync();
    t.addEventListener('click', function(){
      var next = root.getAttribute('data-theme')==='dark' ? 'light' : 'dark';
      root.setAttribute('data-theme', next);
      try{ localStorage.setItem('wtf-theme', next); }catch(e){}
      sync();
    });
  }

  /* ---- single-select groups (chips, segmented, nav, pickers) ---- */
  function group(sel, childSel, cls){
    document.querySelectorAll(sel).forEach(function(g){
      g.querySelectorAll(childSel).forEach(function(c){
        c.addEventListener('click', function(){
          g.querySelectorAll(childSel).forEach(function(x){ x.classList.remove(cls); });
          c.classList.add(cls);
        });
      });
    });
  }
  group('[data-single]', '.chip', 'is-active');
  group('[data-single]', '.filter-chip', 'is-active');
  group('[data-single]', '.muscle-chip', 'is-active');
  group('[data-seg]', '.seg', 'is-active');
  group('[data-nav]', '.nav-item', 'is-active');
  group('[data-pick]', '.type-pick', 'is-active');
  group('[data-pick]', '.body-card', 'is-active');

  /* ---- switches ---- */
  document.querySelectorAll('[data-switch]').forEach(function(s){
    s.addEventListener('click', function(){ s.classList.toggle('is-on'); });
  });

  /* ---- expandable session cards ---- */
  document.querySelectorAll('[data-sess] .sc-head').forEach(function(h){
    h.addEventListener('click', function(){ h.closest('.sess-card').classList.toggle('open'); });
  });

  /* ---- steppers (+ / -) ---- */
  document.querySelectorAll('.stepper').forEach(function(st){
    var b = st.querySelector('b'); var btns = st.querySelectorAll('button');
    btns.forEach(function(btn,i){
      btn.addEventListener('click', function(e){
        e.stopPropagation();
        var v = parseInt(b.textContent,10)||0; v += (i===0 ? -1 : 1); if(v<0) v=0; b.textContent = v;
      });
    });
  });

  /* ---- state switcher (multi-state prototype pages) ----
     A control with [data-state-switch] holds buttons [data-go="name"].
     Elements [data-state="a b"] / [data-when="a b"] are shown when the
     current state is in their space-separated list. */
  var sw = document.querySelector('[data-state-switch]');
  if(sw){
    var apply = function(state){
      root.setAttribute('data-screen-state', state);
      document.querySelectorAll('[data-state]').forEach(function(el){
        el.classList.toggle('is-shown', el.getAttribute('data-state').split(/\s+/).indexOf(state) !== -1);
      });
      document.querySelectorAll('[data-when]').forEach(function(el){
        el.classList.toggle('is-shown', el.getAttribute('data-when').split(/\s+/).indexOf(state) !== -1);
      });
      sw.querySelectorAll('[data-go]').forEach(function(b){
        b.classList.toggle('is-active', b.getAttribute('data-go') === state);
      });
    };
    // outside switcher buttons
    sw.querySelectorAll('[data-go]').forEach(function(b){
      b.addEventListener('click', function(){ apply(b.getAttribute('data-go')); });
    });
    // in-screen triggers (Edit / Done pills, swap, edit, scrim, etc.)
    document.querySelectorAll('[data-go-state]').forEach(function(b){
      b.addEventListener('click', function(){ apply(b.getAttribute('data-go-state')); });
    });
    var start = sw.getAttribute('data-default') || sw.querySelector('[data-go]').getAttribute('data-go');
    apply(start);
  }
})();
