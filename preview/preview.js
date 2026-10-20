const APPS = [
  { label: "YouTube", className: "yt", html: `<span class="logo-yt"><i></i>YouTube</span>`, glow: "#ff2a2a" },
  { label: "Spotify", className: "spotify", html: "Spotify", glow: "#1DB954" },
  { label: "Netflix", className: "netflix", html: "NETFLIX", glow: "#e50914" },
  { label: "Prime Video", className: "prime", html: "prime video", glow: "#00a8e1" },
  { label: "Disney+", className: "disney", html: "Disney+", glow: "#2f5bff" },
  { label: "Hulu", className: "hulu", html: "hulu", glow: "#1ce783" },
  { label: "Max", className: "max", html: "max", glow: "#2448ff" },
  { label: "Apple TV", className: "apple", html: "tv", glow: "#d0d0d0" },
  { label: "Joyn", className: "joyn", html: "joyn", glow: "#7b2bff" },
];

const PER_PAGE = 5;
let appPage = 0;
let arranging = false;
let holdTimer = 0;
let pickerIndex = 0;
let motionOn = true;

const CATALOG = [
  { label: "ARD", className: "ard", html: "ARD", glow: "#6aa4ff" },
  { label: "ZDF", className: "zdf", html: "ZDF", glow: "#e87a1e" },
  { label: "RTL+", className: "rtl", html: "RTL+", glow: "#e30613" },
  { label: "MagentaTV", className: "magenta", html: "Magenta", glow: "#e20074" },
];

const DEFAULT_GLOW = "#ff8048";
// Preview fallback when the browser won't share a location: Berlin.
const DEFAULT_LOCATION = { lat: 52.52, lon: 13.41 };

// Example data. On the TV this would come from Android's Watch Next list, where apps publish it.
const CONTINUE = [
  { title: "Stranger Things", sub: "S4 · E7 · noch 42 Min", service: "Netflix", color: "#e50914", progress: 68, poster: "p-stranger", glow: "#c0306a" },
  { title: "The Last of Us", sub: "S2 · E3 · noch 51 Min", service: "Max", color: "#4d6bff", progress: 41, poster: "p-last", glow: "#4f9a5c" },
  { title: "Dune: Part Two", sub: "Film · noch 1 Std 12 Min", service: "Prime Video", color: "#00a8e1", progress: 54, poster: "p-dune", glow: "#e0973e" },
  { title: "Veritasium", sub: "Why Machines That Bend Are Better", service: "YouTube", color: "#ff0033", progress: 33, poster: "p-yt", glow: "#7a4fd0" },
];

const INPUTS = [
  { label: "Play Store", icon: "play" },
  { label: "PS5", icon: "hdmi" },
  { label: "Switch", icon: "hdmi" },
  { label: "Receiver", icon: "hdmi" },
  { label: "App hinzufügen", icon: "plus" },
];

// Example data. On the TV this would come from Home Assistant on the home network.
// Example states. On the TV these come from Home Assistant.
// Washer is hidden while off. The robot only shows while it runs or charges.
const DEVICES = [
  { id: "washer", label: "Waschmaschine", icon: "washer", state: "off", value: "" },
  { id: "robot", label: "Saugroboter", icon: "robot", state: "charging", value: "lädt · 80 %" },
];

const ROOMS = [
  { name: "Küche", temp: "21,4°", hum: "48 %" },
  { name: "Bad", temp: "23,1°", hum: "67 %" },
  { name: "Wohnzimmer", temp: "20,8°", hum: "51 %" },
  { name: "Schlafzimmer", temp: "19,2°", hum: "54 %" },
];
let roomsOn = true;

const WASHER_STEPS = [
  { state: "off", value: "" },
  { state: "running", value: "noch 23 Min" },
  { state: "done", value: "fertig" },
];
const ROBOT_STEPS = [
  { state: "charging", value: "lädt · 80 %" },
  { state: "running", value: "saugt Wohnzimmer" },
  { state: "off", value: "" },
];

function deviceVisible(device) {
  if (device.id === "washer") return device.state !== "off";
  return device.state === "running" || device.state === "charging";
}

const ICONS = {
  washer: `<svg viewBox="0 0 24 24"><rect x="4.5" y="3" width="15" height="18" rx="2.5"/><circle cx="12" cy="13" r="4.2"/><path d="M8 6.5h.01M11 6.5h3"/></svg>`,
  robot: `<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="2.5"/><path d="M7 7.5a7 7 0 0 1 10 0"/></svg>`,
  play: `<svg viewBox="0 0 24 24"><path d="M8 6.5v11l9-5.5z"/></svg>`,
  hdmi: `<svg viewBox="0 0 24 24"><path d="M4 9h16v4.2L16.5 17h-9L4 13.2V9z"/><path d="M9 12h6"/></svg>`,
  plus: `<svg viewBox="0 0 24 24"><path d="M12 6v12M6 12h12"/></svg>`,
};

