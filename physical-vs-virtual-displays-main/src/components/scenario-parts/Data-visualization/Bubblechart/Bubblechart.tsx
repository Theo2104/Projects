import React, { useEffect, useRef } from 'react';
import * as d3 from 'd3';
import './Bubblechart.css';

interface CountryData {
  Country: string;
  Population: number;
  'Co2-Emissions': number;
  GDP: number;
}

const processCountryData = (data: any[]): CountryData[] => {
  return data.map((item) => {
    const population =
      typeof item.Population === 'string'
        ? item.Population
        : item.Population.toString();
    const co2Emissions =
      typeof item['Co2-Emissions'] === 'string'
        ? item['Co2-Emissions']
        : item['Co2-Emissions'].toString();
    const gdp = typeof item.GDP === 'string' ? item.GDP : item.GDP.toString();

    const newData: CountryData = {
      Country: item.Country,
      Population: parseFloat(population.replace(/,/g, '')),
      'Co2-Emissions': parseFloat(co2Emissions.replace(/,/g, '')) / 1000000, // Convert to million tonnes
      GDP: parseFloat(gdp.replace(/[^0-9.]/g, '')), // Filter out non-numeric characters
    };

    // Apply logarithmic scaling to GDP
    newData.GDP = Math.log10(newData.GDP); // Taking the logarithm base 10

    return newData;
  });
};

const BubbleChart: React.FC<{
  data: any[];
  selectedCountries: string[];
  onCountryClick: (country: string) => void;
}> = ({ data, selectedCountries, onCountryClick }) => {
  const svgRef = useRef<SVGSVGElement | null>(null);

  const countryData = processCountryData(data);

  useEffect(() => {
    if (!svgRef.current || countryData.length === 0) return;

    const margin = { top: 10, right: 30, bottom: 30, left: 60 };
    const width = 1200 - margin.left - margin.right;
    const height = 600 - margin.top - margin.bottom;

    const svg = d3
      .select(svgRef.current)
      .attr('width', width + margin.left + margin.right)
      .attr('height', height + margin.top + margin.bottom);

    svg.selectAll('*').remove(); // Clear previous content

    const chartArea = svg
      .append('g')
      .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

    const xScale = d3.scaleLinear().domain([0, 1400]).range([0, width]);

    const yScale = d3.scaleLinear().domain([0, 10]).range([height, 0]);

    chartArea
      .append('g')
      .attr('transform', 'translate(0,' + height + ')')
      .call(d3.axisBottom(xScale));

    chartArea.append('g').call(d3.axisLeft(yScale));

    const tooltip = d3
      .select('body')
      .append('div')
      .style('position', 'absolute')
      .style('background', 'white')
      .style('border', '1px solid #ccc')
      .style('padding', '5px')
      .style('display', 'none')
      .style('pointer-events', 'none');

    chartArea
      .selectAll('circle')
      .data(countryData)
      .enter()
      .append('circle')
      .attr('cx', (d) => xScale(d.Population))
      .attr('cy', (d) => yScale(d['Co2-Emissions']))
      .attr('r', (d) => xScale(d.GDP))
      .attr('fill', (d) =>
        selectedCountries.includes(d.Country)
          ? 'orange'
          : 'rgba(75, 192, 192, 0.7)',
      )
      .on('pointerover', (event, d) => {
        tooltip
          .style('display', 'block')
          .html(`<strong>Country:</strong> ${d.Country}<br/>
            <strong>Population:</strong> ${d.Population}<br/>
            <strong>CO2 Emissions:</strong> ${d['Co2-Emissions']}<br/>
            <strong>GDP:</strong> ${d.GDP}`);
      })
      .on('pointermove', (event) => {
        tooltip
          .style('left', event.pageX + 10 + 'px')
          .style('top', event.pageY - 20 + 'px');
      })
      .on('pointerout', () => {
        tooltip.style('display', 'none');
      })
      .on('pointerdown', (_event, d) => {
        tooltip.style('display', 'none');
        onCountryClick(d.Country);
      });
  }, [countryData, selectedCountries, onCountryClick]);

  return <svg ref={svgRef}></svg>;
};

export default BubbleChart;
