import ReactDOM from 'react-dom/client'
import './index.css'
import { App } from './App.tsx'

export const baseUrl = import.meta.env.VITE_API ?? "http://localhost:3000";

ReactDOM.createRoot(document.getElementById('root')!).render(
	<App />,

)
