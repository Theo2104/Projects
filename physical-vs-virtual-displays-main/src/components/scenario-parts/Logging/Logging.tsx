import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { baseUrl } from '../../../main.tsx'


type EventData = { time: number, tags: string[], action: string };

export const Logging = () => {
	const [count, setCount] = useState(0)
	const [receivedMessages, setReceivedMessage] = useState<string[]>([]);
	const queryClient = useQueryClient();

	const add = useMutation({
		mutationFn: async (e: EventData) => {
			const res = await fetch(baseUrl + "/logs", {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
				},
				body: JSON.stringify(e),
			})
			return await res.json() as unknown as EventData[];
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['logs'] })
		},
	});

	useEffect(() => {
		window.addEventListener(
			"log",
			async (e) => {
				const data: EventData | undefined = (e as any).detail;
				if (data) {
					console.log('received log: ', data);
					setReceivedMessage(prevState => [...prevState, JSON.stringify(data)]);

					// TODO: add workspace due have a session
					await add.mutateAsync(data);
				} else {
					console.error('Error sending log, no detail defined!')
				}
			},
			false,
		);
	}, []);

	return (
		<div>
			LOG COMPONENT
			<div>state test - count: {count}</div>
			<button
				onClick={() => {
					setCount((count) => count + 1);
				}}
				onPointerDown={(e) => e.stopPropagation()}
			>
				klick!
			</button>
			<hr />
			{receivedMessages.map((msg, i) => (
				<code key={i}>{msg}</code>
			))}
		</div>
	);
}
