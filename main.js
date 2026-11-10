const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const { DataService } = require('./src/data-service');

let win;
const data = new DataService(app.getPath('userData'));

function createWindow() {
  win = new BrowserWindow({
    width: 1280,
    height: 860,
    minWidth: 960,
    minHeight: 640,
    backgroundColor: '#0f1115',
    title: 'StrengthLogz',
    icon: path.join(__dirname, 'assets', 'icon.ico'),
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

// Wrap handlers so errors reach the renderer as { error } instead of rejected promises with noisy stacks.
function handle(channel, fn) {
  ipcMain.handle(channel, async (_e, arg) => {
    try {
      return { ok: true, data: await fn(arg) };
    } catch (err) {
      console.error(`[${channel}]`, err);
      return { ok: false, error: err.message || String(err) };
    }
  });
}

handle('state', () => data.state());
handle('reload', async () => { await data.reload(); return data.state(); });
handle('saveWorkout', (payload) => data.saveWorkout(payload));
handle('deleteWorkout', (id) => data.deleteWorkout(id));
handle('addExercise', (ex) => data.addExercise(ex));
handle('updateExercise', (payload) => data.updateExercise(payload));
handle('connect', (cfg) => data.connect(cfg));
handle('disconnect', () => data.disconnect());
handle('copyLocalToSheets', (opts) => data.copyLocalToSheets(opts));
handle('clearAllData', () => data.clearAllData());

// Programs
handle('saveProgram', (prog) => data.saveProgram(prog));
handle('createProgram', (name) => data.createProgram(name));
handle('deleteProgram', (id) => data.deleteProgram(id));
handle('duplicateProgram', (id) => data.duplicateProgram(id));
handle('setActiveProgramId', (id) => data.setActiveProgramId(id));

// Goals
handle('saveGoal', (goal) => data.saveGoal(goal));
handle('deleteGoal', (id) => data.deleteGoal(id));
handle('toggleGoalEnabled', (id) => data.toggleGoalEnabled(id));

// Body Weights
handle('addBodyWeight', (entry) => data.addBodyWeight(entry));
handle('deleteBodyWeight', (date) => data.deleteBodyWeight(date));

// Draft
handle('saveDraft', (draft) => data.saveDraft(draft));
handle('clearDraft', () => data.clearDraft());

handle('pickKeyFile', async () => {
  const res = await dialog.showOpenDialog(win, {
    title: 'Select Google service account key (JSON)',
    filters: [{ name: 'JSON', extensions: ['json'] }],
    properties: ['openFile'],
  });
  return res.canceled ? null : res.filePaths[0];
});
handle('openExternal', (url) => shell.openExternal(url));

app.whenReady().then(async () => {
  await data.init();
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });

