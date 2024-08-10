import '@tldraw/tldraw/tldraw.css'
import { Editor } from './components/editor/Editor.tsx'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { WorkspacePicker } from './components/workspace-picker/workspace-picker.tsx'
import InteractiveShapeExample from './components/shapes/shape.tsx'
import './Global.css'

const queryClient = new QueryClient()

export const App = () => {

	// -----------------------------------------------------------------------------------------------
	// routing handler

	const pathEntries = window.location.href.split('/').slice(3);
	const routeMapping = (path: string[]) => [
		{
			route: 'test',
			length: 1,
			element: <InteractiveShapeExample />,
		},
		{
			route: 'workspace',
			length: 2, // /workspace/myworkspace
			element: <Editor path={path} />,
		},
	];

	const foundRouteEntry = routeMapping(pathEntries).find(item =>
		pathEntries.includes(item.route) && pathEntries.length === item.length)

	return (
		<QueryClientProvider client={queryClient}>
			{!pathEntries[0].length || !foundRouteEntry ? (
				<WorkspacePicker />
			) : foundRouteEntry.element}
		</QueryClientProvider>
	);
}


