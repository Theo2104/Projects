import React, { useState, useEffect } from "react";
import "../index.css";

export default function Main() {
    const [csvData, setCsvData] = useState([]);

    useEffect(() => {
        fetch('http://localhost:5000/api/csv-data')
            .then(response => response.json())
            .then(data => {
                setCsvData(data);
            })
            .catch(error => {
                console.error('Fehler beim Abrufen der CSV-Daten:', error);
            });
    }, []);

    return (
        <main className="main-container">
            <div className="main-overview">
                <h1>World Data Overview ...</h1>
                <div className="main-overview-columns">
                    <p>Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur</p>
                    <p> sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod</p>
                    <p>tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. from: <a href="#">www.loremipsum.de</a></p>
                </div>
            </div>
            <div className="main-show-hide">
                <ul className="main-show-hide-ul">Show/Hide:
                    <li className="main-show-hide-li">birth rate |</li>
                    <li className="main-show-hide-li">cellphones |</li>
                    <li className="main-show-hide-li">children / woman |</li>
                    <li className="main-show-hide-li">electric usage |</li>
                    <li className="main-show-hide-li">internet usage</li>
                </ul>
            </div>
            <table>
                <thead>
                    <tr>
                        {csvData.length > 0 && Object.keys(csvData[0]).map((header, index) => (
                            <th key={index}>{header}</th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {csvData.map((row, rowIndex) => (
                        <tr key={rowIndex}>
                            {Object.values(row).map((value, colIndex) => (
                                <td key={colIndex}>{value}</td>
                            ))}
                        </tr>
                    ))}
                </tbody>
            </table>
            <div className="main-show-hide">
                <ul className="main-show-hide-ul">Show/Hide:
                    <li className="main-show-hide-li">birth rate |</li>
                    <li className="main-show-hide-li">cellphones |</li>
                    <li className="main-show-hide-li">children / woman |</li>
                    <li className="main-show-hide-li">electric usage |</li>
                    <li className="main-show-hide-li">internet usage</li>
                </ul>
            </div>
        </main>
    )
}