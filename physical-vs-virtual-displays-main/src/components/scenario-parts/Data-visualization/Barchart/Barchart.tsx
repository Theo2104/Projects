import React, { useRef, useEffect } from 'react'
import * as d3 from 'd3'
import './Barchart.css'

interface BarChartProps {
	data: { Country: string; Population: number }[]
	selectedCountries: string[]
	onCountryClick: (country: string) => void
}

const BarChartComponent: React.FC<BarChartProps> = ({
	data,
	selectedCountries,
	onCountryClick,
}) => {
	const svgRef = useRef<SVGSVGElement>(null)

	useEffect(() => {
		const width = 800
		const height = 600
		const marginTop = 30
		const marginRight = 0
		const marginBottom = 50
		const marginLeft = 50

		const x = d3
			.scaleBand()
			.domain(data.map((d) => d.Country))
			.range([marginLeft, width - marginRight])
			.padding(0.1)

		const y = d3
			.scaleLinear()
			.domain([0, 1400])
			.range([height - marginBottom, marginTop])

		const svg = d3
			.select(svgRef.current)
			.attr('width', width)
			.attr('height', height)
			.attr('viewBox', [0, 0, width, height])
			.style('max-width', '100%')
			.style('height', 'auto')

		svg.selectAll('*').remove()

		const tooltip = d3
			.select('body')
			.selectAll('.tooltip')
			.data([null])
			.join('div')
			.attr('class', 'tooltip')
			.style('position', 'absolute')
			.style('background', 'white')
			.style('border', '1px solid #ccc')
			.style('padding', '5px')
			.style('display', 'none')
			.style('pointer-events', 'none')

		svg
			.append('g')
			.attr('fill', 'steelblue')
			.selectAll('rect')
			.data(data)
			.join('rect')
			.attr('x', (d) => x(d.Country) || 0)
			.attr('y', (d) => y(d.Population))
			.attr('height', (d) => y(0) - y(d.Population))
			.attr('width', x.bandwidth())
			.attr('fill', (d) =>
				selectedCountries.includes(d.Country) ? 'orange' : 'steelblue',
			)
			.on('pointerover', (event, d) => {
				tooltip.style('display', 'block').html(
					`<strong>Country:</strong> ${d.Country}<br/>
            <strong>Population:</strong> ${d.Population}<br/>`,
				)
			})
			.on('pointermove', (event) => {
				tooltip
					.style('left', event.pageX + 10 + 'px')
					.style('top', event.pageY - 20 + 'px')
			})
			.on('pointerout', () => {
				tooltip.style('display', 'none')
			})
			.on('pointerdown', (event, d) => {
				onCountryClick(d.Country)
			})

		svg
			.append('g')
			.attr('transform', `translate(0,${height - marginBottom})`)
			.call(d3.axisBottom(x).tickSizeOuter(0))
			.call((g) => g.selectAll('.tick text').style('font-size', '12px').style('font-weight', 'bold'))
			.call((g) => g.selectAll('.tick line').style('stroke-width', '2px'))

		svg
			.append('g')
			.attr('transform', `translate(${marginLeft},0)`)
			.call(d3.axisLeft(y).tickFormat((y) => y.valueOf().toFixed()))
			.call((g) => g.selectAll('.tick text').style('font-size', '12px').style('font-weight', 'bold'))
			.call((g) => g.selectAll('.tick line').style('stroke-width', '2px'))
			.call((g) => g.select('.domain').remove())
			.call((g) =>
				g
					.append('text')
					.attr('x', -marginLeft)
					.attr('y', 10)
					.attr('fill', 'currentColor')
					.attr('text-anchor', 'start')
					.style('font-size', '14px')
					.style('font-weight', 'bold')
					.text('↑ Population (Mil)'),
			)
	}, [data, selectedCountries, onCountryClick])

	return <svg ref={svgRef}></svg>
}

export default BarChartComponent
