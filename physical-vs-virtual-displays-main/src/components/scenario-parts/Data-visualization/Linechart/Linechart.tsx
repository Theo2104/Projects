import React, { useRef, useEffect } from 'react';
import * as d3 from 'd3';
import './Linechart.css';

interface Props {
  data: any[];
  selectedCountries: string[];
  onCountryClick: (country: string) => void;
}

const LineChart = ({ data, selectedCountries, onCountryClick }: Props) => {
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    if (!data || data.length === 0) return;

    const margin = { top: 40, right: 50, bottom: 50, left: 50 };
    const width = 800 - margin.left - margin.right;
    const height = 600 - margin.top - margin.bottom;

    const svg = d3
      .select(svgRef.current)
      .attr('width', width + margin.left + margin.right)
      .attr('height', height + margin.top + margin.bottom);

    svg.selectAll('*').remove(); // Clear previous content

    const chartArea = svg
      .append('g')
      .attr('transform', `translate(${margin.left},${margin.top})`);

    const xScale = d3
      .scaleLinear()
      .domain([d3.min(data, (d) => d.Population) || 0, 1400])
      .range([0, width]);

    const yScale = d3.scaleLinear().domain([0, 40]).range([height, 0]);

    const line = d3
      .line<{ Birth_Rate: number; Population: number }>()
      .x((d) => xScale(d.Population))
      .y((d) => yScale(d.Birth_Rate));

    chartArea
      .append('path')
      .datum(data)
      .attr('fill', 'none')
      .attr('stroke', 'steelblue')
      .attr('stroke-width', 1.5)
      .attr('d', line);

    chartArea
      .append('g')
      .attr('transform', `translate(0,${height})`)
      .call(d3.axisBottom(xScale));

    chartArea.append('g').call(d3.axisLeft(yScale));

    // Add X axis label
    chartArea
      .append('text')
      .attr('text-anchor', 'middle')
      .attr('x', width / 2)
      .attr('y', height + margin.bottom - 10)
      .text('Population');

    // Add Y axis label
    chartArea
      .append('text')
      .attr('text-anchor', 'middle')
      .attr('transform', 'rotate(-90)')
      .attr('x', -height / 2)
      .attr('y', -margin.left + 20)
      .text('Birth Rate');

    // Add circles for data points
    chartArea
      .selectAll('circle')
      .data(data)
      .enter()
      .append('circle')
      .attr('cx', (d) => xScale(d.Population))
      .attr('cy', (d) => yScale(d.Birth_Rate))
      .attr('r', 5)
      .attr('fill', (d) =>
        selectedCountries.includes(d.Country) ? 'orange' : 'steelblue'
      )
      .on('pointerdown', (_event, d) => onCountryClick(d.Country));
  }, [data, selectedCountries, onCountryClick]);

  return <svg ref={svgRef}></svg>;
};

export default LineChart;
