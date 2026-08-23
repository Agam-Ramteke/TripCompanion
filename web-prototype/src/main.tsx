/**
 * Entry point. Order matters: Leaflet's own stylesheet first, then the tokens
 * every other sheet reads from, then components, then screens, then the harness —
 * so a later sheet can always override an earlier one without !important.
 */

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import 'leaflet/dist/leaflet.css'
import './styles/tokens.css'
import './styles/components.css'
import './styles/screens.css'
import './styles/lab.css'
import { Lab } from './lab/Lab'

const host = document.getElementById('root')
if (!host) throw new Error('#root is missing from index.html')

createRoot(host).render(
  <StrictMode>
    <Lab />
  </StrictMode>,
)
