import { BaseBoxShapeUtil, HTMLContainer, ShapeProps, T, TLBaseShape } from '@tldraw/tldraw'

type IMyInteractiveShape = TLBaseShape<
	'my-interactive-shape',
	{
		w: number
		h: number
		checked: boolean
		text: string
	}
>

export class myInteractiveShape extends BaseBoxShapeUtil<IMyInteractiveShape> {
	static override type = 'my-interactive-shape' as const
	static override props: ShapeProps<IMyInteractiveShape> = {
		w: T.number,
		h: T.number,
		checked: T.boolean,
		text: T.string,
	}

	getDefaultProps(): IMyInteractiveShape['props'] {
		return {
			w: 500,
			h: 500,
			checked: false,
			text: '',
		}
	}

	// [1]
	component(shape: IMyInteractiveShape) {
		return (
			<div>

				<HTMLContainer
					style={{
						padding: 16,
						height: shape.props.h,
						width: shape.props.w,
						// [a] This is where we allow pointer events on our shape
						pointerEvents: 'all',
						backgroundColor: '#efefef',
						overflow: 'hidden',
						zIndex: 10,
					}}
				>
					<div
						className="bg-yellow-50 m-4 border"
						// hier wollen wir events selber haben und nicht vom Editor behandeln lassen -> desgegen lassen wir die mit stopPropagation nicht weitergeben
						onPointerDown={(e) => e.stopPropagation()}
					>
					</div>
				</HTMLContainer>

			</div>
		)
	}

	// [5]
	indicator(shape: IMyInteractiveShape) {
		return <rect width={shape.props.w} height={shape.props.h} />
	}
}
