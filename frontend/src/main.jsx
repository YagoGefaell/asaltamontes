// src/main.jsx
import React from 'react';
import ReactDOM from 'react-dom/client';

import App from './app/App';
import { Providers } from './app/providers';
import { storage } from './utils/helpers.js';

import './styles/global.css';

// Clean any potentially stale localStorage data on app start
storage.clearAppData();

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <Providers>
      <App />
    </Providers>
  </React.StrictMode>
);
