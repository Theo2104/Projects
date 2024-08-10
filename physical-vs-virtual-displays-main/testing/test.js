const WebSocket = require('ws');

const wss = new WebSocket.Server({ port: 3000 });

wss.on('connection', (ws) => {
  console.log('Client connected');

  ws.on('message', (message) => {
    console.log(`Received message: ${message}`);
    const data = JSON.parse(message);
    handleMouseEvent(data);
  });

  ws.on('close', () => {
    console.log('Client disconnected');
  });
});

function handleMouseEvent(data) {
  switch (data.type) {
    case 'mousedown':
      console.log(`Mouse down at (${data.x}, ${data.y})`);
      break;
    case 'mousemove':
      console.log(`Mouse move at (${data.x}, ${data.y})`);
      break;
    case 'mouseup':
      console.log(`Mouse up at (${data.x}, ${data.y})`);
      break;
    default:
      console.log(`Unknown event type: ${data.type}`);
  }
}