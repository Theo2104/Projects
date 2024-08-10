import {
	DefaultToolbar, DrawToolbarItem,
	EraserToolbarItem,
	NoteToolbarItem,
	SelectToolbarItem,
	TldrawUiMenuItem, useEditor,
	useIsToolSelected,
	useTools,
} from "@tldraw/tldraw"
import '@tldraw/tldraw/tldraw.css';

export const CustomToolbar = (props: any) => {
	const tools = useTools();
	const editor = useEditor();
	const isCardSelected = useIsToolSelected(tools['card'])
	return !editor.getInstanceState().isReadonly ? (
		<>
			<DefaultToolbar {...props}>
				<SelectToolbarItem />
				<DrawToolbarItem />
				<EraserToolbarItem />
				<NoteToolbarItem />
				<TldrawUiMenuItem {...tools['card']} isSelected={isCardSelected} />
			</DefaultToolbar>
			<div className="absolute left-1/2 bottom-20 flex gap-x-2 -translate-x-1/2 pointer-events-auto">
				{[{
					label: 'Add logging',
					cardType: 'log',
				}, {
					label: 'Add puzzle',
					cardType: 'puzzle',
				}, {
					label: 'Add Gridgame',
					cardType: 'gridgame',
				}, {
					label: 'Add Mindmap',
					cardType: 'mindmap',
				}, {
					label: 'Add Datavis',
					cardType: 'datavis',
				}].map(({ cardType, label }) => (
					<div key={cardType} className='cursor-pointer border rounded p-2 font-bold hover:underline bg-white' onClick={() => {
						editor.createShape({ type: "card", x: -250, y: -250, meta: { cardType }, props: { w: 500, h: 500 } });
						editor.setCamera({ x: window.innerWidth / 2, y: window.innerHeight /2 });
					}}>
						{label}
					</div>
				))}
				<div className='cursor-pointer border rounded p-2 font-bold hover:underline bg-white' onClick={() => {
					editor.setCamera({ x: window.innerWidth / 2, y: window.innerHeight /2 });
				}}>
					Center Camera
				</div>
			</div>
		</>
	) : null;
}

