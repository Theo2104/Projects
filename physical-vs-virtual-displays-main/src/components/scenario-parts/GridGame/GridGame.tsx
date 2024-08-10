import { useState, useEffect, useRef } from 'react';
import './GridGame.css';
import { v4 as uuidv4 } from 'uuid';

const GridGame = () => {
  const [activeCell, setActiveCell] = useState<number | null>(null);
  const [activeOrangeCell, setActiveOrangeCell] = useState<number | null>(null);
  const [gameOver, setGameOver] = useState<boolean>(false);
  const [score, setScore] = useState<number>(0);
  const [missed, setMissed] = useState<number>(0);
  const [wrong, setWrong] = useState<number>(0);
  const [isCellLit, setIsCellLit] = useState<boolean>(false);
  const [time, setTime] = useState<number>(0);
  const [timerOn, setTimerOn] = useState<boolean>(false);
  const [gameStarted, setGameStarted] = useState<boolean>(false);
  const [paused, setPaused] = useState<boolean>(false);
  const [showStats, setShowStats] = useState<boolean>(false);
  const [distractionActive, setDistractionActive] = useState<boolean>(false);
  const [wrongCell, setWrongCell] = useState<number | null>(null);
  const [randomId, setRandomID] = useState<string | null>(null);

  const maxTime = 30;
  const gridGameHeaderRef = useRef<HTMLDivElement>(null);
  const gridGameContainerRef = useRef<HTMLDivElement>(null);
  const gameContainerRef = useRef<HTMLDivElement>(null);

  const generateRandomId = () => {
    return uuidv4();
  };

  // Log event
  const logEvent = (action: string, index: number | null = null) => {
    const event = new CustomEvent('log', {
      detail: {
        time: time,
        tags: [action],
        action: `${action} at ${index !== null ? `cell ${index}` : 'no cell'}`,
      },
    });
    window.dispatchEvent(event);
  };

  const startGame = () => {
    setGameStarted(true);
    setTimerOn(true);
    logEvent("Gridgame with ID" + randomId + "has started", null)
  };

  const activateRandomCell = () => {
    if (!gameOver && !paused && !distractionActive) {
      const randomIndex = Math.floor(Math.random() * 50);
      if (!isCellLit) {
        const randomOrangeIndex = Math.random() < 0.5 ? Math.floor(Math.random() * 50) : null;
        setActiveCell(randomIndex);
        setActiveOrangeCell(randomOrangeIndex !== randomIndex ? randomOrangeIndex : null);
      } else {
        setActiveCell(randomIndex);
        setActiveOrangeCell(null);
        setMissed(prevMissed => {
          logEvent('missed', activeCell);
          return prevMissed + 1;
        });
      }
      setIsCellLit(true);
    }
  };

  useEffect(() => {
    setRandomID(generateRandomId());
  }, []);

  // Styling adjustments
  useEffect(() => {
    if (gameStarted) {
      if (gameContainerRef.current) {
        console.log(gameContainerRef.current.parentElement, gridGameContainerRef.current, gridGameHeaderRef.current);
        if (gameContainerRef.current.parentElement && gridGameContainerRef.current && gridGameHeaderRef.current) {
          let parentRect = gameContainerRef.current.parentElement.getBoundingClientRect();
          gameContainerRef.current.style.width = `${parentRect.width}px`;
          gameContainerRef.current.style.height = `${parentRect.height}px`;

          let headerRect = gridGameHeaderRef.current.getBoundingClientRect();
          gridGameContainerRef.current.style.width = `${parentRect.width - 32}px`;
          gridGameContainerRef.current.style.height = `${parentRect.height - headerRect.height - 32}px`;
        }
      }
      if (gameContainerRef.current && gridGameContainerRef.current) {
        const { width, height } = gridGameContainerRef.current.getBoundingClientRect();
        const cellWidth = (width - 22) / 10;
        const cellHeight = (height - 12) / 5;
        console.log(width, height);
        gridGameContainerRef.current.style.setProperty('--grid-columns', '10');
        gridGameContainerRef.current.style.setProperty('--grid-rows', '5');
        gridGameContainerRef.current.style.setProperty('--cell-width', `${cellWidth}px`);
        gridGameContainerRef.current.style.setProperty('--cell-height', `${cellHeight}px`);
      }
    }
  }, [gameStarted]);

  // set interval for activating random cell
  useEffect(() => {
    if (gameStarted) {
      const interval = setInterval(activateRandomCell, 2000);
      return () => clearInterval(interval);
    }
  }, [gameStarted, gameOver, isCellLit, paused, distractionActive]);

  useEffect(() => {
    let timer: number | null = null;
    if (timerOn && !paused) {
      timer = window.setInterval(() => {
        setTime(prevTime => {
          const newTime = prevTime + 1;
          if (newTime >= maxTime) {
            setGameOver(true);
            setTimerOn(false);
            setShowStats(true);
            clearInterval(timer!);
            logEvent("Gridgame with ID" + randomId + "has ended", null)
          }
          return newTime;
        });
      }, 1000);
    } else if ((!timerOn || paused) && time !== 0) {
      clearInterval(timer!);
    }
    return () => clearInterval(timer!);
  }, [timerOn, time, paused, maxTime]);

  useEffect(() => {
    const distractionInterval = setInterval(() => {
      if (!distractionActive && gameStarted && !paused && !gameOver) {
        setDistractionActive(true);
      }
    }, 5000 + Math.random() * 10000);
    return () => clearInterval(distractionInterval);
  }, [gameStarted, paused, gameOver, distractionActive]);

  const handleCorrectClick = () => {
    setScore(prevScore => {
      logEvent('score', activeCell);
      return prevScore + 1;
    });
    setActiveCell(null);
    setIsCellLit(false);
  };

  const handleCellClick = (index: number) => {
    logEvent('pointerdown', index);
    if (index === activeCell && !gameOver && !paused) {
      handleCorrectClick();
    } else if (!gameOver && !paused) {
      setWrong(prevWrong => {
        logEvent('wrong', index);
        return prevWrong + 1;
      });
      setWrongCell(index);
      setTimeout(() => setWrongCell(null), 500); // Reset the wrong cell highlight after 500ms
    }
  };

  const restartGame = () => {
    setActiveCell(null);
    setActiveOrangeCell(null);
    setGameOver(false);
    setScore(0);
    setMissed(0);
    setWrong(0);
    setWrongCell(null);
    setIsCellLit(false);
    setTime(0);
    setTimerOn(true);
    setPaused(false);
    setShowStats(false);
    logEvent("Gridgame with ID" + randomId + "has been restarted", null)
  };

  const stopGame = () => {
    setPaused(true);
    logEvent("Gridgame with ID" + randomId + "has been paused", null)
    setTimerOn(false);
  };

  const resumeGame = () => {
    setPaused(false);
    logEvent("Gridgame with ID" + randomId + "has been resumed", null)
    setTimerOn(true);
  };

  const handleDistractionPointerDown = () => {
    setDistractionActive(false);
    logEvent('distraction', null);
  };

  const renderGrid = () => {
    return Array.from({ length: 50 }).map((_, index) => (
      <div
        key={index}
        className={`gridCell ${index === activeCell ? 'active' : index === activeOrangeCell ? 'activeOrange' : index === wrongCell ? 'wrong' : ''}`}
        onPointerDown={() => handleCellClick(index)}
      />
    ));
  };

  return (
    <div className="gameContainer" ref={gameContainerRef} style={{ position: 'relative' }}>
      {!gameStarted ? (
        <div className="startGameContainer">
          <button type="button" onPointerDown={startGame}>Start</button>
        </div>
      ) : (
        <div>
          <div className="gridGameHeader" ref={gridGameHeaderRef}>
            <h2>Grid Game</h2>
            <div className="scoreInfo">
              <p>Score: {score}</p>
              <p>Missed: {missed}</p>
              <p>Wrong: {wrong}</p>
            </div>
            <p>Zeit: {time} Sekunden</p>
            <button className="grid-button" type="button" onPointerDown={restartGame}>Neustart</button>
            {paused ? (
              <button className="grid-button" type="button" onPointerDown={resumeGame}>Fortsetzen</button>
            ) : (
              <button className="grid-button" type="button" onPointerDown={stopGame}>Pause</button>
            )}
          </div>

          <div className="gridGameContainer" ref={gridGameContainerRef}>
            {renderGrid()}
          </div>

          {distractionActive && (
            <button type="button" onPointerDown={handleDistractionPointerDown} className="distractionButton">
              Klick mich!
            </button>
          )}

          {showStats && (
            <div className="statsModal">
              <h3>Statistiken</h3>
              <p>Score: {score}</p>
              <p>Missed: {missed}</p>
              <p>Wrong: {wrong}</p>
              <p>Zeit: {time} Sekunden</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default GridGame;
