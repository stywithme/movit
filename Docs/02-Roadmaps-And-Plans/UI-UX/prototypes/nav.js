/* WayToFix prototypes — unified navigation (main bottom · internal top) */
(function () {
  var MAIN = [
    { title: 'Catalog', links: [{ href: 'index.html', label: 'Index' }] },
    {
      title: 'Navbar',
      links: [
        { href: '08-home.html', label: 'Home' },
        { href: '01-train.html', label: 'Train' },
        { href: '04-explore.html', label: 'Explore' },
        { href: '09-reports.html', label: 'Reports' }
      ]
    },
    {
      title: 'Account',
      links: [
        { href: '10-auth.html', label: 'Auth' },
        { href: '11-profile.html', label: 'Profile' }
      ]
    },
    {
      title: 'System',
      links: [{ href: '00-components.html', label: 'Components' }]
    }
  ];

  var FLOWS = {
    training: {
      title: 'Training flow',
      links: [
        { href: '03-prepare.html', label: 'Prepare & rest' },
        { href: '02-session.html', label: 'Session day' },
        { href: '16-workout-flow.html', label: 'Customize & run' },
        { href: '17-report-detail.html', label: 'Report' }
      ]
    },
    program: {
      title: 'Program flow',
      links: [
        { href: '15-program-flow.html', label: 'Browse & week' },
        { href: '07-program.html', label: 'Program detail' },
        { href: '02-session.html', label: 'Session day' }
      ]
    },
    library: {
      title: 'Library',
      links: [
        { href: '04-explore.html', label: 'Explore hub' },
        { href: '06-workouts.html', label: 'Workouts' },
        { href: '05-exercises.html', label: 'Exercises' }
      ]
    },
    workout: {
      title: 'Workout builder',
      links: [
        { href: '06-workouts.html', label: 'Workout detail' },
        { href: '16-workout-flow.html', label: 'Customize & run' }
      ]
    },
    assessment: {
      title: 'Assessment & level',
      links: [
        { href: '13-assessment.html', label: 'Body scan' },
        { href: '14-level-plan.html', label: 'Level & plan' }
      ]
    },
    onboarding: {
      title: 'Onboarding',
      links: [
        { href: '10-auth.html', label: 'Sign in / up' },
        { href: '12-profile-onboarding.html', label: 'Profile setup' }
      ]
    }
  };

  function currentFile() {
    var p = location.pathname || '';
    var i = p.lastIndexOf('/');
    return i >= 0 ? p.slice(i + 1) : p || 'index.html';
  }

  function buildMainNav(activeMain) {
    var nav = document.createElement('nav');
    nav.className = 'page-nav main-nav';
    nav.setAttribute('aria-label', 'Main screens');

    MAIN.forEach(function (section, idx) {
      if (idx > 0) {
        var sep = document.createElement('div');
        sep.className = 'pn-sep';
        nav.appendChild(sep);
      }
      var title = document.createElement('div');
      title.className = 'pn-title';
      title.textContent = section.title;
      nav.appendChild(title);
      section.links.forEach(function (link) {
        var a = document.createElement('a');
        a.href = link.href;
        a.textContent = link.label;
        if (link.href === activeMain) a.className = 'is-active';
        nav.appendChild(a);
      });
    });
    return nav;
  }

  function buildFlowNav(flowKey, activeFile) {
    var flow = FLOWS[flowKey];
    if (!flow) return null;

    var nav = document.createElement('nav');
    nav.className = 'internal-nav';
    nav.setAttribute('aria-label', flow.title);

    var title = document.createElement('div');
    title.className = 'in-title';
    title.textContent = flow.title;
    nav.appendChild(title);

    flow.links.forEach(function (link) {
      var a = document.createElement('a');
      a.href = link.href;
      a.textContent = link.label;
      if (link.href === activeFile) a.className = 'is-active';
      nav.appendChild(a);
    });

    document.body.classList.add('has-flow-nav');
    document.body.style.setProperty('--flow-nav-h', 28 + flow.links.length * 38 + 'px');
    return nav;
  }

  function ensureProtoNavTop(body, beforeNode) {
    var stack = document.querySelector('.proto-nav-top');
    if (stack) return stack;

    stack = document.createElement('div');
    stack.className = 'proto-nav-top';

    var stateSwitch = document.querySelector('.state-switch:not(.proto-nav-top .state-switch)');
    var anchor = stateSwitch || beforeNode || body.firstChild;
    body.insertBefore(stack, anchor);
    if (stateSwitch) stack.appendChild(stateSwitch);
    return stack;
  }

  function mountLocalPanels(body) {
    body.querySelectorAll('.internal-nav[data-local]').forEach(function (nav) {
      if (nav.closest('.proto-nav-top')) return;
      ensureProtoNavTop(body, nav).appendChild(nav);
    });
  }

  function mount() {
    var body = document.body;
    if (!body || body.dataset.nav === 'off') return;

    var file = currentFile();
    var activeMain = body.getAttribute('data-main') || file;
    var flowKey = body.getAttribute('data-flow');

    var existingMain = document.querySelector('nav.page-nav.main-nav, nav.page-nav:not(.internal-nav)');
    if (existingMain) existingMain.remove();

    var mainNav = buildMainNav(activeMain);
    body.appendChild(mainNav);

    var existingFlow = document.querySelector('nav.internal-nav[data-auto]');
    if (existingFlow) existingFlow.remove();

    if (flowKey) {
      var flowNav = buildFlowNav(flowKey, file);
      if (flowNav) {
        flowNav.setAttribute('data-auto', '1');
        var stack = ensureProtoNavTop(body);
        stack.insertBefore(flowNav, stack.firstChild);
      }
    }

    mountLocalPanels(body);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mount);
  } else {
    mount();
  }
})();
