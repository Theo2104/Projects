import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { WsTest } from '../WsTest';
import { useState } from 'react'
import { baseUrl } from '../../main.tsx'
import './workspace-picker.css'

export type WorkspaceRecord = {
	id: string,
	name: string,
	sceneId?: string,
}

export const WorkspacePicker = () => {
	const queryClient = useQueryClient()
	const [isReadOnly, setIsReadOnly] = useState(false);

	const query = useQuery({
		queryKey: ['workspaces'], queryFn: async () => {
			const res = await fetch(baseUrl + "/workspaces")
			return await res.json() as unknown as WorkspaceRecord[];
		}
	});

	const add = useMutation({
		mutationFn: async () => {
			const res = await fetch(baseUrl + "/workspaces", {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
				},
				body: JSON.stringify({
					name: `workspace_${new Date().getTime()}`
				}),
			})
			return await res.json() as unknown as WorkspaceRecord[];
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['workspaces'] })
		},
	});

	const deleteM = useMutation({
		mutationFn: async (id: string) => {
			const res = await fetch(baseUrl + "/workspaces/" + id, {
				method: 'DELETE',
			});
			return await res.json() as unknown as WorkspaceRecord[];
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['workspaces'] })
		},
	})

	return (
		<div className="flex items-center justify-center w-screen h-screen flex-col">
			<WsTest />
			<div className="font-bold text-3xl">
				Bitte wählen Sie einen Workspace aus!
			</div>
			<button
				className={`custom-toggle ${isReadOnly ? 'bg-green-400' : 'bg-red-500'} text-white px-4 py-2 rounded cursor-pointer`}
				onClick={() => setIsReadOnly(prevState => !prevState)}
			>
				Toggle Read-Only (currently: {isReadOnly ? 'On' : 'Off'})
			</button>
			<div className="flex flex-col center">
				{query.isLoading ? (
					<div>Läd...</div>
				) : query.data?.length === 0 ? (
					<div className="p-6">
						Keine Einträge gefunden, bitte erstellen sie einen!
					</div>
				) : query.data?.map((item, i) => (
					<div className="border border-slate-100 rounded p-4 mt-4" key={i}>
						<button className="custom-button text-white bg-black p-2 rounded cursor-pointer">
						<a
							href={`workspace/${item.id}?name=${item.name}&ro=${isReadOnly}`}
							
						>
							{item.name}
						</a>
						</button>
						<button
							className="custom-button text-white bg-red-500 ml-6 p-2 rounded cursor-pointer"
							onClick={() => deleteM.mutate(item.id)}
						>
							Delete
						</button>
					</div>
				))}
				<div className="flex justify-center custom-button cursor-pointer plus-sign bg-black text-white" onClick={() => add.mutate()}>
					<span className="">+</span>
				</div>
			</div>
		</div>
	);
}
