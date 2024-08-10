import React, { useEffect, useRef } from 'react';
import * as d3 from 'd3';
import './Radarchart.css';

interface CountryData {
  Country?: string;
  [key: string]: any; // dies ermöglicht den Zugriff auf beliebige Felder
}

interface WorldMapProps {
  data: CountryData[];
  selectedCountries: string[];
}

const RadarChart: React.FC<WorldMapProps> = ({
  data,
  selectedCountries,
}) => {
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    if (!data || selectedCountries.length !== 1) return;

    const selectedCountry = selectedCountries[0];
    const selectedCountryData = data.find((d) => d.Country === selectedCountry);
    if (!selectedCountryData) return;

    const newSeries: string[] = [
      'Population',
      'Birth_Rate',
      'Population:_Labor_force_participation(%)',
      'Land_Area(Km2)',
      'Co2-Emissions',
    ];
    const newData: CountryData = { ...selectedCountryData };

    newSeries.forEach((key) => {
      let value = selectedCountryData[key];

      if (typeof value === 'string') {
        value = value.replace('%', '').replace(/[^0-9.]/g, '');
      }

      value = parseFloat(value.toString().replace(/,/g, ''));
      if (!isNaN(value)) {
        if (key === 'Land_Area(Km2)' || key === 'Co2-Emissions') {
          newData[key] = value / 1000000;
        } else {
          newData[key] = value;
        }
      } else {
        newData[key] = value;
      }
    });

    const features = newSeries;
    const width = 600;
    const height = 600;

    const maxRangeValues: number[] = newSeries.map((key: string) => {
      const value = newData[key];
      const numericValue = parseFloat(value);
      return numericValue;
    });

    const maxRangeValue = Math.max(...maxRangeValues);
    const radialScale = d3
      .scaleLinear()
      .domain([0, maxRangeValue])
      .range([0, 250]);

    const svg = d3.select(svgRef.current);
    svg.selectAll('*').remove();

    const featureData = features.map((f, i) => {
      const angle = Math.PI / 2 + (2 * Math.PI * i) / features.length;
      return {
        name: f,
        angle: angle,
        line_coord: angleToCoordinate(
          angle,
          maxRangeValue,
          radialScale,
          width,
          height,
        ),
        label_coord: angleToCoordinate(
          angle,
          maxRangeValue,
          radialScale,
          width,
          height,
        ),
      };
    });

    svg
      .selectAll('line')
      .data(featureData)
      .join('line')
      .attr('x1', width / 2)
      .attr('y1', height / 2)
      .attr('x2', (d) => d.line_coord.x)
      .attr('y2', (d) => d.line_coord.y)
      .attr('stroke', 'black')
      .attr('stroke-width', 2);

    svg
      .selectAll('.axislabel')
      .data(featureData)
      .join('text')
      .attr('x', (d) => d.label_coord.x)
      .attr('y', (d) => d.label_coord.y)
      .attr('font-size', '12px')
      .attr('font-weight', 'bold')
      .text((d) => d.name);

    const line = d3
      .line()
      .x((d: [number, number]) => d[0])
      .y((d: [number, number]) => d[1]);

    const coordinates = getPathCoordinates(
      newData,
      features,
      radialScale,
      width,
      height,
    );

    svg
      .selectAll('path')
      .data([newData])
      .join('path')
      .datum(coordinates)
      .attr('d', line)
      .attr('stroke-width', 3)
      .attr('stroke', 'darkorange')
      .attr('fill', 'darkorange')
      .attr('stroke-opacity', 1)
      .attr('opacity', 0.5);
  }, [data, selectedCountries]);

  function getPathCoordinates(
    data_point: CountryData,
    features: string[],
    radialScale: d3.ScaleLinear<number, number>,
    width: number,
    height: number,
  ): [number, number][] {
    const coordinates: [number, number][] = [];
    features.forEach((ft_name, i) => {
      const angle = Math.PI / 2 + (2 * Math.PI * i) / features.length;
      const { x, y } = angleToCoordinate(
        angle,
        parseFloat(data_point[ft_name]),
        radialScale,
        width,
        height,
      );
      coordinates.push([x, y]);
    });
    return coordinates;
  }

  function angleToCoordinate(
    angle: number,
    value: number,
    radialScale: d3.ScaleLinear<number, number>,
    width: number,
    height: number,
  ): { x: number; y: number } {
    const x = Math.cos(angle) * radialScale(value);
    const y = Math.sin(angle) * radialScale(value);
    return { x: width / 2 + x, y: height / 2 - y };
  }

  return (
    <div>
      {selectedCountries.length === 1 ? (
        <svg ref={svgRef} width={600} height={600} id="star_chart"></svg>
      ) : (
        <div className="placeholder">
          Please select a single country to view the radar chart.
        </div>
      )}
    </div>
  );
};

export default RadarChart;
