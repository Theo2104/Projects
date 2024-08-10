import React, { useState, useEffect } from 'react'
import Papa from 'papaparse'
import BarChartComponent from './Barchart/Barchart'
import LineChartComponent from './Linechart/Linechart'
import WorldMap from './Worldmap/Worldmap'
import Radarchart from './Radarchart/Radarchart'
import Bubblechart from './Bubblechart/Bubblechart'
import './DataVis.css'

const DataVis: React.FC = () => {
	const [csvData, setCsvData] = useState<any[]>([])
	const [selectedCountries, setSelectedCountries] = useState<string[]>([])

	useEffect(() => {
		const fetchData = async () => {
			try {
				const response = await fetch('/world-data-2023.csv')
				if (!response.ok) {
					throw new Error('Failed to fetch CSV')
				}
				const csv = await response.text()
				const parsedData = Papa.parse(csv, {
					header: true,
					transform: (value, field) => {
						if (field === 'Population') {
							const formattedPopulation =
								parseInt(value.replace(/,/g, ''), 10) || 0
							const populationInMillions = formattedPopulation / 1000000
							return populationInMillions.toFixed(1)
						}
						return value
					},
				}).data

				parsedData.sort(
					(a: any, b: any) =>
						parseInt(b.Population, 10) - parseInt(a.Population, 10),
				)

				const top10Countries = parsedData.slice(0, 10)

				setCsvData(top10Countries)
			} catch (error) {
				console.error('Error fetching or parsing CSV data:', error)
			}
		}

		fetchData()
	}, [])

	const handleCountryClick = (country: string) => {
		const normalizedCountry = country === 'USA' ? 'United States' : country
		const normalizedSelectedCountries = selectedCountries.map((c) =>
			c === 'United States' ? 'USA' : c,
		)

		setSelectedCountries((prevSelected) =>
			normalizedSelectedCountries.includes(normalizedCountry)
				? prevSelected.filter((c) => c !== normalizedCountry)
				: [...prevSelected, normalizedCountry],
		)
	}

	const handleDeselectAll = () => {
		setSelectedCountries([])
	}

	return (
		<div className="DataVis">
			<button onPointerDown={handleDeselectAll} className="deselectButton">
				Deselect All
			</button>
			<div className="charts-container">
				<div className="row">
					<div className="chart">
						<WorldMap
							data={csvData}
							selectedCountries={selectedCountries}
							onCountryClick={handleCountryClick}
						/>
					</div>
					<div className="chart">
						<Bubblechart
							data={csvData}
							selectedCountries={selectedCountries}
							onCountryClick={handleCountryClick}
						/>
					</div>
				</div>
				<div className="row">
					<div className="chart">
						<LineChartComponent
							data={csvData}
							selectedCountries={selectedCountries}
							onCountryClick={handleCountryClick}
						/>
					</div>
					<div className="chart">
						<BarChartComponent
							data={csvData}
							selectedCountries={selectedCountries}
							onCountryClick={handleCountryClick}
						/>
					</div>
					<div className="chart">
						{selectedCountries.length === 1 ? (
							<Radarchart
								data={csvData}
								selectedCountries={selectedCountries}
							/>
						) : (
							<div className="radarchart-placeholder">
								{selectedCountries.length === 0
									? 'Please select a country to view the radar chart.'
									: 'Please select only one country to view the radar chart.'}
							</div>
						)}
					</div>
				</div>
			</div>
		</div>
	)
}

export default DataVis
