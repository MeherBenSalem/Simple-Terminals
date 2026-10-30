// Renders the listing artwork in marketing/images/ from the real in-game captures (images/screenshots/), the offline
// model renders (source/renders/, made by source/renders.py) and the bundled OFL fonts.
// Usage (from the repository root): NODE_PATH="$(npm root -g)" node marketing/source/render.cjs [name...]
// Requires the `playwright` package with a Chromium build available.
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

// The release and the supported versions. Adding a version is one line here (plus a row in curseforge/description.md).
const RELEASE = { version: '2.0', name: 'OmniVault' };
const VERSIONS = [{ minecraft: '1.21.1', loaders: ['NeoForge'] }];

const root = path.resolve(__dirname, '..', '..');
const out = path.join(root, 'marketing', 'images');
const shots = path.join(out, 'screenshots');
const renders = path.join(__dirname, 'renders');
const fonts = path.join(__dirname, 'fonts');
const url = (file) => 'file://' + file;
const shot = (name) => url(path.join(shots, name));
const art = (name) => url(path.join(renders, name + '.png'));
const source = (name) => url(path.join(__dirname, name));

// The mod's own GUI palette (client/gui/GuiStyle.java).
const C = {
  outline: '#07090d', panel: '#1b2029', panelLight: '#2a313d', highlight: '#3e4859', shadow: '#0f1218', well: '#11151c',
  slot: '#262d38', cyan: '#38e1ff', cyanDim: '#1a7f99', violet: '#a46bff', text: '#e6edf3', dim: '#8b949e',
  good: '#5be08a', warn: '#f2c14e', bad: '#ff6b6b', bg: '#0a0d12',
};