const ROWS = ["top", "apps", "continue", "inputs"];
const rows = { top: [], apps: [], continue: [], inputs: [] };
let current = null;

function greeting(hour) {
  if (hour < 5 || hour >= 22) return "Gute Nacht";
  if (hour < 11) return "Guten Morgen";
  if (hour < 18) return "Guten Tag";
  return "Guten Abend";
}

function tickClock() {
  const now = new Date();
  document.getElementById("date").textContent = now.toLocaleDateString("de-DE", {
    weekday: "long", day: "numeric", month: "long",
  });
  document.getElementById("time").textContent =
    `${now.getHours()}:${String(now.getMinutes()).padStart(2, "0")}`;
  document.getElementById("greet").textContent = greeting(now.getHours());
  paintNowPlaying();
  paintAmbientNote();
  requestAnimationFrame(tickClock);
}

let spotifyOn = true;
const TRACK_MS = 32000;
let trackStarted = performance.now();

function paintNowPlaying() {
  const box = document.getElementById("now-playing");
  box.classList.toggle("is-on", spotifyOn);
  if (!spotifyOn) return;
  const played = ((performance.now() - trackStarted) % TRACK_MS) / TRACK_MS;
  document.getElementById("np-progress").style.width = `${played * 100}%`;
}

let ambientSignature = "";
function paintAmbientNote() {
  const active = DEVICES.filter(deviceVisible);
  const signature = active.map((device) => `${device.id}${device.value}`).join("|");
  if (signature === ambientSignature) return;
  ambientSignature = signature;
  document.getElementById("ambient-note").replaceChildren(...active.map((device) => {
    const item = document.createElement("div");
    item.className = "ambient-device";
    item.innerHTML = `<span class="ambient-device-name">${device.label}</span><span class="ambient-device-value">${device.value}</span>`;
    return item;
  }));
}

function el(tag, className, html) {
  const node = document.createElement(tag);
  node.className = className;
  node.type = "button";
  node.innerHTML = html;
  return node;
}

function register(node, row) {
  rows[row].push(node);
  node.dataset.row = row;
  node.addEventListener("click", () => setFocus(node));
}

function setFocus(node) {
  if (!node) return;
  if (current) current.classList.remove("is-focused");
  current = node;
  node.classList.add("is-focused");
  node.focus({ preventScroll: true });
  moveGlow(node);
}

function moveGlow(node) {
  const stage = document.getElementById("stage").getBoundingClientRect();
  const r = node.getBoundingClientRect();
  const hex = node.dataset.glow || DEFAULT_GLOW;
  const [red, green, blue] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  const layer = document.getElementById("bg-focus").style;
  layer.setProperty("--glow", `rgba(${red}, ${green}, ${blue}, 0.3)`);
  layer.setProperty("--glow-x", `${((r.left + r.width / 2 - stage.left) / stage.width) * 100}%`);
  layer.setProperty("--glow-y", `${((r.top + r.height / 2 - stage.top) / stage.height) * 100}%`);
}

const WEATHER_ICONS = {
  sun: `<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"/></svg>`,
  moon: `<svg viewBox="0 0 24 24"><path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/></svg>`,
  cloud: `<svg viewBox="0 0 24 24"><path d="M7 18h10a4 4 0 0 0 .5-7.97A6 6 0 0 0 6 11a3.5 3.5 0 0 0 1 7z"/></svg>`,
  rain: `<svg viewBox="0 0 24 24"><path d="M7 15h10a4 4 0 0 0 .5-7.97A6 6 0 0 0 6 8a3.5 3.5 0 0 0 1 7z"/><path d="M9 18l-1 2.5M13 18l-1 2.5M17 18l-1 2.5"/></svg>`,
  snow: `<svg viewBox="0 0 24 24"><path d="M7 15h10a4 4 0 0 0 .5-7.97A6 6 0 0 0 6 8a3.5 3.5 0 0 0 1 7z"/><path d="M9 19h.01M12 21h.01M15 19h.01"/></svg>`,
  fog: `<svg viewBox="0 0 24 24"><path d="M4 9h16M6 13h12M4 17h16"/></svg>`,
  storm: `<svg viewBox="0 0 24 24"><path d="M7 15h10a4 4 0 0 0 .5-7.97A6 6 0 0 0 6 8a3.5 3.5 0 0 0 1 7z"/><path d="M12.5 15l-2 3.5h3l-2 3.5"/></svg>`,
};

