import { Tldraw } from '@tldraw/tldraw'
import '@tldraw/tldraw/tldraw.css'
import { myInteractiveShape } from './my-interactive-shape-util'

// There's a guide at the bottom of this file!

// [1]
const customShapeUtils = [myInteractiveShape]

//[2]
export default function InteractiveShapeExample() {
	return (
		<div className='tldraw__editor'>
			<Tldraw
				shapeUtils={customShapeUtils}
				onMount={(editor) => {
					editor.createShape({ type: 'my-interactive-shape', x: 100, y: 100 })
				}}
			/>
		</div>
	)
}
