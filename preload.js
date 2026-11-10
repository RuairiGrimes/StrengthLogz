const { contextBridge, ipcRenderer } = require('electron');

const call = async (channel, arg) => {
  const res = await ipcRenderer.invoke(channel, arg);
  if (!res.ok) throw new Error(res.error);
  return res.data;
};

contextBridge.exposeInMainWorld('api', {
  state: () => call('state'),
  reload: () => call('reload'),
  saveWorkout: (payload) => call('saveWorkout', payload),
  deleteWorkout: (id) => call('deleteWorkout', id),
  addExercise: (ex) => call('addExercise', ex),
  connect: (cfg) => call('connect', cfg),
  disconnect: () => call('disconnect'),
  importHistory: () => call('importHistory'),
  copyLocalToSheets: (opts) => call('copyLocalToSheets', opts),
  clearAllData: () => call('clearAllData'),
  pickKeyFile: () => call('pickKeyFile'),
  openExternal: (url) => call('openExternal', url),
});

