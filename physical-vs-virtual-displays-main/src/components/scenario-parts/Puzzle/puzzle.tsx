import "react-jigsaw-puzzle/lib/jigsaw-puzzle.css";
import "./puzzle.css";
import { JigsawPuzzle } from './jigsaw-puzzle';
import Img from "../../../assets/dresden.jpg"

export default function Puzzle() {
    return (
        <JigsawPuzzle
            imageSrc={Img}
            rows={3}
            columns={4}
            onSolved={() => console.log("solved")}
        />
    )
}