// Open-Meteo weather codes: https://open-meteo.com/en/docs
function describeWeather(code, isDay) {
  if (code === 0) return [isDay ? "sun" : "moon", "Klar"];
  if (code <= 2) return [isDay ? "sun" : "moon", "Leicht bewölkt"];
  if (code === 3) return ["cloud", "Bewölkt"];
  if (code <= 48) return ["fog", "Nebel"];
  if (code <= 67) return ["rain", "Regen"];
  if (code <= 77) return ["snow", "Schnee"];
  if (code <= 82) return ["rain", "Schauer"];
  if (code <= 86) return ["snow", "Schneeschauer"];
  return ["storm", "Gewitter"];
}

function locate() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) return resolve(DEFAULT_LOCATION);
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lon: p.coords.longitude }),
      () => resolve(DEFAULT_LOCATION),
      { timeout: 4000 },
    );
  });
}

async function updateWeather() {
  try {
    const { lat, lon } = await locate();
    const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}`
      + "&current=temperature_2m,weather_code,is_day&timezone=auto";
    const { current } = await (await fetch(url)).json();
    const [icon, text] = describeWeather(current.weather_code, current.is_day === 1);
    const temp = `${Math.round(current.temperature_2m)}°`;
    for (const id of ["weather", "weather-day"]) {
      document.getElementById(id + "-icon").innerHTML = WEATHER_ICONS[icon];
      document.getElementById(id + "-temp").textContent = temp;
      document.getElementById(id + "-text").textContent = text;
      document.getElementById(id).hidden = false;
    }
  } catch {
    document.getElementById("weather").hidden = true;
    document.getElementById("weather-day").hidden = true;
  }
}

function centerX(node) {
  const r = node.getBoundingClientRect();
  return r.left + r.width / 2;
}

// Up and down land on whatever sits closest above or below, like a TV remote.
function appsOnPage() {
  return rows.apps.slice(appPage * PER_PAGE, (appPage + 1) * PER_PAGE);
}

function renderApps(focusLabel) {
  rows.apps = [];
  const tiles = APPS.map((app) => {
    const tile = el("button", "tile",
      `<div class="brand ${app.className}">${app.html}</div><div class="caption">${app.label}</div>`);
    tile.dataset.glow = app.glow;
    tile.dataset.label = app.label;
    register(tile, "apps");
    return tile;
  });
  const add = el("button", "tile tile-add",
    `<div class="brand add">${ICONS.plus}</div><div class="caption">App hinzufügen</div>`);
  add.dataset.glow = "#ffffff";
  add.addEventListener("click", () => openPicker());
  register(add, "apps");
  tiles.push(add);

  const pages = Math.ceil(tiles.length / PER_PAGE);
  document.getElementById("apps").replaceChildren(...Array.from({ length: pages }, (_, page) => {
    const wrap = document.createElement("div");
    wrap.className = "apps-page";
    wrap.replaceChildren(...tiles.slice(page * PER_PAGE, (page + 1) * PER_PAGE));
    return wrap;
  }));
  document.getElementById("page-dots").replaceChildren(...Array.from({ length: pages }, () => document.createElement("i")));
  const focus = rows.apps.find((node) => node.dataset.label === focusLabel) || rows.apps[0];
  showAppPage(Math.max(0, Math.floor(rows.apps.indexOf(focus) / PER_PAGE)));
  setFocus(focus);
  if (arranging && focus && !focus.classList.contains("tile-add")) focus.classList.add("is-moving");
}

function beginArranging(tile) {
  if (!tile || tile.classList.contains("tile-add")) return;
  arranging = true;
  document.getElementById("stage").classList.add("is-arranging");
  setFocus(tile);
  tile.classList.add("is-moving");
}

function stopArranging() {
  arranging = false;
  document.getElementById("stage").classList.remove("is-arranging");
  document.querySelectorAll(".is-moving").forEach((node) => node.classList.remove("is-moving"));
}

function shiftApp(dir) {
  const index = APPS.findIndex((app) => app.label === current.dataset.label);
  const next = index + dir;
  if (index < 0 || next < 0 || next >= APPS.length) return;
  const [app] = APPS.splice(index, 1);
  APPS.splice(next, 0, app);
  renderApps(app.label);
}

function availableApps() {
  return CATALOG.filter((app) => !APPS.some((installed) => installed.label === app.label));
}

function paintPicker() {
  [...document.querySelectorAll("#picker-list button")].forEach((button, index) => {
    button.classList.toggle("is-focused", index === pickerIndex);
  });
}

function openPicker() {
  const choices = availableApps();
  const list = document.getElementById("picker-list");
  pickerIndex = 0;
  document.getElementById("picker").hidden = false;
  if (!choices.length) {
    list.innerHTML = `<p class="picker-empty">Alle Beispiel-Apps sind schon auf dem Bildschirm.</p>`;
    return;
  }
  list.replaceChildren(...choices.map((app) => {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = app.label;
    button.addEventListener("click", () => addApp(app));
    return button;
  }));
  paintPicker();
}

function addApp(app) {
  APPS.push({ ...app });
  document.getElementById("picker").hidden = true;
  stopArranging();
  renderApps(app.label);
}

const WALLPAPERS = [
  { id: "warm", name: "Warm" },
  { id: "dusk", name: "Dämmerung" },
  { id: "forest", name: "Wald" },
  { id: "sea", name: "See" },
  { id: "night", name: "Nacht" },
  { id: "ink", name: "Tinte" },
  { id: "graphite", name: "Graphit" },
  { id: "mist", name: "Nebel" },
  { id: "stone", name: "Stein" },
  { id: "sand", name: "Sand" },
];
const WALL_FILE = { id: "file", name: "Eigenes Bild" };
let wallpaperIndex = 0;
let wallpaperUrl = "";
let wallFocus = 0;

function paintWallpaper() {
  const bg = document.querySelector(".bg");
  const current = WALLPAPERS[wallpaperIndex];
  const custom = wallpaperUrl && wallpaperIndex < 0;
  bg.dataset.wallpaper = custom ? "custom" : current.id;
  bg.style.backgroundImage = custom ? `url("${wallpaperUrl}")` : "";
  bg.style.backgroundSize = custom ? "cover" : "";
  bg.style.backgroundPosition = custom ? "center" : "";
  document.getElementById("wallpaper-name").textContent = custom ? "Eigenes Bild" : current.name;
  if (!custom) localStorage.setItem("wallpaper", current.id);
}

function wallChoices() {
  return [...WALLPAPERS, WALL_FILE];
}

function openWallMenu() {
  const choices = wallChoices();
  const currentId = wallpaperIndex < 0 ? "file" : WALLPAPERS[wallpaperIndex].id;
  wallFocus = Math.max(0, choices.findIndex((item) => item.id === currentId));
  document.getElementById("wall-grid").innerHTML = choices.map((item) => `
    <button type="button" class="wall-option" data-id="${item.id}">
      <i class="wall-swatch" data-wall="${item.id}"></i>
      <span>${item.name}</span>
    </button>
  `).join("");
  document.getElementById("wall-menu").hidden = false;
  paintWallFocus();
}

function paintWallFocus() {
  const currentId = wallpaperIndex < 0 ? "file" : WALLPAPERS[wallpaperIndex].id;
  [...document.querySelectorAll("#wall-grid button")].forEach((button, index) => {
    button.classList.toggle("is-focused", index === wallFocus);
    button.classList.toggle("is-on", button.dataset.id === currentId);
  });
}

function closeWallMenu() {
  document.getElementById("wall-menu").hidden = true;
}

function chooseWall(id) {
  if (id === "file") {
    document.getElementById("wallpaper-file").click();
    return;
  }
  wallpaperUrl = "";
  wallpaperIndex = WALLPAPERS.findIndex((item) => item.id === id);
  paintWallpaper();
  closeWallMenu();
}

function useWallpaperFile(file) {
  if (!file) return;
  if (wallpaperUrl.startsWith("blob:")) URL.revokeObjectURL(wallpaperUrl);
  wallpaperUrl = URL.createObjectURL(file);
  wallpaperIndex = -1;
  paintWallpaper();
  closeWallMenu();
}

function toggleMotion() {
  motionOn = !motionOn;
  document.getElementById("stage").classList.toggle("is-calm", !motionOn);
  document.getElementById("motion-toggle").textContent = motionOn ? "Animationen an" : "Animationen aus";
}

let settingsOpen = false;
let settingsIndex = 0;
let sleepTimer = 0;
let sleepMinutes = 0;

function settingsItems() {
  return [...document.querySelectorAll("#settings button, #settings input")];
}

function paintSettingsFocus() {
  settingsItems().forEach((item, index) => item.classList.toggle("is-focused", index === settingsIndex));
}

function openSettings() {
  settingsOpen = true;
  settingsIndex = 0;
  document.getElementById("settings").hidden = false;
  paintSettingsFocus();
}

function closeSettings() {
  settingsOpen = false;
  document.getElementById("settings").hidden = true;
}

function paintSleep() {
  document.querySelectorAll("#sleep-row button").forEach((button) => {
    button.classList.toggle("is-on", Number(button.dataset.minutes) === sleepMinutes);
  });
}

function armSleep(minutes) {
  clearTimeout(sleepTimer);
  sleepMinutes = minutes;
  paintSleep();
  if (!minutes) return;
  sleepTimer = setTimeout(() => {
    closeSettings();
    clearTimeout(idleTimer);
    document.getElementById("power-off").hidden = false;
  }, minutes * 60 * 1000);
}

function applyCustomSleep() {
  const value = Number(document.getElementById("sleep-custom").value);
  if (!value || value < 1) return;
  armSleep(Math.min(480, Math.round(value)));
}

let memoryFree = 595;

function showNote(title, text) {
  document.querySelector(".toast-app").textContent = "Einstellungen";
  document.querySelector(".toast-title").textContent = title;
  document.querySelector(".toast-text").textContent = text;
  const toast = document.getElementById("toast");
  toast.classList.add("is-shown");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove("is-shown"), 4000);
}

function freeMemory() {
  memoryFree = Math.min(900, memoryFree + 48);
  document.getElementById("memory-free").textContent = `${memoryFree} MB frei`;
  showNote("Speicher freigeben", "Beispielwert. Auf dem Fernseher wird der Cache geleert.");
}

function openAndroidSettings() {
  closeSettings();
  showNote("Android-Einstellungen", "Auf dem Fernseher öffnet das die Systemeinstellungen.");
}

function closePicker() {
  document.getElementById("picker").hidden = true;
}

function showAppPage(page) {
  appPage = page;
  document.getElementById("apps").style.transform = `translateX(${-page * 100}%)`;
  document.querySelectorAll(".page-dots i").forEach((dot, i) => dot.classList.toggle("is-on", i === page));
}

function move(dx, dy) {
  const row = rows[current.dataset.row];
  if (dx) {
    if (current.dataset.row === "apps") {
      const i = row.indexOf(current) + dx;
      if (i < 0 || i >= row.length) return;
      const nextPage = Math.floor(i / PER_PAGE);
      if (nextPage !== appPage) showAppPage(nextPage);
      setFocus(row[i]);
      return;
    }
    const i = row.indexOf(current) + dx;
    if (i >= 0 && i < row.length) setFocus(row[i]);
    return;
  }
  const nextName = ROWS[ROWS.indexOf(current.dataset.row) + dy];
  const next = nextName === "apps" ? appsOnPage() : rows[nextName];
  if (!next || !next.length) return;
  const x = centerX(current);
  setFocus(next.reduce((a, b) => (Math.abs(centerX(b) - x) < Math.abs(centerX(a) - x) ? b : a)));
}

function build() {
  document.querySelectorAll(".icon-btn").forEach((b) => register(b, "top"));
  renderApps(APPS[0].label);

  const cards = CONTINUE.map((c) => el("button", "card", `
    <div class="card-art ${c.poster}">
      <span class="badge"><i style="background:${c.color}"></i>${c.service}</span>
      <div class="card-title">${c.title}</div>
      <div class="progress"><i style="width:${c.progress}%"></i></div>
    </div>
    <div class="card-sub">${c.sub}</div>`));
  cards.forEach((n, i) => { n.dataset.glow = CONTINUE[i].glow; register(n, "continue"); });
  document.getElementById("continue").replaceChildren(...cards);

  const chips = INPUTS.map((c) => el("button", "chip", `${ICONS[c.icon]}<span>${c.label}</span>`));
  chips.forEach((n) => register(n, "inputs"));
  document.getElementById("inputs").replaceChildren(...chips);

  renderClimate();
  renderDevices();
  document.getElementById("toast-icon").innerHTML = ICONS.washer;

  setFocus(rows.apps[0]);
  const stage = document.getElementById("stage");
  if (motionOn) {
    stage.classList.add("is-arriving");
    setTimeout(() => stage.classList.remove("is-arriving"), 1000);
  }
}

const QUIET_FROM = 22;
const IDLE_MS = 12000;
let forcedLate = false;
let lateDismissed = false;
let nightFocus = 0;
let idleTimer;
let fadeTimer;
let arriveTimer;

function moveClock(next) {
  if (!motionOn) {
    next();
    return;
  }
  const time = document.getElementById("time");
  const first = time.getBoundingClientRect();
  next();
  const last = time.getBoundingClientRect();
  const dx = first.left + first.width / 2 - (last.left + last.width / 2);
  const dy = first.top + first.height / 2 - (last.top + last.height / 2);
  const scale = first.width / Math.max(last.width, 1);
  time.style.transition = "none";
  time.style.transformOrigin = "center center";
  time.style.transform = `translate(${dx}px, ${dy}px) scale(${scale})`;
  time.getBoundingClientRect();
  requestAnimationFrame(() => {
    time.style.transition = "transform 1.05s cubic-bezier(0.22, 1, 0.36, 1)";
    time.style.transform = "";
  });
}

function enterAmbient() {
  const stage = document.getElementById("stage");
  if (stage.classList.contains("is-ambient") || stage.classList.contains("is-fading")) return;
  if (!document.getElementById("night").hidden) return;
  document.getElementById("toast").classList.remove("is-shown");
  if (!motionOn) {
    stage.classList.add("is-ambient");
    return;
  }
  stage.classList.add("is-fading");
  fadeTimer = setTimeout(() => {
    moveClock(() => stage.classList.add("is-ambient"));
  }, 460);
}

function leaveAmbient() {
  const stage = document.getElementById("stage");
  moveClock(() => {
    stage.classList.remove("is-ambient");
    stage.classList.add("is-fading");
  });
  requestAnimationFrame(() => {
    stage.classList.remove("is-fading");
    if (!motionOn) return;
    stage.classList.add("is-arriving");
    clearTimeout(arriveTimer);
    arriveTimer = setTimeout(() => stage.classList.remove("is-arriving"), 1000);
  });
}

function isQuietHours() {
  const hour = new Date().getHours();
  return forcedLate || hour >= QUIET_FROM || hour < 6;
}

function nightButtons() {
  return [document.getElementById("night-yes"), document.getElementById("night-no")];
}

function setNightFocus(index) {
  nightFocus = index;
  nightButtons().forEach((button, i) => button.classList.toggle("is-focused", i === index));
}

function openNight() {
  const night = document.getElementById("night");
  if (!night.hidden) return;
  night.hidden = false;
  setNightFocus(0);
}

function closeNight() {
  document.getElementById("night").hidden = true;
}

function considerNight() {
  const stage = document.getElementById("stage");
  if (stage.classList.contains("is-ambient") || !document.getElementById("power-off").hidden) return;
  if (isQuietHours() && !lateDismissed) openNight();
  else closeNight();
}

function wake() {
  clearTimeout(idleTimer);
  clearTimeout(fadeTimer);
  const stage = document.getElementById("stage");
  const wasAmbient = stage.classList.contains("is-ambient");
  if (wasAmbient) leaveAmbient();
  else stage.classList.remove("is-fading");
  if (!document.getElementById("power-off").hidden || !document.getElementById("night").hidden) return;
  idleTimer = setTimeout(enterAmbient, IDLE_MS);
  if (wasAmbient) considerNight();
}

function chooseNight(turnOff) {
  closeNight();
  lateDismissed = true;
  if (turnOff) {
    clearTimeout(idleTimer);
    document.getElementById("power-off").hidden = false;
    document.getElementById("stage").classList.remove("is-ambient");
  } else {
    wake();
  }
}

document.addEventListener("keydown", (e) => {
  const stage = document.getElementById("stage");
  if (!document.getElementById("power-off").hidden) {
    document.getElementById("power-off").hidden = true;
    lateDismissed = true;
    wake();
    return;
  }
  if (!document.getElementById("wall-menu").hidden) {
    e.preventDefault();
    const choices = wallChoices();
    const cols = 4;
    if (e.key === "Escape") closeWallMenu();
    else if (e.key === "ArrowRight") wallFocus = Math.min(choices.length - 1, wallFocus + 1);
    else if (e.key === "ArrowLeft") wallFocus = Math.max(0, wallFocus - 1);
    else if (e.key === "ArrowDown") wallFocus = Math.min(choices.length - 1, wallFocus + cols);
    else if (e.key === "ArrowUp") wallFocus = Math.max(0, wallFocus - cols);
    else if (e.key === "Enter") chooseWall(choices[wallFocus].id);
    paintWallFocus();
    return;
  }
  if (stage.classList.contains("is-ambient")) {
    wake();
    return;
  }
  if (!document.getElementById("picker").hidden) {
    e.preventDefault();
    const choices = availableApps();
    if (e.key === "Escape") closePicker();
    else if (e.key === "ArrowDown") {
      pickerIndex = Math.min(choices.length - 1, pickerIndex + 1);
      paintPicker();
    } else if (e.key === "ArrowUp") {
      pickerIndex = Math.max(0, pickerIndex - 1);
      paintPicker();
    } else if (e.key === "Enter" && choices[pickerIndex]) addApp(choices[pickerIndex]);
    return;
  }
  if (!document.getElementById("night").hidden) {
    e.preventDefault();
    if (e.key === "ArrowRight" || e.key === "ArrowLeft") setNightFocus(nightFocus === 0 ? 1 : 0);
    else if (e.key === "Enter") chooseNight(nightFocus === 0);
    else if (e.key === "Escape") chooseNight(false);
    return;
  }
  wake();
  if (settingsOpen) {
    const items = settingsItems();
    const item = items[settingsIndex];
    if (item && item.id === "sleep-custom" && (e.key.length === 1 || e.key === "Backspace")) return;
    e.preventDefault();
    if (e.key === "Escape") closeSettings();
    else if (e.key === "ArrowDown") {
      settingsIndex = Math.min(items.length - 1, settingsIndex + 1);
      paintSettingsFocus();
    } else if (e.key === "ArrowUp") {
      settingsIndex = Math.max(0, settingsIndex - 1);
      paintSettingsFocus();
    } else if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
      if (item && item.id === "brightness") {
        const step = e.key === "ArrowRight" ? 5 : -5;
        item.value = String(Math.min(100, Math.max(25, Number(item.value) + step)));
        item.dispatchEvent(new Event("input", { bubbles: true }));
      }
    } else if (e.key === "Enter" && item) {
      if (item.id === "wallpaper-btn") openWallMenu();
      else if (item.id === "motion-toggle") toggleMotion();
      else if (item.dataset.minutes != null) armSleep(Number(item.dataset.minutes));
      else if (item.id === "sleep-custom") applyCustomSleep();
      else if (item.id === "memory-btn") freeMemory();
      else if (item.id === "android-settings") openAndroidSettings();
    }
    return;
  }
  if (e.key === "Enter" && current && current.id === "settings-btn") {
    if (!e.repeat) openSettings();
    return;
  }
  if (e.key === "Enter" && current && current.dataset.row === "apps") {
    if (current.classList.contains("tile-add")) {
      if (!e.repeat) openPicker();
      return;
    }
    if (arranging) {
      if (!e.repeat) stopArranging();
      return;
    }
    if (!e.repeat) holdTimer = setTimeout(() => beginArranging(current), 520);
    return;
  }
  const keys = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
  if (keys[e.key]) {
    e.preventDefault();
    if (arranging && (e.key === "ArrowLeft" || e.key === "ArrowRight")) {
      shiftApp(e.key === "ArrowLeft" ? -1 : 1);
      return;
    }
    if (arranging) stopArranging();
    move(...keys[e.key]);
  } else if (e.key === "Escape") {
    if (arranging) stopArranging();
    else setFocus(rows.apps[0]);
  } else if (e.key === "b" || e.key === "B") {
    forcedLate = !forcedLate;
    lateDismissed = false;
    considerNight();
  } else if (e.key === "w" || e.key === "W" || e.key === "n" || e.key === "N") {
    stepDevice("washer", WASHER_STEPS);
  } else if (e.key === "r" || e.key === "R") {
    stepDevice("robot", ROBOT_STEPS);
  } else if (e.key === "t" || e.key === "T") {
    roomsOn = !roomsOn;
    renderClimate();
  } else if (e.key === "f" || e.key === "F") {
    if (document.fullscreenElement) document.exitFullscreen();
    else document.documentElement.requestFullscreen();
  } else if (e.key === "s" || e.key === "S") {
    spotifyOn = !spotifyOn;
    if (spotifyOn) trackStarted = performance.now();
    paintNowPlaying();
  }
});

document.addEventListener("keyup", (e) => {
  if (e.key === "Enter") clearTimeout(holdTimer);
});

let press = null;
document.getElementById("apps").addEventListener("pointerdown", (event) => {
  const tile = event.target.closest(".tile");
  if (!tile || tile.classList.contains("tile-add")) return;
  press = { x: event.clientX, y: event.clientY, dragging: false };
  clearTimeout(holdTimer);
  holdTimer = setTimeout(() => {
    press.dragging = true;
    beginArranging(tile);
  }, 520);
});
document.addEventListener("pointermove", (event) => {
  if (!press) return;
  if (!press.dragging && Math.hypot(event.clientX - press.x, event.clientY - press.y) > 12) clearTimeout(holdTimer);
  if (!press.dragging || !arranging || !current) return;
  const hit = document.elementFromPoint(event.clientX, event.clientY)?.closest(".tile");
  if (!hit || hit.classList.contains("tile-add") || hit.dataset.label === current.dataset.label) return;
  const from = APPS.findIndex((app) => app.label === current.dataset.label);
  const to = APPS.findIndex((app) => app.label === hit.dataset.label);
  if (from < 0 || to < 0) return;
  const [app] = APPS.splice(from, 1);
  APPS.splice(to, 0, app);
  renderApps(app.label);
});
document.addEventListener("pointerup", () => {
  clearTimeout(holdTimer);
  if (press?.dragging) stopArranging();
  press = null;
});

document.getElementById("stage").addEventListener("pointerdown", () => {
  if (document.getElementById("stage").classList.contains("is-ambient")) wake();
});

function renderClimate() {
  const box = document.getElementById("climate");
  box.hidden = !roomsOn;
  if (!roomsOn || box.childElementCount) return;
  box.replaceChildren(...ROOMS.map((room) => {
    const node = document.createElement("div");
    node.className = "room";
    node.innerHTML = `<span class="room-name">${room.name}</span><span class="room-temp">${room.temp}</span><span class="room-hum">${room.hum}</span>`;
    return node;
  }));
}

function stepDevice(id, steps) {
  const device = DEVICES.find((item) => item.id === id);
  const index = steps.findIndex((step) => step.state === device.state);
  const next = steps[(index + 1) % steps.length];
  device.state = next.state;
  device.value = next.value;
  renderDevices();
  const toast = document.getElementById("toast");
  if (id === "washer" && device.state === "done") {
    document.querySelector(".toast-app").textContent = "Home Assistant · jetzt";
    document.querySelector(".toast-title").textContent = "Waschmaschine fertig";
    document.querySelector(".toast-text").textContent = "Die Wäsche kann raus.";
    toast.classList.add("is-shown");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove("is-shown"), 7000);
  } else if (id === "washer") {
    toast.classList.remove("is-shown");
  }
}

function renderDevices() {
  rows.inputs = rows.inputs.filter((node) => !node.classList.contains("status"));
  const nodes = DEVICES.filter(deviceVisible).map((device) => {
    const node = el("button", `chip status${device.state === "done" ? " is-done" : ""}`,
      `${ICONS[device.icon]}<span>${device.label}</span><span class="status-value">${device.value}</span>`);
    node.dataset.id = device.id;
    register(node, "inputs");
    return node;
  });
  document.getElementById("home").replaceChildren(...nodes);
  if (current && !current.isConnected) setFocus(rows.apps[0]);
}

let toastTimer;

const savedWallpaper = localStorage.getItem("wallpaper");
const savedWallpaperIndex = WALLPAPERS.findIndex((item) => item.id === savedWallpaper);
if (savedWallpaperIndex >= 0) wallpaperIndex = savedWallpaperIndex;
paintWallpaper();

requestAnimationFrame(tickClock);
build();
updateWeather();
setInterval(updateWeather, 15 * 60 * 1000);
document.getElementById("settings-btn").addEventListener("click", () => (settingsOpen ? closeSettings() : openSettings()));
document.getElementById("wallpaper-btn").addEventListener("click", openWallMenu);
document.getElementById("wall-menu").addEventListener("click", (event) => {
  const option = event.target.closest(".wall-option");
  if (option) chooseWall(option.dataset.id);
  else if (event.target.id === "wall-menu") closeWallMenu();
});
document.getElementById("wallpaper-file").addEventListener("change", (event) => {
  useWallpaperFile(event.target.files && event.target.files[0]);
  event.target.value = "";
});
document.getElementById("motion-toggle").addEventListener("click", toggleMotion);
document.getElementById("brightness").addEventListener("input", (event) => {
  document.querySelector(".screen").style.setProperty("--screen-bright", String(event.target.value / 100));
});
document.getElementById("sleep-row").addEventListener("click", (event) => {
  const button = event.target.closest("button");
  if (button) armSleep(Number(button.dataset.minutes));
});
document.getElementById("sleep-custom").addEventListener("change", applyCustomSleep);
document.getElementById("memory-btn").addEventListener("click", freeMemory);
document.getElementById("android-settings").addEventListener("click", openAndroidSettings);
document.addEventListener("pointerdown", (event) => {
  if (!settingsOpen) return;
  if (event.target.closest("#settings, #settings-btn")) return;
  closeSettings();
});
document.getElementById("night-yes").addEventListener("click", () => chooseNight(true));
document.getElementById("night-no").addEventListener("click", () => chooseNight(false));
wake();
considerNight();
setInterval(considerNight, 60 * 1000);