const css = `
@font-face { font-family: OvDisplay; font-weight: 500; src: url('${url(path.join(fonts, 'barlow-condensed-latin-500-normal.woff2'))}'); }
@font-face { font-family: OvDisplay; font-weight: 600; src: url('${url(path.join(fonts, 'barlow-condensed-latin-600-normal.woff2'))}'); }
@font-face { font-family: OvDisplay; font-weight: 700; src: url('${url(path.join(fonts, 'barlow-condensed-latin-700-normal.woff2'))}'); }
@font-face { font-family: OvDisplay; font-weight: 800; src: url('${url(path.join(fonts, 'barlow-condensed-latin-800-normal.woff2'))}'); }
@font-face { font-family: OvText; font-weight: 400; src: url('${url(path.join(fonts, 'inter-latin-400-normal.woff2'))}'); }
@font-face { font-family: OvText; font-weight: 500; src: url('${url(path.join(fonts, 'inter-latin-500-normal.woff2'))}'); }
@font-face { font-family: OvText; font-weight: 600; src: url('${url(path.join(fonts, 'inter-latin-600-normal.woff2'))}'); }
:root { --cyan:${C.cyan}; --violet:${C.violet}; --text:${C.text}; --dim:${C.dim}; --panel:${C.panel}; --bg:${C.bg}; }
* { box-sizing: border-box; }
html, body { margin: 0; background: var(--bg); color: var(--text); font-family: OvText, sans-serif; }
.stage { position: relative; overflow: hidden; background: radial-gradient(ellipse at 72% 40%, #18202b 0%, #0a0d12 70%); }
.display { font-family: OvDisplay, sans-serif; font-weight: 800; text-transform: uppercase; letter-spacing: .015em; }
.title { background: linear-gradient(180deg, #ffffff 0%, #e3f9ff 45%, #9eefff 75%, #38e1ff 100%);
         -webkit-background-clip: text; background-clip: text; color: transparent; }
.glow { filter: drop-shadow(0 0 26px rgba(56,225,255,.28)) drop-shadow(0 3px 0 rgba(0,0,0,.65)); }
.grad { background: linear-gradient(90deg, ${C.cyan} 0%, #8fd9ff 45%, ${C.violet} 100%); -webkit-background-clip: text; background-clip: text; color: transparent; }
.kicker { font-family: OvDisplay, sans-serif; font-weight: 700; color: var(--cyan); letter-spacing: .3em; font-size: 22px; text-transform: uppercase;
          display: flex; align-items: center; gap: 14px; }
.led { display: inline-block; width: 12px; height: 12px; background: ${C.good}; box-shadow: 0 0 10px ${C.good}, inset 0 0 0 2px rgba(0,0,0,.25); }
.led.cyan { background: var(--cyan); box-shadow: 0 0 10px var(--cyan); } .led.violet { background: var(--violet); box-shadow: 0 0 10px var(--violet); }
.led.amber { background: ${C.warn}; box-shadow: 0 0 10px ${C.warn}; } .led.red { background: ${C.bad}; box-shadow: 0 0 10px ${C.bad}; }
.rule { height: 3px; background: linear-gradient(90deg, ${C.cyan} 0%, ${C.cyan} 30%, ${C.violet} 70%, rgba(164,107,255,0) 100%); }
ul.points { list-style: none; padding: 0; margin: 0; }
ul.points li { position: relative; padding-left: 30px; margin: 0 0 15px; font-size: 25px; line-height: 1.34; color: var(--text); }
ul.points li::before { content: ''; position: absolute; left: 1px; top: 11px; width: 10px; height: 10px; background: var(--cyan);
                       box-shadow: 0 0 8px rgba(56,225,255,.8); }
b.hi { color: #fff; font-weight: 600; } b.c { color: var(--cyan); font-weight: 600; } b.v { color: #c9a8ff; font-weight: 600; }
/* A machined panel in the style of GuiStyle.window: outline, bevel, an accent strip on top and four rivets. */
.panel { position: relative; background: ${C.panel}; border: 2px solid ${C.outline};
         box-shadow: inset 2px 2px 0 ${C.highlight}, inset -2px -2px 0 ${C.shadow}, 0 30px 80px rgba(0,0,0,.65), 0 0 60px rgba(56,225,255,.08); }
.panel::before { content: ''; position: absolute; left: 6px; right: 6px; top: 3px; height: 3px; background: linear-gradient(90deg, ${C.cyan}, ${C.cyanDim}); z-index: 2; }
.rivets > i { position: absolute; width: 5px; height: 5px; background: ${C.shadow}; box-shadow: inset 1px 1px 0 ${C.highlight}; z-index: 2; }
.frame { position: relative; padding: 8px; }
.frame .inner { position: relative; overflow: hidden; background: #000; border: 2px solid ${C.outline}; }
.frame .inner > img, .frame .inner > div { display: block; }
.plate { position: absolute; font-family: OvDisplay, sans-serif; font-weight: 700; font-size: 21px; letter-spacing: .12em; text-transform: uppercase;
         color: #fff; background: rgba(15,18,24,.92); border: 2px solid ${C.outline}; box-shadow: inset 2px 2px 0 ${C.highlight}, 0 8px 24px rgba(0,0,0,.6);
         padding: 6px 14px 5px 12px; white-space: nowrap; z-index: 3; display: flex; align-items: center; gap: 10px; }
.plate small { font-family: OvText, sans-serif; font-weight: 500; letter-spacing: 0; text-transform: none; font-size: 16px; color: var(--dim); }
.grid { position: absolute; inset: 0; pointer-events: none;
        background-image: linear-gradient(rgba(56,225,255,.06) 1px, transparent 1px), linear-gradient(90deg, rgba(56,225,255,.06) 1px, transparent 1px),
                          linear-gradient(rgba(56,225,255,.03) 1px, transparent 1px), linear-gradient(90deg, rgba(56,225,255,.03) 1px, transparent 1px);
        background-size: 128px 128px, 128px 128px, 32px 32px, 32px 32px; }
.vignette { position: absolute; inset: 0; background: radial-gradient(ellipse at center, rgba(0,0,0,0) 38%, rgba(0,0,0,.8) 100%); }
.backdrop { position: absolute; inset: -40px; background-size: cover; background-position: center; filter: blur(16px) brightness(.32) saturate(1.2); }
.chip { display: inline-flex; align-items: center; gap: 8px; font-family: OvDisplay, sans-serif; font-weight: 700; font-size: 20px; letter-spacing: .1em;
        text-transform: uppercase; padding: 6px 14px 5px; margin: 0 10px 10px 0; background: rgba(27,32,41,.9); border: 2px solid ${C.outline};
        box-shadow: inset 2px 2px 0 ${C.highlight}; color: #fff; }
.stat { font-family: OvDisplay, sans-serif; font-weight: 800; font-size: 34px; color: #fff; line-height: 1; }
.cap { font-family: OvDisplay, sans-serif; font-weight: 600; font-size: 17px; letter-spacing: .14em; text-transform: uppercase; color: var(--dim); }
img.px { image-rendering: pixelated; }
kbd { font-family: OvDisplay, sans-serif; font-weight: 700; font-size: .85em; color: #fff; background: ${C.panelLight};
      border: 2px solid ${C.outline}; box-shadow: inset 2px 2px 0 ${C.highlight}; padding: 0 9px; }
`;

const page = (width, height, body, transparent = false) => `<!doctype html><html><head><meta charset="utf-8"><style>${css}
body { width: ${width}px; height: ${height}px; } .stage { width: ${width}px; height: ${height}px; }
${transparent ? 'html, body, .stage { background: transparent; }' : ''}</style></head>
<body><div class="stage">${body}</div></body></html>`;

