import React, { useEffect, useRef, useState } from 'react';
import * as d3 from 'd3';
import "./Piechart.css"

interface Data {
    [key: string]: any;
}

const PieChart: React.FC<{ data: Data[] }> = ({ data }) => {
    const [selectedItemIndex, setSelectedItemIndex] = useState(0);

    const handleSelectChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
        setSelectedItemIndex(parseInt(event.target.value));
    };

    return (
        <div>
            <select value={selectedItemIndex} onChange={handleSelectChange}>
                {data.map((item: Data, index: number) => (
                    <option key={index} value={index}>{item.Country}</option>
                ))}
            </select>
            <PieChartSVG data={data[selectedItemIndex]} />
        </div>
    );
};

const PieChartSVG: React.FC<{ data: Data }> = ({ data }) => {
    const svgRef = useRef<SVGSVGElement>(null);

    useEffect(() => {
        if (!data) return;

        const processData = (value: any) => {
            if (typeof value === 'string') {
                value = value.replace('%', '').replace(/,/g, '').replace(/[^0-9.]/g, '');
            }
            return parseFloat(value);
        };

        const pieData = {
            "Agricultural_Land(%)": processData(data["Agricultural_Land(%)"]),
            "Forested_Area(%)": processData(data["Forested_Area_(%)"]),
        };

        const width = 800;
        const height = 600;
        const margin = 40;
        const radius = Math.min(width, height) / 2 - margin;

        const color = d3.scaleOrdinal<string>()
            .domain(Object.keys(pieData))
            .range(["#98abc5", "#8a89a6", "#7b6888", "#6b486b"]);

        const pie = d3.pie<{ key: string, value: number }>()
            .value(d => d.value);
        const data_ready = pie(Object.entries(pieData).map(([key, value]) => ({ key, value: Number(value) })));

        const svg = d3.select(svgRef.current);
        svg.selectAll("*").remove();

        const g = svg
            .attr("width", width)
            .attr("height", height)
            .append("g")
            .attr("transform", `translate(${width / 2},${height / 2})`);

        const arc = d3.arc<{ key: string, value: number }>()
            .innerRadius(0)
            .outerRadius(radius);

        const tooltip = d3.select("body")
            .append("div")
            .style("position", "absolute")
            .style("background", "white")
            .style("border", "1px solid #ccc")
            .style("padding", "5px")
            .style("display", "none")
            .style("pointer-events", "none");

        g.selectAll('path')
            .data(data_ready)
            .enter()
            .append('path')
            .attr('d', arc as any)
            .attr('fill', d => color(d.data.key) as string)
            .attr("stroke", "black")
            .style("stroke-width", "2px")
            .style("opacity", 0.7)
            .on("pointerover", (event, d) => {
                tooltip
                    .style("display", "block")
                    .html(`<strong>${d.data.key}</strong>: ${d.data.value}`);
            })
            .on("pointermove", (event) => {
                tooltip
                    .style("left", (event.pageX + 10) + "px")
                    .style("top", (event.pageY - 20) + "px");
            })
            .on("pointerout", () => {
                tooltip.style("display", "none");
            });

        return () => {
            d3.select(svgRef.current).selectAll('*').remove();
            tooltip.remove();
        };

    }, [data]);

    return <svg ref={svgRef} width={450} height={450}></svg>;
};

export default PieChart;
