import React, { useEffect, useRef, useState } from 'react';
import * as d3 from 'd3';
import { data } from './data';

interface Node extends d3.SimulationNodeDatum {
  id: string;
  name: string;
}

interface Link extends d3.SimulationLinkDatum<Node> {
  source: string | Node;
  target: string | Node;
}

const MindMap: React.FC = () => {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const childRef = useRef<HTMLDivElement>(null);
  const [parentSize, setParentSize] = useState<{ width: number; height: number } | null>(null);

  // Log event
  const logEvent = (action: string) => {
    const event = new CustomEvent('log', {
      detail: {
        time: Date.now(),
        tags: [action],
        action: `${action}`,
      },
    });
    window.dispatchEvent(event);
  };
  
  // Size the component properly with respect to parent
  useEffect(() => {
    const handleResize = () => {
      if (childRef.current) {
        const parentElement = childRef.current.parentElement;
        if (parentElement) {
          const { width, height } = parentElement.getBoundingClientRect();
          setParentSize({ width: width, height: height});
        }
      }
    };

    handleResize();
    window.addEventListener('resize', handleResize);

    return () => window.removeEventListener('resize', handleResize);
  }, []);

  useEffect(() => {
    if (!parentSize) return;

    let nodeId: any = null;
    let isDragging: boolean = false;

    const width = parentSize.width - 64;
    const height = parentSize.height - 64;

    const svg = d3.select(svgRef.current);
    svg.selectAll('*').remove();

    function setSelectedNodeId(node: string) {
      nodeId = node;
    }

    const simulation = d3
      .forceSimulation<Node>(data.nodes)
      .force('link', d3.forceLink<Node, Link>(data.links).id((d) => d.id))
      .force('charge', d3.forceManyBody().strength(-((width * height) / 150)))
      .force('center', d3.forceCenter(width / 2, height / 2));

    // Create a group for links
    const linkGroup = svg.append('g').attr('stroke', '#999').attr('stroke-opacity', 0.6);

    // Append and update links
    const links = linkGroup
      .selectAll<SVGLineElement, Link>('line')
      .data<Link>(data.links)
      .enter()
      .append('line')
      .attr('stroke-width', 2.5)
      .attr('stroke', '#999');

    // Create a group for nodes
    const nodeGroup = svg.append('g').attr('stroke', '#fff').attr('stroke-width', 1.5);

    // Append and update nodes
    const nodes = nodeGroup
      .selectAll<SVGCircleElement, Node>('circle')
      .data<Node>(data.nodes, (d) => d.name)
      .enter()
      .append('circle')
      .attr('r', 10)
      .attr('fill', 'black')
      .each(function (d) {
        const nodeElement = d3.select(this);
        nodeElement.on('pointerdown', (event) => dragstarted(event, d, simulation))
                   .on('pointermove', (event) => dragged(event, d))
                   .on('pointerup', (event) => dragended(event, d, simulation));
      });

    // Append labels
    const labelGroup = svg.append('g').attr('class', 'labels');
    const labels = labelGroup
      .selectAll<SVGTextElement, Node>('text')
      .data<Node>(data.nodes, (d) => d.name)
      .enter()
      .append('text')
      .attr('class', 'label')
      .attr('fill', '#555')
      .attr('font-size', 16)
      .attr('dx', 10)
      .attr('dy', '.35em')
      .text((d) => d.name);

    simulation.on('tick', () => {
      links
        .attr('x1', (d) => (d.source as Node).x!)
        .attr('y1', (d) => (d.source as Node).y!)
        .attr('x2', (d) => (d.target as Node).x!)
        .attr('y2', (d) => (d.target as Node).y!)
        .attr('stroke', function (d) {
          if (!nodeId) {
            return '#999';
          }
          // Mark selected links red
          const isSelectedLink =
            (typeof d.source !== 'string' && (d.source as Node).id === nodeId) ||
            (typeof d.target !== 'string' && (d.target as Node).id === nodeId); 

          return isSelectedLink ? 'red' : '#999';
        });

      nodes
        .attr('cx', (d) => d.x!)
        .attr('cy', (d) => d.y!)
        .attr('fill', function (d) {
          if (!nodeId) {
            return 'black';
          }
          // Mark selected node red
          const isSelectedNode = d.id === nodeId;
          return isSelectedNode ? 'red' : 'black';
        });

      labels.attr('x', (d) => d.x!).attr('y', (d) => d.y!);
    });

    // Drag and Drop Interaction
    function dragstarted(event: PointerEvent, d: Node, simulation: d3.Simulation<Node, undefined>) {
      if (!event.isPrimary) return;
      isDragging = true;
      simulation.alphaTarget(0.3).restart();
      d.fx = d.x;
      d.fy = d.y;
      setSelectedNodeId(d.id);
      logEvent(`Node ${d.id} has been selected with start position ${d.fx, d.fy}`);
    }

    function dragged(event: PointerEvent, d: Node) {
      if (!event.isPrimary || !isDragging) return;
      d.fx = Math.max(10, Math.min(width - 20, event.clientX - svgRef.current!.getBoundingClientRect().left));
      d.fy = Math.max(10, Math.min(height - 20, event.clientY - svgRef.current!.getBoundingClientRect().top));
    }

    function dragended(event: PointerEvent, d: Node, simulation: d3.Simulation<Node, undefined>) {
      if (!event.isPrimary) return;
      logEvent(`Node ${d.id} has been dragged to ${d.fx, d.fy}`);
      isDragging = false;
      simulation.alphaTarget(0);
      d.fx = null;
      d.fy = null;
    }

    return () => {
      simulation.stop();
    };
  }, [parentSize]);

  return (
    <div ref={childRef} style={{ height: '100%', position: 'relative' }}>
      <svg ref={svgRef} width="100%" height="100%"></svg>
    </div>
  );
};

export default MindMap;
