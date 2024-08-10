import { useEffect } from 'react'
import { Editor } from '@tldraw/tldraw'

type Message = {
	type: 'register',
	id: string,
} | {
	id: string,
	type: 'message',
	kind: 'pointerdata',
	mouseEvent: 'mousedown' | 'mousemove' | 'mouseup' | 'mouseclick',
	x: number,
	y: number,
}

export const WsTest = () => {
	useEffect(() => {
		const socket = new WebSocket('ws://localhost:3000');

		socket.onopen = () => {
			console.log('WebSocket connection established');
			const msg: Message = { type: 'register', id: navigator.userAgent } // todo: maybe?
			socket.send(JSON.stringify(msg));
		};

		socket.onmessage = (event) => {
			//console.log('getting', event.data)

			try {
				const message = JSON.parse(event.data) as Message;
				if (message.type === 'message') {
					//console.log(`Message from ${message.id}`);

					if (message.kind === 'pointerdata') {
						function unityToTLDraw(unityX: number, unityY: number) {
							// Skalierungsfaktor für X-Achse
							const scaleX = 720 / 1080;
							// Skalierungsfaktor für Y-Achse
							const scaleY = 480 / 720;

							// Versatz für X-Achse
							const offsetX = 0;
							// Versatz für Y-Achse
							const offsetY = 0; // 720 - 480

							// Umrechnung der Koordinaten
							const tlDrawX = unityX * scaleX + offsetX;
							const tlDrawY = unityY * scaleY + offsetY;

							return { x: tlDrawX, y: tlDrawY };
						}

						const tlDrawCoordinates = unityToTLDraw(message.x, message.y);
						const element = document.elementsFromPoint(tlDrawCoordinates.x, tlDrawCoordinates.y);
						//const element = document.querySelector(selector);
						console.log("position", tlDrawCoordinates.x, tlDrawCoordinates.y)
						console.log('element', element)
						if (!element) return;

						let pointerEventType;

						if (element[0].classList.contains("tl-background")) {
							switch (message.mouseEvent) {
								case "mouseclick":
									const clickEvent = new Event('click');
									element[0].dispatchEvent(clickEvent);
									let clickelement: HTMLElement = element[0] as HTMLElement;
									clickelement.click();
									break;
								case "mousedown":
									pointerEventType = 'pointerdown';
									break;
								case "mouseup":
									pointerEventType = 'pointerup';
									break;
								case "mousemove":
									pointerEventType = 'pointermove';
									break;
								default:
									console.log("Unknown event");
									break;
							}
						}
						else {
							switch (message.mouseEvent) {
								case "mouseclick":
									const clickEvent = new Event('click');
									element[0].dispatchEvent(clickEvent);
									let clickelement: HTMLElement = element[0] as HTMLElement;
									clickelement.click();
									break;
								case "mousedown":
									pointerEventType = 'mousedown';
									break;
								case "mouseup":
									pointerEventType = 'mouseup';
									break;
								case "mousemove":
									pointerEventType = 'mousemove';
									break;
								default:
									console.log("Unknown event");
									break;
							}
						}

						if (pointerEventType) {
							var pointerEvent = new PointerEvent(pointerEventType, {
								bubbles: true,
								cancelable: true,
								pointerId: 1,
								pointerType: 'mouse',
								clientX: tlDrawCoordinates.x,
								clientY: tlDrawCoordinates.y,
							});
							element[0].dispatchEvent(pointerEvent);
						} else {
							console.error("No valid pointer event type determined");
						}
					}

				} else {
					console.log(message);
				}
			} catch (e) {
				// ignore
				console.log('error', e);
			}
		};

		socket.onclose = () => {
			console.log('WebSocket connection closed');
		};

		socket.onerror = (error) => {
			console.error('WebSocket error:', error);
		};
	}, []);

	// Helper
	/*window.addEventListener('pointerdown', function (event) {
		console.log('Mouse coordinates: ', event.clientX, event.clientY);
	});

	window.addEventListener('pointermove', function (event) {
		console.log('Mouse coordinates: ', event.clientX, event.clientY);
	});

	window.addEventListener('pointerup', function (event) {
		console.log('Mouse coordinates: ', event.clientX, event.clientY);
	});

	window.addEventListener('click', function (event) {
		console.log('Mouse coordinates: ', event.clientX, event.clientY);
	});

	console.log('render wstest');*/

	return null;
}
