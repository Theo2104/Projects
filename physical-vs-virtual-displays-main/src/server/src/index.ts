import express from 'express';
import cors from 'cors';
import { JsonDB, Config } from 'node-json-db';
import { v4 as uuidV4 } from 'uuid';
import * as Websocket from 'ws';
import expressWs from 'express-ws';

// setup express
const { app } = expressWs(express());
app.use(express.json());
app.use(cors())

const port = 3000

const clientStore = new Map<Websocket.WebSocket, string>

// setup json db
const normalDb = new JsonDB(new Config("database", true, true, '/'));
const logDb = new JsonDB(new Config("logs", true, true, '/'));
const db = (table: string) => table === 'logs' ? logDb : normalDb;

// -----------------------------------------------------------------------------------------------
// helpers

const sendList = async (res: any, table: string) => {
	try {
		const data = await db(table).getObjectDefault(`/${table}`, []);
		res.send(data)
	} catch(error) {
		console.error(error);
	}
}

// -----------------------------------------------------------------------------------------------
// routes

app.ws('/', (ws) => {
	ws.on('message', (message) => {
		try {
			const data = JSON.parse(message.toString());
			console.log(data)

			if (data.type === 'register') {
				const clientId = data.id;
				clientStore.set(ws, clientId);
				console.log(`client registered with id: ${clientId}`);

			} else if (data.type === 'message') {
				for (const [client, clientId] of clientStore) {
					client.send(JSON.stringify(data));
				}
				for (const [partner, partnerId] of clientStore) {
					//just for testing
					//partner.send(JSON.stringify(data));

					// broadcast to all that have same id as sent in message
					if (data.id === partnerId && partner !== ws) {
						console.log('sending to partner');
						partner.send(JSON.stringify(data));
					}
				}

			}
		} catch (error) {
			console.log('Error!');
		}
	});

	ws.on('close', () => {
		console.log('Client disconnected');
		clientStore.delete(ws);
	});
})

app.get('/:table', async (req, res) => {
	const table = req.params.table;
	if (table)
		await sendList(res, table);
});

app.delete('/:table/:id', async (req, res) => {
	const table = req.params.table;
	const idToDel = req.params.id;
	if (table && idToDel) {
		const idx = await db(table).getIndex(`/${table}`, idToDel, 'id');
		if (idx !== -1) {
			await db(table).delete(`/${table}[${idx}]`)
			await sendList(res, table);
		} else {
			res.status(400).send("Item not found");
		}
	}
});

app.post('/:table', async (req, res) => {
	const table = req.params.table;
	const dataToInsert = req.body;

	if (table && dataToInsert) {
		await db(table).push(`/${table}[]`, {
			...dataToInsert,
			id: uuidV4(),
		}, true);

		await sendList(res, table);
	}

});

app.put('/:table/:id', async (req, res) => {
	const table = req.params.table;
	const idToDel = req.params.id;
	const dataToInsert = req.body;

	if (table && idToDel) {
		const idx = await db(table).getIndex(`/${table}`, idToDel, 'id');
		if (idx !== -1) {
			await db(table).push(`/${table}[${idx}]`, dataToInsert, false)

			if ('data' in dataToInsert) {
				await db(table).push(`/${table}[${idx}]/data`, dataToInsert.data, true)
			}

			await sendList(res, table);
		} else {
			res.status(400).send("Item not found");
		}
	}

});

app.listen(port, () => {
	console.log(`Persistence & Logging server listening on port ${port}`)
})
