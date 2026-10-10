import React from "react";
// import ReactDOM from "react-dom";
import { createRoot } from "react-dom/client";
import "./css/base-pc.css";
import App from "./App";

const root = createRoot(document.getElementById("root") as HTMLElement);
root.render(<App />);