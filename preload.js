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
  updateExercise: (payload) => call('updateExercise', payload),
  connect: (cfg) => call('connect', cfg),
  disconnect: () => call('disconnect'),
  copyLocalToSheets: (opts) => call('copyLocalToSheets', opts),
  clearAllData: () => call('clearAllData'),
  pickKeyFile: () => call('pickKeyFile'),
  openExternal: (url) => call('openExternal', url),

  // Programs
  saveProgram: (prog) => call('saveProgram', prog),
  createProgram: (name) => call('createProgram', name),
  deleteProgram: (id) => call('deleteProgram', id),
  duplicateProgram: (id) => call('duplicateProgram', id),
  setActiveProgramId: (id) => call('setActiveProgramId', id),

  // Goals
  saveGoal: (goal) => call('saveGoal', goal),
  deleteGoal: (id) => call('deleteGoal', id),
  toggleGoalEnabled: (id) => call('toggleGoalEnabled', id),

  // Body Weights
  addBodyWeight: (entry) => call('addBodyWeight', entry),
  deleteBodyWeight: (date) => call('deleteBodyWeight', date),

  // Draft
  saveDraft: (draft) => call('saveDraft', draft),
  clearDraft: () => call('clearDraft'),
});

