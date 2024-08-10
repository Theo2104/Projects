import React, { useRef, useEffect } from 'react';
import * as d3 from 'd3';
import { FeatureCollection, Geometry, GeoJsonProperties } from 'geojson';
import './Worldmap.css';

interface CountryData {
  Country: string;
}

interface WorldMapProps {
  data: CountryData[];
  selectedCountries: string[];
  onCountryClick: (country: string) => void;
}

const WorldMap: React.FC<WorldMapProps> = ({
  data,
  selectedCountries,
  onCountryClick,
}) => {
  const svgRef = useRef<SVGSVGElement>(null);

  // Mapping function for special cases
  const mapCountryName = (countryName: string) => {
    const specialCases: { [key: string]: string } = {
      'United States': 'USA',
      USA: 'United States',
    };
    return specialCases[countryName] || countryName;
  };

  useEffect(() => {
    const svg = d3.select(svgRef.current);
    const width = +svg.attr('width');
    const height = +svg.attr('height');

    const projection = d3
      .geoNaturalEarth1()
      .scale(width / 2 / Math.PI)
      .translate([width / 2, height / 2]);

    const path = d3.geoPath().projection(projection);

    const tooltip = d3
      .select('body')
      .selectAll('.world__tooltip')
      .data([null])
      .join('div')
      .attr('class', 'world__tooltip')
      .style('position', 'absolute')
      .style('background-color', 'white')
      .style('border', 'solid')
      .style('border-width', '1px')
      .style('border-radius', '5px')
      .style('padding', '10px')
      .style('display', 'none');

    d3.json(
      'https://raw.githubusercontent.com/holtzy/D3-graph-gallery/master/DATA/world.geojson',
    )
      .then((geoJsonData) => {
        const geoData = geoJsonData as FeatureCollection<
          Geometry,
          GeoJsonProperties
        >;

        svg.selectAll('*').remove();

        svg
          .append('g')
          .selectAll('path')
          .data(geoData.features)
          .join('path')
          .attr('fill', (d) =>
            selectedCountries.includes(mapCountryName(d.properties?.name || ''))
              ? 'orange'
              : '#69b3a2',
          )
          .attr('d', path)
          .style('stroke', '#fff')
          .on('pointerover', (event, d) => {
            tooltip
              .style('display', 'block')
              .html(d.properties?.name || '')
              .style('left', event.pageX + 10 + 'px')
              .style('top', event.pageY - 20 + 'px');
          })
          .on('pointermove', (event) => {
            tooltip
              .style('left', event.pageX + 10 + 'px')
              .style('top', event.pageY - 20 + 'px');
          })
          .on('pointerout', () => {
            tooltip.style('display', 'none');
          })
          .on('pointerdown', (event, d) => {
            const countryName = mapCountryName(d.properties?.name || '');
            onCountryClick(countryName);
          });
      })
      .catch((error) => {
        console.error('Error loading or parsing data:', error);
      });
  }, [data, selectedCountries, onCountryClick]);

  return <svg ref={svgRef} width={1600} height={600}></svg>;
};

export default WorldMap;