const rivets = () => `<div class="rivets"><i style="left:9px;top:11px"></i><i style="right:9px;top:11px"></i><i style="left:9px;bottom:9px"></i><i style="right:9px;bottom:9px"></i></div>`;

// An exact region (x, y, w, h in 1920x1080 pixels) of a capture, scaled to W pixels wide.
const region = (file, x, y, w, h, W) => `<div style="position:relative;width:${W}px;height:${Math.round(W * h / w)}px;overflow:hidden">
  <img src="${shot(file)}" style="position:absolute;max-width:none;width:${W * 1920 / w}px;left:${-x * W / w}px;top:${-y * W / w}px;image-rendering:${W / w >= 1 ? 'pixelated' : 'auto'}"></div>`;
const framed = (inner, style = '') => `<div class="panel frame" style="${style}">${rivets()}<div class="inner">${inner}</div></div>`;
const plate = (text, style, small = '', led = 'cyan') => `<div class="plate" style="${style}"><span class="led ${led}"></span>${text}${small ? ` <small>${small}</small>` : ''}</div>`;

// GUI regions in the 1920x1080 captures (GUI scale 3).
const GUI = {
  terminal: ['terminal.jpg', 600, 165, 660, 750],
  fluids: ['terminal-fluids.jpg', 600, 165, 660, 290],
  crafting: ['crafting.jpg', 600, 150, 660, 780],
  vault: ['vault-gui.jpg', 448, 128, 1024, 820],
  backpack: ['backpack-gui.jpg', 504, 198, 1176, 684],
  request: ['request.jpg', 664, 188, 592, 706],
  status: ['crafting-status.jpg', 664, 226, 592, 628],
  pattern: ['pattern-terminal.jpg', 600, 150, 660, 780],
  early: ['early-terminal.jpg', 600, 165, 660, 750],
};
const gui = (key, W) => { const [f, x, y, w, h] = GUI[key]; return region(f, x, y, w, h, W); };

const header = (title) => page(1400, 120, `
  <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:24px">
    <div class="rule" style="width:290px;transform:scaleX(-1)"></div>
    <span class="led cyan" style="width:14px;height:14px"></span>
    <div class="display title glow" style="font-size:60px;white-space:nowrap;letter-spacing:.04em">${title}</div>
    <span class="led violet" style="width:14px;height:14px"></span>
    <div class="rule" style="width:290px"></div>
  </div>`, true);

const divider = () => page(1400, 40, `
  <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:14px">
    <div class="rule" style="width:460px;transform:scaleX(-1);opacity:.75"></div>
    <span class="led"></span><span class="led cyan"></span><span class="led amber"></span><span class="led violet"></span>
    <div class="rule" style="width:460px;opacity:.75"></div>
  </div>`, true);

// A feature card: copy on the left, artwork on the right.
const card = ({ kicker, title, points, art: artwork, artWidth = 940, backdrop = 'backdrop.jpg', led = 'cyan' }) => page(1600, 900, `
  <div class="backdrop" style="background-image:url('${source(backdrop)}')"></div>
  <div class="grid"></div><div class="vignette"></div>
  <div style="position:absolute;left:80px;top:0;bottom:0;width:${1600 - artWidth - 150}px;display:flex;flex-direction:column;justify-content:center">
    <div class="kicker"><span class="led ${led}"></span>${kicker}</div>
    <div class="display title glow" style="font-size:72px;line-height:.98;margin:18px 0 22px">${title}</div>
    <div class="rule" style="width:80%;margin-bottom:30px"></div>
    <ul class="points">${points.map((p) => `<li>${p.replace(/ (\S+)$/, '&nbsp;$1')}</li>`).join('')}</ul>
  </div>
  <div style="position:absolute;right:60px;top:50%;transform:translateY(-50%);width:${artWidth}px">${artwork}</div>`);

const tile = (img, label, sub, size = 120, extra = '') => `<div style="display:flex;flex-direction:column;align-items:center;gap:6px;${extra}">
  <div style="width:${size}px;height:${size}px;display:flex;align-items:center;justify-content:center;filter:drop-shadow(0 12px 18px rgba(0,0,0,.7))">
    <img src="${img}" style="max-width:100%;max-height:100%"></div>
  <div class="display" style="font-size:22px;color:#fff;letter-spacing:.06em">${label}</div>
  ${sub ? `<div class="cap" style="font-size:15px;margin-top:-4px">${sub}</div>` : ''}</div>`;

const CELLS = [['4k', '4K', '4,096'], ['16k', '16K', '16,384'], ['64k', '64K', '65,536'], ['256k', '256K', '262,144'],
  ['1m', '1M', '1,048,576'], ['16m', '16M', '16.7M'], ['singularity', 'Singularity', '2.1 billion']];
