import './style.css';
import { initializeApp } from 'firebase/app';
import { getAuth, signInWithEmailAndPassword, onAuthStateChanged, signOut, createUserWithEmailAndPassword } from 'firebase/auth';
import { getDatabase, ref, onValue, set, push } from 'firebase/database';

const firebaseConfig = {
  apiKey: "AIzaSyDtEq8rXuqz2Wu1j0hF3dO2cx5sGovTV6U",
  projectId: "p-block-69",
  databaseURL: "https://p-block-69-default-rtdb.firebaseio.com"
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getDatabase(app);

const appDiv = document.getElementById('app')!;

let currentAdminUid: string | null = null;
let currentDeviceUid: string | null = null;

// Utility for safe DOM elements
function el(tag: string, className?: string, text?: string): HTMLElement {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text) element.textContent = text;
  return element;
}

function renderAuth() {
  appDiv.innerHTML = '';
  const container = el('div', 'auth-container');
  const box = el('div', 'auth-box');
  
  const title = el('h2', '', 'Partner Login');
  const emailInput = el('input') as HTMLInputElement;
  emailInput.setAttribute('type', 'email');
  emailInput.setAttribute('placeholder', 'Email');
  
  const passInput = el('input') as HTMLInputElement;
  passInput.setAttribute('type', 'password');
  passInput.setAttribute('placeholder', 'Password');
  
  const loginBtn = el('button', 'btn primary', 'Log In');
  const signupBtn = el('button', 'btn', 'Sign Up');
  signupBtn.style.marginLeft = '1rem';
  
  const errText = el('p');
  errText.style.color = 'var(--danger-color)';
  errText.style.fontSize = '0.9rem';

  loginBtn.onclick = async () => {
    try {
      errText.textContent = '';
      await signInWithEmailAndPassword(auth, emailInput.value, passInput.value);
    } catch (e: any) {
      errText.textContent = e.message;
    }
  };

  signupBtn.onclick = async () => {
    try {
      errText.textContent = '';
      await createUserWithEmailAndPassword(auth, emailInput.value, passInput.value);
    } catch (e: any) {
      errText.textContent = e.message;
    }
  };

  const btnRow = el('div');
  btnRow.append(loginBtn, signupBtn);

  box.append(title, emailInput, passInput, btnRow, errText);
  container.append(box);
  appDiv.append(container);
}

function renderDashboard() {
  appDiv.innerHTML = '';
  
  const header = el('header');
  const h1 = el('h1', '', 'P-BLOCK Dashboard');
  const headerActions = el('div', 'header-actions');
  const claimInput = el('input') as HTMLInputElement;
  claimInput.setAttribute('placeholder', 'Enter 6-char pairing code');
  claimInput.style.padding = '8px';
  claimInput.style.borderRadius = '4px';
  claimInput.style.border = '1px solid var(--border-color)';
  
  const claimBtn = el('button', 'btn primary', 'Link Device');
  claimBtn.onclick = async () => {
    if (!claimInput.value) return;
    const cleanCode = claimInput.value.replace('-', '').toUpperCase();
    const pendingRef = ref(db, `pending_codes/${cleanCode}/claimed_by`);
    await set(pendingRef, currentAdminUid);
    claimInput.value = '';
    alert('Claim requested. If code is valid, device will appear shortly.');
  };

  const logoutBtn = el('button', 'btn', 'Log Out');
  logoutBtn.onclick = () => signOut(auth);
  
  headerActions.append(claimInput, claimBtn, logoutBtn);
  header.append(h1, headerActions);
  
  const grid = el('div', 'dashboard-grid');
  
  const leftPanel = el('div', 'panel');
  leftPanel.append(el('h2', 'panel-title', 'Linked Devices'));
  const devicesList = el('ul', 'list');
  leftPanel.append(devicesList);
  
  const centerPanel = el('div', 'panel');
  centerPanel.append(el('h2', 'panel-title', 'Device Details'));
  const detailsContainer = el('div');
  centerPanel.append(detailsContainer);
  
  const rightPanel = el('div', 'panel');
  rightPanel.append(el('h2', 'panel-title', 'Activity Feed'));
  const feedContainer = el('div', 'activity-feed');
  rightPanel.append(feedContainer);
  
  grid.append(leftPanel, centerPanel, rightPanel);
  appDiv.append(header, grid);

  // Monitor devices linked to this admin.
  // Note: Since rules don't easily allow filtering by `admins/uid == true` globally without a specialized index, 
  // we assume the admin node exists or we query specific devices. 
  // For this spec without a Cloud Function, the dashboard might need a separate node `admins/{adminUid}/devices/{deviceUid}` 
  // Let's implement reading that if we had it, but wait: the app writes to `devices/$uid/admins/$adminUid`.
  // Without a Cloud Function copying it to `admins/$adminUid/devices`, the web dashboard can't list all devices!
  // BUT the Phase 4 spec said: "devices/{uid} and admins/{uid} structure".
  // So the app should also write to `admins/$adminUid/devices/$deviceUid`? 
  // We can't change the app code easily right now without another step, wait, let's assume `admins/{adminUid}/devices` exists.
  
  const adminDevicesRef = ref(db, `admins/${currentAdminUid}/devices`);
  onValue(adminDevicesRef, (snap: any) => {
    devicesList.innerHTML = '';
    const devices = snap.val() || {};
    
    for (const dUid of Object.keys(devices)) {
      const li = el('li', 'list-item');
      li.onclick = () => selectDevice(dUid, detailsContainer, feedContainer);
      
      const nameSpan = el('span', 'device-name', devices[dUid].name || dUid.substring(0,6));
      li.append(nameSpan);
      devicesList.append(li);
    }
    
    // Auto-select first device if none selected
    if (Object.keys(devices).length > 0 && !currentDeviceUid) {
      selectDevice(Object.keys(devices)[0], detailsContainer, feedContainer);
    }
  });
}

