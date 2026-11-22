// The page learns it runs as the desktop program and can bring its window forward (a click on a reminder).
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('__VINDICA_DESKTOP', {
  show: () => ipcRenderer.send('vindica:show'),
  platform: process.platform
});