const VAULTS = [['copper', 45], ['iron', 54], ['gold', 81], ['diamond', 108], ['netherite', 135], ['omnium', 162]];
const BACKPACKS = [['leather', 27], ['copper', 36], ['iron', 54], ['gold', 81], ['diamond', 108], ['netherite', 120]];
const BP_UPGRADES = ['pickup', 'magnet', 'void', 'feeding', 'restock', 'compacting', 'crafting', 'everlasting', 'deposit'];

const versionText = VERSIONS.map((v) => `Minecraft ${v.minecraft}`).join(' · ');
const loaders = [...new Set(VERSIONS.flatMap((v) => v.loaders))];

const assets = {
  banner: () => page(1800, 600, `
    <img src="${shot('base.jpg')}" style="position:absolute;right:-120px;top:-80px;width:1380px;-webkit-mask-image:linear-gradient(90deg,transparent 0%,#000 30%);mask-image:linear-gradient(90deg,transparent 0%,#000 30%)">
    <div style="position:absolute;inset:0;background:linear-gradient(90deg,rgba(10,13,18,.98) 0%,rgba(10,13,18,.92) 34%,rgba(10,13,18,.35) 58%,rgba(10,13,18,0) 80%)"></div>
    <div style="position:absolute;inset:0;background:linear-gradient(0deg,rgba(10,13,18,.8) 0%,rgba(10,13,18,0) 34%)"></div>
    <div class="grid" style="-webkit-mask-image:linear-gradient(90deg,#000 0%,#000 36%,transparent 60%);mask-image:linear-gradient(90deg,#000 0%,#000 36%,transparent 60%)"></div>
    <div class="vignette" style="opacity:.5"></div>
    <div style="position:absolute;left:86px;top:70px;width:1000px">
      <div class="kicker"><span class="led"></span><span class="led amber"></span><span class="led violet"></span>The all-in-one storage mod</div>
      <div class="display title glow" style="font-size:168px;line-height:.86;margin:20px 0 12px;letter-spacing:.02em">OmniVault</div>
      <div class="rule" style="width:620px;margin:8px 0 24px"></div>
      <div style="font-size:29px;line-height:1.32;font-weight:500;color:#fff;width:640px">
        Chest networks on day one. Cell drives, autocrafting and quantum bridges by the end. Vaults, drawers and backpacks too.</div>
      <div style="margin-top:28px;display:flex;align-items:center">${loaders.map((l) => `<span class="chip" style="margin-bottom:0"><span class="led cyan"></span>${l}</span>`).join('')}<span class="chip" style="margin-bottom:0"><span class="led violet"></span>${versionText}</span>
        <span style="display:inline-flex;gap:2px;margin-left:12px">${CELLS.map(([id]) => `<img src="${art('cell-' + id)}" style="width:46px;filter:drop-shadow(0 6px 8px rgba(0,0,0,.8))">`).join('')}</span></div>
    </div>`),

  icon: () => page(512, 512, `
    <div style="position:absolute;inset:14px;border-radius:84px;overflow:hidden;background:radial-gradient(circle at 50% 44%,#24303f 0%,#141a22 56%,#0b0e13 100%);
                border:4px solid ${C.outline};box-shadow:inset 6px 6px 0 rgba(62,72,89,.9),inset -6px -6px 0 rgba(15,18,24,.95)">
      <div class="grid" style="background-size:64px 64px,64px 64px,16px 16px,16px 16px;opacity:.9"></div>
      <div style="position:absolute;left:24px;right:24px;top:10px;height:8px;background:linear-gradient(90deg,${C.cyan},${C.violet});box-shadow:0 0 16px rgba(56,225,255,.7)"></div>
      <svg width="484" height="484" style="position:absolute;inset:0">
        <circle cx="242" cy="256" r="178" fill="none" stroke="${C.violet}" stroke-width="5" opacity=".55" stroke-dasharray="18 12"/>
        <circle cx="242" cy="256" r="158" fill="none" stroke="${C.cyan}" stroke-width="3" opacity=".45"/>
      </svg>
      <img src="${art('core')}" style="position:absolute;left:52px;top:66px;width:380px;filter:drop-shadow(0 0 30px rgba(56,225,255,.45)) drop-shadow(0 18px 22px rgba(0,0,0,.8))">
      ${[[34, 444], [440, 444], [34, 30], [440, 30]].map(([x, y]) => `<i style="position:absolute;left:${x}px;top:${y}px;width:10px;height:10px;background:${C.shadow};box-shadow:inset 2px 2px 0 ${C.highlight}"></i>`).join('')}
    </div>`, true),

  'card-network': () => card({
    kicker: 'The storage network', title: 'One core.<br>No channels.',
    points: ['A <b class="c">Nexus Core</b> runs any number of devices, no channel limits',
      '<b class="hi">Cell Drives</b> hold ten item or fluid cells, each with a status LED',
      '<b class="hi">Storage Monitors</b> show live counts on your walls',
      'Wireless terminals reach 32 blocks from the core, 64+ from a transmitter',
      'Forge Energy is optional and off by default'],
    art: `<div style="position:relative">${framed(region('base.jpg', 120, 40, 1680, 945, 940))}
      ${plate('Nexus Core', 'left:392px;bottom:120px')}${plate('Cell Drives', 'left:300px;top:250px', '', 'green')}
      ${plate('Storage Monitors', 'right:40px;top:120px', '', 'violet')}</div>` }),

  'card-cells': () => card({
    kicker: 'Storage cells', title: '4K to<br>Singularity',
    points: ['Seven tiers of item cells, from <b class="hi">4,096</b> to <b class="hi">2,147,483,648</b> items',
      'Fluid cells for every tier, up to <b class="hi">536 million buckets</b>',
      '128 item types per cell (32 fluids); Singularity cells take 1,024',
      'Contents live in the world save, never on the item',
      'Creative cells for modpacks and testing'],
    art: `<div class="panel" style="padding:34px 30px 26px">${rivets()}
      <div style="display:flex;justify-content:space-between;align-items:flex-end">
        ${CELLS.map(([id, name, cap]) => tile(art('cell-' + id), name, cap + (id === 'singularity' ? '' : ''), 104)).join('')}</div>
      <div style="display:flex;align-items:center;gap:30px;margin-top:30px;padding-top:26px;border-top:2px solid ${C.outline};box-shadow:0 -2px 0 ${C.highlight} inset">
        <img src="${art('drive')}" style="width:280px;filter:drop-shadow(0 14px 18px rgba(0,0,0,.7))">
        <div style="flex:1">
          <div class="display" style="font-size:40px;color:#fff">Cell Drive</div>
          <div style="font-size:21px;color:var(--dim);margin:6px 0 16px;line-height:1.4">Ten bays. Each LED shows its cell at a glance.</div>
          <div style="display:flex;gap:22px;font-size:20px">
            <span><span class="led"></span> &nbsp;room to spare</span><span><span class="led amber"></span> &nbsp;nearly full</span><span><span class="led red"></span> &nbsp;full</span></div>
        </div></div></div>` }),

  'card-terminals': () => card({
    kicker: 'Terminals', title: 'Find anything<br>in a keystroke',
    points: ['Search by name, <b class="c">@mod</b> or <b class="c">#tag</b>; sort by count, name, mod or ID',
      'A fluid view: fill and empty buckets right in the grid',
      'The <b class="hi">Crafting Terminal</b> refills its 3×3 grid from the network',
      'JEI’s <kbd>+</kbd> moves recipes into the grid, network first',
      'Craftable items show <b class="c">Craft</b>: middle-click to order'],
    art: `<div style="position:relative;height:740px">
      ${framed(gui('terminal', 520), 'position:absolute;left:0;top:0;width:540px')}
      ${framed(gui('crafting', 400), 'position:absolute;right:0;top:110px;width:420px')}
      ${framed(gui('fluids', 400), 'position:absolute;left:300px;bottom:0;width:420px')}
      ${plate('Storage Terminal', 'left:24px;top:-18px')}${plate('Crafting Terminal', 'right:24px;top:92px', '', 'violet')}
      ${plate('Fluids', 'left:322px;bottom:178px', '', 'green')}</div>` }),

  'card-early': () => card({
    kicker: 'Day one', title: 'Every chest,<br>one terminal',
    points: ['A <b class="c">Storage Hub</b> and <b class="hi">Inventory Cable</b>, from vanilla materials',
      'Every chest, barrel or other inventory the cable touches joins in',
      'Double chests count once',
      'Search, sort, take and craft from the whole room',
      'Swap the hub for a Nexus Core later: everything keeps working'],
    art: `<div style="position:relative;height:720px">
      ${framed(region('early.jpg', 0, 60, 1920, 1020, 860), 'position:absolute;left:0;top:0;width:880px')}
      ${framed(gui('early', 330), 'position:absolute;right:0;bottom:0;width:350px')}
      ${plate('Storage Hub', 'left:560px;top:250px', '', 'amber')}${plate('Inventory Cable', 'left:150px;top:200px', '', 'amber')}</div>` }),

  'card-autocrafting': () => card({
    kicker: 'Autocrafting', title: 'Ask for it.<br>It gets made.',
    points: ['Encode crafting and processing patterns; JEI’s <kbd>+</kbd> loads any recipe',
      'The planner uses stock first, crafts only the shortfall, sub-crafts included',
      '<b class="hi">Assemblers</b> craft, <b class="hi">Pattern Providers</b> feed furnaces and any mod’s machines',
      'Ingredients are reserved; cancel anytime to get them back',
      'One job per <b class="c">Crafting Processor</b>, any size'],
    art: `<div style="position:relative;height:740px">
      ${framed(region('autocraft.jpg', 0, 160, 1920, 900, 900), 'position:absolute;left:40px;top:0;width:900px')}
      ${framed(gui('request', 380), 'position:absolute;left:0;bottom:0;width:400px')}
      ${framed(gui('status', 420), 'position:absolute;right:0;bottom:30px;width:440px')}
      ${plate('Five-step plan', 'left:24px;top:262px', '', 'green')}${plate('Assemblers at work', 'right:30px;top:20px', '', 'violet')}</div>` }),

  'card-automation': () => card({
    kicker: 'Automation', title: 'Wire it into<br>everything',
    points: ['Import, Export and Storage Buses for items and fluids',
      'The <b class="hi">Network Port</b>: your whole network as one inventory for pipes',
      'Destructor and Constructor mine and place from the network',
      'Signal Emitters react to stock levels',
      'Works with any mod that exposes NeoForge item or fluid handlers'],
    art: `<div class="panel" style="padding:32px 30px 20px">${rivets()}
      <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:14px 16px">
        ${[['import_bus', 'Import Bus', 'pulls in'], ['export_bus', 'Export Bus', 'pushes out'], ['storage_bus', 'Storage Bus', 'any inventory as storage'],
           ['network_port', 'Network Port', 'for pipes and hoppers'], ['destructor', 'Destructor', 'mines into storage'], ['constructor', 'Constructor', 'places from storage'],
           ['signal_emitter', 'Signal Emitter', 'redstone on stock levels'], ['storage_monitor', 'Storage Monitor', 'a live count'], ['wireless_transmitter', 'Transmitter', 'wireless range']]
          .map(([id, name, sub]) => tile(art('device-' + id), name, sub, 112)).join('')}</div>
      <div style="display:flex;justify-content:center;gap:24px;margin-top:22px;padding-top:18px;border-top:2px solid ${C.outline};box-shadow:0 -2px 0 ${C.highlight} inset">
        ${['speed', 'stack', 'fuzzy', 'redstone', 'range'].map((u) => tile(art('upgrade-' + u), u, 'upgrade', 64)).join('')}</div></div>` }),

  'card-vaults': () => card({
    kicker: 'Vaults', title: 'Six tiers,<br>45 to 162 slots',
    points: ['Copper, Iron, Gold, Diamond, Netherite and Omnium Vaults',
      'Lids open on their hinges while someone looks inside',
      'One-click <b class="hi">Sort</b>; comparators read the fill level',
      '<b class="c">Upgrade Kits</b> raise a placed vault a tier, contents kept; the Copper kit converts chests and barrels',
      'Netherite and Omnium Vaults are blast resistant'],
    art: `<div style="position:relative;height:720px">
      ${framed(region('vaults.jpg', 100, 160, 1820, 860, 900), 'position:absolute;left:40px;top:0;width:900px')}
      ${framed(gui('vault', 520), 'position:absolute;left:0;bottom:0;width:540px')}
      <div class="panel" style="position:absolute;right:0;bottom:40px;width:360px;padding:20px 24px 16px">${rivets()}
        ${VAULTS.map(([t, n]) => `<div style="display:flex;justify-content:space-between;align-items:center;padding:7px 0;border-bottom:1px solid rgba(62,72,89,.6)">
          <span class="display" style="font-size:25px;color:#fff">${t[0].toUpperCase() + t.slice(1)}</span><span class="stat" style="font-size:27px">${n} <span class="cap">slots</span></span></div>`).join('')}</div>
      ${plate('Omnium Vault, open', 'right:30px;top:24px', '', 'violet')}</div>` }),

  'card-drawers': () => card({
    kicker: 'Drawers', title: 'Bulk storage<br>you can read',
    points: ['1×1, 1×2 and 2×2 drawers show each item and count on the front',
      'The <b class="hi">Compacting Drawer</b> stores blocks, ingots and nuggets as one amount',
      'A <b class="c">Drawer Controller</b> joins every drawer within 12 blocks',
      'Iron ×2, Gold ×4, Diamond ×8 and Netherite ×16 upgrades; a Void Upgrade for overflow',
      'Lock a drawer with the Drawer Key; it keeps a ghost of its item'],
    art: `<div style="position:relative">${framed(region('drawers.jpg', 120, 0, 1680, 945, 940))}
      ${plate('Compacting', 'left:300px;bottom:70px', '', 'amber')}${plate('Controller', 'left:470px;bottom:22px')}
      ${plate('Locked', 'right:150px;bottom:60px', '', 'violet')}</div>` }),

  'card-backpacks': () => card({
    kicker: 'Backpacks', title: 'Wear it.<br>Place it.',
    points: ['Six tiers from 27 to 120 slots; leather is dyeable',
      'Wear it on your back and press <kbd>B</kbd>, or set it down as a block',
      '<b class="hi">Nine upgrades</b>: pickup, magnet, void, feeding, restock, compacting, crafting, everlasting, deposit',
      'Each upgrade gets a tab with a 9-slot filter',
      'Placed backpacks keep magnet, pickup, void and compacting running'],
    art: `<div style="position:relative;height:740px">
      ${framed(region('backpacks.jpg', 140, 100, 1640, 922, 820), 'position:absolute;right:0;top:0;width:840px')}
      ${framed(gui('backpack', 600), 'position:absolute;left:0;bottom:0;width:620px')}
      <div style="position:absolute;right:6px;bottom:20px;display:grid;grid-template-columns:repeat(3,92px);gap:10px">
        ${BP_UPGRADES.map((u) => `<div class="panel" style="padding:10px;box-shadow:inset 2px 2px 0 ${C.highlight},inset -2px -2px 0 ${C.shadow}"><img class="px" src="${art('upgrade-' + u)}" style="width:68px;display:block"></div>`).join('')}</div>
      ${plate('Worn &amp; placed', 'right:24px;top:20px', '', 'green')}</div>` }),

  'card-quantum': () => card({
    kicker: 'Late game', title: 'Quantum<br>everything',
    points: ['<b class="v">Quantum Bridges</b> join two parts of a network across any distance, even between dimensions',
      'Entangle a pair with a <b class="hi">Quantum Link Card</b>',
      '<b class="v">Singularity cells</b>: 2,147,483,648 items each',
      'The <b class="v">Quantum Assembler</b> crafts sixteen times as much per cycle',
      'The Quantum Wireless Terminal works from anywhere, in any dimension'],
    led: 'violet', backdrop: 'backdrop-late.jpg',
    art: `<div style="position:relative;height:720px">
      ${framed(region('late.jpg', 0, 60, 1920, 1020, 900), 'position:absolute;left:0;top:0;width:900px')}
      ${framed(region('quantum-assembler.jpg', 380, 140, 1160, 800, 420), 'position:absolute;right:0;bottom:0;width:440px')}
      <img src="${art('quantum_bridge')}" style="position:absolute;left:70px;bottom:-10px;width:250px;filter:drop-shadow(0 0 30px rgba(164,107,255,.5)) drop-shadow(0 16px 20px rgba(0,0,0,.8))">
      ${plate('Bridge pair, linked', 'left:300px;top:24px', '', 'violet')}${plate('Quantum Assemblers', 'right:24px;bottom:310px', '', 'violet')}</div>` }),

  'card-progression': () => {
    const col = (kick, led, title, img, items) => `<div class="panel" style="flex:1;padding:14px 14px 18px">${rivets()}
      <div class="kicker" style="font-size:18px;margin:6px 4px 10px"><span class="led ${led}"></span>${kick}</div>
      <div style="border:2px solid ${C.outline}">${img}</div>
      <div class="display" style="font-size:30px;color:#fff;margin:14px 4px 6px">${title}</div>
      <div style="font-size:18px;color:var(--dim);line-height:1.45;margin:0 4px">${items}</div></div>`;
    return card({
      kicker: 'Progression', title: 'From your first<br>chest to a<br>quantum network',
      points: ['Useful on day one with vanilla materials and no power',
        'Omnium, from amethyst, quartz and redstone, unlocks the network',
        'Nether stars and echo shards unlock the quantum tier',
        'A Storage Hub network upgrades to a Nexus Core in place'],
      art: `<div style="display:flex;gap:18px">
        ${col('Early', 'amber', 'Storage Hub', region('early.jpg', 300, 120, 1400, 900, 280), 'Inventory Cable · Storage &amp; Crafting Terminals · Copper Vault · drawers · Leather Backpack · Trash Can')}
        ${col('Mid', 'cyan', 'Nexus Core', region('base.jpg', 260, 60, 1400, 900, 280), 'Cell Drives · 4K–256K cells · buses · wireless · autocrafting · Iron–Diamond Vaults · Linked Vaults')}
        ${col('Late', 'violet', 'Quantum', region('late.jpg', 300, 120, 1400, 900, 280), '1M, 16M &amp; Singularity cells · Quantum Bridges · Quantum Assembler · Netherite &amp; Omnium Vaults')}</div>` });
  },

  'card-utilities': () => card({
    kicker: 'And the rest', title: 'Every storage<br>tool you need',
    points: ['<b class="hi">Linked Vault</b> and <b class="hi">Linked Pouch</b>: 27 shared slots on a three-colour channel, across dimensions',
      '<b class="hi">Trash Can</b> with an allow/deny filter for automation',
      '<b class="hi">Relocator</b>: pick up a full chest or machine and move it (16 uses; the Netherite one never wears out)',
      'The <b class="hi">OmniTool</b> rotates, configures and dismantles',
      'The <b class="hi">Drawer Key</b> locks drawers, one or all at once'],
    art: `<div class="panel" style="padding:34px 30px 24px">${rivets()}
      <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:26px 16px">
        ${[['linked_vault', 'Linked Vault', 'shared channel', 170], ['item-linked_pouch', 'Linked Pouch', 'opens anywhere', 170],
           ['trash_can', 'Trash Can', 'voids, filtered', 170], ['item-omnium_vault_kit', 'Vault Kits', 'a tier up, in place', 140],
           ['item-relocator', 'Relocator', '16 uses', 140], ['item-netherite_relocator', 'Netherite', 'unbreakable', 140],
           ['item-omni_tool', 'OmniTool', 'rotate · dismantle', 140], ['item-drawer_key', 'Drawer Key', 'lock · unlock', 140]]
          .map(([id, name, sub, size]) => tile(art(id), name, sub, size)).join('')}</div></div>` }),

  'card-config': () => {
    const k = (t) => `<span style="color:${C.cyan}">${t}</span>`;
    const v = (t) => `<span style="color:${C.warn}">${t}</span>`;
    const c = (t) => `<span style="color:#5d6773">${t}</span>`;
    const s = (t) => `<span style="color:#c9a8ff">${t}</span>`;
    const lines = [c('# config/omnivault-common.toml (defaults)'), s('[network]'), `${k('maxNodes')} = ${v('8192')}`, `${k('requireEnergy')} = ${v('false')}`,
      `${k('coreEnergyBuffer')} = ${v('1000000')}`, s('[cells]'), `${k('typeLimit')} = ${v('128')}`, `${k('fluidTypeLimit')} = ${v('32')}`,
      `${k('singularityTypeLimit')} = ${v('1024')}`, s('[wireless]'), `${k('coreRange')} = ${v('32')}`, `${k('transmitterRange')} = ${v('64')}`,
      `${k('rangePerUpgrade')} = ${v('48')}`, s('[buses]'), `${k('baseInterval')} = ${v('20')}`, `${k('baseItemsPerOperation')} = ${v('8')}`];
    return card({
      kicker: 'Configuration', title: 'One file,<br>every number',
      points: ['One commented <b class="hi">omnivault-common.toml</b>',
        'Every value lists its default and allowed range',
        'Out-of-range values are clamped, invalid ones fall back with a warning',
        'Sections for the network, cells, wireless, buses, vaults, drawers, backpacks and autocrafting'],
      art: `<div class="panel" style="padding:34px 40px 30px">${rivets()}<pre style="margin:0;font-family:'DejaVu Sans Mono',ui-monospace,monospace;font-size:24px;line-height:1.55;color:${C.text}">${lines.join('\n')}</pre></div>` });
  },
};