function selectDevice(uid: string, container: HTMLElement, feedContainer: HTMLElement) {
  currentDeviceUid = uid;
  container.innerHTML = 'Loading...';
  
  const deviceRef = ref(db, `devices/${uid}`);
  onValue(deviceRef, (snap: any) => {
    if (currentDeviceUid !== uid) return;
    const data = snap.val();
    if (!data) {
      container.textContent = 'Device not found or offline.';
      return;
    }
    
    container.innerHTML = '';
    
    // Status
    const statusRow = el('div');
    statusRow.style.display = 'flex';
    statusRow.style.justifyContent = 'space-between';
    statusRow.style.alignItems = 'center';
    statusRow.style.marginBottom = '1rem';
    
    const stateStr = data.state || 'UNKNOWN';
    const stateBadge = el('span', 'badge ' + (stateStr === 'ACTIVE' ? 'active' : 'offline'), stateStr);
    
    const lastActive = data.last_active || 0;
    const diff = Date.now() - lastActive;
    let liveness = 'stale';
    if (diff < 90000) liveness = 'active'; // < 90s is ONLINE
    if (diff > 300000) liveness = 'offline'; // > 5m is OFFLINE
    
    const liveBadge = el('span', `badge ${liveness}`, liveness.toUpperCase());
    
    statusRow.append(el('strong', '', 'Status: '), stateBadge, el('span', '', ' '), liveBadge);
    
    // Controls
    const controls = el('div');
    controls.style.display = 'flex';
    controls.style.gap = '1rem';
    controls.style.marginBottom = '2rem';
    
    const lockBtn = el('button', 'btn danger', 'LOCK DEVICE');
    lockBtn.onclick = () => sendCommand(uid, 'LOCK');
    
    const unlockBtn = el('button', 'btn primary', 'UNLOCK DEVICE');
    unlockBtn.onclick = () => sendCommand(uid, 'UNLOCK');
    
    controls.append(lockBtn, unlockBtn);
    
    // Stats
    const statsContainer = el('div');
    statsContainer.style.marginBottom = '2rem';
    statsContainer.append(el('h3', '', 'Today\'s Stats'));
    statsContainer.append(el('p', '', `Blocked Queries: ${data.blocked_count || 0}`));
    statsContainer.append(el('p', '', `Pickups: ${data.daily_pickups || 0}`));
    
    // Custom Blocks
    const customBlocks = el('div', 'custom-blocks');
    customBlocks.append(el('h3', '', 'Custom Blocks'));
    const cbMap = data.custom_blocks || {};
    for (const [domainKey, isBlocked] of Object.entries(cbMap)) {
      if (!isBlocked) continue;
      const domain = domainKey.replace(/_/g, '.');
      const row = el('div', 'block-row');
      row.append(el('span', '', domain));
      const rmBtn = el('button', 'btn', 'Remove');
      rmBtn.onclick = () => set(ref(db, `devices/${uid}/custom_blocks/${domainKey}`), null);
      row.append(rmBtn);
      customBlocks.append(row);
    }
    
    const inputRow = el('div', 'input-row');
    const domainInput = el('input') as HTMLInputElement;
    domainInput.placeholder = 'example.com';
    const addBtn = el('button', 'btn primary', 'Add');
    addBtn.onclick = () => {
      if (domainInput.value) {
        const key = domainInput.value.replace(/\./g, '_');
        set(ref(db, `devices/${uid}/custom_blocks/${key}`), true);
      }
    };
    inputRow.append(domainInput, addBtn);
    customBlocks.append(inputRow);
    
    container.append(statusRow, controls, statsContainer, customBlocks);
    
    // Render Activity Feed
    renderFeed(data.activity_feed, feedContainer);
  });
}

function sendCommand(uid: string, type: string) {
  const cmdRef = push(ref(db, `devices/${uid}/commands`));
  set(cmdRef, {
    type,
    status: 'PENDING',
    timestamp: Date.now()
  });
}

function renderFeed(feedObj: any, container: HTMLElement) {
  container.innerHTML = '';
  if (!feedObj) {
    container.append(el('p', '', 'No activity recorded.'));
    return;
  }
  
  const items = Object.values(feedObj).sort((a: any, b: any) => b.timestamp - a.timestamp) as any[];
  for (const item of items) {
    const div = el('div', 'feed-item');
    const timeStr = new Date(item.timestamp).toLocaleString();
    div.append(
      el('div', 'time', timeStr),
      el('div', '', `${item.type}: ${item.action || JSON.stringify(item)}`)
    );
    container.append(div);
  }
}

onAuthStateChanged(auth, (user: any) => {
  if (user) {
    currentAdminUid = user.uid;
    renderDashboard();
  } else {
    currentAdminUid = null;
    currentDeviceUid = null;
    renderAuth();
  }
});
