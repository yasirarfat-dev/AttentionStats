import tokens from './tokens.js';

// Only synthetic fixtures. No Android bridge, persistence, permission request or collector.
const screen = document.querySelector('#screen');
const navigation = document.querySelector('#navigation');
const sheet = document.querySelector('#sheet');
const root = document.documentElement;
const mediaTheme = matchMedia('(prefers-color-scheme: dark)');
const freshState = () => ({
  route: 'today', scenario: 'sample', theme: 'light', motion: false, large: false,
  duration: 25, deadline: null, ruleOn: true, ruleBudget: 45, ruleAction: 'remind',
  extraRule: null, draft: null, details: 'social', table: false, source: false,
  protectionPaused: false,
  overlay: {visit:18, today:64, hidden:false, unknown:false, corner:'center'},
});
let state = freshState();
let returnFocus;
const paths = {
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/>',
  history: '<path d="M3 11a9 9 0 1 1 1 5M3 5v6h6m3-5v6l4 2"/>',
  pattern: '<path d="M3 17l5-6 5 3 8-10M3 21h18"/><circle cx="8" cy="11" r="1"/><circle cx="13" cy="14" r="1"/>',
  shield: '<path d="M12 3l8 3v6c0 4-4 7-8 9-4-2-8-5-8-9V6z"/><path d="M8 12l3 3 5-6"/>',
  settings: '<path d="M4 6h16M4 12h16M4 18h16"/><circle cx="9" cy="6" r="2" fill="var(--surface)"/><circle cx="15" cy="12" r="2" fill="var(--surface)"/><circle cx="10" cy="18" r="2" fill="var(--surface)"/>',
  arrow: '<path d="M5 12h14m-6-6 6 6-6 6"/>',
  back: '<path d="M19 12H5m6-6-6 6 6 6"/>',
  close: '<path d="M6 6l12 12M6 18 18 6"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v1"/>',
  leaf: '<path d="M5 19C1 8 10 3 20 3c0 10-5 19-15 16Zm0 0L16 7"/>',
  lock: '<rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2"/>',
  pause: '<path d="M8 5v14M16 5v14"/>',
  check: '<path d="M5 12l4 4L19 6"/>',
};
const icon = name => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.info}</svg>`;
const sprig = '<svg class="sprig" viewBox="0 0 64 64" aria-hidden="true"><path d="M19 54c7-12 12-25 14-44m-7 29C9 39 10 23 10 23s16 0 16 16Zm5-17C39 9 51 15 51 15s-4 14-20 7Zm-9 25c16 2 23-11 23-11s-15-4-23 11Z"/></svg>';
const button = (text, action, kind = 'primary', extra = '') => `<button class="${kind}" data-action="${action}" ${extra}>${text}</button>`;
const activeData = () => state.scenario === 'sample' || state.scenario === 'gap';
const announce = text => { document.querySelector('#announcements').textContent = text; };

function applyPreferences() {
  const theme = state.theme === 'system' ? (mediaTheme.matches ? 'dark' : 'light') : state.theme;
  root.dataset.theme = theme;
  root.classList.toggle('theme-dark', theme === 'dark');
  root.classList.toggle('large-text', state.large);
  root.dataset.motion = state.motion ? 'reduce' : 'system';
  for (const [name, value] of Object.entries(tokens[theme])) root.style.setProperty(`--${name}`, value);
  for (const [name, value] of Object.entries(tokens.motionMs)) root.style.setProperty(`--${name}`, `${value}ms`);
  document.querySelector('#theme').value = state.theme;
  document.querySelector('#scenario').value = state.scenario;
  document.querySelector('#reduce-motion').checked = state.motion;
  document.querySelector('#large-text').checked = state.large;
}
mediaTheme.addEventListener('change', applyPreferences);

function header() {
  return `<header class="app-header"><span class="wordmark"><span class="dot" aria-hidden="true">●</span>Attention Stats</span><button class="icon-button" data-action="privacy" aria-label="Privacy and trust">${icon('lock')}</button></header>`;
}
function title(eyebrow, heading, intro = '') {
  return `<p class="date">${eyebrow}</p><h1 tabindex="-1">${heading}</h1>${intro ? `<p class="intro">${intro}</p>` : ''}`;
}
function render({ focus = true } = {}) {
  const views = { today, history, patterns, protect, settings, app: appDetail, overlay: overlayPreview };
  screen.innerHTML = `<div class="page">${header()}${views[state.route]()}</div>`;
  const tabs = [['today','sun','Today'],['history','history','History'],['patterns','pattern','Patterns'],['protect','shield','Protect'],['settings','settings','Settings']];
  navigation.innerHTML = tabs.map(([route, image, label]) => `<button class="nav-item" data-route="${route}" ${state.route === route || (state.route === 'app' && route === 'history') || (state.route === 'overlay' && route === 'protect') ? 'aria-current="page"' : ''}>${icon(image)}<span>${label}</span></button>`).join('');
  applyPreferences();
  if (focus) screen.querySelector('h1').focus({ preventScroll:true });
  tick();
}
function navigate(route) {
  state.route = route;
  render();
  screen.scrollTop = 0;
  // Keep keyboard focus and the destination together, including the mobile layout.
  screen.scrollIntoView({ block:'start', behavior:'instant' });
}
function chart() {
  const bars = [0,8,15,null,14,27,22,16];
  const labels = ['06','08','10','12','14','16','18','20'];
  const rows = bars.map((value, i) => `<tr><th scope="row">${labels[i]}:00</th><td>${value === null ? 'Unknown · access gap' : `${value} min observed`}</td></tr>`).join('');
  return `<svg class="chart" viewBox="0 0 320 86" role="img" aria-label="Observed app time by two-hour bin. 102 minutes total. The 12:00 bin contains an access gap; it is unknown, not zero.">${bars.map((value, i) => `<rect class="${value === null ? 'gap' : 'bar'}" x="${i*40+4}" y="${value === null ? 14 : 59-Math.max(2,value*1.65)}" width="22" height="${value === null ? 45 : Math.max(2,value*1.65)}" rx="4"/><text x="${i*40+15}" y="80" text-anchor="middle">${labels[i]}</text>`).join('')}</svg><p class="chart-caption"><span class="gap-swatch" aria-hidden="true"></span>Gap at 12:00 · each bar spans 2 hours</p>${button(state.table ? 'Hide chart table' : 'Read chart as a table','table','text-button','aria-expanded="'+state.table+'"')}${state.table ? `<table><caption>Sample day · UTC · observed minutes; incomplete bin withheld</caption><thead><tr><th scope="col">Bin start</th><th scope="col">App time</th></tr></thead><tbody>${rows}</tbody></table>` : ''}`;
}
function appList() {
  return `<ul class="app-list">${[['social','S','Social sample','Social · 8 opens','42 min','lilac'],['reader','R','Reader sample','Reading · 3 opens','35 min',''],['notes','N','Notes sample','Tools · 2 opens','25 min','']].map(([id, initial, name, category, duration, color]) => `<li><button class="app-row" data-app="${id}"><span class="app-icon ${color}" aria-hidden="true">${initial}</span><span class="app-copy"><strong>${name}</strong><small>${category}</small></span><span class="app-time">${duration}</span>${icon('arrow')}</button></li>`).join('')}</ul>`;
}
function today() {
  if (state.scenario === 'fresh') return welcome();
  if (state.scenario === 'empty') return `${title('A FRESH START','Your day, at your pace.','No observations yet. That is different from a day with no activity.')}<div class="empty-art">${sprig}</div><h2>A little time to get a clear view.</h2><p class="intro">In this sample, app usage is enabled. New observations will appear here when available. Nothing before consent is imported.</p>${button('Review sources','sources','secondary')}${button('Explore settings','go-settings','text-button')}`;
  return `${title('TUESDAY, 27 OCT · SAMPLE DAY','A little clarity.<br>A little more room.','Notice your patterns. Choose what comes next.')}
    ${state.scenario === 'gap' ? '<div class="callout"><strong>App usage access interrupted.</strong><p>History after 18:00 is unknown. Any rule relying on this source is unavailable.</p>'+button('Review access','coverage','text-button')+'</div>' : ''}
    <section class="card hero" aria-label="Observed app time"><div class="hero-top"><span class="micro">Observed app time</span>${sprig}</div><div class="metric">${state.scenario === 'gap' ? '1<span>h</span> 4<span>m</span>' : '1<span>h</span> 42<span>m</span>'}</div><p class="hero-description">In covered periods · ${state.scenario === 'gap' ? '06:00–18:00' : '06:00–20:30'} UTC</p><span class="badge">OBSERVED · SAMPLE</span>${state.scenario !== 'gap' ? chart() : '<p class="micro">Later observations are withheld in this access-loss example.</p>'}<button class="coverage-link" data-action="coverage">${icon('info')}Coverage includes a gap · see what is known${icon('arrow')}</button></section>
    <section class="section"><div class="section-heading"><h2>Make a little space</h2></div><div class="card"><div class="intent"><div class="intent-icon">${icon('leaf')}</div><div><h3>A moment for what matters</h3><p>A short focus session, on your terms.</p></div></div>${button(state.deadline ? 'Return to focus preview' : 'Choose a focus session','go-protect')}</div></section>
    <section class="section"><div class="section-heading"><h2>One pattern to explore</h2></div><div class="card pattern-card"><p class="eyebrow">DESCRIPTIVE PATTERN · SAMPLE</p><h3>${state.scenario === 'gap' ? 'Too little coverage to infer.' : 'Sometimes, the next open<br>comes quickly.'}</h3><p>${state.scenario === 'gap' ? 'The 18:10–18:20 window is unknown. The reopening insight is withheld.' : 'Social sample reopened 3 times between 18:10 and 18:20. There could be many reasons.'}</p>${button('What this is based on '+icon('arrow'),'insight','text-button')}</div></section>
    ${state.scenario !== 'gap' ? `<section class="section"><div class="section-heading"><h2>Where your time went</h2>${button('History','go-history','text-button')}</div>${appList()}</section>` : ''}<p class="quiet-end">That’s your snapshot. Take what helps.</p>`;
}
function welcome() {
  return `${title('HELLO, AND WELCOME','More room for<br>what matters.','Understand your digital habits. Get support with the boundaries you choose.')}<div class="empty-art">${sprig}</div><ul class="welcome-list"><li>${icon('pattern')}<div><strong>Understand, without judgment.</strong><p>Clear history. Honest gaps. No score for how well you lived.</p></div></li><li>${icon('shield')}<div><strong>Choose your kind of support.</strong><p>Observe only, set a reminder, or plan a pause. You stay in charge.</p></div></li><li>${icon('lock')}<div><strong>Your history belongs to you.</strong><p>No data sent to us. Open source. Optional exports are your choice.</p></div></li></ul><div class="stack">${button('Choose what to observe','sources','secondary')}${button('Continue without tracking','skip','secondary')}</div><p class="quiet-end">Nothing is being tracked. This is a design preview.</p>`;
}
function history() {
  if (!activeData()) return `${title('YOUR HISTORY','A clear view, in time.','Your history is yours to choose.')}<div class="card"><h2>${state.scenario === 'fresh' ? 'Nothing is being tracked.' : 'No observations yet.'}</h2><p class="intro">${state.scenario === 'fresh' ? 'You can explore the app without enabling a source.' : 'Enabled does not mean events have arrived. This is not a zero-use day.'}</p>${button('Review sources','sources','secondary')}</div>`;
  return `${title('YOUR HISTORY','The shape of a day.','Tuesday, 27 October · synthetic examples · UTC')}<div class="card"><div class="row"><h2>Observed app time</h2><span class="badge">${state.scenario === 'gap' ? '64' : '102'} MIN</span></div>${state.scenario === 'gap' ? '<p class="micro">App access unavailable after 18:00; the later timeline is unknown.</p>' : chart()}</div><section class="section"><h2>Apps in covered periods</h2>${state.scenario === 'gap' ? '<p class="intro">Per-app totals withheld in this access-loss scenario.</p>' : appList()}</section><section class="section"><h2>Selected moments</h2><p class="micro">A few observations, not the full timeline.</p><ol class="timeline">${state.scenario === 'gap' ? '<li class="gap-entry"><time>18:00</time><div><strong>Access interrupted</strong><p>Unknown until access is restored. No automatic repair is simulated.</p></div></li>' : '<li><time>18:20</time><div><strong>Social sample opened</strong><p>Third reopening in this 10-minute window.</p></div></li><li><time>18:16</time><div><strong>Social sample reopened</strong><p>Observed package entry, not inferred intent.</p></div></li><li><time>18:10</time><div><strong>Social sample reopened</strong><p>Observed after leaving the app.</p></div></li>'}<li class="gap-entry"><time>12:00</time><div><strong>30 minutes not covered</strong><p>Source access unavailable. The incomplete 12:00 bin is withheld.</p></div></li><li><time>06:00</time><div><strong>Sample coverage begins</strong><p>No events before consent were imported.</p></div></li></ol></section>`;
}
function patterns() {
  if (!activeData()) return `${title('UNDERSTAND YOUR PATTERNS','Curiosity, without<br>the judgment.','Patterns need enough covered history. There is nothing to infer yet.')}<div class="card"><h2>No insight is better than a guess.</h2><p class="intro">You can choose a source when you are ready. Predictions and content topics stay off.</p>${button('Review sources','sources','secondary')}</div>`;
  return `${title('UNDERSTAND YOUR PATTERNS','Small observations.<br>Your own meaning.','Activity can show a pattern. Only you know how it felt.')}<div class="card pattern-card"><p class="eyebrow">DESCRIPTIVE · SAMPLE</p><h3>A quick return to Social sample</h3><p>${state.scenario === 'gap' ? 'The 18:10–18:20 window has no coverage in this scenario. This insight is withheld.' : '3 reopenings in 10 minutes. A possible moment to pause, if that would help you.'}</p>${state.scenario === 'gap' ? button('See coverage','coverage','text-button') : button('See the observations','insight','text-button')}</div><section class="section stack"><div class="card"><h2>Trends need a fair comparison.</h2><p class="intro">A single sample day cannot show improvement or decline. Comparable covered days are needed first.</p><span class="badge">INSUFFICIENT DATA</span></div><div class="card"><h2>What keeps pulling you back?</h2><p class="intro">Optional content-topic insights could help explore that question. They would need separately consented metadata and reliable local classification.</p><span class="badge">PLANNED · OFF BY DEFAULT</span><p class="micro">They cannot establish addiction or reveal your inner attention state.</p></div></section><p class="quiet-end">No predictions. No diagnosis. Room for your interpretation.</p>`;
}
function protect() {
  if (state.deadline) return `${title('A LITTLE SPACE','One thing at a time.','Focus preview · no apps are being blocked.')}<div class="focus-orbit"><strong id="focus-time" aria-label="Time remaining"></strong><small>remaining in this preview</small></div><p class="intro">Your chosen targets: Social sample. Calls, system settings, and emergency access remain available.</p><div class="stack">${button('End focus preview','end-focus','secondary')}${button('Emergency access · end now','emergency','emergency')}</div><p class="quiet-end">You can end this at any time. There is no lost streak.</p>`;
  return `${title('YOUR BOUNDARIES','Support, on<br>your terms.','Gentle reminders or firmer boundaries. You choose what fits.')}<div class="card hero"><div class="intent"><div class="intent-icon">${icon('leaf')}</div><div><h2>Make time for one thing</h2><p class="micro">Social sample · demo target</p></div></div><div class="segmented" role="group" aria-label="Focus duration">${[15,25,45,60].map(n => `<button data-duration="${n}" aria-pressed="${n === state.duration}">${n} min</button>`).join('')}</div>${button('Preview '+state.duration+' minutes of focus','start-focus')}<p class="micro">A timer demonstration. No permissions or blocking.</p></div><section class="section"><div class="section-heading"><h2>Your sample rules</h2>${button('Add rule','add-rule','text-button')}</div>${state.protectionPaused ? '<div class="callout">All demo rules are paused. Observation settings are separate.</div>' : ''}<div class="card"><div class="rule">${icon('shield')}<div class="app-copy"><h3>A gentler social limit</h3><p>${state.ruleBudget} min a day · ${state.ruleAction === 'remind' ? 'reminder' : 'pause prompt'} · Social sample</p><span class="rule-state">${state.scenario === 'gap' ? 'Unavailable · access interrupted' : state.protectionPaused ? 'Paused · demo only' : 'Demo configuration · not enforced'}</span></div><button class="rule-toggle" data-action="toggle-rule" aria-label="Enable sample social rule" aria-pressed="${state.ruleOn}">${state.ruleOn ? 'On' : 'Off'}</button></div>${button('Review rule and exceptions','edit-rule','text-button')}</div>${state.extraRule ? `<div class="card"><h3>Another social boundary</h3><p class="micro">${state.extraRule.budget} min · ${state.extraRule.action === 'remind' ? 'reminder' : 'pause prompt'} · demo only</p><p class="micro">Overlaps with the existing social rule. The stricter active action wins; usage is counted once.</p></div>` : ''}</section><div class="section stack">${button(state.protectionPaused ? 'Resume demo rules' : 'Pause demo rules','pause-protection','secondary')}${button('Preview a gentle interruption','intervention','secondary')}${button('Preview live counter','live-overlay','secondary')}${button('Emergency access · release all demo rules','emergency','emergency')}</div><p class="quiet-end">This preview does not enforce any rule.</p>`;
}
function settings() {
  return `${title('MAKE IT YOURS','Quietly, your way.','Privacy, comfort, and control. No account needed.')}<section><h2>Comfort</h2><div class="setting"><div><strong>Appearance</strong><p>Choose what feels comfortable.</p></div><select id="app-theme" aria-label="App appearance">${[['light','Light'],['dark','Dark'],['system','System']].map(([id,label]) => `<option value="${id}" ${state.theme === id ? 'selected' : ''}>${label}</option>`).join('')}</select></div><label class="setting"><span><strong>Reduce motion</strong><p>System preference is always respected.</p></span><input type="checkbox" id="app-motion" ${state.motion ? 'checked' : ''}></label><label class="setting"><span><strong>Large text preview</strong><p>Review layouts at 200% text size.</p></span><input type="checkbox" id="app-large" ${state.large ? 'checked' : ''}></label></section><section class="section"><h2>Your data, your decisions</h2><div class="setting"><div><strong>Sources and permissions</strong><p>Choose each source separately.</p></div>${button('Review','sources','text-button')}</div><div class="setting"><div><strong>Privacy and trust</strong><p>What stays local and what can leave.</p></div>${button('Read','privacy','text-button')}</div><div class="setting"><div><strong>Storage and export</strong><p>Planned, not functional in this preview.</p></div>${button('Details','storage','text-button')}</div></section><section class="privacy-note">${icon('lock')}<h2>A clear promise.<br>Yours to verify.</h2><p>Planned app: no data sent to us. Your history stays on your device. Open source.</p><p>User-selected exports or an approved future backup would send encrypted data and metadata to your chosen provider.</p><a href="https://github.com/yasirarfat-dev/AttentionStats" target="_blank" rel="noopener noreferrer">Inspect the source ${icon('arrow')}</a></section>`;
}
function appDetail() {
  const data = { social:['Social sample',42,8], reader:['Reader sample',35,3], notes:['Notes sample',25,2] }[state.details];
  return `${button(icon('back')+' Back to history','go-history','text-button')}${title('APP DETAIL · SYNTHETIC',data[0],'Observed activity in covered periods · UTC')}<section class="card hero"><span class="micro">Observed app time</span><div class="metric">${data[1]}<span> min</span></div><p class="hero-description">${data[2]} observed package entries · not measures of attention</p>${button('Coverage and source','coverage','text-button')}</section>${state.details === 'social' ? '<section class="section stack"><div class="card"><div class="row"><h2>Short-video surface</h2><span class="badge">ESTIMATE</span></div><div class="metric">18</div><p class="intro">Estimated exposures from confirmed item transitions in a synthetic fixture. Repeats may count; this is not unique videos or videos completed.</p><p class="micro">Real Facebook Reels, YouTube Shorts, Instagram Reels, and TikTok adapters are unverified. No live support is claimed here.</p>'+button('How counting would work','counting','text-button')+'</div><div class="card"><h2>Content topics are off.</h2><p class="intro">No titles, URLs, or content are read here. Future optional topic analysis needs its own consent.</p></div></section>' : '<p class="quiet-end">No content history or inferred topics.</p>'}`;
}

function overlayPreview() {
  const o = state.overlay;
  return `${title('AWARENESS, IN THE MOMENT','A quiet point<br>of reference.','Optional live counter concept · independent synthetic fixture. No real apps or feeds are observed.')}
    <div class="sample-feed" aria-label="Synthetic short-video surface">
      <div class="sample-scene" aria-hidden="true"><div class="scene-sun"></div><div class="scene-hill"></div></div>
      <span class="feed-label">ORIGINAL ILLUSTRATION · NOT A VIDEO</span>
      ${!o.hidden && !o.unknown ? `<button class="feed-counter counter-${o.corner}" data-action="overlay-detail" aria-label="Awareness counter: this visit approximately ${o.visit} exposures; today approximately ${o.today} exposures. Open details."><span class="counter-heading">Estimated exposures</span><span class="counter-values"><span>This visit <strong>~${o.visit}</strong></span><span>Today <strong>~${o.today}</strong></span></span></button>` : ''}
      <div class="feed-bottom"><h2>A little open space.</h2><p>Sample short-video surface · demo only</p></div>
    </div>
    <p class="micro overlay-state">${o.unknown ? 'Surface unknown · counter removed. Counts are not inferred.' : o.hidden ? 'Counter hidden. Observation choice and protection are separate.' : 'Same app and surface · current visit is already included in today’s total. Sample date/zone: 27 Oct · UTC.'}</p>
    <section class="section stack" aria-label="Counter simulation controls"><h2>Try the concept</h2>
    ${button('Simulate next confirmed item','overlay-next','secondary')}
    ${button('Replay same-item callback','overlay-duplicate','secondary')}
    ${button('Begin a new confirmed visit','overlay-visit','secondary')}
    ${button(o.unknown ? 'Restore surface signal' : 'Lose surface signal','overlay-signal','secondary')}
    ${button(o.hidden ? 'Show counter' : 'Hide counter','overlay-hide','secondary')}
    ${button('Counter details and position','overlay-detail','text-button')}
    </section><p class="quiet-end">Numbers should inform a choice, never become a score.</p>`;
}
function overlayDetail() {
  const o = state.overlay;
  showSheet('A glance, then your choice.', `<p>This independent fixture represents one app and one short-video surface. The current visit is included once in today’s total.</p><dl class="evidence-grid"><dt>This visit</dt><dd>${o.unknown ? 'Unavailable · unknown surface' : '~'+o.visit+' estimated exposures'}</dd><dt>Today · UTC</dt><dd>${o.unknown ? 'Partial · last sample ~'+o.today+' before signal loss' : '~'+o.today+' estimated exposures'}</dd><dt>Meaning</dt><dd>Confirmed item transitions, including repeat exposures. Not unique videos, completion, or attention.</dd><dt>Source</dt><dd>Synthetic button simulation · no accessibility or overlay permission requested</dd></dl><div class="stack">${['center','start','end'].map((position,i)=>button(['Place at top center','Place at leading edge','Place at trailing edge'][i],'overlay-position','secondary','data-position="'+position+'"')).join('')}${button(o.hidden ? 'Show counter' : 'Hide counter for this demo','overlay-hide-dialog','secondary')}${button('Explore a chosen pause','overlay-pause','secondary')}${button('Close details','close','secondary')}</div><p class="micro">In the real app: display, collection and protection would be separate controls. No rule or permission is changed by hiding this counter.</p>`);
}

function showSheet(heading, content) {
  if (!sheet.open) returnFocus = document.activeElement;
  sheet.innerHTML = `<div class="sheet-heading"><h2 id="sheet-title" tabindex="-1">${heading}</h2><button class="icon-button" data-action="close" aria-label="Close dialog">${icon('close')}</button></div>${content}`;
  if (!sheet.open) sheet.showModal();
  sheet.querySelector('h2').focus();
}
function closeSheet() {
  sheet.close();
  if (returnFocus?.isConnected) returnFocus.focus();
}
sheet.addEventListener('cancel', event => { event.preventDefault(); closeSheet(); });
sheet.addEventListener('keydown', event => {
  if (event.key !== 'Tab') return;
  const controls = [...sheet.querySelectorAll('button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled)')];
  const first = controls[0], last = controls.at(-1);
  const current = document.activeElement;
  if (!controls.includes(current) || (event.shiftKey && current === first) || (!event.shiftKey && current === last)) {
    event.preventDefault();
    (event.shiftKey ? last : first)?.focus();
  }
});

function coverage() {
  showSheet('A gap is not zero.', `<p>These are synthetic coverage states, not device readings.</p><dl class="evidence-grid"><dt>Source</dt><dd>Sample Android app-usage events</dd><dt>Enabled from</dt><dd>06:00 UTC · sample day</dd><dt>Known gap</dt><dd>12:00–12:30 UTC · access unavailable</dd><dt>Current state</dt><dd>${state.scenario === 'gap' ? 'Access interrupted from 18:00; affected rules unavailable' : 'Sample grant available; no actual collector'}</dd><dt>Time shown</dt><dd>Observed time only. Missing activity is not estimated.</dd><dt>Last example check</dt><dd>${state.scenario === 'gap' ? '18:00' : '20:30'} UTC · fixed fixture</dd></dl><p>Future app: opening Android settings would recheck the grant on return. It would not silently enable a source or import pre-consent history.</p><div class="stack">${button('Review source choices','sources','secondary')}${button('Got it','close')}</div>`);
}
function insight() {
  if (!activeData() || state.scenario === 'gap') return coverage();
  showSheet('A pattern, not a verdict.', `<span class="badge">DESCRIPTIVE · SYNTHETIC</span><p>Social sample reopened at 18:10, 18:16, and 18:20 UTC, after leaving the app each time.</p><dl class="evidence-grid"><dt>Observation</dt><dd>3 package reopenings in 10 minutes</dd><dt>Coverage</dt><dd>The full 18:10–18:20 window in this fixture</dd><dt>Chosen threshold</dt><dd>At least 3 reopenings in 10 minutes</dd><dt>Cannot tell</dt><dd>Your intention, feelings, or whether this was a problem</dd></dl><p>If the pattern feels unhelpful, you could try a reminder. If it does not, you do not need to act.</p><div class="stack">${button('Explore a reminder','insight-rule','secondary')}${button('No change for now','close','secondary')}</div>`);
}
function sources() {
  showSheet('Choose what to observe.', `<p>This is a consent journey preview. It cannot grant Android access or record activity. Each real source would start off.</p><label class="check"><input type="checkbox" id="source-choice" ${state.source ? 'checked' : ''}> App usage · demo selection</label><p>Would retain selected app identifiers, session times, and package entries. It would require Android usage access and an explicit app selection.</p><p>Screen events, notifications, accessibility surfaces, titles, URLs, and topics require their own choices. None is enabled here.</p><div class="stack">${button('Review selected source','source-disclosure','secondary')}${button('Keep tracking off','source-off','secondary')}</div>`);
}
function disclosure() {
  if (!document.querySelector('#source-choice')?.checked && !state.source) {
    announce('No source selected. Nothing has been enabled.');
    closeSheet();
    return;
  }
  state.source = true;
  showSheet('App usage, explained.', `<p>Future app: selected app identifiers, foreground intervals, and app-open events would stay in the local history. Content, passwords, messages, and URLs are not part of this source.</p><p>Keep history until you delete it by default. You could exclude apps, pause collection, choose retention, or delete history.</p><div class="callout">The real app would next open Android usage-access settings, check the result, then ask you to enable the selected source. This preview does none of those things.</div><div class="stack">${button('Finish explanation · keep tracking off','source-off','secondary')}${button('Back to source choices','sources','secondary')}</div>`);
}
function editRule(edit = false) {
  state.draft = { budget:edit ? state.ruleBudget : 30, action:edit ? state.ruleAction : 'remind', edit };
  showSheet(edit ? 'Review your boundary.' : 'A boundary you choose.', `<p>Demo draft · Social sample only. No real apps are affected.</p><form id="rule-form"><label class="field">Daily app-time budget, in minutes<input id="budget" type="number" min="1" max="1440" step="1" value="${state.draft.budget}" required></label><fieldset class="choices"><legend>When the budget is reached</legend><label><input type="radio" name="action" value="remind" ${state.draft.action === 'remind' ? 'checked' : ''}> A gentle reminder · immediate dismissal</label><label><input type="radio" name="action" value="pause" ${state.draft.action === 'pause' ? 'checked' : ''}> A pause prompt · immediate override</label></fieldset><p class="micro">Emergency access is always immediate. Observation settings are separate. Timezone: UTC for this fixture; budget resets at midnight.</p><div class="stack"><button class="primary" type="submit">Review demo rule</button>${button('Cancel draft','cancel-draft','secondary','type="button"')}</div></form>`);
}
function rulePreview(event) {
  event.preventDefault();
  const budget = Number(document.querySelector('#budget').value);
  if (!Number.isInteger(budget) || budget < 1 || budget > 1440) return;
  state.draft.budget = budget;
  state.draft.action = document.querySelector('input[name=action]:checked').value;
  const { action, edit } = state.draft;
  showSheet('Here is what you chose.', `<dl class="evidence-grid"><dt>Target</dt><dd>Social sample · entire demo app</dd><dt>Trigger</dt><dd>${budget} observed app minutes per UTC day</dd><dt>Action</dt><dd>${action === 'remind' ? 'A reminder you can dismiss immediately' : 'A pause prompt with an immediate scoped override'}</dd><dt>Ordinary override</dt><dd>This demo rule only, for 5 minutes; other rules still apply</dd><dt>Emergency access</dt><dd>Immediate release of all demo rules</dd><dt>Exceptions</dt><dd>Calls, Android settings, and recovery are never obstructed</dd><dt>Requirements</dt><dd>Real enforcement is unimplemented; no access requested</dd></dl>${!edit ? '<div class="callout">This overlaps with the sample social rule. Usage is counted once. The stricter active action applies; a single-rule override does not waive another rule.</div>' : ''}<p>No rule will be activated on your device. This saves an in-memory preview only.</p><div class="stack">${button('Save demo configuration','save-rule')}${button('Discard draft','cancel-draft','secondary')}</div>`);
}
function intervention() {
  showSheet('A moment to choose.', `<div class="prompt-art">${icon('leaf')}</div><p class="eyebrow">SAMPLE RULE · NOT A LIVE BLOCK</p><p>You chose a ${state.ruleBudget}-minute social budget. This example shows the pause you asked for when it is reached.</p><p>Would you like to return to what you had planned, or continue for a little while?</p><div class="stack">${button('Return to my plans','leave-prompt')}${button('Continue for 5 minutes','override','secondary')}${button('Emergency access · release all now','emergency','emergency')}</div><p class="micro">Ordinary override: this demo rule only. Other rules would still apply. Usage would remain in history. Emergency access is immediate and releases all demo rules.</p>`);
}
function privacy() {
  showSheet('Trust you can inspect.', `<p><strong>This preview:</strong> synthetic data only. No behavioral collection, browser storage, tracking permission, or automatic external requests. Reloading resets the demo.</p><p><strong>Planned application:</strong> history and interpretation on your device. No developer backend, mandatory account, ads, remote analytics, or automatic diagnostics.</p><p>Optional export or approved future cloud backup sends encrypted data and provider-visible metadata to your chosen provider. The developer receives neither. Direct automatic Google backup is currently blocked pending policy clarification.</p><p>Open source supports inspection. It does not replace build, security, or device verification; those app checks have not run.</p><a class="text-button" href="https://github.com/yasirarfat-dev/AttentionStats" target="_blank" rel="noopener noreferrer">Read the public source ${icon('arrow')}</a>${button('Close privacy explanation','close','secondary')}`);
}
function storage() {
  showSheet('Your history, your choice.', `<p>Planned app controls: measured storage, preserve-until-deleted default, optional pruning, scoped deletion, and portable encrypted export and restore.</p><p>There is no history database, encryption, archive creation, provider connection, or deletion implementation in this preview. A browser reset clears only this synthetic demo state.</p><div class="callout">Automatic personal Google-account backup is blocked pending policy clarification. Manual encrypted exports will be a separate feature.</div>${button('Got it','close','secondary')}`);
}
function counting() {
  showSheet('What an exposure means.', `<p>A candidate detector would first identify the app, then a supported short-video surface from qualified structural accessibility metadata.</p><p>One stable item produces one exposure. Repeated callbacks or redraws add none. A confirmed next item adds another. Revisiting an item may count as another exposure.</p><p>That is different from unique videos, completed watches, or measured attention. An unknown layout must report unknown, not invent a count from every swipe.</p><p>The 18 exposures shown here are an illustrative fixture. No real target version, locale, selector, or detector is qualified.</p>${button('Got it','close','secondary')}`);
}
function finishFocus(emergency = false) {
  state.deadline = null;
  if (emergency) { state.protectionPaused = true; state.ruleOn = false; }
  closeSheet();
  state.route = 'protect';
  render();
  announce(emergency ? 'Emergency access: all demo rules released immediately. No device apps were blocked.' : 'Focus preview ended. You can return whenever it helps.');
}
const actions = {
  close: closeSheet, privacy, coverage, insight, sources, counting, storage, intervention,
  'live-overlay': () => navigate('overlay'),
  'overlay-detail': overlayDetail,
  'overlay-next': () => {
    if (state.overlay.unknown) { announce('Surface unknown. No exposure added.'); return; }
    state.overlay.visit++; state.overlay.today++;
    render({focus:false});
    screen.querySelector('[data-action=overlay-next]').focus({preventScroll:true});
    announce('One confirmed synthetic exposure added to this visit and today.');
  },
  'overlay-duplicate': () => announce('Same item callback replayed. Counts unchanged.'),
  'overlay-visit': () => {
    if (state.overlay.unknown) { announce('Surface unknown. Cannot confirm a new visit.'); return; }
    state.overlay.visit=1; state.overlay.today++;
    render(); announce('New visit confirmed with its first exposure. Today retained and incremented once.');
  },
  'overlay-signal': () => {
    state.overlay.unknown = !state.overlay.unknown;
    render(); announce(state.overlay.unknown ? 'Surface unknown. Counter removed.' : 'Sample signal restored. Counts retained without an invented transition.');
  },
  'overlay-hide': () => { state.overlay.hidden=!state.overlay.hidden; render(); announce('Only the counter display changed. Tracking and protection choices unchanged.'); },
  'overlay-hide-dialog': () => { closeSheet(); actions['overlay-hide'](); },
  'overlay-position': event => { state.overlay.corner=event.target.closest('button').dataset.position; closeSheet(); render(); },
  'overlay-pause': () => { closeSheet(); navigate('protect'); announce('Choose a focus preview if it helps. No rule automatically activated.'); },
  'preview-prompt': intervention,
  'go-protect': () => navigate('protect'), 'go-history': () => navigate('history'), 'go-settings': () => navigate('settings'),
  skip: () => { navigate('settings'); announce('Tracking remains off. Settings are available without permissions.'); },
  table: () => {
    const position = window.scrollY;
    state.table = !state.table; render({ focus:false });
    screen.querySelector('[data-action=table]').focus({ preventScroll:true });
    window.scrollTo(0, position);
  },
  'source-disclosure': disclosure,
  'source-off': () => { state.source = false; closeSheet(); announce('Tracking remains off. No source or permission was enabled.'); },
  'add-rule': () => editRule(false), 'edit-rule': () => editRule(true),
  'insight-rule': () => editRule(false),
  'cancel-draft': () => { state.draft = null; closeSheet(); announce('Draft discarded. Demo rules unchanged.'); },
  'save-rule': () => {
    if (!state.draft) return;
    const { budget, action, edit } = state.draft;
    if (edit) { state.ruleBudget = budget; state.ruleAction = action; }
    else state.extraRule = { budget, action };
    state.draft = null; closeSheet(); navigate('protect');
    announce('Demo configuration saved in memory. No actual protection activated.');
  },
  'toggle-rule': event => {
    state.ruleOn = !state.ruleOn;
    const control = event.target.closest('button');
    control.setAttribute('aria-pressed', state.ruleOn); control.textContent = state.ruleOn ? 'On' : 'Off';
    announce(`Demo rule ${state.ruleOn ? 'enabled' : 'disabled'}. No apps are blocked.`);
  },
  'pause-protection': () => { state.protectionPaused = !state.protectionPaused; render(); announce(`Demo rules ${state.protectionPaused ? 'paused' : 'resumed'}. Observation choice unchanged.`); },
  'start-focus': () => {
    state.deadline = performance.now() + state.duration*60000;
    render(); announce('Focus timer preview started. No apps are being blocked.');
  },
  'end-focus': () => finishFocus(), emergency: () => finishFocus(true),
  'leave-prompt': () => { closeSheet(); navigate('protect'); announce('Prompt dismissed. Choose your next step.'); },
  override: () => { closeSheet(); announce('Demo override selected for this sample rule for 5 minutes. Other rules would remain active. No device rules changed.'); },
};
document.addEventListener('click', event => {
  const target = event.target.closest('button');
  if (!target) return;
  if (target.dataset.route) navigate(target.dataset.route);
  else if (target.dataset.app) { state.details = target.dataset.app; navigate('app'); }
  else if (target.dataset.duration) {
    state.duration = Number(target.dataset.duration);
    document.querySelectorAll('[data-duration]').forEach(control => control.setAttribute('aria-pressed', Number(control.dataset.duration) === state.duration));
    screen.querySelector('[data-action=start-focus]').textContent = `Preview ${state.duration} minutes of focus`;
  } else if (actions[target.dataset.action]) actions[target.dataset.action](event);
});
document.addEventListener('submit', event => { if (event.target.id === 'rule-form') rulePreview(event); });
document.addEventListener('change', event => {
  const { id, value, checked } = event.target;
  if (id === 'scenario') {
    state.scenario = value; state.source = false; state.route = 'today'; state.table = false;
    render({ focus:false }); announce(`Experience changed: ${event.target.selectedOptions[0].textContent}. Synthetic data only.`);
  } else if (id === 'theme' || id === 'app-theme') { state.theme = value; applyPreferences(); }
  else if (id === 'reduce-motion' || id === 'app-motion') { state.motion = checked; applyPreferences(); }
  else if (id === 'large-text' || id === 'app-large') { state.large = checked; applyPreferences(); }
});
document.querySelector('#preview-prompt').addEventListener('click', intervention);
document.querySelector('#preview-overlay').addEventListener('click', () => navigate('overlay'));
document.querySelector('#reset').addEventListener('click', () => { state = freshState(); closeSheet(); render({ focus:false }); announce('Demo reset. All changes discarded. No tracking enabled.'); });
function tick() {
  if (!state.deadline) return;
  const remaining = Math.max(0, Math.ceil((state.deadline - performance.now())/1000));
  const output = document.querySelector('#focus-time');
  if (output) output.textContent = `${Math.floor(remaining/60)}:${String(remaining%60).padStart(2,'0')}`;
  if (remaining === 0) { state.deadline = null; if (state.route === 'protect') render(); announce('Your focus preview is complete. Take a moment before choosing what comes next.'); }
}
setInterval(tick, 1000);
render({ focus:false });