const HEADERS = {
  'header-whats-new': 'What’s New in 2.0', 'header-progression': 'Progression', 'header-network': 'The Network',
  'header-terminals': 'Terminals', 'header-autocrafting': 'Autocrafting', 'header-storage': 'Physical Storage',
  'header-drawers': 'Drawers', 'header-backpacks': 'Backpacks', 'header-quantum': 'Late Game', 'header-config': 'Configuration',
  'header-compatibility': 'Downloads', 'header-faq': 'FAQ', 'header-gallery': 'Gallery',
};
for (const [name, title] of Object.entries(HEADERS)) assets[name] = () => header(title);
assets.divider = divider;

(async () => {
  const wanted = process.argv.slice(2);
  const names = wanted.length ? wanted : Object.keys(assets);
  const browser = await chromium.launch();
  const context = await browser.newContext({ deviceScaleFactor: 1 });
  for (const name of names) {
    if (!assets[name]) {
      console.error('unknown asset', name);
      continue;
    }
    const file = path.join(__dirname, `.render-${name}.html`);
    fs.writeFileSync(file, assets[name]());
    const p = await context.newPage();
    await p.goto(url(file));
    await p.evaluate(() => document.fonts.ready);
    await p.waitForTimeout(200);
    const { width, height } = await p.evaluate(() => ({ width: document.body.scrollWidth, height: document.body.scrollHeight }));
    await p.setViewportSize({ width, height });
    const transparent = name.startsWith('header') || name === 'divider' || name === 'icon';
    await p.screenshot({ path: path.join(out, `${name}.png`), omitBackground: transparent });
    await p.close();
    fs.unlinkSync(file);
    console.log('rendered', name);
  }
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
