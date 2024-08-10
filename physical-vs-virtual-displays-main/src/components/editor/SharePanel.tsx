import { track, useEditor } from "@tldraw/tldraw"
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { FiPlus, FiSave } from "react-icons/fi"
import { baseUrl } from "../../main.tsx"
import { WorkspaceRecord } from "../workspace-picker/workspace-picker.tsx"
import { MdDriveFileRenameOutline } from "react-icons/md"

type SceneRecord = {
	id: string,
	name: string,
	data: any,
}

export const NameEditor = track(() => {
	const editor = useEditor()
	const queryClient = useQueryClient()

	const { color, name } = editor.user.getUserPreferences()

	const pathEntries = window.location.href.split("/").slice(3)
	const workspaceId = pathEntries[1].split("?")[0]

	const [displayDialog, setDisplayDialog] = useState(false)
	const [selectedOptions, setSelectedOptions] = useState([{
		value: "none",
		label: "keine Auswahl",
	}])
	const [selectedItemId, setSelectedItemId] = useState("none")
	const [size, setSize] = useState({ width: 250, height: 250 })
	const [position, setPosition] = useState({ x: 0, y: 0 })

	const sceneQuery = useQuery({
		queryKey: ["scenes"], queryFn: async () => {
			const res = await fetch(baseUrl + "/scenes")
			return await res.json() as unknown as SceneRecord[]
		},
	})

	const workspaceSceneQuery = useQuery({
		queryKey: ["workspaces"], queryFn: async () => {
			const res = await fetch(baseUrl + "/workspaces")
			return await res.json() as unknown as WorkspaceRecord[]
		},
	})

	useEffect(() => {
		if (sceneQuery.data) {
			setSelectedOptions(sceneQuery.data.map(entry => ({
				label: entry.name,
				value: entry.id,
			})))
			if (sceneQuery.data.length > 0 && selectedItemId === "none") {

				// this logic restores the currently chosen scenario of a workspace
				const wksp = workspaceSceneQuery.data?.find(item => item.id == workspaceId)
				if (wksp && wksp.sceneId && sceneQuery.data.find(entry => entry.id === wksp.sceneId)) {
					setSelectedItemId(wksp.sceneId)
				} else {
					setSelectedItemId(sceneQuery.data[0].id)
				}

			}
		}
	}, [sceneQuery.data, workspaceSceneQuery.data])

	const setSceneIdToWorkspace = useMutation({
		mutationFn: async ({ workspaceId, sceneId }: { workspaceId: string, sceneId: string }) => {
			const res = await fetch(baseUrl + "/workspaces/" + workspaceId, {
				method: "PUT",
				headers: {
					"Content-Type": "application/json",
				},
				body: JSON.stringify({
					sceneId: sceneId,
				}),
			})
			return await res.json() as unknown as WorkspaceRecord[]
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["workspaces"] })
		},
	})

	useEffect(() => {
		const item = sceneQuery.data?.find(item => item.id == selectedItemId)
		if (item && item.data) {
			editor.store?.loadSnapshot(item.data);
			editor.updateInstanceState({ isReadonly: pathEntries[1].includes("&ro=true") });
			editor.updateInstanceState({ isFocused: true });
			editor.updateInstanceState({ canMoveCamera: !pathEntries[1].includes("&ro=true") });

			if (pathEntries[1].includes("&ro=true")) {
				editor.setCamera({ x: window.innerWidth / 2, y: window.innerHeight / 2 });
			}

			// take scene id and set to workspace
			setSceneIdToWorkspace.mutate({ sceneId: selectedItemId, workspaceId })
		}
	}, [sceneQuery.data, selectedItemId])

	const add = useMutation({
		mutationFn: async (name: string) => {
			const res = await fetch(baseUrl + "/scenes", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
				},
				body: JSON.stringify({
					name,
					data: editor.store?.getSnapshot(),
				}),
			})
			return await res.json() as unknown as SceneRecord[]
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["scenes"] })
		},
	})

	const upsertData = useMutation({
		mutationFn: async ({ id, zoom }: { id: string, zoom: number }) => {
			const res = await fetch(baseUrl + "/scenes/" + id, {
				method: "PUT",
				headers: {
					"Content-Type": "application/json",
				},
				body: JSON.stringify({
					id,
					name: selectedOptions.find(item => item.value == id)?.label,
					data: editor.store?.getSnapshot('all'),
				}),
			})
			return await res.json() as unknown as SceneRecord[]
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["scenes"] })
		},
	})

	const changeName = useMutation({
		mutationFn: async ({ id, name }: { id: string, name: string }) => {
			const res = await fetch(baseUrl + "/scenes/" + id, {
				method: "PUT",
				headers: {
					"Content-Type": "application/json",
				},
				body: JSON.stringify({
					name,
				}),
			})
			return await res.json() as unknown as SceneRecord[]
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["scenes"] })
		},
	})

	return (
		<>
			{displayDialog && (
				<div
					className='absolute z-[1000] w-screen h-screen flex items-center justify-center bg-black/50'>
					<div className='bg-white rounded w-1/5 h-1/5 p-4'>
						<select
							value={selectedItemId}
							onChange={e => setSelectedItemId(e.target.value)}
						>
							{selectedOptions.map((option, index) => (
								<option key={index} value={option.value}>{option.label}</option>
							))}
						</select>
					</div>
				</div>
			)}
			<div className='flex'>

				{!editor.getInstanceState().isReadonly ? (
					<>
						<div
							className='flex m-4 border rounded border-slate-300 pointer-events-auto gap-x-4 items-center bg-white'>

							<div className='bg-white w-[300px] flex items-center justify-between p-4'>
								<div className='flex'>
									<input
										className='w-[70px] border'
										value={position.x}
										type='number'
										onChange={(e) => setPosition(prevState => ({
											...prevState,
											x: parseInt(e.target.value),
										}))}
									/>
									x
									<input
										className='w-[70px] border'
										value={position.y}
										type='number'
										onChange={(e) => setPosition(prevState => ({
											...prevState,
											y: parseInt(e.target.value),
										}))}
									/>
								</div>
								<div className='cursor-pointer hover:underline text-center' onClick={() => {
									const shapes = editor.getSelectedShapes()
									for (const shape of shapes) {
										editor.updateShapes([
											{
												id: shape.id,
												type: shape.type,
												x: position.x,
												y: position.y,
											},
										])
									}
								}}>apply position
								</div>
								<div className='cursor-pointer hover:underline text-center' onClick={() => {
									const shapes = editor.getSelectedShapes()
									if (shapes.length) {
										setPosition({ x: shapes[0].x, y: shapes[0].y })
									}
								}}>get position
								</div>
							</div>
						</div>
						<div
							className='flex m-4 border rounded border-slate-300 pointer-events-auto gap-x-4 items-center bg-white'>

							<div className='bg-white w-[300px] flex items-center justify-between p-4'>
								<div className='flex'>
									<input
										className='w-[70px] border'
										value={size.width}
										type='number'
										onChange={(e) => setSize(prevState => ({
											...prevState,
											width: parseInt(e.target.value),
										}))}
									/>
									x
									<input
										className='w-[70px] border'
										value={size.height}
										type='number'
										onChange={(e) => setSize(prevState => ({
											...prevState,
											height: parseInt(e.target.value),
										}))}
									/>
								</div>
								<div className='cursor-pointer hover:underline text-center' onClick={() => {
									const shapes = editor.getSelectedShapes()
									for (const shape of shapes) {
										editor.updateShapes([
											{
												id: shape.id,
												type: shape.type,
												props: {
													w: size.width,
													h: size.height,
												},
											},
										])
									}
								}}>apply size
								</div>
								<div className='cursor-pointer hover:underline text-center' onClick={() => {
									const shapes = editor.getSelectedShapes()
									if (shapes.length) {
										const size = shapes[0].props as { w: number, h: number }
										setSize({ width: size.w, height: size.h })
									}
								}}>get size
								</div>
							</div>
							{/*
					<div className="cursor-pointer" onClick={() => {
						const shapes = editor.getSelectedShapes();
						alert("sizes: ", shapes.map(shape => shape.props.w).join(', '))
					}}>
						get current size
					</div>
					*/}


						</div>

						<div
							className='flex m-4 p-4 border rounded border-slate-300 pointer-events-auto gap-x-2 items-center bg-white'>
							<select
								className='w-30 border p-2'
								value={selectedItemId}
								onChange={e => setSelectedItemId(e.target.value)}
							>
								{selectedOptions.map((option, index) => (
									<option key={index} value={option.value}>{option.label}</option>
								))}
							</select>
							<div className='cursor-pointer border p-2' onClick={async () => {
								localStorage.setItem("test", JSON.stringify(editor.store?.getSnapshot('all')))
								console.log("saving")
								await upsertData.mutateAsync({
									id: selectedItemId,
									zoom: editor.getZoomLevel(),
								})

							}}>
								<FiSave />
							</div>
							<div className='cursor-pointer border p-2' onClick={async () => {
								const name = prompt("Name des Eintrags:")
								if (name) {
									await changeName.mutateAsync({ id: selectedItemId, name })
								}
							}}>
								<MdDriveFileRenameOutline />
							</div>
							<div
								className='text-center cursor-pointer border p-2'
								onClick={async () => {
									const name = prompt("Name des Eintrags:")
									if (name) {
										await add.mutateAsync(name)
									}
								}}
							>
								<FiPlus />
							</div>
						</div>

						<div
							className='flex m-4 border rounded border-slate-300 items-center pointer-events-auto bg-white'>
							<input
								type='color'
								value={color}
								onChange={(e) => {
									editor.user.updateUserPreferences({
										color: e.currentTarget.value,
									})
								}}
							/>
						</div>
					</>

				) : (
					<div className="m-2 font-bold">
						{selectedOptions.find(item => item.value === selectedItemId)?.label}
					</div>
				)}



			</div>

		</>


	)
})
