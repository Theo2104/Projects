import React from "react";
import Logo from "../world_data-logo.svg";
import "../index.css";

export default function Navbar() {
  return (
    <nav className="nav-container">
      <a href="#" className="nav-logo-link">
        <img src={Logo} alt="Logo" className="nav-logo" />
      </a>
      <ul className="nav-list">
        <li><a href="#"><i className="fa fa-table"></i> A1-Table</a></li>
        <li><a href="#"><i className="fa fa-cog"></i> A2-Parse</a></li>
        <li><a href="#"><i className="fa fa-save"></i> A2-Save</a></li>
        <li><a href="#"><i className="fa fa-print"></i> A2-Print</a></li>
        <li><a href="#"><i className="fa fa-rest"></i> A3-REST</a></li>
        <li><a href="#"><i className="fa fa-bar-chart"></i> A4-Vis</a></li>
        <li><a href="#"><i className="fa fa-cube"></i> A5-3D</a></li>
      </ul>
      <a href="#" id="pull">Menu</a>
    </nav>
  );
}
