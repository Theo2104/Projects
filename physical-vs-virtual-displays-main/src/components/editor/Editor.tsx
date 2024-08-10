import { useYjsStore } from '../../useYjsStore.ts';
import { Tldraw, TLEventInfo, TLUiAssetUrlOverrides, TLUiOverrides } from '@tldraw/tldraw'
import { NameEditor } from './SharePanel.tsx'
import { useCallback, useState } from 'react'
import { CustomToolbar } from './CustomToolbar.tsx'
import { CardShapeUtil } from '../shapes/CardShape.tsx'
import { myInteractiveShape } from '../shapes/my-interactive-shape-util.tsx'

const HOST_URL =
	import.meta.env.MODE === 'development'
		? 'ws://141.76.67.197:1234'
		: 'wss://demos.yjs.dev'

const customAssetUrls: TLUiAssetUrlOverrides = {
	icons: {
		'log-icon': '/record2.svg',
	},
}

const uiOverrides: TLUiOverrides = {
	tools(editor, tools) {
		// Create a tool item in the ui's context.
		tools.card = {
			id: 'card',
			icon: 'log-icon',
			label: 'Card',
			kbd: 'c',
			onSelect: () => {
				editor.setCurrentTool('card')
			},
		}
		return tools
	},
}
const customShapeUtils = [myInteractiveShape, CardShapeUtil]

export const Editor	= ({ path }: { path: string[] }) => {
	const usedRoom = path[1] || 'fallback_room';
	const store = useYjsStore({
		roomId: usedRoom,
		hostUrl: HOST_URL,
		shapeUtils: customShapeUtils,
	});

	const [events, setEvents] = useState<any[]>([])

	const handleEvent = useCallback((data: TLEventInfo) => {
		setEvents((events) => {
			const newEvents = events.slice(0, 100)
			if (
				newEvents[newEvents.length - 1] &&
				newEvents[newEvents.length - 1].type === 'pointer' &&
				data.type === 'pointer' &&
				data.target === 'canvas'
			) {
				newEvents[newEvents.length - 1] = data
			} else {
				newEvents.unshift(data)
			}
			return newEvents
		})
	}, [])

	const RoomComponent = () => (
		<div className="flex items-center justify-center pointer-events-auto gap-x-2">
			<a href='/'>
				{'<-'}
			</a>
			<span>
				{usedRoom.split('?name=')[1].split('&')[0]}
			</span>
			<span className="text-red-600 font-bold">
				{usedRoom.split('&ro=')[1] === 'false' ? 'ADMIN MODE' : ''}
			</span>
		</div>
	);

	if (!path[1].length) {
		return <div className="text-red-300">Es ist ein Fehler aufgetreten!</div>
	}

	const showEventDebug = false;

	return (
		<div style={showEventDebug ? { display: 'flex' } : undefined}>
			<div className={!showEventDebug ? 'tldraw__editor' : undefined} style={showEventDebug ? { width: '50%', height: '100vh' } : undefined}>
				<Tldraw
					onMount={(editor) => {
						editor.on('event', (event) => handleEvent(event));
						console.log(usedRoom.split('&ro=')[1]);
					}}
					autoFocus
					shapeUtils={customShapeUtils}
					overrides={uiOverrides}
					store={store}
					assetUrls={customAssetUrls}
					components={{
						SharePanel: NameEditor,
						PageMenu: RoomComponent,
						StylePanel: null,
						QuickActions: null,
						NavigationPanel: null,
						Toolbar: CustomToolbar
					}}
				/>
			</div>
			{showEventDebug && (
				<div
					style={{
						width: '50%',
						height: '100vh',
						padding: 8,
						background: '#eee',
						border: 'none',
						fontFamily: 'monospace',
						fontSize: 12,
						borderLeft: 'solid 2px #333',
						display: 'flex',
						flexDirection: 'column-reverse',
						overflow: 'auto',
						whiteSpace: 'pre-wrap',
					}}
					onCopy={(event) => event.stopPropagation()}
				>
					<div>{JSON.stringify(events, undefined, 2)}</div>
				</div>
			)}
		</div>
	);
}
