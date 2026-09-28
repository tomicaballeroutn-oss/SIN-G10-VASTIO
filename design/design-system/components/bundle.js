/* @ds-bundle: {"format": 4, "namespace": "Vastio", "components": [{"name": "Icon"}, {"name": "Button"}, {"name": "IconButton"}, {"name": "Input"}, {"name": "Select"}, {"name": "Checkbox"}, {"name": "Switch"}, {"name": "Stepper"}, {"name": "Alert"}, {"name": "Dialog"}, {"name": "EmptyState"}, {"name": "Badge"}, {"name": "StatusChip"}, {"name": "SalonTag"}, {"name": "Card"}, {"name": "Table"}, {"name": "Tabs"}, {"name": "Actor"}, {"name": "Stat"}, {"name": "Timeline"}, {"name": "Nav"}, {"name": "AgendaGrid"}, {"name": "EventCard"}, {"name": "StockLevel"}, {"name": "MovementCard"}]} */
(function () {
  'use strict';
  /* Icon shapes: Lucide (ISC License), https://lucide.dev */
  var ICONS = {"calendar": "<path d=\"M8 2v3\" /> <path d=\"M16 2v3\" /> <rect x=\"3\" y=\"3\" width=\"18\" height=\"18\" rx=\"2\" /> <path d=\"M3 9h18\" />", "calendar-days": "<path d=\"M8 2v3\" /> <path d=\"M16 2v3\" /> <rect x=\"3\" y=\"3\" width=\"18\" height=\"18\" rx=\"2\" /> <path d=\"M3 9h18\" /> <path d=\"M8 13h.01\" /> <path d=\"M12 13h.01\" /> <path d=\"M16 13h.01\" /> <path d=\"M8 17h.01\" /> <path d=\"M12 17h.01\" /> <path d=\"M16 17h.01\" />", "calendar-check": "<path d=\"M8 2v3\" /> <path d=\"M16 2v3\" /> <rect x=\"3\" y=\"3\" width=\"18\" height=\"18\" rx=\"2\" /> <path d=\"M3 9h18\" /> <path d=\"m9 15 2 2 4-4\" />", "calendar-x": "<path d=\"M8 2v3\" /> <path d=\"M16 2v3\" /> <rect x=\"3\" y=\"3\" width=\"18\" height=\"18\" rx=\"2\" /> <path d=\"M3 9h18\" /> <path d=\"m14 13-4 4\" /> <path d=\"m10 13 4 4\" />", "calendar-clock": "<path d=\"M16 14v2.2l1.6 1\" /> <path d=\"M16 2v3\" /> <path d=\"M21 7.338V5a2 2 0 00-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2h2.338\" /> <path d=\"M3 9h5.859\" /> <path d=\"M8 2v3\" /> <circle cx=\"16\" cy=\"16\" r=\"6\" />", "calendar-plus": "<path d=\"M16 18h6\" /> <path d=\"M16 2v3\" /> <path d=\"M19 15v6\" /> <path d=\"M21 11.5V5a2 2 0 00-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2h8.3\" /> <path d=\"M3 9h18\" /> <path d=\"M8 2v3\" />", "calendar-off": "<path d=\"M16 2v3\" /> <path d=\"m2 2 20 20\" /> <path d=\"M21 9h-5.5\" /> <path d=\"M3 9h6\" /> <path d=\"M3.586 3.586A2 2 0 003 5v14a2 2 0 002 2h14a2 2 0 001.414-.586\" /> <path d=\"M8.656 3H19a2 2 0 012 2v10.344\" />", "plus": "<path d=\"M5 12h14\" /> <path d=\"M12 5v14\" />", "minus": "<path d=\"M5 12h14\" />", "x": "<path d=\"M18 6 6 18\" /> <path d=\"m6 6 12 12\" />", "check": "<path d=\"M20 6 9 17l-5-5\" />", "check-check": "<path d=\"M18 6 7 17l-5-5\" /> <path d=\"m22 10-7.5 7.5L13 16\" />", "chevron-left": "<path d=\"m15 18-6-6 6-6\" />", "chevron-right": "<path d=\"m9 18 6-6-6-6\" />", "chevron-down": "<path d=\"m6 9 6 6 6-6\" />", "chevron-up": "<path d=\"m18 15-6-6-6 6\" />", "search": "<path d=\"m21 21-4.34-4.34\" /> <circle cx=\"11\" cy=\"11\" r=\"8\" />", "funnel": "<path d=\"M10 20a1 1 0 0 0 .553.895l2 1A1 1 0 0 0 14 21v-7a2 2 0 0 1 .517-1.341L21.74 4.67A1 1 0 0 0 21 3H3a1 1 0 0 0-.742 1.67l7.225 7.989A2 2 0 0 1 10 14z\" />", "user": "<path d=\"M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2\" /> <circle cx=\"12\" cy=\"7\" r=\"4\" />", "users": "<path d=\"M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2\" /> <path d=\"M16 3.128a4 4 0 0 1 0 7.744\" /> <path d=\"M22 21v-2a4 4 0 0 0-3-3.87\" /> <circle cx=\"9\" cy=\"7\" r=\"4\" />", "log-in": "<path d=\"m10 17 5-5-5-5\" /> <path d=\"M15 12H3\" /> <path d=\"M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4\" />", "log-out": "<path d=\"m16 17 5-5-5-5\" /> <path d=\"M21 12H9\" /> <path d=\"M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4\" />", "settings": "<path d=\"M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915\" /> <circle cx=\"12\" cy=\"12\" r=\"3\" />", "package": "<path d=\"M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z\" /> <path d=\"M12 22V12\" /> <polyline points=\"3.29 7 12 12 20.71 7\" /> <path d=\"m7.5 4.27 9 5.15\" />", "warehouse": "<path d=\"M18 21V10a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1v11\" /> <path d=\"M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 1.132-1.803l7.95-3.974a2 2 0 0 1 1.837 0l7.948 3.974A2 2 0 0 1 22 8z\" /> <path d=\"M6 13h12\" /> <path d=\"M6 17h12\" />", "boxes": "<path d=\"M2.97 12.92A2 2 0 0 0 2 14.63v3.24a2 2 0 0 0 .97 1.71l3 1.8a2 2 0 0 0 2.06 0L12 19v-5.5l-5-3-4.03 2.42Z\" /> <path d=\"m7 16.5-4.74-2.85\" /> <path d=\"m7 16.5 5-3\" /> <path d=\"M7 16.5v5.17\" /> <path d=\"M12 13.5V19l3.97 2.38a2 2 0 0 0 2.06 0l3-1.8a2 2 0 0 0 .97-1.71v-3.24a2 2 0 0 0-.97-1.71L17 10.5l-5 3Z\" /> <path d=\"m17 16.5-5-3\" /> <path d=\"m17 16.5 4.74-2.85\" /> <path d=\"M17 16.5v5.17\" /> <path d=\"M7.97 4.42A2 2 0 0 0 7 6.13v4.37l5 3 5-3V6.13a2 2 0 0 0-.97-1.71l-3-1.8a2 2 0 0 0-2.06 0l-3 1.8Z\" /> <path d=\"M12 8 7.26 5.15\" /> <path d=\"m12 8 4.74-2.85\" /> <path d=\"M12 13.5V8\" />", "wine": "<path d=\"M8 22h8\" /> <path d=\"M7 10h10\" /> <path d=\"M12 15v7\" /> <path d=\"M12 15a5 5 0 0 0 5-5c0-2-.5-4-2-8H9c-1.5 4-2 6-2 8a5 5 0 0 0 5 5Z\" />", "glass-water": "<path d=\"M5.116 4.104A1 1 0 0 1 6.11 3h11.78a1 1 0 0 1 .994 1.105L17.19 20.21A2 2 0 0 1 15.2 22H8.8a2 2 0 0 1-2-1.79z\" /> <path d=\"M6 12a5 5 0 0 1 6 0 5 5 0 0 0 6 0\" />", "arrow-right-left": "<path d=\"m16 3 4 4-4 4\" /> <path d=\"M20 7H4\" /> <path d=\"m8 21-4-4 4-4\" /> <path d=\"M4 17h16\" />", "undo-2": "<path d=\"M9 14 4 9l5-5\" /> <path d=\"M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11\" />", "clipboard-list": "<rect width=\"8\" height=\"4\" x=\"8\" y=\"2\" rx=\"1\" ry=\"1\" /> <path d=\"M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2\" /> <path d=\"M12 11h4\" /> <path d=\"M12 16h4\" /> <path d=\"M8 11h.01\" /> <path d=\"M8 16h.01\" />", "chart-column": "<path d=\"M3 3v16a2 2 0 0 0 2 2h16\" /> <path d=\"M18 17V9\" /> <path d=\"M13 17V5\" /> <path d=\"M8 17v-3\" />", "triangle-alert": "<path d=\"m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3\" /> <path d=\"M12 9v4\" /> <path d=\"M12 17h.01\" />", "info": "<circle cx=\"12\" cy=\"12\" r=\"10\" /> <path d=\"M12 16v-4\" /> <path d=\"M12 8h.01\" />", "circle-check": "<circle cx=\"12\" cy=\"12\" r=\"10\" /> <path d=\"m16 9-5.5 5.5L8 12\" />", "circle-x": "<circle cx=\"12\" cy=\"12\" r=\"10\" /> <path d=\"m15 9-6 6\" /> <path d=\"m9 9 6 6\" />", "clock": "<circle cx=\"12\" cy=\"12\" r=\"10\" /> <path d=\"M12 6v6l4 2\" />", "history": "<path d=\"M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8\" /> <path d=\"M3 3v5h5\" /> <path d=\"M12 7v5l4 2\" />", "pencil": "<path d=\"M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z\" /> <path d=\"m15 5 4 4\" />", "trash-2": "<path d=\"M10 11v6\" /> <path d=\"M14 11v6\" /> <path d=\"M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6\" /> <path d=\"M3 6h18\" /> <path d=\"M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2\" />", "ellipsis": "<circle cx=\"12\" cy=\"12\" r=\"1\" /> <circle cx=\"19\" cy=\"12\" r=\"1\" /> <circle cx=\"5\" cy=\"12\" r=\"1\" />", "menu": "<path d=\"M4 5h16\" /> <path d=\"M4 12h16\" /> <path d=\"M4 19h16\" />", "lock": "<rect width=\"18\" height=\"11\" x=\"3\" y=\"11\" rx=\"2\" ry=\"2\" /> <path d=\"M7 11V7a5 5 0 0 1 10 0v4\" />", "banknote": "<rect width=\"20\" height=\"12\" x=\"2\" y=\"6\" rx=\"2\" /> <circle cx=\"12\" cy=\"12\" r=\"2\" /> <path d=\"M6 12h.01M18 12h.01\" />", "receipt": "<path d=\"M12 17V7\" /> <path d=\"M16 8h-6a2 2 0 0 0 0 4h4a2 2 0 0 1 0 4H8\" /> <path d=\"M4 3a1 1 0 0 1 1-1 1.3 1.3 0 0 1 .7.2l.933.6a1.3 1.3 0 0 0 1.4 0l.934-.6a1.3 1.3 0 0 1 1.4 0l.933.6a1.3 1.3 0 0 0 1.4 0l.933-.6a1.3 1.3 0 0 1 1.4 0l.934.6a1.3 1.3 0 0 0 1.4 0l.933-.6A1.3 1.3 0 0 1 19 2a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1 1.3 1.3 0 0 1-.7-.2l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.934.6a1.3 1.3 0 0 1-1.4 0l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-1.4 0l-.934-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-.7.2 1 1 0 0 1-1-1z\" />", "party-popper": "<path d=\"M5.8 11.3 2 22l10.7-3.79\" /> <path d=\"M4 3h.01\" /> <path d=\"M22 8h.01\" /> <path d=\"M15 2h.01\" /> <path d=\"M22 20h.01\" /> <path d=\"m22 2-2.24.75a2.9 2.9 0 0 0-1.96 3.12c.1.86-.57 1.63-1.45 1.63h-.38c-.86 0-1.6.6-1.76 1.44L14 10\" /> <path d=\"m22 13-.82-.33c-.86-.34-1.82.2-1.98 1.11c-.11.7-.72 1.22-1.43 1.22H17\" /> <path d=\"m11 2 .33.82c.34.86-.2 1.82-1.11 1.98C9.52 4.9 9 5.52 9 6.23V7\" /> <path d=\"M11 13c1.93 1.93 2.83 4.17 2 5-.83.83-3.07-.07-5-2-1.93-1.93-2.83-4.17-2-5 .83-.83 3.07.07 5 2Z\" />", "sparkles": "<path d=\"M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z\" /> <path d=\"M20 2v4\" /> <path d=\"M22 4h-4\" /> <circle cx=\"4\" cy=\"20\" r=\"2\" />", "star": "<path d=\"M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z\" />", "layout-dashboard": "<rect width=\"7\" height=\"9\" x=\"3\" y=\"3\" rx=\"1\" /> <rect width=\"7\" height=\"5\" x=\"14\" y=\"3\" rx=\"1\" /> <rect width=\"7\" height=\"9\" x=\"14\" y=\"12\" rx=\"1\" /> <rect width=\"7\" height=\"5\" x=\"3\" y=\"16\" rx=\"1\" />", "shield-check": "<path d=\"M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z\" /> <path d=\"m9 12 2 2 4-4\" />", "key-round": "<path d=\"M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z\" /> <circle cx=\"16.5\" cy=\"7.5\" r=\".5\" fill=\"currentColor\" />", "eye": "<path d=\"M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0\" /> <circle cx=\"12\" cy=\"12\" r=\"3\" />", "eye-off": "<path d=\"M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49\" /> <path d=\"M14.084 14.158a3 3 0 0 1-4.242-4.242\" /> <path d=\"M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143\" /> <path d=\"m2 2 20 20\" />", "arrow-right": "<path d=\"M5 12h14\" /> <path d=\"m12 5 7 7-7 7\" />", "arrow-left": "<path d=\"m12 19-7-7 7-7\" /> <path d=\"M19 12H5\" />", "truck": "<path d=\"M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2\" /> <path d=\"M15 18H9\" /> <path d=\"M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.624l-3.48-4.35A1 1 0 0 0 17.52 8H14\" /> <circle cx=\"17\" cy=\"18\" r=\"2\" /> <circle cx=\"7\" cy=\"18\" r=\"2\" />", "download": "<path d=\"M12 15V3\" /> <path d=\"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4\" /> <path d=\"m7 10 5 5 5-5\" />", "upload": "<path d=\"M12 3v12\" /> <path d=\"m17 8-5-5-5 5\" /> <path d=\"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4\" />", "bell": "<path d=\"M10.268 21a2 2 0 0 0 3.464 0\" /> <path d=\"M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326\" />", "sun": "<circle cx=\"12\" cy=\"12\" r=\"4\" /> <path d=\"M12 2v2\" /> <path d=\"M12 20v2\" /> <path d=\"m4.93 4.93 1.41 1.41\" /> <path d=\"m17.66 17.66 1.41 1.41\" /> <path d=\"M2 12h2\" /> <path d=\"M20 12h2\" /> <path d=\"m6.34 17.66-1.41 1.41\" /> <path d=\"m19.07 4.93-1.41 1.41\" />", "moon": "<path d=\"M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401\" />", "circle-alert": "<circle cx=\"12\" cy=\"12\" r=\"10\" /> <line x1=\"12\" x2=\"12\" y1=\"8\" y2=\"12\" /> <line x1=\"12\" x2=\"12.01\" y1=\"16\" y2=\"16\" />", "flag": "<path d=\"M4 22V4a1 1 0 0 1 .4-.8A6 6 0 0 1 8 2c3 0 5 2 7.333 2q2 0 3.067-.8A1 1 0 0 1 20 4v10a1 1 0 0 1-.4.8A6 6 0 0 1 16 16c-3 0-5-2-8-2a6 6 0 0 0-4 1.528\" />", "hourglass": "<path d=\"M5 22h14\" /> <path d=\"M5 2h14\" /> <path d=\"M17 22v-4.172a2 2 0 0 0-.586-1.414L12 12l-4.414 4.414A2 2 0 0 0 7 17.828V22\" /> <path d=\"M7 2v4.172a2 2 0 0 0 .586 1.414L12 12l4.414-4.414A2 2 0 0 0 17 6.172V2\" />", "file-text": "<path d=\"M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z\" /> <path d=\"M14 2v5a1 1 0 0 0 1 1h5\" /> <path d=\"M10 9H8\" /> <path d=\"M16 13H8\" /> <path d=\"M16 17H8\" />", "tag": "<path d=\"M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z\" /> <circle cx=\"7.5\" cy=\"7.5\" r=\".5\" fill=\"currentColor\" />", "map-pin": "<path d=\"M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0\" /> <circle cx=\"12\" cy=\"10\" r=\"3\" />", "utensils-crossed": "<path d=\"m16 2-2.3 2.3a3 3 0 0 0 0 4.2l1.8 1.8a3 3 0 0 0 4.2 0L22 8\" /> <path d=\"M15 15 3.3 3.3a4.2 4.2 0 0 0 0 6l7.3 7.3c.7.7 2 .7 2.8 0L15 15Zm0 0 7 7\" /> <path d=\"m2.1 21.8 6.4-6.3\" /> <path d=\"m19 5-7 7\" />", "refresh-cw": "<path d=\"M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8\" /> <path d=\"M21 3v5h-5\" /> <path d=\"M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16\" /> <path d=\"M8 16H3v5\" />", "badge-check": "<path d=\"M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z\" /> <path d=\"m16 9-5.5 5.5L8 12\" />", "ban": "<circle cx=\"12\" cy=\"12\" r=\"10\" /> <path d=\"M4.929 4.929 19.07 19.071\" />", "repeat-2": "<path d=\"m2 9 3-3 3 3\" /> <path d=\"M13 18H7a2 2 0 0 1-2-2V6\" /> <path d=\"m22 15-3 3-3-3\" /> <path d=\"M11 6h6a2 2 0 0 1 2 2v10\" />", "circle-dashed": "<path d=\"M10.1 2.182a10 10 0 0 1 3.8 0\" /> <path d=\"M13.9 21.818a10 10 0 0 1-3.8 0\" /> <path d=\"M17.609 3.721a10 10 0 0 1 2.69 2.7\" /> <path d=\"M2.182 13.9a10 10 0 0 1 0-3.8\" /> <path d=\"M20.279 17.609a10 10 0 0 1-2.7 2.69\" /> <path d=\"M21.818 10.1a10 10 0 0 1 0 3.8\" /> <path d=\"M3.721 6.391a10 10 0 0 1 2.7-2.69\" /> <path d=\"M6.391 20.279a10 10 0 0 1-2.69-2.7\" />"};
  var React = window.React;
  var h = React.createElement;
  var useState = React.useState, useEffect = React.useEffect, useRef = React.useRef, useId = React.useId;

  function cx() { var o = []; for (var i = 0; i < arguments.length; i++) { if (arguments[i]) o.push(arguments[i]); } return o.join(' '); }
  function fid(id) { var g = useId(); return id || ('v' + String(g).replace(/[^a-zA-Z0-9]/g, '')); }
  function initials(n) {
    var w = String(n || '?').trim().split(/\s+/);
    var a = (w[0] || '?').charAt(0), b = w.length > 1 ? w[w.length - 1].charAt(0) : '';
    return (a + b).toUpperCase();
  }

  /* ---------- Icon ---------- */
  function Icon(p) {
    var size = p.size || 20;
    return h('svg', {
      className: cx('v-icon', p.className), width: size, height: size, viewBox: '0 0 24 24', fill: 'none',
      stroke: 'currentColor', strokeWidth: p.strokeWidth || 2, strokeLinecap: 'round', strokeLinejoin: 'round',
      'aria-hidden': p.title ? undefined : true, role: p.title ? 'img' : undefined, 'aria-label': p.title || undefined,
      focusable: 'false', dangerouslySetInnerHTML: { __html: ICONS[p.name] || '' }
    });
  }

  /* ---------- Button / IconButton ---------- */
  function Button(p) {
    var variant = p.variant || 'solid', size = p.size || 'md';
    var cls = cx('v-btn', 'button', 'v-btn--' + variant, 'v-btn--' + size, p.tone === 'danger' && 'v-tone-danger',
      p.block && 'v-btn--block', p.loading && 'is-loading', p.className);
    var kids = [];
    if (p.loading) kids.push(h('span', { key: 's', className: 'v-spin', 'aria-hidden': true }));
    else if (p.icon) kids.push(h(Icon, { key: 'i', name: p.icon, size: size === 'sm' ? 18 : 20 }));
    kids.push(h('span', { key: 'l' }, p.children));
    if (p.iconEnd && !p.loading) kids.push(h(Icon, { key: 'e', name: p.iconEnd, size: size === 'sm' ? 18 : 20 }));
    var common = { className: cls, onClick: p.onClick, title: p.title, 'aria-busy': p.loading || undefined };
    if (p.href) return h('a', Object.assign({ href: p.href }, common), kids);
    return h('button', Object.assign({ type: p.type || 'button', disabled: p.disabled || p.loading }, common), kids);
  }

  function IconButton(p) {
    var size = p.size || 'md';
    return h('button', {
      type: p.type || 'button', className: cx('v-iconbtn', 'v-iconbtn--' + (p.variant || 'outline'), 'v-iconbtn--' + size, p.tone === 'danger' && 'v-tone-danger', p.className),
      'aria-label': p.label, title: p.label, disabled: p.disabled, onClick: p.onClick, 'aria-pressed': p.pressed
    }, h(Icon, { name: p.icon, size: size === 'sm' ? 18 : 22 }));
  }

  /* ---------- Forms ---------- */
  function Field(p) {
    return h('div', { className: cx('v-field', p.className) },
      p.label && h('label', { className: 'label v-field__label', htmlFor: p.id }, p.label, p.optional && h('span', { className: 'v-muted' }, ' (opcional)')),
      p.children,
      p.error
        ? h('p', { id: p.id + '-msg', className: 'body-sm v-field__error', role: 'alert' }, h(Icon, { name: 'circle-alert', size: 16 }), h('span', null, p.error))
        : (p.hint && h('p', { id: p.id + '-msg', className: 'body-sm v-muted' }, p.hint))
    );
  }

  function Input(p) {
    var id = fid(p.id);
    var props = {
      id: id, name: p.name, type: p.multiline ? undefined : (p.type || 'text'), value: p.value, defaultValue: p.defaultValue,
      onChange: p.onChange, placeholder: p.placeholder, disabled: p.disabled, required: p.required, readOnly: p.readOnly,
      inputMode: p.inputMode, autoComplete: p.autoComplete, rows: p.multiline ? (p.rows || 3) : undefined,
      className: cx('v-control', 'body', p.size === 'lg' && 'v-control--lg', p.icon && 'has-icon', p.suffix && 'has-suffix', p.multiline && 'v-control--area'),
      'aria-invalid': p.error ? true : undefined, 'aria-describedby': (p.error || p.hint) ? id + '-msg' : undefined
    };
    var control = h(p.multiline ? 'textarea' : 'input', props);
    return h(Field, { label: p.label, hint: p.hint, error: p.error, optional: p.optional, id: id, className: p.className },
      h('div', { className: 'v-control-wrap' },
        p.icon && h(Icon, { name: p.icon, size: 20, className: 'v-control__icon' }),
        control,
        p.suffix && h('span', { className: 'body-sm v-muted v-control__suffix' }, p.suffix)));
  }

  function Select(p) {
    var id = fid(p.id);
    var opts = (p.options || []).map(function (o) { return h('option', { key: o.value, value: o.value, disabled: o.disabled }, o.label); });
    if (p.placeholder) opts.unshift(h('option', { key: '__ph', value: '', disabled: true }, p.placeholder));
    return h(Field, { label: p.label, hint: p.hint, error: p.error, optional: p.optional, id: id, className: p.className },
      h('div', { className: 'v-control-wrap' },
        h('select', {
          id: id, name: p.name, value: p.value, defaultValue: p.value === undefined ? (p.defaultValue !== undefined ? p.defaultValue : (p.placeholder ? '' : undefined)) : undefined,
          onChange: p.onChange, disabled: p.disabled, required: p.required,
          className: cx('v-control', 'body', 'v-select', p.size === 'lg' && 'v-control--lg'),
          'aria-invalid': p.error ? true : undefined, 'aria-describedby': (p.error || p.hint) ? id + '-msg' : undefined
        }, opts),
        h(Icon, { name: 'chevron-down', size: 20, className: 'v-select__chev' })));
  }

  function Checkbox(p) {
    var id = fid(p.id);
    return h('label', { className: cx('v-choice', p.disabled && 'is-disabled', p.className), htmlFor: id },
      h('input', { id: id, type: 'checkbox', className: 'v-check', checked: p.checked, defaultChecked: p.defaultChecked, onChange: p.onChange, disabled: p.disabled, name: p.name }),
      h('span', { className: 'v-choice__text' }, h('span', { className: 'body' }, p.label), p.hint && h('span', { className: 'body-sm v-muted' }, p.hint)));
  }

  function Switch(p) {
    var id = fid(p.id);
    return h('label', { className: cx('v-choice', 'v-choice--switch', p.disabled && 'is-disabled', p.className), htmlFor: id },
      h('input', { id: id, type: 'checkbox', role: 'switch', className: 'v-switch', checked: p.checked, defaultChecked: p.defaultChecked, onChange: p.onChange, disabled: p.disabled, name: p.name }),
      h('span', { className: 'v-choice__text' }, h('span', { className: 'body' }, p.label), p.hint && h('span', { className: 'body-sm v-muted' }, p.hint)));
  }

  function Stepper(p) {
    var id = fid(p.id);
    var st = useState(p.defaultValue == null ? 0 : p.defaultValue);
    var ctl = p.value !== undefined, v = ctl ? p.value : st[0];
    var min = p.min == null ? 0 : p.min, max = p.max == null ? Infinity : p.max, step = p.step || 1;
    function set(n) { n = Math.max(min, Math.min(max, n)); if (!ctl) st[1](n); if (p.onChange) p.onChange(n); }
    return h(Field, { label: p.label, hint: p.hint, error: p.error, id: id, className: p.className },
      h('div', { className: cx('v-stepper', p.error && 'is-invalid') },
        h('button', { type: 'button', className: 'v-stepper__btn', 'aria-label': 'Restar ' + step, disabled: p.disabled || v <= min, onClick: function () { set(v - step); } }, h(Icon, { name: 'minus', size: 22 })),
        h('div', { className: 'v-stepper__value' },
          h('input', {
            id: id, className: 'numeral v-stepper__input', inputMode: 'numeric', value: v, disabled: p.disabled,
            'aria-describedby': (p.error || p.hint) ? id + '-msg' : undefined, 'aria-invalid': p.error ? true : undefined,
            onChange: function (e) { var n = parseInt(String(e.target.value).replace(/\D/g, ''), 10); set(isNaN(n) ? min : n); }
          }),
          p.unit && h('span', { className: 'caption v-muted' }, p.unit)),
        h('button', { type: 'button', className: 'v-stepper__btn', 'aria-label': 'Sumar ' + step, disabled: p.disabled || v >= max, onClick: function () { set(v + step); } }, h(Icon, { name: 'plus', size: 22 }))));
  }

  /* ---------- Feedback ---------- */
  var TONE_ICON = { success: 'circle-check', warning: 'triangle-alert', danger: 'circle-alert', info: 'info' };
  function Alert(p) {
    var tone = p.tone || 'info';
    return h('div', { className: cx('v-alert', 'v-alert--' + tone, p.className), role: (tone === 'danger' || tone === 'warning') ? 'alert' : 'status' },
      h(Icon, { name: p.icon || TONE_ICON[tone], size: 22, className: 'v-alert__icon' }),
      h('div', { className: 'v-alert__body' },
        p.title && h('p', { className: 'label' }, p.title),
        p.children && h('div', { className: 'body-sm' }, p.children)),
      p.action && h('div', { className: 'v-alert__action' }, p.action));
  }

  function Dialog(p) {
    var tid = fid(p.id), ref = useRef(null);
    useEffect(function () {
      if (!p.open || p.inline) return undefined;
      if (ref.current) ref.current.focus();
      function k(e) { if (e.key === 'Escape' && p.onClose) p.onClose(); }
      document.addEventListener('keydown', k);
      return function () { document.removeEventListener('keydown', k); };
    }, [p.open, p.inline]);
    if (!p.open && !p.inline) return null;
    var panel = h('div', { ref: ref, tabIndex: -1, role: 'dialog', 'aria-modal': p.inline ? undefined : true, 'aria-labelledby': tid, className: cx('v-dialog', p.className) },
      h('div', { className: 'v-dialog__head' },
        h('h2', { id: tid, className: 'h2' }, p.title),
        p.onClose && h(IconButton, { icon: 'x', label: 'Cerrar', variant: 'text', onClick: p.onClose })),
      h('div', { className: 'body v-dialog__body' }, p.children),
      p.actions && h('div', { className: 'v-dialog__foot' }, p.actions));
    if (p.inline) return h('div', { className: 'v-dialog-stage' }, panel);
    return h('div', { className: 'v-scrim', onMouseDown: function (e) { if (e.target === e.currentTarget && p.onClose) p.onClose(); } }, panel);
  }

  function EmptyState(p) {
    return h('div', { className: cx('v-empty', p.className) },
      h('span', { className: 'v-empty__icon', 'aria-hidden': true }, h(Icon, { name: p.icon || 'sparkles', size: 28 })),
      h('h3', { className: 'h3' }, p.title),
      p.children && h('p', { className: 'body v-muted v-empty__text' }, p.children),
      p.action);
  }

  function Badge(p) {
    return h('span', { className: cx('v-badge', 'caption', 'v-badge--' + (p.tone || 'neutral'), p.className) },
      p.icon && h(Icon, { name: p.icon, size: 14, strokeWidth: 2.25 }), p.children);
  }

  /* ---------- Status & salon ---------- */
  var STATUS = {
    disponible: { label: 'Disponible', icon: 'circle-dashed', tone: 'outline' },
    prereserva: { label: 'Pre-reserva', icon: 'hourglass', tone: 'warning' },
    senado: { label: 'Señado', icon: 'banknote', tone: 'info' },
    confirmado: { label: 'Confirmado', icon: 'circle-check', tone: 'success' },
    realizado: { label: 'Realizado', icon: 'badge-check', tone: 'solid' },
    cancelado: { label: 'Cancelado', icon: 'circle-x', tone: 'danger' },
    bloqueado: { label: 'Bloqueado', icon: 'lock', tone: 'muted' },
    ok: { label: 'En stock', icon: 'circle-check', tone: 'success' },
    bajo: { label: 'Stock bajo', icon: 'triangle-alert', tone: 'warning' },
    'sin-stock': { label: 'Sin stock', icon: 'circle-x', tone: 'danger' }
  };
  function StatusChip(p) {
    var s = STATUS[p.status] || STATUS.disponible, sm = p.size === 'sm';
    return h('span', { className: cx('v-chip', 'label', 'v-chip--' + s.tone, sm && 'v-chip--sm', p.className) },
      h(Icon, { name: s.icon, size: sm ? 14 : 16, strokeWidth: 2.25 }), p.label || s.label);
  }

  var SALONS = { avril: 'Avril', club: 'Club de Campo', 'santa-barbara': 'Santa Bárbara' };
  var SALON_ORDER = ['avril', 'club', 'santa-barbara'];
  function SalonTag(p) {
    return h('span', { className: cx('v-salon', 'label', 'v-salon--' + (p.variant || 'tint'), 'v-salon--' + p.salon, p.className) },
      h('span', { className: 'v-salon__dot', 'aria-hidden': true }), SALONS[p.salon] || p.salon);
  }

  /* ---------- Data ---------- */
  function Card(p) {
    var Tag = p.as || 'section';
    var head = (p.title || p.eyebrow || p.actions) && h('header', { className: 'v-card__head' },
      h('div', { className: 'v-card__titles' },
        p.eyebrow && h('p', { className: 'overline v-muted' }, p.eyebrow),
        p.title && h('h3', { className: 'h3' }, p.title),
        p.subtitle && h('p', { className: 'body-sm v-muted' }, p.subtitle)),
      p.actions && h('div', { className: 'v-card__actions' }, p.actions));
    return h(Tag, {
      className: cx('v-card', p.interactive && 'v-card--interactive', p.flush && 'v-card--flush', p.className),
      onClick: p.onClick, tabIndex: p.interactive ? 0 : undefined,
      onKeyDown: (p.interactive && p.onClick) ? function (e) { if (e.key === 'Enter') p.onClick(e); } : undefined
    }, head, h('div', { className: 'v-card__body' }, p.children), p.footer && h('footer', { className: 'v-card__foot' }, p.footer));
  }

  function Table(p) {
    var cols = p.columns || [], rows = p.rows || [];
    return h('div', { className: cx('v-table-wrap', p.className) },
      h('table', { className: cx('v-table', p.dense && 'v-table--dense') },
        p.caption && h('caption', { className: 'v-sr' }, p.caption),
        h('thead', null, h('tr', null, cols.map(function (c) {
          return h('th', { key: c.key, scope: 'col', className: cx('label', c.numeric && 'v-num'), style: c.width ? { width: c.width } : undefined }, c.header);
        }))),
        h('tbody', null, rows.length ? rows.map(function (r, i) {
          return h('tr', {
            key: r.id != null ? r.id : i, className: p.onRowClick ? 'is-click' : undefined, tabIndex: p.onRowClick ? 0 : undefined,
            onClick: p.onRowClick ? function () { p.onRowClick(r); } : undefined,
            onKeyDown: p.onRowClick ? function (e) { if (e.key === 'Enter') p.onRowClick(r); } : undefined
          }, cols.map(function (c) {
            return h('td', { key: c.key, className: cx(c.numeric ? 'numeral-sm v-num' : 'body-sm') }, c.render ? c.render(r) : r[c.key]);
          }));
        }) : h('tr', null, h('td', { colSpan: cols.length, className: 'body-sm v-muted v-table__empty' }, p.empty || 'Sin resultados')))));
  }

  function Tabs(p) {
    var items = p.items || [];
    var st = useState(p.defaultValue || (items[0] && items[0].id));
    var ctl = p.value !== undefined, v = ctl ? p.value : st[0];
    function sel(id) { if (!ctl) st[1](id); if (p.onChange) p.onChange(id); }
    function key(e, i) {
      var n = items.length, j = e.key === 'ArrowRight' ? (i + 1) % n : e.key === 'ArrowLeft' ? (i + n - 1) % n : -1;
      if (j < 0) return;
      e.preventDefault(); sel(items[j].id);
      var el = e.currentTarget.parentNode.children[j]; if (el) el.focus();
    }
    return h('div', { role: 'tablist', 'aria-label': p.label, className: cx('v-tabs', 'v-tabs--' + (p.variant || 'underline'), p.className) },
      items.map(function (it, i) {
        var on = it.id === v;
        return h('button', {
          key: it.id, role: 'tab', type: 'button', 'aria-selected': on, tabIndex: on ? 0 : -1, className: cx('v-tab', 'label', on && 'is-on'),
          onClick: function () { sel(it.id); }, onKeyDown: function (e) { key(e, i); }
        }, it.icon && h(Icon, { name: it.icon, size: 18 }), it.label, it.count != null && h('span', { className: 'v-tab__count caption' }, it.count));
      }));
  }

  function Actor(p) {
    var meta = [p.action, p.at].filter(Boolean).join(' · ');
    return h('div', { className: cx('v-actor', 'v-actor--' + (p.size || 'md'), p.className) },
      h('span', { className: 'v-avatar label', 'aria-hidden': true }, initials(p.name)),
      h('div', { className: 'v-actor__text' },
        h('span', { className: 'label' }, p.name),
        meta && h('span', { className: 'body-sm v-muted' }, meta)));
  }

  function Stat(p) {
    return h('div', { className: cx('v-stat', p.className) },
      h('p', { className: 'label v-muted' }, p.label),
      h('p', { className: 'v-stat__row' }, h('span', { className: 'stat' }, p.value), p.unit && h('span', { className: 'body-sm v-muted' }, p.unit)),
      p.delta && h('div', null, h(Badge, { tone: p.deltaTone || 'neutral', icon: p.deltaIcon }, p.delta)),
      p.hint && h('p', { className: 'body-sm v-muted' }, p.hint));
  }

  function Timeline(p) {
    return h('ol', { className: cx('v-timeline', p.className) }, (p.items || []).map(function (it, i) {
      return h('li', { key: i, className: 'v-tl__item' },
        h('span', { className: cx('v-tl__dot', 'v-tl__dot--' + (it.tone || 'neutral')), 'aria-hidden': true }, h(Icon, { name: it.icon || 'circle-check', size: 16, strokeWidth: 2.25 })),
        h('div', { className: 'v-tl__body' },
          h('p', { className: 'label' }, it.title),
          (it.from || it.to) && h('p', { className: 'v-tl__flow' },
            it.from && h(StatusChip, { status: it.from, size: 'sm' }),
            it.from && it.to && h(Icon, { name: 'arrow-right', size: 14, className: 'v-muted' }),
            it.to && h(StatusChip, { status: it.to, size: 'sm' })),
          it.detail && h('p', { className: 'body-sm v-muted' }, it.detail),
          it.actor && h(Actor, { name: it.actor, at: it.at, action: it.action, size: 'sm' })));
    }));
  }

  /* ---------- Navigation ---------- */
  function Nav(p) {
    var items = p.items || [], layout = p.layout || 'sidebar';
    function pick(it) { return function () { if (p.onSelect) p.onSelect(it.id); }; }
    if (layout === 'bottom') {
      return h('nav', { 'aria-label': p.label || 'Principal', className: cx('v-nav', 'v-nav--bottom', p.className) }, items.map(function (it) {
        var on = it.id === p.value;
        return h('button', { key: it.id, type: 'button', className: cx('v-nav__item', 'caption', on && 'is-on'), 'aria-current': on ? 'page' : undefined, onClick: pick(it) },
          h('span', { className: 'v-nav__ico' }, h(Icon, { name: it.icon, size: 22 }), it.badge != null && h('span', { className: 'v-nav__badge caption' }, it.badge)), it.label);
      }));
    }
    var groups = [], idx = {};
    items.forEach(function (it) { var g = it.group || ''; if (!(g in idx)) { idx[g] = groups.length; groups.push({ name: g, items: [] }); } groups[idx[g]].items.push(it); });
    return h('nav', { 'aria-label': p.label || 'Principal', className: cx('v-nav', 'v-nav--sidebar', p.className) },
      p.logo && h('div', { className: 'v-nav__logo' }, p.logo),
      h('div', { className: 'v-nav__scroll' }, groups.map(function (g, gi) {
        return h('div', { key: gi, className: 'v-nav__group' },
          g.name && h('p', { className: 'overline v-muted v-nav__title' }, g.name),
          g.items.map(function (it) {
            var on = it.id === p.value;
            return h('button', { key: it.id, type: 'button', className: cx('v-nav__item', 'label', on && 'is-on'), 'aria-current': on ? 'page' : undefined, onClick: pick(it) },
              h(Icon, { name: it.icon, size: 20 }), h('span', { className: 'v-nav__text' }, it.label), it.badge != null && h('span', { className: 'v-nav__count caption' }, it.badge));
          }));
      })),
      p.footer && h('div', { className: 'v-nav__foot' }, p.footer));
  }

  /* ---------- Agenda (Módulo A) ---------- */
  var MONTHS = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
  var DAYS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
  var DAYS_LONG = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'];
  var TURNOS = ['mediodia', 'noche'];
  function pad(n) { return (n < 10 ? '0' : '') + n; }

  function AgendaGrid(p) {
    var y = p.year, m = p.month, first = new Date(y, m, 1), lead = (first.getDay() + 6) % 7, total = new Date(y, m + 1, 0).getDate();
    var byDate = {};
    (p.events || []).forEach(function (e) { (byDate[e.date] = byDate[e.date] || []).push(e); });
    var cells = [], i;
    for (i = 0; i < lead; i++) cells.push(h('div', { key: 'b' + i, className: 'v-day v-day--blank', 'aria-hidden': true }));
    for (var d = 1; d <= total; d++) {
      (function (d) {
        var iso = y + '-' + pad(m + 1) + '-' + pad(d), evs = byDate[iso] || [];
        var pips = [], label = [];
        TURNOS.forEach(function (t) {
          SALON_ORDER.forEach(function (s) {
            var ev = evs.filter(function (e) { return e.salon === s && e.turno === t; })[0];
            var st = ev ? ev.status : 'disponible';
            pips.push(h('span', { key: t + s, className: cx('v-pip', 'v-pip--' + s, 'v-pip--' + st) }));
            if (ev && st !== 'disponible') label.push(SALONS[s] + ' ' + (t === 'noche' ? 'noche' : 'mediodía') + ': ' + (STATUS[st] ? STATUS[st].label : st));
          });
        });
        var wd = DAYS_LONG[(new Date(y, m, d).getDay() + 6) % 7];
        cells.push(h('button', {
          key: iso, type: 'button', className: cx('v-day', p.selected === iso && 'is-selected', p.today === iso && 'is-today'),
          'aria-pressed': p.selected === iso, 'aria-label': wd + ' ' + d + ' de ' + MONTHS[m].toLowerCase() + (label.length ? '. ' + label.join('; ') : '. Todo disponible'),
          onClick: function () { if (p.onSelectDay) p.onSelectDay(iso); }
        }, h('span', { className: 'v-day__num numeral-sm' }, d), h('span', { className: 'v-day__pips', 'aria-hidden': true }, pips)));
      })(d);
    }
    var head = h('div', { className: 'v-agenda__head' },
      p.onPrev && h(IconButton, { icon: 'chevron-left', label: 'Mes anterior', onClick: p.onPrev }),
      h('h2', { className: 'h2 v-agenda__title' }, MONTHS[m] + ' ' + y),
      p.onNext && h(IconButton, { icon: 'chevron-right', label: 'Mes siguiente', onClick: p.onNext }));
    var legend = p.legend === false ? null : h('div', { className: 'v-agenda__legend body-sm v-muted' },
      h('span', { className: 'v-legend__item' }, h(Icon, { name: 'sun', size: 16 }), 'Fila superior: mediodía'),
      h('span', { className: 'v-legend__item' }, h(Icon, { name: 'moon', size: 16 }), 'Fila inferior: noche'),
      ['prereserva', 'senado', 'confirmado', 'bloqueado'].map(function (s) {
        return h('span', { key: s, className: 'v-legend__item' }, h('span', { className: 'v-pip v-pip--club v-pip--' + s }), STATUS[s].label);
      }));
    return h('div', { className: cx('v-agenda', p.className) }, head,
      h('div', { className: 'v-agenda__weekdays label', 'aria-hidden': true }, DAYS.map(function (n) { return h('span', { key: n }, n); })),
      h('div', { className: 'v-agenda__grid' }, cells),
      legend,
      h('div', { className: 'v-agenda__salons' }, SALON_ORDER.map(function (s) { return h(SalonTag, { key: s, salon: s }); })));
  }

  function EventCard(p) {
    var open = p.onOpen ? function () { p.onOpen(p); } : undefined;
    return h(Card, { interactive: !!open, onClick: open, className: cx('v-event', p.className) },
      h('div', { className: 'v-event__top' }, h(SalonTag, { salon: p.salon }), h(StatusChip, { status: p.status })),
      h('div', null, h('p', { className: 'overline v-muted' }, p.tipo), h('h3', { className: 'h3' }, p.title)),
      h('dl', { className: 'v-event__meta body-sm' },
        h('div', null, h(Icon, { name: 'calendar', size: 18 }), h('dt', { className: 'v-sr' }, 'Fecha'), h('dd', null, p.fecha)),
        h('div', null, h(Icon, { name: 'clock', size: 18 }), h('dt', { className: 'v-sr' }, 'Turno'), h('dd', null, p.turno)),
        p.invitados != null && h('div', null, h(Icon, { name: 'users', size: 18 }), h('dt', { className: 'v-sr' }, 'Invitados'), h('dd', { className: 'numeral-sm' }, p.invitados + ' invitados'))),
      h('div', { className: 'v-event__foot' },
        p.vendedora && h(Actor, { name: p.vendedora, action: 'Vendedora', size: 'sm' }),
        p.planner ? h(Actor, { name: p.planner, action: 'Planner', size: 'sm' }) : h(Badge, { tone: 'warning', icon: 'user' }, 'Sin planner')));
  }

  /* ---------- Stock (Módulo B) ---------- */
  function StockLevel(p) {
    var c = p.cantidad, need = p.comprometido || 0;
    var unidad = p.unidad || 'cajones';
    var unit = function (n) { return n === 1 ? (p.unidadUno || 'cajón') : unidad; };
    var status = p.status || (c <= 0 ? 'sin-stock' : (c < need ? 'bajo' : 'ok'));
    var scale = Math.max(c, need, 1) * 1.15, fill = Math.min(100, (c / scale) * 100), mark = Math.min(100, (need / scale) * 100);
    return h('div', { className: cx('v-stock', p.className) },
      h('div', { className: 'v-stock__top' },
        h('div', null, h('p', { className: 'label' }, p.name), h('p', { className: 'body-sm v-muted' }, [p.presentacion, p.ubicacion].filter(Boolean).join(' · '))),
        h(StatusChip, { status: status, size: 'sm' })),
      h('p', { className: 'v-stock__qty' }, h('span', { className: 'numeral' }, c), h('span', { className: 'body-sm v-muted' }, ' ' + unit(c))),
      h('div', { className: cx('v-meter', 'v-meter--' + status), role: 'meter', 'aria-valuemin': 0, 'aria-valuemax': Math.round(scale), 'aria-valuenow': c, 'aria-label': 'Existencias de ' + p.name },
        h('span', { className: 'v-meter__fill', style: { width: fill + '%' } }),
        need > 0 && h('span', { className: 'v-meter__mark', style: { left: mark + '%' } })),
      need > 0 && h('p', { className: 'body-sm v-muted' }, h('span', { className: 'numeral-sm' }, need), ' ' + unit(need) + (need === 1 ? ' comprometido' : ' comprometidos') + ' por la agenda'));
  }

  var MOV = {
    entrega: { label: 'Entrega a barra', icon: 'arrow-right-left', tone: 'info' },
    devolucion: { label: 'Devolución', icon: 'undo-2', tone: 'success' },
    'retiro-adicional': { label: 'Retiro adicional', icon: 'plus', tone: 'warning' },
    ingreso: { label: 'Ingreso al depósito', icon: 'truck', tone: 'brand' }
  };
  function MovementCard(p) {
    var m = MOV[p.tipo] || MOV.entrega;
    return h(Card, { className: cx('v-move', p.className) },
      h('div', { className: 'v-move__top' }, h(Badge, { tone: m.tone, icon: m.icon }, m.label), p.salon && h(SalonTag, { salon: p.salon })),
      p.evento && h('p', { className: 'h4' }, p.evento),
      (p.desde || p.hasta) && h('p', { className: 'v-move__route label' }, p.desde, h(Icon, { name: 'arrow-right', size: 16, className: 'v-muted' }), p.hasta),
      h('ul', { className: 'v-move__items' }, (p.items || []).map(function (it, i) {
        return h('li', { key: i, className: 'body-sm' }, h('span', null, it.nombre), h('span', { className: 'numeral-sm' }, it.cantidad + ' ' + (it.cantidad === 1 ? (it.unidadUno || 'cajón') : (it.unidad || 'cajones'))));
      })),
      p.nota && h('p', { className: 'body-sm v-muted' }, p.nota),
      h('div', { className: 'v-move__foot' },
        h(Actor, { name: p.actor, at: p.at, action: 'Registró', size: 'sm' }),
        h('span', { className: 'caption v-muted v-move__lock' }, h(Icon, { name: 'lock', size: 14 }), 'No editable · se corrige con un ajuste')));
  }

  window.Vastio = {
    Icon: Icon, Button: Button, IconButton: IconButton, Input: Input, Select: Select, Checkbox: Checkbox, Switch: Switch, Stepper: Stepper,
    Alert: Alert, Dialog: Dialog, EmptyState: EmptyState, Badge: Badge, StatusChip: StatusChip, SalonTag: SalonTag,
    Card: Card, Table: Table, Tabs: Tabs, Actor: Actor, Stat: Stat, Timeline: Timeline, Nav: Nav,
    AgendaGrid: AgendaGrid, EventCard: EventCard, StockLevel: StockLevel, MovementCard: MovementCard
  };
})();
