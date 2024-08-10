import { useState } from 'react'
import {
	HTMLContainer,
	TLOnResizeHandler,
	resizeBox,
	DefaultColorStyle, ShapeProps, T, TLBaseShape, TLDefaultColorStyle, BaseBoxShapeUtil,
} from '@tldraw/tldraw'
import MindMap from '../scenario-parts/MindMap/MindMap.tsx'
import MemoryGame from '../scenario-parts/memory/MemoryGame.tsx'
import GridGame from '../scenario-parts/GridGame/GridGame.tsx'
import { Logging } from '../scenario-parts/Logging/Logging.tsx'
import Puzzle from "../scenario-parts/Puzzle/puzzle.tsx"
import DataVis from '../scenario-parts/Data-visualization/DataVis.tsx'

type ICardShape = TLBaseShape<
	'card',
	{
		w: number
		h: number
		color: TLDefaultColorStyle
	}
>


export class CardShapeUtil extends BaseBoxShapeUtil<ICardShape> {
	static override type = 'card' as const
	static override props: ShapeProps<ICardShape> = {
		w: T.number,
		h: T.number,
		color: DefaultColorStyle,
	}

	getDefaultProps(): ICardShape['props'] {
		return {
			w: 300,
			h: 300,
			color: 'black',
		}
	}

	component(shape: ICardShape) {
		console.log('card data', shape.meta)

		return (
			<HTMLContainer
				id={shape.id}
				style={{
					height: shape.meta.cardType === 'datavis' ? 1500 : shape.props.h,
					width: shape.meta.cardType === 'datavis' ? 3000 : shape.props.w,
					// This is where we allow pointer events on our shape
					pointerEvents: 'all',
					backgroundColor: '#efefef',
					padding: 16,
					display: shape.meta.cardType === 'log' && this.editor.getInstanceState().isReadonly ? 'none' : undefined
				}}
			>
				<div
					className="bg-white-50 border p-4 w-full h-full"
					// hier wollen wir events selber haben und nicht vom Editor behandeln lassen -> desgegen lassen wir die mit stopPropagation nicht weitergeben
					onPointerDown={(e) => e.stopPropagation()}
				>
					{/*hier werden die card shapes als verschiedene bausteine gerendert*/}
					{/*ein working beispiel für das senden von infos an logging ist in memory*/}
					{shape.meta.cardType === 'log' ? (
						<Logging />
					) : shape.meta.cardType === 'memory' ? (
						<MemoryGame />
					) : shape.meta.cardType === 'gridgame' ? (
						<GridGame />
					) : shape.meta.cardType === 'mindmap' ? (
						<MindMap />
					) : shape.meta.cardType === 'puzzle' ? (
						<Puzzle />
					) : shape.meta.cardType === 'datavis' ? (
						<DataVis />
					) : (
						<div className="text-red-300">cardType not found!</div>
					)}

				</div>
			</HTMLContainer>
		)
	}

	indicator(shape: ICardShape) {
		return <rect width={shape.meta.cardType === 'datavis' ? 3000 : shape.props.w} height={shape.meta.cardType === 'datavis' ? 1500 : shape.props.h} />
	}

	override onResize: TLOnResizeHandler<ICardShape> = (shape, info) => {
		return resizeBox(shape, info)
	}
}
