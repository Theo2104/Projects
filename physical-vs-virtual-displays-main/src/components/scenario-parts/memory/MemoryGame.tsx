import React, { useState, useEffect } from 'react';
import './memory.css'


const MemoryGame: React.FC = () => {
  const [cards, setCards] = useState<string[]>([]);
  const [flippedCards, setFlippedCards] = useState<number[]>([]);
  const [matchedCards, setMatchedCards] = useState<number[]>([]);

  // function for shuffling the cards
  const shuffleCards = (cards: string[]) => {
    for (let i = cards.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [cards[i], cards[j]] = [cards[j], cards[i]];
    }
    return cards;
  };

  // function for revealing a card
  const flipCard = (index: number) => {
    // Check whether the card has already been turned over
    console.log(`Card ${index} clicked`);

    window.dispatchEvent(new CustomEvent("log", {
      detail: {
        time: new Date().getTime(),
        tags: ['memory'],
        action: 'clicked',
      },
    }));

    if (flippedCards.includes(index) || matchedCards.includes(index)) {
      return;
    }

    // Check whether two cards have already been turned over
    if (flippedCards.length === 2) {
      return;
    }

    // Add the index to the list of flipped cards
    setFlippedCards([...flippedCards, index]);
  };

  useEffect(() => {
    // Check whether two cards have been turned over
    if (flippedCards.length === 2) {
      const [firstCard, secondCard] = flippedCards;
      if (cards[firstCard] === cards[secondCard]) {
        setMatchedCards([...matchedCards, firstCard, secondCard]);
      }
      setTimeout(() => setFlippedCards([]), 1000);
    }
  }, [flippedCards, cards, matchedCards]);

  useEffect(() => {
    // create the deck
    if (cards.length === 0) {
      const newCards = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'].flatMap((card) => [card, card]);
      setCards(shuffleCards(newCards));
    }
  }, [cards]);

  return (
    <div className="memory-game">
      {cards.map((card, index) => (
        <div
          key={index}
          className={`card ${flippedCards.includes(index) || matchedCards.includes(index) ? 'flipped' : ''}`}
          onClick={() => flipCard(index)}
        >
          <div className="card-inner">
            <div className="card-front">?</div>
            <div className="card-back">{card}</div>
          </div>
        </div>
      ))}
    </div>
  );
};

export default MemoryGame;
